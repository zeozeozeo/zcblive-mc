package lol.zeo.zcblive.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import lol.zeo.zcblive.ZCBLiveClient;
import lol.zeo.zcblive.client.ZcbClientController;
import lol.zeo.zcblive.client.db.ClickpackDbClient;
import lol.zeo.zcblive.client.db.ClickpackDbEntry;

public final class ClickpackBrowserScreen extends Screen {
	private static final Component TITLE = Component.literal("ClickpackDB");
	private static final Component SEARCH_HINT = Component.literal("Search clickpacks");
	private static boolean initialRefreshAttempted;

	private final Screen parent;
	private final ZcbClientController controller = ZCBLiveClient.controller();
	private final List<ClickpackDbEntry> allEntries = new ArrayList<>();

	private ClickpackDbClient.DatabaseSnapshot snapshot;
	private @Nullable ClickpackDbEntry selectedEntry;
	private @Nullable EditBox searchBox;
	private @Nullable ClickpackList clickpackList;
	private @Nullable Button refreshButton;
	private @Nullable Button downloadButton;
	private @Nullable Button useKeyboardButton;
	private @Nullable Button useMouseButton;
	private boolean refreshInProgress;
	private boolean downloadInProgress;
	private Component statusText = Component.literal("Loading ClickpackDB...");

	public ClickpackBrowserScreen(Screen parent) {
		super(TITLE);
		this.parent = parent;
	}

	@Override
	protected void init() {
		clearWidgets();
		int searchWidth = Math.max(1, width - 24);
		searchBox = addRenderableWidget(new EditBox(font, 12, 28, searchWidth, 18, Component.empty()));
		searchBox.setHint(SEARCH_HINT);
		searchBox.setResponder(ignored -> rebuildFilteredEntries());

		int panelX = width / 2 + 8;
		int contentTop = 54;
		int contentBottom = height - 84;
		int listWidth = Math.max(1, panelX - 24);
		int listHeight = Math.max(1, contentBottom - contentTop);
		clickpackList = addRenderableWidget(new ClickpackList(minecraft, listWidth, listHeight, contentTop, 26));
		clickpackList.updateSizeAndPosition(listWidth, listHeight, contentTop);

		int buttonWidth = Math.max(1, Math.min(160, (width - 40) / 3));
		int buttonGap = 8;
		int rowWidth = buttonWidth * 3 + buttonGap * 2;
		int buttonX = Math.max(12, (width - rowWidth) / 2);
		int topRowY = height - 50;
		int bottomRowY = height - 28;
		refreshButton = addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> refreshDatabase()).bounds(buttonX, topRowY, buttonWidth, 20).build());
		downloadButton = addRenderableWidget(Button.builder(Component.literal("Download"), ignored -> downloadSelected()).bounds(buttonX + buttonWidth + buttonGap, topRowY, buttonWidth, 20).build());
		useKeyboardButton = addRenderableWidget(Button.builder(Component.literal("Use as Keyboard"), ignored -> activateSelectedForKeyboard()).bounds(buttonX + (buttonWidth + buttonGap) * 2, topRowY, buttonWidth, 20).build());
		useMouseButton = addRenderableWidget(Button.builder(Component.literal("Use as Mouse"), ignored -> activateSelectedForMouse()).bounds(buttonX, bottomRowY, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Options"), ignored -> openOptions()).bounds(buttonX + buttonWidth + buttonGap, bottomRowY, buttonWidth, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, ignored -> onClose()).bounds(buttonX + (buttonWidth + buttonGap) * 2, bottomRowY, buttonWidth, 20).build());

		setInitialFocus(searchBox);
		loadSnapshot(controller.cachedSnapshot());
		if (!allEntries.isEmpty()) {
			statusText = Component.literal("Showing installed and cached clickpacks.");
		} else {
			statusText = Component.literal("Showing installed clickpacks. Press Refresh to load ClickpackDB.");
		}
		if (!initialRefreshAttempted) {
			initialRefreshAttempted = true;
			refreshDatabase();
		}
		updateButtons();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
		int panelX = width / 2 + 8;
		int panelY = 54;
		int panelWidth = Math.max(1, width - panelX - 12);
		int panelHeight = Math.max(1, height - panelY - 84);

		graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x7A000000);
		graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, 0xFF4A4A4A);
		graphics.drawString(font, statusText, 12, height - 72, 0xFFD0D0D0, false);

		super.render(graphics, mouseX, mouseY, a);
		renderDetailPanel(graphics, panelX, panelY, panelWidth, panelHeight);
	}

	private void renderDetailPanel(GuiGraphics graphics, int x, int y, int width, int height) {
		if (selectedEntry == null) {
			graphics.drawString(font, Component.literal("Select a clickpack from the list."), x + 8, y + 8, 0xFFFFFFFF, false);
			return;
		}

		int drawY = y + 8;
		graphics.drawString(font, Component.literal(selectedEntry.name()), x + 8, drawY, 0xFFFFFFFF, false);
		drawY += 14;
		graphics.drawString(font, Component.literal("Compressed: " + formatSize(selectedEntry.size())), x + 8, drawY, 0xFFD0D0D0, false);
		drawY += 10;
		graphics.drawString(font, Component.literal("Uncompressed: " + formatSize(selectedEntry.uncompressedSize())), x + 8, drawY, 0xFFD0D0D0, false);
		drawY += 10;
		graphics.drawString(font, Component.literal("Noise: " + (selectedEntry.hasNoise() ? "yes" : "no")), x + 8, drawY, 0xFFD0D0D0, false);
		drawY += 10;
		graphics.drawString(font, Component.literal("Installed: " + (controller.isInstalled(selectedEntry.name()) ? "yes" : "no")), x + 8, drawY, 0xFFD0D0D0, false);
		drawY += 10;
		graphics.drawString(font, Component.literal("Keyboard active: " + (controller.isKeyboardActivePack(selectedEntry.name()) ? "yes" : "no")), x + 8, drawY, 0xFFD0D0D0, false);
		drawY += 10;
		graphics.drawString(font, Component.literal("Mouse active: " + (controller.isMouseActivePack(selectedEntry.name()) ? "yes" : "no")), x + 8, drawY, 0xFFD0D0D0, false);
		drawY += 14;

		String updatedAt = snapshot != null && snapshot.updatedAtIso() != null ? formatRelativeTime(snapshot.updatedAtIso()) : "unknown";
		graphics.drawString(font, Component.literal("DB updated: " + updatedAt), x + 8, drawY, 0xFFA8A8A8, false);
		drawY += 16;
		graphics.drawString(font, Component.literal("Readme"), x + 8, drawY, 0xFFFFFFFF, false);
		drawY += 12;

		String readme = selectedEntry.readme() == null || selectedEntry.readme().isBlank() ? "No readme in ClickpackDB." : selectedEntry.readme();
		List<FormattedCharSequence> lines = font.split(Component.literal(readme), width - 16);
		int maxLines = Math.max(1, (height - (drawY - y) - 8) / 9);
		int rendered = 0;
		for (FormattedCharSequence line : lines) {
			if (rendered >= maxLines) {
				graphics.drawString(font, Component.literal("..."), x + 8, drawY, 0xFFA8A8A8, false);
				break;
			}
			graphics.drawString(font, line, x + 8, drawY, 0xFFDCDCDC, false);
			drawY += 9;
			rendered++;
		}
	}

	private void refreshDatabase() {
		if (refreshInProgress || downloadInProgress) {
			return;
		}
		refreshInProgress = true;
		statusText = Component.literal("Refreshing ClickpackDB...");
		updateButtons();
		controller.refreshDatabase().whenComplete((loadedSnapshot, throwable) -> screenExecutor.execute(() -> {
			refreshInProgress = false;
			if (throwable != null) {
				statusText = Component.literal("Failed to refresh: " + rootMessage(throwable));
			} else {
				loadSnapshot(loadedSnapshot);
				statusText = Component.literal("Loaded " + loadedSnapshot.entries().size() + " clickpacks.");
			}
			updateButtons();
		}));
	}

	private void downloadSelected() {
		if (selectedEntry == null || refreshInProgress || downloadInProgress || controller.isInstalled(selectedEntry.name())) {
			return;
		}
		downloadInProgress = true;
		statusText = Component.literal("Downloading " + selectedEntry.name() + "...");
		updateButtons();
		controller.downloadClickpack(selectedEntry).whenComplete((ignored, throwable) -> screenExecutor.execute(() -> {
			downloadInProgress = false;
			if (throwable != null) {
				statusText = Component.literal("Download failed: " + rootMessage(throwable));
			} else {
				statusText = Component.literal("Installed " + selectedEntry.name() + ".");
			}
			rebuildFilteredEntries();
			updateButtons();
		}));
	}

	private void activateSelectedForKeyboard() {
		if (selectedEntry == null) {
			return;
		}
		try {
			controller.activateInstalledForKeyboard(selectedEntry.name());
			statusText = Component.literal("Using " + selectedEntry.name() + " for keyboard.");
		} catch (Exception exception) {
			statusText = Component.literal("Keyboard assignment failed: " + rootMessage(exception));
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
			statusText = Component.literal("Using " + selectedEntry.name() + " for mouse.");
		} catch (Exception exception) {
			statusText = Component.literal("Mouse assignment failed: " + rootMessage(exception));
		}
		rebuildFilteredEntries();
		updateButtons();
	}

	private void openOptions() {
		if (minecraft != null) {
			minecraft.setScreen(new ClickpackOptionsScreen(this));
		}
	}

	private void loadSnapshot(ClickpackDbClient.DatabaseSnapshot snapshot) {
		this.snapshot = snapshot;
		allEntries.clear();
		allEntries.addAll(controller.browserEntries());
		rebuildFilteredEntries();
	}

	private void rebuildFilteredEntries() {
		if (clickpackList == null) {
			return;
		}
		String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		List<ClickpackDbEntry> filtered = allEntries.stream()
			.filter(entry -> query.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(query))
			.toList();
		clickpackList.setEntries(filtered);
		if (selectedEntry == null && !filtered.isEmpty()) {
			selectedEntry = filtered.getFirst();
		}
		if (selectedEntry != null && filtered.stream().noneMatch(entry -> entry.name().equals(selectedEntry.name()))) {
			selectedEntry = filtered.isEmpty() ? null : filtered.getFirst();
		}
		clickpackList.restoreSelection(selectedEntry);
		updateButtons();
	}

	private void updateButtons() {
		boolean installed = selectedEntry != null && controller.isInstalled(selectedEntry.name());
		if (refreshButton != null) {
			refreshButton.active = !refreshInProgress && !downloadInProgress;
		}
		if (downloadButton != null) {
			downloadButton.active = selectedEntry != null && !installed && !refreshInProgress && !downloadInProgress;
		}
		if (useKeyboardButton != null) {
			useKeyboardButton.active = selectedEntry != null && installed;
		}
		if (useMouseButton != null) {
			useMouseButton.active = selectedEntry != null && installed;
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

			long seconds = duration.toSeconds();
			if (seconds < 60) {
				return seconds <= 1 ? "1 second ago" : seconds + " seconds ago";
			}

			long minutes = duration.toMinutes();
			if (minutes < 60) {
				return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
			}

			long hours = duration.toHours();
			if (hours < 24) {
				return hours == 1 ? "1 hour ago" : hours + " hours ago";
			}

			long days = duration.toDays();
			if (days < 30) {
				return days == 1 ? "1 day ago" : days + " days ago";
			}

			long months = days / 30;
			if (months < 12) {
				return months == 1 ? "1 month ago" : months + " months ago";
			}

			long years = days / 365;
			return years == 1 ? "1 year ago" : years + " years ago";
		} catch (DateTimeParseException exception) {
			return isoDateTime;
		}
	}

	private final class ClickpackList extends ObjectSelectionList<ClickpackEntry> {
		private boolean draggingScrollbar;
		private double scrollbarGrabOffset;

		private ClickpackList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (updateScrolling(mouseX, mouseY, button)) {
				draggingScrollbar = true;
				int currentScrollerTop = scrollBarY();
				int currentScrollerBottom = currentScrollerTop + scrollerHeight();
				if (mouseY >= currentScrollerTop && mouseY <= currentScrollerBottom) {
					scrollbarGrabOffset = mouseY - currentScrollerTop;
				} else {
					scrollbarGrabOffset = scrollerHeight() / 2.0D;
					updateScrollbarFromMouse(mouseY);
				}
				ClickpackBrowserScreen.this.setFocused(this);
				ClickpackBrowserScreen.this.setDragging(true);
				setDragging(true);
				return true;
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
			if (draggingScrollbar) {
				updateScrollbarFromMouse(mouseY);
				return true;
			}
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}

		@Override
		public boolean mouseReleased(double mouseX, double mouseY, int button) {
			draggingScrollbar = false;
			onRelease(mouseX, mouseY);
			return super.mouseReleased(mouseX, mouseY, button);
		}

		private void setEntries(List<ClickpackDbEntry> entries) {
			List<ClickpackEntry> rows = entries.stream().map(ClickpackEntry::new).toList();
			replaceEntries(rows);
		}

		private void restoreSelection(@Nullable ClickpackDbEntry entry) {
			if (entry == null) {
				setSelected(null);
				return;
			}
			for (ClickpackEntry row : children()) {
				if (row.entry.name().equals(entry.name())) {
					setSelected(row);
					break;
				}
			}
		}

		@Override
		public int getRowWidth() {
			return width - 10;
		}

		@Override
		protected int scrollBarX() {
			return getX() + Math.max(0, width - 6);
		}

		private void updateScrollbarFromMouse(double mouseY) {
			int scrollerHeight = scrollerHeight();
			int minY = getY();
			int maxY = getBottom() - scrollerHeight;
			double unclampedTop = mouseY - scrollbarGrabOffset;
			double clampedTop = Math.max(minY, Math.min(unclampedTop, maxY));
			double trackHeight = Math.max(1.0D, maxY - minY);
			double progress = (clampedTop - minY) / trackHeight;
			setScrollAmount(progress * maxScrollAmount());
		}
	}

	private final class ClickpackEntry extends ObjectSelectionList.Entry<ClickpackEntry> {
		private final ClickpackDbEntry entry;

		private ClickpackEntry(ClickpackDbEntry entry) {
			this.entry = entry;
		}

		@Override
		public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float a) {
			String prefix;
			if (controller.isKeyboardActivePack(entry.name()) && controller.isMouseActivePack(entry.name())) {
				prefix = "[Keyboard+Mouse] ";
			} else if (controller.isKeyboardActivePack(entry.name())) {
				prefix = "[Keyboard] ";
			} else if (controller.isMouseActivePack(entry.name())) {
				prefix = "[Mouse] ";
			} else if (controller.isInstalled(entry.name())) {
				prefix = "[Installed] ";
			} else {
				prefix = "";
			}
			graphics.drawString(font, Component.literal(prefix + entry.name()), x + 4, y + 1, 0xFFFFFFFF, false);
			graphics.drawString(font, Component.literal(formatSize(entry.size())), x + 4, y + 12, 0xFFB8B8B8, false);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			selectedEntry = entry;
			clickpackList.setSelected(this);
			updateButtons();
			return true;
		}

		@Override
		public Component getNarration() {
			return Component.literal(entry.name());
		}
	}
}
