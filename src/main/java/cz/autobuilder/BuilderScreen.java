package cz.autobuilder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class BuilderScreen extends Screen {
    private static final int PER_PAGE = 6;

    private static Schematic loaded;
    private static String loadedName = null;
    private static String status = "Vyber soubor .litematic ze složky schematics.";
    private static int page = 0;

    private List<Path> files = new ArrayList<>();

    public BuilderScreen() {
        super(Text.literal("Auto Builder"));
    }

    private Path folder() {
        return client.runDirectory.toPath().resolve("schematics");
    }

    private void refreshFiles() {
        files = new ArrayList<>();
        try {
            Files.createDirectories(folder());
            try (Stream<Path> st = Files.list(folder())) {
                st.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                        .forEach(files::add);
            }
        } catch (IOException e) {
            status = "Nelze číst složku: " + e.getMessage();
        }
    }

    @Override
    protected void init() {
        refreshFiles();
        int maxPage = Math.max(0, (files.size() - 1) / PER_PAGE);
        if (page > maxPage) page = maxPage;

        int cx = width / 2;
        int top = 45;

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= files.size()) break;
            Path p = files.get(idx);
            String name = p.getFileName().toString();
            boolean sel = name.equals(loadedName);
            addDrawableChild(ButtonWidget.builder(Text.literal((sel ? "> " : "") + name), b -> select(p))
                    .dimensions(cx - 130, top + i * 22, 260, 20).build());
        }

        int navY = top + PER_PAGE * 22 + 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { if (page > 0) { page--; clearAndInit(); } })
                .dimensions(cx - 130, navY, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { if (page < maxPage) { page++; clearAndInit(); } })
                .dimensions(cx + 90, navY, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Otevřít složku"), b -> Util.getOperatingSystem().open(folder().toUri()))
                .dimensions(cx - 85, navY, 170, 20).build());

        int btnY = navY + 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("Postavit zde"), b -> {
            if (loaded == null || loaded.blocks.isEmpty()) {
                status = "Nejdřív vyber platný soubor.";
                return;
            }
            if (client.player != null) {
                Builder.start(loaded, client.player.getBlockPos());
                close();
            }
        }).dimensions(cx - 130, btnY, 125, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zastavit stavění"), b -> { Builder.stop(); status = "Stavění zastaveno."; })
                .dimensions(cx + 5, btnY, 125, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zavřít"), b -> close())
                .dimensions(cx - 50, btnY + 26, 100, 20).build());
    }

    private void select(Path p) {
        try {
            Schematic s = Schematic.load(p);
            loaded = s;
            loadedName = p.getFileName().toString();
            if (s.blocks.isEmpty()) {
                status = "Soubor je prázdný nebo ho nelze přečíst.";
            } else {
                status = "Načteno: " + s.blocks.size() + " bloků, rozměr " + s.sizeX + "x" + s.sizeY + "x" + s.sizeZ
                        + ". Stavba začne na tvé pozici.";
            }
        } catch (Exception e) {
            loaded = null;
            loadedName = null;
            status = "Chyba při čtení: " + e.getMessage();
        }
        clearAndInit();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Složka: .minecraft/schematics  (strana " + (page + 1) + ")"), width / 2, 28, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height - 24, 0xFFFFFF55);
    }
}
