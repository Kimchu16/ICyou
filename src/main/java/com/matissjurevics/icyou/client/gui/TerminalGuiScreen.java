package com.matissjurevics.icyou.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.matissjurevics.icyou.client.ClientDeviceCache;
import com.matissjurevics.icyou.network.DeviceActionC2SPayload;
import com.matissjurevics.icyou.network.DeviceSnapshotS2CPayload;
import com.matissjurevics.icyou.network.DeviceSubscribeC2SPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Terminal management GUI: three stacked sections (cameras / screens /
 * wireless), rename + remove per device, and drag & drop a camera onto a
 * screen to assign it.
 */
public class TerminalGuiScreen extends Screen {

    private record Rect(int x, int y, int w, int h) {
        boolean contains(int px, int py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    private final BlockPos terminal;

    private int camPage, scrPage, wrlPage;
    private int dragCamId = -1;
    private int dragX, dragY;

    // inline rename state
    private int editingType = -1;
    private int editingId = -1;
    private TextFieldWidget renameField;

    // hitboxes rebuilt each render
    private final List<Rect> camRowRects = new ArrayList<>();
    private final List<Rect> scrRowRects = new ArrayList<>();
    private final List<Rect> wrlRowRects = new ArrayList<>();
    private final List<Rect> camRenameRects = new ArrayList<>();
    private final List<Rect> camRemoveRects = new ArrayList<>();
    private final List<Rect> scrRenameRects = new ArrayList<>();
    private final List<Rect> scrRemoveRects = new ArrayList<>();
    private final List<Rect> wrlRenameRects = new ArrayList<>();
    private final List<Rect> wrlRemoveRects = new ArrayList<>();
    private final List<Rect> camPageLeft = new ArrayList<>();
    private final List<Rect> camPageRight = new ArrayList<>();
    private final List<Rect> scrPageLeft = new ArrayList<>();
    private final List<Rect> scrPageRight = new ArrayList<>();
    private final List<Rect> wrlPageLeft = new ArrayList<>();
    private final List<Rect> wrlPageRight = new ArrayList<>();

    private static final int ROWS = 4;
    private static final int ROW_H = 14;
    private static final int PANEL_W = 280;

    // palette
    private static final int BG           = 0xF216161A;
    private static final int TITLE_BG     = 0xFF202027;
    private static final int PANEL_BORDER = 0xFF33333E;
    private static final int ACCENT       = 0xFF30FF60;
    private static final int ROW_BG       = 0x14000000;
    private static final int ROW_HOVER    = 0x24FFFFFF;
    private static final int SEP          = 0x18FFFFFF;
    private static final int TEXT_MAIN    = 0xFFE8E8F0;
    private static final int TEXT_DIM     = 0xFF8A8A94;
    private static final int TEXT_OFF     = 0xFF808080;
    private static final int[] SECTION_ACCENT = { 0xFF4FC8FF, 0xFF58E07A, 0xFFFFC05A };

    public TerminalGuiScreen(DeviceSnapshotS2CPayload initial) {
        super(Text.literal("ICyou Terminal"));
        this.terminal = initial.terminal();
    }

    @Override
    protected void init() {
        int cx = (width - PANEL_W) / 2;
        renameField = new TextFieldWidget(textRenderer, cx + 4, 0, 120, 12, Text.literal(""));
        renameField.setMaxLength(24);
        renameField.setVisible(false);
        addDrawableChild(renameField);
    }

    @Override
    public void close() {
        ClientPlayNetworking.send(new DeviceSubscribeC2SPayload(terminal, false));
        super.close();
    }

    private int panelX() {
        return (width - PANEL_W) / 2;
    }

    private int panelHeight(DeviceSnapshotS2CPayload snap) {
        int h = 14; // title bar
        h += sectionH(snap.cameras().size());
        h += sectionH(snap.screens().size());
        h += sectionH(snap.wireless().size());
        return h + 2; // bottom border
    }

    private int sectionH(int total) {
        return ROW_H + Math.min(ROWS, Math.max(0, total)) * ROW_H + 6;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        DeviceSnapshotS2CPayload snap = ClientDeviceCache.get();
        if (snap == null) {
            return;
        }

        int ph = panelHeight(snap);
        int x0 = panelX();
        int y0 = (height - ph) / 2;

        // panel background + rounded-corner border
        context.fill(x0, y0, x0 + PANEL_W, y0 + ph, BG);
        context.fill(x0, y0 + ph - 1, x0 + PANEL_W, y0 + ph, PANEL_BORDER);
        context.fill(x0 + PANEL_W - 1, y0, x0 + PANEL_W, y0 + ph, PANEL_BORDER);

        // title bar
        context.fill(x0, y0, x0 + PANEL_W, y0 + 14, TITLE_BG);
        context.drawTextWithShadow(textRenderer, Text.literal("ICyou Terminal"),
                x0 + 8, y0 + 4, ACCENT);
        String slug = snap.slug();
        if (slug != null && !slug.isEmpty()) {
            int sw = textRenderer.getWidth(slug);
            context.drawTextWithShadow(textRenderer, Text.literal(slug),
                    x0 + PANEL_W - 8 - sw, y0 + 4, 0xFF7FB0FF);
        }

        // left accent spine (on top of the title bar)
        context.fill(x0, y0, x0 + 3, y0 + ph, ACCENT);
        // title underline
        context.fill(x0 + 3, y0 + 13, x0 + PANEL_W, y0 + 14, 0x22FFFFFF);

        clearRects();
        int y = y0 + 14;

        y = renderSection(context, "CAMERAS", snap.cameras().size(), camPage, y, x0,
                snap, 0, mouseX, mouseY);
        y = renderSection(context, "SCREENS", snap.screens().size(), scrPage, y, x0,
                snap, 1, mouseX, mouseY);
        y = renderSection(context, "WIRELESS", snap.wireless().size(), wrlPage, y, x0,
                snap, 2, mouseX, mouseY);

        // dragged camera label
        if (dragCamId >= 0) {
            String label = snap.cameras().stream().filter(c -> c.id() == dragCamId)
                    .map(c -> c.name()).findFirst().orElse("?");
            int w = textRenderer.getWidth(label);
            context.fill(dragX - 2, dragY - 2, dragX + w + 4, dragY + 10, 0xE0202027);
            context.fill(dragX - 2, dragY - 2, dragX + w + 4, dragY - 1, ACCENT);
            context.drawTextWithShadow(textRenderer, Text.literal(label), dragX, dragY, 0xFFE8E8F0);
        }

        if (editingType >= 0 && renameField != null && renameField.isVisible()) {
            renameField.render(context, mouseX, mouseY, delta);
        }
    }

    private void clearRects() {
        camRowRects.clear(); scrRowRects.clear(); wrlRowRects.clear();
        camRenameRects.clear(); camRemoveRects.clear();
        scrRenameRects.clear(); scrRemoveRects.clear();
        wrlRenameRects.clear(); wrlRemoveRects.clear();
        camPageLeft.clear(); camPageRight.clear(); scrPageLeft.clear(); scrPageRight.clear();
        wrlPageLeft.clear(); wrlPageRight.clear();
    }

    private int renderSection(DrawContext context, String title, int total, int page,
                              int y, int x0, DeviceSnapshotS2CPayload snap, int type,
                              int mouseX, int mouseY) {
        int accent = SECTION_ACCENT[type];
        int headerX = x0 + 8;
        context.drawTextWithShadow(textRenderer, Text.literal(title), headerX, y, accent);
        context.drawTextWithShadow(textRenderer, Text.literal(String.valueOf(total)),
                headerX + textRenderer.getWidth(title) + 4, y, TEXT_DIM);

        // paging buttons
        int right = x0 + PANEL_W - 14;
        Rect leftBtn = new Rect(right - 18, y - 2, 8, 10);
        Rect rightBtn = new Rect(right - 8, y - 2, 8, 10);
        boolean hl = leftBtn.contains(mouseX, mouseY);
        boolean hr = rightBtn.contains(mouseX, mouseY);
        context.fill(leftBtn.x, leftBtn.y, leftBtn.x + leftBtn.w, leftBtn.y + leftBtn.h,
                hl ? ROW_HOVER : ROW_BG);
        context.fill(rightBtn.x, rightBtn.y, rightBtn.x + rightBtn.w, rightBtn.y + rightBtn.h,
                hr ? ROW_HOVER : ROW_BG);
        context.drawTextWithShadow(textRenderer, Text.literal("<"), leftBtn.x, y - 2, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal(">"), rightBtn.x, y - 2, 0xFFFFFFFF);
        storePageRects(type, leftBtn, rightBtn);
        // section underline
        context.fill(headerX, y + ROW_H - 4, x0 + PANEL_W - 8, y + ROW_H - 3, SEP);
        y += ROW_H;

        int maxPage = Math.max(0, (total + ROWS - 1) / ROWS - 1);
        int page2 = Math.min(page, maxPage);
        int start = page2 * ROWS;
        int shown = Math.min(ROWS, Math.max(0, total - start));

        for (int i = 0; i < shown; i++) {
            int idx = start + i;
            String name;
            String extra;
            boolean online = true;
            int id;
            if (type == 0) {
                var c = snap.cameras().get(idx);
                name = c.name(); id = c.id();
                extra = c.online() ? "" : "  [OFFLINE]";
                online = c.online();
            } else if (type == 1) {
                var s = snap.screens().get(idx);
                name = s.name(); id = s.id();
                extra = "  \u2192 " + s.camName() + (s.online() ? "" : " (off)");
                online = s.online();
            } else {
                var w = snap.wireless().get(idx);
                name = w.name(); id = w.id();
                extra = "";
            }

            int rowX = x0 + 8;
            int rowW = PANEL_W - 16;
            Rect rowRect = new Rect(rowX, y - 2, rowW, ROW_H);
            storeRowRect(type, rowRect);

            boolean hov = rowRect.contains(mouseX, mouseY);
            context.fill(rowX, y - 2, rowX + rowW, y + ROW_H - 2, hov ? ROW_HOVER : ROW_BG);
            // status dot
            context.fill(rowX + 2, y + 1, rowX + 6, y + 5,
                    online ? 0xFF3FDF5F : 0xFFE05050);
            int color = online ? TEXT_MAIN : TEXT_OFF;
            context.drawTextWithShadow(textRenderer, Text.literal(name + extra),
                    rowX + 10, y, color);

            Rect rename = new Rect(x0 + PANEL_W - 46, y - 2, 16, 12);
            Rect remove = new Rect(x0 + PANEL_W - 28, y - 2, 16, 12);
            boolean hr2 = rename.contains(mouseX, mouseY);
            boolean hx2 = remove.contains(mouseX, mouseY);
            context.fill(rename.x, rename.y, rename.x + rename.w, rename.y + rename.h,
                    hr2 ? 0xFF4A6FD0 : 0xFF3A5FBF);
            context.fill(remove.x, remove.y, remove.x + remove.w, remove.y + remove.h,
                    hx2 ? 0xFFD05656 : 0xFFB03F3F);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("R"),
                    rename.x + rename.w / 2, rename.y + 3, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("X"),
                    remove.x + remove.w / 2, remove.y + 3, 0xFFFFFFFF);
            storeBtnRects(type, rename, remove);

            // park the rename widget over the row being edited
            if (type == editingType && id == editingId && renameField != null) {
                renameField.setX(rowX);
                renameField.setY(y - 2);
                renameField.setWidth(rowW);
            }

            y += ROW_H;
        }
        return y + 6;
    }

    private void storePageRects(int type, Rect left, Rect right) {
        switch (type) {
            case 0 -> { camPageLeft.add(left); camPageRight.add(right); }
            case 1 -> { scrPageLeft.add(left); scrPageRight.add(right); }
            case 2 -> { wrlPageLeft.add(left); wrlPageRight.add(right); }
        }
    }

    private void storeRowRect(int type, Rect r) {
        switch (type) {
            case 0 -> camRowRects.add(r);
            case 1 -> scrRowRects.add(r);
            case 2 -> wrlRowRects.add(r);
        }
    }

    private void storeBtnRects(int type, Rect rename, Rect remove) {
        switch (type) {
            case 0 -> { camRenameRects.add(rename); camRemoveRects.add(remove); }
            case 1 -> { scrRenameRects.add(rename); scrRemoveRects.add(remove); }
            case 2 -> { wrlRenameRects.add(rename); wrlRemoveRects.add(remove); }
        }
    }

    // --- interaction ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editingType >= 0 && renameField != null && renameField.isVisible()) {
            if (renameField.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            commitRename();
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (button == 0) {
            // rename / remove buttons
            if (hit(camRenameRects, mx, my) >= 0) { startRename(0, idxFrom(camRenameRects, mx, my)); return true; }
            if (hit(camRemoveRects, mx, my) >= 0) { removeDevice(0, idxFrom(camRemoveRects, mx, my)); return true; }
            if (hit(scrRenameRects, mx, my) >= 0) { startRename(1, idxFrom(scrRenameRects, mx, my)); return true; }
            if (hit(scrRemoveRects, mx, my) >= 0) { removeDevice(1, idxFrom(scrRemoveRects, mx, my)); return true; }
            if (hit(wrlRenameRects, mx, my) >= 0) { startRename(2, idxFrom(wrlRenameRects, mx, my)); return true; }
            if (hit(wrlRemoveRects, mx, my) >= 0) { removeDevice(2, idxFrom(wrlRemoveRects, mx, my)); return true; }

            // paging
            if (hit(camPageLeft, mx, my) >= 0) { camPage = Math.max(0, camPage - 1); return true; }
            if (hit(camPageRight, mx, my) >= 0) { camPage++; return true; }
            if (hit(scrPageLeft, mx, my) >= 0) { scrPage = Math.max(0, scrPage - 1); return true; }
            if (hit(scrPageRight, mx, my) >= 0) { scrPage++; return true; }
            if (hit(wrlPageLeft, mx, my) >= 0) { wrlPage = Math.max(0, wrlPage - 1); return true; }
            if (hit(wrlPageRight, mx, my) >= 0) { wrlPage++; return true; }

            // start drag on a camera row
            int ci = hit(camRowRects, mx, my);
            if (ci >= 0 && ClientDeviceCache.get() != null) {
                var cams = ClientDeviceCache.get().cameras();
                int global = camPage * ROWS + ci;
                if (global < cams.size()) {
                    dragCamId = cams.get(global).id();
                    dragX = mx;
                    dragY = my;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragCamId >= 0) {
            int si = hit(scrRowRects, (int) mouseX, (int) mouseY);
            if (si >= 0 && ClientDeviceCache.get() != null) {
                var screens = ClientDeviceCache.get().screens();
                int global = scrPage * ROWS + si;
                if (global < screens.size()) {
                    ClientPlayNetworking.send(new DeviceActionC2SPayload(terminal,
                            DeviceActionC2SPayload.ACTION_ASSIGN,
                            DeviceActionC2SPayload.TYPE_SCREEN,
                            screens.get(global).id(), dragCamId, ""));
                }
            }
            dragCamId = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        dragX = (int) mouseX;
        dragY = (int) mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingType >= 0 && renameField != null && renameField.isVisible()) {
            return renameField.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingType >= 0 && renameField != null && renameField.isVisible()) {
            if (keyCode == 257 || keyCode == 335) { // Enter / numpad enter
                commitRename();
                return true;
            }
            if (keyCode == 256) { // Esc
                cancelRename();
                return true;
            }
            return renameField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static int hit(List<Rect> rects, int mx, int my) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).contains(mx, my)) {
                return i;
            }
        }
        return -1;
    }

    private static int idxFrom(List<Rect> rects, int mx, int my) {
        return hit(rects, mx, my);
    }

    private void startRename(int type, int rowIdx) {
        var snap = ClientDeviceCache.get();
        if (snap == null) {
            return;
        }
        String name = null;
        int id = -1;
        if (type == 0) {
            int g = camPage * ROWS + rowIdx;
            if (g < snap.cameras().size()) { name = snap.cameras().get(g).name(); id = snap.cameras().get(g).id(); }
        } else if (type == 1) {
            int g = scrPage * ROWS + rowIdx;
            if (g < snap.screens().size()) { name = snap.screens().get(g).name(); id = snap.screens().get(g).id(); }
        } else {
            int g = wrlPage * ROWS + rowIdx;
            if (g < snap.wireless().size()) { name = snap.wireless().get(g).name(); id = snap.wireless().get(g).id(); }
        }
        if (id < 0) {
            return;
        }
        editingType = type;
        editingId = id;
        renameField.setText(name);
        renameField.setVisible(true);
        renameField.setFocused(true);
        renameField.setCursorToEnd(false);
    }

    private void commitRename() {
        if (editingType >= 0 && editingId >= 0 && renameField != null) {
            ClientPlayNetworking.send(new DeviceActionC2SPayload(terminal,
                    DeviceActionC2SPayload.ACTION_RENAME, (byte) editingType,
                    editingId, 0, renameField.getText()));
        }
        cancelRename();
    }

    private void cancelRename() {
        editingType = -1;
        editingId = -1;
        if (renameField != null) {
            renameField.setVisible(false);
            renameField.setFocused(false);
        }
    }

    private void removeDevice(int type, int rowIdx) {
        var snap = ClientDeviceCache.get();
        if (snap == null) {
            return;
        }
        int id = -1;
        if (type == 0) {
            int g = camPage * ROWS + rowIdx;
            if (g < snap.cameras().size()) { id = snap.cameras().get(g).id(); }
        } else if (type == 1) {
            int g = scrPage * ROWS + rowIdx;
            if (g < snap.screens().size()) { id = snap.screens().get(g).id(); }
        } else {
            int g = wrlPage * ROWS + rowIdx;
            if (g < snap.wireless().size()) { id = snap.wireless().get(g).id(); }
        }
        if (id >= 0) {
            ClientPlayNetworking.send(new DeviceActionC2SPayload(terminal,
                    DeviceActionC2SPayload.ACTION_REMOVE, (byte) type, id, 0, ""));
        }
    }
}
