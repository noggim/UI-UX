package com.yongsa.gfx;

import com.badlogic.gdx.graphics.Color;

/** WorldRenderer 가 가진 화면 효과 (빛 번쩍임, 퍼지는 고리, 알갱이, 발밑 그림자). 맵·적 그리기에서 빌려 쓴다. */
public interface Effects {
    Particles particles();

    void flash(float x, float y, float radius, Color c, float dur);

    void ring(float x, float y, float r0, float r1, float dur, Color c);

    void actorShadow(float x, float y, float width, float alpha, boolean flying);
}
