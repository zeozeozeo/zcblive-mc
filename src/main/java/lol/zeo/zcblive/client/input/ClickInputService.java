package lol.zeo.zcblive.client.input;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lol.zeo.zcblive.client.ZcbConfig;
import lol.zeo.zcblive.client.audio.ClickAudioService;
import lol.zeo.zcblive.client.clickpack.ClickSample;
import lol.zeo.zcblive.client.clickpack.ClickType;
import lol.zeo.zcblive.client.clickpack.LoadedClickpack;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public final class ClickInputService {
    private static final int LEFT_MOUSE_BUTTON = 0;
    private static final int RIGHT_MOUSE_BUTTON = 1;
    private static final int SIDE_MOUSE_BUTTON_1 = 3;
    private static final int SIDE_MOUSE_BUTTON_2 = 4;

    private static final double FIRST_EVENT_DT = 0.151D;
    private static final double KEYBOARD_MERGE_WINDOW = 0.014D;
    private static final double KEYBOARD_CHORD_VOLUME_STEP = 0.16D;
    private static final double KEYBOARD_CHORD_VOLUME_FLOOR = 0.45D;
    private static final double DUPLICATE_MOUSE_EVENT_WINDOW = 0.01D;

    private final Supplier<ZcbConfig> configSupplier;
    private final Supplier<LoadedClickpack> keyboardClickpackSupplier;
    private final Supplier<LoadedClickpack> mouseClickpackSupplier;
    private final ClickAudioService audioService;
    private final KeyboardState keyboard = new KeyboardState();
    private final LaneState mouse = new LaneState();
    private int lastMouseEventButton = Integer.MIN_VALUE;
    private boolean lastMouseEventPress;
    private double lastMouseEventTime = Double.NEGATIVE_INFINITY;

    public ClickInputService(
        Supplier<ZcbConfig> configSupplier,
        Supplier<LoadedClickpack> keyboardClickpackSupplier,
        Supplier<LoadedClickpack> mouseClickpackSupplier,
        ClickAudioService audioService
    ) {
        this.configSupplier = configSupplier;
        this.keyboardClickpackSupplier = keyboardClickpackSupplier;
        this.mouseClickpackSupplier = mouseClickpackSupplier;
        this.audioService = audioService;
    }

    public void handleKeyboard(int keyCode, boolean press) {
        handleKeyboard(keyCode, press, -1L);
    }

    public void handleKeyboard(int keyCode, boolean press, long eventNanos) {
        ZcbConfig config = configSupplier.get();
        if (config == null || config.inputMode == ZcbConfig.InputMode.MOUSE_ONLY) {
            return;
        }
        handleKeyboardLane(keyCode, press, resolveEventTimeSeconds(eventNanos));
    }

    public void handleMouse(int button, boolean press) {
        handleMouse(button, press, -1L);
    }

    public void handleMouse(int button, boolean press, long eventNanos) {
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

        double eventTimeSeconds = resolveEventTimeSeconds(eventNanos);
        if (isSideMouseButton(button)) {
            handleLane(mouse, Integer.valueOf(button), press, false, eventTimeSeconds, ClickType.MICRO_RELEASE);
            return;
        }
        handleLane(mouse, Integer.valueOf(button), press, false, eventTimeSeconds, null);
    }

    public void handleScreenMouse(int button, boolean press) {
        handleMouse(button, press);
    }

    private void handleKeyboardLane(int keyCode, boolean press, double eventTimeSeconds) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ZcbConfig config = configSupplier.get();
        LoadedClickpack primaryClickpack = keyboardClickpackSupplier.get();
        LoadedClickpack secondaryClickpack = mouseClickpackSupplier.get();
        if (minecraft == null || minecraft.world == null || config == null || !config.enabled || (primaryClickpack == null && secondaryClickpack == null)) {
            return;
        }

        Integer key = Integer.valueOf(keyCode);
        ButtonState state = keyboard.states.get(key);
        if (state == null) {
            state = new ButtonState();
            keyboard.states.put(key, state);
        }
        if (state.pressed == press) {
            return;
        }

        double now = eventTimeSeconds;
        int otherPressedKeys = keyboardPressedCount(key);
        KeyboardHand hand = keyboardHand(keyCode);
        HandState handState = keyboard.hands.get(hand);
        if (press) {
            state.pressVolumeFactor = keyboardChordVolumeFactor(otherPressedKeys);
        }

        double classificationDt = handState.lastGlobalEventTime > 0.0D ? now - handState.lastGlobalEventTime : Double.POSITIVE_INFINITY;
        ClickType clickType = ClickType.fromTime(press, classificationDt, config.timings, hardClicksEnabled(config));
        clickType = adjustKeyboardClickType(clickType, press, otherPressedKeys);
        ClickSample sample = resolveSample(primaryClickpack, secondaryClickpack, clickType, true, hardClicksEnabled(config));
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

    private void handleLane(
        LaneState laneState,
        Object inputId,
        boolean press,
        boolean keyboardLane,
        double eventTimeSeconds,
        ClickType forcedClickType
    ) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ZcbConfig config = configSupplier.get();
        LoadedClickpack primaryClickpack = keyboardLane ? keyboardClickpackSupplier.get() : mouseClickpackSupplier.get();
        LoadedClickpack secondaryClickpack = keyboardLane ? mouseClickpackSupplier.get() : keyboardClickpackSupplier.get();
        if (minecraft == null || minecraft.world == null || config == null || !config.enabled || (primaryClickpack == null && secondaryClickpack == null)) {
            return;
        }

        ButtonState state = laneState.states.get(inputId);
        if (state == null) {
            state = new ButtonState();
            laneState.states.put(inputId, state);
        }
        if (state.pressed == press) {
            return;
        }

        double now = eventTimeSeconds;
        ClickType clickType;
        if (forcedClickType != null) {
            clickType = forcedClickType;
        } else {
            double classificationDt = state.lastEventTime > 0.0D ? now - state.lastEventTime : FIRST_EVENT_DT;
            clickType = ClickType.fromTime(press, classificationDt, config.timings, hardClicksEnabled(config));
        }
        ClickSample sample = resolveSample(primaryClickpack, secondaryClickpack, clickType, keyboardLane, hardClicksEnabled(config));
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

    private boolean isSupportedMouseButton(int button) {
        return button == LEFT_MOUSE_BUTTON
            || button == RIGHT_MOUSE_BUTTON
            || button == SIDE_MOUSE_BUTTON_1
            || button == SIDE_MOUSE_BUTTON_2;
    }

    private boolean isSideMouseButton(int button) {
        return button == SIDE_MOUSE_BUTTON_1 || button == SIDE_MOUSE_BUTTON_2;
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

    private int keyboardPressedCount(Integer activeKey) {
        int pressed = 0;
        for (Map.Entry<Integer, ButtonState> entry : keyboard.states.entrySet()) {
            if (activeKey.equals(entry.getKey())) {
                continue;
            }
            if (entry.getValue().pressed) {
                pressed++;
            }
        }
        return pressed;
    }

    private ClickType adjustKeyboardClickType(ClickType clickType, boolean press, int otherPressedKeys) {
        if (otherPressedKeys <= 0) {
            return clickType;
        }

        if (press) {
            if (clickType == ClickType.HARD_CLICK || clickType == ClickType.CLICK) {
                return ClickType.SOFT_CLICK;
            }
            return clickType;
        }

        if (clickType == ClickType.HARD_RELEASE || clickType == ClickType.RELEASE) {
            return ClickType.SOFT_RELEASE;
        }
        return clickType;
    }

    private KeyboardHand keyboardHand(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_GRAVE:
            case Keyboard.KEY_1:
            case Keyboard.KEY_2:
            case Keyboard.KEY_3:
            case Keyboard.KEY_4:
            case Keyboard.KEY_5:
            case Keyboard.KEY_Q:
            case Keyboard.KEY_W:
            case Keyboard.KEY_E:
            case Keyboard.KEY_R:
            case Keyboard.KEY_T:
            case Keyboard.KEY_A:
            case Keyboard.KEY_S:
            case Keyboard.KEY_D:
            case Keyboard.KEY_F:
            case Keyboard.KEY_G:
            case Keyboard.KEY_Z:
            case Keyboard.KEY_X:
            case Keyboard.KEY_C:
            case Keyboard.KEY_V:
            case Keyboard.KEY_B:
            case Keyboard.KEY_TAB:
            case Keyboard.KEY_CAPITAL:
            case Keyboard.KEY_LSHIFT:
            case Keyboard.KEY_LCONTROL:
            case Keyboard.KEY_LMENU:
            case Keyboard.KEY_LMETA:
            case Keyboard.KEY_ESCAPE:
            case Keyboard.KEY_F1:
            case Keyboard.KEY_F2:
            case Keyboard.KEY_F3:
            case Keyboard.KEY_F4:
            case Keyboard.KEY_F5:
            case Keyboard.KEY_F6:
                return KeyboardHand.LEFT;
            default:
                return KeyboardHand.RIGHT;
        }
    }

    private boolean hardClicksEnabled(ZcbConfig config) {
        return config != null && (config.hardClicksEnabled == null || config.hardClicksEnabled.booleanValue());
    }

    private ClickSample resolveSample(
        LoadedClickpack primaryClickpack,
        LoadedClickpack secondaryClickpack,
        ClickType clickType,
        boolean keyboardLane,
        boolean allowHardClicks
    ) {
        ClickSample sample = resolveSampleFromPack(primaryClickpack, clickType, keyboardLane, allowHardClicks);
        if (sample != null) {
            return sample;
        }
        return resolveSampleFromPack(secondaryClickpack, clickType, keyboardLane, allowHardClicks);
    }

    private ClickSample resolveSampleFromPack(LoadedClickpack clickpack, ClickType clickType, boolean keyboardLane, boolean allowHardClicks) {
        if (clickpack == null) {
            return null;
        }
        if (keyboardLane) {
            ClickSample sample = clickpack.randomKeyboardSample(clickType, allowHardClicks);
            if (sample == null) {
                sample = clickpack.randomMouseSample(clickType, allowHardClicks);
            }
            return sample;
        }

        ClickSample sample = clickpack.randomMouseSample(clickType, allowHardClicks);
        if (sample == null) {
            sample = clickpack.randomKeyboardSample(clickType, allowHardClicks);
        }
        return sample;
    }

    private double currentTimeSeconds() {
        return System.nanoTime() / 1_000_000_000.0D;
    }

    private double resolveEventTimeSeconds(long eventNanos) {
        if (eventNanos > 0L) {
            return eventNanos / 1_000_000_000.0D;
        }
        return currentTimeSeconds();
    }

    private double randomBetween(double min, double max) {
        if (min == max) {
            return min;
        }
        if (min > max) {
            double swap = min;
            min = max;
            max = swap;
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
        private final Map<Object, ButtonState> states = new HashMap<Object, ButtonState>();
        private double lastGlobalEventTime;
        private double lastAudibleEventTime;
    }

    private static final class KeyboardState {
        private final Map<Integer, ButtonState> states = new HashMap<Integer, ButtonState>();
        private final EnumMap<KeyboardHand, HandState> hands = new EnumMap<KeyboardHand, HandState>(KeyboardHand.class);

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
