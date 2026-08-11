package com.micatechnologies.minecraft.lbe.casino.economy;

import com.micatechnologies.minecraft.lbe.Lbe;
import java.util.OptionalDouble;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

/**
 * The casino's one door to money, and the gate that keeps SUM optional.
 *
 * <h2>The rule</h2>
 *
 * <p>{@link SumEconomyBridge} is named in exactly one place in this class, inside
 * {@link #connect(boolean)}, which is only ever reached after {@code Loader.isModLoaded} has
 * confirmed SUM is installed. The JVM does not load a class until something reaches it, so on a
 * pack without SUM that line is never executed, the class is never loaded, and its missing
 * supertypes never have to resolve.
 *
 * <p>Move that reference anywhere reachable without the check — a field of that type, an
 * {@code import} used in a signature, a stray {@code instanceof} — and LBE stops working on every
 * pack that does not run SUM. It will still compile, still pass the tests, and still boot; it will
 * throw {@code NoClassDefFoundError} the first time somebody touches a slot machine.
 *
 * <h2>What happens without SUM</h2>
 *
 * <p>Loot boxes are unaffected — they are the mod, and they have never needed a currency. The casino
 * blocks still exist and can still be placed; they say they are closed. That is deliberately not a
 * crash and not a startup refusal: a player who moves a pack around should get a machine that
 * politely says the bank is shut, not a world that will not load.
 */
public final class LbeEconomy {

    /** SUM's mod id. A string, not a class reference, precisely so this file stays SUM-free. */
    private static final String SUM_MOD_ID = "sum";

    /** How long to wait before trying SUM again after a failed connection. */
    private static final long RETRY_INTERVAL_MILLIS = 5_000L;

    /** Never null; a closed bank until a connection succeeds. */
    private static CasinoBank bank = ClosedBank.INSTANCE;

    /** True between server start and stop. Outside that window there is nothing to connect to. */
    private static boolean serverRunning;

    /** Throttle for the retry in {@link #bank()}, so a closed casino is not a busy loop. */
    private static long nextRetryAtMillis;

    private LbeEconomy() {
        throw new AssertionError("No instances.");
    }

    /**
     * Connects to whatever economy this server has, if any.
     *
     * <p>Must run on {@code FMLServerStartingEvent}: SUM picks its backend — local world save or a
     * remote service — as the server starts, so anything earlier is asking before there is an answer.
     */
    public static void onServerStarting() {
        serverRunning = true;
        nextRetryAtMillis = 0L;
        bank = ClosedBank.INSTANCE;
        if (!Loader.isModLoaded(SUM_MOD_ID)) {
            Lbe.LOGGER.info("[casino] SUM is not installed, so the casino is closed. Loot boxes are "
                + "unaffected.");
            return;
        }
        // Quietly: SUM publishes its economy during this same event, so if FML happens to run LBE
        // first this attempt fails through no fault of anyone's configuration. Complaining about it
        // would put a scary warning in the log of a server that is about to work perfectly.
        connect(false);
    }

    /**
     * Second attempt, once every mod's {@code FMLServerStartingEvent} handler has run.
     *
     * <p>This is the honest moment to report: SUM has certainly installed its economy by now if it
     * is going to, so whatever this says is what an operator actually has. The quiet attempt in
     * {@link #onServerStarting()} exists only to catch the common case early; this one is the
     * message worth reading.
     */
    public static void onServerStarted() {
        if (!serverRunning || !Loader.isModLoaded(SUM_MOD_ID) || bank.isReady()) {
            return;
        }
        connect(true);
    }

    /** Drops the connection when the server stops, so a second world does not reuse the first's. */
    public static void onServerStopped() {
        serverRunning = false;
        bank = ClosedBank.INSTANCE;
    }

    /**
     * The current source of money. Never null.
     *
     * <p><b>Retries a failed connection rather than giving up for the session</b>, which is what
     * makes the casino independent of mod load order. SUM publishes its economy during
     * {@code FMLServerStartingEvent} and LBE asks for it during that same event, so which of the
     * two runs first is decided by FML's sort -- and {@code after:sum} in LBE's {@code @Mod}
     * annotation turned out not to be enough to guarantee it in practice. Asking again later costs
     * a map lookup on a block interaction and removes the race completely.
     *
     * <p>It also covers the case an operator hits most often: authorizing LBE in SUM's config and
     * running {@code /sum econ api reload} on a live server. Without a retry the casino would stay
     * shut until the next restart, and the operator would reasonably conclude the config did not
     * work.
     */
    public static CasinoBank bank() {
        if (!bank.isReady() && serverRunning && Loader.isModLoaded(SUM_MOD_ID)) {
            long now = System.currentTimeMillis();
            if (now >= nextRetryAtMillis) {
                nextRetryAtMillis = now + RETRY_INTERVAL_MILLIS;
                // Quietly: the first attempt already explained itself, and a player standing at a
                // machine on a server with no economy must not fill the log with one line forever.
                connect(false);
            }
        }
        return bank;
    }

    /**
     * Tries to reach SUM's economy.
     *
     * @param announce whether to log a refusal. True for the attempt at server start, false for the
     *     periodic retries, which would otherwise repeat one message forever.
     */
    private static void connect(boolean announce) {
        try {
            // The one and only mention of the SUM-typed class. See the class note above.
            bank = SumEconomyBridge.acquire(announce);
        } catch (Throwable t) {
            // Defensive: a SUM present but too different to talk to must close the casino, not take
            // the server down with it. Loot boxes have nothing to do with any of this.
            if (announce) {
                Lbe.LOGGER.error("[casino] Could not connect to SUM's economy; it is closed.", t);
            }
            bank = ClosedBank.INSTANCE;
        }
    }

    /** True when a bet placed right now could actually move money. */
    public static boolean isOpen() {
        return bank.isReady();
    }

    /** Formats an amount the way this server's currency reads, e.g. {@code $12.50}. */
    public static String format(double amount) {
        return bank.currencySymbol() + String.format(java.util.Locale.ROOT, "%.2f", amount);
    }

    /**
     * A bank that has no money in it, used when there is no economy to talk to.
     *
     * <p>A null object rather than a null: every caller would otherwise need a null check before
     * every question, and the one that forgot would be the one a player found.
     */
    private static final class ClosedBank implements CasinoBank {

        static final ClosedBank INSTANCE = new ClosedBank();

        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public String unavailableReason() {
            return "This server has no economy, so the casino is closed.";
        }

        @Override
        public OptionalDouble balance(EntityPlayer player) {
            return OptionalDouble.empty();
        }

        @Override
        public String currencySymbol() {
            return "$";
        }

        @Override
        @Nullable
        public Wager stake(EntityPlayer player, double amount, String reason) {
            return null;
        }

        @Override
        public String lastFailure() {
            return unavailableReason();
        }
    }
}
