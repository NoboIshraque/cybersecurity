package com.mirage.ui.shell;

import java.awt.Color;

/**
 * A terminal colour scheme. The blue team gets a cool blue/green console; the red
 * team gets a hot red/amber one. Each theme carries a six-colour palette for
 * per-thread tagging (indexed by {@link ShellModel#colorIndex(String)}).
 */
public final class ShellTheme {

    public final Color background;
    public final Color foreground;
    public final Color info;
    public final Color good;
    public final Color warn;
    public final Color bad;
    public final Color command;
    public final Color bannerBg;
    public final Color bannerFg;
    public final Color[] palette;

    private ShellTheme(Color background, Color foreground, Color info, Color good, Color warn,
                       Color bad, Color command, Color bannerBg, Color bannerFg, Color[] palette) {
        this.background = background;
        this.foreground = foreground;
        this.info = info;
        this.good = good;
        this.warn = warn;
        this.bad = bad;
        this.command = command;
        this.bannerBg = bannerBg;
        this.bannerFg = bannerFg;
        this.palette = palette;
    }

    /** Blue-team Console shell: dark slate, green/cyan accents. */
    public static ShellTheme console() {
        return new ShellTheme(
                new Color(0x0B, 0x12, 0x1A), new Color(0xC7, 0xD3, 0xE0),
                new Color(0x7F, 0x9C, 0xB8), new Color(0x4C, 0xD9, 0x64),
                new Color(0xF2, 0xC4, 0x4C), new Color(0xFF, 0x5C, 0x5C),
                new Color(0xE6, 0xF1, 0xFF), new Color(0x12, 0x3A, 0x5A), new Color(0xDE, 0xF1, 0xFF),
                new Color[]{
                        new Color(0x56, 0xC7, 0xE8), new Color(0x6E, 0xE0, 0x8A),
                        new Color(0xF2, 0xC4, 0x4C), new Color(0xC9, 0x8B, 0xFF),
                        new Color(0xFF, 0xA8, 0x5C), new Color(0x8A, 0xB4, 0xF8)});
    }

    /** Red-team Specter shell: near-black, red/amber accents. */
    public static ShellTheme specter() {
        return new ShellTheme(
                new Color(0x12, 0x0A, 0x0A), new Color(0xE0, 0xC9, 0xC9),
                new Color(0xB8, 0x8F, 0x8F), new Color(0x6E, 0xE0, 0x8A),
                new Color(0xF2, 0xB0, 0x4C), new Color(0xFF, 0x4C, 0x4C),
                new Color(0xFF, 0xE8, 0xDE), new Color(0x5A, 0x12, 0x12), new Color(0xFF, 0xDE, 0xDE),
                new Color[]{
                        new Color(0xFF, 0x6E, 0x6E), new Color(0xFF, 0xA8, 0x5C),
                        new Color(0xF2, 0xD0, 0x4C), new Color(0xFF, 0x8A, 0xC9),
                        new Color(0xE8, 0x7C, 0x56), new Color(0xD8, 0x9A, 0x9A)});
    }
}
