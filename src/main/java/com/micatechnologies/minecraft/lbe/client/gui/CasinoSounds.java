package com.micatechnologies.minecraft.lbe.client.gui;

import com.micatechnologies.minecraft.lbe.casino.CasinoFanfare;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import com.micatechnologies.minecraft.lbe.casino.plinko.PlinkoGame;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotSymbol;
import com.micatechnologies.minecraft.lbe.network.PacketCasinoResult;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;

/**
 * The sounds the player at a machine hears, played in step with {@link GuiCasinoMachine}'s
 * animation.
 *
 * <p>These are played here rather than by the server because only the screen knows when its reels
 * stop and its cards turn over, and a sound a few ticks off the picture is worse than none. The
 * server plays what everyone <i>else</i> nearby hears, and holds it until the same moment; see
 * {@code CasinoEffects}.
 *
 * <p>Every sound is played locally at the machine, in the Blocks category, so the player's own
 * volume slider governs it and nobody else hears it. Vanilla sounds only for now: no assets to ship.
 */
final class CasinoSounds {

    private final BlockPos pos;
    private final CasinoGame game;
    private final Random cosmetic = new Random();

    /** Plinko: the row the ball was on last tick, so a peg sounds once per row. */
    private int lastPlinkoRow = -1;

    /** Roulette: the tick of the next ball click. The gaps widen as the wheel slows. */
    private int nextRouletteClick;

    CasinoSounds(BlockPos pos, CasinoGame game) {
        this.pos = pos;
        this.game = game;
    }

    /** A round has started animating. */
    void roundStarted() {
        lastPlinkoRow = -1;
        nextRouletteClick = 0;
    }

    /** One tick of a round's animation, {@code tick} ticks in. */
    void animationTick(int tick) {
        switch (game) {
            case SLOTS:
                for (int reel = 0; reel < CasinoFanfare.REEL_STOP_TICKS.length; reel++) {
                    if (tick == CasinoFanfare.REEL_STOP_TICKS[reel]) {
                        play(SoundEvents.BLOCK_NOTE_BASEDRUM, 0.8F, 0.8F + 0.2F * reel);
                        return;
                    }
                }
                if (tick % 3 == 0 && tick < CasinoFanfare.REEL_STOP_TICKS[2]) {
                    play(SoundEvents.BLOCK_NOTE_HAT, 0.15F, 1.8F);
                }
                break;
            case ROULETTE:
                if (tick >= nextRouletteClick) {
                    play(SoundEvents.BLOCK_NOTE_HAT, 0.35F, 1.5F + cosmetic.nextFloat() * 0.2F);
                    // Every tick at first, then one in two, one in three...: a ball losing speed.
                    nextRouletteClick = tick + 1 + tick / 8;
                }
                break;
            case PLINKO: {
                int row = Math.min(PlinkoGame.ROWS,
                    tick * PlinkoGame.ROWS / CasinoFanfare.REVEAL_TICKS);
                if (row != lastPlinkoRow) {
                    lastPlinkoRow = row;
                    play(SoundEvents.BLOCK_NOTE_XYLOPHONE, 0.4F, 0.9F + cosmetic.nextFloat() * 0.5F);
                }
                break;
            }
            case KENO:
                if (tick % 4 == 0) {
                    play(SoundEvents.BLOCK_NOTE_PLING, 0.3F,
                        0.8F + tick / (float) CasinoFanfare.REVEAL_TICKS);
                }
                break;
            case COIN_FLIP:
                if (tick % 4 == 0) {
                    play(SoundEvents.BLOCK_NOTE_CHIME, 0.3F, (tick / 4) % 2 == 0 ? 1.6F : 1.8F);
                }
                break;
            case WAR:
            case BACCARAT:
            case HIGH_LOW:
            case VIDEO_POKER:
                if (tick == 0 || tick == 8 || tick == 16) {
                    play(SoundEvents.ENTITY_ITEMFRAME_ROTATE_ITEM, 0.6F, 1.2F);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Something was dealt mid-round: a hand to hold or call on, or a mines tile that was safe.
     *
     * @param revealedCount mines only: how many tiles are now turned
     */
    void dealt(int revealedCount) {
        if (game == CasinoGame.MINES) {
            if (revealedCount > 0) {
                // Rising with each safe tile, so a long run sounds like it is getting tense.
                play(SoundEvents.BLOCK_NOTE_PLING, 0.6F, Math.min(2.0F, 0.8F + 0.08F * revealedCount));
            }
            return;
        }
        play(SoundEvents.ENTITY_ITEMFRAME_ROTATE_ITEM, 0.6F, 1.0F);
    }

    /** The result is on screen. */
    void settled(PacketCasinoResult result) {
        CasinoFanfare fanfare = CasinoFanfare.of(result.multiplier(), isSlotsTopPrize(result));
        if (game == CasinoGame.MINES && fanfare == CasinoFanfare.LOSS) {
            play(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.35F, 1.4F);
        }
        switch (fanfare) {
            case JACKPOT:
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                break;
            case BIG_WIN:
                play(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.8F, 1.0F);
                break;
            case WIN:
                play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);
                play(SoundEvents.BLOCK_NOTE_CHIME, 0.6F, 1.5F);
                break;
            case PUSH:
                play(SoundEvents.BLOCK_NOTE_HARP, 0.6F, 1.0F);
                break;
            case LOSS:
            default:
                play(SoundEvents.BLOCK_NOTE_BASS, 0.6F, 0.6F);
                break;
        }
    }

    /**
     * The same rule the server uses for slots: three sevens. Read from the reels the screen was
     * sent, which the server derived from the spin that paid.
     */
    private boolean isSlotsTopPrize(PacketCasinoResult result) {
        // Checked explicitly rather than with a fallback index: byIndex wraps, so a missing reel
        // read as -1 would come back as the last symbol, which is the seven.
        if (game != CasinoGame.SLOTS || result.reveal().length < 3) {
            return false;
        }
        for (int reel = 0; reel < 3; reel++) {
            if (SlotSymbol.byIndex(result.reveal(reel, 0)) != SlotSymbol.SEVEN) {
                return false;
            }
        }
        return true;
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return;
        }
        mc.world.playSound(pos, sound, SoundCategory.BLOCKS, volume, pitch, false);
    }
}
