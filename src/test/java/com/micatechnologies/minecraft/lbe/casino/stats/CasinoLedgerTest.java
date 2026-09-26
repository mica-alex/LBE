package com.micatechnologies.minecraft.lbe.casino.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The ledger adds up, orders its leaderboard fairly, and keeps its lists bounded. */
class CasinoLedgerTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    @DisplayName("rounds, stakes and returns add up per game and overall")
    void tallies() {
        CasinoLedger ledger = new CasinoLedger();
        ledger.record(ALICE, "Alice", "slot_machine", 10.0, 0.0);
        ledger.record(ALICE, "Alice", "slot_machine", 10.0, 50.0);
        ledger.record(ALICE, "Alice", "roulette_table", 5.0, 10.0);

        CasinoLedger.Player alice = ledger.player(ALICE);
        CasinoLedger.Tally slots = alice.games().get("slot_machine");
        assertEquals(2, slots.rounds());
        assertEquals(20.0, slots.staked(), 1e-9);
        assertEquals(50.0, slots.returned(), 1e-9);
        assertEquals(30.0, slots.net(), 1e-9);
        assertEquals(50.0, slots.biggestReturn(), 1e-9);

        CasinoLedger.Tally total = alice.total();
        assertEquals(3, total.rounds());
        assertEquals(35.0, total.net(), 1e-9);
    }

    @Test
    @DisplayName("the leaderboard lists only players who are ahead, best first")
    void topWinnersOnlyAhead() {
        CasinoLedger ledger = new CasinoLedger();
        ledger.record(ALICE, "Alice", "slot_machine", 10.0, 30.0);   // +20
        ledger.record(BOB, "Bob", "slot_machine", 10.0, 0.0);        // -10
        ledger.record(CAROL, "Carol", "slot_machine", 10.0, 60.0);   // +50

        List<CasinoLedger.Player> top = ledger.topWinners(5);
        assertEquals(2, top.size(), "nobody who is down appears as a winner");
        assertEquals("Carol", top.get(0).name());
        assertEquals("Alice", top.get(1).name());
        assertEquals(1, ledger.topWinners(1).size());
    }

    @Test
    @DisplayName("a rename follows through, and lookup by name ignores case")
    void renames() {
        CasinoLedger ledger = new CasinoLedger();
        ledger.record(ALICE, "Alice", "slot_machine", 1.0, 0.0);
        ledger.record(ALICE, "Alicia", "slot_machine", 1.0, 0.0);
        assertEquals("Alicia", ledger.player(ALICE).name());
        assertNotNull(ledger.playerNamed("ALICIA"));
        assertNull(ledger.playerNamed("Alice"));
    }

    @Test
    @DisplayName("recent big wins are newest first and bounded")
    void recentBigWins() {
        CasinoLedger ledger = new CasinoLedger();
        for (int i = 0; i < CasinoLedger.RECENT_BIG_WINS + 5; i++) {
            ledger.bigWin(new CasinoLedger.BigWin("P" + i, "Slots", i, i));
        }
        List<CasinoLedger.BigWin> wins = ledger.recentBigWins();
        assertEquals(CasinoLedger.RECENT_BIG_WINS, wins.size());
        assertEquals("P" + (CasinoLedger.RECENT_BIG_WINS + 4), wins.get(0).name());
    }

    @Test
    @DisplayName("every change moves the version, so a board knows to refresh")
    void version() {
        CasinoLedger ledger = new CasinoLedger();
        long before = ledger.version();
        ledger.record(ALICE, "Alice", "slot_machine", 1.0, 0.0);
        long afterRound = ledger.version();
        assertNotEquals(before, afterRound);
        ledger.bigWin(new CasinoLedger.BigWin("Alice", "Slots", 500.0, 0L));
        assertTrue(ledger.version() > afterRound);
    }
}
