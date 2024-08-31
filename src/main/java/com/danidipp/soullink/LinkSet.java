package com.danidipp.soullink;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record LinkSet(String id, UUID uuid1, UUID uuid2, int level) {
	static LinkSet findPair(UUID uuid1, UUID uuid2) {
		Map<String, LinkSet> links = SoulLink.plugin.links;
		// Ensure the lowest UUID is always uuid1
		if (uuid1.compareTo(uuid2) > 0) {
			var temp = uuid1;
			uuid1 = uuid2;
			uuid2 = temp;
		}
		for (var link : links.values()) {
			if (link.uuid1().equals(uuid1) && link.uuid2().equals(uuid2)) {
				return link;
			}
		}
		return null;
	}

	static LinkSet findHighest(UUID uuid, Set<UUID> include) {
		Map<String, LinkSet> links = SoulLink.plugin.links;
		LinkSet highest = links.values().stream()
				.filter(link -> link.uuid1().equals(uuid) || link.uuid2().equals(uuid))
				.filter(link -> include.contains(link.uuid1()) || include.contains(link.uuid2()))
				.max((a, b) -> Integer.compare(a.level(), b.level()))
				.orElse(null);
		return highest;
	}
}
