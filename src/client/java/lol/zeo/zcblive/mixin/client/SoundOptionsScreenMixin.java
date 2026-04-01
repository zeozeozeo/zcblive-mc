package lol.zeo.zcblive.mixin.client;

import java.io.IOException;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import lol.zeo.zcblive.ZCBLive;
import lol.zeo.zcblive.ZCBLiveClient;

@Mixin(net.minecraft.client.gui.screens.options.SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin extends OptionsSubScreen {
	private static final Component CLICK_VOLUME_TOOLTIP = Component.translatable("options.zcb-live.click_volume.tooltip");

	protected SoundOptionsScreenMixin(Screen lastScreen, Options options, Component title) {
		super(lastScreen, options, title);
	}

	@Inject(method = "addOptions", at = @At("TAIL"))
	private void zcblive$addClickVolumeOption(CallbackInfo ci) {
		this.list.addBig(new OptionInstance<>(
			"options.zcb-live.click_volume",
			OptionInstance.cachedConstantTooltip(CLICK_VOLUME_TOOLTIP),
			(caption, value) -> Options.genericValueLabel(caption, Component.literal(value + "%")),
			new OptionInstance.IntRange(0, 500),
			currentClickVolumePercent(),
			value -> {
				try {
					ZCBLiveClient.controller().setClickVolume(value / 100.0D);
				} catch (IOException exception) {
					ZCBLive.LOGGER.warn("Failed to save click volume", exception);
				}
			}
		));
	}

	private static int currentClickVolumePercent() {
		return (int) Math.round(ZCBLiveClient.controller().clickVolume() * 100.0D);
	}
}
