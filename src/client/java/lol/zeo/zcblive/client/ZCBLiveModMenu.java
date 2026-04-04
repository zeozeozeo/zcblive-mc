package lol.zeo.zcblive.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

import lol.zeo.zcblive.client.gui.ClickpackOptionsScreen;

public final class ZCBLiveModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<Screen> getModConfigScreenFactory() {
		return parent -> new ClickpackOptionsScreen(parent);
	}
}
