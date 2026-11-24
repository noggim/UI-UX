package com.yongsa.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

import java.util.Random;

/**
 * 빛 지도. 어두운 바탕색(ambient)을 깔고 그 위에 빛을 더한 그림을 따로 그린 뒤,
 * 맵 그림에 곱해서 던전은 어둡고 불빛 근처만 밝게 보이게 한다.
 */
public class Lighting implements Disposable {
    private static final int MAX = 256;

    private final FrameBuffer fbo;
    private final TextureRegion fboRegion;
    private final Texture light;
    private final Matrix4 screen = new Matrix4();
    private final int width, height;

    private final float[] lx = new float[MAX], ly = new float[MAX], lr = new float[MAX];
    private final Color[] lc = new Color[MAX];
    private int count;
    private final Color ambient = new Color();

    public Lighting(int width, int height) {
        this.width = width;
        this.height = height;
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);
        light = radial(256);
        screen.setToOrtho2D(0, 0, width, height);
        for (int i = 0; i < MAX; i++) lc[i] = new Color();
    }

    /** 가운데가 가장 밝고 바깥으로 부드럽게 사라지는 원. 띠가 보이지 않게 아주 약한 잡음을 섞는다. */
    public static Texture radial(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        Random r = new Random(3);
        float c = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.hypot(x - c, y - c) / c;
                float v = Math.max(0f, 1f - d);
                v = v * v * (3 - 2 * v);
                if (v > 0) v = Math.max(0f, Math.min(1f, v + (r.nextFloat() - 0.5f) / 160f));
                p.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, v));
            }
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return t;
    }

    public Texture lightTexture() {
        return light;
    }

    public void begin(float r, float g, float b) {
        ambient.set(r, g, b, 1f);
        count = 0;
    }

    /** 빛 하나. radius 는 빛이 사라지는 거리, 색의 a 는 세기. */
    public void add(float x, float y, float radius, float r, float g, float b, float intensity) {
        if (count >= MAX) return;
        lx[count] = x;
        ly[count] = y;
        lr[count] = radius;
        lc[count].set(r, g, b, intensity);
        count++;
    }

    public void render(SpriteBatch batch, OrthographicCamera cam) {
        fbo.begin();
        Gdx.gl.glClearColor(ambient.r, ambient.g, ambient.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        for (int i = 0; i < count; i++) {
            batch.setColor(lc[i]);
            batch.draw(light, lx[i] - lr[i], ly[i] - lr[i], lr[i] * 2, lr[i] * 2);
        }
        batch.end();
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        fbo.end();
    }

    /** 이미 그린 맵 위에 빛 지도를 곱한다. 화면 비율 그대로 꽉 채운다. */
    public void composite(SpriteBatch batch) {
        batch.setProjectionMatrix(screen);
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(fboRegion, 0, 0, width, height);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void dispose() {
        fbo.dispose();
        light.dispose();
    }
}
