package com.yongsa.combat;

/** 맵 위의 용사. 체력은 GameState 에 있고 여기서는 움직임과 쿨타임만 다룬다. */
public class HeroActor extends Actor {
    public Facing facing = Facing.DOWN;
    /** 공격 방향 (마지막으로 움직인 8방향). */
    public float aimX = 0, aimY = -1;
    public float attackCd, rollCd, mineCd;
    /** 스킬 칸마다 남은 재사용 시간 (Rules.SKILL_SLOTS 칸). */
    public final float[] skillCd = new float[4];
    public float invuln;
    /** 구르기 남은 시간과 방향, 전체 시간 (그리기용). */
    public float rollT, rollTotal = 0.32f;
    public float rollX, rollY;
    /** Roll art follows travel direction even when automatic combat facing points elsewhere. */
    public Facing rollFacing = Facing.DOWN;
    public float walkTime;
    public boolean moving;
    /** 그리기용: 지금 휘두르는 공격 종류와 남은 시간. */
    public int swingKind;
    public float swingT;
    public float mineSwingT;
    /** 둔화 남은 시간 (피웅덩이). */
    public float slowT;
    /** 굳음 (마왕의 눈알): 남은 동안 움직이지도 치지도 못한다. */
    public float stunT;

    public HeroActor() {
        hw = 9;
        hh = 6;
        hurtRadius = 12;
    }

    public boolean rolling() {
        return rollT > 0;
    }
}
