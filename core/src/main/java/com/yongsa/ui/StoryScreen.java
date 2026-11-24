package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.YongsaGame;
import com.yongsa.config.Story;
import com.yongsa.ui.Ui;

import java.util.List;

/**
 * 이야기 그림 화면 (시작·엔딩 공통, 지용 버전에서 가져옴): 그림 한 장 + 아래 자막.
 * 그림은 천천히 확대되며 이전 그림 위로 겹쳐 나타난다. 아무 키나 누르면 다음 장, Esc 는 건너뛰기.
 */
public class StoryScreen extends ScreenAdapter {
    private final YongsaGame game;
    private final List<Story.Page> pages;
    private final Runnable onDone;
    private final OrthographicCamera cam = new OrthographicCamera();
    private final FitViewport viewport = new FitViewport(YongsaGame.WIDTH, YongsaGame.HEIGHT, cam);
    private final GlyphLayout layout = new GlyphLayout();
    private Texture current, previous;
    private float t, fade = 1f, textAlpha;
    private int index = -1;
    private boolean finished;

    public StoryScreen(YongsaGame game, List<Story.Page> pages, Runnable onDone) {
        this.game = game;
        this.pages = pages;
        this.onDone = onDone;
        next();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) index = pages.size() - 1;
                else game.sfx.play("select");
                next();
                return true;
            }

            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                next();
                return true;
            }
        });
    }

    private void next() {
        if (finished) return;
        index++;
        if (index >= pages.size()) {
            finished = true;
            Gdx.app.postRunnable(() -> {
                onDone.run();
                dispose();
            });
            return;
        }
        Story.Page p = pages.get(index);
        if (p.image != null) {
            if (previous != null) previous.dispose();
            previous = current;
            current = new Texture(Gdx.files.internal("story/" + p.image));
            current.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            t = 0;
            fade = 0;
        }
        textAlpha = 0;
    }

    @Override
    public void render(float delta) {
        t += delta;
        fade = Math.min(1f, fade + delta * 1.4f);
        textAlpha = Math.min(1f, textAlpha + delta / 0.6f);
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        SpriteBatch b = game.batch;
        b.setProjectionMatrix(cam.combined);
        b.begin();
        float w = YongsaGame.WIDTH, h = YongsaGame.HEIGHT;
        if (previous != null && fade < 1f) drawImage(b, previous, w, h, t + 8f, 1f);
        if (current != null) drawImage(b, current, w, h, t, fade);
        if (index >= 0 && index < pages.size()) drawCaption(b, pages.get(index).caption, w);
        b.setColor(Color.WHITE);
        b.end();
    }

    /** 화면을 꽉 채우고 (비율 유지) 천천히 확대. */
    private void drawImage(SpriteBatch b, Texture tex, float w, float h, float time, float alpha) {
        float scale = Math.max(w / tex.getWidth(), h / tex.getHeight()) * (1f + Math.min(0.08f, time * 0.008f));
        float iw = tex.getWidth() * scale, ih = tex.getHeight() * scale;
        b.setColor(1, 1, 1, alpha);
        b.draw(tex, (w - iw) / 2, (h - ih) / 2 + Math.min(20, time * 1.5f), iw, ih);
    }

    /** 아래쪽 어두운 띠 위에 자막과 쪽 번호. */
    private void drawCaption(SpriteBatch b, String caption, float w) {
        BitmapFont big = game.fonts.large, small = game.fonts.small;
        float boxW = 1100, boxX = (w - boxW) / 2;
        layout.setText(big, caption == null ? "" : caption, Color.WHITE, boxW - 60, Align.center, true);
        float boxH = layout.height + 74;
        b.setColor(0.04f, 0.05f, 0.07f, 0.82f * textAlpha);
        b.draw(game.art.white(), boxX, 26, boxW, boxH);
        b.setColor(0.5f, 0.42f, 0.29f, 0.8f * textAlpha);
        b.draw(game.art.white(), boxX, 26 + boxH - 1, boxW, 1);
        b.setColor(Color.WHITE);
        big.setColor(1, 1, 1, textAlpha);
        big.draw(b, caption == null ? "" : caption, boxX + 30, 26 + boxH - 18, boxW - 60, Align.center, true);
        Color dim = Ui.DIM;
        small.setColor(dim.r, dim.g, dim.b, textAlpha);
        small.draw(b, (index + 1) + " / " + pages.size() + "     아무 키나 누르면 다음  ·  Esc 건너뛰기",
                boxX, 26 + 30, boxW, Align.center, false);
        big.setColor(Color.WHITE);
        small.setColor(Color.WHITE);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        if (current != null) current.dispose();
        if (previous != null) previous.dispose();
        current = previous = null;
    }
}
