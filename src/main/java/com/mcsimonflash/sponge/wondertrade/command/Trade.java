package com.mcsimonflash.sponge.wondertrade.command;

import com.google.common.collect.Range;
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

@Aliases("trade")
@Permission("wondertrade.command.trade.base")
public class Trade extends Command {

	@Inject
	protected Trade(Settings settings) {
		super(settings
				.usage(CmdUtils.usage("/wondertrade trade ", "WonderTrade's a Pokemon",
						CmdUtils.arg(false, "player", "The player WonderTrading (defaults to the sender, if possible)"),
						CmdUtils.arg(true, "slot", "The slot of the Pokemon to WonderTrade (1-6)")))
				.elements(Arguments.player().orSource().toElement("player"),
						Arguments.intObj().inRange(Range.closed(1, 6)).toElement("slot")));
	}

	@Override
	public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
		Player player = args.<Player>getOne("player").get();
		int slot = args.<Integer>getOne("slot").get();
		if (player != src) {
			if (!src.hasPermission("wondertrade.command.trade.other")) {
				throw new CommandException(WonderTrade.getMessage(src, "wondertrade.command.trade.self-only"));
			}
		} else if (Config.defCooldown > 0 && !player.hasPermission("wondertrade.trade.no-defCooldown")) {
			long time = Utils.getCooldown(player)
					- (System.currentTimeMillis() - Config.getCooldown(player.getUniqueId()));
			if (time > 0) {
				throw new CommandException(
						WonderTrade.getMessage(src, "wondertrade.trade.cooldown", "time", time / 1000));
			} else if (!Config.resetCooldown(player.getUniqueId())) {
				throw new CommandException(WonderTrade.getMessage(src, "wondertrade.trade.reset-cooldown.failure"));
			}
		}
		try {
			Utils.trade(player, slot - 1);
			return CommandResult.success();
		} catch (IllegalArgumentException e) {
			throw new CommandException(WonderTrade.getPrefix().concat(Utils.toText(e.getMessage())));
		}
	}

}
