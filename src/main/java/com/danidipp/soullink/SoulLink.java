package com.danidipp.soullink;

import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.danidipp.soullink.utils.PocketBase;

public class SoulLink extends JavaPlugin {
	public static SoulLink plugin;
	protected Map<String, LinkSet> links;
	protected PocketBase pb;

	@Override
	public void onLoad() {
		this.saveDefaultConfig();
		String host = this.getConfig().getString("host");
		String user = this.getConfig().getString("username");
		String password = this.getConfig().getString("password");

		if (host == null || user == null || password == null ||
				host.isEmpty() || user.isEmpty() || password.isEmpty()) {
			getLogger().severe("Missing configuration: host, username, password");
			return;

		}
		if (host.endsWith("/")) {
			host = host.substring(0, host.length() - 1);
		}

		this.pb = new PocketBase(host, user, password);
		getLogger().info("SoulLink has been loaded!");
	}

	@Override
	public void onEnable() {
		plugin = this;
		if (this.pb == null) {
			getLogger().severe("Failed to load PocketBase");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			new SoulLinkExpansion(this).register();
		}

		this.getCommand("soullink").setExecutor(new SoullinkCommand());
		this.getCommand("reload").setExecutor(new ReloadCommand());

		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			this.links = this.pb.getLinksSync();
			SoulLink.plugin.getLogger().log(Level.FINE, "{0} Links loaded", links.values().size());
			if (this.links == null) {
				getLogger().severe("Failed to load links");
				getServer().getPluginManager().disablePlugin(this);
				return;
			}

			try {
				this.pb.watchLinks((action, linkSet) -> {
					SoulLink.plugin.getLogger().log(Level.FINE, "Received PocketBase Event: {0} {1}",
							new Object[] { action, linkSet });
					switch (action) {
						case "create" -> links.put(linkSet.id(), linkSet);
						case "update" -> links.put(linkSet.id(), linkSet);
						case "delete" -> links.remove(linkSet.id());
						default -> getLogger().warning("Unknown PocketBase Record action: " + action);
					}
				});
			} catch (Exception e) {
				getLogger().severe("Failed to watch links: " + e.getMessage());
				getServer().getPluginManager().disablePlugin(this);
				return;
			}
		});

		getLogger().info("SoulLink has been enabled!");
	}

	@Override
	public void onDisable() {
		if (this.pb != null && this.pb.sse != null)
			this.pb.sse.shutdown();
		getLogger().info("SoulLink has been disabled!");
	}
}