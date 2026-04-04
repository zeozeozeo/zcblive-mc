package lol.zeo.zcblive;

import java.util.Comparator;
import java.util.List;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import lol.zeo.zcblive.client.ZcbClientController;
import lol.zeo.zcblive.client.gui.ClickpackBrowserScreen;

public class ZCBLiveClient implements ClientModInitializer {
	private static final int MENU_BUTTON_WIDTH = 200;
	private static final int MENU_BUTTON_HEIGHT = 20;
	private static final int CORNER_BUTTON_WIDTH = 120;
	private static final int SCREEN_MARGIN = 12;
	private static final Component CLICKPACKS_TEXT = Component.literal("Clickpacks");
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ZCBLive.MOD_ID, "controls"));
	private static final KeyMapping TOGGLE_CLICKBOT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		"key.zcb-live.toggle_clickbot",
		InputConstants.UNKNOWN.getType(),
		InputConstants.UNKNOWN.getValue(),
		CATEGORY
	));
	private static final KeyMapping CHANGE_INPUT_MODE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		"key.zcb-live.change_input_mode",
		InputConstants.UNKNOWN.getType(),
		InputConstants.UNKNOWN.getValue(),
		CATEGORY
	));
	private static final ZcbClientController CONTROLLER = new ZcbClientController();

	public static ZcbClientController controller() {
		return CONTROLLER;
	}

	@Override
	public void onInitializeClient() {
		CONTROLLER.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			CONTROLLER.tick();
			while (TOGGLE_CLICKBOT.consumeClick()) {
				toggleClickbot();
			}
			while (CHANGE_INPUT_MODE.consumeClick()) {
				changeInputMode();
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			addMenuButton(screen);
			registerScreenInputHooks(screen);
		});
	}

	private static void addMenuButton(Screen screen) {
		if (screen instanceof TitleScreen) {
			addTitleScreenButton(screen);
			return;
		}
		if (screen instanceof PauseScreen pauseScreen && pauseScreen.showsPauseMenu()) {
			addPauseScreenButton(screen);
		}
	}

	private static void addTitleScreenButton(Screen screen) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		int centerX = clamp(screen.width / 2 - MENU_BUTTON_WIDTH / 2, SCREEN_MARGIN, screen.width - MENU_BUTTON_WIDTH - SCREEN_MARGIN);
		Integer menuRowY = widgets.stream()
			.filter(widget -> widget.getHeight() == MENU_BUTTON_HEIGHT)
			.filter(widget -> widget.getWidth() >= 90 && widget.getWidth() <= 110)
			.filter(widget -> Math.abs((widget.getX() + widget.getWidth() / 2) - screen.width / 2) <= 130)
			.max(Comparator.comparingInt(AbstractWidget::getY))
			.map(AbstractWidget::getY)
			.orElse(null);
		int buttonY;
		int buttonWidth;
		int buttonX;
		if (menuRowY != null) {
			buttonY = Math.min(menuRowY + 24, screen.height - MENU_BUTTON_HEIGHT - SCREEN_MARGIN);
			buttonWidth = MENU_BUTTON_WIDTH;
			buttonX = centerX;
		} else {
			buttonWidth = CORNER_BUTTON_WIDTH;
			buttonX = Math.max(SCREEN_MARGIN, screen.width - buttonWidth - SCREEN_MARGIN);
			buttonY = SCREEN_MARGIN;
		}
		widgets.add(createMenuButton(screen, buttonX, buttonY, buttonWidth));
	}

	private static void addPauseScreenButton(Screen screen) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		int centerX = clamp(screen.width / 2 - MENU_BUTTON_WIDTH / 2, SCREEN_MARGIN, screen.width - MENU_BUTTON_WIDTH - SCREEN_MARGIN);
		int buttonY = widgets.stream()
			.filter(widget -> widget.getHeight() == MENU_BUTTON_HEIGHT)
			.filter(widget -> widget.getWidth() >= 180)
			.filter(widget -> Math.abs((widget.getX() + widget.getWidth() / 2) - screen.width / 2) <= 24)
			.max(Comparator.comparingInt(AbstractWidget::getY))
			.map(widget -> Math.min(widget.getY() + 24, screen.height - MENU_BUTTON_HEIGHT - SCREEN_MARGIN))
			.orElse(SCREEN_MARGIN);
		widgets.add(createMenuButton(screen, centerX, buttonY, MENU_BUTTON_WIDTH));
	}

	private static Button createMenuButton(Screen screen, int x, int y, int width) {
		return Button.builder(CLICKPACKS_TEXT, ignored -> Screens.getMinecraft(screen).setScreen(new ClickpackBrowserScreen(screen)))
			.bounds(x, y, width, MENU_BUTTON_HEIGHT)
			.build();
	}

	private static void registerScreenInputHooks(Screen screen) {
		ScreenMouseEvents.afterMouseClick(screen).register((currentScreen, context, consumed) -> {
			if (consumed) {
				CONTROLLER.handleScreenMouseEvent(context.button(), true);
			}
			return consumed;
		});
		ScreenMouseEvents.afterMouseRelease(screen).register((currentScreen, context, consumed) -> {
			if (consumed) {
				CONTROLLER.handleScreenMouseEvent(context.button(), false);
			}
			return consumed;
		});
	}

	private static void toggleClickbot() {
		try {
			CONTROLLER.updateConfig(current -> current.enabled = !current.enabled);
		} catch (Exception exception) {
			ZCBLive.LOGGER.warn("Failed to toggle clickbot", exception);
		}
	}

	private static void changeInputMode() {
		try {
			CONTROLLER.cycleInputMode();
		} catch (Exception exception) {
			ZCBLive.LOGGER.warn("Failed to change input mode", exception);
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
