package lol.zeo.zcblive.client.db;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lol.zeo.zcblive.client.clickpack.ClickpackLoader;
import lol.zeo.zcblive.client.clickpack.LoadedClickpack;

public final class ClickpackDbClient {
	private static final URI DATABASE_URI = URI.create("https://raw.githubusercontent.com/zeozeozeo/clickpack-db/main/db.json");

	private final HttpClient httpClient = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(20))
		.build();

	public CompletableFuture<DatabaseSnapshot> fetchDatabase() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(DATABASE_URI).timeout(Duration.ofSeconds(20)).GET().build();
				HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					throw new IOException("Database request failed with status " + response.statusCode());
				}
				return parseSnapshot(response.body());
			} catch (IOException exception) {
				throw new UncheckedIOException(exception);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new CompletionException(exception);
			}
		});
	}

	public CompletableFuture<String> downloadAndInstall(ClickpackDbEntry entry, Path clickpacksDir, ClickpackLoader loader) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				Files.createDirectories(clickpacksDir);
				Path tempZip = Files.createTempFile(clickpacksDir, "zcb-live-", ".zip");
				Path tempDir = Files.createTempDirectory(clickpacksDir, "zcb-live-extract-");
				try {
					downloadArchive(entry, tempZip);
					extractArchive(tempZip, tempDir);
					try (LoadedClickpack ignored = loader.load(tempDir)) {
						// validated by successful load
					}
					String directoryName = directoryNameForPack(entry.name());
					Path finalDir = clickpacksDir.resolve(directoryName);
					deleteRecursively(finalDir);
					Files.move(tempDir, finalDir, StandardCopyOption.ATOMIC_MOVE);
					Files.deleteIfExists(tempZip);
					return directoryName;
				} finally {
					Files.deleteIfExists(tempZip);
					deleteRecursively(tempDir);
				}
			} catch (IOException exception) {
				throw new UncheckedIOException(exception);
			}
		});
	}

	public String directoryNameForPack(String packName) {
		StringBuilder builder = new StringBuilder(packName.length());
		for (int index = 0; index < packName.length(); index++) {
			char character = packName.charAt(index);
			if (character <= 31 || character == '/' || character == '\\' || character == ':' || character == '*' || character == '?' || character == '"' || character == '<' || character == '>' || character == '|') {
				builder.append('_');
			} else {
				builder.append(character);
			}
		}
		String result = builder.toString().trim();
		return result.isEmpty() ? "clickpack" : result;
	}

	private DatabaseSnapshot parseSnapshot(String rawJson) {
		JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
		JsonObject clickpacks = root.getAsJsonObject("clickpacks");
		List<ClickpackDbEntry> entries = new ArrayList<>();
		for (String name : clickpacks.keySet()) {
			JsonObject clickpack = clickpacks.getAsJsonObject(name);
			entries.add(new ClickpackDbEntry(
				name,
				getLong(clickpack, "size"),
				getLong(clickpack, "uncompressed_size"),
				getBoolean(clickpack, "has_noise"),
				getString(clickpack, "url"),
				getString(clickpack, "checksum"),
				getNullableString(clickpack, "readme"),
				getNullableString(clickpack, "added_at")
			));
		}
		entries.sort(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
		return new DatabaseSnapshot(
			List.copyOf(entries),
			getNullableString(root, "updated_at_iso"),
			(int) getLong(root, "version")
		);
	}

	private void downloadArchive(ClickpackDbEntry entry, Path tempZip) throws IOException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(entry.url())).timeout(Duration.ofMinutes(2)).GET().build();
		HttpResponse<InputStream> response;
		try {
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while downloading " + entry.name(), exception);
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Archive request failed with status " + response.statusCode());
		}

		MessageDigest digest = md5();
		try (InputStream input = response.body(); var output = Files.newOutputStream(tempZip)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) >= 0) {
				if (read == 0) {
					continue;
				}
				digest.update(buffer, 0, read);
				output.write(buffer, 0, read);
			}
		}

		String actualChecksum = HexFormat.of().formatHex(digest.digest());
		if (!actualChecksum.equalsIgnoreCase(entry.checksum())) {
			throw new IOException("Checksum mismatch for " + entry.name());
		}
	}

	private void extractArchive(Path archive, Path tempDir) throws IOException {
		try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
			ZipEntry entry;
			while ((entry = input.getNextEntry()) != null) {
				Path target = tempDir.resolve(entry.getName()).normalize();
				if (!target.startsWith(tempDir)) {
					throw new IOException("Blocked invalid archive entry " + entry.getName());
				}
				if (entry.isDirectory()) {
					Files.createDirectories(target);
					continue;
				}
				Files.createDirectories(target.getParent());
				Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private void deleteRecursively(Path path) throws IOException {
		if (path == null || !Files.exists(path)) {
			return;
		}
		try (var stream = Files.walk(path)) {
			for (Path child : stream.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(child);
			}
		}
	}

	private MessageDigest md5() {
		try {
			return MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("MD5 algorithm unavailable", exception);
		}
	}

	private long getLong(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value == null ? 0L : value.getAsLong();
	}

	private boolean getBoolean(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.getAsBoolean();
	}

	private String getString(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value == null ? "" : value.getAsString();
	}

	private String getNullableString(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value == null || value.isJsonNull() ? null : value.getAsString();
	}

	public record DatabaseSnapshot(List<ClickpackDbEntry> entries, String updatedAtIso, int version) {
	}
}
