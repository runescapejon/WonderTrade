package com.mcsimonflash.sponge.wondertrade;

import com.google.inject.Inject;
import com.mcsimonflash.sponge.wondertrade.command.Base;
import com.mcsimonflash.sponge.wondertrade.command.Menu;
import com.mcsimonflash.sponge.wondertrade.internal.Config;
import com.mcsimonflash.sponge.wondertrade.internal.Utils;

import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartingServerEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

@Plugin(id = WonderTrade.PluginID, name = "WonderTradePlus", version = "1.1.6", description = "Lets you do spicy thing with ur beloved pokémon.", dependencies = @Dependency(id = "pixelmon", version = "8.0.0"), authors = {
		"Simon_Flash", "happyzleaf", "runescapejon" })
public class WonderTrade {

	public static final String PluginID = "wondertradeplus";
	private static WonderTrade instance;
	private static PluginContainer container;
	private static Logger logger;
	private static com.mcsimonflash.sponge.teslalibs.command.CommandService commands;
	private static Path directory;
	private static com.mcsimonflash.sponge.teslalibs.message.MessageService messages;
	private static Text prefix;

	@Inject
	public WonderTrade(PluginContainer c) {
		instance = this;
		container = c;
		logger = container.getLogger();
		commands = com.mcsimonflash.sponge.teslalibs.command.CommandService.of(container);
		directory = Sponge.getConfigManager().getPluginConfig(container).getDirectory();
		Path translations = directory.resolve("translations");
		try {
			container.getAsset("messages.properties").get().copyToDirectory(translations);
			messages = com.mcsimonflash.sponge.teslalibs.message.MessageService.of(translations, "messages");
		} catch (IOException e) {
			logger.error("An error occurred initializing message translations. Using internal copies.");
			messages = com.mcsimonflash.sponge.teslalibs.message.MessageService.of(container, "messages");
		}

	}

	public static WonderTrade getInstance() {
		return instance;
	}

	public static PluginContainer getContainer() {
		return container;
	}

	public static Logger getLogger() {
		return logger;
	}

	public static Path getDirectory() {
		return directory;
	}

	public static Text getPrefix() {
		return prefix;
	}

	public static com.mcsimonflash.sponge.teslalibs.message.Message getMessage(Locale locale, String key,
			Object... args) {
		return messages.get(key, locale).args(args);
	}

	public static Text getMessage(CommandSource src, String key, Object... args) {
		return Text.of(TextSerializers.FORMATTING_CODE.deserialize(Config.prefix),
				getMessage(src.getLocale(), key, args).toText());
	}

	@Listener
	public void onStart(GameStartingServerEvent event) {
		commands.register(Base.class);
		Sponge.getCommandManager().register(container, commands.getInstance(Menu.class).getSpec(), "wt");
		Utils.initialize();

	}
 

	@Listener
	public void onReload(GameReloadEvent event) {
		messages.reload();
		Utils.initialize();
	}

}