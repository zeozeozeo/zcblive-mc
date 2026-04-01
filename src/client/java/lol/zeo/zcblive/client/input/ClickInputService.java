package lol.zeo.zcblive.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.jspecify.annotations.Nullable;

import lol.zeo.zcblive.client.ZcbConfig;
import lol.zeo.zcblive.client.audio.ClickAudioService;
import lol.zeo.zcblive.client.clickpack.ClickSample;
import lol.zeo.zcblive.client.clickpack.ClickType;
import lol.zeo.zcblive.client.clickpack.LoadedClickpack;

public final class ClickInputService {
	private static final double FIRST_EVENT_DT = 0.151D;
	private static final double KEYBOARD_MERGE_WINDOW = 0.014D;
	private static final double KEYBOARD_CHORD_VOLUME_STEP = 0.16D;
	private static final double KEYBOARD_CHORD_VOLUME_FLOOR = 0.45D;

	private final Supplier<ZcbConfig> configSupplier;
	private final Supplier<@Nullable LoadedClickpack> keyboardClickpackSupplier;
	private final Supplier<@Nullable LoadedClickpack> mouseClickpackSupplier;
	private final ClickAudioService audioService;
	private final KeyboardState keyboard = new KeyboardState();
	private final LaneState mouse = new LaneState();

	public ClickInputService(
		Supplier<ZcbConfig> configSupplier,
		Supplier<@Nullable LoadedClickpack> keyboardClickpackSupplier,
		Supplier<@Nullable LoadedClickpack> mouseClickpackSupplier,
		ClickAudioService audioService
	) {
		this.configSupplier = configSupplier;
		this.keyboardClickpackSupplier = keyboardClickpackSupplier;
		this.mouseClickpackSupplier = mouseClickpackSupplier;
		this.audioService = audioService;
	}

	public void handleKeyboard(InputConstants.Key key, boolean press) {
		ZcbConfig config = configSupplier.get();
		if (config == null || config.inputMode == ZcbConfig.InputMode.MOUSE_ONLY) {
			return;
		}
		handleKeyboardLane(key, press);
	}

	public void handleMouse(int button, boolean press) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			return;
		}
		ZcbConfig config = configSupplier.get();
		if (config == null || config.inputMode == ZcbConfig.InputMode.KEYBOARD_ONLY) {
			return;
		}
		handleLane(mouse, button, press, false);
	}

	private void handleKeyboardLane(InputConstants.Key key, boolean press) {
		Minecraft minecraft = Minecraft.getInstance();
		ZcbConfig config = configSupplier.get();
		LoadedClickpack clickpack = keyboardClickpackSupplier.get();
		if (minecraft == null || minecraft.level == null || config == null || clickpack == null || !config.enabled) {
			return;
		}

		ButtonState state = keyboard.states.computeIfAbsent(key, ignored -> new ButtonState());
		if (state.pressed == press) {
			return;
		}

		double now = currentTimeSeconds();
		int otherPressedKeys = keyboardPressedCount(key);
		KeyboardHand hand = keyboardHand(key);
		HandState handState = keyboard.hands.get(hand);
		if (press) {
			state.pressVolumeFactor = keyboardChordVolumeFactor(otherPressedKeys);
		}

		double classificationDt = handState.lastGlobalEventTime > 0.0D ? now - handState.lastGlobalEventTime : Double.POSITIVE_INFINITY;
		ClickType clickType = ClickType.fromTime(press, classificationDt, config.timings);
		clickType = adjustKeyboardClickType(clickType, press, otherPressedKeys);
		ClickSample sample = resolveSample(clickpack, clickType, true);
		if (sample == null) {
			state.pressed = press;
			state.lastEventTime = now;
			handState.lastGlobalEventTime = now;
			return;
		}

		double spamDt = handState.lastGlobalEventTime > 0.0D ? now - handState.lastGlobalEventTime : Double.POSITIVE_INFINITY;
		double volume = calculateVolume(config, press, spamDt) * state.pressVolumeFactor;
		double pitch = config.pitchEnabled ? randomBetween(config.pitch.from, config.pitch.to) : 1.0D;
		boolean shouldPlay = now - handState.lastAudibleEventTime >= KEYBOARD_MERGE_WINDOW;
		if (shouldPlay) {
			audioService.play(sample, volume, pitch, config.clickVolume);
			handState.lastAudibleEventTime = now;
		}

		state.pressed = press;
		state.lastEventTime = now;
		handState.lastGlobalEventTime = now;
	}

	private void handleLane(LaneState laneState, Object inputId, boolean press, boolean keyboardLane) {
		Minecraft minecraft = Minecraft.getInstance();
		ZcbConfig config = configSupplier.get();
		LoadedClickpack clickpack = keyboardLane ? keyboardClickpackSupplier.get() : mouseClickpackSupplier.get();
		if (minecraft == null || minecraft.level == null || config == null || clickpack == null || !config.enabled) {
			return;
		}

		ButtonState state = laneState.states.computeIfAbsent(inputId, ignored -> new ButtonState());
		if (state.pressed == press) {
			return;
		}

		double now = currentTimeSeconds();
		double classificationDt = state.lastEventTime > 0.0D ? now - state.lastEventTime : FIRST_EVENT_DT;
		ClickType clickType = ClickType.fromTime(press, classificationDt, config.timings);
		ClickSample sample = resolveSample(clickpack, clickType, keyboardLane);
		if (sample == null) {
			state.pressed = press;
			state.lastEventTime = now;
			laneState.lastGlobalEventTime = now;
			return;
		}

		double spamDt = laneState.lastGlobalEventTime > 0.0D ? now - laneState.lastGlobalEventTime : Double.POSITIVE_INFINITY;
		double volume = calculateVolume(config, press, spamDt);
		if (keyboardLane) {
			volume *= state.pressVolumeFactor;
		}
		double pitch = config.pitchEnabled ? randomBetween(config.pitch.from, config.pitch.to) : 1.0D;
		boolean shouldPlay = !keyboardLane || now - laneState.lastAudibleEventTime >= KEYBOARD_MERGE_WINDOW;
		if (shouldPlay) {
			audioService.play(sample, volume, pitch, config.clickVolume);
			laneState.lastAudibleEventTime = now;
		}

		state.pressed = press;
		state.lastEventTime = now;
		laneState.lastGlobalEventTime = now;
	}

	private double calculateVolume(ZcbConfig config, boolean press, double spamDt) {
		double volume = 1.0D;
		ZcbConfig.VolumeSettings settings = config.volumeSettings;
		if (settings.volumeVar != 0.0D) {
			volume += randomBetween(-settings.volumeVar, settings.volumeVar);
		}
		if ((press || settings.changeReleasesVolume) && spamDt < settings.spamTime && settings.enabled) {
			double offset = (settings.spamTime - spamDt) * settings.spamVolOffsetFactor;
			volume -= Math.min(offset, settings.maxSpamVolOffset);
		}
		return Math.max(0.0D, volume * settings.globalVolume);
	}

	private double keyboardChordVolumeFactor(int otherPressedKeys) {
		return Math.max(KEYBOARD_CHORD_VOLUME_FLOOR, 1.0D - (otherPressedKeys * KEYBOARD_CHORD_VOLUME_STEP));
	}

	private int keyboardPressedCount(InputConstants.Key key) {
		int pressedKeys = 0;
		for (Map.Entry<InputConstants.Key, ButtonState> entry : keyboard.states.entrySet()) {
			if (entry.getKey().equals(key)) {
				continue;
			}
			if (entry.getValue().pressed) {
				pressedKeys++;
			}
		}
		return pressedKeys;
	}

	private ClickType adjustKeyboardClickType(ClickType clickType, boolean press, int otherPressedKeys) {
		if (otherPressedKeys <= 0) {
			return clickType;
		}
		if (press) {
			return switch (clickType) {
				case HARD_CLICK, CLICK -> ClickType.SOFT_CLICK;
				default -> clickType;
			};
		}
		return switch (clickType) {
			case HARD_RELEASE, RELEASE -> ClickType.SOFT_RELEASE;
			default -> clickType;
		};
	}

	private KeyboardHand keyboardHand(InputConstants.Key key) {
		return switch (key.getValue()) {
			case GLFW.GLFW_KEY_GRAVE_ACCENT,
				GLFW.GLFW_KEY_1,
				GLFW.GLFW_KEY_2,
				GLFW.GLFW_KEY_3,
				GLFW.GLFW_KEY_4,
				GLFW.GLFW_KEY_5,
				GLFW.GLFW_KEY_Q,
				GLFW.GLFW_KEY_W,
				GLFW.GLFW_KEY_E,
				GLFW.GLFW_KEY_R,
				GLFW.GLFW_KEY_T,
				GLFW.GLFW_KEY_A,
				GLFW.GLFW_KEY_S,
				GLFW.GLFW_KEY_D,
				GLFW.GLFW_KEY_F,
				GLFW.GLFW_KEY_G,
				GLFW.GLFW_KEY_Z,
				GLFW.GLFW_KEY_X,
				GLFW.GLFW_KEY_C,
				GLFW.GLFW_KEY_V,
				GLFW.GLFW_KEY_B,
				GLFW.GLFW_KEY_TAB,
				GLFW.GLFW_KEY_CAPS_LOCK,
				GLFW.GLFW_KEY_LEFT_SHIFT,
				GLFW.GLFW_KEY_LEFT_CONTROL,
				GLFW.GLFW_KEY_LEFT_ALT,
				GLFW.GLFW_KEY_LEFT_SUPER,
				GLFW.GLFW_KEY_ESCAPE,
				GLFW.GLFW_KEY_F1,
				GLFW.GLFW_KEY_F2,
				GLFW.GLFW_KEY_F3,
				GLFW.GLFW_KEY_F4,
				GLFW.GLFW_KEY_F5,
				GLFW.GLFW_KEY_F6 -> KeyboardHand.LEFT;
			default -> KeyboardHand.RIGHT;
		};
	}

	private ClickSample resolveSample(LoadedClickpack clickpack, ClickType clickType, boolean keyboardLane) {
		if (keyboardLane) {
			ClickSample sample = clickpack.randomKeyboardSample(clickType);
			if (sample == null) {
				sample = clickpack.randomMouseSample(clickType);
			}
			return sample;
		}

		ClickSample sample = clickpack.randomMouseSample(clickType);
		if (sample == null) {
			sample = clickpack.randomKeyboardSample(clickType);
		}
		return sample;
	}

	private double currentTimeSeconds() {
		return System.nanoTime() / 1_000_000_000.0D;
	}

	private double randomBetween(double min, double max) {
		if (min == max) {
			return min;
		}
		if (min > max) {
			double swapped = min;
			min = max;
			max = swapped;
		}
		return ThreadLocalRandom.current().nextDouble(min, max);
	}

	private static final class LaneState {
		private final Map<Object, ButtonState> states = new HashMap<>();
		private double lastGlobalEventTime;
		private double lastAudibleEventTime;
	}

	private static final class KeyboardState {
		private final Map<InputConstants.Key, ButtonState> states = new HashMap<>();
		private final EnumMap<KeyboardHand, HandState> hands = new EnumMap<>(KeyboardHand.class);

		private KeyboardState() {
			hands.put(KeyboardHand.LEFT, new HandState());
			hands.put(KeyboardHand.RIGHT, new HandState());
		}
	}

	private static final class HandState {
		private double lastGlobalEventTime;
		private double lastAudibleEventTime;
	}

	private static final class ButtonState {
		private boolean pressed;
		private double lastEventTime;
		private double pressVolumeFactor = 1.0D;
	}

	private enum KeyboardHand {
		LEFT,
		RIGHT
	}
}
