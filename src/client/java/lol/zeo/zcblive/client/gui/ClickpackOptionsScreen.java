package lol.zeo.zcblive.client.gui;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.DoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import lol.zeo.zcblive.ZCBLive;
import lol.zeo.zcblive.ZCBLiveClient;
import lol.zeo.zcblive.client.ZcbClientController;
import lol.zeo.zcblive.client.ZcbConfig;
import lol.zeo.zcblive.client.clickpack.ClickType;

public final class ClickpackOptionsScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("gui.zcb-live.clickpack_options.title");
	private static final Component GENERAL_HEADER = Component.translatable("gui.zcb-live.clickpack_options.general");
	private static final Component KEYBOARD_HEADER = Component.translatable("gui.zcb-live.clickpack_options.keyboard");
	private static final Component MOUSE_HEADER = Component.translatable("gui.zcb-live.clickpack_options.mouse");
	private static final Component AUDIO_HEADER = Component.translatable("gui.zcb-live.clickpack_options.audio");
	private static final Component CLICK_TYPES_HEADER = Component.translatable("gui.zcb-live.clickpack_options.click_types");
	private static final Component TIMINGS_HEADER = Component.translatable("gui.zcb-live.clickpack_options.timings");
	private static final Component VOLUME_HEADER = Component.translatable("gui.zcb-live.clickpack_options.volume_changes");
	private static final Component KEYBOARD_TAB = Component.translatable("gui.zcb-live.clickpack_options.keyboard_tab");
	private static final Component MOUSE_TAB = Component.translatable("gui.zcb-live.clickpack_options.mouse_tab");

	private final LaneTab selectedTab;

	public ClickpackOptionsScreen(Screen parent) {
		this(parent, LaneTab.KEYBOARD);
	}

	public ClickpackOptionsScreen(Screen parent, LaneTab selectedTab) {
		super(parent, Minecraft.getInstance().options, TITLE);
		this.selectedTab = selectedTab;
	}

	@Override
	protected void addTitle() {
		layout.setHeaderHeight(96);
		super.addTitle();
		addTopTabs();
	}

	@Override
	protected void addOptions() {
		ZcbClientController controller = ZCBLiveClient.controller();
		ZcbConfig config = controller.config();
		ZcbConfig.LaneSettings laneSettings = selectedLane(config);

		list.addHeader(GENERAL_HEADER);
		list.addBig(booleanOption("options.zcb-live.enabled", config.enabled, value -> controller.updateConfig(current -> current.enabled = value)));
		list.addBig(inputModeOption(controller, config.inputMode));
		list.addBig(booleanOption("options.zcb-live.play_noise", config.playNoise, value -> controller.updateConfig(current -> current.playNoise = value)));

		list.addHeader(selectedTab == LaneTab.KEYBOARD ? KEYBOARD_HEADER : MOUSE_HEADER);
		if (selectedTab == LaneTab.KEYBOARD) {
			list.addBig(keyboardGroupingOption(controller, config.keyboard.grouping));
		}
		list.addHeader(AUDIO_HEADER);
		list.addBig(intOption(
			"options.zcb-live.click_volume",
			0,
			500,
			(int) Math.round(laneSettings.clickVolume * 100.0D),
			value -> value + "%",
			value -> controller.updateConfig(current -> selectedLane(current).clickVolume = value / 100.0D)
		));
		list.addBig(booleanOption(
			"options.zcb-live.pitch.enabled",
			laneSettings.pitchEnabled,
			value -> controller.updateConfig(current -> selectedLane(current).pitchEnabled = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.pitch.from",
			0.5D,
			2.0D,
			laneSettings.pitch.from,
			this::formatDecimal,
			value -> controller.updateConfig(current -> selectedLane(current).pitch.from = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.pitch.to",
			0.5D,
			2.0D,
			laneSettings.pitch.to,
			this::formatDecimal,
			value -> controller.updateConfig(current -> selectedLane(current).pitch.to = value)
		));

		list.addHeader(CLICK_TYPES_HEADER);
		list.addBig(booleanOption(
			"options.zcb-live.click_types.ignore_hard_click",
			laneSettings.isClickTypeIgnored(ClickType.HARD_CLICK),
			value -> controller.updateConfig(current -> selectedLane(current).setClickTypeIgnored(ClickType.HARD_CLICK, value))
		));
		list.addBig(booleanOption(
			"options.zcb-live.click_types.ignore_hard_release",
			laneSettings.isClickTypeIgnored(ClickType.HARD_RELEASE),
			value -> controller.updateConfig(current -> selectedLane(current).setClickTypeIgnored(ClickType.HARD_RELEASE, value))
		));
		list.addBig(booleanOption(
			"options.zcb-live.click_types.ignore_click",
			laneSettings.isClickTypeIgnored(ClickType.CLICK),
			value -> controller.updateConfig(current -> selectedLane(current).setClickTypeIgnored(ClickType.CLICK, value))
		));
		list.addBig(booleanOption(
			"options.zcb-live.click_types.ignore_release",
			laneSettings.isClickTypeIgnored(ClickType.RELEASE),
			value -> controller.updateConfig(current -> selectedLane(current).setClickTypeIgnored(ClickType.RELEASE, value))
		));
		list.addBig(booleanOption(
			"options.zcb-live.click_types.ignore_soft_click",
			laneSettings.isClickTypeIgnored(ClickType.SOFT_CLICK),
			value -> controller.updateConfig(current -> selectedLane(current).setClickTypeIgnored(ClickType.SOFT_CLICK, value))
		));
		list.addBig(booleanOption(
			"options.zcb-live.click_types.ignore_soft_release",
			laneSettings.isClickTypeIgnored(ClickType.SOFT_RELEASE),
			value -> controller.updateConfig(current -> selectedLane(current).setClickTypeIgnored(ClickType.SOFT_RELEASE, value))
		));
		list.addBig(booleanOption(
			"options.zcb-live.click_types.ignore_micro_click",
			laneSettings.isClickTypeIgnored(ClickType.MICRO_CLICK),
			value -> controller.updateConfig(current -> selectedLane(current).setClickTypeIgnored(ClickType.MICRO_CLICK, value))
		));
		list.addBig(booleanOption(
			"options.zcb-live.click_types.ignore_micro_release",
			laneSettings.isClickTypeIgnored(ClickType.MICRO_RELEASE),
			value -> controller.updateConfig(current -> selectedLane(current).setClickTypeIgnored(ClickType.MICRO_RELEASE, value))
		));

		list.addHeader(TIMINGS_HEADER);
		list.addBig(doubleOption(
			"options.zcb-live.timing.hard",
			0.0D,
			5.0D,
			laneSettings.timings.hard,
			this::formatSeconds,
			value -> controller.updateConfig(current -> selectedLane(current).timings.hard = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.timing.regular",
			0.0D,
			5.0D,
			laneSettings.timings.regular,
			this::formatSeconds,
			value -> controller.updateConfig(current -> selectedLane(current).timings.regular = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.timing.soft",
			0.0D,
			5.0D,
			laneSettings.timings.soft,
			this::formatSeconds,
			value -> controller.updateConfig(current -> selectedLane(current).timings.soft = value)
		));

		list.addHeader(VOLUME_HEADER);
		list.addBig(booleanOption(
			"options.zcb-live.volume_changes.enabled",
			laneSettings.volumeSettings.enabled,
			value -> controller.updateConfig(current -> selectedLane(current).volumeSettings.enabled = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.volume_changes.spam_time",
			0.0D,
			5.0D,
			laneSettings.volumeSettings.spamTime,
			this::formatSeconds,
			value -> controller.updateConfig(current -> selectedLane(current).volumeSettings.spamTime = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.volume_changes.spam_offset_factor",
			0.0D,
			5.0D,
			laneSettings.volumeSettings.spamVolOffsetFactor,
			this::formatDecimal,
			value -> controller.updateConfig(current -> selectedLane(current).volumeSettings.spamVolOffsetFactor = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.volume_changes.max_spam_offset",
			0.0D,
			5.0D,
			laneSettings.volumeSettings.maxSpamVolOffset,
			this::formatDecimal,
			value -> controller.updateConfig(current -> selectedLane(current).volumeSettings.maxSpamVolOffset = value)
		));
		list.addBig(booleanOption(
			"options.zcb-live.volume_changes.change_releases",
			laneSettings.volumeSettings.changeReleasesVolume,
			value -> controller.updateConfig(current -> selectedLane(current).volumeSettings.changeReleasesVolume = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.volume_changes.global_volume",
			0.0D,
			5.0D,
			laneSettings.volumeSettings.globalVolume,
			this::formatMultiplier,
			value -> controller.updateConfig(current -> selectedLane(current).volumeSettings.globalVolume = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.volume_changes.volume_var",
			0.0D,
			1.0D,
			laneSettings.volumeSettings.volumeVar,
			this::formatDecimal,
			value -> controller.updateConfig(current -> selectedLane(current).volumeSettings.volumeVar = value)
		));
	}

	@Override
	protected void addFooter() {
		int buttonWidth = Math.max(100, Math.min(200, (width - 32) / 2));
		LinearLayout footer = LinearLayout.horizontal().spacing(8);
		footer.addChild(Button.builder(resetButtonText(), ignored -> resetDefaults()).width(buttonWidth).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, ignored -> onClose()).width(buttonWidth).build());
		layout.addToFooter(footer);
	}

	private void addTopTabs() {
		int tabWidth = Math.max(120, Math.min(180, (width - 56) / 2));
		int tabGap = 8;
		LinearLayout tabRow = LinearLayout.horizontal().spacing(tabGap);
		Button keyboardTab = tabRow.addChild(Button.builder(KEYBOARD_TAB, ignored -> openTab(LaneTab.KEYBOARD)).width(tabWidth).build());
		Button mouseTab = tabRow.addChild(Button.builder(MOUSE_TAB, ignored -> openTab(LaneTab.MOUSE)).width(tabWidth).build());
		layout.addToHeader(tabRow, settings -> settings.alignHorizontallyCenter().paddingTop(48));
		keyboardTab.active = selectedTab != LaneTab.KEYBOARD;
		mouseTab.active = selectedTab != LaneTab.MOUSE;
	}

	private void openTab(LaneTab tab) {
		if (minecraft != null && tab != selectedTab) {
			minecraft.setScreen(new ClickpackOptionsScreen(lastScreen, tab));
		}
	}

	private OptionInstance<Boolean> booleanOption(String key, boolean currentValue, IOBooleanAction action) {
		return OptionInstance.createBoolean(key, currentValue, value -> persist(() -> action.accept(value)));
	}

	private OptionInstance<Integer> intOption(
		String key,
		int min,
		int max,
		int currentValue,
		DoubleFunction<String> formatter,
		IOIntAction action
	) {
		return new OptionInstance<>(
			key,
			OptionInstance.noTooltip(),
			(caption, value) -> Options.genericValueLabel(caption, Component.literal(formatter.apply(value.doubleValue()))),
			new OptionInstance.IntRange(min, max),
			currentValue,
			value -> persist(() -> action.accept(value))
		);
	}

	private OptionInstance<Double> doubleOption(
		String key,
		double min,
		double max,
		double currentValue,
		DoubleFunction<String> formatter,
		IODoubleAction action
	) {
		return new OptionInstance<>(
			key,
			OptionInstance.noTooltip(),
			(caption, value) -> Options.genericValueLabel(caption, Component.literal(formatter.apply(value))),
			OptionInstance.UnitDouble.INSTANCE.xmap(value -> Mth.lerp(value, min, max), value -> Mth.inverseLerp(value, min, max)),
			currentValue,
			value -> persist(() -> action.accept(value))
		);
	}

	private OptionInstance<ZcbConfig.InputMode> inputModeOption(ZcbClientController controller, ZcbConfig.InputMode currentValue) {
		return new OptionInstance<>(
			"options.zcb-live.input_mode",
			OptionInstance.noTooltip(),
			(caption, value) -> Component.literal(value.label()),
			new OptionInstance.Enum<>(Arrays.asList(ZcbConfig.InputMode.values()), ZcbConfig.InputMode.CODEC),
			currentValue,
			value -> persist(() -> controller.updateConfig(current -> current.inputMode = value))
		);
	}

	private OptionInstance<ZcbConfig.KeyboardGrouping> keyboardGroupingOption(ZcbClientController controller, ZcbConfig.KeyboardGrouping currentValue) {
		return new OptionInstance<>(
			"options.zcb-live.keyboard_grouping",
			OptionInstance.noTooltip(),
			(caption, value) -> Component.literal(value.label()),
			new OptionInstance.Enum<>(Arrays.asList(ZcbConfig.KeyboardGrouping.values()), ZcbConfig.KeyboardGrouping.CODEC),
			currentValue,
			value -> persist(() -> controller.updateConfig(current -> current.keyboard.grouping = value))
		);
	}

	private void persist(IOAction action) {
		try {
			action.run();
		} catch (IOException exception) {
			ZCBLive.LOGGER.warn("Failed to save clickpack options", exception);
		}
	}

	private void resetDefaults() {
		try {
			ZCBLiveClient.controller().resetLaneSettingsToDefaults(selectedTab == LaneTab.KEYBOARD);
			if (minecraft != null) {
				minecraft.setScreen(new ClickpackOptionsScreen(lastScreen, selectedTab));
			}
		} catch (IOException exception) {
			ZCBLive.LOGGER.warn("Failed to reset clickpack options", exception);
		}
	}

	private Component resetButtonText() {
		if (selectedTab == LaneTab.KEYBOARD) {
			return Component.translatable("gui.zcb-live.clickpack_options.reset_keyboard");
		}
		return Component.translatable("gui.zcb-live.clickpack_options.reset_mouse");
	}

	private ZcbConfig.LaneSettings selectedLane(ZcbConfig config) {
		return selectedTab == LaneTab.KEYBOARD ? config.keyboard.settings : config.mouse;
	}

	private String formatSeconds(double value) {
		return String.format(Locale.ROOT, "%.3fs", value);
	}

	private String formatDecimal(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private String formatMultiplier(double value) {
		return String.format(Locale.ROOT, "%.2fx", value);
	}

	private interface IOAction {
		void run() throws IOException;
	}

	private interface IOBooleanAction {
		void accept(boolean value) throws IOException;
	}

	private interface IOIntAction {
		void accept(int value) throws IOException;
	}

	private interface IODoubleAction {
		void accept(double value) throws IOException;
	}

	public enum LaneTab {
		KEYBOARD,
		MOUSE
	}
}
