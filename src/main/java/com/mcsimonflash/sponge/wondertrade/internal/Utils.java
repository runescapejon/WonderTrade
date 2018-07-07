package com.mcsimonflash.sponge.wondertrade.internal;

import com.google.common.base.Preconditions;
import com.mcsimonflash.sponge.wondertrade.WonderTrade;
import com.mcsimonflash.sponge.wondertrade.data.TradeEntry;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.enums.DeleteType;
import com.pixelmonmod.pixelmon.api.enums.ReceiveType;
import com.pixelmonmod.pixelmon.api.events.PixelmonDeletedEvent;
import com.pixelmonmod.pixelmon.api.events.PixelmonReceivedEvent;
import com.pixelmonmod.pixelmon.config.PixelmonEntityList;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityPixelmon;
import com.pixelmonmod.pixelmon.enums.EnumPokemon;
import com.pixelmonmod.pixelmon.items.heldItems.NoItem;
import com.pixelmonmod.pixelmon.storage.PixelmonStorage;
import com.pixelmonmod.pixelmon.storage.PlayerComputerStorage;
import com.pixelmonmod.pixelmon.storage.PlayerStorage;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.text.translation.locale.Locales;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static World world;
    private static Task task;
    public static final UUID ZERO_UUID = new UUID(0, 0);
    public static final Pattern MESSAGE = Pattern.compile("\\[(.+?)]\\(((?:.|\n)+?)\\)");

    public static void initialize() {
        if (task != null) task.cancel();
        world = (net.minecraft.world.World) Sponge.getServer().getWorld(Sponge.getServer().getDefaultWorldName())
                .orElseThrow(() -> new IllegalStateException("No default world."));
        Config.load();
        Manager.fillPool(false, true);
        if (Config.announceInt > 0) {
            task = Task.builder()
                    .execute(t -> {
                        int shinies = 0, legendaries = 0;
                        for (TradeEntry entry : Manager.trades) {
                            if (entry.getPokemon().getIsShiny()) shinies++;
                            if (EnumPokemon.legendaries.contains(entry.getPokemon().getSpecies().name))
                                legendaries++;
                        }
                        Sponge.getServer().getBroadcastChannel().send(WonderTrade.getMessage(Sponge.getServer().getConsole(), "wondertrade.announcement", "pool-size", Config.poolSize, "shinies", shinies, "legendaries", legendaries));
                    })
                    .interval(Config.announceInt, TimeUnit.MILLISECONDS)
                    .submit(WonderTrade.getContainer());
        }
    }

    public static Text toText(String msg) {
        return TextSerializers.FORMATTING_CODE.deserialize(msg);
    }

    public static Text parseText(String message) {
        Matcher matcher = MESSAGE.matcher(message);
        Text.Builder builder = Text.builder();
        int index = 0;
        while (matcher.find()) {
            if (matcher.start() > index) {
                builder.append(toText(message.substring(index, matcher.start())));
            }
            Text.Builder subtext = toText(matcher.group(1)).toBuilder();
            String group = matcher.group(2);
            try {
                subtext.onClick(group.startsWith("/") ? TextActions.runCommand(group) : TextActions.openUrl(new URL(group)));
                subtext.onHover(TextActions.showText(Text.of(group)));
            } catch (MalformedURLException e) {
                subtext.onHover(TextActions.showText(toText(group)));
            }
            builder.append(subtext.build());
            index = matcher.end();
            if (matcher.hitEnd() && index < message.length()) {
                builder.append(toText(message.substring(index)));
            }
        }
        if (index == 0) {
            builder.append(toText(message));
        }
        return builder.toText();
    }

    public static EntityPixelmon createEntityPixelmon(NBTTagCompound nbt, World world) {
        return (EntityPixelmon) PixelmonEntityList.createEntityFromNBT(nbt, world);
    }

    public static PlayerStorage getPartyStorage(Player player) {
        return PixelmonStorage.pokeBallManager.getPlayerStorage((EntityPlayerMP) player).orElseThrow(() -> new IllegalStateException("No player storage."));
    }

    public static PlayerComputerStorage getPcStorage(Player player) {
        return PixelmonStorage.computerManager.getPlayerStorage((EntityPlayerMP) player);
    }

    public static long getCooldown(Player player) {
        try {
            return Integer.parseInt(player.getOption("wondertrade:cooldown").orElse(String.valueOf(Config.defCooldown)));
        } catch (NumberFormatException e) {
            WonderTrade.getLogger().error("Malformatted cooldown option set for player " + player.getName() + ": " + player.getOption("wondertrade:cooldown").orElse(""));
            return Config.defCooldown;
        }
    }

    public static void trade(Player player, int slot) {
        PlayerStorage storage = getPartyStorage(player);
        storage.recallAllPokemon();
        NBTTagCompound nbt = storage.getList()[slot];
        TradeEntry entry = trade(player, nbt);
        storage.removeFromPartyPlayer(slot);
        Pixelmon.EVENT_BUS.post(new PixelmonDeletedEvent((EntityPlayerMP) player, nbt, DeleteType.COMMAND));
        storage.addToParty(entry.getPokemon(), slot);
        Pixelmon.EVENT_BUS.post(new PixelmonReceivedEvent((EntityPlayerMP) player, ReceiveType.Command, entry.getPokemon()));
    }

    public static void trade(Player player, int box, int pos) {
        PlayerComputerStorage storage = getPcStorage(player);
        NBTTagCompound nbt = storage.getBox(box).getNBTByPosition(pos);
        TradeEntry entry = trade(player, nbt);
        storage.getBox(box).changePokemon(pos, entry.getPokemon().serializeNBT());
        Pixelmon.EVENT_BUS.post(new PixelmonDeletedEvent((EntityPlayerMP) player, nbt, DeleteType.COMMAND));
        Pixelmon.EVENT_BUS.post(new PixelmonReceivedEvent((EntityPlayerMP) player, ReceiveType.Command, entry.getPokemon()));
    }

    private static TradeEntry trade(Player player, NBTTagCompound nbt) {
        EntityPixelmon pokemon = createEntityPixelmon(nbt, (World) player.getWorld());
        Preconditions.checkArgument(Config.allowEggs || !pokemon.isEgg, WonderTrade.getMessage(player.getLocale(), "wondertrade.trade.no-eggs"));
        TradeEntry entry = new TradeEntry(pokemon, player.getUniqueId(), LocalDateTime.now());
        logTransaction(player, entry, true);
        entry = Manager.trade(entry).refine(player);
        logTransaction(player, entry, false);
        Object[] args = new Object[] {"player", player.getName(), "traded", getShortDesc(pokemon), "traded-details", getDesc(pokemon), "received", getShortDesc(entry.getPokemon()), "received-details", getDesc(entry.getPokemon())};
        if (pokemon.getIsShiny() || EnumPokemon.legendaries.contains(pokemon.getSpecies().name)) {
            Sponge.getServer().getBroadcastChannel().send(WonderTrade.getPrefix().concat(parseText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.trade.success.broadcast", args).toString())));
        } else {
            player.sendMessage(WonderTrade.getPrefix().concat(parseText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.trade.success.message", args).toString())));
        }
        return entry;
    }

   public static void take(Player player, int index) {
        PlayerStorage storage = getPartyStorage(player);
        storage.recallAllPokemon();
        TradeEntry entry = Manager.take(index).refine(player);
        logTransaction(player, entry, false);
        storage.addToParty(entry.getPokemon());
    }

    public static void logTransaction(User user, TradeEntry entry, boolean add) {
        WonderTrade.getLogger().info(user.getName() + (add ? " added " : " removed ") + " a " + getShortDesc(entry.getPokemon()) + ".");
    }

    public static String getShortDesc(EntityPixelmon pokemon) {
        return pokemon.isEgg ? "mysterious egg" : "level " + pokemon.getLvl().getLevel() + (pokemon.getIsShiny() ? " shiny " : " ") + pokemon.getSpecies().name;
    }

    public static String getDesc(EntityPixelmon pokemon) {
        if (pokemon.isEgg) {
            return "&3Pokemon: &9???";
        }
        StringBuilder builder = new StringBuilder("&3Pokemon: &9").append(pokemon.getSpecies().name);
        if (pokemon.getItemHeld() != NoItem.noItem) {
            builder.append("\n&3Held Item: &9").append(pokemon.getItemHeld().getUnlocalizedName());
        }
        builder.append("\n&3Ability: &9").append(pokemon.getAbility().getName())
                .append("\n&3Level: &9").append(pokemon.getLvl().getLevel())
                .append("\n&3Shiny: &9").append(pokemon.getIsShiny())
                .append("\n&3EVs: &9")
                    .append(pokemon.stats.evs.hp).append("&3/&9")
                    .append(pokemon.stats.evs.attack).append("&3/&9")
                    .append(pokemon.stats.evs.defence).append("&3/&9")
                    .append(pokemon.stats.evs.specialAttack).append("&3/&9")
                    .append(pokemon.stats.evs.specialDefence).append("&3/&9")
                    .append(pokemon.stats.evs.speed)
                .append("\n&3IVs: &9")
                    .append(pokemon.stats.ivs.HP).append("&3/&9")
                    .append(pokemon.stats.ivs.Attack).append("&3/&9")
                    .append(pokemon.stats.ivs.Defence).append("&3/&9")
                    .append(pokemon.stats.ivs.SpAtt).append("&3/&9")
                    .append(pokemon.stats.ivs.SpDef).append("&3/&9")
                    .append(pokemon.stats.ivs.Speed);
        return builder.toString();
    }

    public static World getWorld() {
        return world;
    }

}