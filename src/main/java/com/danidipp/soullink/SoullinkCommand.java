package com.danidipp.soullink;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class SoullinkCommand implements CommandExecutor {

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
			@NotNull String[] args) {
		if (args.length != 2) {
			return false;
		}
		var player1 = Bukkit.getOfflinePlayerIfCached(args[0]);
		var player2 = Bukkit.getOfflinePlayerIfCached(args[1]);

		if (player1 == null) {
			sender.sendMessage("Player \"" + args[0] + "\" not found");
			return true;
		}
		if (player2 == null) {
			sender.sendMessage("Player \"" + args[1] + "\" not found");
			return true;
		}

		// Ensure player1 is always the player with the lowest UUID
		if (player1.getUniqueId().compareTo(player2.getUniqueId()) > 0) {
			var temp = player1;
			player1 = player2;
			player2 = temp;
		}
		var uuid1 = player1.getUniqueId();
		var uuid2 = player2.getUniqueId();

		LinkSet link = LinkSet.findPair(uuid1, uuid2);

		Bukkit.getScheduler().runTaskAsynchronously(SoulLink.plugin, () -> {
			LinkSet newLink;
			if (link == null) {
				newLink = SoulLink.plugin.pb.setLinkSync(null, uuid1, uuid2,
						1);
			} else {
				newLink = SoulLink.plugin.pb.setLinkSync(link.id(), uuid1, uuid2, link.level() + 1);
			}

			if (newLink == null) {
				sender.sendMessage(
						Component.text("Failed to create Soul Link. Check console for details!", NamedTextColor.RED));
			} else {
				sender.sendMessage(Component.text("Soul Link created for ", NamedTextColor.GREEN)
						.append(Component.text(args[0], NamedTextColor.AQUA))
						.append(Component.text(" and ", NamedTextColor.GREEN))
						.append(Component.text(args[1], NamedTextColor.AQUA)));
			}
		});
		return true;
	}

}
