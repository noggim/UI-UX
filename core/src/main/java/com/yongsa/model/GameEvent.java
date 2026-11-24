package com.yongsa.model;

/**
 * GameSession 이 화면에 알리는 일 (FR-공통-06). 화면은 이벤트를 받아 창을 열거나 글자를 띄운다.
 */
public class GameEvent {
    public enum Type {
        TOAST, FLOAT_TEXT, ORE_GAINED, SOUND,
        ROOM_LOCKED, ROOM_CLEARED,
        OPEN_FORGE, BED_USED, AUTOSAVE,
        BOSS_DEFEATED, FINAL_DEFEATED, HERO_DIED,
        UNSEAL_READY, ENTERED_DUNGEON,
        /** 연출용: 맞은 자리 (tone 이 HERO_HIT 면 용사가 맞음), 쓰러진 몬스터, 보스 내려찍기, 곡괭이질. */
        HIT_FX, MONSTER_DIED, SLAM_FX, MINE_FX,
        /** 보스방 문이 잠기며 보스가 나타남 (text = 보스 이름). */
        BOSS_FIGHT_START,
        /** 귀환 포탈을 사용함. 화면이 어두워진 뒤 GameSession.warpToCamp 를 부른다. */
        WARP,
        /** 보스 체력이 절반 아래로 내려가 2페이즈가 됨 (text = 보스 이름). */
        BOSS_ENRAGED,
        /** 마족병이 순간이동한 자리. */
        BLINK_FX,
        /** 보스 방패가 생김 / 깨짐 (size = 크기). */
        SHIELD_UP, SHIELD_BREAK,
        /** 바닥 공격이 터짐 (text = 모양 rock·bolt·tentacle·spear, 창은 dirX·dirY 가 끝점). */
        HAZARD_FX,
        /** 마왕이 쓰러졌다가 2페이즈로 변신을 시작함. */
        BOSS_PHASE2,
        /** 보스 대사 (text). x, y 는 말풍선 자리. */
        BOSS_SPEECH,
        /** 캠프 문양에서 심연으로 떨어짐. 화면이 어두워진 뒤 GameSession.dropToAbyss 를 부른다. */
        FALL,
        /** 문을 지나 다른 방으로 넘어감 (deferDoors 일 때). 화면이 어두워진 뒤 GameSession.passPendingDoor 를 부른다. */
        DOOR_ENTER,
        /** 상자를 열었다 (ore, amount, dungeonId, opened=room id; x, y는 보상 글자 위치). */
        CHEST_OPENED,
        /** 잡몹방의 다음 웨이브가 나왔다. text = 알림 글. */
        WAVE_STARTED,
        /** 검 스킬을 씀 (text = 종류, dirX·dirY = 방향) / 검 스킬이 맞음·낙뢰가 떨어짐 (size = 크기). */
        SKILL_CAST, SKILL_HIT
    }

    /** 떠오르는 글자 색 구분. */
    public enum Tone { HERO_HIT, MONSTER_HIT, INFO, ORE, HEAL, BLOCKED, SHIELD }

    public final Type type;
    public String text;
    public float x, y;
    public Tone tone;
    public OreType ore;
    public int amount;
    public String dungeonId;
    public int hpGain;
    /** BOSS_SPEECH: 말하는 사람 (null 이면 보스). 용사·마을 사람이면 이름이 붙는다. */
    public String speaker;
    /** BOSS_DEFEATED: 처음 잡았나 (다시 생긴 보스면 false, 힘은 오르지 않는다). */
    public boolean first;
    /** CHEST_OPENED: 얻은 포션 수. */
    public int potions;
    public String opened;
    /** 연출용 값: 방향, 크기, 그림 이름. */
    public float dirX, dirY, size;
    public String sprite;
    public boolean boss, flip;
    /** MONSTER_DIED: 쓰러질 때의 본모습을 유지한다. */
    public boolean phase2;

    public GameEvent(Type type) {
        this.type = type;
    }

    public static GameEvent toast(String text) {
        GameEvent e = new GameEvent(Type.TOAST);
        e.text = text;
        return e;
    }

    public static GameEvent floatText(float x, float y, String text, Tone tone) {
        GameEvent e = new GameEvent(Type.FLOAT_TEXT);
        e.x = x;
        e.y = y;
        e.text = text;
        e.tone = tone;
        return e;
    }

    public static GameEvent fx(Type type, float x, float y) {
        GameEvent e = new GameEvent(type);
        e.x = x;
        e.y = y;
        return e;
    }

    public static GameEvent sound(String name) {
        GameEvent e = new GameEvent(Type.SOUND);
        e.text = name;
        return e;
    }
}
