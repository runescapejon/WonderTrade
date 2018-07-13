package com.mcsimonflash.sponge.wondertrade.internal;

import com.mcsimonflash.sponge.wondertrade.data.TradeEntry;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonSpec;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityPixelmon;
import com.pixelmonmod.pixelmon.enums.EnumPokemon;

import java.time.LocalDateTime;
import java.util.Random;

public class Manager {

    static TradeEntry[] trades;
    private static final Random RANDOM = new Random();

    public static TradeEntry trade(TradeEntry entry) {
        int index = RANDOM.nextInt(trades.length);
        TradeEntry ret = trades[index];
        trades[index] = entry;
        Config.saveTrade(index);
        return ret;
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

    private static EntityPixelmon genRandomPixelmon() {
        EnumPokemon type = Config.legendRate != 0 && RANDOM.nextInt(Config.legendRate) == 0 ? EnumPokemon.LEGENDARY_ENUMS[RANDOM.nextInt(EnumPokemon.LEGENDARY_ENUMS.length)] : EnumPokemon.randomPoke(false);
        PokemonSpec spec = PokemonSpec.from(type.name);
        spec.level = RANDOM.nextInt(Config.maxLvl - Config.minLvl) + Config.minLvl;
        spec.shiny = Config.shinyRate != 0 && RANDOM.nextInt(Config.shinyRate) == 0;
        return spec.create(Utils.getWorld());
    }

}