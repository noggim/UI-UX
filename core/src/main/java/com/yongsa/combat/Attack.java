package com.yongsa.combat;

import com.yongsa.enemy.Monster;

import java.util.HashSet;
import java.util.Set;

/** 공격 한 번. 같은 몬스터는 한 번만 맞는다 (FR-전투-14). */
public class Attack {
    public enum Kind { BASIC }

    public final Kind kind;
    public final float multiplier;
    private final Set<Monster> hit = new HashSet<>();

    public Attack(Kind kind, float multiplier) {
        this.kind = kind;
        this.multiplier = multiplier;
    }

    /** 처음 맞는 몬스터면 true 를 돌려주고 기록한다. */
    public boolean tryHit(Monster m) {
        return hit.add(m);
    }
}
