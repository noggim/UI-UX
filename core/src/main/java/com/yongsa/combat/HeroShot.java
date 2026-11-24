package com.yongsa.combat;

import com.yongsa.enemy.Monster;

import java.util.HashSet;
import java.util.Set;

/**
 * 용사의 검 스킬이 만든 것: 날아가는 검기(cleave·crescent·beam) 또는 잠깐 뒤 떨어지는 낙뢰(strike).
 * 몬스터마다 한 번씩만 맞힌다.
 */
public class HeroShot {
    public String kind;
    public float x, y, vx, vy;
    /** 판정 반지름 (낙뢰는 터지는 범위). */
    public float radius;
    public float life, age;
    /** 낙뢰: 떨어질 때까지 남은 시간. 0 이하면 날아가는 검기다. */
    public float delay;
    public float multiplier;
    /** 맞은 잡몹을 기절시키는 시간, 맞힌 적마다 회복하는 최대 체력 비율. */
    public float stun, heal;
    public boolean dead;
    public final Set<Monster> hit = new HashSet<>();

    public boolean strike() {
        return "strike".equals(kind);
    }
}
