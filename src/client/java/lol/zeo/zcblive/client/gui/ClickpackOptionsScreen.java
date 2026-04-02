package lol.zeo.zcblive.client.gui;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.DoubleFunction;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
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

public final class ClickpackOptionsScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("gui.zcb-live.clickpack_options.title");
	private static final Component GENERAL_HEADER = Component.translatable("gui.zcb-live.clickpack_options.general");
	private static final Component CLICK_TYPES_HEADER = Component.translatable("gui.zcb-live.clickpack_options.click_types");
	private static final Component TIMINGS_HEADER = Component.translatable("gui.zcb-live.clickpack_options.timings");
	private static final Component VOLUME_HEADER = Component.translatable("gui.zcb-live.clickpack_options.volume_changes");

	public ClickpackOptionsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, TITLE);
	}

	@Override
	protected void addOptions() {
		ZcbClientController controller = ZCBLiveClient.controller();
		ZcbConfig config = controller.config();

		list.addHeader(GENERAL_HEADER);
		list.addBig(booleanOption("options.zcb-live.enabled", config.enabled, value -> controller.updateConfig(current -> current.enabled = value)));
		list.addBig(inputModeOption(controller, config.inputMode));
		list.addBig(intOption(
			"options.zcb-live.click_volume",
			0,
			500,
			(int) Math.round(config.clickVolume * 100.0D),
			value -> value + "%",
			value -> controller.updateConfig(current -> current.clickVolume = value / 100.0D)
		));
		list.addBig(booleanOption("options.zcb-live.play_noise", config.playNoise, value -> controller.updateConfig(current -> current.playNoise = value)));

		list.addHeader(CLICK_TYPES_HEADER);
		list.addBig(booleanOption(
			"options.zcb-live.hard_clicks",
			hardClicksEnabled(config),
			value -> controller.updateConfig(current -> current.hardClicksEnabled = value)
		));

		list.addHeader(TIMINGS_HEADER);
		list.addBig(doubleOption(
			"options.zcb-live.timing.hard",
			0.0D,
			5.0D,
			config.timings.hard,
			this::formatSeconds,
			value -> controller.updateConfig(current -> current.timings.hard = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.timing.regular",
			0.0D,
			5.0D,
			config.timings.regular,
			this::formatSeconds,
			value -> controller.updateConfig(current -> current.timings.regular = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.timing.soft",
			0.0D,
			5.0D,
			config.timings.soft,
			this::formatSeconds,
			value -> controller.updateConfig(current -> current.timings.soft = value)
		));

		list.addHeader(VOLUME_HEADER);
		list.addBig(booleanOption(
			"options.zcb-live.volume_changes.enabled",
			config.volumeSettings.enabled,
			value -> controller.updateConfig(current -> current.volumeSettings.enabled = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.volume_changes.spam_time",
			0.0D,
			5.0D,
			config.volumeSettings.spamTime,
			this::formatSeconds,
			value -> controller.updateConfig(current -> current.volumeSettings.spamTime = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.volume_changes.spam_offset_factor",
			0.0D,
			5.0D,
			config.volumeSettings.spamVolOffsetFactor,
			this::formatDecimal,
			value -> controller.updateConfig(current -> current.volumeSettings.spamVolOffsetFactor = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.volume_changes.max_spam_offset",
			0.0D,
			5.0D,
			config.volumeSettings.maxSpamVolOffset,
			this::formatDecimal,
			value -> controller.updateConfig(current -> current.volumeSettings.maxSpamVolOffset = value)
		));
		list.addBig(booleanOption(
			"options.zcb-live.volume_changes.change_releases",
			config.volumeSettings.changeReleasesVolume,
			value -> controller.updateConfig(current -> current.volumeSettings.changeReleasesVolume = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.volume_changes.global_volume",
			0.0D,
			5.0D,
			config.volumeSettings.globalVolume,
			this::formatMultiplier,
			value -> controller.updateConfig(current -> current.volumeSettings.globalVolume = value)
		));
		list.addBig(doubleOption(
			"options.zcb-live.volume_changes.volume_var",
			0.0D,
			1.0D,
			config.volumeSettings.volumeVar,
			this::formatDecimal,
			value -> controller.updateConfig(current -> current.volumeSettings.volumeVar = value)
		));
	}

	@Override
	protected void addFooter() {
		int buttonWidth = Math.max(100, Math.min(200, (width - 32) / 2));
		LinearLayout footer = LinearLayout.horizontal().spacing(8);
		footer.addChild(Button.builder(Component.translatable("gui.zcb-live.clickpack_options.reset"), ignored -> resetDefaults()).width(buttonWidth).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, ignored -> onClose()).width(buttonWidth).build());
		layout.addToFooter(footer);
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

	private void persist(IOAction action) {
		try {
			action.run();
		} catch (IOException exception) {
			ZCBLive.LOGGER.warn("Failed to save clickpack options", exception);
		}
	}

	private void resetDefaults() {
		try {
			ZCBLiveClient.controller().resetConfigToDefaults();
			if (minecraft != null) {
				minecraft.setScreen(new ClickpackOptionsScreen(lastScreen));
			}
		} catch (IOException exception) {
			ZCBLive.LOGGER.warn("Failed to reset clickpack options", exception);
		}
	}

	private static boolean hardClicksEnabled(ZcbConfig config) {
		return config.hardClicksEnabled == null || config.hardClicksEnabled;
	}

	private String formatSeconds(double value) {
		return String.format(Locale.ROOT, "%.3f s", value);
	}

	private String formatDecimal(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private String formatMultiplier(double value) {
		return String.format(Locale.ROOT, "x%.2f", value);
	}

	@FunctionalInterface
	private interface IOAction {
		void run() throws IOException;
	}

	@FunctionalInterface
	private interface IOBooleanAction {
		void accept(boolean value) throws IOException;
	}

	@FunctionalInterface
	private interface IOIntAction {
		void accept(int value) throws IOException;
	}

	@FunctionalInterface
	private interface IODoubleAction {
		void accept(double value) throws IOException;
	}
}
