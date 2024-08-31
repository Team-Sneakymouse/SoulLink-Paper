package com.danidipp.soullink;

import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;

public class SoulLinkExpansion extends PlaceholderExpansion implements Relational {
	private final SoulLink plugin;

	public SoulLinkExpansion(SoulLink plugin) {
		this.plugin = plugin;
	}

	@Override
	public String getAuthor() {
		return "DaniDipp";
	}

	@Override
	public String getIdentifier() {
		return "soullink";
	}

	@Override
	public String getVersion() {
		return "1.0.0";
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public String onRequest(OfflinePlayer player, String identifier) {
		if (player == null) {
			return null;
		}

		if (player instanceof Player && identifier.equalsIgnoreCase("highest_nearby")) {
			var onlinePlayer = (Player) player;
			var range = plugin.getConfig().getDouble("range", 10);
			var nearby = onlinePlayer.getWorld().getNearbyEntities(onlinePlayer.getLocation(), range, range, range)
					.stream()
					.filter(e -> e instanceof Player)
					.map(Entity::getUniqueId)
					.collect(Collectors.toSet());
			if (nearby.isEmpty()) {
				return 0 + "";
			}

			LinkSet link = LinkSet.findHighest(onlinePlayer.getUniqueId(), nearby);
			if (link == null) {
				return 0 + "";
			}
			return link.level() + "";
		}

		if (identifier.startsWith("level_")) {
			var target = Bukkit.getOfflinePlayerIfCached(identifier.substring(6));
			if (target == null) {
				return 0 + "";
			}
			LinkSet link = LinkSet.findPair(player.getUniqueId(), target.getUniqueId());
			if (link == null) {
				return 0 + "";
			}
			return link.level() + "";
		}
		return null;
	}

	@Override
	public String onPlaceholderRequest(Player player1, Player player2, String identifier) {
		if (player1 == null || player2 == null) {
			return null;
		}
		if (identifier.equalsIgnoreCase("level")) {
			LinkSet link = LinkSet.findPair(player1.getUniqueId(), player2.getUniqueId());
			if (link == null) {
				return 0 + "";
			}
			return link.level() + "";
		}
		return null;
	}
}