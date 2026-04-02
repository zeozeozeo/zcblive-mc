package lol.zeo.zcblive.client.gui;

import java.io.IOException;
import java.util.Locale;
import lol.zeo.zcblive.ZCBLiveMod;
import lol.zeo.zcblive.client.ZcbClientController;
import lol.zeo.zcblive.client.ZcbConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;

public final class ClickpackOptionsScreen extends GuiScreen {
    private static final int BUTTON_DONE = 1;
    private static final int BUTTON_RESET = 2;
    private static final int BUTTON_ENABLED = 10;
    private static final int BUTTON_INPUT_MODE = 11;
    private static final int BUTTON_PLAY_NOISE = 12;
    private static final int BUTTON_HARD_CLICKS = 13;
    private static final int BUTTON_VOLUME_ENABLED = 14;
    private static final int BUTTON_CHANGE_RELEASES = 15;

    private static final int SLIDER_CLICK_VOLUME = 20;
    private static final int SLIDER_TIMING_HARD = 21;
    private static final int SLIDER_TIMING_REGULAR = 22;
    private static final int SLIDER_TIMING_SOFT = 23;
    private static final int SLIDER_SPAM_TIME = 24;
    private static final int SLIDER_SPAM_OFFSET_FACTOR = 25;
    private static final int SLIDER_MAX_SPAM_OFFSET = 26;
    private static final int SLIDER_GLOBAL_VOLUME = 27;
    private static final int SLIDER_VOLUME_VAR = 28;

    private final GuiScreen parent;
    private final ZcbClientController controller;

    private GuiButton enabledButton;
    private GuiButton inputModeButton;
    private GuiButton playNoiseButton;
    private GuiButton hardClicksButton;
    private GuiButton volumeEnabledButton;
    private GuiButton changeReleasesButton;

    private ValueSliderButton clickVolumeSlider;
    private ValueSliderButton timingHardSlider;
    private ValueSliderButton timingRegularSlider;
    private ValueSliderButton timingSoftSlider;
    private ValueSliderButton spamTimeSlider;
    private ValueSliderButton spamOffsetFactorSlider;
    private ValueSliderButton maxSpamOffsetSlider;
    private ValueSliderButton globalVolumeSlider;
    private ValueSliderButton volumeVarSlider;

    private int sectionTextX;
    private int generalHeaderY;
    private int clickTypesHeaderY;
    private int timingsHeaderY;
    private int volumeHeaderY;

    public ClickpackOptionsScreen(GuiScreen parent, ZcbClientController controller) {
        this.parent = parent;
        this.controller = controller;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int fullWidth = Math.min(310, Math.max(220, this.width - 24));
        int leftX = (this.width - fullWidth) / 2;
        int columnGap = 8;
        int columnWidth = (fullWidth - columnGap) / 2;
        int rightX = leftX + columnWidth + columnGap;

        this.sectionTextX = leftX;
        int y = 32;

        this.generalHeaderY = y;
        y += 14;
        this.enabledButton = addButtonCompat(new GuiButton(BUTTON_ENABLED, leftX, y, columnWidth, 20, ""));
        this.inputModeButton = addButtonCompat(new GuiButton(BUTTON_INPUT_MODE, rightX, y, columnWidth, 20, ""));
        y += 22;
        this.clickVolumeSlider = addButtonCompat(new ValueSliderButton(
            SLIDER_CLICK_VOLUME,
            leftX,
            y,
            fullWidth,
            20,
            tr("options.zcb-live.click_volume"),
            0.0D,
            5.0D,
            1.0D,
            value -> String.format(Locale.ROOT, "%d%%", Integer.valueOf((int) Math.round(value * 100.0D))),
            value -> controller.updateConfig(config -> config.clickVolume = value)
        ));
        y += 22;
        this.playNoiseButton = addButtonCompat(new GuiButton(BUTTON_PLAY_NOISE, leftX, y, columnWidth, 20, ""));
        y += 24;

        this.clickTypesHeaderY = y;
        y += 14;
        this.hardClicksButton = addButtonCompat(new GuiButton(BUTTON_HARD_CLICKS, leftX, y, columnWidth, 20, ""));
        y += 24;

        this.timingsHeaderY = y;
        y += 14;
        this.timingHardSlider = addButtonCompat(new ValueSliderButton(
            SLIDER_TIMING_HARD,
            leftX,
            y,
            columnWidth,
            20,
            tr("options.zcb-live.timing.hard"),
            0.0D,
            5.0D,
            2.0D,
            value -> String.format(Locale.ROOT, "%.3f s", Double.valueOf(value)),
            value -> controller.updateConfig(config -> config.timings.hard = value)
        ));
        this.timingRegularSlider = addButtonCompat(new ValueSliderButton(
            SLIDER_TIMING_REGULAR,
            rightX,
            y,
            columnWidth,
            20,
            tr("options.zcb-live.timing.regular"),
            0.0D,
            5.0D,
            0.15D,
            value -> String.format(Locale.ROOT, "%.3f s", Double.valueOf(value)),
            value -> controller.updateConfig(config -> config.timings.regular = value)
        ));
        y += 22;
        this.timingSoftSlider = addButtonCompat(new ValueSliderButton(
            SLIDER_TIMING_SOFT,
            leftX,
            y,
            columnWidth,
            20,
            tr("options.zcb-live.timing.soft"),
            0.0D,
            5.0D,
            0.025D,
            value -> String.format(Locale.ROOT, "%.3f s", Double.valueOf(value)),
            value -> controller.updateConfig(config -> config.timings.soft = value)
        ));
        y += 24;

        this.volumeHeaderY = y;
        y += 14;
        this.volumeEnabledButton = addButtonCompat(new GuiButton(BUTTON_VOLUME_ENABLED, leftX, y, columnWidth, 20, ""));
        this.changeReleasesButton = addButtonCompat(new GuiButton(BUTTON_CHANGE_RELEASES, rightX, y, columnWidth, 20, ""));
        y += 22;
        this.spamTimeSlider = addButtonCompat(new ValueSliderButton(
            SLIDER_SPAM_TIME,
            leftX,
            y,
            columnWidth,
            20,
            tr("options.zcb-live.volume_changes.spam_time"),
            0.0D,
            5.0D,
            0.3D,
            value -> String.format(Locale.ROOT, "%.3f s", Double.valueOf(value)),
            value -> controller.updateConfig(config -> config.volumeSettings.spamTime = value)
        ));
        this.spamOffsetFactorSlider = addButtonCompat(new ValueSliderButton(
            SLIDER_SPAM_OFFSET_FACTOR,
            rightX,
            y,
            columnWidth,
            20,
            tr("options.zcb-live.volume_changes.spam_offset_factor"),
            0.0D,
            5.0D,
            1.3D,
            value -> String.format(Locale.ROOT, "%.3f", Double.valueOf(value)),
            value -> controller.updateConfig(config -> config.volumeSettings.spamVolOffsetFactor = value)
        ));
        y += 22;
        this.maxSpamOffsetSlider = addButtonCompat(new ValueSliderButton(
            SLIDER_MAX_SPAM_OFFSET,
            leftX,
            y,
            columnWidth,
            20,
            tr("options.zcb-live.volume_changes.max_spam_offset"),
            0.0D,
            5.0D,
            0.6D,
            value -> String.format(Locale.ROOT, "%.3f", Double.valueOf(value)),
            value -> controller.updateConfig(config -> config.volumeSettings.maxSpamVolOffset = value)
        ));
        this.globalVolumeSlider = addButtonCompat(new ValueSliderButton(
            SLIDER_GLOBAL_VOLUME,
            rightX,
            y,
            columnWidth,
            20,
            tr("options.zcb-live.volume_changes.global_volume"),
            0.0D,
            5.0D,
            1.0D,
            value -> String.format(Locale.ROOT, "x%.2f", Double.valueOf(value)),
            value -> controller.updateConfig(config -> config.volumeSettings.globalVolume = value)
        ));
        y += 22;
        this.volumeVarSlider = addButtonCompat(new ValueSliderButton(
            SLIDER_VOLUME_VAR,
            leftX,
            y,
            columnWidth,
            20,
            tr("options.zcb-live.volume_changes.volume_var"),
            0.0D,
            1.0D,
            0.2D,
            value -> String.format(Locale.ROOT, "%.3f", Double.valueOf(value)),
            value -> controller.updateConfig(config -> config.volumeSettings.volumeVar = value)
        ));

        int footerButtonWidth = Math.max(100, Math.min(200, (this.width - 32) / 2));
        int footerY = this.height - 28;
        int resetX = this.width / 2 - footerButtonWidth - 4;
        int doneX = this.width / 2 + 4;
        addButtonCompat(new GuiButton(BUTTON_RESET, resetX, footerY, footerButtonWidth, 20, tr("gui.zcb-live.clickpack_options.reset")));
        addButtonCompat(new GuiButton(BUTTON_DONE, doneX, footerY, footerButtonWidth, 20, "Done"));

        refreshFromConfig();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }

        switch (button.id) {
            case BUTTON_DONE:
                this.mc.displayGuiScreen(parent);
                return;
            case BUTTON_RESET:
                persist(controller::resetConfigToDefaults);
                this.mc.displayGuiScreen(new ClickpackOptionsScreen(parent, controller));
                return;
            case BUTTON_ENABLED:
                persist(() -> controller.updateConfig(config -> config.enabled = !config.enabled));
                break;
            case BUTTON_INPUT_MODE:
                persist(() -> controller.updateConfig(config -> config.inputMode = config.inputMode.next()));
                break;
            case BUTTON_PLAY_NOISE:
                persist(() -> controller.updateConfig(config -> config.playNoise = !config.playNoise));
                break;
            case BUTTON_HARD_CLICKS:
                persist(() -> controller.updateConfig(config -> config.hardClicksEnabled = !hardClicksEnabled(config)));
                break;
            case BUTTON_VOLUME_ENABLED:
                persist(() -> controller.updateConfig(config -> config.volumeSettings.enabled = !config.volumeSettings.enabled));
                break;
            case BUTTON_CHANGE_RELEASES:
                persist(() -> controller.updateConfig(config -> config.volumeSettings.changeReleasesVolume = !config.volumeSettings.changeReleasesVolume));
                break;
            default:
                return;
        }

        refreshFromConfig();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(this.fontRendererObj, tr("gui.zcb-live.clickpack_options.title"), this.width / 2, 10, 0xFFFFFFFF);
        drawString(this.fontRendererObj, tr("gui.zcb-live.clickpack_options.general"), sectionTextX, generalHeaderY, 0xFFD8D8D8);
        drawString(this.fontRendererObj, tr("gui.zcb-live.clickpack_options.click_types"), sectionTextX, clickTypesHeaderY, 0xFFD8D8D8);
        drawString(this.fontRendererObj, tr("gui.zcb-live.clickpack_options.timings"), sectionTextX, timingsHeaderY, 0xFFD8D8D8);
        drawString(this.fontRendererObj, tr("gui.zcb-live.clickpack_options.volume_changes"), sectionTextX, volumeHeaderY, 0xFFD8D8D8);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void refreshFromConfig() {
        ZcbConfig config = controller.config();
        if (enabledButton != null) {
            enabledButton.displayString = tr("options.zcb-live.enabled") + ": " + onOff(config.enabled);
        }
        if (inputModeButton != null) {
            inputModeButton.displayString = tr("options.zcb-live.input_mode") + ": " + config.inputMode.label();
        }
        if (playNoiseButton != null) {
            playNoiseButton.displayString = tr("options.zcb-live.play_noise") + ": " + onOff(config.playNoise);
        }
        if (hardClicksButton != null) {
            hardClicksButton.displayString = tr("options.zcb-live.hard_clicks") + ": " + onOff(hardClicksEnabled(config));
        }
        if (volumeEnabledButton != null) {
            volumeEnabledButton.displayString = tr("options.zcb-live.volume_changes.enabled") + ": " + onOff(config.volumeSettings.enabled);
        }
        if (changeReleasesButton != null) {
            changeReleasesButton.displayString = tr("options.zcb-live.volume_changes.change_releases") + ": " + onOff(config.volumeSettings.changeReleasesVolume);
        }
        if (clickVolumeSlider != null) {
            clickVolumeSlider.setValue(config.clickVolume);
        }
        if (timingHardSlider != null) {
            timingHardSlider.setValue(config.timings.hard);
        }
        if (timingRegularSlider != null) {
            timingRegularSlider.setValue(config.timings.regular);
        }
        if (timingSoftSlider != null) {
            timingSoftSlider.setValue(config.timings.soft);
        }
        if (spamTimeSlider != null) {
            spamTimeSlider.setValue(config.volumeSettings.spamTime);
        }
        if (spamOffsetFactorSlider != null) {
            spamOffsetFactorSlider.setValue(config.volumeSettings.spamVolOffsetFactor);
        }
        if (maxSpamOffsetSlider != null) {
            maxSpamOffsetSlider.setValue(config.volumeSettings.maxSpamVolOffset);
        }
        if (globalVolumeSlider != null) {
            globalVolumeSlider.setValue(config.volumeSettings.globalVolume);
        }
        if (volumeVarSlider != null) {
            volumeVarSlider.setValue(config.volumeSettings.volumeVar);
        }
    }

    private void persist(IOAction action) {
        try {
            action.run();
        } catch (IOException exception) {
            ZCBLiveMod.LOGGER.warn("Failed to save clickpack options", exception);
        }
    }

    private static boolean hardClicksEnabled(ZcbConfig config) {
        return config.hardClicksEnabled == null || config.hardClicksEnabled.booleanValue();
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private static String tr(String key) {
        return I18n.format(key);
    }

    private <T extends GuiButton> T addButtonCompat(T button) {
        this.buttonList.add(button);
        return button;
    }

    @FunctionalInterface
    private interface IOAction {
        void run() throws IOException;
    }

    @FunctionalInterface
    private interface ValueFormatter {
        String format(double value);
    }

    @FunctionalInterface
    private interface ValueChangeHandler {
        void apply(double value) throws IOException;
    }

    private static final class ValueSliderButton extends GuiButton {
        private final String label;
        private final double min;
        private final double max;
        private final ValueFormatter formatter;
        private final ValueChangeHandler onChange;

        private boolean dragging;
        private float sliderPosition;
        private double value;

        private ValueSliderButton(
            int id,
            int x,
            int y,
            int width,
            int height,
            String label,
            double min,
            double max,
            double currentValue,
            ValueFormatter formatter,
            ValueChangeHandler onChange
        ) {
            super(id, x, y, width, height, "");
            this.label = label;
            this.min = min;
            this.max = max;
            this.formatter = formatter;
            this.onChange = onChange;
            setValue(currentValue);
        }

        @Override
        public int getHoverState(boolean mouseOver) {
            return 0;
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                updateFromMouse(mouseX);
                this.dragging = true;
                return true;
            }
            return false;
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (this.visible && this.dragging) {
                updateFromMouse(mouseX);
            }
            if (this.visible) {
                int knobX = this.xPosition + (int) (this.sliderPosition * (this.width - 8));
                this.drawTexturedModalRect(knobX, this.yPosition, 0, 66, 4, 20);
                this.drawTexturedModalRect(knobX + 4, this.yPosition, 196, 66, 4, 20);
            }
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            this.dragging = false;
        }

        private void updateFromMouse(int mouseX) {
            float normalized = (mouseX - (this.xPosition + 4)) / (float) (this.width - 8);
            this.sliderPosition = MathHelper.clamp_float(normalized, 0.0F, 1.0F);
            this.value = this.min + (this.max - this.min) * this.sliderPosition;
            updateDisplayString();
            try {
                this.onChange.apply(this.value);
            } catch (IOException exception) {
                ZCBLiveMod.LOGGER.warn("Failed to save slider option {}", this.label, exception);
            }
        }

        private void updateDisplayString() {
            this.displayString = this.label + ": " + this.formatter.format(this.value);
        }

        private void setValue(double value) {
            this.value = MathHelper.clamp_double(value, this.min, this.max);
            if (this.max <= this.min) {
                this.sliderPosition = 0.0F;
            } else {
                this.sliderPosition = (float) ((this.value - this.min) / (this.max - this.min));
            }
            updateDisplayString();
        }
    }
}
