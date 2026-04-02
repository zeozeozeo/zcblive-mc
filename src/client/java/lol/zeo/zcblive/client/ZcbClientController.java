package lol.zeo.zcblive.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import lol.zeo.zcblive.ZCBLive;
import lol.zeo.zcblive.client.audio.ClickAudioService;
import lol.zeo.zcblive.client.clickpack.ClickSample;
import lol.zeo.zcblive.client.clickpack.ClickpackLoader;
import lol.zeo.zcblive.client.clickpack.LoadedClickpack;
import lol.zeo.zcblive.client.db.ClickpackDbClient;
import lol.zeo.zcblive.client.db.ClickpackDbEntry;
import lol.zeo.zcblive.client.input.ClickInputService;

public final class ZcbClientController {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path configDirectory = FabricLoader.getInstance().getGameDir().resolve("config").resolve("zcb-live");
	private final Path configFile = configDirectory.resolve("config.json");
	private final Path clickpackDbCacheFile = configDirectory.resolve("clickpackdb-cache.json");
	private final Path clickpacksDirectory = configDirectory.resolve("clickpacks");
	private final ClickpackLoader clickpackLoader = new ClickpackLoader();
	private final ClickAudioService clickAudioService = new ClickAudioService();
	private final ClickpackDbClient clickpackDbClient = new ClickpackDbClient();

	private ZcbConfig config = new ZcbConfig();
	private ClickInputService inputService;
	private volatile @Nullable LoadedClickpack activeKeyboardClickpack;
	private volatile @Nullable LoadedClickpack activeMouseClickpack;
	private volatile ClickpackDbClient.DatabaseSnapshot cachedSnapshot;

	public void initialize() {
		try {
			Files.createDirectories(clickpacksDirectory);
			config = loadConfig();
			cachedSnapshot = loadCachedSnapshot();
			inputService = new ClickInputService(() -> config, this::getKeyboardClickpack, this::getMouseClickpack, clickAudioService);
			reloadAssignedClickpacks();
		} catch (IOException exception) {
			throw new RuntimeException("Failed to initialize ZCB Live", exception);
		}
	}

	public void handleKeyboardEvent(com.mojang.blaze3d.platform.InputConstants.Key key, boolean press) {
		if (inputService != null) {
			inputService.handleKeyboard(key, press);
		}
	}

	public void handleMouseEvent(int button, boolean press) {
		if (inputService != null) {
			inputService.handleMouse(button, press);
		}
	}

	public void handleScreenMouseEvent(int button, boolean press) {
		if (inputService != null) {
			inputService.handleScreenMouse(button, press);
		}
	}

	public synchronized @Nullable LoadedClickpack getKeyboardClickpack() {
		return activeKeyboardClickpack;
	}

	public synchronized @Nullable LoadedClickpack getMouseClickpack() {
		return activeMouseClickpack;
	}

	public synchronized boolean isInstalled(String packName) {
		return Files.isDirectory(clickpacksDirectory.resolve(clickpackDbClient.directoryNameForPack(packName)));
	}

	public synchronized boolean isKeyboardActivePack(String packName) {
		return config.activeKeyboardClickpack.equals(clickpackDbClient.directoryNameForPack(packName));
	}

	public synchronized boolean isMouseActivePack(String packName) {
		return config.activeMouseClickpack.equals(clickpackDbClient.directoryNameForPack(packName));
	}

	public synchronized boolean isAnyActivePack(String packName) {
		return isKeyboardActivePack(packName) || isMouseActivePack(packName);
	}

	public synchronized ZcbConfig.InputMode inputMode() {
		return config.inputMode;
	}

	public synchronized ZcbConfig config() {
		return config;
	}

	public synchronized double clickVolume() {
		return config.clickVolume;
	}

	public synchronized boolean playNoiseEnabled() {
		return config.playNoise;
	}

	public synchronized void cycleInputMode() throws IOException {
		config.inputMode = config.inputMode.next();
		saveConfig();
	}

	public synchronized void setClickVolume(double clickVolume) throws IOException {
		config.clickVolume = Math.max(0.0D, Math.min(clickVolume, 5.0D));
		saveConfig();
	}

	public synchronized void updateConfig(Consumer<ZcbConfig> updater) throws IOException {
		updater.accept(config);
		saveConfig();
	}

	public synchronized void resetConfigToDefaults() throws IOException {
		config = new ZcbConfig().normalize();
		saveConfig();
		reloadAssignedClickpacks();
	}

	public synchronized void togglePlayNoise() throws IOException {
		config.playNoise = !config.playNoise;
		saveConfig();
	}

	public synchronized void activateInstalledForKeyboard(String packName) throws IOException {
		config.activeKeyboardClickpack = clickpackDbClient.directoryNameForPack(packName);
		saveConfig();
		reloadAssignedClickpacks();
	}

	public synchronized void activateInstalledForMouse(String packName) throws IOException {
		config.activeMouseClickpack = clickpackDbClient.directoryNameForPack(packName);
		saveConfig();
		reloadAssignedClickpacks();
	}

	public CompletableFuture<ClickpackDbClient.DatabaseSnapshot> refreshDatabase() {
		return clickpackDbClient.fetchDatabase().thenApply(snapshot -> {
			cacheSnapshot(snapshot);
			cachedSnapshot = snapshot;
			return snapshot;
		});
	}

	public ClickpackDbClient.DatabaseSnapshot cachedSnapshot() {
		return cachedSnapshot;
	}

	public synchronized List<ClickpackDbEntry> browserEntries() {
		Map<String, ClickpackDbEntry> merged = new LinkedHashMap<>();
		if (cachedSnapshot != null) {
			for (ClickpackDbEntry entry : cachedSnapshot.entries()) {
				merged.put(normalizedName(entry.name()), entry);
			}
		}
		for (String installedName : installedClickpackNames()) {
			merged.computeIfAbsent(normalizedName(installedName), ignored -> offlineEntry(installedName));
		}
		return merged.values().stream()
			.sorted(
				Comparator.<ClickpackDbEntry>comparingInt(this::browserPriority)
					.thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT))
			)
			.toList();
	}

	public CompletableFuture<String> downloadClickpack(ClickpackDbEntry entry) {
		return clickpackDbClient.downloadAndInstall(entry, clickpacksDirectory, clickpackLoader);
	}

	public Path clickpacksDirectory() {
		return clickpacksDirectory;
	}

	public void tick() {
		Minecraft minecraft = Minecraft.getInstance();
		boolean allowNoise = minecraft != null && minecraft.level != null && config.enabled && config.playNoise;
		clickAudioService.syncNoiseLoop(desiredNoiseSample(), config.clickVolume, allowNoise);
	}

	private synchronized void reloadAssignedClickpacks() throws IOException {
		clickAudioService.stopNoiseLoop();
		LoadedClickpack previousKeyboard = activeKeyboardClickpack;
		LoadedClickpack previousMouse = activeMouseClickpack;
		activeKeyboardClickpack = null;
		activeMouseClickpack = null;

		Path keyboardPath = resolveAssignedPath(config.activeKeyboardClickpack);
		Path mousePath = resolveAssignedPath(config.activeMouseClickpack);
		if (keyboardPath != null && mousePath != null && keyboardPath.equals(mousePath)) {
			LoadedClickpack shared = clickpackLoader.load(keyboardPath);
			activeKeyboardClickpack = shared;
			activeMouseClickpack = shared;
		} else {
			if (keyboardPath != null) {
				activeKeyboardClickpack = clickpackLoader.load(keyboardPath);
			}
			if (mousePath != null) {
				activeMouseClickpack = clickpackLoader.load(mousePath);
			}
		}

		closeIfUnique(previousKeyboard, previousMouse);
		closeIfUnique(previousMouse, previousKeyboard);
	}

	private @Nullable Path resolveAssignedPath(String directoryName) throws IOException {
		if (directoryName.isBlank()) {
			return null;
		}
		Path candidate = clickpacksDirectory.resolve(directoryName);
		if (Files.isDirectory(candidate)) {
			return candidate;
		}
		if (config.activeKeyboardClickpack.equals(directoryName)) {
			config.activeKeyboardClickpack = "";
		}
		if (config.activeMouseClickpack.equals(directoryName)) {
			config.activeMouseClickpack = "";
		}
		saveConfig();
		return null;
	}

	private void closeIfUnique(@Nullable LoadedClickpack clickpack, @Nullable LoadedClickpack other) {
		if (clickpack != null && clickpack != other) {
			clickpack.close();
		}
	}

	private synchronized @Nullable ClickSample desiredNoiseSample() {
		if (activeMouseClickpack != null && activeMouseClickpack.hasNoise()) {
			return activeMouseClickpack.noiseSample();
		}
		if (activeKeyboardClickpack != null && activeKeyboardClickpack.hasNoise()) {
			return activeKeyboardClickpack.noiseSample();
		}
		return null;
	}

	private ZcbConfig loadConfig() throws IOException {
		Files.createDirectories(configDirectory);
		if (Files.isRegularFile(configFile)) {
			try (Reader reader = Files.newBufferedReader(configFile)) {
				ZcbConfig loaded = GSON.fromJson(reader, ZcbConfig.class);
				if (loaded != null) {
					return loaded.normalize();
				}
			} catch (Exception exception) {
				ZCBLive.LOGGER.warn("Failed to read {}, writing defaults", configFile, exception);
			}
		}
		ZcbConfig defaults = new ZcbConfig().normalize();
		config = defaults;
		saveConfig();
		return defaults;
	}

	private synchronized void saveConfig() throws IOException {
		Files.createDirectories(configDirectory);
		try (Writer writer = Files.newBufferedWriter(configFile)) {
			GSON.toJson(config.normalize(), writer);
		}
	}

	private ClickpackDbClient.DatabaseSnapshot loadCachedSnapshot() throws IOException {
		if (Files.isRegularFile(clickpackDbCacheFile)) {
			try (Reader reader = Files.newBufferedReader(clickpackDbCacheFile)) {
				ClickpackDbClient.DatabaseSnapshot snapshot = GSON.fromJson(reader, ClickpackDbClient.DatabaseSnapshot.class);
				if (snapshot != null && snapshot.entries() != null) {
					return snapshot;
				}
			} catch (Exception exception) {
				ZCBLive.LOGGER.warn("Failed to read {}, ignoring cached ClickpackDB snapshot", clickpackDbCacheFile, exception);
			}
		}
		return new ClickpackDbClient.DatabaseSnapshot(List.of(), null, 0);
	}

	private synchronized void cacheSnapshot(ClickpackDbClient.DatabaseSnapshot snapshot) {
		try {
			Files.createDirectories(configDirectory);
			try (Writer writer = Files.newBufferedWriter(clickpackDbCacheFile)) {
				GSON.toJson(snapshot, writer);
			}
		} catch (IOException exception) {
			ZCBLive.LOGGER.warn("Failed to write {}", clickpackDbCacheFile, exception);
		}
	}

	private List<String> installedClickpackNames() {
		if (!Files.isDirectory(clickpacksDirectory)) {
			return List.of();
		}
		try (var stream = Files.list(clickpacksDirectory)) {
			List<String> names = new ArrayList<>();
			for (Path path : stream.filter(Files::isDirectory).toList()) {
				names.add(path.getFileName().toString());
			}
			return names;
		} catch (IOException exception) {
			ZCBLive.LOGGER.warn("Failed to list installed clickpacks in {}", clickpacksDirectory, exception);
			return List.of();
		}
	}

	private ClickpackDbEntry offlineEntry(String installedName) {
		return new ClickpackDbEntry(
			installedName,
			0L,
			0L,
			false,
			"",
			"",
			"Installed locally. Online metadata is unavailable offline.",
			null
		);
	}

	private String normalizedName(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private int browserPriority(ClickpackDbEntry entry) {
		if (isKeyboardActivePack(entry.name()) && isMouseActivePack(entry.name())) {
			return 0;
		}
		if (isAnyActivePack(entry.name())) {
			return 1;
		}
		if (isInstalled(entry.name())) {
			return 2;
		}
		return 3;
	}
}
