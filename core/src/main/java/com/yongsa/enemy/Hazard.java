package com.yongsa.enemy;

/**
 * 바닥에 생기는 보스 공격: 예고가 보이고 delay 초 뒤 터진다.
 * CIRCLE(낙석·낙뢰·촉수)과 LINE(창)은 한 번 터지고 사라지고,
 * POOL(피웅덩이)은 점점 넓어지며 life 초 동안 남아 밟으면 체력이 닳고 느려진다.
 * EYE(마왕의 눈알)는 뜨는 순간 용사가 그쪽을 보고 있으면 맞는다 (범위가 아니라 시선).
 */
public class Hazard {
    public enum Kind { CIRCLE, LINE, POOL, EYE }

    public final Kind kind;
    /** 그림 구분: rock, bolt, tentacle, spear, blood, bone, whip, eye, quake, boulder, pound, brand, flame, cage, spin, leap. */
    public final String style;
    public float x, y;
    /** LINE 의 끝점. */
    public float x2, y2;
    /** CIRCLE·LINE 의 범위 (LINE 은 두께의 절반). POOL 은 지금 반지름. */
    public float radius;
    public float startRadius, maxRadius, grow;
    /** CIRCLE: 0보다 크면 가운데가 빈 고리 (지진 파동). 이 반지름 안쪽은 맞지 않는다. */
    public float innerRadius;
    /** 남은 동안 용사를 따라다니다가, 다 되면 그 자리에 멈춘다 (마왕의 낙인). */
    public float followT;
    /** 이 몬스터가 멈춰 서 있는 동안만 터진다. 움직이기 시작하면(CHANNEL 이 끝나면) 사라진다 (기사의 창 찌르기). */
    public Monster standingOwner;
    /** 터지기까지 남은 시간, 예고 전체 시간. */
    public float delay, warning;
    /** POOL 이 남아 있는 시간. */
    public float life;
    public int damage;
    public float multiplier;
    public float age;
    public boolean triggered, dead;

    public Hazard(Kind kind, String style) {
        this.kind = kind;
        this.style = style;
    }

    /** 예고가 차오르는 정도 0 → 1. */
    public float progress() {
        return warning <= 0 ? 1f : 1f - Math.max(0f, delay) / warning;
    }

    /** 점 (px, py) 가 범위 안인가. LINE 은 선분까지의 거리로 본다. */
    public boolean covers(float px, float py, float extra) {
        if (kind != Kind.LINE) {
            float dx = px - x, dy = py - y, r = radius + extra;
            float d2 = dx * dx + dy * dy;
            if (innerRadius > 0) {
                float in = Math.max(0f, innerRadius - extra);
                if (d2 < in * in) return false;
            }
            return d2 <= r * r;
        }
        float vx = x2 - x, vy = y2 - y;
        float len2 = vx * vx + vy * vy;
        float t = len2 <= 0 ? 0 : Math.max(0f, Math.min(1f, ((px - x) * vx + (py - y) * vy) / len2));
        float cx = x + vx * t, cy = y + vy * t;
        float dx = px - cx, dy = py - cy, r = radius + extra;
        return dx * dx + dy * dy <= r * r;
    }
}
