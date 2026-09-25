package com.vandorlabs.client;

import com.vandorlabs.tiles.ScreenHousingTextures;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

/** Shared scrollable housing finish list for programmable screen menus. */
final class HousingTextureList {
    private static final int ROW_HEIGHT = 12;
    private static final int ROWS = 8;
    private final int x, y, width;
    private int selected;
    private int scroll;
    private boolean dragging;
    private int dragOffset;

    HousingTextureList(int x, int y, int width, int selected) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.selected = ScreenHousingTextures.clamp(selected);
        scroll = Math.min(maxScroll(), Math.max(0, this.selected - ROWS / 2));
    }

    int selected() { return selected; }
    private int height() { return ROW_HEIGHT * ROWS; }
    private int maxScroll() { return Math.max(0, ScreenHousingTextures.IDS.length - ROWS); }
    private int thumbHeight() {
        return Math.max(8, height() * ROWS / ScreenHousingTextures.IDS.length);
    }
    private int thumbY() {
        return y + (height() - thumbHeight()) * scroll / Math.max(1, maxScroll());
    }

    boolean click(int mouseX, int mouseY, int button) {
        if (button != 0 || mouseY < y || mouseY >= y + height()) return false;
        if (mouseX >= x + width && mouseX < x + width + 7 && maxScroll() > 0) {
            dragOffset = mouseY >= thumbY() && mouseY < thumbY() + thumbHeight()
                    ? mouseY - thumbY() : thumbHeight() / 2;
            dragging = true;
            drag(mouseY);
            return true;
        }
        if (mouseX < x || mouseX >= x + width) return false;
        int index = scroll + (mouseY - y) / ROW_HEIGHT;
        if (index < ScreenHousingTextures.IDS.length) selected = index;
        return true;
    }

    boolean drag(int mouseY) {
        if (!dragging) return false;
        scroll = GuiProgrammableWall.scrollForDrag(mouseY, y, height(),
                thumbHeight(), maxScroll(), dragOffset);
        return true;
    }

    void release() { dragging = false; }

    boolean wheel(int mouseX, int mouseY, int delta) {
        if (delta == 0 || mouseX < x || mouseX >= x + width + 7
                || mouseY < y || mouseY >= y + height()) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll + (delta > 0 ? -1 : 1)));
        return true;
    }

    void draw(FontRenderer font, int mouseX, int mouseY) {
        Gui.drawRect(x - 1, y - 1, x + width + 8, y + height() + 1, 0xFF000000);
        Gui.drawRect(x, y, x + width, y + height(), 0xFF0A0A0C);
        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= ScreenHousingTextures.IDS.length) break;
            int yy = y + row * ROW_HEIGHT;
            boolean hover = mouseX >= x && mouseX < x + width
                    && mouseY >= yy && mouseY < yy + ROW_HEIGHT;
            if (index == selected || hover) Gui.drawRect(x, yy, x + width,
                    yy + ROW_HEIGHT, index == selected ? 0xFF2A4A6A : 0xFF1A1A20);
            String name = I18n.format("tile.vandorlabs."
                    + ScreenHousingTextures.IDS[index] + ".name");
            font.drawStringWithShadow(font.trimStringToWidth(name, width - 8),
                    x + 3, yy + 2, index == selected ? 0xFFFFE08A : 0xFFD8D8D8);
        }
        if (maxScroll() > 0) {
            Gui.drawRect(x + width, y, x + width + 7, y + height(), 0xFF303038);
            Gui.drawRect(x + width, thumbY(), x + width + 7,
                    thumbY() + thumbHeight(), 0xFF808090);
        }
    }
}
