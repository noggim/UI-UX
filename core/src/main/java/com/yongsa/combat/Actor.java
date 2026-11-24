package com.yongsa.combat;

/**
 * 맵 위에서 움직이는 것 (용사, 몬스터). x, y 는 발 위치(픽셀)이고,
 * 충돌은 발 둘레의 작은 사각형(hw × hh), 피격은 몸통 원(hurtRadius)으로 판정한다.
 */
public abstract class Actor {
    public float x, y;
    public float hw, hh;
    public float hurtRadius;
    public int hp, maxHp;
    /** 맞으면 깜빡이는 남은 시간. */
    public float flash;
    /** 밀려나는 속도와 남은 시간 (FR-전투-07). */
    public float kbVX, kbVY, kbT;
    public boolean dead;

    public float centerY() {
        return y + hurtRadius * 0.9f;
    }

    public void knockback(float dirX, float dirY, float distance) {
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 0.001f || distance <= 0) return;
        kbT = 0.12f;
        kbVX = dirX / len * distance / kbT;
        kbVY = dirY / len * distance / kbT;
    }
}
