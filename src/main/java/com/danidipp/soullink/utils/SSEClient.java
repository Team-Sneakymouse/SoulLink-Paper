package com.danidipp.soullink.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;

import javax.net.ssl.HttpsURLConnection;

import org.bukkit.Bukkit;

import com.danidipp.soullink.SoulLink;
import com.google.gson.Gson;

public class SSEClient {
	private String userAgent;
	private Map<String, Class<? extends SSEEventData>> eventTypes;
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();
	private boolean reconnect = true;

	SSEClient(String userAgent, Map<String, Class<? extends SSEEventData>> eventTypes) {
		this.userAgent = userAgent;
		this.eventTypes = eventTypes;
	}

	public void shutdown() {
		this.reconnect = false;
		this.executorService.shutdown();
	}

	public void connect(URL host, Map<String, String> headers, Consumer<SSEEvent> callback) {
		CompletableFuture.runAsync(() -> { // Run SSE connection on a separate thread
			while (reconnect) {
				try {

					HttpsURLConnection connection = (HttpsURLConnection) host.openConnection();
					connection.setRequestMethod("GET");
					connection.setRequestProperty("User-Agent", this.userAgent);
					connection.setRequestProperty("Accept", "text/event-stream");
					for (var entry : headers.entrySet()) {
						connection.setRequestProperty(entry.getKey(), entry.getValue());
					}

					SoulLink.plugin.getLogger().log(Level.FINE, "Establishing PocketBase SSE Connection");
					BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
					if (connection.getResponseCode() != 200) {
						SoulLink.plugin.getLogger().log(Level.SEVERE, "Connection failed: {0}",
								connection.getResponseMessage());
						reconnect = false;
						return;
					}
					SoulLink.plugin.getLogger().log(Level.FINE, "Connected to PocketBase SSE Stream");
					this.processStream(reader, callback);
				} catch (IOException e) {
					SoulLink.plugin.getLogger().log(Level.SEVERE, "Error connecting to PocketBase SSE: {0}",
							e.getMessage());
					e.printStackTrace();
					reconnect = false;
					Bukkit.getPluginManager().disablePlugin(SoulLink.plugin);
				}
			}
		}, executorService);
	}

	private void processStream(BufferedReader reader, Consumer<SSEEvent> callback) {
		SoulLink.plugin.getLogger().log(Level.INFO,
				"Starting stream processing on thread: " + Thread.currentThread().getName());
		var currentEvent = new SSEEvent(null, null, null);

		try {
			String line;
			while ((line = reader.readLine()) != null) {
				switch (line) {
					case String l when l.startsWith("id:") -> currentEvent.id = l.substring(3);
					case String l when l.startsWith("event:") -> currentEvent.event = l.substring(6);
					case String l when l.startsWith("data:") -> {
						var type = this.eventTypes.get(currentEvent.event);
						if (type == null) {
							SoulLink.plugin.getLogger().warning("Unknown event type: " + currentEvent.event);
							return;
						}
						currentEvent.data = new Gson().fromJson(l.substring(5), type);
					}
					// Empty line: end of event
					case String l when l.isEmpty() -> {
						if (!currentEvent.isComplete()) {
							SoulLink.plugin.getLogger().warning("Incomplete event: " + currentEvent);
							return;
						}
						callback.accept(currentEvent.clone());
						currentEvent.clear();
					}
					default ->
						SoulLink.plugin.getLogger().log(Level.WARNING, "Can't parse line in SSE stream: {0}", line);
				}
			}
		} catch (Exception e) {
			SoulLink.plugin.getLogger().log(Level.SEVERE, "Error processing SSE stream: {0}", e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				reader.close();
			} catch (Exception e) {
				SoulLink.plugin.getLogger().log(Level.SEVERE, "Error closing BufferedReader: {0}", e.getMessage());
				e.printStackTrace();
			}
			SoulLink.plugin.getLogger().log(Level.FINE, "Stream processing ended");
		}
	}
}

interface SSEEventType {
	public static SSEEventType valueOf(String name) {
		throw new IllegalArgumentException("Unknown event type: " + name);
	}

	public abstract String name();
}

class SSEEvent {
	String id;
	String event;
	SSEEventData data;

	public SSEEvent(String id, String event, SSEEventData data) {
		this.id = id;
		this.event = event;
		this.data = data;
	}

	void clear() {
		this.id = null;
		this.event = null;
		this.data = null;
	}

	boolean isComplete() {
		return this.id != null && this.event != null && this.data != null;
	}

	public SSEEvent clone() {
		return new SSEEvent(this.id, this.event, this.data);
	}

	@Override
	public String toString() {
		return "SSEEvent{" +
				"id='" + id + '\'' +
				", event=" + event +
				", data=" + data +
				'}';
	}
}
