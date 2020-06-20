package com.mcsimonflash.sponge.wondertrade.command;

import com.google.inject.Inject;
import com.mcsimonflash.sponge.teslalibs.command.Aliases;
import com.mcsimonflash.sponge.teslalibs.command.Children;
import com.mcsimonflash.sponge.teslalibs.command.Command;
import com.mcsimonflash.sponge.teslalibs.command.Permission;
import com.mcsimonflash.sponge.wondertrade.WonderTrade;
import com.mcsimonflash.sponge.wondertrade.internal.Utils;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Aliases({"wondertrade", "wtrade"})
@Permission("wondertrade.command.base")
@Children({Menu.class, Pool.class, Regen.class, Take.class, Trade.class})
public class Base extends Command {
	
	private static final Text LINKS = Text.of("                       ", CmdUtils.link("Ore Project", Utils.parseURL("https://ore.spongepowerd.org/Simon_Flash/WonderTrade")), TextColors.GRAY, " | ", CmdUtils.link("Support Discord", Utils.parseURL("https://discord.gg/4wayq37")));
	
	@Inject
	protected Base(Settings settings) {
		super(settings.usage(CmdUtils.usage("/wondertrade", "The base command for WonderTrade")));
	}
	
	@Override
	public CommandResult execute(CommandSource src, CommandContext args) {
		PaginationList.builder()
				.title(WonderTrade.getPrefix())
				.padding(Utils.toText("&7="))
				.contents(Stream.concat(Stream.of(getUsage()), ((Command) this).getChildren().stream().filter(c -> c.getSpec().testPermission(src)).map(Command::getUsage)).collect(Collectors.toList()))
				.footer(LINKS)
				.sendTo(src);
		return CommandResult.success();
	}
	
}