package lol.zeo.zcblive.client;

import java.util.List;
import lol.zeo.zcblive.client.gui.ClickpackBrowserScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiScreenEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ZcbForgeClient {
    private static final int MENU_BUTTON_ID = 904218;
    private static final String MENU_BUTTON_TEXT = "Clickpacks";
    private static final int MENU_BUTTON_WIDTH = 200;
    private static final int MENU_BUTTON_HEIGHT = 20;
    private static final int CORNER_BUTTON_WIDTH = 120;
    private static final int SCREEN_MARGIN = 12;

    private static ZcbForgeClient instance;

    private final ZcbClientController controller = new ZcbClientController();

    public void initialize() {
        instance = this;
        controller.initialize();
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    public static ZcbForgeClient instance() {
        return instance;
    }

    public ZcbClientController controller() {
        return controller;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            controller.tick();
        }
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (Keyboard.isRepeatEvent()) {
            return;
        }
        int keyCode = Keyboard.getEventKey();
        if (keyCode == Keyboard.KEY_NONE) {
            keyCode = Keyboard.getEventCharacter() + 256;
        }
        controller.handleKeyboardEvent(keyCode, Keyboard.getEventKeyState(), Keyboard.getEventNanoseconds());
    }

    @SubscribeEvent
    public void onMouseInput(InputEvent.MouseInputEvent event) {
        int button = Mouse.getEventButton();
        if (button < 0) {
            return;
        }
        boolean state = Mouse.getEventButtonState();
        controller.handleMouseEvent(button, state, Mouse.getEventNanoseconds());
        if (button == 3 || button == 4) {
            controller.handleScreenMouseEvent(button, state);
        }
    }

    @SubscribeEvent
    public void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.gui;
        if (gui instanceof GuiMainMenu) {
            addTitleScreenButton(gui, event.buttonList);
            return;
        }
        if (gui instanceof GuiIngameMenu) {
            addPauseScreenButton(gui, event.buttonList);
        }
    }

    private void addTitleScreenButton(GuiScreen gui, List<GuiButton> buttonList) {
        if (containsButton(buttonList, MENU_BUTTON_ID)) {
            return;
        }

        int centerX = clamp(gui.width / 2 - MENU_BUTTON_WIDTH / 2, SCREEN_MARGIN, gui.width - MENU_BUTTON_WIDTH - SCREEN_MARGIN);
        Integer menuRowY = null;
        for (GuiButton button : buttonList) {
            if (button == null) {
                continue;
            }
            if (button.height != MENU_BUTTON_HEIGHT) {
                continue;
            }
            if (button.width < 90 || button.width > 110) {
                continue;
            }
            int centerDelta = Math.abs((button.xPosition + button.width / 2) - gui.width / 2);
            if (centerDelta > 130) {
                continue;
            }
            if (menuRowY == null || button.yPosition > menuRowY.intValue()) {
                menuRowY = Integer.valueOf(button.yPosition);
            }
        }

        final int x;
        final int y;
        final int width;
        if (menuRowY != null) {
            x = centerX;
            width = MENU_BUTTON_WIDTH;
            y = Math.min(menuRowY.intValue() + 24, gui.height - MENU_BUTTON_HEIGHT - SCREEN_MARGIN);
        } else {
            width = CORNER_BUTTON_WIDTH;
            x = Math.max(SCREEN_MARGIN, gui.width - width - SCREEN_MARGIN);
            y = SCREEN_MARGIN;
        }

        buttonList.add(new GuiButton(MENU_BUTTON_ID, x, y, width, MENU_BUTTON_HEIGHT, MENU_BUTTON_TEXT));
    }

    private void addPauseScreenButton(GuiScreen gui, List<GuiButton> buttonList) {
        if (containsButton(buttonList, MENU_BUTTON_ID)) {
            return;
        }

        int centerX = clamp(gui.width / 2 - MENU_BUTTON_WIDTH / 2, SCREEN_MARGIN, gui.width - MENU_BUTTON_WIDTH - SCREEN_MARGIN);
        Integer menuRowY = null;
        for (GuiButton button : buttonList) {
            if (button == null) {
                continue;
            }
            if (button.height != MENU_BUTTON_HEIGHT) {
                continue;
            }
            if (button.width < 180) {
                continue;
            }
            int centerDelta = Math.abs((button.xPosition + button.width / 2) - gui.width / 2);
            if (centerDelta > 24) {
                continue;
            }
            if (menuRowY == null || button.yPosition > menuRowY.intValue()) {
                menuRowY = Integer.valueOf(button.yPosition);
            }
        }

        int y = menuRowY == null
            ? SCREEN_MARGIN
            : Math.min(menuRowY.intValue() + 24, gui.height - MENU_BUTTON_HEIGHT - SCREEN_MARGIN);
        buttonList.add(new GuiButton(MENU_BUTTON_ID, centerX, y, MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT, MENU_BUTTON_TEXT));
    }

    @SubscribeEvent
    public void onScreenAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        GuiButton button = event.button;
        if (button == null || button.id != MENU_BUTTON_ID) {
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(new ClickpackBrowserScreen(event.gui, controller));
        event.setCanceled(true);
    }

    private boolean containsButton(List<GuiButton> buttonList, int id) {
        for (GuiButton button : buttonList) {
            if (button != null && button.id == id) {
                return true;
            }
        }
        return false;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
