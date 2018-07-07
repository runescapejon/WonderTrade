package com.mcsimonflash.sponge.wondertrade.data;

import com.mcsimonflash.sponge.wondertrade.internal.Utils;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityPixelmon;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class TradeEntry {

    private final EntityPixelmon pokemon;
    private final UUID owner;
    private final LocalDateTime date;

    public TradeEntry(EntityPixelmon pokemon, UUID owner, LocalDateTime date) {
        this.pokemon = pokemon;
        this.owner = owner;
        this.date = date;
    }

    public EntityPixelmon getPokemon() {
        return pokemon;
    }
    public UUID getOwner() {
        return owner;
    }
    public LocalDateTime getDate() {
        return date;
    }

    public TradeEntry refine(User user) {
        if (owner.equals(Utils.ZERO_UUID)) {
            pokemon.setOwnerId(user.getUniqueId());
        }
        return this;
    }

}