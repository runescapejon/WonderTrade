package com.mcsimonflash.sponge.wondertrade.internal;

import com.google.common.base.Preconditions;
import com.mcsimonflash.sponge.wondertrade.WonderTrade;
import com.mcsimonflash.sponge.wondertrade.data.TradeEntry;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PartyStorage;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityPixelmon;
import com.pixelmonmod.pixelmon.enums.EnumSpecies;
import com.pixelmonmod.pixelmon.enums.forms.EnumNoForm;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;
import net.minecraft.item.ItemStack;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
	public static final UUID ZERO_UUID = new UUID(0, 0);
	public static final Pattern MESSAGE = Pattern.compile("\\[(.+?)]\\(((?:.|\n)+?)\\)");
	private static Task task;

	public static void initialize() {
		if (task != null) {
			task.cancel();
		}
		Config.load();
		Manager.fillPool(false, true);
		if (Config.announceInt > 0) {
			task = Task.builder().execute(t -> {
				int shinies = 0, legendaries = 0, ultrabeasts = 0;
				for (TradeEntry entry : Manager.trades) {
					if (entry.getPokemon().isShiny()) {
						shinies++;
					}
					if (EnumSpecies.legendaries.contains(entry.getPokemon().getSpecies().name)) {
						legendaries++;
					}
					if (EnumSpecies.ultrabeasts.contains(entry.getPokemon().getSpecies().name)) {
						ultrabeasts++;
					}
				}
				Sponge.getServer().getBroadcastChannel()
						.send(WonderTrade.getMessage(Sponge.getServer().getConsole(), "wondertrade.announcement",
								"pool-size", Config.poolSize, "shinies", shinies, "legendaries", legendaries,
								"ultrabeasts", ultrabeasts));
			}).interval(Config.announceInt, TimeUnit.MILLISECONDS).submit(WonderTrade.getContainer());
		}
	}

	public static Optional<URL> parseURL(String url) {
		try {
			return Optional.of(new URL(url));
		} catch (MalformedURLException ignored) {
			return Optional.empty();
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
				subtext.onClick(
						group.startsWith("/") ? TextActions.runCommand(group) : TextActions.openUrl(new URL(group)));
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

	public static long getCooldown(Player player) {
		try {
			return Integer
					.parseInt(player.getOption("wondertrade:cooldown").orElse(String.valueOf(Config.defCooldown)));
		} catch (NumberFormatException e) {
			WonderTrade.getLogger().error("Malformatted cooldown option set for player " + player.getName() + ": "
					+ player.getOption("wondertrade:cooldown").orElse(""));
			return Config.defCooldown;
		}
	}

	public static void recallAllPokemon(PartyStorage storage) {
		for (Pokemon p : storage.getAll()) {
			if (p != null) {
				p.ifEntityExists(EntityPixelmon::retrieve);
			}
		}
	}

	public static void trade(Player player, int slot) {
		PlayerPartyStorage party = Pixelmon.storageManager.getParty(player.getUniqueId());
		recallAllPokemon(party);
		TradeEntry entry = trade(player, party.getAll()[slot]);
		party.set(slot, entry.getPokemon());
	}

	public static void trade(Player player, int box, int pos) {
		PCStorage pc = Pixelmon.storageManager.getPCForPlayer(player.getUniqueId());
		TradeEntry entry = trade(player, pc.get(box, pos));
		pc.set(box, pos, entry.getPokemon());
	}

	private static TradeEntry trade(Player player, Pokemon pokemon) {
		Preconditions.checkArgument(Config.allowEggs || !pokemon.isEgg(),
				WonderTrade.getMessage(player.getLocale(), "wondertrade.trade.no-eggs"));
		Preconditions.checkArgument(Config.allowuntradeable || !pokemon.hasSpecFlag("untradeable"),
				WonderTrade.getMessage(player.getLocale(), "wondertrade.trade.no-untradeable"));
		TradeEntry entry = new TradeEntry(pokemon, player.getUniqueId(), LocalDateTime.now());
		logTransaction(player, entry, true);
		entry = Manager.trade(entry).refine(player);
		logTransaction(player, entry, false);
		entry.getPokemon().getPersistentData().setBoolean(WonderTrade.PluginID, true);
		Object[] args = new Object[] { "player", player.getName(), "traded", getShortDesc(pokemon), "traded-details",
				getDesc(pokemon), "received", getShortDesc(entry.getPokemon()), "received-details",
				getDesc(entry.getPokemon()) };
		if (Config.broadcastTrades
				&& (entry.getPokemon().isShiny() || entry.getPokemon().isLegendary() ||  EnumSpecies.ultrabeasts.contains(entry.getPokemon().getSpecies().name))) {
			Sponge.getServer().getBroadcastChannel()
					.send(Text.of(TextSerializers.FORMATTING_CODE.deserialize(Config.prefix), parseText(WonderTrade
							.getMessage(Locales.DEFAULT, "wondertrade.trade.success.broadcast", args).toString())));
		} else {
			player.sendMessage(Text.of(TextSerializers.FORMATTING_CODE.deserialize(Config.prefix), parseText(
					WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.trade.success.message", args).toString())));
		}
		return entry;
	}

	public static void take(Player player, int index) {
		PlayerPartyStorage party = Pixelmon.storageManager.getParty(player.getUniqueId());
		recallAllPokemon(party);
		TradeEntry entry = Manager.take(index).refine(player);

		logTransaction(player, entry, false);
		party.add(entry.getPokemon());
	}

	public static void logTransaction(User user, TradeEntry entry, boolean add) {
		WonderTrade.getLogger().info(user.getName() + (add ? " added " : " removed ") + " a "
				+ getShortDesc(entry.getPokemon()) + (add ? "." : " (added by " + entry.getOwnerName() + ")."));
	}

	public static String getShortDesc(Pokemon pokemon) {
		return pokemon.isEgg() ? "mysterious egg"
				: "level " + pokemon.getLevel() + (pokemon.isShiny() ? " shiny " : " ")
						+ (EnumSpecies.legendaries.contains(pokemon.getSpecies().name) ? "legendary " : "")
						+ (EnumSpecies.ultrabeasts.contains(pokemon.getSpecies().name) ? "ultra beast " : "")
						+ pokemon.getSpecies().name;
	}

	public static String getDesc(Pokemon pokemon) {
		if (pokemon.isEgg()) {
			return WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.egg.lore").toString();
		}
		StringBuilder builder = new StringBuilder(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.pokemon.lore").toString()).append(pokemon.getSpecies().name);
		if (pokemon.getHeldItem() != ItemStack.EMPTY) {
			builder.append("\n&3Held Item: &9").append(pokemon.getHeldItem().getDisplayName());
		}
		builder.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ability.lore")).append(pokemon.getAbility().getName())
		.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.level.lore")).append(pokemon.getLevel())
		.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.lore"))
		.append(pokemon.getStats().evs.hp).append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.hp.lore"))
				.append(pokemon.getStats().evs.attack).append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.attack.lore"))
				.append(pokemon.getStats().evs.defence).append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.defence.lore"))
				.append(pokemon.getStats().evs.specialAttack).append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.specialattack.lore"))
				.append(pokemon.getStats().evs.specialDefence).append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.specialdefence.lore"))
				.append(pokemon.getStats().evs.speed)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.lore"))
				.append(pokemon.getStats().ivs.hp).append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.hp.lore"))
				.append(pokemon.getStats().ivs.attack).append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.attack.lore"))
				.append(pokemon.getStats().ivs.defence).append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.defence.lore"))
				.append(pokemon.getStats().ivs.specialAttack).append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.specialattack.lore"))
				.append(pokemon.getStats().ivs.specialDefence).append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.specialdefence.lore"))
				.append(pokemon.getStats().ivs.speed);
		if (pokemon.getFormEnum() 
				!= EnumNoForm.NoForm) {
			builder.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.form.lore")).append(pokemon.getFormEnum());
		}
		if (pokemon.isShiny() == true) {
			builder.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.shiny.lore"));
		}
		if (!pokemon.getCustomTexture().isEmpty()) {
			builder.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.customtexture.lore")).append(pokemon.getCustomTexture());
		}

		if (Config.EnableEntityParticle) {
			if (!pokemon.getPersistentData().getString("entity-particles:particle").isEmpty()) {
				builder.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.aura.lore"))
						.append(pokemon.getPersistentData().getString("entity-particles:particle"));
			}

		}

		return builder.toString();
	}
}