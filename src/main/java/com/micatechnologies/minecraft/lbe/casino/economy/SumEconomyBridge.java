package com.micatechnologies.minecraft.lbe.casino.economy;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.sum.api.EconomyFailure;
import com.micatechnologies.minecraft.sum.api.EconomyHandle;
import com.micatechnologies.minecraft.sum.api.EconomyResult;
import com.micatechnologies.minecraft.sum.api.EconomyScope;
import com.micatechnologies.minecraft.sum.api.EconomyStatus;
import com.micatechnologies.minecraft.sum.api.EscrowResult;
import com.micatechnologies.minecraft.sum.api.EscrowTicket;
import com.micatechnologies.minecraft.sum.api.SumEconomy;
import java.util.Optional;
import java.util.OptionalDouble;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The casino's money, backed by SUM.
 *
 * <p><b>This is the only class in LBE that names a SUM type.</b> Everything else talks to
 * {@link CasinoBank} and {@link Wager}, which are pure LBE. Loading this class requires SUM's api
 * jar to be present, so {@link LbeEconomy} only ever mentions it behind a
 * {@code Loader.isModLoaded} check — see the note there.
 *
 * <p><b>Why escrow rather than spend-then-credit.</b> A game is two money movements with a gap in
 * the middle: take the bet, then pay or keep it. Done as a spend followed by a credit, a crash in
 * that gap deletes the player's stake with no record that it ever existed. Escrow makes the gap
 * safe — the stake sits in a ticket written to the world save, and SUM refunds it automatically if
 * LBE is removed or de-authorized. The gap here is only milliseconds wide, but it is a gap in
 * somebody's real balance, and closing it costs one extra call.
 */
public final class SumEconomyBridge implements CasinoBank {

    /** SUM's api version this was written against. Refuse to run against an older one. */
    private static final int REQUIRED_API = 1;

    @Nullable
    private final EconomyHandle economy;

    private final String denial;

    private String lastFailure = "";

    private SumEconomyBridge(@Nullable EconomyHandle economy, String denial) {
        this.economy = economy;
        this.denial = denial;
    }

    /**
     * Asks SUM for access. Call from {@code FMLServerStartingEvent} or later — SUM chooses its
     * economy backend when the server starts, so asking in {@code preInit} always fails.
     *
     * @param announce whether to log a refusal. False for the periodic retries in
     *     {@link LbeEconomy#bank()}, which would otherwise repeat one warning forever.
     * @return a bridge that is always usable as a {@link CasinoBank}, even when access was refused;
     *     in that case it reports {@link #isReady()} false and explains why.
     */
    public static CasinoBank acquire(boolean announce) {
        if (!SumEconomy.isCompatible(REQUIRED_API)) {
            String message = "SUM's economy API is older than this mod needs (wants v"
                + REQUIRED_API + ", found v" + SumEconomy.API_VERSION + "). Update SUM.";
            if (announce) {
                Lbe.LOGGER.warn("[casino] {}", message);
            }
            return new SumEconomyBridge(null, message);
        }
        Optional<EconomyHandle> handle = SumEconomy.acquire(LbeConstants.MOD_NAMESPACE);
        if (!handle.isPresent()) {
            // SUM's denial text names the exact config line an operator has to add, so it is worth
            // far more in the log verbatim than any summary of it would be.
            String reason = SumEconomy.describeDenial(LbeConstants.MOD_NAMESPACE);
            if (announce) {
                Lbe.LOGGER.warn("[casino] The casino is switched off: {}", reason);
            }
            return new SumEconomyBridge(null,
                "This server has not authorized the casino to handle money yet.");
        }
        EconomyHandle economy = handle.get();
        if (!economy.hasScope(EconomyScope.ESCROW)) {
            // Authorized, but not for the one thing a wager needs. Worth its own message: the fix
            // is a different config line, not the same one again.
            String message = "The casino is authorized but not for 'escrow', so it cannot hold "
                + "bets. Add escrow to lbe's scopes in SUM's economy_integration.allowedMods.";
            if (announce) {
                Lbe.LOGGER.warn("[casino] {}", message);
            }
            return new SumEconomyBridge(null, "This server has not allowed the casino to hold bets.");
        }
        // Always logged, announce or not: a casino coming online is a state change an operator
        // wants to see, and it happens at most once per server run.
        Lbe.LOGGER.info("[casino] Connected to SUM's economy with {}.", economy.getScopes());
        return new SumEconomyBridge(economy, "");
    }

    @Override
    public boolean isReady() {
        return economy != null && economy.getStatus().isAvailable();
    }

    @Override
    public String unavailableReason() {
        if (economy == null) {
            return denial;
        }
        return economy.getStatus().isAvailable() ? "" : "The economy is unavailable right now.";
    }

    @Override
    public OptionalDouble balance(EntityPlayer player) {
        return economy == null ? OptionalDouble.empty() : economy.getWalletBalance(player);
    }

    @Override
    public String currencySymbol() {
        if (economy == null) {
            return "$";
        }
        EconomyStatus status = economy.getStatus();
        String symbol = status.getCurrencySymbol();
        return symbol == null || symbol.isEmpty() ? "$" : symbol;
    }

    @Override
    @Nullable
    public Wager stake(EntityPlayer player, double amount, String reason) {
        lastFailure = "";
        if (economy == null) {
            lastFailure = denial;
            return null;
        }
        EscrowResult held = economy.escrowOpen(player, amount, reason);
        if (!held.isOk()) {
            lastFailure = playerFacing(held.getFailure(), held.getMessage());
            return null;
        }
        return new EscrowWager(economy, held.getTicket().get());
    }

    @Override
    public String lastFailure() {
        return lastFailure;
    }

    /**
     * Turns a SUM failure into something worth showing a player.
     *
     * <p>SUM already splits its failures into "the caller's bug" and "the player's situation". The
     * first kind is ours — a scope we forgot to ask for, a call off the server thread — and showing
     * a player the internals of our mistake tells them nothing they can act on, so it is logged and
     * replaced with something honest and vague.
     */
    private static String playerFacing(EconomyFailure failure, String message) {
        if (failure != null && failure.isCallerError()) {
            Lbe.LOGGER.error("[casino] The casino asked SUM for something invalid: {} ({}). This is "
                + "a bug in LBE or a misconfiguration, not the player's fault.", failure, message);
            return "The machine is out of order.";
        }
        return message;
    }

    /** A stake held in a SUM escrow ticket. */
    private static final class EscrowWager implements Wager {

        private final EconomyHandle economy;
        private final EscrowTicket ticket;
        private boolean settled;

        EscrowWager(EconomyHandle economy, EscrowTicket ticket) {
            this.economy = economy;
            this.ticket = ticket;
        }

        @Override
        public double amount() {
            return ticket.getAmount();
        }

        @Override
        public boolean payOut(double totalReturn) {
            if (!claim("payOut")) {
                return false;
            }
            EntityPlayer owner = ownerOrNull();
            if (owner == null) {
                // They logged out mid-spin. The hold stays open on purpose: SUM will not credit an
                // absent player, and destroying a winning stake because somebody's connection died
                // is the worst possible resolution. An operator can refund it, and SUM's orphan
                // sweep gets it eventually regardless.
                Lbe.LOGGER.warn("[casino] {} won but left before being paid; their stake stays held "
                    + "in ticket {}.", ticket.getOwner(), ticket.getId());
                settled = false;
                return false;
            }
            EconomyResult returned = economy.escrowRelease(ticket, owner, "casino payout");
            if (!returned.isOk()) {
                Lbe.LOGGER.error("[casino] Could not return a winning stake ({}); the hold stays "
                    + "open, so the money is not lost.", returned.getMessage());
                settled = false;
                return false;
            }
            // The stake is back in their wallet. Anything above it is new money from the house, and
            // it is a separate credit precisely so an operator's cap applies to it.
            double winnings = totalReturn - ticket.getAmount();
            if (winnings <= 0.0) {
                return true;
            }
            EconomyResult paid = economy.walletCredit(owner, winnings, "casino winnings");
            if (!paid.isOk()) {
                // The stake landed and the winnings did not. Say so loudly: this is the one path
                // where a player is genuinely owed money and nothing in the world records it.
                Lbe.LOGGER.error("[casino] Paid back {}'s stake but could not pay {} in winnings: "
                    + "{}", owner.getName(), winnings, paid.getMessage());
                return false;
            }
            return true;
        }

        @Override
        public boolean loseToHouse() {
            if (!claim("loseToHouse")) {
                return false;
            }
            // Forfeit rather than release: the house keeps a losing bet, and SUM destroys it. This
            // is the only thing LBE does that removes money from the economy, and it is what keeps
            // the casino from being a faucet.
            EconomyResult kept = economy.escrowForfeit(ticket, "casino loss");
            if (!kept.isOk()) {
                Lbe.LOGGER.error("[casino] Could not settle a losing bet: {}", kept.getMessage());
                settled = false;
                return false;
            }
            return true;
        }

        @Override
        public boolean cancel() {
            if (!claim("cancel")) {
                return false;
            }
            EconomyResult refunded = economy.escrowRefund(ticket, "casino: game did not happen");
            if (!refunded.isOk()) {
                Lbe.LOGGER.error("[casino] Could not refund an abandoned bet: {}. The hold stays "
                    + "open; '/sum econ api refund {}' will return it.", refunded.getMessage(),
                    ticket.getId());
                settled = false;
                return false;
            }
            return true;
        }

        /**
         * Marks this wager as being settled now, refusing a second attempt.
         *
         * <p>SUM would refuse the duplicate itself — it re-reads every ticket by id — so this is not
         * what makes double-settlement safe. It is here to catch the bug on <i>our</i> side, loudly
         * and with a stack trace, rather than letting it show up as an unexplained
         * {@code ESCROW_ALREADY_CLOSED} in someone else's log.
         */
        private boolean claim(String operation) {
            if (settled) {
                Lbe.LOGGER.error("[casino] {} called on an already-settled wager (ticket {}). This "
                    + "is a bug in LBE's game flow.", operation, ticket.getId(),
                    new IllegalStateException("wager settled twice"));
                return false;
            }
            settled = true;
            return true;
        }

        @Nullable
        private EntityPlayer ownerOrNull() {
            net.minecraft.server.MinecraftServer server =
                net.minecraftforge.fml.common.FMLCommonHandler.instance()
                    .getMinecraftServerInstance();
            return server == null ? null
                : server.getPlayerList().getPlayerByUUID(ticket.getOwner());
        }
    }
}
