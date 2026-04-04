package lol.zeo.zcblive.client.clickpack;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class ClickpackOverlay implements AutoCloseable {
	private final EnumMap<KeyboardBucket, PlayerClicks> keyboardBuckets = new EnumMap<>(KeyboardBucket.class);
	private final EnumMap<MouseBucket, PlayerClicks> mouseBuckets = new EnumMap<>(MouseBucket.class);

	public void addKeyboardBucket(KeyboardBucket bucket, PlayerClicks clicks) {
		keyboardBuckets.put(bucket, clicks);
	}

	public void addMouseBucket(MouseBucket bucket, PlayerClicks clicks) {
		mouseBuckets.put(bucket, clicks);
	}

	public boolean hasKeyboardSamples() {
		return !keyboardBuckets.isEmpty();
	}

	public boolean hasMouseSamples() {
		return !mouseBuckets.isEmpty();
	}

	public boolean isEmpty() {
		return keyboardBuckets.isEmpty() && mouseBuckets.isEmpty();
	}

	public @Nullable ClickSample randomKeyboardSample(InputConstants.Key key, ClickType preferredType, Predicate<ClickType> allowedType) {
		KeyboardBucket bucket = KeyboardBucket.fromKey(key);
		if (bucket == null) {
			return null;
		}
		return randomKeyboardBucket(bucket, preferredType, allowedType);
	}

	public @Nullable ClickSample randomMouseSample(int button, ClickType preferredType, Predicate<ClickType> allowedType) {
		MouseBucket bucket = MouseBucket.fromButton(button);
		if (bucket == null) {
			return null;
		}
		return randomMouseBucket(bucket, preferredType, allowedType);
	}

	private @Nullable ClickSample randomKeyboardBucket(KeyboardBucket bucket, ClickType preferredType, Predicate<ClickType> allowedType) {
		PlayerClicks clicks = keyboardBuckets.get(bucket);
		if (clicks != null) {
			ClickSample sample = clicks.randomSample(preferredType, allowedType);
			if (sample != null) {
				return sample;
			}
		}
		KeyboardBucket fallback = bucket.fallback();
		if (fallback == null) {
			return null;
		}
		clicks = keyboardBuckets.get(fallback);
		return clicks == null ? null : clicks.randomSample(preferredType, allowedType);
	}

	private @Nullable ClickSample randomMouseBucket(MouseBucket bucket, ClickType preferredType, Predicate<ClickType> allowedType) {
		PlayerClicks clicks = mouseBuckets.get(bucket);
		if (clicks != null) {
			ClickSample sample = clicks.randomSample(preferredType, allowedType);
			if (sample != null) {
				return sample;
			}
		}
		MouseBucket fallback = bucket.fallback();
		if (fallback == null) {
			return null;
		}
		clicks = mouseBuckets.get(fallback);
		return clicks == null ? null : clicks.randomSample(preferredType, allowedType);
	}

	@Override
	public void close() {
		for (PlayerClicks clicks : keyboardBuckets.values()) {
			clicks.close();
		}
		for (PlayerClicks clicks : mouseBuckets.values()) {
			clicks.close();
		}
	}

	public enum KeyboardBucket {
		ROW1(true, false),
		ROW2(true, false),
		ROW3(true, false),
		ROW4(true, false),
		SPACEBAR(false, true),
		BACKSPACE(false, true),
		ENTER(false, true),
		TAB(false, true),
		MODIFIERS(false, true),
		NAVIGATION(false, true),
		FUNCTION(false, true),
		NUMPAD(false, true),
		OTHER(false, false);

		private final boolean row;
		private final boolean fallbackToOther;

		KeyboardBucket(boolean row, boolean fallbackToOther) {
			this.row = row;
			this.fallbackToOther = fallbackToOther;
		}

		public boolean isRow() {
			return row;
		}

		public @Nullable KeyboardBucket fallback() {
			return fallbackToOther ? OTHER : null;
		}

		public static @Nullable KeyboardBucket fromDirectoryName(String directoryName) {
			return switch (normalize(directoryName)) {
				case "row1" -> ROW1;
				case "row2" -> ROW2;
				case "row3" -> ROW3;
				case "row4" -> ROW4;
				case "space", "spacebar" -> SPACEBAR;
				case "backspace" -> BACKSPACE;
				case "enter", "return" -> ENTER;
				case "tab" -> TAB;
				case "modifier", "modifiers", "mods" -> MODIFIERS;
				case "navigation", "nav" -> NAVIGATION;
				case "function", "functions", "fn" -> FUNCTION;
				case "numpad", "numpads", "num" -> NUMPAD;
				case "other", "misc", "miscellaneous" -> OTHER;
				default -> null;
			};
		}

		public static KeyboardBucket fromKey(InputConstants.Key key) {
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
					GLFW.GLFW_KEY_EQUAL -> ROW1;
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
					GLFW.GLFW_KEY_BACKSLASH -> ROW2;
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
					GLFW.GLFW_KEY_APOSTROPHE -> ROW3;
				case GLFW.GLFW_KEY_Z,
					GLFW.GLFW_KEY_X,
					GLFW.GLFW_KEY_C,
					GLFW.GLFW_KEY_V,
					GLFW.GLFW_KEY_B,
					GLFW.GLFW_KEY_N,
					GLFW.GLFW_KEY_M,
					GLFW.GLFW_KEY_COMMA,
					GLFW.GLFW_KEY_PERIOD,
					GLFW.GLFW_KEY_SLASH -> ROW4;
				case GLFW.GLFW_KEY_SPACE -> SPACEBAR;
				case GLFW.GLFW_KEY_BACKSPACE -> BACKSPACE;
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> ENTER;
				case GLFW.GLFW_KEY_TAB -> TAB;
				case GLFW.GLFW_KEY_LEFT_SHIFT,
					GLFW.GLFW_KEY_RIGHT_SHIFT,
					GLFW.GLFW_KEY_LEFT_CONTROL,
					GLFW.GLFW_KEY_RIGHT_CONTROL,
					GLFW.GLFW_KEY_LEFT_ALT,
					GLFW.GLFW_KEY_RIGHT_ALT,
					GLFW.GLFW_KEY_LEFT_SUPER,
					GLFW.GLFW_KEY_RIGHT_SUPER,
					GLFW.GLFW_KEY_CAPS_LOCK -> MODIFIERS;
				case GLFW.GLFW_KEY_LEFT,
					GLFW.GLFW_KEY_RIGHT,
					GLFW.GLFW_KEY_UP,
					GLFW.GLFW_KEY_DOWN,
					GLFW.GLFW_KEY_HOME,
					GLFW.GLFW_KEY_END,
					GLFW.GLFW_KEY_PAGE_UP,
					GLFW.GLFW_KEY_PAGE_DOWN,
					GLFW.GLFW_KEY_INSERT,
					GLFW.GLFW_KEY_DELETE -> NAVIGATION;
				case GLFW.GLFW_KEY_F1,
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
					GLFW.GLFW_KEY_F25 -> FUNCTION;
				case GLFW.GLFW_KEY_KP_0,
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
					GLFW.GLFW_KEY_KP_EQUAL -> NUMPAD;
				default -> OTHER;
			};
		}
	}

	public enum MouseBucket {
		SIDE(false),
		SIDE4(true),
		SIDE5(true);

		private final boolean fallbackToSide;

		MouseBucket(boolean fallbackToSide) {
			this.fallbackToSide = fallbackToSide;
		}

		public @Nullable MouseBucket fallback() {
			return fallbackToSide ? SIDE : null;
		}

		public static @Nullable MouseBucket fromDirectoryName(String directoryName) {
			return switch (normalize(directoryName)) {
				case "side4", "button4", "mouse4", "extra4" -> SIDE4;
				case "side5", "button5", "mouse5", "extra5" -> SIDE5;
				case "side", "sides", "sidebuttons", "mousebuttons" -> SIDE;
				default -> null;
			};
		}

		public static @Nullable MouseBucket fromButton(int button) {
			return switch (button) {
				case GLFW.GLFW_MOUSE_BUTTON_4 -> SIDE4;
				case GLFW.GLFW_MOUSE_BUTTON_5 -> SIDE5;
				default -> null;
			};
		}
	}

	private static String normalize(String value) {
		StringBuilder builder = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char character = Character.toLowerCase(value.charAt(index));
			if (Character.isLetterOrDigit(character)) {
				builder.append(character);
			}
		}
		return builder.toString();
	}
}
