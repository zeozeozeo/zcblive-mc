package lol.zeo.zcblive;

import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
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
	private static final ZcbClientController CONTROLLER = new ZcbClientController();

	public static ZcbClientController controller() {
		return CONTROLLER;
	}

	@Override
	public void onInitializeClient() {
		CONTROLLER.initialize();
		ClientTickEvents.END_CLIENT_TICK.register(client -> CONTROLLER.tick());
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
		List<AbstractWidget> widgets = Screens.getButtons(screen);
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
		List<AbstractWidget> widgets = Screens.getButtons(screen);
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
		return Button.builder(CLICKPACKS_TEXT, ignored -> Screens.getClient(screen).setScreen(new ClickpackBrowserScreen(screen)))
			.bounds(x, y, width, MENU_BUTTON_HEIGHT)
			.build();
	}

	private static void registerScreenInputHooks(Screen screen) {
		ScreenMouseEvents.afterMouseClick(screen).register((currentScreen, mouseX, mouseY, button) -> CONTROLLER.handleScreenMouseEvent(button, true));
		ScreenMouseEvents.afterMouseRelease(screen).register((currentScreen, mouseX, mouseY, button) -> CONTROLLER.handleScreenMouseEvent(button, false));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
