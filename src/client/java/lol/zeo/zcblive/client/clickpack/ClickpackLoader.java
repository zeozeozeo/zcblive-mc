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
		ClickSample noise = null;
		try {
			Path player1 = findChild(root, "player1");
			Path player2 = findChild(root, "player2");
			if (player1 != null || player2 != null) {
				if (player1 != null) {
					keyboardClicks = loadPlayerClicks(player1);
					noise = findNoise(player1);
				}
				if (player2 != null) {
					mouseClicks = loadPlayerClicks(player2);
					if (noise == null) {
						noise = findNoise(player2);
					}
				}
				if (noise == null) {
					noise = findNoise(root);
				}
			} else {
				mouseClicks = loadPlayerClicks(root);
				noise = findNoise(root);
			}

			if ((keyboardClicks == null || keyboardClicks.isEmpty()) && (mouseClicks == null || mouseClicks.isEmpty())) {
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

			return new LoadedClickpack(root.getFileName().toString(), root, keyboardClicks, mouseClicks, noise);
		} catch (IOException exception) {
			closeQuietly(keyboardClicks);
			closeQuietly(mouseClicks);
			if (noise != null) {
				noise.close();
			}
			throw exception;
		}
	}

	private PlayerClicks loadPlayerClicks(Path directory) throws IOException {
		PlayerClicks clicks = new PlayerClicks();
		try (Stream<Path> stream = Files.list(directory)) {
			List<Path> children = stream.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).toList();
			for (Path child : children) {
				if (!Files.isDirectory(child)) {
					continue;
				}
				ClickType type = clickTypeForDirectory(child.getFileName().toString());
				if (type == ClickType.NONE) {
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

	private @Nullable ClickSample findNoise(Path directory) throws IOException {
		try (Stream<Path> stream = Files.list(directory)) {
			for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
				String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
				if (name.startsWith("noise") || name.startsWith("whitenoise") || name.startsWith("pcnoise") || name.startsWith("background")) {
					try {
						return ClickSample.load(path);
					} catch (UnsupportedAudioFileException exception) {
						ZCBLive.LOGGER.warn("Skipping unsupported noise file {}", path, exception);
						return null;
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
		String normalized = directoryName.chars()
			.filter(Character::isAlphabetic)
			.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
			.toString()
			.toLowerCase(Locale.ROOT);
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

	private void closeQuietly(@Nullable PlayerClicks clicks) {
		if (clicks != null) {
			clicks.close();
		}
	}
}
