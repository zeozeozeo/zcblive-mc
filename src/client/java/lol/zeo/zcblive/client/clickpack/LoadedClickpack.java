package lol.zeo.zcblive.client.clickpack;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class LoadedClickpack implements AutoCloseable {
	private final String name;
	private final Path root;
	private final @Nullable PlayerClicks keyboardClicks;
	private final @Nullable PlayerClicks mouseClicks;
	private final @Nullable ClickpackOverlay overlay;
	private final @Nullable ClickSample noise;

	public LoadedClickpack(
		String name,
		Path root,
		@Nullable PlayerClicks keyboardClicks,
		@Nullable PlayerClicks mouseClicks,
		@Nullable ClickpackOverlay overlay,
		@Nullable ClickSample noise
	) {
		this.name = name;
		this.root = root;
		this.keyboardClicks = keyboardClicks;
		this.mouseClicks = mouseClicks;
		this.overlay = overlay;
		this.noise = noise;
	}

	public String name() {
		return name;
	}

	public Path root() {
		return root;
	}

	public boolean hasKeyboardClicks() {
		return (keyboardClicks != null && !keyboardClicks.isEmpty()) || (overlay != null && overlay.hasKeyboardSamples());
	}

	public boolean hasMouseClicks() {
		return (mouseClicks != null && !mouseClicks.isEmpty()) || (overlay != null && overlay.hasMouseSamples());
	}

	public boolean hasNoise() {
		return noise != null;
	}

	public @Nullable ClickSample randomKeyboardOverlaySample(InputConstants.Key key, ClickType type, Predicate<ClickType> allowedType) {
		return overlay == null ? null : overlay.randomKeyboardSample(key, type, allowedType);
	}

	public @Nullable ClickSample randomMouseOverlaySample(int button, ClickType type, Predicate<ClickType> allowedType) {
		return overlay == null ? null : overlay.randomMouseSample(button, type, allowedType);
	}

	public @Nullable ClickSample randomKeyboardSample(ClickType type) {
		return randomKeyboardSample(type, clickType -> true);
	}

	public @Nullable ClickSample randomKeyboardSample(ClickType type, boolean allowHardClicks) {
		return randomKeyboardSample(type, clickType -> allowHardClicks || (clickType != ClickType.HARD_CLICK && clickType != ClickType.HARD_RELEASE));
	}

	public @Nullable ClickSample randomKeyboardSample(ClickType type, Predicate<ClickType> allowedType) {
		return keyboardClicks == null ? null : keyboardClicks.randomSample(type, allowedType);
	}

	public @Nullable ClickSample randomMouseSample(ClickType type) {
		return randomMouseSample(type, clickType -> true);
	}

	public @Nullable ClickSample randomMouseSample(ClickType type, boolean allowHardClicks) {
		return randomMouseSample(type, clickType -> allowHardClicks || (clickType != ClickType.HARD_CLICK && clickType != ClickType.HARD_RELEASE));
	}

	public @Nullable ClickSample randomMouseSample(ClickType type, Predicate<ClickType> allowedType) {
		return mouseClicks == null ? null : mouseClicks.randomSample(type, allowedType);
	}

	public @Nullable ClickSample noiseSample() {
		return noise;
	}

	@Override
	public void close() {
		if (keyboardClicks != null) {
			keyboardClicks.close();
		}
		if (mouseClicks != null && mouseClicks != keyboardClicks) {
			mouseClicks.close();
		}
		if (overlay != null) {
			overlay.close();
		}
		if (noise != null) {
			noise.close();
		}
	}
}
