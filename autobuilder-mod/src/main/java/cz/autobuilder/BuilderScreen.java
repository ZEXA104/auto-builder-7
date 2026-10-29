package cz.autobuilder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Hlavní menu klienta (pravý Shift): hledání a seznam modulů. */
public class BuilderScreen extends Screen {
    private static String query = "";

    private TextFieldWidget search;
    private final List<ClickableWidget> rows = new ArrayList<>();

    public BuilderScreen() {
        super(Text.literal("Builder Client"));
    }

    @Override
    protected void init() {
        rows.clear();
        int cx = width / 2;

        search = new TextFieldWidget(textRenderer, cx - 130, 40, 260, 20, Text.literal("Hledat"));
        search.setText(query);
        search.setChangedListener(s -> {
            query = s;
            rebuildRows();
        });
        addDrawableChild(search);

        rebuildRows();

        addDrawableChild(ButtonWidget.builder(Text.literal("Zavřít"), b -> close())
                .dimensions(cx - 50, height - 40, 100, 20).build());
    }

    private void rebuildRows() {
        for (ClickableWidget w : rows) remove(w);
        rows.clear();

        int cx = width / 2;
        int y = 70;
        String q = query.toLowerCase();
        for (Module m : ModuleManager.ALL) {
            if (!q.isEmpty() && !m.name.toLowerCase().contains(q) && !m.description.toLowerCase().contains(q)) continue;

            ButtonWidget toggle = ButtonWidget.builder(
                    Text.literal((m.enabled ? "[ZAP]  " : "[VYP]  ") + m.name).formatted(Formatting.LIGHT_PURPLE),
                    b -> {
                        m.toggle();
                        clearAndInit();
                    }).dimensions(cx - 130, y, 195, 20).build();
            ButtonWidget gear = ButtonWidget.builder(
                    Text.literal("Nastavení").formatted(Formatting.LIGHT_PURPLE),
                    b -> client.setScreen(m.createScreen(this))).dimensions(cx + 70, y, 60, 20).build();

            addDrawableChild(toggle);
            addDrawableChild(gear);
            rows.add(toggle);
            rows.add(gear);
            y += 24;
        }
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
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Pravý Shift = menu  |  klávesy modulů nastavíš v Ovládání"), width / 2, 28, Theme.SUBTITLE);
    }
}
