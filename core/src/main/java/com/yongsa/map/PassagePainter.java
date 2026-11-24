package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.map.Corridor;
import com.yongsa.map.Room;
import com.yongsa.map.WorldMap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Intact authored passage scenes; legacy tile maps retain the repeating-wall fallback. */
class PassagePainter implements Disposable {
    private static final String DIR = "scenes/passages/";
    /** 그림 속 바닥 폭 (칸). 걸을 수 있는 폭 3칸보다 조금 넓게 그려서 벽 위를 걷는 것처럼 보이지 않게 한다. */
    private static final float FLOOR_TILES = 3.4f;
    private static final float[] EDGE_ALPHA = {0, 1, 1, 0};
    private static final float GROUND_TINT = 0.58f;

    /**
     * 테마별 그림 이름과 바닥 띠 위치 (그림 크기 대비 0~1):
     * 가로 그림은 위에서부터 바닥 띠 시작·끝, 세로 그림은 왼쪽에서부터 시작·끝. 그림을 보고 잰 값이다.
     */
    private static final Object[][] DEFS = {
            {"CAMP", "camp", 0.28f, 0.68f, 0.27f, 0.70f},
            {"LEFT", "mine", 0.30f, 0.70f, 0.25f, 0.72f},
            {"RIGHT", "citadel", 0.30f, 0.72f, 0.23f, 0.77f},
            {"TOP", "tower", 0.30f, 0.72f, 0.30f, 0.70f},
    };

    private static final class Skin {
        Texture h, v, floor;
        int hw, hh, vw, vh;
        float hTop, hBot, vL, vR;
    }

    /** 통로의 곧은 구간. */
    private static final class Seg {
        boolean horizontal;
        /** 가운데 줄 좌표 (월드 픽셀): 가로면 y, 세로면 x. */
        float c;
        /** 길이 방향 범위 (월드 픽셀, a < b). */
        float a, b;
        /** 바닥 띠 범위: 방 출구 쪽은 방 그림 바닥이 끝나는 곳까지. */
        float fa, fb;
        /** 양 끝이 방 출구인가 (아니면 꺾이는 곳). */
        boolean mouthA, mouthB;
        String theme;
        float[] floorAlong, floorAcross;
    }

    private Texture ground;
    private final WorldMap map;
    private final float T;
    private final Map<String, Skin> skins = new HashMap<>();
    private final List<Seg> segs = new ArrayList<>();
    private final float[] verts = new float[20];
    private final Map<String, Texture> paintings = new HashMap<>();
    private final boolean painted;
    final boolean active;
    final PaintedJoints joints;

    PassagePainter(WorldMap map, float tileSize) {
        this.map = map;
        this.T = tileSize;
        joints = new PaintedJoints(map);
        painted = !map.corridors.isEmpty() && map.corridors.stream().allMatch(c -> c.scene != null);
        if (painted) {
            active = true;
            ground = repeating(Gdx.files.internal(DIR + "terrain-bedrock.png"));
            return;
        }
        boolean all = true;
        for (Object[] d : DEFS) {
            String n = (String) d[1];
            all &= Gdx.files.internal(DIR + n + "-h.png").exists() && Gdx.files.internal(DIR + n + "-v.png").exists()
                    && Gdx.files.internal(DIR + "floor-" + (n.equals("camp") ? "mine" : n) + ".png").exists();
        }
        active = all && Gdx.files.internal(DIR + "terrain-bedrock.png").exists();
        if (!active) return;
        for (Object[] d : DEFS) skins.put((String) d[0], load(d));
        for (Corridor c : map.corridors) buildSegs(c);
        ground = repeating(Gdx.files.internal(DIR + "terrain-bedrock.png"));
    }

    // ───────────────────────── 불러오기

    private static Skin load(Object[] d) {
        String n = (String) d[1];
        Skin s = new Skin();
        s.hTop = (Float) d[2];
        s.hBot = (Float) d[3];
        s.vL = (Float) d[4];
        s.vR = (Float) d[5];
        Pixmap h = rgba(Gdx.files.internal(DIR + n + "-h.png"));
        s.hw = h.getWidth();
        s.hh = h.getHeight();
        featherAcross(h, true, 0.14f);
        s.h = texture(h, true, false);

        Pixmap v = rgba(Gdx.files.internal(DIR + n + "-v.png"));
        s.vw = v.getWidth();
        s.vh = v.getHeight();
        featherAcross(v, false, 0.14f);
        s.v = texture(v, false, true);

        s.floor = repeating(Gdx.files.internal(DIR + "floor-" + (n.equals("camp") ? "mine" : n) + ".png"));
        return s;
    }

    /** Own and dispose the intermediate CPU pixmap after uploading it. */
    private static Texture repeating(FileHandle file) {
        Texture t = texture(seamless(rgba(file)), false, false);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        return t;
    }

    private static Pixmap rgba(FileHandle f) {
        return rgba(new Pixmap(f));
    }

    private static Pixmap rgba(Pixmap p) {
        if (p.getFormat() == Pixmap.Format.RGBA8888) return p;
        Pixmap q = new Pixmap(p.getWidth(), p.getHeight(), Pixmap.Format.RGBA8888);
        q.setBlending(Pixmap.Blending.None);
        q.drawPixmap(p, 0, 0);
        p.dispose();
        return q;
    }

    /** 거울 반복 텍스처 (반복하는 방향만), 밉맵. pixmap 은 여기서 버린다. */
    private static Texture texture(Pixmap p, boolean repeatU, boolean repeatV) {
        Texture t = new Texture(p, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        t.setWrap(repeatU ? Texture.TextureWrap.MirroredRepeat : Texture.TextureWrap.ClampToEdge,
                repeatV ? Texture.TextureWrap.MirroredRepeat : Texture.TextureWrap.ClampToEdge);
        p.dispose();
        return t;
    }

    /** 길 폭 방향 바깥 가장자리(벽 바깥쪽)를 투명하게 흐려서 땅과 이어지게 한다. */
    private static void featherAcross(Pixmap p, boolean horizontal, float frac) {
        int w = p.getWidth(), h = p.getHeight(), n = horizontal ? h : w;
        int f = Math.max(1, Math.round(n * frac));
        ByteBuffer px = p.getPixels();
        for (int i = 0; i < f; i++) {
            float a = (i + 0.5f) / f;
            a = a * a * (3 - 2 * a);
            scaleAlpha(px, w, h, horizontal, i, a);
            scaleAlpha(px, w, h, horizontal, n - 1 - i, a);
        }
    }

    private static void scaleAlpha(ByteBuffer px, int w, int h, boolean row, int line, float a) {
        int count = row ? w : h;
        for (int k = 0; k < count; k++) {
            int x = row ? k : line, y = row ? line : k;
            int i = (y * w + x) * 4 + 3;
            px.put(i, (byte) Math.round((px.get(i) & 0xff) * a));
        }
    }

    /**
     * 땅 그림을 이음매 없이 반복되게: 반 칸 밀어 놓은 같은 그림과 섞는데, 가장자리로 갈수록 밀어 놓은 쪽 비중을 키운다.
     * 가장자리에서는 밀어 놓은 그림(그 자리에서 이어지는 안쪽 무늬)만 남아 맞은편 가장자리와 이어진다.
     * 거울 반복보다 대칭 무늬가 덜 보인다. 받은 pixmap 은 버리고 새 것을 돌려준다.
     */
    static Pixmap seamless(Pixmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Pixmap out = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        ByteBuffer a = src.getPixels(), o = out.getPixels();
        for (int y = 0; y < h; y++) {
            int sy = (y + h / 2) % h;
            float wy = edge(y, h), wsy = edge(sy, h);
            for (int x = 0; x < w; x++) {
                int sx = (x + w / 2) % w;
                float w1 = edge(x, w) * wy, w2 = edge(sx, w) * wsy;
                float k = w1 + w2 <= 0 ? 0.5f : w2 / (w1 + w2);
                int i = (y * w + x) * 4, j = (sy * w + sx) * 4;
                for (int c = 0; c < 3; c++) {
                    float v = (a.get(i + c) & 0xff) * (1 - k) + (a.get(j + c) & 0xff) * k;
                    o.put(i + c, (byte) Math.round(v));
                }
                o.put(i + 3, (byte) 255);
            }
        }
        src.dispose();
        return out;
    }

    /** 가장자리에서 0, 가운데에서 1. */
    private static float edge(int i, int n) {
        float d = Math.min(i + 0.5f, n - i - 0.5f) / (n / 2f);
        return d * d;
    }

    /** 방 그림 테두리를 투명하게 흐린다 (가로·세로 모두 그림 짧은 변의 8%). 받은 pixmap 은 버리고 새 것을 돌려준다. */
    static Pixmap featherEdges(Pixmap src) {
        Pixmap p = rgba(src);
        int w = p.getWidth(), h = p.getHeight();
        int f = Math.max(2, Math.round(Math.min(w, h) * 0.08f));
        ByteBuffer px = p.getPixels();
        for (int i = 0; i < f; i++) {
            float a = (i + 0.5f) / f;
            a = a * a * (3 - 2 * a);
            scaleAlpha(px, w, h, true, i, a);
            scaleAlpha(px, w, h, true, h - 1 - i, a);
            scaleAlpha(px, w, h, false, i, a);
            scaleAlpha(px, w, h, false, w - 1 - i, a);
        }
        return p;
    }

    // ───────────────────────── 통로 구간

    private void buildSegs(Corridor c) {
        List<int[]> p = c.path;
        if (p.size() < 2) return;
        int fa = firstOpen(p, false), fb = firstOpen(p, true);
        int start = 0;
        while (start < p.size() - 1) {
            int dx = Integer.signum(p.get(start + 1)[0] - p.get(start)[0]);
            int dy = Integer.signum(p.get(start + 1)[1] - p.get(start)[1]);
            int end = start + 1;
            while (end + 1 < p.size() && p.get(end + 1)[0] - p.get(end)[0] == dx && p.get(end + 1)[1] - p.get(end)[1] == dy) end++;
            Seg s = new Seg();
            s.theme = c.theme;
            s.horizontal = dy == 0;
            int[] ps = p.get(start), pe = p.get(end);
            s.c = ((s.horizontal ? ps[1] : ps[0]) + 0.5f) * T;
            float u0 = ((s.horizontal ? ps[0] : ps[1]) + 0.5f) * T, u1 = ((s.horizontal ? pe[0] : pe[1]) + 0.5f) * T;
            boolean firstSeg = start == 0, lastSeg = end == p.size() - 1;
            // 바닥 띠 시작·끝: 출구 쪽이면 방 그림 바닥이 끝나는 칸, 꺾이는 곳이면 그 칸 가운데
            float f0 = u0, f1 = u1;
            if (firstSeg) f0 = along(p.get(Math.min(fa, end)), s.horizontal);
            if (lastSeg) f1 = along(p.get(Math.max(fb, start)), s.horizontal);
            s.mouthA = firstSeg;
            s.mouthB = lastSeg;
            if (u0 <= u1) {
                s.a = u0; s.b = u1; s.fa = f0; s.fb = f1;
            } else {
                s.a = u1; s.b = u0; s.fa = f1; s.fb = f0;
                boolean t = s.mouthA; s.mouthA = s.mouthB; s.mouthB = t;
            }
            float floorA = s.mouthA ? s.fa - .5f * T : s.a - 1.5f * T;
            float floorB = s.mouthB ? s.fb + .5f * T : s.b + 1.5f * T;
            float fadeA = s.mouthA ? 1.5f * T : .2f * T;
            float fadeB = s.mouthB ? 1.5f * T : .2f * T;
            s.floorAlong = new float[]{floorA - fadeA, floorA, floorB, floorB + fadeB};
            s.floorAcross = new float[]{s.c - 1.9f * T, s.c - .95f * T, s.c + .95f * T, s.c + 1.9f * T};
            segs.add(s);
            start = end;
        }
    }

    private float along(int[] cell, boolean horizontal) {
        return ((horizontal ? cell[0] : cell[1]) + 0.5f) * T;
    }

    /** 방 그림 바닥이 끝나고 통로 칸이 시작되는 가운데 줄 칸 (끝에서부터 찾으면 fromEnd). */
    private int firstOpen(List<int[]> p, boolean fromEnd) {
        int n = p.size();
        for (int k = 0; k < n; k++) {
            int i = fromEnd ? n - 1 - k : k;
            int[] c = p.get(i);
            if (map.tile(c[0], c[1]) == WorldMap.CORRIDOR) return i;
        }
        return fromEnd ? n - 1 : 0;
    }

    /** A shared bedrock material avoids decorative floor islands outside the collision map. */
    void drawGround(SpriteBatch batch, float x0, float y0, float x1, float y1) {
        float repeat = 32f * T;
        quad(batch, ground, x0, y0, x1, y1, x0 / repeat, -y0 / repeat,
                x1 / repeat, -y1 / repeat, GROUND_TINT, 1, 1, 1, 1);
    }

    /** Side walls stop short of turns, leaving the entire collision width visibly clear. */
    void drawWalls(SpriteBatch batch, float x0, float y0, float x1, float y1) {
        if (painted) {
            batch.setColor(Color.WHITE);
            for (Corridor c : map.corridors) {
                if (c.originX > x1 || c.originX + c.widthPx < x0 || c.originY > y1 || c.originY + c.heightPx < y0) continue;
                Texture t = paintings.get(c.scene.id);
                if (t == null) {
                    t = texture(featherPassage(new Pixmap(Gdx.files.internal(c.scene.image)), c), false, false);
                    paintings.put(c.scene.id, t);
                }
                batch.draw(t, c.originX, c.originY, c.widthPx, c.heightPx, 0, 0, t.getWidth(), t.getHeight(), c.flip, false);
            }
            return;
        }
        for (Seg s : segs) {
            Skin k = skins.getOrDefault(s.theme, skins.get("CAMP"));
            float a = s.mouthA ? s.a : s.a + 1.7f * T;
            float b = s.mouthB ? s.b : s.b - 1.7f * T;
            if (b <= a) continue;
            float fa = s.mouthA ? 0 : .7f * T, fb = s.mouthB ? 0 : .7f * T;
            if (s.horizontal) {
                float ph = FLOOR_TILES * T / (k.hBot - k.hTop), rep = ph * k.hw / k.hh;
                float top = s.c + ph * ((k.hTop + k.hBot) / 2f), bot = top - ph;
                if (b < x0 || a > x1 || top < y0 || bot > y1) continue;
                strip(batch, k.h, true, a, b, bot, top, rep, 0, 1, fa, fb);
            } else {
                float pw = FLOOR_TILES * T / (k.vR - k.vL), rep = pw * k.vh / k.vw;
                float left = s.c - pw * ((k.vL + k.vR) / 2f), right = left + pw;
                if (b < y0 || a > y1 || right < x0 || left > x1) continue;
                strip(batch, k.v, false, a, b, left, right, rep, 0, 1, fa, fb);
            }
        }
    }

    /** Keep floor exits and their masonry opaque; only the surrounding rock fades.
     * Fading all four edges also faded the entrance stairs/torches into the bedrock. */
    private static Pixmap featherPassage(Pixmap source, Corridor c) {
        Pixmap p = rgba(source);
        int w = p.getWidth(), h = p.getHeight();
        float fade = Math.min(w, h) * .08f;
        ByteBuffer pixels = p.getPixels();
        float[][] ends = {c.scene.path.get(0), c.scene.path.get(c.scene.path.size() - 1)};
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            float left = smooth((x + .5f) / fade), right = smooth((w - x - .5f) / fade);
            float top = smooth((y + .5f) / fade), bottom = smooth((h - y - .5f) / fade);
            for (float[] end : ends) {
                float protectX = 1 - smooth((Math.abs(x - end[0] * w) - fade * 1.7f) / fade);
                float protectY = 1 - smooth((Math.abs(y - end[1] * h) - fade * 1.7f) / fade);
                if (end[0] < .06f) left += (1 - left) * protectY;
                if (end[0] > .94f) right += (1 - right) * protectY;
                if (end[1] < .06f) top += (1 - top) * protectX;
                if (end[1] > .94f) bottom += (1 - bottom) * protectX;
            }
            int idx = (y * w + x) * 4 + 3;
            pixels.put(idx, (byte) Math.round((pixels.get(idx) & 255) * left * right * top * bottom));
        }
        return p;
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    /** World-aligned UVs continue through every bend; no rotated rails, stairs or carpet seams. */
    void drawFloors(SpriteBatch batch, float x0, float y0, float x1, float y1) {
        if (painted) return; // 바닥은 길목 그림 안에 이미 있다. 별도 직사각형을 덧그리지 않는다.
        for (Seg s : segs) {
            Skin k = skins.getOrDefault(s.theme, skins.get("CAMP"));
            float[] ls = s.floorAlong, cs = s.floorAcross, alpha = EDGE_ALPHA;
            float repeat = 10 * T;
            for (int l = 0; l < 3; l++) for (int c = 0; c < 3; c++) {
                float left = s.horizontal ? ls[l] : cs[c], right = s.horizontal ? ls[l+1] : cs[c+1];
                float bottom = s.horizontal ? cs[c] : ls[l], top = s.horizontal ? cs[c+1] : ls[l+1];
                if (right < x0 || left > x1 || top < y0 || bottom > y1) continue;
                float a00 = alpha[l] * alpha[c], a11 = alpha[l+1] * alpha[c+1];
                float a10 = s.horizontal ? alpha[l+1] * alpha[c] : alpha[l] * alpha[c+1];
                float a01 = s.horizontal ? alpha[l] * alpha[c+1] : alpha[l+1] * alpha[c];
                quad(batch, k.floor, left, bottom, right, top,
                        left / repeat, -bottom / repeat, right / repeat, -top / repeat,
                        .9f, a00, a10, a11, a01);
            }
        }
    }

    /** 방 그림의 출구 바깥만 길목 그림으로 부드럽게 넘긴다. 원본 파일은 바꾸지 않는다. */
    Pixmap joinRoom(Pixmap source, Room r) {
        return joinRoom(source, r, false);
    }

    Pixmap joinForeground(Pixmap source, Room r) {
        return joinRoom(source, r, true);
    }

    private Pixmap joinRoom(Pixmap source, Room r, boolean foreground) {
        if (!painted) return featherEdges(source);
        Pixmap p = rgba(source);
        int w = p.getWidth(), h = p.getHeight();
        ByteBuffer pixels = p.getPixels();
        RoomJoinMask mask = new RoomJoinMask(r, T);
        for (int py = 0; py < h; py++) for (int px = 0; px < w; px++) {
            float alpha = mask.alpha(r.sceneX((px + .5f) / w), r.sceneY((py + .5f) / h));
            if (foreground) alpha *= joints.originalForegroundAlpha(r.sceneX((px + .5f) / w), r.sceneY((py + .5f) / h));
            int idx = (py * w + px) * 4 + 3;
            pixels.put(idx, (byte) Math.round((pixels.get(idx) & 255) * alpha));
        }
        return p;
    }

    /**
     * 길이 방향 [a, b] 에 그림을 깐다 (폭 방향 [c0, c1]). 길이 방향 무늬는 월드 좌표에 맞춰 거울 반복한다.
     * 양 끝에서 fadeA / fadeB 만큼 바깥으로 흐려지며 더 나간다.
     */
    private void strip(SpriteBatch batch, Texture tex, boolean horizontal, float a, float b, float c0, float c1,
                       float rep, float s0, float s1, float fadeA, float fadeB) {
        if (fadeA > 0) piece(batch, tex, horizontal, a - fadeA, a, c0, c1, rep, s0, s1, 0f, 1f);
        if (b > a) piece(batch, tex, horizontal, a, b, c0, c1, rep, s0, s1, 1f, 1f);
        if (fadeB > 0) piece(batch, tex, horizontal, b, b + fadeB, c0, c1, rep, s0, s1, 1f, 0f);
    }

    private void piece(SpriteBatch batch, Texture tex, boolean horizontal, float l0, float l1, float c0, float c1,
                       float rep, float s0, float s1, float alpha0, float alpha1) {
        if (horizontal) {
            // 가로 그림: u = 길이(x) 방향 반복, v = 위(0) ~ 아래(1)
            quad(batch, tex, l0, c0, l1, c1, l0 / rep, s1, l1 / rep, s0, 1f, alpha0, alpha1, alpha1, alpha0);
        } else {
            // 세로 그림: u = 왼쪽(0) ~ 오른쪽(1), v = 길이(y) 방향 반복 (위가 작은 v)
            quad(batch, tex, c0, l0, c1, l1, s0, -l0 / rep, s1, -l1 / rep, 1f, alpha0, alpha0, alpha1, alpha1);
        }
    }

    /**
     * 꼭짓점마다 투명도가 다른 사각형. (x0,y0) 왼쪽 아래 ~ (x1,y1) 오른쪽 위.
     * u0,v0 는 왼쪽 아래 꼭짓점, u1,v1 은 오른쪽 위 꼭짓점의 텍스처 좌표.
     * a00 왼쪽 아래, a10 오른쪽 아래, a11 오른쪽 위, a01 왼쪽 위.
     */
    private void quad(SpriteBatch batch, Texture tex, float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1, float tint, float a00, float a10, float a11, float a01) {
        float[] v = verts;
        put(v, 0, x0, y0, tint, a00, u0, v0);
        put(v, 5, x0, y1, tint, a01, u0, v1);
        put(v, 10, x1, y1, tint, a11, u1, v1);
        put(v, 15, x1, y0, tint, a10, u1, v0);
        batch.draw(tex, v, 0, 20);
    }

    private static void put(float[] v, int i, float x, float y, float tint, float a, float u, float tv) {
        v[i] = x;
        v[i + 1] = y;
        v[i + 2] = Color.toFloatBits(tint, tint, tint, a);
        v[i + 3] = u;
        v[i + 4] = tv;
    }

    @Override
    public void dispose() {
        joints.dispose();
        for (Texture t : paintings.values()) t.dispose();
        paintings.clear();
        for (Skin s : skins.values()) {
            s.h.dispose();
            s.v.dispose();
            s.floor.dispose();
        }
        skins.clear();
        if (ground != null) ground.dispose();
    }
}
