package com.mcsimonflash.sponge.wondertrade.data;

import com.mcsimonflash.sponge.wondertrade.internal.Utils;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityPixelmon;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.user.UserStorageService;

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

    public String getOwnerName() {
        return owner.equals(Utils.ZERO_UUID) ? "Server" : Sponge.getServiceManager().provideUnchecked(UserStorageService.class).get(owner).map(User::getName).orElse(owner.toString());
    }

    public TradeEntry refine(User user) {
        if (owner.equals(Utils.ZERO_UUID)) {
            pokemon.setOwnerId(user.getUniqueId());
        }
        return this;
    }

}