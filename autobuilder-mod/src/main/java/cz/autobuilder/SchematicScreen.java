package cz.autobuilder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Nastavení a výběr souboru pro Schematic Builder. */
public class SchematicScreen extends Screen {
    private static final int PER_PAGE = 6;
    private static int page = 0;

    private final Screen parent;
    private final SchematicModule m = ModuleManager.SCHEMATIC;
    private List<Path> files = new ArrayList<>();

    public SchematicScreen(Screen parent) {
        super(Text.literal("Schematic Builder"));
        this.parent = parent;
    }

    private Path folder() {
        return client.runDirectory.toPath().resolve("schematics");
    }

    private void refreshFiles() {
        files = new ArrayList<>();
        try {
            Files.createDirectories(folder());
            try (Stream<Path> st = Files.list(folder())) {
                st.filter(p -> {
                            String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".litematic") || n.endsWith(".schem");
                        })
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                        .forEach(files::add);
            }
        } catch (IOException e) {
            m.status = "Nelze číst složku: " + e.getMessage();
        }
    }

    @Override
    protected void init() {
        refreshFiles();
        int maxPage = Math.max(0, (files.size() - 1) / PER_PAGE);
        if (page > maxPage) page = maxPage;

        int cx = width / 2;
        int top = 45;
        int lx = cx - 225;
        int rx = cx + 5;

        // levý sloupec: soubory
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= files.size()) break;
            Path p = files.get(idx);
            String name = p.getFileName().toString();
            boolean sel = name.equals(m.fileName);
            addDrawableChild(ButtonWidget.builder(Text.literal((sel ? "> " : "") + name), b -> select(p))
                    .dimensions(lx, top + i * 22, 220, 20).build());
        }
        int navY = top + PER_PAGE * 22 + 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { if (page > 0) { page--; clearAndInit(); } })
                .dimensions(lx, navY, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { if (page < maxPage) { page++; clearAndInit(); } })
                .dimensions(lx + 190, navY, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Otevřít složku"), b -> Util.getOperatingSystem().open(folder().toUri()))
                .dimensions(lx + 35, navY, 150, 20).build());

        // pravý sloupec: nastavení
        int y = top;
        for (Setting s : m.settings) {
            if (s.type == Setting.Type.NUMBER) {
                addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> { s.dec(); clearAndInit(); })
                        .dimensions(rx, y, 20, 20).build());
                ButtonWidget label = ButtonWidget.builder(Text.literal(s.display()), b -> {})
                        .dimensions(rx + 22, y, 176, 20).build();
                label.active = false;
                addDrawableChild(label);
                addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> { s.inc(); clearAndInit(); })
                        .dimensions(rx + 200, y, 20, 20).build());
            } else {
                addDrawableChild(ButtonWidget.builder(Text.literal(s.display()), b -> { s.inc(); clearAndInit(); })
                        .dimensions(rx, y, 220, 20).build());
            }
            y += 24;
        }

        y += 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("Postavit zde").formatted(Formatting.LIGHT_PURPLE), b -> {
            m.startBuild(client);
            if (m.isBuilding()) client.setScreen(null);
            else clearAndInit();
        }).dimensions(rx, y, 108, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zastavit"), b -> { m.stopBuild(); clearAndInit(); })
                .dimensions(rx + 112, y, 108, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Seznam materiálu").formatted(Formatting.LIGHT_PURPLE),
                        b -> client.setScreen(new MaterialsScreen(this)))
                .dimensions(rx, y + 24, 220, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Zpět"), b -> close())
                .dimensions(cx - 50, height - 40, 100, 20).build());
    }

    private void select(Path p) {
        try {
            m.load(p);
        } catch (Exception e) {
            m.schematic = null;
            m.fileName = null;
            m.status = "Chyba při čtení: " + e.getMessage();
        }
        clearAndInit();
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
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(".minecraft/schematics  (.litematic, .schem)  strana " + (page + 1)), width / 2, 28, Theme.SUBTITLE);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(m.status), width / 2, height - 62, Theme.STATUS);
    }
}
