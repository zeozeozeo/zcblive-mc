package lol.zeo.zcblive.mixin.client;

import lol.zeo.zcblive.ZCBLiveClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("TAIL"))
	private void zcblive$onButton(long handle, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
		if (action != 0 && action != 1) {
			return;
		}
		ZCBLiveClient.controller().handleMouseEvent(buttonInfo.button(), action == 1);
	}
}
