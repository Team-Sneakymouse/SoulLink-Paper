package com.danidipp.soullink.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.danidipp.soullink.LinkSet;
import com.danidipp.soullink.SoulLink;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

public class PocketBase {
	HttpClient client;
	public SSEClient sse;
	private String host;
	private String token;
	private final String userAgent = "SoulLink/1.0";

	public PocketBase(String host, String username, String password) {
		this.host = host;
		this.client = HttpClient.newHttpClient();
		this.sse = new SSEClient(this.userAgent, Map.of(
				"PB_CONNECT", InitData.class,
				"lom2_soullink", SoullinkRecordData.class));
		this.token = this.authenticateSync(username, password);
		if (this.token == null) {
			throw new RuntimeException("Failed to authenticate with PocketBase");
		}
	}

	private String authenticateSync(String username, String password) {
		var request = HttpRequest.newBuilder()
				.uri(URI.create(this.host + "/api/admins/auth-with-password"))
				.header("User-Agent", this.userAgent)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers
						.ofString("{\"identity\":\"" + username + "\",\"password\":\"" + password + "\"}"))
				.build();

		HttpResponse<String> response;
		try {
			response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return null;
		}
		var body = response.body();
		var data = new Gson().fromJson(body, AuthResponse.class);
		return data.token();
	}

	public Map<String, LinkSet> getLinksSync() {
		var type = new TypeToken<CollectionsResponse<SoullinkRecord>>() {
		}.getType();

		List<SoullinkRecord> records = new ArrayList<>();
		var apiUrl = this.host + "/api/collections/lom2_soullink/records?perPage=500";
		var page = 1;
		while (true) {
			var request = HttpRequest.newBuilder()
					.uri(URI.create(apiUrl + "&page=" + page))
					.header("User-Agent", this.userAgent)
					.header("Authorization", this.token)
					.GET().build();
			HttpResponse<String> response;
			try {
				SoulLink.plugin.getLogger().log(Level.FINE, "Fetching Links, page ", page);
				response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
				return null;
			}
			var body = response.body();
			CollectionsResponse<SoullinkRecord> data = new Gson().fromJson(body, type);
			records.addAll(data.items());
			if (data.page() >= data.totalPages()) {
				break;
			}
			page++;
		}
		return records.stream().collect(Collectors.toMap(SoullinkRecord::id, record -> new LinkSet(
				record.id(),
				UUID.fromString(record.uuid1()),
				UUID.fromString(record.uuid2()),
				record.level())));
	}

	public LinkSet setLinkSync(String id, UUID uuid1, UUID uuid2, int level) {
		var builder = HttpRequest.newBuilder()
				.header("User-Agent", this.userAgent)
				.header("Authorization", this.token)
				.header("Content-Type", "application/json");
		var body = HttpRequest.BodyPublishers
				.ofString("{\"uuid1\":\"" + uuid1 + "\",\"uuid2\":\"" + uuid2 + "\",\"level\":" + level + "}");
		if (id == null) {
			builder = builder
					.uri(URI.create(this.host + "/api/collections/lom2_soullink/records"))
					.method("POST", body);
		} else {
			builder = builder
					.uri(URI.create(this.host + "/api/collections/lom2_soullink/records/" + id))
					.method("PATCH", body);
		}
		HttpRequest request = builder.build();
		try {
			var response = this.client.send(request, BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				SoulLink.plugin.getLogger().log(Level.SEVERE, "Failed to set link: Error {0}\n{1}",
						new Object[] { response.statusCode(), response.body() });
				return null;
			}
			var data = new Gson().fromJson(response.body(), SoullinkRecord.class);
			return new LinkSet(data.id(), UUID.fromString(data.uuid1()), UUID.fromString(data.uuid2()), data.level());
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			return null;
		}
	}

	public void watchLinks(BiConsumer<String, LinkSet> callback) throws MalformedURLException {
		var url = URI.create(host + "/api/realtime").toURL();
		this.sse.connect(url, Map.of("Authorization", this.token), (event) -> {
			switch (event.event) {
				case "PB_CONNECT" -> {
					var data = (InitData) event.data;
					this.startSubscription(data.clientId());
				}
				case "lom2_soullink" -> {
					var data = (SoullinkRecordData) event.data;
					callback.accept(data.action(), new LinkSet(
							data.record().id(),
							UUID.fromString(data.record().uuid1()),
							UUID.fromString(data.record().uuid2()),
							data.record().level()));
				}
				default ->
					SoulLink.plugin.getLogger().warning("Unknown PocketBase Event: " + event.event);
			}
		});
	}

	private void startSubscription(String clientId) {
		var request = HttpRequest.newBuilder()
				.uri(URI.create(this.host + "/api/realtime"))
				.header("User-Agent", this.userAgent)
				.header("Authorization", this.token)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers
						.ofString("{\"clientId\":\"" + clientId + "\", \"subscriptions\":[\"lom2_soullink\"]}"))
				.build();

		SoulLink.plugin.getLogger().log(Level.FINE, "Starting subscription");
		this.client.sendAsync(request, BodyHandlers.ofString())
				.exceptionally(e -> {
					SoulLink.plugin.getLogger().log(Level.SEVERE, "Failed to start subscription: {0}", e.getMessage());
					return null;
				})
				.thenAccept(response -> {
					if (response.statusCode() != 204) {
						SoulLink.plugin.getLogger().log(Level.SEVERE, "Failed to start subscription: Error {0}\n{1}",
								new Object[] { response.statusCode(), response.body() });
					}
				});
	}
}

// Plain API
record AdminResponse(
		String id,
		String created,
		String updated,
		int avatar,
		String email) {
}

record AuthResponse(
		String token,
		AdminResponse admin) {
}

record SoullinkRecord(
		String id,
		String uuid1,
		String uuid2,
		int level) {
}

record CollectionsResponse<T>(
		int page,
		int perPage,
		int totalItems,
		int totalPages,
		List<T> items) {
}

// Realtime API
record InitData(
		String clientId) implements SSEEventData {
}

record SoullinkRecordData(
		String action,
		SoullinkRecord record) implements SSEEventData {
}