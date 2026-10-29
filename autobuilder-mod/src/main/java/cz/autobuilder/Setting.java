package cz.autobuilder;

import java.util.Locale;

/** Jedno nastavení modulu: číslo, zap/vyp nebo výběr z možností. */
public class Setting {
    public enum Type { NUMBER, BOOL, MODE }

    public final String name;
    public final Type type;
    public double value, min, max, step;
    public boolean on;
    public String[] options = new String[0];
    public int index;

    private Setting(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public static Setting number(String name, double value, double min, double max, double step) {
        Setting s = new Setting(name, Type.NUMBER);
        s.value = value; s.min = min; s.max = max; s.step = step;
        return s;
    }

    public static Setting bool(String name, boolean on) {
        Setting s = new Setting(name, Type.BOOL);
        s.on = on;
        return s;
    }

    public static Setting mode(String name, String... options) {
        Setting s = new Setting(name, Type.MODE);
        s.options = options;
        return s;
    }

    public void inc() {
        if (type == Type.NUMBER) value = Math.min(max, value + step);
        else if (type == Type.BOOL) on = !on;
        else index = (index + 1) % options.length;
    }

    public void dec() {
        if (type == Type.NUMBER) value = Math.max(min, value - step);
        else if (type == Type.BOOL) on = !on;
        else index = (index + options.length - 1) % options.length;
    }

    public String valueText() {
        if (type == Type.NUMBER) {
            return step >= 1 ? String.valueOf((int) Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
        }
        if (type == Type.BOOL) return on ? "Zap" : "Vyp";
        return options[index];
    }

    public String display() {
        return name + ": " + valueText();
    }
}
