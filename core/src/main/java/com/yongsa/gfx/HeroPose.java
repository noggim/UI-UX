package com.yongsa.gfx;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** A source crop drawn in a common virtual canvas; the texture's pixels remain untouched. */
public final class HeroPose extends TextureRegion {
    public final float left, top, width, height;
    public final HeroAtlasData.Frame frame;

    HeroPose(Texture texture, HeroAtlasData.Frame frame) {
        super(texture, frame.x(), frame.y(), frame.width(), frame.height());
        this.frame = frame;
        float[] p = frame.placement();
        left=p[0]; top=p[1]; width=p[2]; height=p[3];
    }
}
