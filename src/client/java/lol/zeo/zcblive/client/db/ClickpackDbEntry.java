package lol.zeo.zcblive.client.db;

import org.jspecify.annotations.Nullable;

public record ClickpackDbEntry(
	String name,
	long size,
	long uncompressedSize,
	long downloadCount,
	boolean hasNoise,
	String url,
	String checksum,
	@Nullable String readme,
	@Nullable String addedAt
) {
}
