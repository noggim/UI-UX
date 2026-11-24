package com.yongsa.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Original-resolution sheets; UV regions and world dimensions are independent. */
public final class HdArt implements Disposable {
    public static final class Entry {
        public String sheet;
        public int x, y, width, height;
        public float drawWidth, drawHeight;
        /** Blend mip levels while keeping texels crisp inside each level for detailed pixel art. */
        public boolean pixelMip;

        public void validate(int sheetWidth, int sheetHeight) {
            if (sheet == null || x < 0 || y < 0 || width <= 0 || height <= 0
                    || x + width > sheetWidth || y + height > sheetHeight
                    || !Float.isFinite(drawWidth) || !Float.isFinite(drawHeight)
                    || drawWidth <= 0 || drawHeight <= 0)
                throw new IllegalArgumentException("Invalid HD sprite bounds: " + sheet);
        }
    }
    public static final class Manifest { public Map<String, Entry> sprites; }
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, TextureRegion> regions = new HashMap<>();
    private final Map<TextureRegion, Entry> metrics = new IdentityHashMap<>();

    public HdArt() {
        if (!Gdx.files.internal("data/hd-art.json").exists()) return;
        Manifest manifest = new Gson().fromJson(Gdx.files.internal("data/hd-art.json").readString("UTF-8"), Manifest.class);
        for (Map.Entry<String, Entry> item : manifest.sprites.entrySet()) {
            Entry e = item.getValue();
            Texture texture = textures.computeIfAbsent(e.sheet, path -> {
                boolean world = path.startsWith("world/");
                Texture t = new Texture(Gdx.files.internal(path), !world || e.pixelMip);
                t.setFilter(world ? (e.pixelMip ? Texture.TextureFilter.MipMapNearestLinear : Texture.TextureFilter.Nearest)
                                : Texture.TextureFilter.MipMapLinearLinear,
                        world ? Texture.TextureFilter.Nearest : Texture.TextureFilter.Linear);
                return t;
            });
            e.validate(texture.getWidth(), texture.getHeight());
            TextureRegion region = new TextureRegion(texture, e.x, e.y, e.width, e.height);
            regions.put(item.getKey(), region);
            metrics.put(region, e);
        }
    }
    public TextureRegion get(String name) { return regions.get(name); }
    public boolean contains(String name) { return regions.containsKey(name); }
    public float width(TextureRegion region) {
        Entry e = metrics.get(region);
        return e == null ? region.getRegionWidth() : e.drawWidth;
    }
    public float height(TextureRegion region) {
        Entry e = metrics.get(region);
        return e == null ? region.getRegionHeight() : e.drawHeight;
    }
    @Override public void dispose() { textures.values().forEach(Texture::dispose); }
}
