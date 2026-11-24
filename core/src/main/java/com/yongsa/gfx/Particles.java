package com.yongsa.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 작은 입자 효과 (불똥, 먼지, 광석 조각, 쓰러질 때 연기).
 * z 는 땅에서 뜬 높이라서 조각이 튀어 올랐다가 바닥에 떨어져 구른다.
 */
public class Particles {
    public static class P {
        public float x, y, z, vx, vy, vz;
        public float life, max;
        public float size, endSize;
        public float r, g, b, a;
        public float gravity, drag;
        public boolean glow, soft;
    }

    private final List<P> list = new ArrayList<>();
    private final List<P> pool = new ArrayList<>();
    private final TextureRegion white;
    private final Texture softTex;

    public Particles(TextureRegion white, Texture softTex) {
        this.white = white;
        this.softTex = softTex;
    }

    public P spawn(float x, float y, float life, float size, Color c) {
        P p = pool.isEmpty() ? new P() : pool.remove(pool.size() - 1);
        p.x = x;
        p.y = y;
        p.z = 0;
        p.vx = p.vy = p.vz = 0;
        p.life = p.max = life;
        p.size = p.endSize = size;
        p.r = c.r;
        p.g = c.g;
        p.b = c.b;
        p.a = c.a;
        p.gravity = 0;
        p.drag = 0;
        p.glow = false;
        p.soft = false;
        list.add(p);
        return p;
    }

    /** 사방으로 튀는 불똥. */
    public void burst(float x, float y, int n, Color c, float speed, float life, float size, boolean glow) {
        for (int i = 0; i < n; i++) {
            float a = MathUtils.random(MathUtils.PI2);
            float s = speed * MathUtils.random(0.4f, 1f);
            P p = spawn(x, y, life * MathUtils.random(0.6f, 1f), size, c);
            p.vx = MathUtils.cos(a) * s;
            p.vy = MathUtils.sin(a) * s * 0.7f;
            p.drag = 4f;
            p.endSize = 0;
            p.glow = glow;
        }
    }

    /** 한쪽 방향으로 퍼지는 불똥. */
    public void spray(float x, float y, float dirX, float dirY, int n, Color c, float speed, float spread, float life, float size) {
        float base = MathUtils.atan2(dirY, dirX);
        for (int i = 0; i < n; i++) {
            float a = base + MathUtils.random(-spread, spread);
            float s = speed * MathUtils.random(0.5f, 1f);
            P p = spawn(x, y, life * MathUtils.random(0.6f, 1f), size, c);
            p.vx = MathUtils.cos(a) * s;
            p.vy = MathUtils.sin(a) * s;
            p.drag = 6f;
            p.endSize = 0;
            p.glow = true;
        }
    }

    /** 위로 튀어 올랐다가 떨어지는 조각. */
    public void debris(float x, float y, int n, Color c, float speed, float size) {
        for (int i = 0; i < n; i++) {
            float a = MathUtils.random(MathUtils.PI2);
            float s = speed * MathUtils.random(0.3f, 1f);
            Color cc = new Color(c).mul(MathUtils.random(0.7f, 1.15f), MathUtils.random(0.7f, 1.15f), MathUtils.random(0.7f, 1.15f), 1f);
            P p = spawn(x, y, MathUtils.random(0.6f, 1.0f), size * MathUtils.random(0.6f, 1.2f), cc);
            p.vx = MathUtils.cos(a) * s;
            p.vy = MathUtils.sin(a) * s * 0.6f;
            p.vz = MathUtils.random(80f, 200f);
            p.gravity = 600f;
            p.drag = 1.5f;
        }
    }

    /** 부드러운 연기 덩어리. */
    public void smoke(float x, float y, int n, Color c, float radius, float size, float life) {
        for (int i = 0; i < n; i++) {
            float a = MathUtils.random(MathUtils.PI2);
            float d = MathUtils.random(radius);
            P p = spawn(x + MathUtils.cos(a) * d, y + MathUtils.sin(a) * d * 0.6f, life * MathUtils.random(0.7f, 1.1f), size * 0.5f, c);
            p.endSize = size * MathUtils.random(1f, 1.5f);
            p.vx = MathUtils.cos(a) * 20f;
            p.vy = MathUtils.sin(a) * 12f;
            p.vz = MathUtils.random(10f, 30f);
            p.drag = 2f;
            p.soft = true;
        }
    }

    public void update(float dt) {
        for (int i = list.size() - 1; i >= 0; i--) {
            P p = list.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                list.remove(i);
                pool.add(p);
                continue;
            }
            float k = Math.max(0f, 1f - p.drag * dt);
            p.vx *= k;
            p.vy *= k;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.gravity != 0) {
                p.vz -= p.gravity * dt;
                p.z += p.vz * dt;
                if (p.z < 0) {
                    p.z = 0;
                    p.vz = -p.vz * 0.35f;
                    p.vx *= 0.6f;
                    p.vy *= 0.6f;
                }
            } else {
                p.z += p.vz * dt;
            }
        }
    }

    /** glow=false 인 것 (보통 섞기). */
    public void drawNormal(SpriteBatch batch) {
        for (P p : list) if (!p.glow) draw(batch, p);
        batch.setColor(Color.WHITE);
    }

    /** glow=true 인 것 (빛처럼 더하기). 부르기 전에 섞는 방식을 더하기로 바꿔 둔다. */
    public void drawGlow(SpriteBatch batch) {
        for (P p : list) if (p.glow) draw(batch, p);
        batch.setColor(Color.WHITE);
    }

    private void draw(SpriteBatch batch, P p) {
        float t = 1f - p.life / p.max;
        float s = p.size + (p.endSize - p.size) * t;
        float alpha = p.a * (p.soft ? (1f - t) * Math.min(1f, t * 6f) : Math.min(1f, p.life / (p.max * 0.4f)));
        if (s <= 0.2f || alpha <= 0) return;
        batch.setColor(p.r, p.g, p.b, alpha);
        if (p.soft) batch.draw(softTex, p.x - s, p.y + p.z - s, s * 2, s * 2);
        else batch.draw(white, Math.round(p.x - s / 2f), Math.round(p.y + p.z - s / 2f), s, s);
    }

    public int size() {
        return list.size();
    }

    public void clear() {
        pool.addAll(list);
        list.clear();
    }

    public static void additive(SpriteBatch batch) {
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
    }

    public static void normal(SpriteBatch batch) {
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }
}
