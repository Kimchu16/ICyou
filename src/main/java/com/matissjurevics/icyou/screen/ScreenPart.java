package com.matissjurevics.icyou.screen;

import net.minecraft.util.StringIdentifiable;

/** Position of one block inside a bottom-left-anchored display. */
public enum ScreenPart implements StringIdentifiable {
    BOTTOM_LEFT("bottom_left", 0, 0),
    BOTTOM_CENTER("bottom_center", 1, 0),
    BOTTOM_RIGHT("bottom_right", 2, 0),
    MIDDLE_LEFT("middle_left", 0, 1),
    CENTER("center", 1, 1),
    MIDDLE_RIGHT("middle_right", 2, 1),
    TOP_LEFT("top_left", 0, 2),
    TOP_CENTER("top_center", 1, 2),
    TOP_RIGHT("top_right", 2, 2);

    private final String name;
    private final int column;
    private final int row;

    ScreenPart(String name, int column, int row) {
        this.name = name;
        this.column = column;
        this.row = row;
    }

    public int column() {
        return column;
    }

    public int row() {
        return row;
    }

    public boolean fits(int size) {
        return column < size && row < size;
    }

    public static ScreenPart at(int column, int row) {
        for (ScreenPart part : values()) {
            if (part.column == column && part.row == row) {
                return part;
            }
        }
        throw new IllegalArgumentException("No screen part at " + column + "," + row);
    }

    @Override
    public String asString() {
        return name;
    }
}
