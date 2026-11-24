package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.YongsaGame;
import com.yongsa.gfx.Art;
import com.yongsa.ui.Modal;
import com.yongsa.ui.ModalStack;

/** 타이틀·소개·엔딩이 함께 쓰는 틀: 캠프 바닥과 모닥불 배경, 키로 고르는 창. */
public abstract class MenuScreen extends ScreenAdapter {
    protected final YongsaGame game;
    protected final Stage stage;
    protected final ModalStack modals;
    protected float time;

    protected MenuScreen(YongsaGame game) {
        this.game = game;
        this.stage = new Stage(new FitViewport(YongsaGame.WIDTH, YongsaGame.HEIGHT), game.batch);
        this.modals = new ModalStack(stage);
        this.embers = new com.yongsa.gfx.Particles(game.art.white(), game.art.glow);
        lightCam.position.set(YongsaGame.WIDTH / 2f, YongsaGame.HEIGHT / 2f, 0);
        lightCam.update();
    }

    /** 바닥에 깔리는 창. 다른 창이 위에 열리면 그 창이 키를 받는다. */
    protected void open(Modal base) {
        modals.push(base);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                boolean handled = modals.keyDown(keycode);
                if (handled) game.sfx.play("select");
                return true;
            }
        }, stage));
    }

    @Override
    public void render(float delta) {
        time += delta;
        Gdx.gl.glClearColor(0.04f, 0.035f, 0.05f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.getViewport().apply();
        SpriteBatch b = game.batch;
        b.setProjectionMatrix(stage.getCamera().combined);
        b.begin();
        drawBackground(b);
        b.end();
        stage.act(delta);
        stage.draw();
    }

    protected void drawBackground(SpriteBatch b) {
        Art art = game.art;
        float W = YongsaGame.WIDTH, H = YongsaGame.HEIGHT;
        if (art.hasHd("title_background")) {
            b.setColor(Color.WHITE);
            b.draw(art.get("title_background"), 0, 0, W, H);
            // Fine strips produce a smooth readable scrim without another bitmap.
            for (int x = 0; x < W; x += 4) {
                float a = 0.86f * (1f - MathUtils.clamp((x - 270f) / 720f, 0, 1));
                b.setColor(0.025f, 0.035f, 0.045f, a);
                b.draw(art.white(), x, 0, 4, H);
            }
            b.setColor(Color.WHITE);
            return;
        }
        float cx = W / 2f, cy = 96;
        float flick = 1f + 0.05f * MathUtils.sin(time * 9f) + 0.03f * MathUtils.sin(time * 23f);
        // 캠프 바닥. 밝기는 아래에서 빛 지도를 곱해 정한다.
        int T = 48;
        for (int y = 0; y < H / T + 1; y++) {
            for (int x = 0; x < W / T + 1; x++) {
                b.draw(art.floor("CAMP", x, y), x * T, y * T, T, T);
                TextureRegion dec = art.decal("CAMP", x, y);
                if (dec != null) b.draw(dec, x * T, y * T, T, T);
            }
        }
        b.setColor(Color.WHITE);
        // 성검과 용사 (모닥불을 보고 앉아 있다)
        b.setColor(0, 0, 0, 0.35f);
        b.draw(art.get("shadow"), cx - 150, cy - 8, 60, 16);
        b.draw(art.get("shadow"), cx - 30, cy - 10, 60, 18);
        b.setColor(1f, 0.85f, 0.7f, 1f);
        b.draw(art.get("hero_side"), cx - 152, cy - 6, 48, 60);
        b.draw(art.get("sword_holy"), cx + 112, cy + 44, 0, 0, 48, 48, 1, 1, -135);
        b.draw(art.get("chain"), cx + 110, cy + 36, 0, 0, 36, 36, 1, 1, -135);
        b.setColor(Color.WHITE);
        TextureRegion fire = art.get(((int) (time * 6)) % 2 == 0 ? "campfire" : "campfire2");
        b.draw(fire, cx - 24, cy - 8, 48, 48);

        b.end();
        light.begin(0.07f, 0.06f, 0.08f);
        light.add(cx, cy + 20, 560 * flick, 1f, 0.62f, 0.32f, 1f);
        light.add(cx + 112, cy + 10, 110, 1f, 0.9f, 0.5f, 0.35f + 0.1f * MathUtils.sin(time * 2f));
        light.render(b, lightCam);
        stage.getViewport().apply();
        light.composite(b);
        b.setProjectionMatrix(stage.getCamera().combined);
        b.begin();
        // 빛과 불똥 (더하기)
        embers.update(Gdx.graphics.getDeltaTime());
        emberT -= Gdx.graphics.getDeltaTime();
        while (emberT <= 0) {
            emberT += 0.04f;
            com.yongsa.gfx.Particles.P e = embers.spawn(cx + MathUtils.random(-14f, 14f), cy + 18, MathUtils.random(1.2f, 2.6f),
                    MathUtils.random(2f, 4f), new Color(1f, MathUtils.random(0.45f, 0.8f), 0.2f, 1f));
            e.vy = MathUtils.random(40f, 90f);
            e.vx = MathUtils.random(-18f, 18f);
            e.glow = true;
            e.endSize = 0;
        }
        com.yongsa.gfx.Particles.additive(b);
        b.setColor(1f, 0.55f, 0.2f, 0.35f * flick);
        b.draw(art.glow, cx - 160, cy - 110, 320, 260);
        b.setColor(1f, 0.7f, 0.3f, 0.5f * flick);
        b.draw(art.glow, cx - 50, cy - 20, 100, 90);
        b.setColor(1f, 0.9f, 0.5f, 0.25f + 0.1f * MathUtils.sin(time * 2f));
        b.draw(art.glow, cx + 72, cy - 20, 80, 80);
        b.setColor(1f, 0.8f, 0.4f, 0.12f + 0.04f * MathUtils.sin(time * 1.3f));
        b.draw(art.glow, cx - 420, H - 300, 840, 260);
        embers.drawGlow(b);
        com.yongsa.gfx.Particles.normal(b);
        b.setColor(0, 0, 0, 0.75f);
        b.draw(art.vignette, 0, 0, W, H);
        b.setColor(Color.WHITE);
    }

    private final com.yongsa.gfx.Particles embers;
    private final com.yongsa.gfx.Lighting light = new com.yongsa.gfx.Lighting(YongsaGame.WIDTH, YongsaGame.HEIGHT);
    private final com.badlogic.gdx.graphics.OrthographicCamera lightCam = new com.badlogic.gdx.graphics.OrthographicCamera(YongsaGame.WIDTH, YongsaGame.HEIGHT);
    private float emberT;

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        light.dispose();
    }
}
