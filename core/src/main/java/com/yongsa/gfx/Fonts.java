package com.yongsa.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.Disposable;

/**
 * 한국어 글꼴 (나눔스퀘어라운드, OFL). 글자는 처음 쓸 때 만들어 붙인다(incremental).
 * 화면 글자는 1280×720 기준 최소 16픽셀 (NFR-10).
 */
public class Fonts implements Disposable {
    public final BitmapFont small, normal, large, title;
    private final FreeTypeFontGenerator generator;

    public Fonts() {
        generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NanumSquareRoundB.ttf"));
        small = make(16, 0);
        normal = make(20, 0);
        large = make(28, 0);
        title = make(48, 1);
    }

    private BitmapFont make(int size, int border) {
        FreeTypeFontParameter p = new FreeTypeFontParameter();
        p.size = size;
        p.incremental = true;
        p.borderWidth = border;
        p.borderColor = new com.badlogic.gdx.graphics.Color(0, 0, 0, 0.85f);
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        p.packer = new PixmapPacker(1024, 1024, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, 2, false);
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS;
        BitmapFont f = generator.generateFont(p);
        f.setUseIntegerPositions(true);
        return f;
    }

    @Override
    public void dispose() {
        small.dispose();
        normal.dispose();
        large.dispose();
        title.dispose();
        generator.dispose();
    }
}
