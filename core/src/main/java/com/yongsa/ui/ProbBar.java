package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;

/** 강화 결과 하나의 확률 막대. 0% 도 회색으로 남긴다 (FR-대장간-10). */
public class ProbBar extends Widget {
    private final String name;
    private final double percent;
    private final Color color;
    private final BitmapFont font;
    private final TextureRegion white;
    private final GlyphLayout layout = new GlyphLayout();

    public ProbBar(String name, double percent, Color color, BitmapFont font, TextureRegion white) {
        this.name = name;
        this.percent = percent;
        this.color = color;
        this.font = font;
        this.white = white;
    }

    @Override
    public float getPrefWidth() {
        return 420;
    }

    @Override
    public float getPrefHeight() {
        return 28;
    }

    public static String format(double p) {
        if (Math.abs(p - Math.round(p)) < 1e-9) return String.valueOf(Math.round(p));
        String s = String.format(java.util.Locale.ROOT, "%.2f", p);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        boolean zero = percent <= 0;
        Color c = zero ? Ui.GREY : color;
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        font.setColor(zero ? Ui.DIM : Ui.TEXT);
        font.draw(batch, name, x, y + h - 5);
        float bx = x + 60, bw = w - 140;
        batch.setColor(0.1f, 0.09f, 0.13f, parentAlpha);
        batch.draw(white, bx, y + 5, bw, h - 10);
        batch.setColor(c.r, c.g, c.b, parentAlpha);
        batch.draw(white, bx, y + 5, (float) (bw * percent / 100.0), h - 10);
        batch.setColor(Color.WHITE);
        String t = format(percent) + "%";
        layout.setText(font, t);
        font.setColor(zero ? Ui.DIM : c);
        font.draw(batch, t, x + w - layout.width, y + h - 5);
        font.setColor(Color.WHITE);
    }
}
