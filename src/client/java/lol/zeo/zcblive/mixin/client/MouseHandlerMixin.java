package lol.zeo.zcblive.mixin.client;

import lol.zeo.zcblive.ZCBLiveClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onPress", at = @At("TAIL"))
	private void zcblive$onButton(long handle, int button, int action, int modifiers, CallbackInfo ci) {
		if (action != 0 && action != 1) {
			return;
		}
		ZCBLiveClient.controller().handleMouseEvent(button, action == 1);
	}
}
