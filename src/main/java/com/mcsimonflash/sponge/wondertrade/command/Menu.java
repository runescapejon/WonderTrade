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

@Aliases("menu")
@Permission("wondertrade.command.menu.base")
public class Menu extends Command {
	
	@Inject
	protected Menu(Settings settings) {
		super(settings.usage(CmdUtils.usage("/wondertrade menu", "Opens the WonderTrade menu")));
	}
	
	@Override
	public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
		Player player = CmdUtils.requirePlayer(src);
		Inventory.createMainMenu(player).open(player);
		return CommandResult.success();
	}
	
}