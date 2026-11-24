package com.yongsa.model;

import com.yongsa.config.Balance;
import com.yongsa.config.Balance.Band;
import com.yongsa.config.Balance.MasteryCfg;
import com.yongsa.config.Balance.OddsRow;
import com.yongsa.config.Balance.WeaponDef;

import java.util.Random;

/**
 * 강화 한 번을 판정한다 (FR-대장간-05~10, 명세서 5.2).
 * <p>
 * 결과는 성공(+1)·유지·하락(-1)·파괴 네 가지 (2026-11-07 플레이 피드백으로 대성공을 없앴다).
 * 기본 확률은 balance.json 의 enhance.odds 표 그대로이고, 강화 숙련도가 오를수록 하락·파괴가 줄어
 * 그만큼 유지로 옮겨 간다. 숙련도 18~20 에서는 유지 몫 일부가 성공으로 간다.
 * 숙련도 경험치는 성공·유지·하락·파괴 모두에서 쌓인다.
 * 숙련도는 검마다가 아니라 용사 하나의 실력이라 모든 검이 함께 쓴다 ({@link GameState#masteryLevel}).
 */
public class EnhanceService {

    public enum Outcome { SUCCESS, KEEP, DOWN, DESTROY }

    /** 한 번 시도 기준 확률(%), 네 값의 합은 100. */
    public static final class Odds {
        public final double success, keep, down, destroy;

        Odds(double success, double keep, double down, double destroy) {
            this.success = success;
            this.keep = keep;
            this.down = down;
            this.destroy = destroy;
        }

        public double total() {
            return success + keep + down + destroy;
        }
    }

    public static final class Result {
        public final Outcome outcome;
        public final int levelBefore, levelAfter;
        public final boolean awakened;
        public final int refund;
        public final OreType refundOre;
        /** 이번 강화로 얻은 숙련도 경험치와 그 뒤 숙련도 레벨 (레벨이 올랐는지 화면에서 보여 준다). */
        public final int masteryGained, masteryBefore, masteryAfter;

        Result(Outcome outcome, int before, int after, boolean awakened, int refund, OreType refundOre,
               int masteryGained, int masteryBefore, int masteryAfter) {
            this.outcome = outcome;
            this.levelBefore = before;
            this.levelAfter = after;
            this.awakened = awakened;
            this.refund = refund;
            this.refundOre = refundOre;
            this.masteryGained = masteryGained;
            this.masteryBefore = masteryBefore;
            this.masteryAfter = masteryAfter;
        }

        public boolean masteryLeveledUp() {
            return masteryAfter > masteryBefore;
        }
    }

    private final Balance b;
    private final Rules rules;

    public EnhanceService(Balance b) {
        this.b = b;
        this.rules = new Rules(b);
    }

    private Band band(int level) {
        for (Band band : b.enhance.bands) {
            if (level >= band.fromLevel && level <= band.toLevel) return band;
        }
        throw new IllegalArgumentException("강화 구간 없음: +" + level);
    }

    private OddsRow row(int level) {
        for (OddsRow r : b.enhance.odds) if (r.level == level) return r;
        throw new IllegalArgumentException("강화 확률 없음: +" + level);
    }

    private MasteryCfg mastery() {
        return b.enhance.mastery;
    }

    /** 숙련도 0 기준 확률. */
    public Odds odds(int level) {
        return odds(level, 0);
    }

    /** 지금 상태(숙련도 포함)의 확률. */
    public Odds odds(GameState s, int level) {
        return odds(level, s.masteryLevel);
    }

    /**
     * 숙련도 masteryLevel 에서의 확률. 하락·파괴는 레벨에 비례해 줄어 downZeroLevel·destroyZeroLevel 에서 0이 되고,
     * 줄어든 만큼은 유지가 된다. successBonus 레벨부터는 유지 몫에서 성공으로 옮긴다.
     */
    public Odds odds(int level, int masteryLevel) {
        OddsRow r = row(level);
        MasteryCfg m = mastery();
        int lv = Math.max(0, Math.min(masteryLevel, m.maxLevel));
        double down = r.down * Math.max(0, 1 - (double) lv / m.downZeroLevel);
        double destroy = r.destroy * Math.max(0, 1 - (double) lv / m.destroyZeroLevel);
        double keep = r.keep + (r.down - down) + (r.destroy - destroy);
        double bonus = Math.min(successBonus(lv), keep);
        keep -= bonus;
        return new Odds(r.success + bonus, keep, down, destroy);
    }

    /** 이 숙련도 레벨에서 성공에 더하는 %p (유지에서 옮겨 온다). */
    public double successBonus(int masteryLevel) {
        double best = 0;
        if (mastery().successBonus == null) return 0;
        for (Balance.SuccessBonus g : mastery().successBonus) if (masteryLevel >= g.level) best = Math.max(best, g.percent);
        return best;
    }

    public int cost(int level) {
        return band(level).cost;
    }

    public boolean isMax(Weapon w) {
        return w.level >= b.enhance.maxLevel;
    }

    /** 지금 숙련도로 이 무기가 파괴될 수 있나. */
    public boolean canDestroy(GameState s, Weapon w) {
        return !isMax(w) && odds(s, w.level).destroy > 0;
    }

    /** 숙련도를 생각하지 않은 기본 확률로 파괴될 수 있는 단계인가. */
    public boolean canDestroy(Weapon w) {
        return !isMax(w) && odds(w.level).destroy > 0;
    }

    public OreType oreOf(Weapon w) {
        WeaponDef d = b.weapon(w.defId);
        return d.ore == null ? null : OreType.valueOf(d.ore);
    }

    public int destroyRefund(Weapon w) {
        return (int) Math.floor(b.weapon(w.defId).craftCost * b.enhance.destroyRefundRatio);
    }

    // ───────────────────────── 숙련도

    public int masteryMaxLevel() {
        return mastery().maxLevel;
    }

    public boolean masteryMaxed(GameState s) {
        return s.masteryLevel >= mastery().maxLevel;
    }

    /** masteryLevel 에서 다음 레벨까지 필요한 경험치. 최대 레벨이면 0. */
    public int masteryNeed(int masteryLevel) {
        MasteryCfg m = mastery();
        if (masteryLevel >= m.maxLevel) return 0;
        double l = masteryLevel;
        return (int) Math.round(m.needBase + m.needLinear * l + m.needSquare * l * l);
    }

    /** 레벨 0부터 masteryLevel 까지 쌓아야 하는 경험치 합. */
    public int masteryTotalTo(int masteryLevel) {
        int sum = 0;
        for (int l = 0; l < masteryLevel; l++) sum += masteryNeed(l);
        return sum;
    }

    /** 이 결과로 얻는 숙련도 경험치: 결과별 기본값 × 강화하던 단계 구간의 배율. 0강에서도 쌓인다. */
    public int masteryExp(Outcome o, int levelBefore) {
        MasteryCfg m = mastery();
        int base;
        switch (o) {
            case SUCCESS: base = m.expSuccess; break;
            case KEEP: base = m.expKeep; break;
            case DOWN: base = m.expDown; break;
            default: base = m.expDestroy;
        }
        return Math.round(base * band(levelBefore).expMultiplier);
    }

    /** 경험치를 더하고 넘치면 레벨을 올린다. 최대 레벨이면 경험치는 0으로 둔다. */
    public void addMasteryExp(GameState s, int exp) {
        if (masteryMaxed(s)) {
            s.masteryLevel = mastery().maxLevel;
            s.masteryExp = 0;
            return;
        }
        s.masteryExp += Math.max(0, exp);
        while (!masteryMaxed(s) && s.masteryExp >= masteryNeed(s.masteryLevel)) {
            s.masteryExp -= masteryNeed(s.masteryLevel);
            s.masteryLevel++;
        }
        if (masteryMaxed(s)) s.masteryExp = 0;
    }

    /** 예전 저장 파일이나 손으로 고친 값이 범위를 벗어나면 바로잡는다. */
    public void clampMastery(GameState s) {
        s.masteryLevel = Math.max(0, Math.min(s.masteryLevel, mastery().maxLevel));
        s.masteryExp = masteryMaxed(s) ? 0 : Math.max(0, Math.min(s.masteryExp, masteryNeed(s.masteryLevel) - 1));
    }

    // ───────────────────────── 강화

    /** 강화할 수 없는 이유. 할 수 있으면 null. */
    public String blockReason(GameState s, Weapon w) {
        if (w == null) return "장착한 무기가 없습니다";
        WeaponDef d = b.weapon(w.defId);
        if (!d.enhanceable) return d.name + "은 강화할 수 없습니다";
        if (isMax(w)) return "이미 최대 단계입니다";
        if (s.ore(oreOf(w)) < cost(w.level)) return oreOf(w).displayName + "이 모자랍니다";
        return null;
    }

    public Result enhance(GameState s, Weapon w, Random rnd) {
        String reason = blockReason(s, w);
        if (reason != null) throw new IllegalStateException(reason);
        OreType ore = oreOf(w);
        int before = w.level;
        s.addOre(ore, -cost(before));
        s.stats.enhanceTries++;

        Odds o = odds(s, before);
        double r = rnd.nextDouble() * 100.0;
        Outcome outcome;
        if (r < o.success) outcome = Outcome.SUCCESS;
        else if (r < o.success + o.keep) outcome = Outcome.KEEP;
        else if (r < o.success + o.keep + o.down) outcome = Outcome.DOWN;
        else outcome = Outcome.DESTROY;

        int mBefore = s.masteryLevel;
        int gained = masteryExp(outcome, before);
        addMasteryExp(s, gained);

        Result result;
        switch (outcome) {
            case SUCCESS: {
                w.level = Math.min(b.enhance.maxLevel, w.level + 1);
                boolean awakenedNow = false;
                if (isMax(w) && !w.awakened) {
                    w.awakened = true;
                    awakenedNow = true;
                }
                s.stats.enhanceSuccess++;
                result = new Result(outcome, before, w.level, awakenedNow, 0, null, gained, mBefore, s.masteryLevel);
                break;
            }
            case KEEP:
                s.stats.enhanceKeep++;
                result = new Result(outcome, before, before, false, 0, null, gained, mBefore, s.masteryLevel);
                break;
            case DOWN:
                w.level = Math.max(0, w.level - 1);
                s.stats.enhanceDown++;
                result = new Result(outcome, before, w.level, false, 0, null, gained, mBefore, s.masteryLevel);
                break;
            default: {
                int refund = destroyRefund(w);
                s.addOre(ore, refund);
                s.weapons.remove(w);
                Weapon holy = holySword(s);
                s.equippedUid = holy == null ? 0 : holy.uid;
                s.stats.destroyed++;
                result = new Result(Outcome.DESTROY, before, -1, false, refund, ore, gained, mBefore, s.masteryLevel);
            }
        }
        log(s, w, result);
        return result;
    }

    private Weapon holySword(GameState s) {
        for (Weapon x : s.weapons) if (rules.isHoly(x)) return x;
        return null;
    }

    private void log(GameState s, Weapon w, Result r) {
        String name = b.weapon(w.defId).name;
        String line;
        switch (r.outcome) {
            case SUCCESS:
                line = name + " +" + r.levelBefore + " → +" + r.levelAfter + " 성공" + (r.awakened ? " (각성!)" : "");
                break;
            case KEEP:
                line = name + " +" + r.levelBefore + " 유지";
                break;
            case DOWN:
                line = name + " +" + r.levelBefore + " → +" + r.levelAfter + " 하락";
                break;
            default:
                line = name + " +" + r.levelBefore + " 파괴 (" + r.refundOre.displayName + " " + r.refund + "개 돌려받음)";
        }
        s.enhanceLog.add(0, line);
        while (s.enhanceLog.size() > 5) s.enhanceLog.remove(s.enhanceLog.size() - 1);
    }
}
