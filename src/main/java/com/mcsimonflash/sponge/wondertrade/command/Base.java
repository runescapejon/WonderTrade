package com.mcsimonflash.sponge.wondertrade.command;

import com.google.inject.Inject;
import com.mcsimonflash.sponge.teslalibs.command.Aliases;
import com.mcsimonflash.sponge.teslalibs.command.Children;
import com.mcsimonflash.sponge.teslalibs.command.Command;
import com.mcsimonflash.sponge.teslalibs.command.Permission;
import com.mcsimonflash.sponge.wondertrade.WonderTrade;
import com.mcsimonflash.sponge.wondertrade.internal.Inventory;
import com.mcsimonflash.sponge.wondertrade.internal.Utils;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.entity.living.player.Player;

@Aliases({"wondertrade", "wt"})
@Permission("wondertrade.command.base")
@Children({Pool.class, Take.class, Trade.class})
public class Base extends Command {

    @Inject
    protected Base(Settings settings) {
        super(settings);
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        if (!(src instanceof Player)) {
            throw new CommandException(WonderTrade.getMessage(src, "wondertrade.command.player-only"));
        }
        Inventory.createMainMenu((Player) src).open((Player) src);
        return CommandResult.success();
    }

}