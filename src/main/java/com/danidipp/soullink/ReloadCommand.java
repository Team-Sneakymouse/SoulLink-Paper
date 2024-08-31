package com.danidipp.soullink;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length != 0) {
			return false;
		}
		SoulLink.plugin.reloadConfig();
		sender.sendMessage("Configuration reloaded!");
		return true;
	}
}
