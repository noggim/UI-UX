package com.yongsa;

import com.yongsa.model.EnhanceService;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.ui.ForgeMotion;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ForgeMotionTest {
    @Test void finalStrikeCommitsOnceEvenAcrossDroppedFrames() {
        var motion = new ForgeMotion();
        motion.start(1.35f);
        assertFalse(motion.advance(0.5f));
        assertTrue(motion.busy());
        assertThrows(IllegalStateException.class, () -> motion.start(1));
        assertTrue(motion.advance(5));
        motion.result(EnhanceService.Outcome.SUCCESS, false, 1);
        assertFalse(motion.advance(10));
        assertFalse(motion.advance(10));
        assertFalse(motion.busy());
    }

    @Test void everyActualOutcomeSurvivesPresentationAndChargesOnlyOnce() {
        // 2026-11-07: +6 은 성공 65 · 유지 18 · 하락 14 · 파괴 3 (숙련도 0)
        double[] rolls = {0.1, 0.7, 0.9, 0.99};
        var outcomes = new EnhanceService.Outcome[]{EnhanceService.Outcome.SUCCESS,
                EnhanceService.Outcome.KEEP, EnhanceService.Outcome.DOWN, EnhanceService.Outcome.DESTROY};
        for (int i = 0; i < rolls.length; i++) {
            var balance = TestData.balance();
            var state = NewGame.create(balance);
            var sword = state.newWeapon("blackiron");
            sword.level = 6; state.equippedUid = sword.uid;
            state.addOre(OreType.BLACKIRON, 100);
            int before = state.ore(OreType.BLACKIRON);
            double roll = rolls[i];
            int[] draws = {0};
            Random random = new Random() {
                @Override public double nextDouble() { draws[0]++; return roll; }
            };
            var motion = new ForgeMotion();
            var service = new EnhanceService(balance);
            motion.start(1);
            assertFalse(motion.advance(0.75f));
            assertEquals(before, state.ore(OreType.BLACKIRON));
            assertTrue(motion.advance(0.3f));
            var result = service.enhance(state, sword, random);
            motion.result(result.outcome, result.awakened, 1);
            assertEquals(outcomes[i], motion.outcome());
            assertEquals(before - service.cost(6) + result.refund, state.ore(OreType.BLACKIRON));
            assertFalse(motion.advance(2));
            assertFalse(motion.advance(2));
            assertEquals(1, draws[0]);
            assertEquals(1, state.stats.enhanceTries);
            assertEquals(outcomes[i], motion.outcome());
        }
    }
}
