package com.yongsa.map;

/**
 * 카메라는 방 단위로 넘어가지 않고 용사를 부드럽게 따라간다 (FR-맵-07).
 * 따라가는 비율은 1 − e^(−k·delta) 라서 프레임 수와 상관없이 같은 느낌이다. 맵 밖은 보이지 않게 막는다.
 */
public class CameraFollow {
    public float x, y;
    public float stiffness = 7f;
    private boolean snapped;

    public void update(float targetX, float targetY, float dt, float viewW, float viewH, float worldW, float worldH) {
        if (!snapped) {
            x = targetX;
            y = targetY;
            snapped = true;
        } else {
            float a = 1f - (float) Math.exp(-stiffness * dt);
            x += (targetX - x) * a;
            y += (targetY - y) * a;
        }
        x = clamp(x, viewW / 2f, worldW - viewW / 2f);
        y = clamp(y, viewH / 2f, worldH - viewH / 2f);
    }

    public void snap() {
        snapped = false;
    }

    private static float clamp(float v, float lo, float hi) {
        if (lo > hi) return (lo + hi) / 2f;
        return Math.max(lo, Math.min(hi, v));
    }
}
