package cz.autobuilder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Seznam materiálů potřebných na načtenou stavbu. */
public class MaterialsScreen extends Screen {
    private static final int PER_PAGE = 11;
    private static int page = 0;

    private final Screen parent;
    private List<String> lines = new ArrayList<>();

    public MaterialsScreen(Screen parent) {
        super(Text.literal("Seznam materiálu"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        lines = ModuleManager.SCHEMATIC.materialLines(client);
        int maxPage = Math.max(0, (lines.size() - 1) / PER_PAGE);
        if (page > maxPage) page = maxPage;

        int cx = width / 2;
        int y = height - 40;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { if (page > 0) { page--; clearAndInit(); } })
                .dimensions(cx - 160, y, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { if (page < maxPage) { page++; clearAndInit(); } })
                .dimensions(cx - 125, y, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Kopírovat").formatted(Formatting.LIGHT_PURPLE), b -> {
            client.keyboard.setClipboard(String.join("\n", lines));
        }).dimensions(cx - 85, y, 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zpět"), b -> close())
                .dimensions(cx + 5, y, 80, 20).build());
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        Theme.paint(context, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, Theme.TITLE);
        if (lines.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Nejdřív vyber soubor ve Schematic Builderu."), width / 2, 50, Theme.STATUS);
            return;
        }
        int start = page * PER_PAGE;
        int y = 34;
        for (int i = start; i < Math.min(lines.size(), start + PER_PAGE); i++) {
            context.drawTextWithShadow(textRenderer, Text.literal(lines.get(i)), width / 2 - 200, y, Theme.TEXT);
            y += 14;
        }
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("strana " + (page + 1) + " z " + (Math.max(0, (lines.size() - 1) / PER_PAGE) + 1)),
                width / 2, height - 54, Theme.SUBTITLE);
    }
}
