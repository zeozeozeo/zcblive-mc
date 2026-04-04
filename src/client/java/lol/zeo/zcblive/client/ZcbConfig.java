package lol.zeo.zcblive.client;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lol.zeo.zcblive.client.clickpack.ClickType;

public final class ZcbConfig {
	public String activeClickpack = "";
	public String activeKeyboardClickpack = "";
	public String activeMouseClickpack = "";
	public List<String> featuredClickpacks = new ArrayList<>();
	public boolean enabled = true;
	public InputMode inputMode = InputMode.BOTH;
	public KeyboardSettings keyboard = new KeyboardSettings();
	public LaneSettings mouse = new LaneSettings();
	public boolean playNoise = false;

	public ZcbConfig normalize() {
		if (activeClickpack == null) {
			activeClickpack = "";
		}
		if (activeKeyboardClickpack == null) {
			activeKeyboardClickpack = "";
		}
		if (activeMouseClickpack == null) {
			activeMouseClickpack = "";
		}
		if (featuredClickpacks == null) {
			featuredClickpacks = new ArrayList<>();
		} else {
			LinkedHashSet<String> uniqueFeatured = new LinkedHashSet<>();
			for (String clickpack : featuredClickpacks) {
				if (clickpack != null) {
					String normalized = clickpack.trim();
					if (!normalized.isBlank()) {
						uniqueFeatured.add(normalized);
					}
				}
			}
			featuredClickpacks = new ArrayList<>(uniqueFeatured);
		}
		if (activeKeyboardClickpack.isBlank() && activeMouseClickpack.isBlank() && !activeClickpack.isBlank()) {
			activeKeyboardClickpack = activeClickpack;
			activeMouseClickpack = activeClickpack;
		}
		activeClickpack = "";
		if (inputMode == null) {
			inputMode = InputMode.BOTH;
		}
		if (keyboard == null) {
			keyboard = new KeyboardSettings();
		}
		if (mouse == null) {
			mouse = new LaneSettings();
		}
		keyboard.normalize();
		mouse.normalize();
		return this;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public enum InputMode {
		KEYBOARD_ONLY("Keyboard Only"),
		MOUSE_ONLY("Mouse Only"),
		BOTH("Both");

		public static final Codec<InputMode> CODEC = Codec.STRING.xmap(InputMode::fromSerializedName, InputMode::serializedName);

		private final String label;

		InputMode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public InputMode next() {
			return switch (this) {
				case KEYBOARD_ONLY -> MOUSE_ONLY;
				case MOUSE_ONLY -> BOTH;
				case BOTH -> KEYBOARD_ONLY;
			};
		}

		public String serializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static InputMode fromSerializedName(String serializedName) {
			return switch (serializedName.toLowerCase(Locale.ROOT)) {
				case "keyboard_only" -> KEYBOARD_ONLY;
				case "mouse_only" -> MOUSE_ONLY;
				case "both" -> BOTH;
				default -> BOTH;
			};
		}
	}

	public static final class PitchSettings {
		public double from = 0.98D;
		public double to = 1.02D;

		public PitchSettings copy() {
			PitchSettings copy = new PitchSettings();
			copy.from = from;
			copy.to = to;
			return copy;
		}
	}

	public static final class LaneSettings {
		public double clickVolume = 1.0D;
		public boolean pitchEnabled = true;
		public PitchSettings pitch = new PitchSettings();
		public Timings timings = new Timings();
		public VolumeSettings volumeSettings = new VolumeSettings();
		public boolean ignoreHardClick = false;
		public boolean ignoreHardRelease = false;
		public boolean ignoreClick = false;
		public boolean ignoreRelease = false;
		public boolean ignoreSoftClick = false;
		public boolean ignoreSoftRelease = false;
		public boolean ignoreMicroClick = false;
		public boolean ignoreMicroRelease = false;
		public Boolean hardClicksEnabled = Boolean.TRUE;

		public LaneSettings normalize() {
			clickVolume = clamp(clickVolume, 0.0D, 5.0D);
			if (pitch == null) {
				pitch = new PitchSettings();
			}
			if (timings == null) {
				timings = new Timings();
			}
			if (volumeSettings == null) {
				volumeSettings = new VolumeSettings();
			}
			if (pitch.from > pitch.to) {
				double swapped = pitch.from;
				pitch.from = pitch.to;
				pitch.to = swapped;
			}
			timings.hard = Math.max(timings.hard, 0.0D);
			timings.regular = Math.max(timings.regular, 0.0D);
			timings.soft = Math.max(timings.soft, 0.0D);
			double[] sortedTimings = {timings.soft, timings.regular, timings.hard};
			Arrays.sort(sortedTimings);
			timings.soft = sortedTimings[0];
			timings.regular = sortedTimings[1];
			timings.hard = sortedTimings[2];
			volumeSettings.spamTime = Math.max(volumeSettings.spamTime, 0.0D);
			volumeSettings.maxSpamVolOffset = Math.max(volumeSettings.maxSpamVolOffset, 0.0D);
			volumeSettings.globalVolume = Math.max(volumeSettings.globalVolume, 0.0D);
			volumeSettings.volumeVar = Math.max(volumeSettings.volumeVar, 0.0D);
			if (hardClicksEnabled != null) {
				if (!hardClicksEnabled) {
					ignoreHardClick = true;
					ignoreHardRelease = true;
				}
				hardClicksEnabled = null;
			}
			return this;
		}

		public LaneSettings copy() {
			LaneSettings copy = new LaneSettings();
			copy.clickVolume = clickVolume;
			copy.pitchEnabled = pitchEnabled;
			copy.pitch = pitch == null ? new PitchSettings() : pitch.copy();
			copy.timings = timings == null ? new Timings() : timings.copy();
			copy.volumeSettings = volumeSettings == null ? new VolumeSettings() : volumeSettings.copy();
			copy.ignoreHardClick = ignoreHardClick;
			copy.ignoreHardRelease = ignoreHardRelease;
			copy.ignoreClick = ignoreClick;
			copy.ignoreRelease = ignoreRelease;
			copy.ignoreSoftClick = ignoreSoftClick;
			copy.ignoreSoftRelease = ignoreSoftRelease;
			copy.ignoreMicroClick = ignoreMicroClick;
			copy.ignoreMicroRelease = ignoreMicroRelease;
			copy.hardClicksEnabled = hardClicksEnabled;
			return copy;
		}

		public boolean isClickTypeIgnored(ClickType clickType) {
			return switch (clickType) {
				case HARD_CLICK -> ignoreHardClick;
				case HARD_RELEASE -> ignoreHardRelease;
				case CLICK -> ignoreClick;
				case RELEASE -> ignoreRelease;
				case SOFT_CLICK -> ignoreSoftClick;
				case SOFT_RELEASE -> ignoreSoftRelease;
				case MICRO_CLICK -> ignoreMicroClick;
				case MICRO_RELEASE -> ignoreMicroRelease;
				case NONE -> false;
			};
		}

		public void setClickTypeIgnored(ClickType clickType, boolean ignored) {
			switch (clickType) {
				case HARD_CLICK -> ignoreHardClick = ignored;
				case HARD_RELEASE -> ignoreHardRelease = ignored;
				case CLICK -> ignoreClick = ignored;
				case RELEASE -> ignoreRelease = ignored;
				case SOFT_CLICK -> ignoreSoftClick = ignored;
				case SOFT_RELEASE -> ignoreSoftRelease = ignored;
				case MICRO_CLICK -> ignoreMicroClick = ignored;
				case MICRO_RELEASE -> ignoreMicroRelease = ignored;
				case NONE -> {
				}
			}
		}
	}

	public static final class Timings {
		public double hard = 2.0D;
		public double regular = 0.15D;
		public double soft = 0.025D;

		public Timings copy() {
			Timings copy = new Timings();
			copy.hard = hard;
			copy.regular = regular;
			copy.soft = soft;
			return copy;
		}
	}

	public static final class VolumeSettings {
		public boolean enabled = true;
		public double spamTime = 0.3D;
		public double spamVolOffsetFactor = 1.3D;
		public double maxSpamVolOffset = 0.6D;
		public boolean changeReleasesVolume = false;
		public double globalVolume = 1.0D;
		public double volumeVar = 0.2D;

		public VolumeSettings copy() {
			VolumeSettings copy = new VolumeSettings();
			copy.enabled = enabled;
			copy.spamTime = spamTime;
			copy.spamVolOffsetFactor = spamVolOffsetFactor;
			copy.maxSpamVolOffset = maxSpamVolOffset;
			copy.changeReleasesVolume = changeReleasesVolume;
			copy.globalVolume = globalVolume;
			copy.volumeVar = volumeVar;
			return copy;
		}
	}

	public static final class KeyboardSettings {
		public KeyboardGrouping grouping = KeyboardGrouping.PER_ROW;
		public LaneSettings settings = new LaneSettings();

		public KeyboardSettings normalize() {
			if (grouping == null) {
				grouping = KeyboardGrouping.PER_ROW;
			}
			if (settings == null) {
				settings = new LaneSettings();
			}
			settings.normalize();
			return this;
		}

		public double clickVolume() {
			return settings.clickVolume;
		}

		public void setClickVolume(double clickVolume) {
			settings.clickVolume = clamp(clickVolume, 0.0D, 5.0D);
		}
	}

	public enum KeyboardGrouping {
		PER_ROW("Per Row"),
		PER_HAND("Per Hand");

		public static final Codec<KeyboardGrouping> CODEC = Codec.STRING.xmap(KeyboardGrouping::fromSerializedName, KeyboardGrouping::serializedName);

		private final String label;

		KeyboardGrouping(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public String serializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static KeyboardGrouping fromSerializedName(String serializedName) {
			return switch (serializedName.toLowerCase(Locale.ROOT)) {
				case "per_hand" -> PER_HAND;
				case "per_row" -> PER_ROW;
				default -> PER_ROW;
			};
		}
	}

	public enum KeyboardGroup {
		ROW1("Row 1"),
		ROW2("Row 2"),
		ROW3("Row 3"),
		ROW4("Row 4"),
		LEFT_HAND("Left Hand"),
		RIGHT_HAND("Right Hand");

		private final String label;

		KeyboardGroup(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}
}
