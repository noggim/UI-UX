package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.yongsa.model.EnhanceService;
import com.yongsa.model.GameState;

/**
 * 대장간 머리줄의 강화 숙련도 표시: "강화 숙련도 Lv 7" + 다음 레벨까지 경험치 막대.
 * 숙련도는 모든 검이 함께 쓰므로 어느 탭에서나 보인다. 마스터면 막대를 금색으로 채운다.
 */
public class MasteryBar extends Widget {
    private final EnhanceService es;
    private final GameState s;
    private final BitmapFont font;
    private final TextureRegion white;
    private final GlyphLayout layout = new GlyphLayout();

    public MasteryBar(EnhanceService es, GameState s, BitmapFont font, TextureRegion white) {
        this.es = es;
        this.s = s;
        this.font = font;
        this.white = white;
    }

    @Override public float getPrefWidth() { return 300; }
    @Override public float getPrefHeight() { return 40; }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean master = es.masteryMaxed(s);
        int need = es.masteryNeed(s.masteryLevel);
        float ratio = master ? 1f : need <= 0 ? 0f : Math.min(1f, s.masteryExp / (float) need);

        String title = master ? "강화 숙련도 MASTER" : "강화 숙련도 Lv " + s.masteryLevel;
        String right = master ? "Lv " + s.masteryLevel : s.masteryExp + " / " + need;
        font.setColor(master ? Ui.GOLD : Ui.TEXT);
        font.draw(batch, title, x, y + h - 2);
        layout.setText(font, right);
        font.setColor(Ui.DIM);
        font.draw(batch, right, x + w - layout.width, y + h - 2);

        float by = y + 2, bh = 9;
        batch.setColor(0.1f, 0.09f, 0.13f, parentAlpha);
        batch.draw(white, x, by, w, bh);
        Color c = master ? Ui.GOLD : Ui.PURPLE;
        batch.setColor(c.r, c.g, c.b, parentAlpha);
        batch.draw(white, x, by, w * ratio, bh);
        batch.setColor(Color.WHITE);
        font.setColor(Color.WHITE);
    }
}
