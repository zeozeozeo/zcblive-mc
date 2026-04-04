package lol.zeo.zcblive.client.clickpack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.jspecify.annotations.Nullable;

import lol.zeo.zcblive.ZCBLive;

public final class ClickpackLoader {
	public LoadedClickpack load(Path root) throws IOException {
		if (!Files.isDirectory(root)) {
			throw new IOException("Clickpack path is not a directory: " + root);
		}

		PlayerClicks keyboardClicks = null;
		PlayerClicks mouseClicks = null;
		ClickpackOverlay overlay = null;
		ClickSample noise = null;
		try {
			Path player1 = findChild(root, "player1");
			Path player2 = findChild(root, "player2");
			Path keyboardRoot = findChild(root, "keyboard");
			Path mouseRoot = findChild(root, "mouse");
			if (player1 != null || player2 != null) {
				if (player1 != null) {
					keyboardClicks = loadPlayerClicks(player1, true);
				}
				if (player2 != null) {
					mouseClicks = loadPlayerClicks(player2, true);
				}
			} else {
				mouseClicks = loadPlayerClicks(root, true);
			}
			overlay = loadOverlay(root);
			noise = findNoise(player1, player2, keyboardRoot, mouseRoot, root);

			if ((keyboardClicks == null || keyboardClicks.isEmpty())
				&& (mouseClicks == null || mouseClicks.isEmpty())
				&& (overlay == null || overlay.isEmpty())) {
				throw new IOException("No click sounds found in " + root);
			}
			if (keyboardClicks != null && keyboardClicks.isEmpty()) {
				keyboardClicks.close();
				keyboardClicks = null;
			}
			if (mouseClicks != null && mouseClicks.isEmpty()) {
				mouseClicks.close();
				mouseClicks = null;
			}

			return new LoadedClickpack(root.getFileName().toString(), root, keyboardClicks, mouseClicks, overlay, noise);
		} catch (IOException exception) {
			closeQuietly(keyboardClicks);
			closeQuietly(mouseClicks);
			if (overlay != null) {
				overlay.close();
			}
			if (noise != null) {
				noise.close();
			}
			throw exception;
		}
	}

	private @Nullable ClickpackOverlay loadOverlay(Path root) throws IOException {
		ClickpackOverlay overlay = new ClickpackOverlay();
		try {
			Path keyboardRoot = findChild(root, "keyboard");
			if (keyboardRoot != null) {
				loadKeyboardOverlay(overlay, keyboardRoot);
			}
			Path mouseRoot = findChild(root, "mouse");
			if (mouseRoot != null) {
				loadMouseOverlay(overlay, mouseRoot);
			}
			return overlay.isEmpty() ? null : overlay;
		} catch (IOException exception) {
			overlay.close();
			throw exception;
		}
	}

	private void loadKeyboardOverlay(ClickpackOverlay overlay, Path keyboardRoot) throws IOException {
		try (Stream<Path> stream = Files.list(keyboardRoot)) {
			List<Path> children = stream.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
			for (Path child : children) {
				if (!Files.isDirectory(child)) {
					continue;
				}
				String normalized = normalizedDirectoryName(child.getFileName().toString());
				if ("special".equals(normalized)) {
					loadKeyboardSpecialOverlay(overlay, child);
					continue;
				}
				ClickpackOverlay.KeyboardBucket bucket = ClickpackOverlay.KeyboardBucket.fromDirectoryName(normalized);
				if (bucket == null || !bucket.isRow()) {
					continue;
				}
				PlayerClicks clicks = loadPlayerClicks(child, false);
				if (!clicks.isEmpty()) {
					overlay.addKeyboardBucket(bucket, clicks);
				} else {
					clicks.close();
				}
			}
		}
	}

	private void loadKeyboardSpecialOverlay(ClickpackOverlay overlay, Path specialRoot) throws IOException {
		try (Stream<Path> stream = Files.list(specialRoot)) {
			List<Path> children = stream.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
			for (Path child : children) {
				if (!Files.isDirectory(child)) {
					continue;
				}
				ClickpackOverlay.KeyboardBucket bucket = ClickpackOverlay.KeyboardBucket.fromDirectoryName(child.getFileName().toString());
				if (bucket == null || bucket.isRow()) {
					continue;
				}
				PlayerClicks clicks = loadPlayerClicks(child, false);
				if (!clicks.isEmpty()) {
					overlay.addKeyboardBucket(bucket, clicks);
				} else {
					clicks.close();
				}
			}
		}
	}

	private void loadMouseOverlay(ClickpackOverlay overlay, Path mouseRoot) throws IOException {
		try (Stream<Path> stream = Files.list(mouseRoot)) {
			List<Path> children = stream.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
			for (Path child : children) {
				if (!Files.isDirectory(child)) {
					continue;
				}
				ClickpackOverlay.MouseBucket bucket = ClickpackOverlay.MouseBucket.fromDirectoryName(child.getFileName().toString());
				if (bucket == null) {
					continue;
				}
				PlayerClicks clicks = loadPlayerClicks(child, false);
				if (!clicks.isEmpty()) {
					overlay.addMouseBucket(bucket, clicks);
				} else {
					clicks.close();
				}
			}
		}
	}

	private PlayerClicks loadPlayerClicks(Path directory, boolean allowHardClicks) throws IOException {
		PlayerClicks clicks = new PlayerClicks();
		try (Stream<Path> stream = Files.list(directory)) {
			List<Path> children = stream.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
			for (Path child : children) {
				if (!Files.isDirectory(child)) {
					continue;
				}
				ClickType type = clickTypeForDirectory(child.getFileName().toString());
				if (type == ClickType.NONE || (!allowHardClicks && (type == ClickType.HARD_CLICK || type == ClickType.HARD_RELEASE))) {
					continue;
				}
				loadSamples(clicks, type, child);
			}
		}
		return clicks;
	}

	private void loadSamples(PlayerClicks clicks, ClickType type, Path directory) throws IOException {
		try (Stream<Path> stream = Files.list(directory)) {
			List<Path> paths = stream
				.filter(Files::isRegularFile)
				.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
				.toList();
			for (Path path : paths) {
				try {
					clicks.add(type, ClickSample.load(path));
				} catch (UnsupportedAudioFileException exception) {
					ZCBLive.LOGGER.warn("Skipping unsupported audio file {}", path, exception);
				} catch (IOException exception) {
					ZCBLive.LOGGER.warn("Skipping unreadable audio file {}", path, exception);
				}
			}
		}
	}

	private @Nullable ClickSample findNoise(Path... directories) throws IOException {
		for (Path directory : directories) {
			if (directory == null || !Files.isDirectory(directory)) {
				continue;
			}
			try (Stream<Path> stream = Files.list(directory)) {
				for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
					String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
					if (name.startsWith("noise") || name.startsWith("whitenoise") || name.startsWith("pcnoise") || name.startsWith("background")) {
						try {
							return ClickSample.load(path);
						} catch (UnsupportedAudioFileException exception) {
							ZCBLive.LOGGER.warn("Skipping unsupported noise file {}", path, exception);
							continue;
						}
					}
				}
			}
		}
		return null;
	}

	private @Nullable Path findChild(Path root, String wanted) throws IOException {
		try (Stream<Path> stream = Files.list(root)) {
			return stream
				.filter(Files::isDirectory)
				.filter(path -> path.getFileName().toString().equalsIgnoreCase(wanted))
				.findFirst()
				.orElse(null);
		}
	}

	private ClickType clickTypeForDirectory(String directoryName) {
		String normalized = normalizedDirectoryName(directoryName);
		return switch (normalized) {
			case "hardclick", "hardclicks" -> ClickType.HARD_CLICK;
			case "hardrelease", "hardreleases" -> ClickType.HARD_RELEASE;
			case "click", "clicks" -> ClickType.CLICK;
			case "release", "releases" -> ClickType.RELEASE;
			case "softclick", "softclicks" -> ClickType.SOFT_CLICK;
			case "softrelease", "softreleases" -> ClickType.SOFT_RELEASE;
			case "microclick", "microclicks" -> ClickType.MICRO_CLICK;
			case "microrelease", "microreleases" -> ClickType.MICRO_RELEASE;
			default -> ClickType.NONE;
		};
	}

	private String normalizedDirectoryName(String directoryName) {
		StringBuilder builder = new StringBuilder(directoryName.length());
		for (int index = 0; index < directoryName.length(); index++) {
			char character = Character.toLowerCase(directoryName.charAt(index));
			if (Character.isLetterOrDigit(character)) {
				builder.append(character);
			}
		}
		return builder.toString();
	}

	private void closeQuietly(@Nullable PlayerClicks clicks) {
		if (clicks != null) {
			clicks.close();
		}
	}
}
