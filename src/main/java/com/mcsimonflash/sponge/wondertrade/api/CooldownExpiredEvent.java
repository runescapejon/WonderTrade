package com.mcsimonflash.sponge.wondertrade.api;

import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;

/**
 * an event when a player cooldown is expired.
 */
public class CooldownExpiredEvent extends AbstractEvent {
	// Side note for me plz ignore
	// https://docs.spongepowered.org/stable/en/plugin/event/custom.html
	// the link above I learn how to start to create an event. From there I had
	// tested this event it wouldn't work properly in the cause.
	// I then learn that it was due to how i did the cause originally it was acting
	// a bit strange, I had it like
	// Sponge.getCauseStackManager().getCurrentCause();
	// Which is sort of wrong I believe, I don't know what went wrong honestly. Anyways Then i continue
	// to read after creating an event that i need to post it to fire this event to
	// ensure that the information is being send properly

	private Player player;
	private Cause cause;

	public CooldownExpiredEvent(Player player, Cause cause) {
		super();
		this.cause = cause;
		this.player = player;
	}

	// Get the cause of event
	@Override
	public Cause getCause() {
		// return Sponge.getCauseStackManager().getCurrentCause();
		return this.cause;
	}

	// Get the player of event
	public Player getPlayer() {
		return this.player;
	}
}
