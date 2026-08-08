package com.micatechnologies.minecraft.lbe.rarity;

/**
 * The one place that builds and takes apart item keys.
 *
 * <p>An LBE item key is <b>{@code modid:name#meta}</b>. The metadata suffix is not optional
 * internally: in 1.12.2 an enormous amount of content is distinguished only by metadata (every dye,
 * every wool colour, and most mods' "variants live in the damage value" blocks), so a key without
 * one collapses sixteen different items into a single rarity — which is exactly the bug that makes
 * a loot table hand out black wool when it promised a diamond.</p>
 *
 * <p>Config files may omit it. {@code minecraft:diamond} in the override table means
 * {@code minecraft:diamond#0}, because that is what a person writing it means, and requiring
 * {@code #0} everywhere would make the file unreadable. {@link #normalise(String)} is what turns the
 * written form into the internal one; run every externally-supplied key through it.</p>
 *
 * <p>{@code *} is accepted as a wildcard metadata ({@code minecraft:wool#*}) and matched by
 * {@link #matches(String, String)}. It is deliberately the only wildcard: full glob matching over a
 * modded registry is a performance trap and an ambiguity trap, and the one case that genuinely needs
 * it — "all sixteen colours of this thing" — is covered.</p>
 */
public final class ItemKeys {

    /** Separates the registry name from the metadata. */
    public static final char META_SEPARATOR = '#';

    /** Metadata value meaning "any". */
    public static final String META_WILDCARD = "*";

    private ItemKeys() {
        throw new AssertionError("No instances.");
    }

    /** Build a key from a registry name and metadata. */
    public static String of(String registryName, int meta) {
        return registryName + META_SEPARATOR + meta;
    }

    /** Build a metadata-wildcard key covering every variant of {@code registryName}. */
    public static String wildcard(String registryName) {
        return registryName + META_SEPARATOR + META_WILDCARD;
    }

    /**
     * Canonicalise an externally-written key: trim it, lowercase the registry portion, and append
     * {@code #0} if no metadata was given.
     *
     * <p>The registry portion is lowercased because Forge registry names are lowercase by contract
     * but hand-edited config lines are not. The metadata portion is left alone so {@code *} survives.</p>
     *
     * @return the normalised key, or {@code null} if {@code raw} is null/blank
     */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int hash = trimmed.indexOf(META_SEPARATOR);
        if (hash < 0) {
            return trimmed.toLowerCase(java.util.Locale.ROOT) + META_SEPARATOR + '0';
        }
        String name = trimmed.substring(0, hash).toLowerCase(java.util.Locale.ROOT);
        String meta = trimmed.substring(hash + 1).trim();
        if (meta.isEmpty()) {
            meta = "0";
        }
        return name + META_SEPARATOR + meta;
    }

    /** The registry-name half of a key ({@code "minecraft:wool"}). */
    public static String registryName(String key) {
        int hash = key.indexOf(META_SEPARATOR);
        return hash < 0 ? key : key.substring(0, hash);
    }

    /** The metadata half of a key, or {@code "*"} / {@code "0"}. Never null. */
    public static String metaPart(String key) {
        int hash = key.indexOf(META_SEPARATOR);
        return hash < 0 ? "0" : key.substring(hash + 1);
    }

    /**
     * Numeric metadata, or {@code -1} for the wildcard (and for anything unparseable, which is
     * treated as the wildcard rather than as an error — a config typo should widen a match, not
     * crash a registry pass).
     */
    public static int meta(String key) {
        String part = metaPart(key);
        if (META_WILDCARD.equals(part)) {
            return -1;
        }
        try {
            return Integer.parseInt(part);
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The mod id a key belongs to ({@code "minecraft"}), or {@code ""} if the key has no colon. */
    public static String modId(String key) {
        String name = registryName(key);
        int colon = name.indexOf(':');
        return colon < 0 ? "" : name.substring(0, colon);
    }

    /**
     * Does {@code pattern} — a normalised key that may carry a wildcard metadata — match the
     * concrete key {@code candidate}?
     *
     * <p>Registry names must be equal; only metadata wildcards are honoured. Both sides are assumed
     * already {@link #normalise(String) normalised}.</p>
     */
    public static boolean matches(String pattern, String candidate) {
        if (pattern == null || candidate == null) {
            return false;
        }
        if (!registryName(pattern).equals(registryName(candidate))) {
            return false;
        }
        String patternMeta = metaPart(pattern);
        return META_WILDCARD.equals(patternMeta) || patternMeta.equals(metaPart(candidate));
    }
}
