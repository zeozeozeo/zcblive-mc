package lol.zeo.zcblive.client.clickpack;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

public final class LoadedClickpack implements AutoCloseable {
	private final String name;
	private final Path root;
	private final @Nullable PlayerClicks keyboardClicks;
	private final @Nullable PlayerClicks mouseClicks;
	private final @Nullable ClickSample noise;

	public LoadedClickpack(
		String name,
		Path root,
		@Nullable PlayerClicks keyboardClicks,
		@Nullable PlayerClicks mouseClicks,
		@Nullable ClickSample noise
	) {
		this.name = name;
		this.root = root;
		this.keyboardClicks = keyboardClicks;
		this.mouseClicks = mouseClicks;
		this.noise = noise;
	}

	public String name() {
		return name;
	}

	public Path root() {
		return root;
	}

	public boolean hasKeyboardClicks() {
		return keyboardClicks != null && !keyboardClicks.isEmpty();
	}

	public boolean hasMouseClicks() {
		return mouseClicks != null && !mouseClicks.isEmpty();
	}

	public boolean hasNoise() {
		return noise != null;
	}

	public @Nullable ClickSample randomKeyboardSample(ClickType type) {
		return keyboardClicks == null ? null : keyboardClicks.randomSample(type);
	}

	public @Nullable ClickSample randomMouseSample(ClickType type) {
		return mouseClicks == null ? null : mouseClicks.randomSample(type);
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
		if (noise != null) {
			noise.close();
		}
	}
}
