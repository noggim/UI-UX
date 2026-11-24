package com.yongsa.enemy;

/** 몬스터가 쏜 불덩이·마력탄. 벽에 닿거나 용사를 맞히면 사라진다. 구르는 동안에는 통과한다. */
public class Projectile {
    public float x, y, vx, vy;
    public float radius;
    public float life;
    public int damage;
    /** 그림 색 구분용: 쏜 몬스터의 그림 이름. */
    public String source;
    public boolean dead;
    /** 0보다 크면 유도탄: 초당 이 각도(라디안)까지 용사 쪽으로 방향을 튼다. */
    public float homing;
    /** 구르기로 피하면 사라진다 (수문장 유도 마탄). */
    public boolean dodgeVanish;
    /** 0보다 크면 사라질 때 그 자리에서 이만큼 사방으로 작은 탄이 퍼진다 (어둠 구체). */
    public int burst;
    public float age;
}
