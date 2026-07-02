package lol.zeo.zcblive.client.gui;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lol.zeo.zcblive.client.ZcbClientController;
import lol.zeo.zcblive.client.db.ClickpackDbClient;
import lol.zeo.zcblive.client.db.ClickpackDbEntry;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public final class ClickpackBrowserScreen extends GuiScreen {
    private static final int BUTTON_REFRESH = 1;
    private static final int BUTTON_DOWNLOAD = 2;
    private static final int BUTTON_KEYBOARD = 3;
    private static final int BUTTON_MOUSE = 4;
    private static final int BUTTON_OPTIONS = 5;
    private static final int BUTTON_SORT = 6;
    private static final int BUTTON_BACK = 7;
    private static final int BUTTON_SORT_BASE = 20;

    private static boolean initialRefreshAttempted;

    private final GuiScreen parent;
    private final ZcbClientController controller;
    private final List<ClickpackDbEntry> allEntries = new ArrayList<ClickpackDbEntry>();
    private final List<ClickpackDbEntry> filteredEntries = new ArrayList<ClickpackDbEntry>();

    private ClickpackDbClient.DatabaseSnapshot snapshot;
    private ClickpackDbEntry selectedEntry;
    private GuiTextField searchBox;
    private ClickpackList clickpackList;
    private GuiButton refreshButton;
    private GuiButton sortDropdownButton;
    private GuiButton downloadButton;
    private GuiButton useKeyboardButton;
    private GuiButton useMouseButton;
    private GuiButton optionsButton;
    private final List<GuiButton> sortOptionButtons = new ArrayList<GuiButton>();
    private SortMode sortMode = SortMode.ALPHABETIC;
    private boolean sortDropdownOpen;
    private boolean refreshInProgress;
    private boolean downloadInProgress;
    private String statusText = "Loading ClickpackDB...";
    private String lastSearchQuery = "";

    public ClickpackBrowserScreen(GuiScreen parent, ZcbClientController controller) {
        this.parent = parent;
        this.controller = controller;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.sortOptionButtons.clear();

        int sortButtonWidth = 132;
        int searchWidth = Math.max(1, width - 24 - sortButtonWidth - 8);
        searchBox = new GuiTextField(0, fontRenderer, 12, 28, searchWidth, 18);
        searchBox.setMaxStringLength(128);
        searchBox.setCanLoseFocus(true);
        searchBox.setFocused(true);
        sortDropdownButton = addButtonCompat(new GuiButton(BUTTON_SORT, 20 + searchWidth, 28, sortButtonWidth, 20, sortMode.label()));
        addSortOptionButtons(20 + searchWidth, 48, sortButtonWidth);

        int contentTop = 54;
        int contentBottom = height - 84;
        int listWidth = Math.max(220, (width - 36) / 2);
        clickpackList = new ClickpackList(12, listWidth, contentBottom, contentTop, 26);

        int buttonWidth = Math.max(1, Math.min(160, (width - 40) / 3));
        int buttonGap = 8;
        int rowWidth = buttonWidth * 3 + buttonGap * 2;
        int buttonX = Math.max(12, (width - rowWidth) / 2);
        int topRowY = height - 50;
        int bottomRowY = height - 28;

        refreshButton = addButtonCompat(new GuiButton(BUTTON_REFRESH, buttonX, topRowY, buttonWidth, 20, "Refresh"));
        downloadButton = addButtonCompat(new GuiButton(BUTTON_DOWNLOAD, buttonX + buttonWidth + buttonGap, topRowY, buttonWidth, 20, "Download"));
        useKeyboardButton = addButtonCompat(new GuiButton(BUTTON_KEYBOARD, buttonX + (buttonWidth + buttonGap) * 2, topRowY, buttonWidth, 20, "Use as Keyboard"));
        useMouseButton = addButtonCompat(new GuiButton(BUTTON_MOUSE, buttonX, bottomRowY, buttonWidth, 20, "Use as Mouse"));
        optionsButton = addButtonCompat(new GuiButton(BUTTON_OPTIONS, buttonX + buttonWidth + buttonGap, bottomRowY, buttonWidth, 20, "Options"));
        addButtonCompat(new GuiButton(BUTTON_BACK, buttonX + (buttonWidth + buttonGap) * 2, bottomRowY, buttonWidth, 20, "Back"));

        loadSnapshot(controller.cachedSnapshot());
        if (!allEntries.isEmpty()) {
            statusText = "Showing installed and cached clickpacks.";
        } else {
            statusText = "Showing installed clickpacks. Press Refresh to load ClickpackDB.";
        }
        if (!initialRefreshAttempted) {
            initialRefreshAttempted = true;
            refreshDatabase();
        }
        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        searchBox.updateCursorCounter();
        String query = searchBox.getText();
        if (!query.equals(lastSearchQuery)) {
            lastSearchQuery = query;
            rebuildFilteredEntries();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        clickpackList.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchBox.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (!searchBox.textboxKeyTyped(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == null || !button.enabled) {
            return;
        }

        switch (button.id) {
            case BUTTON_REFRESH:
                refreshDatabase();
                return;
            case BUTTON_DOWNLOAD:
                downloadSelected();
                return;
            case BUTTON_KEYBOARD:
                activateSelectedForKeyboard();
                return;
            case BUTTON_MOUSE:
                activateSelectedForMouse();
                return;
            case BUTTON_OPTIONS:
                openOptions();
                return;
            case BUTTON_SORT:
                toggleSortDropdown();
                return;
            case BUTTON_BACK:
                mc.displayGuiScreen(parent);
                return;
            default:
                if (button.id >= BUTTON_SORT_BASE && button.id < BUTTON_SORT_BASE + SortMode.values().length) {
                    setSortMode(SortMode.values()[button.id - BUTTON_SORT_BASE]);
                    return;
                }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        clickpackList.drawScreen(mouseX, mouseY, partialTicks);

        int panelX = width / 2 + 8;
        int panelY = 54;
        int panelWidth = width - panelX - 12;
        int panelHeight = height - panelY - 84;

        drawCenteredString(fontRenderer, "ClickpackDB", width / 2, 10, 0xFFFFFFFF);
        drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x7A000000);
        drawHorizontalLine(panelX, panelX + panelWidth - 1, panelY, 0xFF4A4A4A);
        drawHorizontalLine(panelX, panelX + panelWidth - 1, panelY + panelHeight - 1, 0xFF4A4A4A);
        drawVerticalLine(panelX, panelY, panelY + panelHeight - 1, 0xFF4A4A4A);
        drawVerticalLine(panelX + panelWidth - 1, panelY, panelY + panelHeight - 1, 0xFF4A4A4A);
        drawString(fontRenderer, statusText, 12, height - 72, 0xFFD0D0D0);
        if (sortDropdownOpen && !sortOptionButtons.isEmpty()) {
            GuiButton lastButton = sortOptionButtons.get(sortOptionButtons.size() - 1);
            drawRect(sortDropdownButton.x, sortDropdownButton.y + sortDropdownButton.height, sortDropdownButton.x + sortDropdownButton.width, lastButton.y + lastButton.height, 0xE0101010);
        }

        searchBox.drawTextBox();
        if (!searchBox.isFocused() && searchBox.getText().isEmpty()) {
            drawString(fontRenderer, "Search clickpacks", searchBox.x + 4, searchBox.y + 6, 0xFF808080);
        }

        renderDetailPanel(panelX, panelY, panelWidth, panelHeight);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void renderDetailPanel(int x, int y, int width, int height) {
        if (selectedEntry == null) {
            drawString(fontRenderer, "Select a clickpack from the list.", x + 8, y + 8, 0xFFFFFFFF);
            return;
        }

        int drawY = y + 8;
        drawString(fontRenderer, displayName(selectedEntry), x + 8, drawY, 0xFFFFFFFF);
        drawY += 14;
        drawString(fontRenderer, "Compressed: " + formatSize(selectedEntry.size()), x + 8, drawY, 0xFFD0D0D0);
        drawY += 10;
        drawString(fontRenderer, "Uncompressed: " + formatSize(selectedEntry.uncompressedSize()), x + 8, drawY, 0xFFD0D0D0);
        drawY += 10;
        drawString(fontRenderer, "Downloads: " + formatDownloads(selectedEntry.downloadCount()), x + 8, drawY, 0xFFD0D0D0);
        drawY += 10;
        drawString(fontRenderer, "Noise: " + (selectedEntry.hasNoise() ? "yes" : "no"), x + 8, drawY, 0xFFD0D0D0);
        drawY += 10;
        drawString(fontRenderer, "Installed: " + (controller.isInstalled(selectedEntry.name()) ? "yes" : "no"), x + 8, drawY, 0xFFD0D0D0);
        drawY += 10;
        drawString(fontRenderer, "Keyboard active: " + (controller.isKeyboardActivePack(selectedEntry.name()) ? "yes" : "no"), x + 8, drawY, 0xFFD0D0D0);
        drawY += 10;
        drawString(fontRenderer, "Mouse active: " + (controller.isMouseActivePack(selectedEntry.name()) ? "yes" : "no"), x + 8, drawY, 0xFFD0D0D0);
        drawY += 14;

        String updatedAt = snapshot != null && snapshot.updatedAtIso() != null ? formatRelativeTime(snapshot.updatedAtIso()) : "unknown";
        drawString(fontRenderer, "DB updated: " + updatedAt, x + 8, drawY, 0xFFA8A8A8);
        drawY += 16;
        drawString(fontRenderer, "Readme", x + 8, drawY, 0xFFFFFFFF);
        drawY += 12;

        String readme = selectedEntry.readme() == null || selectedEntry.readme().trim().isEmpty()
            ? "No readme in ClickpackDB."
            : selectedEntry.readme();
        List<String> lines = fontRenderer.listFormattedStringToWidth(readme, width - 16);
        int maxLines = Math.max(1, (height - (drawY - y) - 8) / 9);
        int rendered = 0;
        for (String line : lines) {
            if (rendered >= maxLines) {
                drawString(fontRenderer, "...", x + 8, drawY, 0xFFA8A8A8);
                break;
            }
            drawString(fontRenderer, line, x + 8, drawY, 0xFFDCDCDC);
            drawY += 9;
            rendered++;
        }
    }

    private void refreshDatabase() {
        if (refreshInProgress || downloadInProgress) {
            return;
        }

        refreshInProgress = true;
        statusText = "Refreshing ClickpackDB...";
        updateButtons();

        controller.refreshDatabase().whenComplete((loadedSnapshot, throwable) -> {
            scheduleOnMainThread(() -> {
                refreshInProgress = false;
                if (throwable != null) {
                    statusText = "Failed to refresh: " + rootMessage(throwable);
                } else {
                    loadSnapshot(loadedSnapshot);
                    statusText = "Loaded " + loadedSnapshot.entries().size() + " clickpacks.";
                }
                updateButtons();
            });
        });
    }

    private void downloadSelected() {
        if (selectedEntry == null || refreshInProgress || downloadInProgress || controller.isInstalled(selectedEntry.name())) {
            return;
        }

        downloadInProgress = true;
        statusText = "Downloading " + selectedEntry.name() + "...";
        updateButtons();

        controller.downloadClickpack(selectedEntry).whenComplete((ignored, throwable) -> {
            scheduleOnMainThread(() -> {
                downloadInProgress = false;
                if (throwable != null) {
                    statusText = "Download failed: " + rootMessage(throwable);
                } else {
                    statusText = "Installed " + selectedEntry.name() + ".";
                }
                rebuildFilteredEntries();
                updateButtons();
            });
        });
    }

    private void activateSelectedForKeyboard() {
        if (selectedEntry == null) {
            return;
        }
        try {
            controller.activateInstalledForKeyboard(selectedEntry.name());
            statusText = "Using " + selectedEntry.name() + " for keyboard.";
        } catch (Exception exception) {
            statusText = "Keyboard assignment failed: " + rootMessage(exception);
        }
        rebuildFilteredEntries();
        updateButtons();
    }

    private void activateSelectedForMouse() {
        if (selectedEntry == null) {
            return;
        }
        try {
            controller.activateInstalledForMouse(selectedEntry.name());
            statusText = "Using " + selectedEntry.name() + " for mouse.";
        } catch (Exception exception) {
            statusText = "Mouse assignment failed: " + rootMessage(exception);
        }
        rebuildFilteredEntries();
        updateButtons();
    }

    private void openOptions() {
        if (mc != null) {
            mc.displayGuiScreen(new ClickpackOptionsScreen(this, controller));
        }
    }

    private void addSortOptionButtons(int x, int y, int width) {
        for (int index = 0; index < SortMode.values().length; index++) {
            SortMode mode = SortMode.values()[index];
            GuiButton button = addButtonCompat(new GuiButton(BUTTON_SORT_BASE + index, x, y + index * 20, width, 20, mode.label()));
            button.visible = sortDropdownOpen;
            sortOptionButtons.add(button);
        }
    }

    private void toggleSortDropdown() {
        sortDropdownOpen = !sortDropdownOpen;
        updateButtons();
    }

    private void setSortMode(SortMode mode) {
        sortMode = mode;
        sortDropdownOpen = false;
        rebuildFilteredEntries();
        updateButtons();
    }

    private void loadSnapshot(ClickpackDbClient.DatabaseSnapshot snapshot) {
        this.snapshot = snapshot;
        allEntries.clear();
        allEntries.addAll(controller.browserEntries());
        rebuildFilteredEntries();
    }

    private void rebuildFilteredEntries() {
        String query = searchBox == null ? "" : searchBox.getText().trim().toLowerCase(Locale.ROOT);
        filteredEntries.clear();
        for (ClickpackDbEntry entry : allEntries) {
            if (query.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(query)) {
                filteredEntries.add(entry);
            }
        }
        Collections.sort(filteredEntries, sortMode.comparator());

        if (selectedEntry == null && !filteredEntries.isEmpty()) {
            selectedEntry = filteredEntries.get(0);
        }
        if (selectedEntry != null && !containsByName(filteredEntries, selectedEntry.name())) {
            selectedEntry = filteredEntries.isEmpty() ? null : filteredEntries.get(0);
        }

        clickpackList.updateSelection();
        updateButtons();
    }

    private void updateButtons() {
        boolean installed = selectedEntry != null && controller.isInstalled(selectedEntry.name());
        if (refreshButton != null) {
            refreshButton.enabled = !refreshInProgress && !downloadInProgress;
        }
        if (sortDropdownButton != null) {
            sortDropdownButton.enabled = !refreshInProgress && !downloadInProgress;
            sortDropdownButton.displayString = sortMode.label();
        }
        for (int index = 0; index < sortOptionButtons.size(); index++) {
            GuiButton button = sortOptionButtons.get(index);
            button.visible = sortDropdownOpen;
            button.enabled = !refreshInProgress && !downloadInProgress && SortMode.values()[index] != sortMode;
        }
        if (downloadButton != null) {
            downloadButton.enabled = selectedEntry != null && !installed && !refreshInProgress && !downloadInProgress;
        }
        if (useKeyboardButton != null) {
            useKeyboardButton.enabled = selectedEntry != null && installed;
        }
        if (useMouseButton != null) {
            useMouseButton.enabled = selectedEntry != null && installed;
        }
        if (optionsButton != null) {
            optionsButton.enabled = true;
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0D);
        }
        return String.format(Locale.ROOT, "%.2f MiB", bytes / (1024.0D * 1024.0D));
    }

    private String formatDownloads(long downloads) {
        return String.format(Locale.ROOT, "%,d", downloads);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String formatRelativeTime(String isoDateTime) {
        try {
            OffsetDateTime updatedAt = OffsetDateTime.parse(isoDateTime);
            Duration duration = Duration.between(updatedAt, OffsetDateTime.now());
            if (duration.isNegative()) {
                return "just now";
            }

            long seconds = duration.getSeconds();
            if (seconds < 60L) {
                return seconds <= 1L ? "1 second ago" : seconds + " seconds ago";
            }

            long minutes = duration.toMinutes();
            if (minutes < 60L) {
                return minutes == 1L ? "1 minute ago" : minutes + " minutes ago";
            }

            long hours = duration.toHours();
            if (hours < 24L) {
                return hours == 1L ? "1 hour ago" : hours + " hours ago";
            }

            long days = duration.toDays();
            if (days < 30L) {
                return days == 1L ? "1 day ago" : days + " days ago";
            }

            long months = days / 30L;
            if (months < 12L) {
                return months == 1L ? "1 month ago" : months + " months ago";
            }

            long years = days / 365L;
            return years == 1L ? "1 year ago" : years + " years ago";
        } catch (DateTimeParseException exception) {
            return isoDateTime;
        }
    }

    private String displayName(ClickpackDbEntry entry) {
        StringBuilder builder = new StringBuilder();
        if (controller.isKeyboardActivePack(entry.name()) && controller.isMouseActivePack(entry.name())) {
            builder.append("[Keyboard+Mouse] ");
        } else if (controller.isKeyboardActivePack(entry.name())) {
            builder.append("[Keyboard] ");
        } else if (controller.isMouseActivePack(entry.name())) {
            builder.append("[Mouse] ");
        } else if (controller.isInstalled(entry.name())) {
            builder.append("[Installed] ");
        }
        builder.append(entry.name());
        return builder.toString();
    }

    private static long sortTimestamp(ClickpackDbEntry entry) {
        if (entry.addedAt() == null || entry.addedAt().trim().isEmpty()) {
            return Long.MIN_VALUE;
        }
        try {
            return OffsetDateTime.parse(entry.addedAt()).toInstant().toEpochMilli();
        } catch (DateTimeParseException exception) {
            return Long.MIN_VALUE;
        }
    }

    private boolean containsByName(List<ClickpackDbEntry> entries, String name) {
        for (ClickpackDbEntry entry : entries) {
            if (entry.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void scheduleOnMainThread(Runnable runnable) {
        if (mc != null) {
            mc.addScheduledTask(runnable);
        }
    }

    private GuiButton addButtonCompat(GuiButton button) {
        this.buttonList.add(button);
        return button;
    }

    private enum SortMode {
        ALPHABETIC("Sort: Alphabetic") {
            @Override
            Comparator<ClickpackDbEntry> comparator() {
                return byName();
            }
        },
        BIGGER_SIZE("Sort: Bigger Size") {
            @Override
            Comparator<ClickpackDbEntry> comparator() {
                return new Comparator<ClickpackDbEntry>() {
                    @Override
                    public int compare(ClickpackDbEntry left, ClickpackDbEntry right) {
                        int compare = Long.compare(right.size(), left.size());
                        return compare != 0 ? compare : byName().compare(left, right);
                    }
                };
            }
        },
        SMALLER_SIZE("Sort: Smaller Size") {
            @Override
            Comparator<ClickpackDbEntry> comparator() {
                return new Comparator<ClickpackDbEntry>() {
                    @Override
                    public int compare(ClickpackDbEntry left, ClickpackDbEntry right) {
                        int compare = Long.compare(left.size(), right.size());
                        return compare != 0 ? compare : byName().compare(left, right);
                    }
                };
            }
        },
        MOST_DOWNLOADED("Sort: Most Downloaded") {
            @Override
            Comparator<ClickpackDbEntry> comparator() {
                return new Comparator<ClickpackDbEntry>() {
                    @Override
                    public int compare(ClickpackDbEntry left, ClickpackDbEntry right) {
                        int compare = Long.compare(right.downloadCount(), left.downloadCount());
                        return compare != 0 ? compare : byName().compare(left, right);
                    }
                };
            }
        },
        LEAST_DOWNLOADED("Sort: Least Downloaded") {
            @Override
            Comparator<ClickpackDbEntry> comparator() {
                return new Comparator<ClickpackDbEntry>() {
                    @Override
                    public int compare(ClickpackDbEntry left, ClickpackDbEntry right) {
                        int compare = Long.compare(left.downloadCount(), right.downloadCount());
                        return compare != 0 ? compare : byName().compare(left, right);
                    }
                };
            }
        },
        NEWEST_FIRST("Sort: Newest First") {
            @Override
            Comparator<ClickpackDbEntry> comparator() {
                return new Comparator<ClickpackDbEntry>() {
                    @Override
                    public int compare(ClickpackDbEntry left, ClickpackDbEntry right) {
                        int compare = Long.compare(sortTimestamp(right), sortTimestamp(left));
                        return compare != 0 ? compare : byName().compare(left, right);
                    }
                };
            }
        },
        OLDEST_FIRST("Sort: Oldest First") {
            @Override
            Comparator<ClickpackDbEntry> comparator() {
                return new Comparator<ClickpackDbEntry>() {
                    @Override
                    public int compare(ClickpackDbEntry left, ClickpackDbEntry right) {
                        int compare = Long.compare(sortTimestamp(left), sortTimestamp(right));
                        return compare != 0 ? compare : byName().compare(left, right);
                    }
                };
            }
        };

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        abstract Comparator<ClickpackDbEntry> comparator();

        static Comparator<ClickpackDbEntry> byName() {
            return new Comparator<ClickpackDbEntry>() {
                @Override
                public int compare(ClickpackDbEntry left, ClickpackDbEntry right) {
                    return left.name().toLowerCase(Locale.ROOT).compareTo(right.name().toLowerCase(Locale.ROOT));
                }
            };
        }
    }

    private final class ClickpackList extends GuiSlot {
        private ClickpackList(int left, int width, int bottom, int top, int slotHeight) {
            super(ClickpackBrowserScreen.this.mc, width, ClickpackBrowserScreen.this.height, top, bottom, slotHeight);
            this.left = left;
            this.right = left + width;
        }

        @Override
        protected int getSize() {
            return filteredEntries.size();
        }

        @Override
        protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
            if (slotIndex >= 0 && slotIndex < filteredEntries.size()) {
                selectedEntry = filteredEntries.get(slotIndex);
                updateButtons();
            }
        }

        @Override
        protected boolean isSelected(int slotIndex) {
            return selectedEntry != null
                && slotIndex >= 0
                && slotIndex < filteredEntries.size()
                && selectedEntry.name().equals(filteredEntries.get(slotIndex).name());
        }

        @Override
        protected void drawBackground() {
            // Background is drawn by the parent screen.
        }

        @Override
        protected void drawSlot(int entryId, int x, int y, int slotHeight, int mouseX, int mouseY, float partialTicks) {
            ClickpackDbEntry entry = filteredEntries.get(entryId);
            fontRenderer.drawString(displayName(entry), x + 2, y + 2, 0xFFFFFFFF);
            String downloads = formatDownloads(entry.downloadCount());
            int downloadsX = this.right - 12 - fontRenderer.getStringWidth(downloads);
            fontRenderer.drawString(downloads, downloadsX, y + 2, 0xFF8F8F8F);
            fontRenderer.drawString(formatSize(entry.size()), x + 2, y + 13, 0xFFB8B8B8);
        }

        @Override
        public int getListWidth() {
            return this.width - 10;
        }

        @Override
        protected int getScrollBarX() {
            return this.left + this.width - 6;
        }

        private void updateSelection() {
            // Selection visuals are derived from isSelected().
        }
    }
}
