package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.EnhanceService;
import com.yongsa.model.EnhanceService.Odds;
import com.yongsa.model.EnhanceService.Outcome;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 강화 숙련도·대성공·하락 (2026-10-27 김선우 결정). */
class EnhanceMasteryTest {
    private final Balance b = TestData.balance();
    private final EnhanceService es = new EnhanceService(b);

    /** nextDouble 이 언제나 이 값 (0~1) 인 난수. 확률 표의 원하는 칸을 고른다. */
    private static Random fixed(double v) {
        return new Random() {
            @Override public double nextDouble() { return v; }
        };
    }

    private GameState withSword(int level) {
        GameState s = NewGame.create(b);
        Weapon w = s.newWeapon("blackiron");
        w.level = level;
        s.equippedUid = w.uid;
        s.addOre(OreType.BLACKIRON, 1000);
        return s;
    }

    @Test
    void oddsSumTo100AtEveryMasteryLevel() {
        for (int m = 0; m <= es.masteryMaxLevel(); m++)
            for (int lv = 0; lv < b.enhance.maxLevel; lv++)
                assertEquals(100.0, es.odds(lv, m).total(), 1e-9, "Lv" + m + " +" + lv);
    }

    @Test
    void masteryShrinksDownAndDestroyIntoKeepButNotSuccess() {
        Odds o0 = es.odds(8, 0), o10 = es.odds(8, 10);
        assertEquals(o0.success, o10.success, 1e-9, "성공 확률은 그대로");
        assertEquals(18 * (1 - 10 / 17.0), o10.down, 1e-9);
        assertEquals(5 * (1 - 10 / 15.0), o10.destroy, 1e-9);
        assertTrue(o10.keep > o0.keep);
        // 파괴는 Lv 15, 하락은 Lv 17 에서 사라진다
        for (int lv = 0; lv < 10; lv++) {
            assertEquals(0, es.odds(lv, 15).destroy, 1e-9);
            assertTrue(es.odds(9, 14).destroy > 0);
            assertEquals(0, es.odds(lv, 17).down, 1e-9);
        }
        assertTrue(es.odds(9, 16).down > 0);
        // Lv 17 부터는 유지·성공만
        Odds late = es.odds(9, 17);
        assertEquals(100.0, late.success + late.keep, 1e-9);
    }

    @Test
    void masterBonusMovesKeepIntoSuccess() {
        assertEquals(65, es.odds(6, 17).success, 1e-9);
        assertEquals(66, es.odds(6, 18).success, 1e-9);
        assertEquals(67, es.odds(6, 19).success, 1e-9);
        assertEquals(68, es.odds(6, 20).success, 1e-9);
        assertEquals(es.odds(6, 17).keep - 3, es.odds(6, 20).keep, 1e-9);
    }

    @Test
    void successAlwaysRaisesExactlyOne() {
        // 대성공이 없어졌다: 성공은 언제나 +1
        GameState s = withSword(6);
        Weapon w = s.equipped();
        EnhanceService.Result r = es.enhance(s, w, fixed(0.0));
        assertEquals(Outcome.SUCCESS, r.outcome);
        assertEquals(7, w.level);
        assertEquals(1, s.stats.enhanceSuccess);

        GameState s9 = withSword(9);
        Weapon w9 = s9.equipped();
        EnhanceService.Result r9 = es.enhance(s9, w9, fixed(0.0));
        assertEquals(10, w9.level);
        assertTrue(w9.awakened);
    }

    @Test
    void keepCountsAndTeachesTheHero() {
        GameState s = withSword(6);
        Weapon w = s.equipped();
        // 성공 65 다음이 유지 18
        EnhanceService.Result r = es.enhance(s, w, fixed(0.70));
        assertEquals(Outcome.KEEP, r.outcome);
        assertEquals(6, w.level);
        assertEquals(1, s.stats.enhanceKeep);
        assertEquals(20, r.masteryGained, "유지 10 × 2");
    }

    @Test
    void enhancingAllThreeSwordsToTenRarelyDestroysMoreThanTwice() {
        // 2026-11-07 플레이 피드백: 검 세 자루를 모두 +10 까지 올려도 파괴는 많아야 1~2번
        int runs = 2000, three = 0;
        long total = 0;
        java.util.Random rnd = new java.util.Random(11);
        for (int run = 0; run < runs; run++) {
            GameState s = NewGame.create(b);
            int destroyed = 0;
            for (String id : new String[]{"blackiron", "bloodmoon", "skyfall"}) {
                OreType ore = OreType.valueOf(b.weapon(id).ore);
                Weapon w = s.newWeapon(id);
                s.equippedUid = w.uid;
                while (w.level < b.enhance.maxLevel) {
                    s.addOre(ore, 10);
                    EnhanceService.Result r = es.enhance(s, w, rnd);
                    if (r.outcome == Outcome.DESTROY) {
                        destroyed++;
                        w = s.newWeapon(id);
                        s.equippedUid = w.uid;
                    }
                }
            }
            total += destroyed;
            if (destroyed >= 3) three++;
        }
        double mean = total / (double) runs;
        assertTrue(mean < 0.5, "평균 파괴 " + mean);
        assertTrue(three <= runs / 100, "세 번 이상 파괴 " + three + "/" + runs);
    }

    @Test
    void downLowersOneLevel() {
        GameState s = withSword(5);
        Weapon w = s.equipped();
        // 성공 70 + 유지 16 = 86 이후가 하락
        EnhanceService.Result r = es.enhance(s, w, fixed(0.90));
        assertEquals(Outcome.DOWN, r.outcome);
        assertEquals(4, w.level);
        assertEquals(1, s.stats.enhanceDown);
    }

    @Test
    void everyOutcomeGivesMasteryEvenAtPlusZero() {
        assertEquals(15, es.masteryExp(Outcome.SUCCESS, 0), "0강 성공도 숙련도가 쌓인다 (20 × 0.75)");
        assertEquals(8, es.masteryExp(Outcome.KEEP, 0), "유지도 쌓인다 (10 × 0.75 반올림)");
        assertEquals(45, es.masteryExp(Outcome.DOWN, 4), "하락 30 × 1.5");
        assertEquals(120, es.masteryExp(Outcome.DESTROY, 8), "파괴 60 × 2");
        assertEquals(40, es.masteryExp(Outcome.SUCCESS, 9));

        GameState s = withSword(0);
        es.enhance(s, s.equipped(), fixed(0.0)); // 성공
        assertEquals(0, s.masteryLevel, "15 경험치로는 아직 Lv0 (필요 20)");
        assertEquals(15, s.masteryExp);
        es.enhance(s, s.equipped(), fixed(0.0)); // +1 → +2 성공
        assertEquals(1, s.masteryLevel, "두 번 성공하면 Lv1");
        assertEquals(10, s.masteryExp);
    }

    @Test
    void destructionStillTeachesTheHero() {
        GameState s = withSword(9);
        es.enhance(s, s.equipped(), fixed(0.9999));
        assertEquals(1, s.stats.destroyed);
        assertEquals(120, es.masteryTotalTo(s.masteryLevel) + s.masteryExp, "파괴 120 경험치");
    }

    @Test
    void requirementGrowsEveryLevelAndTotalsAboutTwentySixHundred() {
        int prev = 0;
        for (int l = 0; l < es.masteryMaxLevel(); l++) {
            int need = es.masteryNeed(l);
            assertTrue(need > prev, "Lv" + l + " 필요 경험치가 점점 는다");
            prev = need;
        }
        assertEquals(20, es.masteryNeed(0));
        assertEquals(106, es.masteryNeed(9));
        assertEquals(296, es.masteryNeed(19));
        assertEquals(0, es.masteryNeed(20));
        assertEquals(2590, es.masteryTotalTo(20));
    }

    @Test
    void masteryIsSharedByAllSwordsAndSurvivesDestruction() {
        GameState s = withSword(9);
        es.enhance(s, s.equipped(), fixed(0.9999)); // 흑철검 파괴
        int lv = s.masteryLevel, exp = s.masteryExp;
        assertTrue(lv > 0);
        Weapon other = s.newWeapon("bloodmoon");
        s.equippedUid = other.uid;
        s.addOre(OreType.BLOODMOON, 100);
        assertEquals(lv, s.masteryLevel, "다른 검으로 바꿔도 숙련도는 그대로");
        es.enhance(s, other, fixed(0.0));
        assertTrue(s.masteryLevel * 10000 + s.masteryExp > lv * 10000 + exp, "혈월검을 강화해도 같은 숙련도가 오른다");
    }

    @Test
    void maxLevelStopsAndClampFixesBadValues() {
        GameState s = NewGame.create(b);
        es.addMasteryExp(s, 999999);
        assertEquals(20, s.masteryLevel);
        assertEquals(0, s.masteryExp);
        es.addMasteryExp(s, 50);
        assertEquals(20, s.masteryLevel);
        s.masteryLevel = 99;
        s.masteryExp = -5;
        es.clampMastery(s);
        assertEquals(20, s.masteryLevel);
        assertEquals(0, s.masteryExp);
    }

    @Test
    void longRunNeverBreaksTheTable() {
        GameState s = withSword(0);
        s.addOre(OreType.BLACKIRON, 100000);
        Random rnd = new Random(7);
        for (int i = 0; i < 3000; i++) {
            Weapon w = s.equipped();
            if (w == null || !"blackiron".equals(w.defId) || es.isMax(w)) {
                w = s.newWeapon("blackiron");
                s.equippedUid = w.uid;
            }
            int before = w.level;
            EnhanceService.Result r = es.enhance(s, w, rnd);
            if (r.outcome != Outcome.DESTROY) assertTrue(w.level >= 0 && w.level <= 10);
            if (r.outcome == Outcome.DOWN) assertTrue(before >= 3);
            if (r.outcome == Outcome.DESTROY) {
                assertTrue(before >= 6);
                assertTrue(r.masteryBefore < 15, "Lv15 부터는 파괴되지 않는다");
            }
            if (r.outcome == Outcome.DOWN) assertTrue(r.masteryBefore < 17, "Lv17 부터는 하락하지 않는다");
        }
        assertEquals(20, s.masteryLevel, "3000번이면 마스터");
    }
}
