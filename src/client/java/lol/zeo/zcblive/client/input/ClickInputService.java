package lol.zeo.zcblive.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
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
	private static final double DUPLICATE_MOUSE_EVENT_WINDOW = 0.01D;

	private final Supplier<ZcbConfig> configSupplier;
	private final Supplier<@Nullable LoadedClickpack> keyboardClickpackSupplier;
	private final Supplier<@Nullable LoadedClickpack> mouseClickpackSupplier;
	private final ClickAudioService audioService;
	private final KeyboardState keyboard = new KeyboardState();
	private final LaneState mouse = new LaneState();
	private int lastMouseEventButton = Integer.MIN_VALUE;
	private boolean lastMouseEventPress;
	private double lastMouseEventTime = Double.NEGATIVE_INFINITY;

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
		if (!isSupportedMouseButton(button)) {
			return;
		}
		ZcbConfig config = configSupplier.get();
		if (config == null || config.inputMode == ZcbConfig.InputMode.KEYBOARD_ONLY) {
			return;
		}
		if (isDuplicateMouseEvent(button, press)) {
			return;
		}
		if (isSideMouseButton(button)) {
			handleSideMouse(button, press);
			return;
		}
		handleLane(mouse, button, press, false);
	}

	public void handleScreenMouse(int button, boolean press) {
		handleMouse(button, press);
	}

	private void handleSideMouse(int button, boolean press) {
		Minecraft minecraft = Minecraft.getInstance();
		ZcbConfig config = configSupplier.get();
		ZcbConfig.LaneSettings settings = config == null ? null : config.mouse;
		LoadedClickpack primaryClickpack = mouseClickpackSupplier.get();
		LoadedClickpack secondaryClickpack = keyboardClickpackSupplier.get();
		if (minecraft == null || minecraft.level == null || config == null || settings == null || !config.enabled || (primaryClickpack == null && secondaryClickpack == null)) {
			return;
		}

		ButtonState state = mouse.states.computeIfAbsent(button, ignored -> new ButtonState());
		if (state.pressed == press) {
			return;
		}

		double now = currentTimeSeconds();
		double classificationDt = state.lastEventTime > 0.0D ? now - state.lastEventTime : FIRST_EVENT_DT;
		ClickType clickType = ClickType.fromTime(press, classificationDt, settings.timings);
		Predicate<ClickType> allowedType = allowedType(settings);
		ClickSample sample = resolveSideOverlaySample(primaryClickpack, button, clickType, allowedType);
		if (sample == null) {
			sample = resolveMouseSampleFromPack(primaryClickpack, button, ClickType.MICRO_RELEASE, settings);
		}
		if (sample == null) {
			sample = resolveSideOverlaySample(secondaryClickpack, button, clickType, allowedType);
		}
		if (sample == null) {
			sample = resolveMouseSampleFromPack(secondaryClickpack, button, ClickType.MICRO_RELEASE, settings);
		}
		if (sample == null) {
			state.pressed = press;
			state.lastEventTime = now;
			mouse.lastGlobalEventTime = now;
			return;
		}

		double spamDt = mouse.lastGlobalEventTime > 0.0D ? now - mouse.lastGlobalEventTime : Double.POSITIVE_INFINITY;
		double volume = calculateVolume(settings, press, spamDt);
		double pitch = settings.pitchEnabled ? randomBetween(settings.pitch.from, settings.pitch.to) : 1.0D;
		audioService.play(sample, volume, pitch, settings.clickVolume);

		state.pressed = press;
		state.lastEventTime = now;
		mouse.lastGlobalEventTime = now;
		mouse.lastAudibleEventTime = now;
	}

	private void handleKeyboardLane(InputConstants.Key key, boolean press) {
		Minecraft minecraft = Minecraft.getInstance();
		ZcbConfig config = configSupplier.get();
		ZcbConfig.KeyboardSettings keyboardSettings = config == null ? null : config.keyboard;
		LoadedClickpack primaryClickpack = keyboardClickpackSupplier.get();
		LoadedClickpack secondaryClickpack = mouseClickpackSupplier.get();
		if (minecraft == null || minecraft.level == null || config == null || keyboardSettings == null || !config.enabled || (primaryClickpack == null && secondaryClickpack == null)) {
			return;
		}

		ButtonState state = keyboard.states.computeIfAbsent(key, ignored -> new ButtonState());
		if (state.pressed == press) {
			return;
		}

		double now = currentTimeSeconds();
		int otherPressedKeys = keyboardPressedCount(key);
		ZcbConfig.KeyboardGroup group = keyboardGroup(key, keyboardSettings.grouping);
		GroupState groupState = keyboard.groups.get(group);
		if (press) {
			state.pressVolumeFactor = keyboardChordVolumeFactor(otherPressedKeys);
		}

		ZcbConfig.LaneSettings settings = keyboardSettings.settings;
		double classificationDt = groupState.lastGlobalEventTime > 0.0D ? now - groupState.lastGlobalEventTime : Double.POSITIVE_INFINITY;
		ClickType clickType = ClickType.fromTime(press, classificationDt, settings.timings);
		clickType = adjustKeyboardClickType(clickType, press, otherPressedKeys);
		ClickSample sample = resolveKeyboardSample(primaryClickpack, secondaryClickpack, key, clickType, settings);
		if (sample == null) {
			state.pressed = press;
			state.lastEventTime = now;
			groupState.lastGlobalEventTime = now;
			return;
		}

		double spamDt = groupState.lastGlobalEventTime > 0.0D ? now - groupState.lastGlobalEventTime : Double.POSITIVE_INFINITY;
		double volume = calculateVolume(settings, press, spamDt) * state.pressVolumeFactor;
		double pitch = settings.pitchEnabled ? randomBetween(settings.pitch.from, settings.pitch.to) : 1.0D;
		boolean shouldPlay = now - groupState.lastAudibleEventTime >= KEYBOARD_MERGE_WINDOW;
		if (shouldPlay) {
			audioService.play(sample, volume, pitch, settings.clickVolume);
			groupState.lastAudibleEventTime = now;
		}

		state.pressed = press;
		state.lastEventTime = now;
		groupState.lastGlobalEventTime = now;
	}

	private void handleLane(LaneState laneState, Object inputId, boolean press, boolean keyboardLane) {
		handleLane(laneState, inputId, press, keyboardLane, null);
	}

	private void handleLane(LaneState laneState, Object inputId, boolean press, boolean keyboardLane, @Nullable ClickType forcedClickType) {
		Minecraft minecraft = Minecraft.getInstance();
		ZcbConfig config = configSupplier.get();
		ZcbConfig.LaneSettings settings = config == null ? null : config.mouse;
		LoadedClickpack primaryClickpack = mouseClickpackSupplier.get();
		LoadedClickpack secondaryClickpack = keyboardClickpackSupplier.get();
		if (minecraft == null || minecraft.level == null || config == null || settings == null || !config.enabled || (primaryClickpack == null && secondaryClickpack == null)) {
			return;
		}

		ButtonState state = laneState.states.computeIfAbsent(inputId, ignored -> new ButtonState());
		if (state.pressed == press) {
			return;
		}

		double now = currentTimeSeconds();
		ClickType clickType;
		if (forcedClickType != null) {
			clickType = forcedClickType;
		} else {
			double classificationDt = state.lastEventTime > 0.0D ? now - state.lastEventTime : FIRST_EVENT_DT;
			clickType = ClickType.fromTime(press, classificationDt, settings.timings);
		}
		int button = inputId instanceof Integer ? (Integer) inputId : -1;
		ClickSample sample = resolveMouseSample(primaryClickpack, secondaryClickpack, button, clickType, settings);
		if (sample == null) {
			state.pressed = press;
			state.lastEventTime = now;
			laneState.lastGlobalEventTime = now;
			return;
		}

		double spamDt = laneState.lastGlobalEventTime > 0.0D ? now - laneState.lastGlobalEventTime : Double.POSITIVE_INFINITY;
		double volume = calculateVolume(settings, press, spamDt);
		if (keyboardLane) {
			volume *= state.pressVolumeFactor;
		}
		double pitch = settings.pitchEnabled ? randomBetween(settings.pitch.from, settings.pitch.to) : 1.0D;
		boolean shouldPlay = !keyboardLane || now - laneState.lastAudibleEventTime >= KEYBOARD_MERGE_WINDOW;
		if (shouldPlay) {
			audioService.play(sample, volume, pitch, settings.clickVolume);
			laneState.lastAudibleEventTime = now;
		}

		state.pressed = press;
		state.lastEventTime = now;
		laneState.lastGlobalEventTime = now;
	}

	private boolean isSupportedMouseButton(int button) {
		return button == GLFW.GLFW_MOUSE_BUTTON_LEFT
			|| button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
			|| button == GLFW.GLFW_MOUSE_BUTTON_4
			|| button == GLFW.GLFW_MOUSE_BUTTON_5;
	}

	private boolean isSideMouseButton(int button) {
		return button == GLFW.GLFW_MOUSE_BUTTON_4 || button == GLFW.GLFW_MOUSE_BUTTON_5;
	}

	private double calculateVolume(ZcbConfig.LaneSettings settings, boolean press, double spamDt) {
		double volume = 1.0D;
		if (settings.volumeSettings.volumeVar != 0.0D) {
			volume += randomBetween(-settings.volumeSettings.volumeVar, settings.volumeSettings.volumeVar);
		}
		if ((press || settings.volumeSettings.changeReleasesVolume) && spamDt < settings.volumeSettings.spamTime && settings.volumeSettings.enabled) {
			double offset = (settings.volumeSettings.spamTime - spamDt) * settings.volumeSettings.spamVolOffsetFactor;
			volume -= Math.min(offset, settings.volumeSettings.maxSpamVolOffset);
		}
		return Math.max(0.0D, volume * settings.volumeSettings.globalVolume);
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

	private ZcbConfig.KeyboardGroup keyboardGroup(InputConstants.Key key, ZcbConfig.KeyboardGrouping grouping) {
		return switch (grouping) {
			case PER_ROW -> keyboardRow(key);
			case PER_HAND -> keyboardHand(key);
		};
	}

	private ZcbConfig.KeyboardGroup keyboardRow(InputConstants.Key key) {
		return switch (key.getValue()) {
			case GLFW.GLFW_KEY_GRAVE_ACCENT,
				GLFW.GLFW_KEY_1,
				GLFW.GLFW_KEY_2,
				GLFW.GLFW_KEY_3,
				GLFW.GLFW_KEY_4,
				GLFW.GLFW_KEY_5,
				GLFW.GLFW_KEY_6,
				GLFW.GLFW_KEY_7,
				GLFW.GLFW_KEY_8,
				GLFW.GLFW_KEY_9,
				GLFW.GLFW_KEY_0,
				GLFW.GLFW_KEY_MINUS,
				GLFW.GLFW_KEY_EQUAL,
				GLFW.GLFW_KEY_F1,
				GLFW.GLFW_KEY_F2,
				GLFW.GLFW_KEY_F3,
				GLFW.GLFW_KEY_F4,
				GLFW.GLFW_KEY_F5,
				GLFW.GLFW_KEY_F6,
				GLFW.GLFW_KEY_F7,
				GLFW.GLFW_KEY_F8,
				GLFW.GLFW_KEY_F9,
				GLFW.GLFW_KEY_F10,
				GLFW.GLFW_KEY_F11,
				GLFW.GLFW_KEY_F12,
				GLFW.GLFW_KEY_F13,
				GLFW.GLFW_KEY_F14,
				GLFW.GLFW_KEY_F15,
				GLFW.GLFW_KEY_F16,
				GLFW.GLFW_KEY_F17,
				GLFW.GLFW_KEY_F18,
				GLFW.GLFW_KEY_F19,
				GLFW.GLFW_KEY_F20,
				GLFW.GLFW_KEY_F21,
				GLFW.GLFW_KEY_F22,
				GLFW.GLFW_KEY_F23,
				GLFW.GLFW_KEY_F24,
				GLFW.GLFW_KEY_F25 -> ZcbConfig.KeyboardGroup.ROW1;
			case GLFW.GLFW_KEY_Q,
				GLFW.GLFW_KEY_W,
				GLFW.GLFW_KEY_E,
				GLFW.GLFW_KEY_R,
				GLFW.GLFW_KEY_T,
				GLFW.GLFW_KEY_Y,
				GLFW.GLFW_KEY_U,
				GLFW.GLFW_KEY_I,
				GLFW.GLFW_KEY_O,
				GLFW.GLFW_KEY_P,
				GLFW.GLFW_KEY_LEFT_BRACKET,
				GLFW.GLFW_KEY_RIGHT_BRACKET,
				GLFW.GLFW_KEY_BACKSLASH,
				GLFW.GLFW_KEY_BACKSPACE,
				GLFW.GLFW_KEY_ENTER,
				GLFW.GLFW_KEY_KP_ENTER,
				GLFW.GLFW_KEY_TAB -> ZcbConfig.KeyboardGroup.ROW2;
			case GLFW.GLFW_KEY_A,
				GLFW.GLFW_KEY_S,
				GLFW.GLFW_KEY_D,
				GLFW.GLFW_KEY_F,
				GLFW.GLFW_KEY_G,
				GLFW.GLFW_KEY_H,
				GLFW.GLFW_KEY_J,
				GLFW.GLFW_KEY_K,
				GLFW.GLFW_KEY_L,
				GLFW.GLFW_KEY_SEMICOLON,
				GLFW.GLFW_KEY_APOSTROPHE -> ZcbConfig.KeyboardGroup.ROW3;
			case GLFW.GLFW_KEY_Z,
				GLFW.GLFW_KEY_X,
				GLFW.GLFW_KEY_C,
				GLFW.GLFW_KEY_V,
				GLFW.GLFW_KEY_B,
				GLFW.GLFW_KEY_N,
				GLFW.GLFW_KEY_M,
				GLFW.GLFW_KEY_COMMA,
				GLFW.GLFW_KEY_PERIOD,
				GLFW.GLFW_KEY_SLASH,
				GLFW.GLFW_KEY_SPACE,
				GLFW.GLFW_KEY_LEFT_SHIFT,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				GLFW.GLFW_KEY_LEFT_CONTROL,
				GLFW.GLFW_KEY_RIGHT_CONTROL,
				GLFW.GLFW_KEY_LEFT_ALT,
				GLFW.GLFW_KEY_RIGHT_ALT,
				GLFW.GLFW_KEY_LEFT_SUPER,
				GLFW.GLFW_KEY_RIGHT_SUPER,
				GLFW.GLFW_KEY_CAPS_LOCK,
				GLFW.GLFW_KEY_ESCAPE,
				GLFW.GLFW_KEY_LEFT,
				GLFW.GLFW_KEY_RIGHT,
				GLFW.GLFW_KEY_UP,
				GLFW.GLFW_KEY_DOWN,
				GLFW.GLFW_KEY_HOME,
				GLFW.GLFW_KEY_END,
				GLFW.GLFW_KEY_PAGE_UP,
				GLFW.GLFW_KEY_PAGE_DOWN,
				GLFW.GLFW_KEY_INSERT,
				GLFW.GLFW_KEY_DELETE,
				GLFW.GLFW_KEY_KP_0,
				GLFW.GLFW_KEY_KP_1,
				GLFW.GLFW_KEY_KP_2,
				GLFW.GLFW_KEY_KP_3,
				GLFW.GLFW_KEY_KP_4,
				GLFW.GLFW_KEY_KP_5,
				GLFW.GLFW_KEY_KP_6,
				GLFW.GLFW_KEY_KP_7,
				GLFW.GLFW_KEY_KP_8,
				GLFW.GLFW_KEY_KP_9,
				GLFW.GLFW_KEY_KP_DECIMAL,
				GLFW.GLFW_KEY_KP_DIVIDE,
				GLFW.GLFW_KEY_KP_MULTIPLY,
				GLFW.GLFW_KEY_KP_SUBTRACT,
				GLFW.GLFW_KEY_KP_ADD,
				GLFW.GLFW_KEY_KP_EQUAL -> ZcbConfig.KeyboardGroup.ROW4;
			default -> ZcbConfig.KeyboardGroup.ROW4;
		};
	}

	private ZcbConfig.KeyboardGroup keyboardHand(InputConstants.Key key) {
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
				GLFW.GLFW_KEY_F6 -> ZcbConfig.KeyboardGroup.LEFT_HAND;
			default -> ZcbConfig.KeyboardGroup.RIGHT_HAND;
		};
	}

	private @Nullable ClickSample resolveKeyboardSample(
		@Nullable LoadedClickpack primaryClickpack,
		@Nullable LoadedClickpack secondaryClickpack,
		InputConstants.Key key,
		ClickType clickType,
		ZcbConfig.LaneSettings settings
	) {
		ClickSample sample = resolveKeyboardSampleFromPack(primaryClickpack, key, clickType, settings);
		if (sample != null) {
			return sample;
		}
		return resolveKeyboardSampleFromPack(secondaryClickpack, key, clickType, settings);
	}

	private @Nullable ClickSample resolveKeyboardSampleFromPack(
		@Nullable LoadedClickpack clickpack,
		InputConstants.Key key,
		ClickType clickType,
		ZcbConfig.LaneSettings settings
	) {
		if (clickpack == null) {
			return null;
		}
		Predicate<ClickType> allowedType = allowedType(settings);
		ClickSample sample = clickpack.randomKeyboardOverlaySample(key, clickType, allowedType);
		if (sample != null) {
			return sample;
		}
		sample = clickpack.randomKeyboardSample(clickType, allowedType);
		if (sample != null) {
			return sample;
		}
		return clickpack.randomMouseSample(clickType, allowedType);
	}

	private @Nullable ClickSample resolveMouseSample(
		@Nullable LoadedClickpack primaryClickpack,
		@Nullable LoadedClickpack secondaryClickpack,
		int button,
		ClickType clickType,
		ZcbConfig.LaneSettings settings
	) {
		ClickSample sample = resolveMouseSampleFromPack(primaryClickpack, button, clickType, settings);
		if (sample != null) {
			return sample;
		}
		return resolveMouseSampleFromPack(secondaryClickpack, button, clickType, settings);
	}

	private @Nullable ClickSample resolveMouseSampleFromPack(
		@Nullable LoadedClickpack clickpack,
		int button,
		ClickType clickType,
		ZcbConfig.LaneSettings settings
	) {
		if (clickpack == null) {
			return null;
		}
		Predicate<ClickType> allowedType = allowedType(settings);
		if (isSideMouseButton(button)) {
			ClickSample sample = resolveSideOverlaySample(clickpack, button, clickType, allowedType);
			if (sample != null) {
				return sample;
			}
		}
		ClickSample sample = clickpack.randomMouseSample(clickType, allowedType);
		if (sample != null) {
			return sample;
		}
		return clickpack.randomKeyboardSample(clickType, allowedType);
	}

	private @Nullable ClickSample resolveSideOverlaySample(
		@Nullable LoadedClickpack clickpack,
		int button,
		ClickType clickType,
		Predicate<ClickType> allowedType
	) {
		if (clickpack == null || !isSideMouseButton(button)) {
			return null;
		}
		return clickpack.randomMouseOverlaySample(button, clickType, allowedType);
	}

	private Predicate<ClickType> allowedType(ZcbConfig.LaneSettings settings) {
		return type -> !settings.isClickTypeIgnored(type);
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

	private boolean isDuplicateMouseEvent(int button, boolean press) {
		double now = currentTimeSeconds();
		boolean duplicate = lastMouseEventButton == button
			&& lastMouseEventPress == press
			&& now - lastMouseEventTime <= DUPLICATE_MOUSE_EVENT_WINDOW;
		lastMouseEventButton = button;
		lastMouseEventPress = press;
		lastMouseEventTime = now;
		return duplicate;
	}

	private static final class LaneState {
		private final Map<Object, ButtonState> states = new HashMap<>();
		private double lastGlobalEventTime;
		private double lastAudibleEventTime;
	}

	private static final class KeyboardState {
		private final Map<InputConstants.Key, ButtonState> states = new HashMap<>();
		private final EnumMap<ZcbConfig.KeyboardGroup, GroupState> groups = new EnumMap<>(ZcbConfig.KeyboardGroup.class);

		private KeyboardState() {
			for (ZcbConfig.KeyboardGroup group : ZcbConfig.KeyboardGroup.values()) {
				groups.put(group, new GroupState());
			}
		}
	}

	private static final class GroupState {
		private double lastGlobalEventTime;
		private double lastAudibleEventTime;
	}

	private static final class ButtonState {
		private boolean pressed;
		private double lastEventTime;
		private double pressVolumeFactor = 1.0D;
	}

}
