package com.mcsimonflash.sponge.wondertrade.internal;

import com.mcsimonflash.sponge.wondertrade.data.TradeEntry;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonSpec;
import com.pixelmonmod.pixelmon.enums.EnumSpecies;
import net.minecraft.util.Tuple;

import java.time.LocalDateTime;
import java.util.Random;

public class Manager {

	private static final Random RANDOM = new Random();
	static TradeEntry[] trades;

	public static TradeEntry trade(TradeEntry entry, Tuple<Integer, TradeEntry> tuple) {
		trades[tuple.getFirst()] = entry;
		Config.saveTrade(tuple.getFirst());
		return tuple.getSecond();
	}

	/***
	 *
	 * Get the index and TradeEntry for the possible trade, if event is posted, trade above.
	 * @return Tuple
	 */
	public static Tuple<Integer, TradeEntry> getPossibleTrade() {
		int index = RANDOM.nextInt(trades.length);
		TradeEntry ret = trades[index];
		return new Tuple<>(index, ret);
	}

	public static TradeEntry take(int index) {
		TradeEntry entry = trades[index];
		trades[index] = new TradeEntry(genRandomPixelmon(), Utils.ZERO_UUID, LocalDateTime.now());
		Config.saveTrade(index);
		return entry;
	}

	public static void fillPool(boolean overwrite, boolean overwritePlayers) {
		for (int i = 0; i < trades.length; i++) {
			if (trades[i] == null || overwrite && (overwritePlayers || trades[i].getOwner().equals(Utils.ZERO_UUID))) {
				trades[i] = new TradeEntry(genRandomPixelmon(), Utils.ZERO_UUID, LocalDateTime.now());
			}
		}
		Config.saveAll();
	}

	private static Pokemon genRandomPixelmon() {
		EnumSpecies type = Config.legendRate != 0 && RANDOM.nextInt(Config.legendRate) == 0
				? EnumSpecies.LEGENDARY_ENUMS[RANDOM.nextInt(EnumSpecies.LEGENDARY_ENUMS.length)]
				: EnumSpecies.randomPoke(false);
		PokemonSpec spec = PokemonSpec.from(type.name);
		spec.level = RANDOM.nextInt(Config.maxLvl - Config.minLvl) + Config.minLvl;
		spec.shiny = Config.shinyRate != 0 && RANDOM.nextInt(Config.shinyRate) == 0;
		Pokemon p = spec.create();
	//made a small workaround to EnumSpecies#RandomPoke that was generating in pool regardless of config set.
		//this should prevent it and set it to Pidgey a normal type pokemon 
		if (p.getSpecies().isUltraBeast()) {
			if (Config.allowultrabeast == false) {
				p.setSpecies(EnumSpecies.Pidgey);
			}
		}
		if (p.isPokemon(EnumSpecies.Ditto)) {
			if (Config.allowDitto == false) {
				p.setSpecies(EnumSpecies.Pidgey);
			}
		}
		return p;

	}
}