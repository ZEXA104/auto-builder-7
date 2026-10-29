package cz.autobuilder;

import net.minecraft.client.gui.DrawContext;

/** Fialový vzhled menu. */
public final class Theme {
    public static final int TITLE = 0xFFD9A0FF;
    public static final int SUBTITLE = 0xFFB890E0;
    public static final int STATUS = 0xFFFF9CFF;
    public static final int TEXT = 0xFFEAD8FF;

    private Theme() {}

    public static void paint(DrawContext ctx, int w, int h) {
        ctx.fill(0, 0, w, h, 0xB01E0838);
        int l = w / 2 - 240, r = w / 2 + 240;
        ctx.fill(l, 8, r, h - 8, 0xD0140626);
        int c = 0xFFB347FF;
        ctx.fill(l, 8, r, 10, c);
        ctx.fill(l, h - 10, r, h - 8, c);
        ctx.fill(l, 8, l + 2, h - 8, c);
        ctx.fill(r - 2, 8, r, h - 8, c);
    }
}
