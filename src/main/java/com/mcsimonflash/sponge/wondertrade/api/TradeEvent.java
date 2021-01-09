package com.mcsimonflash.sponge.wondertrade.api;

import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Cancellable;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;

import com.mcsimonflash.sponge.wondertrade.data.TradeEntry;

/**
 * an event that grab information of a player trade during the wondertrade
 * trading system.
 */
public class TradeEvent extends AbstractEvent implements Cancellable {
	private Player player;
	private Cause cause;
	private TradeEntry ReceivePokemon;
	private TradeEntry SendPokemon;
	private boolean cancelled;

	public TradeEvent(Player player, TradeEntry ReceivePokemon, TradeEntry SendPokemon, Cause cause) {
		super();
		this.cause = cause;
		this.player = player;
		this.SendPokemon = SendPokemon;
		this.ReceivePokemon = ReceivePokemon;
	}

	// Get the player of event
	public Player getPlayer() {
		return this.player;
	}

	// get the input trade
	public TradeEntry getReceivePokemon() {
		return this.ReceivePokemon;
	}

	// get the output trade pretty much grabbing the pokemon that the player is sending to the wondertrade
	public TradeEntry getSendPokemon() {
		return this.SendPokemon;
	}	
	
	// Get the cause of event
	@Override
	public Cause getCause() {
		return this.cause;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}
}
