package lol.zeo.zcblive.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import lol.zeo.zcblive.ZCBLiveClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void zcblive$onKeyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
		if (action != 0 && action != 1) {
			return;
		}
		ZCBLiveClient.controller().handleKeyboardEvent(InputConstants.getKey(event), action == 1);
	}
}
