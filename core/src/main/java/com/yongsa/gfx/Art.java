package com.yongsa.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.model.OreType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * data/hd-art.json 의 고해상도 아트와 기존 픽셀 아틀라스를 함께 관리한다.
 * 원본 이미지 픽셀 수와 월드 크기는 별개이며, 렌더러는 width/height를 사용한다.
 */
public class Art implements Disposable {
    public static final int SCALE = 2;
    public static final String[] THEMES = {"CAMP", "LEFT", "RIGHT", "TOP"};

    private final Map<Character, Integer> palette = new HashMap<>();
    private final Map<String, String[]> sources = new LinkedHashMap<>();
    private final TextureAtlas atlas;
    private final HdArt hd;
    private final Map<String, TextureRegion> cache = new HashMap<>();
    /** 부드러운 빛 원 (입자, 불빛, 포탈). */
    public final Texture glow;
    /** 화면 가장자리를 어둡게 하는 그림. */
    public final Texture vignette;

    public Art() {
        parse(Gdx.files.internal("data/sprites.txt").readString("UTF-8"));
        PixmapPacker packer = new PixmapPacker(1024, 1024, Pixmap.Format.RGBA8888, 2, false);

        Pixmap white = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        white.setColor(Color.WHITE);
        white.fill();
        put(packer, "white", white);
        put(packer, "shadow", shadow());

        for (Map.Entry<String, String[]> e : sources.entrySet()) {
            put(packer, e.getKey(), draw(e.getValue(), null));
        }
        // 광물마다 색을 바꾼 광맥과 보석
        packOreVariant(packer, OreType.BLACKIRON, 0x5d6678ff, 0xb7c3d8ff);
        packOreVariant(packer, OreType.BLOODMOON, 0xa3182eff, 0xff7d92ff);
        packOreVariant(packer, OreType.SKY, 0x2f8fd8ff, 0xc6f0ffff);
        // 무기마다 날 색을 바꾼 검
        packSword(packer, "blackiron", "sword", 0x4b5160ff, 0x9aa3b5ff);
        packSword(packer, "bloodmoon", "sword", 0xa3182eff, 0xff7d92ff);
        packSword(packer, "skyfall", "sword", 0x2f8fd8ff, 0xbdf3ffff);
        packSword(packer, "blackiron_awakened", "sword", 0x2a2f45ff, 0x8fe3ffff);
        packSword(packer, "bloodmoon_awakened", "sword", 0xd4143aff, 0xffd0d8ff);
        packSword(packer, "skyfall_awakened", "sword", 0x6a4cffff, 0xfff6a6ff);
        Map<Character, Integer> holy = new HashMap<>();
        holy.put('w', 0xfff3b0ff);
        holy.put('W', 0xf2c94cff);
        put(packer, "sword_holy_awakened", draw(sources.get("sword_holy"), holy));

        for (String theme : THEMES) {
            for (int v = 0; v < 4; v++) put(packer, "floor_" + theme + "_v" + v, floor(theme, v));
            put(packer, "walltop_" + theme, wallTop(theme));
            put(packer, "wallfront_" + theme, wallFront(theme));
        }
        put(packer, "bars", bars());
        put(packer, "ao_top", ao(0));
        put(packer, "ao_left", ao(1));
        put(packer, "ao_right", ao(2));
        put(packer, "torch_a", torch(false));
        put(packer, "torch_b", torch(true));
        for (String theme : THEMES) {
            String[] kinds = {"a", "b", "c"};
            for (int i = 0; i < kinds.length; i++) put(packer, "decal_" + theme + "_" + kinds[i], decal(theme, i));
        }

        atlas = packer.generateTextureAtlas(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest, false);
        packer.dispose();
        hd = new HdArt();
        glow = Lighting.radial(128);
        vignette = vignetteTexture();
    }

    /** 벽 바로 옆 바닥에 지는 그늘. side 0 위쪽 벽, 1 왼쪽 벽, 2 오른쪽 벽. */
    private Pixmap ao(int side) {
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                float d = side == 0 ? y : side == 1 ? x : 15 - x;
                float a = side == 0 ? 0.5f * Math.max(0, 1f - d / 9f) : 0.3f * Math.max(0, 1f - d / 6f);
                p.drawPixel(x, y, Color.rgba8888(0, 0, 0, a));
            }
        }
        return p;
    }

    /** 벽에 거는 횃불. 불꽃 모양이 두 장 번갈아 나온다. */
    private Pixmap torch(boolean alt) {
        Pixmap p = new Pixmap(8, 16, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        int k = 0x1b1b24ff, wood = 0x6b4826ff, metal = 0x6e6e7aff, f1 = 0xff9c2aff, f2 = 0xffd75eff, f3 = 0xfff6c8ff;
        // 받침
        p.setColor(new Color(k));
        p.fillRectangle(2, 9, 4, 1);
        p.fillRectangle(3, 10, 2, 5);
        p.setColor(new Color(metal));
        p.fillRectangle(2, 8, 4, 1);
        p.setColor(new Color(wood));
        p.fillRectangle(3, 10, 2, 4);
        // 불꽃
        p.setColor(new Color(f1));
        if (alt) {
            p.fillRectangle(2, 3, 4, 5);
            p.fillRectangle(3, 1, 2, 2);
            p.drawPixel(5, 2, f1);
        } else {
            p.fillRectangle(2, 4, 4, 4);
            p.fillRectangle(3, 2, 2, 2);
            p.drawPixel(2, 3, f1);
        }
        p.setColor(new Color(f2));
        p.fillRectangle(3, alt ? 4 : 5, 2, 3);
        p.drawPixel(3, alt ? 6 : 6, f3);
        return p;
    }

    /** 바닥 장식 (자갈, 금, 풀, 뼈, 룬). 일부 바닥 칸에만 얹는다. */
    private Pixmap decal(String theme, int kind) {
        int[] c = themeColors(theme);
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        Random r = new Random(theme.hashCode() * 17L + kind);
        if (kind == 0) {
            // 자갈
            for (int i = 0; i < 3; i++) {
                int x = 3 + r.nextInt(9), y = 4 + r.nextInt(8);
                p.drawPixel(x, y, c[1]);
                p.drawPixel(x + 1, y, c[1]);
                p.drawPixel(x, y + 1, c[2]);
                p.drawPixel(x + 1, y + 1, c[3]);
            }
        } else if (kind == 1) {
            // 금
            p.setColor(new Color(c[3]));
            p.drawLine(2, 9, 6, 7);
            p.drawLine(6, 7, 9, 8);
            p.drawLine(9, 8, 13, 5);
            p.drawLine(6, 7, 7, 4);
        } else {
            switch (theme) {
                case "CAMP": {
                    // 풀
                    int g1 = 0x5f7a3aff, g2 = 0x7f9c4aff;
                    for (int i = 0; i < 4; i++) {
                        int x = 4 + i * 2 + r.nextInt(2);
                        p.drawPixel(x, 10, g1);
                        p.drawPixel(x, 9, g1);
                        p.drawPixel(x + (i % 2 == 0 ? -1 : 1), 8, g2);
                    }
                    break;
                }
                case "LEFT": {
                    // 부서진 나무 받침
                    p.setColor(new Color(0x5a4430ff));
                    p.fillRectangle(3, 8, 9, 2);
                    p.setColor(new Color(0x3c2c1fff));
                    p.drawLine(3, 10, 11, 10);
                    p.drawPixel(12, 7, 0x5a4430ff);
                    break;
                }
                case "RIGHT": {
                    // 뼈
                    int b1 = 0xd8d0bcff, b2 = 0xa8a090ff;
                    p.setColor(new Color(b1));
                    p.drawLine(4, 10, 11, 7);
                    p.drawPixel(3, 10, b1);
                    p.drawPixel(4, 11, b1);
                    p.drawPixel(12, 6, b1);
                    p.drawPixel(12, 7, b1);
                    p.drawPixel(5, 11, b2);
                    p.drawPixel(10, 8, b2);
                    break;
                }
                default: {
                    // 희미한 룬
                    int v = 0x6a55a0ff;
                    p.setColor(new Color(v));
                    p.drawLine(5, 5, 10, 5);
                    p.drawLine(7, 5, 7, 11);
                    p.drawLine(5, 11, 10, 8);
                    break;
                }
            }
        }
        return p;
    }

    private static Texture vignetteTexture() {
        int w = 128, h = 72;
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = (x - (w - 1) / 2f) / (w / 2f), dy = (y - (h - 1) / 2f) / (h / 2f);
                float d = (float) Math.sqrt(dx * dx * 0.8f + dy * dy);
                float a = Math.max(0f, Math.min(1f, (d - 0.55f) / 0.6f));
                p.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a * a));
            }
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return t;
    }

    /** 덮어쓴 그림 이름 (assets/sprites 폴더에서 읽은 것). */
    public final java.util.List<String> overridden = new java.util.ArrayList<>();

    /**
     * assets/sprites/이름.png 가 있으면 코드·sprites.txt 로 만든 그림 대신 그 파일을 쓴다.
     * 디자인을 바꿀 때 코드를 고치지 않고 그림 파일만 넣으면 되게 하려는 것이다.
     * 이 경로는 기존 저해상도 오버라이드용이다. 새 고해상도 그림은 hd-art.json에 등록한다.
     */
    private void put(PixmapPacker packer, String name, Pixmap generated) {
        FileHandle f = Gdx.files.internal("sprites/" + name + ".png");
        if (!f.exists()) {
            packer.pack(name, generated);
            generated.dispose();
            return;
        }
        Pixmap custom = new Pixmap(f);
        if (custom.getWidth() != generated.getWidth() || custom.getHeight() != generated.getHeight()) {
            Gdx.app.log("Art", name + ".png 크기가 " + custom.getWidth() + "x" + custom.getHeight()
                    + " 입니다. 원래 크기 " + generated.getWidth() + "x" + generated.getHeight() + " 와 다르면 화면에서 크기가 달라집니다.");
        }
        packer.pack(name, custom);
        overridden.add(name);
        custom.dispose();
        generated.dispose();
    }

    private void parse(String text) {
        String name = null;
        java.util.List<String> rows = new java.util.ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.replace("\r", "");
            if (line.startsWith("#")) continue;
            if (line.startsWith("palette ")) {
                String[] p = line.split(" ");
                palette.put(p[1].charAt(0), (int) Long.parseLong(p[2], 16));
                continue;
            }
            if (line.startsWith("sprite ")) {
                if (name != null) sources.put(name, rows.toArray(new String[0]));
                name = line.split(" ")[1];
                rows.clear();
                continue;
            }
            if (name != null && !line.isEmpty()) rows.add(line);
        }
        if (name != null) sources.put(name, rows.toArray(new String[0]));
    }

    private Pixmap draw(String[] rows, Map<Character, Integer> override) {
        int w = rows[0].length(), h = rows.length;
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char c = x < rows[y].length() ? rows[y].charAt(x) : '.';
                Integer col = override != null && override.containsKey(c) ? override.get(c) : palette.get(c);
                p.drawPixel(x, y, col == null ? 0 : col);
            }
        }
        return p;
    }

    private void packOreVariant(PixmapPacker packer, OreType ore, int dark, int light) {
        Map<Character, Integer> m = new HashMap<>();
        m.put('o', dark);
        m.put('O', light);
        put(packer, "vein_" + ore.name(), draw(sources.get("vein"), m));
        put(packer, "gem_" + ore.name(), draw(sources.get("gem"), m));
    }

    private void packSword(PixmapPacker packer, String name, String base, int dark, int light) {
        Map<Character, Integer> m = new HashMap<>();
        m.put('o', dark);
        m.put('O', light);
        put(packer, "sword_" + name, draw(sources.get(base), m));
    }

    private Pixmap shadow() {
        Pixmap p = new Pixmap(96, 48, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < 48; y++) for (int x = 0; x < 96; x++) {
            float dx = (x - 47.5f) / 48, dy = (y - 23.5f) / 24;
            float alpha = 0.42f * (float)Math.pow(Math.max(0, 1 - dx * dx - dy * dy), 2);
            p.drawPixel(x, y, Color.rgba8888(0, 0, 0, alpha));
        }
        return p;
    }

    private static int[] themeColors(String theme) {
        switch (theme) {
            case "CAMP":  return new int[]{0x6b5a45ff, 0x7a684fff, 0x594a39ff, 0x3a2f28ff, 0x5b4636ff, 0x47372bff};
            case "LEFT":  return new int[]{0x4d4440ff, 0x5a504bff, 0x3e3633ff, 0x2a2523ff, 0x4a403aff, 0x37302bff};
            case "RIGHT": return new int[]{0x4a2c30ff, 0x56343aff, 0x3b2226ff, 0x241215ff, 0x5c2a2fff, 0x431d22ff};
            default:      return new int[]{0x3b3350ff, 0x463d5eff, 0x2f2842ff, 0x1d182cff, 0x4a3d6bff, 0x362c52ff};
        }
    }

    private Pixmap floor(String theme, int variant) {
        int[] c = themeColors(theme);
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        p.setColor(new Color(c[0]));
        p.fill();
        Random r = new Random(theme.hashCode() * 31L + variant);
        for (int i = 0; i < 14; i++) {
            p.drawPixel(r.nextInt(16), r.nextInt(16), r.nextBoolean() ? c[1] : c[2]);
        }
        if (theme.equals("RIGHT") || theme.equals("TOP")) {
            // 돌바닥 이음매
            p.setColor(new Color(c[2]));
            p.drawLine(0, 15, 15, 15);
            p.drawLine(variant % 2 == 0 ? 7 : 3, 8, variant % 2 == 0 ? 7 : 3, 15);
            p.drawLine(0, 7, 15, 7);
            p.drawLine(variant % 2 == 0 ? 11 : 13, 0, variant % 2 == 0 ? 11 : 13, 7);
        } else if (variant == 3) {
            p.setColor(new Color(c[2]));
            p.drawLine(3, 4, 7, 6);
            p.drawLine(7, 6, 9, 5);
        }
        return p;
    }

    private Pixmap wallTop(String theme) {
        int[] c = themeColors(theme);
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        p.setColor(new Color(c[3]));
        p.fill();
        Random r = new Random(theme.hashCode());
        for (int i = 0; i < 6; i++) p.drawPixel(r.nextInt(16), r.nextInt(16), c[5]);
        return p;
    }

    private Pixmap wallFront(String theme) {
        int[] c = themeColors(theme);
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        p.setColor(new Color(c[3]));
        p.fill();
        p.setColor(new Color(c[4]));
        p.fillRectangle(0, 4, 16, 12);
        p.setColor(new Color(c[5]));
        for (int row = 0; row < 3; row++) {
            int y = 4 + row * 4;
            p.drawLine(0, y, 15, y);
            int off = row % 2 == 0 ? 0 : 4;
            for (int x = off; x < 16; x += 8) p.drawLine(x, y, x, y + 3);
        }
        p.setColor(0, 0, 0, 0.35f);
        p.drawLine(0, 15, 15, 15);
        return p;
    }

    private Pixmap bars() {
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0x2a2a33ff));
        p.fillRectangle(0, 1, 16, 2);
        p.fillRectangle(0, 13, 16, 2);
        p.setColor(new Color(0x8a8f9cff));
        for (int x = 1; x < 16; x += 4) p.fillRectangle(x, 0, 2, 16);
        p.setColor(new Color(0xc9ced8ff));
        for (int x = 1; x < 16; x += 4) p.drawLine(x, 0, x, 15);
        return p;
    }

    public TextureRegion get(String name) {
        TextureRegion highResolution = hd.get(name);
        if (highResolution != null) return highResolution;
        TextureRegion r = cache.get(name);
        if (r == null) {
            r = atlas.findRegion(name);
            if (r == null) throw new IllegalArgumentException("없는 그림: " + name);
            cache.put(name, r);
        }
        return r;
    }

    public TextureRegion white() {
        return get("white");
    }

    /** Gameplay uses the simplified set; menus keep the detailed illustrations. */
    public TextureRegion world(String name) {
        TextureRegion r = hd.get("world_" + name);
        return r != null ? r : get(name);
    }

    public TextureRegion vein(OreType t) {
        return get("vein_" + t.name());
    }

    public TextureRegion gem(OreType t) {
        return get("gem_" + t.name());
    }

    /** 무기 그림. 성검은 봉인 여부, 다른 검은 각성 여부에 따라 다르다. */
    public TextureRegion sword(String defId, boolean awakened) {
        return get(HeroAtlasData.swordKey(defId, awakened));
    }

    public TextureRegion worldSword(String defId, boolean awakened) {
        return world(HeroAtlasData.swordKey(defId, awakened));
    }

    /** 이 바닥 칸에 얹을 장식 (없으면 null). 칸 위치로 정해져서 매번 같은 자리에 나온다. */
    public TextureRegion decal(String theme, int x, int y) {
        if (hd.contains("floor_" + theme + "_hd0")) return null;
        int h = (x * 73856093) ^ (y * 19349663);
        h = Math.floorMod(h, 100);
        if (h >= 9) return null;
        return get("decal_" + theme + "_" + (h < 4 ? "a" : h < 7 ? "b" : "c"));
    }

    public TextureRegion floor(String theme, int x, int y) {
        if (hd.contains("floor_" + theme + "_hd0"))
            return world("floor_" + theme + "_hd" + (Math.floorMod(y, 4) * 4 + Math.floorMod(x, 4)));
        int v = Math.floorMod(x * 7 + y * 13 + (x * y) % 5, 9);
        return get("floor_" + theme + "_v" + (v < 6 ? 0 : v - 5));
    }

    private final Map<String, Texture> extra = new java.util.HashMap<>();
    private final Map<String, TextureRegion[]> extraFrames = new java.util.HashMap<>();
    private HeroMotionAtlas heroMotion;
    private boolean heroMotionChecked;

    /** Optional measured atlas. The legacy property supports same-build visual comparisons. */
    public TextureRegion heroMotion(String action, com.yongsa.combat.Facing facing, int frame) {
        loadHeroMotion();
        return heroMotion == null ? null : heroMotion.frame(action, facing, frame);
    }

    /** Walk pose order for this facing; the legacy sheets play all six poses. */
    public int[] heroWalkCycle(com.yongsa.combat.Facing facing) {
        loadHeroMotion();
        return heroMotion == null ? HeroMotion.DEFAULT_WALK_CYCLE : heroMotion.walkCycle(facing);
    }

    public HeroSword heroSword(String defId, boolean awakened) {
        loadHeroMotion();
        return heroMotion == null ? null : heroMotion.sword(HeroAtlasData.swordKey(defId, awakened));
    }

    private void loadHeroMotion() {
        if (!heroMotionChecked) {
            heroMotionChecked = true;
            FileHandle manifest = Gdx.files.internal(HeroMotionAtlas.MANIFEST);
            if (!"legacy".equals(System.getProperty("yongsa.heroArt")) && manifest.exists())
                heroMotion = new HeroMotionAtlas(manifest);
        }
    }

    /**
     * 따로 불러오는 그림 (assets/ 아래 경로, 예: team/sunwoo/chest_left.png). 팀원 버전에서 가져온 그림처럼
     * sprites.txt·hd-art.json 에 없는 것들. 파일이 없으면 null.
     */
    public Texture tex(String path) {
        if (extra.containsKey(path)) return extra.get(path);
        FileHandle f = Gdx.files.internal(path);
        Texture t = null;
        if (f.exists()) {
            t = new Texture(f);
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        extra.put(path, t);
        return t;
    }

    /** 가로로 frames 장 이어 붙인 그림의 index 번째 칸. 파일이 없으면 null. */
    public TextureRegion frame(String path, int frames, int index) {
        TextureRegion[] fr = extraFrames.get(path);
        if (fr == null) {
            Texture t = tex(path);
            if (t == null) return null;
            fr = new TextureRegion[frames];
            int w = t.getWidth() / frames;
            for (int i = 0; i < frames; i++) fr[i] = new TextureRegion(t, i * w, 0, w, t.getHeight());
            extraFrames.put(path, fr);
        }
        return fr[Math.floorMod(index, fr.length)];
    }

    /**
     * 칸으로 나눈 시트(cols x rows)의 index 번째 칸 (왼쪽 위부터 가로로). 줄여 그리는 큰 그림이라
     * 부드럽게 거른다 (밉맵). 파일이 없으면 null.
     */
    public TextureRegion cell(String path, int cols, int rows, int index) {
        String key = path + "#grid";
        TextureRegion[] fr = extraFrames.get(key);
        if (fr == null) {
            FileHandle f = Gdx.files.internal(path);
            if (!f.exists()) return null;
            Texture t = new Texture(f, true);
            t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            extra.put(key, t);
            fr = new TextureRegion[cols * rows];
            int w = t.getWidth() / cols, h = t.getHeight() / rows;
            for (int i = 0; i < fr.length; i++) fr[i] = new TextureRegion(t, i % cols * w, i / cols * h, w, h);
            extraFrames.put(key, fr);
        }
        return fr[Math.floorMod(index, fr.length)];
    }

    @Override
    public void dispose() {
        if (heroMotion != null) heroMotion.dispose();
        for (Texture t : extra.values()) if (t != null) t.dispose();
        hd.dispose();
        atlas.dispose();
        glow.dispose();
        vignette.dispose();
    }

    public float width(TextureRegion region) { return hd.width(region); }
    public float height(TextureRegion region) { return hd.height(region); }
    public boolean hasHd(String name) { return hd.contains(name); }
}
