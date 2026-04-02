package lol.zeo.zcblive.client;

import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Locale;

public final class ZcbConfig {
	public String activeClickpack = "";
	public String activeKeyboardClickpack = "";
	public String activeMouseClickpack = "";
	public boolean enabled = true;
	public InputMode inputMode = InputMode.BOTH;
	public double clickVolume = 1.0D;
	public boolean pitchEnabled = true;
	public PitchSettings pitch = new PitchSettings();
	public Timings timings = new Timings();
	public VolumeSettings volumeSettings = new VolumeSettings();
	public boolean playNoise = false;
	public Boolean hardClicksEnabled = Boolean.TRUE;

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
		if (activeKeyboardClickpack.isBlank() && activeMouseClickpack.isBlank() && !activeClickpack.isBlank()) {
			activeKeyboardClickpack = activeClickpack;
			activeMouseClickpack = activeClickpack;
		}
		activeClickpack = "";
		if (inputMode == null) {
			inputMode = InputMode.BOTH;
		}
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
		if (hardClicksEnabled == null) {
			hardClicksEnabled = Boolean.TRUE;
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
	}

	public static final class Timings {
		public double hard = 2.0D;
		public double regular = 0.15D;
		public double soft = 0.025D;
	}

	public static final class VolumeSettings {
		public boolean enabled = true;
		public double spamTime = 0.3D;
		public double spamVolOffsetFactor = 1.3D;
		public double maxSpamVolOffset = 0.6D;
		public boolean changeReleasesVolume = false;
		public double globalVolume = 1.0D;
		public double volumeVar = 0.2D;
	}
}
