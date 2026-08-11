package com.micatechnologies.minecraft.lbe.casino;

import java.util.Locale;

/**
 * Every game the casino offers, and the one place a new one is added.
 *
 * <p>A game is a registry name, a cabinet, a screen and some pure logic. Everything generic — the
 * block, the tile entity, the two packets, the bet handling, the settlement — is shared, so adding a
 * game means writing its rules, adding a constant here, and drawing it. Nothing that moves money is
 * touched.
 *
 * <p>The order is stable and the ordinals travel on the wire, so <b>append only</b>: reordering
 * these would point every placed machine in every world at a different game.
 */
public enum CasinoGame {

    SLOTS("slot_machine", "Slot Machine", Cabinet.TALL),
    COIN_FLIP("coin_flip_table", "Coin Flip", Cabinet.TABLE),
    WAR("war_table", "Casino War", Cabinet.TABLE),
    HIGH_LOW("high_low_machine", "High-Low", Cabinet.TALL),
    ROULETTE("roulette_table", "Roulette", Cabinet.TABLE),
    PLINKO("plinko_machine", "Plinko", Cabinet.TALL),
    KENO("keno_machine", "Keno", Cabinet.TALL),
    BACCARAT("baccarat_table", "Baccarat", Cabinet.TABLE);

    /** What shape of block the game sits in. */
    public enum Cabinet {
        /** Two blocks tall, like the slot machine: an upright cabinet with a screen at head height. */
        TALL,
        /** One block, waist height: a table you stand at. */
        TABLE
    }

    private final String registryName;
    private final String displayName;
    private final Cabinet cabinet;

    CasinoGame(String registryName, String displayName, Cabinet cabinet) {
        this.registryName = registryName;
        this.displayName = displayName;
        this.cabinet = cabinet;
    }

    /** Block registry name, e.g. {@code slot_machine}. */
    public String registryName() {
        return registryName;
    }

    /** Name shown on the screen and in the creative tab. */
    public String displayName() {
        return displayName;
    }

    public Cabinet cabinet() {
        return cabinet;
    }

    /** True when this game occupies two block positions. */
    public boolean isTall() {
        return cabinet == Cabinet.TALL;
    }

    /** Texture prefix for this game's cabinet, e.g. {@code blocks/slot_machine}. */
    public String texturePrefix() {
        return "blocks/" + registryName;
    }

    /** Lang key for the block, matching the translation key the block sets. */
    public String translationKey() {
        return "lbe." + registryName;
    }

    /**
     * The game at {@code ordinal}, or null.
     *
     * <p>Used when reading a packet, so it must tolerate a number that is not a game rather than
     * throwing on the network thread.
     */
    public static CasinoGame byOrdinal(int ordinal) {
        CasinoGame[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    /** Matches a registry name, case-insensitively. Null when nothing matches. */
    public static CasinoGame byRegistryName(String name) {
        if (name == null) {
            return null;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (CasinoGame game : values()) {
            if (game.registryName.equals(wanted)) {
                return game;
            }
        }
        return null;
    }
}
