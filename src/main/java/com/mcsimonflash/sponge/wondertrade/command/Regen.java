package com.mcsimonflash.sponge.wondertrade.command;

import com.google.inject.Inject;
import com.mcsimonflash.sponge.teslalibs.argument.Arguments;
import com.mcsimonflash.sponge.teslalibs.command.Aliases;
import com.mcsimonflash.sponge.teslalibs.command.Command;
import com.mcsimonflash.sponge.teslalibs.command.Permission;
import com.mcsimonflash.sponge.wondertrade.WonderTrade;
import com.mcsimonflash.sponge.wondertrade.internal.Manager;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;

@Aliases("regen")
@Permission("wondertrade.command.regen.base")
public class Regen extends Command {

    @Inject
    protected Regen(Settings settings) {
        super(settings.elements(Arguments.booleanObj().optional().toElement("overwrite-players")));
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        boolean overwritePlayers = args.<Boolean>getOne("overwrite-players").orElse(false);
        if (overwritePlayers && !src.hasPermission("wondertrade.command.regen.overwrite-players")) {
            throw new CommandException(WonderTrade.getMessage(src, "wondertrade.command.regen"));
        }
        Manager.fillPool(true, overwritePlayers);
        src.sendMessage(WonderTrade.getMessage(src, "wondertrade.command.regen.success"));
        return CommandResult.success();
    }

}