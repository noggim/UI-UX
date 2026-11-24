package com.yongsa.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.combat.Facing;
import java.util.HashMap;
import java.util.Map;

/** Loads native-size artwork once. Frames share ground anchors, not the generator's uneven grid. */
public final class HeroMotionAtlas implements Disposable {
    public static final String MANIFEST = "data/hero-motion.json";
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, HeroPose[][]> clips = new HashMap<>();
    private final Map<String, HeroSword> swords = new HashMap<>();
    private final int[][] walkCycle;

    public HeroMotionAtlas(FileHandle manifest) {
        HeroAtlasData data = new HeroAtlasData(manifest.readString("UTF-8"));
        walkCycle = data.walkCycle;
        try {
            for (var entry : data.sources.entrySet()) {
                var source = entry.getValue();
                Texture texture = new Texture(Gdx.files.internal(source.path()), true);
                textures.put(entry.getKey(), texture);
                if (texture.getWidth()!=source.width() || texture.getHeight()!=source.height())
                    throw new IllegalArgumentException("Hero source dimensions changed: " + source.path());
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            }
            for (var entry : data.clips.entrySet()) {
                HeroPose[][] frames = new HeroPose[5][6];
                for (int r=0; r<5; r++) for (int c=0; c<6; c++) {
                    var frame = entry.getValue()[r][c];
                    frames[r][c] = new HeroPose(textures.get(frame.source()), frame);
                }
                clips.put(entry.getKey(), frames);
            }
            for (var entry : data.swords.entrySet()) {
                var sword = entry.getValue();
                Texture texture = new Texture(Gdx.files.internal(sword.path()), true);
                textures.put(entry.getKey(), texture);
                if (texture.getWidth()!=sword.width() || texture.getHeight()!=sword.height())
                    throw new IllegalArgumentException("Sword source dimensions changed: " + sword.path());
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                swords.put(entry.getKey(), new HeroSword(texture, sword));
            }
        } catch (RuntimeException e) { dispose(); throw e; }
    }

    public HeroSword sword(String key) { return swords.get(key); }

    public int[] walkCycle(Facing facing) { return walkCycle[HeroAtlasData.row(facing)]; }

    public HeroPose frame(String action, Facing facing, int frame) {
        HeroPose[][] clip = clips.get(action);
        if (clip == null) throw new IllegalArgumentException("Unknown hero motion: " + action);
        return clip[HeroAtlasData.row(facing)][Math.floorMod(frame, 6)];
    }

    @Override public void dispose() { textures.values().forEach(Texture::dispose); textures.clear(); }
}
