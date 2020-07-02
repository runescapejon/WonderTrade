package com.mcsimonflash.sponge.wondertrade.internal;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.DecimalFormat;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.text.translation.locale.Locales;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.mcsimonflash.sponge.wondertrade.WonderTrade;
import com.mcsimonflash.sponge.wondertrade.data.TradeEntry;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.PartyStorage;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityPixelmon;
import com.pixelmonmod.pixelmon.entities.pixelmon.stats.Gender;
import com.pixelmonmod.pixelmon.enums.EnumNature;
import com.pixelmonmod.pixelmon.enums.EnumSpecies;
import com.pixelmonmod.pixelmon.enums.forms.EnumNoForm;
import com.pixelmonmod.pixelmon.storage.PlayerPartyStorage;

import net.minecraft.item.ItemStack;

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
		Preconditions.checkArgument(Config.allowultrabeast || !pokemon.getSpecies().isUltraBeast(),
				WonderTrade.getMessage(player.getLocale(), "wondertrade.trade.no-ultrabeast"));
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
				gethover(pokemon), "received", getShortDesc(entry.getPokemon()), "received-details",
				gethover(entry.getPokemon()) };
		if (Config.broadcastTrades && (entry.getPokemon().isShiny() || entry.getPokemon().isLegendary()
				|| EnumSpecies.ultrabeasts.contains(entry.getPokemon().getSpecies().name))) {
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
		return pokemon.isEgg() ? WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.mysteriousegg").toString()
				: "level " + pokemon.getLevel()
						+ (pokemon.isShiny() ? WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.shiny").toString()
								: " ")
						+ (EnumSpecies.legendaries.contains(pokemon.getSpecies().name)
								? WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.legendary").toString() + " "
								: "")
						+ (EnumSpecies.ultrabeasts.contains(pokemon.getSpecies().name)
								? WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ultrabeast").toString() + " "
								: "")
						+ pokemon.getSpecies().name;
	}

	// ugh okay this is why i did it here and it's a effective. I'd moved everything
	// to List<Test>.
	// This will prevent Lores from glitching from the use of \n which cause some
	// clientside issues once and if the client stretch out will cause invalid
	// characters.
	// Also, that it's not supported or recommended in Sponge to use \n.

	// Update comment: GetHover(pokemon) pretty much putting it back because it
	// broke the regex pattern for hover actions, and since i didn't really study
	// under regex I'm going to put it back and add some fixes here.
	// Also, figure it's the best to separated between Hover actions and Item Lores.
	// Well that is the most important that things will run better on it's own
	// instead of trying to merge them causing weird clientsided issues, especially
	// with \n.
	public static String gethover(Pokemon pokemon) {
		DecimalFormat dformat = new DecimalFormat("#0.##");
		if (pokemon.isEgg()) {
			return WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.egg.lore").toString();
		}
		StringBuilder builder = new StringBuilder(
				WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.pokemon.lore").toString())
						.append(pokemon.getSpecies().name);
		if (pokemon.getHeldItem() != ItemStack.EMPTY) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.helditem.lore"))
					.append(pokemon.getHeldItem().getDisplayName());
		}
		builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ability.lore"))
				.append(pokemon.getAbility().getName()).append("\n")
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.level.lore")).append(pokemon.getLevel());

		builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.lore"))
				.append(pokemon.getStats().evs.hp)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.hp.lore"))
				.append(pokemon.getStats().evs.attack)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.attack.lore"))
				.append(pokemon.getStats().evs.defence)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.defence.lore"))
				.append(pokemon.getStats().evs.specialAttack)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.specialattack.lore"))
				.append(pokemon.getStats().evs.specialDefence)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.specialdefence.lore"))
				.append(pokemon.getStats().evs.speed)
				.append(" " + WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.total.lore").toString()
						.replace("%totalev%", String.valueOf(" " + dformat.format(totalEVs(pokemon) / 510.0 * 100))))
				.append("\n");
		builder.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.lore")).append(pokemon.getStats().ivs.hp)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.hp.lore"))
				.append(pokemon.getStats().ivs.attack)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.attack.lore"))
				.append(pokemon.getStats().ivs.defence)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.defence.lore"))
				.append(pokemon.getStats().ivs.specialAttack)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.specialattack.lore"))
				.append(pokemon.getStats().ivs.specialDefence)
				.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.specialdefence.lore"))
				.append(pokemon.getStats().ivs.speed)
				.append(" " + WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.total.lore").toString()
						.replace("%totaliv%", String.valueOf(" " + dformat.format(totalIVs(pokemon) / 186.0 * 100))));

		if (pokemon.getGender().equals(Gender.Female)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.gender.female.lore"));
		}
		if (pokemon.getGender().equals(Gender.Male)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.gender.male.lore"));
		}
		if (pokemon.getNature().equals(EnumNature.Adamant)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.adamant"));
		}
		if (pokemon.getNature().equals(EnumNature.Bashful)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.bashful"));
		}
		if (pokemon.getNature().equals(EnumNature.Bold)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.bold"));
		}
		if (pokemon.getNature().equals(EnumNature.Brave)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.brave"));
		}
		if (pokemon.getNature().equals(EnumNature.Calm)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.calm"));
		}
		if (pokemon.getNature().equals(EnumNature.Careful)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.careful"));
		}
		if (pokemon.getNature().equals(EnumNature.Docile)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.docile"));
		}
		if (pokemon.getNature().equals(EnumNature.Gentle)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.gentle"));
		}
		if (pokemon.getNature().equals(EnumNature.Hardy)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.hardy"));
		}
		if (pokemon.getNature().equals(EnumNature.Hasty)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.hasty"));
		}
		if (pokemon.getNature().equals(EnumNature.Impish)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.impish"));
		}
		if (pokemon.getNature().equals(EnumNature.Jolly)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.jolly"));
		}
		if (pokemon.getNature().equals(EnumNature.Lax)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.lax"));
		}
		if (pokemon.getNature().equals(EnumNature.Lonely)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.lonely"));
		}
		if (pokemon.getNature().equals(EnumNature.Mild)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.mild"));
		}
		if (pokemon.getNature().equals(EnumNature.Modest)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.modest"));
		}
		if (pokemon.getNature().equals(EnumNature.Naive)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.naive"));
		}
		if (pokemon.getNature().equals(EnumNature.Naughty)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.naughty"));
		}
		if (pokemon.getNature().equals(EnumNature.Quiet)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.quiet"));
		}
		if (pokemon.getNature().equals(EnumNature.Quirky)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.quirky"));
		}
		if (pokemon.getNature().equals(EnumNature.Rash)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.rash"));
		}
		if (pokemon.getNature().equals(EnumNature.Relaxed)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.relaxed"));
		}
		if (pokemon.getNature().equals(EnumNature.Sassy)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.sassy"));
		}
		if (pokemon.getNature().equals(EnumNature.Serious)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.serious"));
		}
		if (pokemon.getNature().equals(EnumNature.Timid)) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature"))
					.append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.timid").toString());
		}
		if (pokemon.getFormEnum() != EnumNoForm.NoForm) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.form.lore")
					+ String.valueOf(pokemon.getFormEnum()));
		}
		if (pokemon.isShiny() == true) {
			builder.append("\n").append((WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.shiny.lore")));
		}
		if (!pokemon.getCustomTexture().isEmpty()) {
			builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.customtexture.lore")
					+ pokemon.getCustomTexture());
		}

		if (Config.EnableEntityParticle) {
			if (!pokemon.getPersistentData().getString("entity-particles:particle").isEmpty()) {
				builder.append("\n").append(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.aura.lore")
						+ pokemon.getPersistentData().getString("entity-particles:particle"));
			}
		}
		return builder.toString();
	}

	public static List<Text> getLore(Pokemon pokemon) {
		List<Text> lore = Lists.newArrayList();
		DecimalFormat dformat = new DecimalFormat("#0.##");
		if (pokemon.isEgg()) {
			lore.add(Text.of(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.egg.lore").toString()));
			return lore;
		}

		lore.add(Text.of(TextSerializers.FORMATTING_CODE
				.deserialize(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.pokemon.lore").toString()
						+ pokemon.getSpecies().name)));
		if (pokemon.getHeldItem() != ItemStack.EMPTY) {
			lore.add(Text.of(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.helditem.lore")
					+ pokemon.getHeldItem().getDisplayName()));
		}

		lore.add(Text.of(TextSerializers.FORMATTING_CODE
				.deserialize(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ability.lore").toString()
						+ pokemon.getAbility().getName())));
		if (pokemon.getGender().equals(Gender.Female)) {
			lore.add(Text.of(TextSerializers.FORMATTING_CODE.deserialize(
					WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.gender.female.lore").toString())));
		}
		if (pokemon.getGender().equals(Gender.Male)) {
			lore.add(Text.of(TextSerializers.FORMATTING_CODE
					.deserialize(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.gender.male.lore").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Adamant)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.adamant").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Bashful)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.bashful").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Bold)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.bold").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Brave)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.brave").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Calm)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.calm").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Careful)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.careful").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Docile)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.docile").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Gentle)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.gentle").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Hardy)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.hardy").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Hasty)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.hasty").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Impish)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.impish").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Jolly)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.jolly").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Lax)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.lax").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Lonely)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.lonely").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Mild)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.mild").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Modest)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.modest").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Naive)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.naive").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Naughty)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.naughty").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Quiet)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.quiet").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Quirky)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.quirky").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Rash)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.rash").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Relaxed)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.relaxed").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Sassy)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.sassy").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Serious)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.serious").toString())));
		}
		if (pokemon.getNature().equals(EnumNature.Timid)) {
			lore.add(Text.of(toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature").toString()),
					toText(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.nature.timid").toString())));
		}
		lore.add(Text.of(TextSerializers.FORMATTING_CODE
				.deserialize(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.level.lore").toString()
						+ String.valueOf(pokemon.getLevel()))));
		lore.add(Text.of(TextSerializers.FORMATTING_CODE
				.deserialize(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.lore").toString() + " "
						+ String.valueOf(pokemon.getStats().evs.hp)
						+ WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.hp.lore")
						+ String.valueOf(pokemon.getStats().evs.attack)
						+ WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.attack.lore")
						+ String.valueOf(pokemon.getStats().evs.defence)
						+ WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.defence.lore")
						+ String.valueOf(pokemon.getStats().evs.specialAttack)
						+ WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.specialattack.lore")
						+ String.valueOf(pokemon.getStats().evs.specialDefence)
						+ WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.specialdefence.lore")
						+ String.valueOf(pokemon.getStats().evs.speed) + " "
						+ WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.ev.total.lore").toString().replace(
								"%totalev%", String.valueOf(" " + dformat.format(totalEVs(pokemon) / 510.0 * 100))))));
		lore.add(Text.of(TextSerializers.FORMATTING_CODE
				.deserialize(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.lore")
						+ String.valueOf(pokemon.getStats().ivs.hp)
						+ WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.hp.lore")
						+ String.valueOf(pokemon.getStats().ivs.attack)
						+ (WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.attack.lore"))
						+ String.valueOf(pokemon.getStats().ivs.defence)
						+ (WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.defence.lore"))
						+ String.valueOf(pokemon.getStats().ivs.specialAttack)
						+ (WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.specialattack.lore"))
						+ String.valueOf(pokemon.getStats().ivs.specialDefence)
						+ (WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.specialdefence.lore"))
						+ String.valueOf(pokemon.getStats().ivs.speed) + " "
						+ WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.iv.total.lore").toString().replace(
								"%totaliv%", String.valueOf(" " + dformat.format(totalIVs(pokemon) / 186.0 * 100))))));
		if (pokemon.getFormEnum() != EnumNoForm.NoForm) {
			lore.add(Text.of(TextSerializers.FORMATTING_CODE
					.deserialize(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.form.lore")
							+ String.valueOf(pokemon.getFormEnum()))));
		}
		if (pokemon.isShiny() == true) {
			lore.add(Text.of(TextSerializers.FORMATTING_CODE
					.deserialize(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.shiny.lore").toString())));
		}
		if (!pokemon.getCustomTexture().isEmpty()) {
			lore.add(Text.of(TextSerializers.FORMATTING_CODE
					.deserialize(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.customtexture.lore")
							+ pokemon.getCustomTexture())));
		}

		if (Config.EnableEntityParticle) {
			if (!pokemon.getPersistentData().getString("entity-particles:particle").isEmpty()) {
				lore.add(Text.of(TextSerializers.FORMATTING_CODE
						.deserialize(WonderTrade.getMessage(Locales.DEFAULT, "wondertrade.aura.lore")
								+ pokemon.getPersistentData().getString("entity-particles:particle"))));
			}

		}

		return lore;
	}

	private static double totalEVs(Pokemon p) {
		return p.getStats().evs.hp + p.getStats().evs.attack + p.getStats().evs.defence + p.getStats().evs.specialAttack
				+ p.getStats().evs.specialDefence + p.getStats().evs.speed;
	}

	private static double totalIVs(Pokemon p) {
		return p.getStats().ivs.hp + p.getStats().ivs.attack + p.getStats().ivs.defence + p.getStats().ivs.specialAttack
				+ p.getStats().ivs.specialDefence + p.getStats().ivs.speed;
	}

}