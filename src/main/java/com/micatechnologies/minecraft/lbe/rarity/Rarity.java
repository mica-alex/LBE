package com.micatechnologies.minecraft.lbe.rarity;

/**
 * The four loot-box tiers.
 *
 * <p>Ordinal order is <b>ascending value</b> — {@code COMMON} is 0, {@code LEGENDARY} is 3 — and
 * a great deal of code leans on that: {@link #atLeast(Rarity)}, the percentile bucketing in
 * {@link RarityTable}, and the config's numeric override syntax all treat the ordinal as the tier
 * number. Do not reorder, and do not insert a tier in the middle without also migrating every
 * config file that stores an override as a number.</p>
 *
 * <p>This enum is part of the Minecraft-free {@code rarity} package. It carries a colour code
 * because that is a display <i>datum</i>, not display <i>code</i> — the string is a vanilla
 * formatting code that the client pastes in, and keeping it here means the tier and its colour
 * cannot drift apart.</p>
 */
public enum Rarity {

    /** Bulk materials and the first rung of any progression: cobble, wheat, leather, coal. */
    COMMON("common", "§f", 0xE0E0E0),

    /** Iron-tier gear, redstone components, the entry machines of most tech mods. */
    UNCOMMON("uncommon", "§a", 0x7BE86C),

    /** Diamond-tier gear, enchanted books, mid-progression modded machinery. */
    RARE("rare", "§b", 0x7ADCF0),

    /** The top of a pack's progression: beacons, end-game modded components, dragon eggs. */
    LEGENDARY("legendary", "§6", 0xFFD554);

    /** Lowercase, stable identifier. Used in registry names, lang keys, config keys and commands. */
    private final String id;

    /** Vanilla formatting code used when this tier's name is shown to a player. */
    private final String colourCode;

    /** Packed {@code 0xRRGGBB} for this tier. */
    private final int rgb;

    Rarity(String id, String colourCode, int rgb) {
        this.id = id;
        this.colourCode = colourCode;
        this.rgb = rgb;
    }

    public String id() {
        return id;
    }

    public String colourCode() {
        return colourCode;
    }

    /**
     * The tier's colour as packed {@code 0xRRGGBB}, for anything that tints or draws rather than
     * printing text.
     *
     * <p>Here rather than in a client class for the same reason {@link #colourCode()} is: it is a
     * display <i>datum</i>, not display <i>code</i>, and keeping the two together is what stops the
     * chat colour and the rendered colour drifting apart. It carries no Minecraft types, so the
     * package stays testable.</p>
     *
     * <p>These values match the palette in {@code tools/gen_box_textures.py}. Change one, change
     * both, or a box's gem will disagree with the glint hovering over it.</p>
     */
    public int rgb() {
        return rgb;
    }

    /** Red channel, {@code 0}–{@code 1}, for {@code GlStateManager.color}. */
    public float red() {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    /** Green channel, {@code 0}–{@code 1}. */
    public float green() {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    /** Blue channel, {@code 0}–{@code 1}. */
    public float blue() {
        return (rgb & 0xFF) / 255.0F;
    }

    /** {@code true} if this tier is at least as valuable as {@code other}. */
    public boolean atLeast(Rarity other) {
        return ordinal() >= other.ordinal();
    }

    /**
     * Look up a tier by its {@link #id()}, case-insensitively, or by its ordinal as a decimal
     * string ({@code "0"}–{@code "3"}).
     *
     * <p>Both forms are accepted because the config's override table is hand-edited by pack authors:
     * {@code minecraft:diamond=rare} and {@code minecraft:diamond=2} should mean the same thing, and
     * a player who guesses either one should not have their edit silently ignored.</p>
     *
     * @return the tier, or {@code null} if {@code text} names none
     */
    public static Rarity byId(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        for (Rarity rarity : values()) {
            if (rarity.id.equalsIgnoreCase(trimmed)) {
                return rarity;
            }
        }
        // Numeric form. Parsed rather than indexed so "10" is rejected instead of throwing.
        try {
            int ordinal = Integer.parseInt(trimmed);
            if (ordinal >= 0 && ordinal < values().length) {
                return values()[ordinal];
            }
        }
        catch (NumberFormatException ignored) {
            // Not a number and not a name — the caller decides how loudly to complain.
        }
        return null;
    }

    /** The lowest tier. Kept as a method so callers never write {@code values()[0]}. */
    public static Rarity lowest() {
        return COMMON;
    }

    /** The highest tier. */
    public static Rarity highest() {
        return values()[values().length - 1];
    }
}
