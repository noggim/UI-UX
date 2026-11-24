package com.yongsa.enemy;

import com.yongsa.combat.Actor;
import com.yongsa.combat.Facing;

import com.yongsa.config.Balance.MonsterDef;

/** 잡몹과 보스 (FR-전투-06, 10). */
public class Monster extends Actor {
    public enum State {
        /** 방에 막 나타남. 마법진만 보이고 움직이지 않는다. */
        SPAWN,
        CHASE,
        /** 근접 공격 예고 (잡몹 공통, 보스 근접). */
        TELEGRAPH,
        RECOVER,
        /** 박쥐: 방향을 정하고 움츠림 → 일직선으로 덮침. */
        SWOOP_TELEGRAPH, SWOOPING,
        /** 마족병·보스: 투사체를 쏘기 전 모으기. */
        CAST_TELEGRAPH,
        /** 보스 패턴. */
        SLAM_TELEGRAPH, CHARGE_TELEGRAPH, CHARGING, STUNNED, COMBO_TELEGRAPH,
        /** 보스: 제자리에서 낙석·낙뢰·회전 화염·피웅덩이 같은 긴 패턴을 이어 간다. */
        CHANNEL,
        /** 마왕: 1페이즈에서 쓰러져 2페이즈로 변하는 중. 무적. */
        PHASE_SHIFT,
        /** 혈월의 기사: 구석에서 뒤로 크게 도약하는 중. 공중이라 맞지 않는다. */
        LEAP,
        /** 해골병: 쓰러져 뼈 무더기가 된 채 다시 일어나려는 중. 이때 때리면 완전히 부서진다. */
        DOWNED
    }

    public final MonsterDef def;
    public final boolean boss, finalBoss;
    public final float speed;
    public State state = State.CHASE;
    /** 지금 상태가 끝날 때까지 남은 시간, 그 상태의 전체 시간. */
    public float stateT, stateTotal;
    public float attackCd;
    public float dirX = 0, dirY = -1;
    public float slamX, slamY, slamRadius;
    public float walkTime;
    public float immuneMsgCd;

    /** 보스: 다음 고유 패턴까지 남은 시간, 마지막에 쓴 패턴. */
    public float patternCd;
    public String lastPattern;
    /** 체력이 절반 아래로 내려가 2페이즈가 됐다. */
    public boolean enraged;
    /** 돌진·덮치기 방향과 남은 거리, 이번 돌진으로 이미 때렸는지. */
    public float lockX, lockY, dashLeft;
    public boolean dashHit;
    /** 연속 베기 남은 횟수. */
    public int comboLeft;
    /** 지금 준비 중인 투사체 패턴 (cast, volley, ring). */
    public String castKind;
    /** 박쥐가 도는 방향, 마족병 순간이동 쿨타임. */
    public float orbitSign = 1;
    public float blinkCd;
    /** 연속 돌진 남은 횟수. */
    public int rushLeft;
    /** 긴 패턴(CHANNEL) 종류, 남은 횟수, 다음 단계까지 시간, 회전 화염 각도. */
    public String channelKind;
    public int channelLeft;
    public float channelT, spinAngle;
    /** 방패: 남아 있으면 체력 대신 방패가 닳는다. */
    public int shield, shieldMax;
    /** 마왕 2페이즈. */
    public boolean phase2;
    /** 구석에 몰려 용사가 붙어 있던 시간. */
    public float cornerT;
    /** 공격력 배율 (보스를 잡은 던전의 잡몹, 마왕 2페이즈). */
    public float attackMul = 1f;
    /** 뒤로 도약: 뛰어오른 자리와 착지할 자리. */
    public float leapX0, leapY0, leapX1, leapY1;
    /** 마왕 2페이즈 최후의 발악 (체력 20% 아래). */
    public boolean lastStand;
    /** 해골병 방패: 지금 들고 있나, 들기·내리기가 바뀔 때까지 남은 시간. */
    public boolean guarding;
    public float guardT;
    /** 해골병: 이미 한 번 다시 일어났다. */
    public boolean revived;

    /** 방패를 들고 맞설 수 있는 상태 (공격 중에는 내린다). */
    public boolean guardActive() {
        return guarding && state == State.CHASE;
    }
    /** 마족병이 지금까지 쏜 불덩이 수 (몇 번째마다 세 갈래). */
    public int castCount;

    public Monster(MonsterDef def, boolean boss, boolean finalBoss, float tileSize) {
        this.def = def;
        this.boss = boss;
        this.finalBoss = finalBoss;
        this.speed = def.speedTiles * tileSize;
        this.hp = this.maxHp = def.hp;
        float scale = finalBoss ? 2.4f : boss ? 2.0f : 1f;
        hw = 9 * scale;
        hh = 6 * scale;
        hurtRadius = 12 * scale;
    }

    /** 실제 공격력. */
    public int attack() {
        return Math.round(def.attack * attackMul);
    }

    /** 이 잡몹의 공격 예고 시간, 공격 뒤 쉬는 시간 (따로 정하지 않았으면 공통 값). */
    public float telegraph(float common) {
        return def.telegraph > 0 ? def.telegraph : common;
    }

    public float cooldown(float common) {
        return def.cooldown > 0 ? def.cooldown : common;
    }

    public String ai() {
        return def.ai == null ? "melee" : def.ai;
    }

    public Facing facing() {
        return Facing.of(dirX, dirY);
    }

    public void enter(State s, float seconds) {
        state = s;
        stateT = stateTotal = seconds;
    }

    /** 0 → 1 로 차오르는 지금 상태의 진행도. */
    public float progress() {
        return stateTotal <= 0 ? 1f : 1f - Math.max(0f, stateT) / stateTotal;
    }

    /** 공격을 준비하거나 돌진하는 중: 이때는 맞아도 밀려나지 않는다. */
    public boolean committed() {
        return state != State.CHASE && state != State.RECOVER && state != State.SPAWN && state != State.STUNNED;
    }

    /** 변신 중이라 맞지 않는다. */
    public boolean invulnerable() {
        return state == State.PHASE_SHIFT || state == State.SPAWN || state == State.LEAP;
    }

    /** 머리 위에 ‘!’ 를 띄울 상태. */
    public boolean warning() {
        return state == State.TELEGRAPH || state == State.SWOOP_TELEGRAPH || state == State.CAST_TELEGRAPH
                || state == State.SLAM_TELEGRAPH || state == State.CHARGE_TELEGRAPH || state == State.COMBO_TELEGRAPH;
    }
}
