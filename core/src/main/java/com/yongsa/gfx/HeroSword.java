package com.yongsa.gfx;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Weapon artwork with its measured pivot; texture lifetime belongs to HeroMotionAtlas. */
public final class HeroSword extends TextureRegion {
    public final HeroAtlasData.Sword data;

    HeroSword(Texture texture, HeroAtlasData.Sword data) {
        super(texture);
        this.data = data;
    }
}
