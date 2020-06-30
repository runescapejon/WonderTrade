package com.mcsimonflash.sponge.wondertrade.command;

import com.google.inject.Inject;
import com.mcsimonflash.sponge.teslalibs.command.Aliases;
import com.mcsimonflash.sponge.teslalibs.command.Command;
import com.mcsimonflash.sponge.teslalibs.command.Permission;
import com.mcsimonflash.sponge.wondertrade.internal.Inventory;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.entity.living.player.Player;

@Aliases("pool")
@Permission("wondertrade.command.pool.base")
public class Pool extends Command {

	@Inject
	protected Pool(Settings settings) {
		super(settings.usage(CmdUtils.usage("/wondertrade pool", "Opens the WonderTrade pool")));
	}

	@Override
	public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
		Player player = CmdUtils.requirePlayer(src);
		Inventory.createPoolMenu(src.hasPermission("wondertrade.command.pool.take")).open(player);
		return CommandResult.success();
	}

}
