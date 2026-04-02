package lol.zeo.zcblive.client.clickpack;

import java.nio.file.Path;

public final class LoadedClickpack implements AutoCloseable {
    private final String name;
    private final Path root;
    private final PlayerClicks keyboardClicks;
    private final PlayerClicks mouseClicks;
    private final ClickSample noise;

    public LoadedClickpack(
        String name,
        Path root,
        PlayerClicks keyboardClicks,
        PlayerClicks mouseClicks,
        ClickSample noise
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

    public ClickSample randomKeyboardSample(ClickType type) {
        return randomKeyboardSample(type, true);
    }

    public ClickSample randomKeyboardSample(ClickType type, boolean allowHardClicks) {
        return keyboardClicks == null ? null : keyboardClicks.randomSample(type, allowHardClicks);
    }

    public ClickSample randomMouseSample(ClickType type) {
        return randomMouseSample(type, true);
    }

    public ClickSample randomMouseSample(ClickType type, boolean allowHardClicks) {
        return mouseClicks == null ? null : mouseClicks.randomSample(type, allowHardClicks);
    }

    public ClickSample noiseSample() {
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
