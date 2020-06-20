package com.mcsimonflash.sponge.wondertrade.command;

import com.google.inject.Inject;
import com.mcsimonflash.sponge.teslalibs.argument.Arguments;
import com.mcsimonflash.sponge.teslalibs.command.Aliases;
import com.mcsimonflash.sponge.teslalibs.command.Command;
import com.mcsimonflash.sponge.teslalibs.command.Permission;
import com.mcsimonflash.sponge.wondertrade.WonderTrade;
import com.mcsimonflash.sponge.wondertrade.internal.Config;
import com.mcsimonflash.sponge.wondertrade.internal.Utils;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.entity.living.player.Player;

@Aliases("take")
@Permission("wondertrade.command.take.base")
public class Take extends Command {
	
	@Inject
	protected Take(Settings settings) {
		super(settings.usage(CmdUtils.usage("/wondertrade take ", "Takes a Pokemon from the pool.", CmdUtils.arg(true, "index", "The index to remove in the range 1 to the pool size.")))
				.elements(Arguments.intObj().toElement("index")));
	}
	
	@Override
	public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
		Player player = CmdUtils.requirePlayer(src);
		int index = args.<Integer>getOne("index").get();
		if (index <= 0 || index > Config.poolSize) {
			throw new CommandException(WonderTrade.getMessage(src, "wondertrade.command.take.invalid-index", "min", 1, "max", Config.poolSize));
		}
		Utils.take(player, index - 1);
		return CommandResult.success();
	}
	
}
