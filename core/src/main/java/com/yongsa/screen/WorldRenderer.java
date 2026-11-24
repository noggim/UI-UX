package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.YongsaGame;
import com.yongsa.combat.Facing;
import com.yongsa.enemy.Hazard;
import com.yongsa.combat.HeroActor;
import com.yongsa.combat.HeroShot;
import com.yongsa.enemy.EnemyPainter;
import com.yongsa.enemy.Monster;
import com.yongsa.enemy.Projectile;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.Effects;
import com.yongsa.gfx.ActorInk;
import com.yongsa.gfx.Fonts;
import com.yongsa.gfx.HeroMotion;
import com.yongsa.gfx.HeroPose;
import com.yongsa.gfx.HeroSword;
import com.yongsa.gfx.SpriteBlend;
import com.yongsa.gfx.Lighting;
import com.yongsa.gfx.Particles;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.OreType;
import com.yongsa.model.Vein;
import com.yongsa.model.Weapon;
import com.yongsa.ui.Ui;
import com.yongsa.map.Door;
import com.yongsa.map.Link;
import com.yongsa.map.Prop;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import com.yongsa.map.MapPainter;
import com.yongsa.map.WorldMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 맵과 그 위의 것들을 그린다. 2.5D 느낌은 비스듬히 내려다본 픽셀 그림과
 * 발 위치 기준 깊이 정렬(아래쪽에 있는 것이 앞)로 낸다 (FR-맵-02, NFR-02).
 *
 * 그리는 순서: 빛 지도 → 바닥·벽 → 깊이 정렬한 것들 → 빛 지도 곱하기 → 빛나는 효과(더하기) → 글자.
 */
public class WorldRenderer implements Disposable {
    private static final int S = Art.SCALE;
    // Presentation only: keep feet/collision, attack ranges and camera in world coordinates.
    private static final float HERO_SCALE = 1.25f;

    private final GameSession g;
    private final Art art;
    private final Fonts fonts;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final WorldMap map;
    private final int T;
    private final GlyphLayout layout = new GlyphLayout();
    private final Lighting lighting = new Lighting(YongsaGame.WIDTH, YongsaGame.HEIGHT);
    private final Particles particles;
    private final Matrix4 screenProj = new Matrix4().setToOrtho2D(0, 0, YongsaGame.WIDTH, YongsaGame.HEIGHT);

    private final List<FloatText> floats = new ArrayList<>();
    private final List<Ghost> ghosts = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Flash> flashes = new ArrayList<>();
    private final Color ambient = new Color(0.6f, 0.55f, 0.5f, 1f);
    private boolean ambientSet;
    private float time, ghostT, moteT, emberT;
    private float walkBlend;
    private final boolean heroMotionAvailable;
    private final SpriteBlend spriteBlend = new SpriteBlend();
    private final ActorInk actorInk = new ActorInk();
    /** 맵 그리기 (map/MapPainter). */
    private final MapPainter mapPainter;
    /** 몬스터·보스 그리기 (enemy/EnemyPainter). */
    private final EnemyPainter enemyPainter;
    private int lastStep = -1;
    private float viewX0, viewY0, viewX1, viewY1;

    private static class FloatText {
        float x, y, t;
        String text;
        Color color;
        boolean big;
    }

    /** 보스 말풍선. */
    private static class Speech {
        float x, y, t;
        String text, speaker;
    }

    private final List<Speech> speeches = new ArrayList<>();
    /** 천공 낙뢰가 떨어진 자리 (x, y, 지난 시간): 하늘에서 내리꽂히는 빛줄기를 잠깐 그린다. */
    private final List<float[]> bolts = new ArrayList<>();
    /** Presentation only: rewards are granted once by GameSession, independently of these animations. */
    private final List<ChestOpening> chestOpenings = new ArrayList<>();

    private static class ChestOpening {
        String room, dungeon;
        float x, y, age;
    }

    /** 검 스킬 색: 흑철 흙빛, 혈월 핏빛, 천공 하늘빛, 성검 금빛. */
    private static Color skillColor(String kind) {
        if (kind == null) return Color.WHITE;
        switch (kind) {
            case "cleave": return new Color(1f, 0.62f, 0.3f, 1f);
            case "crescent": return new Color(1f, 0.18f, 0.3f, 1f);
            case "strike": return new Color(0.55f, 0.85f, 1f, 1f);
            default: return new Color(1f, 0.93f, 0.6f, 1f);
        }
    }

    // ───────────────────────── 지용 버전 보스 동작 시트

    private static class Ghost {
        TextureRegion region;
        float x, y, w, h, t, footOffset;
        boolean flip;
    }

    private static class Ring {
        float x, y, r0, r1, t, dur;
        Color color;
    }

    private static class Flash {
        float x, y, radius, t, dur;
        Color color;
    }

    private interface DrawCall {
        void draw();
    }

    private static class Sorted {
        final float y;
        final DrawCall call;

        Sorted(float y, DrawCall call) {
            this.y = y;
            this.call = call;
        }
    }

    public WorldRenderer(GameSession g, Art art, Fonts fonts, SpriteBatch batch, ShapeRenderer shapes) {
        this.g = g;
        this.art = art;
        this.fonts = fonts;
        this.batch = batch;
        this.shapes = shapes;
        this.map = g.map;
        this.T = g.balance.tileSize;
        heroMotionAvailable = Gdx.files.internal(com.yongsa.gfx.HeroMotionAtlas.MANIFEST).exists()
                || (Gdx.files.internal("sprites/hero-motion/walk.png").exists()
                && Gdx.files.internal("sprites/hero-motion/roll.png").exists());
        this.particles = new Particles(art.white(), art.glow);
        this.mapPainter = new MapPainter(g, art, batch, shapes);
        this.enemyPainter = new EnemyPainter(g, art, batch, shapes, actorInk, new Effects() {
            @Override public Particles particles() { return particles; }
            @Override public void flash(float x, float y, float radius, Color c, float dur) { WorldRenderer.this.flash(x, y, radius, c, dur); }
            @Override public void ring(float x, float y, float r0, float r1, float dur, Color c) { WorldRenderer.this.ring(x, y, r0, r1, dur, c); }
            @Override public void actorShadow(float x, float y, float width, float alpha, boolean flying) { drawActorShadow(x, y, width, alpha, flying); }
        });
    }

    // ───────────────────────── 이벤트 → 효과

    public void onEvent(GameEvent e) {
        if (enemyPainter.onEvent(e)) return; // 몬스터·보스 효과는 enemy/EnemyPainter
        switch (e.type) {
            case FLOAT_TEXT:
                addFloat(e);
                break;
            case SKILL_CAST: {
                Color c = skillColor(e.text);
                flash(e.x, e.y, 140, c, 0.25f);
                if ("cleave".equals(e.text)) {
                    ring(e.x + e.dirX * 20, e.y - 10 + e.dirY * 20, 10, 70, 0.35f, c);
                    particles.debris(e.x + e.dirX * 24, e.y - 8, 12, new Color(0.45f, 0.38f, 0.3f, 1f), 160, 4);
                } else if ("beam".equals(e.text)) {
                    ring(e.x, e.y - 10, 10, 90, 0.4f, c);
                }
                particles.spray(e.x, e.y, e.dirX, e.dirY, 14, c, 300, 0.8f, 0.3f, 3);
                break;
            }
            case SKILL_HIT: {
                Color c = skillColor(e.text);
                if ("strike".equals(e.text)) {
                    flash(e.x, e.y, 220, c, 0.35f);
                    ring(e.x, e.y, 6, e.size * 1.3f, 0.4f, c);
                    particles.burst(e.x, e.y + 10, 26, c, 300, 0.6f, 3, true);
                    bolts.add(new float[]{e.x, e.y, 0f});
                } else {
                    particles.burst(e.x, e.y, 12, c, 220, 0.4f, 3, true);
                    flash(e.x, e.y, 90, c, 0.15f);
                }
                break;
            }
            case CHEST_OPENED: {
                if (e.dungeonId != null && e.opened != null) {
                    chestOpenings.removeIf(c -> e.opened.equals(c.room));
                    ChestOpening c = new ChestOpening();
                    c.room = e.opened;
                    c.dungeon = e.dungeonId;
                    c.x = e.x;
                    c.y = e.y - T;
                    chestOpenings.add(c);
                }
                Color gold = new Color(1f, 0.85f, 0.4f, 1f);
                particles.burst(e.x, e.y - T / 2f, 14, gold, 140, 0.45f, 1.8f, true);
                flash(e.x, e.y - T / 2f, 110, gold, 0.3f);
                break;
            }
            case HIT_FX:
                if (e.tone == GameEvent.Tone.HERO_HIT) {
                    particles.burst(e.x, e.y, 12, new Color(1f, 0.3f, 0.25f, 1f), 160, 0.35f, 3, true);
                    flash(e.x, e.y, 90, new Color(1f, 0.2f, 0.15f, 1f), 0.15f);
                } else {
                    Weapon w = g.state.equipped();
                    Color c = swingColor(w == null ? "" : w.defId);
                    particles.spray(e.x, e.y, e.dirX, e.dirY, e.boss ? 14 : 9, c, 260, 0.7f, 0.3f, 3);
                    particles.spray(e.x, e.y, e.dirX, e.dirY, 4, Color.WHITE, 340, 0.3f, 0.18f, 2);
                    flash(e.x, e.y, 70, c, 0.12f);
                }
                break;
            case MINE_FX: {
                Color oc = oreColor(e.ore);
                float dx = g.hero.x - e.x, dy = g.hero.centerY() - e.y, l = Math.max(1f, (float) Math.hypot(dx, dy));
                particles.spray(e.x, e.y + 6, dx / l, dy / l, 8, new Color(1f, 0.85f, 0.45f, 1f), 220, 0.9f, 0.25f, 2);
                particles.debris(e.x, e.y, 4, new Color(0.42f, 0.42f, 0.48f, 1f), 120, 3);
                particles.debris(e.x, e.y, 2, oc, 120, 3);
                flash(e.x, e.y, 60, oc, 0.1f);
                break;
            }
            case ORE_GAINED: {
                Color oc = oreColor(e.ore);
                particles.debris(e.x, e.y - T / 2f, 14, oc, 180, 4);
                particles.burst(e.x, e.y - T / 4f, 18, oc, 200, 0.6f, 3, true);
                ring(e.x, e.y - T / 2f, 6, 70, 0.35f, oc);
                flash(e.x, e.y, 160, oc, 0.4f);
                break;
            }
            case BOSS_SPEECH: {
                Speech sp = new Speech();
                sp.x = e.x;
                sp.y = e.y;
                for (Monster m : g.monsters) {
                    if (m.boss && Math.abs(m.x - e.x) < .01f)
                        sp.y = Math.max(sp.y, enemyPainter.monsterTop(m) + 16);
                }
                sp.text = e.text;
                sp.speaker = e.speaker;
                speeches.clear();
                speeches.add(sp);
                break;
            }
            case WARP:
                particles.burst(g.hero.x, g.hero.centerY(), 40, new Color(0.6f, 0.5f, 1f, 1f), 260, 0.8f, 3, true);
                flash(g.hero.x, g.hero.y, 260, new Color(0.6f, 0.5f, 1f, 1f), 0.6f);
                break;
            case HERO_DIED:
                chestOpenings.clear();
                enemyPainter.clear();
                speeches.clear();
                particles.clear();
                ghosts.clear();
                rings.clear();
                break;
            case BED_USED:
                chestOpenings.clear();
                enemyPainter.clearEyes();
                break;
            default:
                break;
        }
    }

    public void addFloat(GameEvent e) {
        FloatText f = new FloatText();
        f.x = e.x + MathUtils.random(-6f, 6f);
        f.y = e.y;
        f.text = e.text;
        switch (e.tone) {
            case HERO_HIT: f.color = Ui.RED; f.big = true; break;
            case MONSTER_HIT: f.color = Color.WHITE; f.big = true; break;
            case ORE: f.color = Ui.GOLD; break;
            case HEAL: f.color = Ui.GREEN; f.big = true; break;
            case BLOCKED: f.color = Ui.PURPLE; break;
            case SHIELD: f.color = EnemyPainter.SHIELD_COLOR; f.big = true; break;
            default: f.color = Ui.TEXT;
        }
        floats.add(f);
    }

    private void flash(float x, float y, float radius, Color c, float dur) {
        Flash f = new Flash();
        f.x = x;
        f.y = y;
        f.radius = radius;
        f.color = new Color(c);
        f.dur = dur;
        flashes.add(f);
    }

    private void ring(float x, float y, float r0, float r1, float dur, Color c) {
        Ring r = new Ring();
        r.x = x;
        r.y = y;
        r.r0 = r0;
        r.r1 = r1;
        r.dur = dur;
        r.color = new Color(c);
        rings.add(r);
    }

    // ───────────────────────── 매 프레임

    public void update(float dt) {
        for (ChestOpening c : chestOpenings) c.age += dt;
        chestOpenings.removeIf(c -> c.age >= 2.8f || g.hasChest(map.room(c.room)));
        for (float[] b : bolts) b[2] += dt;
        bolts.removeIf(b -> b[2] > 0.3f);
        time += dt;
        mapPainter.update(dt, time);
        for (Iterator<FloatText> it = floats.iterator(); it.hasNext(); ) {
            FloatText f = it.next();
            f.t += dt;
            f.y += (f.t < 0.15f ? 90 : 26) * dt;
            if (f.t > 0.9f) it.remove();
        }
        for (Iterator<Speech> it = speeches.iterator(); it.hasNext(); ) {
            Speech sp = it.next();
            sp.t += dt;
            if (sp.t > 3.6f) it.remove();
        }
        particles.update(dt);
        enemyPainter.update(dt, time);
        for (Iterator<Ghost> it = ghosts.iterator(); it.hasNext(); ) {
            Ghost gh = it.next();
            gh.t += dt;
            if (gh.t > 0.2f) it.remove();
        }
        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring r = it.next();
            r.t += dt;
            if (r.t > r.dur) it.remove();
        }
        for (Iterator<Flash> it = flashes.iterator(); it.hasNext(); ) {
            Flash f = it.next();
            f.t += dt;
            if (f.t > f.dur) it.remove();
        }

        HeroActor h = g.hero;
        float targetWalk = h.moving && !h.rolling() ? 1f : 0f;
        walkBlend += (targetWalk - walkBlend) * (1f - (float) Math.exp(-20f * dt));
        // 돌진 잔상
        if (h.rolling()) {
            ghostT -= dt;
            if (MathUtils.random() < dt * 40f) {
                particles.smoke(h.x, h.y + 2, 1, new Color(0.75f, 0.7f, 0.62f, 0.3f), 6, 7, 0.4f);
            }
            if (ghostT <= 0) {
                ghostT = 0.06f;
                Ghost gh = new Ghost();
                float p = HeroMotion.clamp(1 - h.rollT / h.rollTotal);
                gh.region = motionFrame("roll", h.rollFacing, HeroMotion.rollFrame(p));
                if (gh.region == null) gh.region = heroRegion(h);
                boolean motion = hasHeroMotion();
                gh.w = motion ? HeroMotion.WIDTH : art.width(gh.region) * S * HERO_SCALE;
                gh.h = motion ? HeroMotion.HEIGHT : art.height(gh.region) * S * HERO_SCALE;
                gh.footOffset = motion ? HeroMotion.FOOT_OFFSET : 2;
                gh.x = h.x;
                gh.y = h.y;
                gh.flip = h.rollFacing.leftward();
                ghosts.add(gh);
            }
        }
        // 발걸음 먼지
        int step = h.moving && !h.rolling() ? (int) (h.walkTime * 4f) : -1;
        if (step >= 0 && step != lastStep) {
            particles.smoke(h.x, h.y + 1, 2, new Color(0.75f, 0.7f, 0.62f, 0.28f), 4, 6, 0.45f);
        }
        lastStep = step;

        // 떠다니는 먼지 (던전), 모닥불 불똥 (캠프)
        Room room = g.currentRoom();
        boolean dungeon = room != null && room.dungeonId != null;
        moteT -= dt;
        if (moteT <= 0 && viewX1 > viewX0) {
            moteT = dungeon ? 0.07f : 0.15f;
            Color mc = dungeon ? mapPainter.themeMote(room.dungeonId) : new Color(1f, 0.85f, 0.6f, 0.3f);
            Particles.P p = particles.spawn(MathUtils.random(viewX0, viewX1), MathUtils.random(viewY0, viewY1),
                    MathUtils.random(3f, 5f), 2, mc);
            p.vx = MathUtils.random(-8f, 8f);
            p.vy = MathUtils.random(4f, 14f);
            p.glow = true;
            p.soft = false;
        }
        emberT -= dt;
        if (emberT <= 0) {
            emberT = 0.05f;
            for (Prop pr : map.props) {
                if (pr.kind != Prop.Kind.CAMPFIRE) continue;
                float cx = pr.centerX(T), cy = pr.centerY(T) + (pr.baked ? 20 : -4);
                Particles.P p = particles.spawn(cx + MathUtils.random(-8f, 8f), cy, MathUtils.random(0.8f, 1.6f),
                        MathUtils.random(2f, 3f), new Color(1f, MathUtils.random(0.5f, 0.8f), 0.2f, 1f));
                p.vz = MathUtils.random(30f, 70f);
                p.vx = MathUtils.random(-12f, 12f);
                p.glow = true;
                p.endSize = 0;
            }
            for (float[] t : mapPainter.torches) {
                if (MathUtils.random() > 0.08f || !inView(t[0], t[1])) continue;
                Particles.P p = particles.spawn(t[0] + MathUtils.random(-2f, 2f), t[1] + 22, MathUtils.random(0.5f, 0.9f), 2,
                        new Color(1f, 0.7f, 0.3f, 1f));
                p.vz = MathUtils.random(20f, 40f);
                p.glow = true;
                p.endSize = 0;
            }
        }
        Room hr = g.heroRoom();

        // 방 분위기에 맞춰 바탕 밝기를 천천히 바꾼다 (통로는 조금 더 어둡다)
        Color target = mapPainter.ambientFor(room);
        com.yongsa.map.Corridor walking = map.corridorAtWorld(g.hero.x, g.hero.y);
        if (hr == null && walking != null) target = mapPainter.corridorAmbient(walking.theme);
        if (!ambientSet) {
            ambient.set(target);
            ambientSet = true;
        } else {
            float a = 1f - (float) Math.exp(-3f * dt);
            ambient.lerp(target, a);
        }
    }

    private boolean inView(float x, float y) {
        return x > viewX0 - 64 && x < viewX1 + 64 && y > viewY0 - 64 && y < viewY1 + 64;
    }

    // ───────────────────────── 빛 지도

    /** 맵을 그리기 전에 부른다 (빛 지도를 따로 그린다). */
    public void renderLights(OrthographicCamera cam) {
        computeView(cam);
        // 실명 (해골 사제): 거의 캄캄해지고 용사 둘레만 조금 보인다. 바닥 예고와 사제의 눈은 빛난다.
        float bs = g.balance.monster.blindSeconds;
        float blind = g.blindT > 0 ? Math.min(1f, Math.min(g.blindT / 0.5f, (bs - g.blindT) / 0.3f)) : 0f;
        float amb = 1f - 0.92f * blind;
        // 마왕 최후의 발악: 방 전체가 어두워진다
        for (Monster lm : g.monsters) {
            if (!lm.dead && lm.lastStand) {
                amb *= 0.6f;
                break;
            }
        }
        lighting.begin(ambient.r * amb, ambient.g * amb, ambient.b * amb);
        HeroActor h = g.hero;
        lighting.add(h.x, h.y + 14, 250 * (1f - 0.55f * blind), 1f, 0.92f, 0.8f, 0.6f);
        if (h.swingT > 0) {
            Weapon w = g.state.equipped();
            Color c = swingColor(w == null ? "" : w.defId);
            lighting.add(h.x, h.centerY(), 170, c.r, c.g, c.b, 0.5f);
        }
        Weapon eq = g.state.equipped();
        if (eq != null && (eq.awakened || "holy_awakened".equals(eq.defId))) {
            Color c = swingColor(eq.defId);
            lighting.add(h.x, h.centerY(), 120, c.r, c.g, c.b, 0.3f + 0.08f * MathUtils.sin(time * 4f));
        }
        mapPainter.addLights(lighting, blind);
        for (Room r : map.rooms) {
            if (r.type == RoomType.ORE) {
                List<Vein> veins = g.veinsOf(r);
                if (veins == null) continue;
                for (Vein v : veins) {
                    Color c = oreColor(v.ore);
                    float px = veinX(v), py = veinY(v);
                    if (!inView(px, py)) continue;
                    // 그림 속 광석 덩어리는 더 넓게, 캐면 캘수록 빛이 줄어든다
                    float left = v.hitsLeft / (float) g.balance.mining.hitsToBreak;
                    float rad = v.painted() ? 80 + 40 * left : 70;
                    lighting.add(px, py, rad, c.r, c.g, c.b, (0.45f + 0.1f * MathUtils.sin(time * 3f + v.tileX)) * (0.6f + 0.4f * left));
                }
            } else if (g.hasPortal(r)) {
                float px = g.portalX(r), py = g.portalY(r);
                if (inView(px, py)) lighting.add(px, py, 145, 0.28f, 0.85f, 0.75f, 0.55f + 0.06f * MathUtils.sin(time * 2f));
            }
        }
        enemyPainter.addLights(lighting, blind);
        for (HeroShot hs : g.shots) {
            Color c = skillColor(hs.kind);
            lighting.add(hs.x, hs.y, hs.strike() ? 70 : 130, c.r, c.g, c.b, 0.8f);
        }
        for (Flash f : flashes) {
            float a = 1f - f.t / f.dur;
            lighting.add(f.x, f.y, f.radius, f.color.r, f.color.g, f.color.b, a);
        }
        lighting.render(batch, cam);
    }

    private void computeView(OrthographicCamera cam) {
        float vw = cam.viewportWidth * cam.zoom, vh = cam.viewportHeight * cam.zoom;
        viewX0 = cam.position.x - vw / 2f;
        viewY0 = cam.position.y - vh / 2f;
        viewX1 = viewX0 + vw;
        viewY1 = viewY0 + vh;
        mapPainter.setView(viewX0, viewY0, viewX1, viewY1);
        enemyPainter.setView(viewX0, viewY0, viewX1, viewY1);
    }

    // ───────────────────────── 그리기

    /** 개발용 (F1): 용사 발이 설 수 있는 곳을 초록으로 덮는다. 그림은 바닥인데 초록이 없으면 막힌 곳이다. */
    public boolean showWalkable;

    public void render(OrthographicCamera cam) {
        computeView(cam);
        int tx0 = Math.max(0, (int) (viewX0 / T) - 1), ty0 = Math.max(0, (int) (viewY0 / T) - 2);
        int tx1 = Math.min(map.width - 1, (int) (viewX1 / T) + 1);
        int ty1 = Math.min(map.height - 1, (int) (viewY1 / T) + 1);

        batch.setProjectionMatrix(cam.combined);
        shapes.setProjectionMatrix(cam.combined);

        batch.begin();
        batch.setColor(Color.WHITE);
        mapPainter.drawGround(tx0, ty0, tx1, ty1);
        batch.end();

        enemyPainter.drawGroundEffects();

        batch.begin();
        drawGhosts();
        List<Sorted> list = new ArrayList<>();
        collect(list, tx0, ty0, tx1, ty1);
        mapPainter.collect((y, draw) -> list.add(new Sorted(y, draw::run)));
        list.sort((a, b) -> Float.compare(b.y, a.y));
        for (Sorted s : list) s.call.draw();
        mapPainter.drawFronts();
        particles.drawNormal(batch);
        batch.setColor(Color.WHITE);
        batch.end();

        lighting.composite(batch);

        batch.setProjectionMatrix(cam.combined);
        shapes.setProjectionMatrix(cam.combined);
        drawGlow();
        enemyPainter.drawHpBars();
        if (showWalkable) mapPainter.drawWalkable();

        batch.setProjectionMatrix(screenProj);
        batch.begin();
        drawTexts(cam);
        batch.end();
    }

    // ───────────────────────── 타일

    // ───────────────────────── 통로: 방 그림의 바닥과 바위를 잘라 이어 붙인다

    // ───────────────────────── 던전 방 배경 그림

    // ───────────────────────── 바닥 효과 (포탈 바닥, 보스 범위 공격 예고)

    // ───────────────────────── 깊이 정렬되는 것들

    private void collect(List<Sorted> list, int tx0, int ty0, int tx1, int ty1) {
        for (Prop p : map.props) {
            if (p.walkable() || p.baked) continue; // 바닥 문양은 타일과 함께 먼저 그린다
            list.add(new Sorted(p.y(T) + 2, () -> drawProp(p)));
        }
        for (Room r : map.rooms) {
            if (r.type != RoomType.ORE) continue;
            if (r.x1 < tx0 || r.x0 > tx1 || r.y1 < ty0 || r.y0 > ty1) continue;
            List<Vein> veins = g.veinsOf(r);
            if (veins == null) continue;
            // 그림 속 광석 덩어리 앞 광맥은 그림이 곧 광석이라 따로 그리지 않는다
            for (Vein v : veins) if (!v.painted()) list.add(new Sorted(v.tileY * T + 4, () -> drawVein(v)));
        }
        if (g.inVillage()) {
            for (com.yongsa.config.Balance.Villager v : g.balance.village.villagers) {
                float vx = g.villagerX(v), vy = g.villagerY(v);
                list.add(new Sorted(vy, () -> drawVillager(v, vx, vy)));
            }
        }
        for (Room r : map.rooms) {
            if (!g.hasChest(r) || g.fighting()) continue;
            float cx = g.chestX(r), cy = g.chestY(r);
            if (inView(cx, cy)) list.add(new Sorted(cy, () -> drawChest(r.dungeonId, cx, cy, 0, 1f)));
        }
        for (ChestOpening c : chestOpenings) {
            if (!inView(c.x, c.y) || g.fighting()) continue;
            int frame = c.age < 0.18f ? 1 : 2;
            float alpha = Math.min(1f, (2.8f - c.age) / 0.5f);
            list.add(new Sorted(c.y, () -> drawChest(c.dungeon, c.x, c.y, frame, alpha)));
        }
        for (float[] pd : g.state.potionDrops) {
            if (!inView(pd[0], pd[1])) continue;
            list.add(new Sorted(pd[1], () -> drawPotionDrop(pd[0], pd[1])));
        }
        HeroActor h = g.hero;
        list.add(new Sorted(h.y, this::drawHero));
        enemyPainter.collect((y, draw) -> list.add(new Sorted(y, draw::run)));
    }

    private static final Map<String, Boolean> STORY_EXISTS = new HashMap<>();

    /** Original-size fallback sheets remain usable; display atlases preserve source detail. */
    private TextureRegion storyFrame(String name, String fallback, int frames, int frame) {
        String high = "sprites/story/" + name + ".png";
        return STORY_EXISTS.computeIfAbsent(high, k -> Gdx.files.internal(k).exists())
                ? art.cell(high, frames, 1, frame) : art.frame(fallback, frames, frame);
    }

    // Adult visible height ~58px, matching the hero; the original child proportions remain smaller.
    private static final float VILLAGER_SCALE = 1.65f;

    /**
     * 마을 사람 (서 있는 두 자세, 수정 속).
     * 마왕 전에는 눈을 감은 채 보랏빛 수정 속에 갇혀 있고, 뒤에는 수정이 사라지고 웃다가 팔을 들고 뛴다.
     */
    private void drawVillager(com.yongsa.config.Balance.Villager v, float x, float y) {
        String path = "team/sunwoo/villager_" + v.kind + ".png";
        float phase = x * 0.031f;
        float k = VILLAGER_SCALE, w = 32 * k, h = 40 * k;
        drawShadow(x, y, 22 * k);
        if (!g.villagersFree()) {
            TextureRegion f = storyFrame("villager_" + v.kind, path, 3, 2);
            if (f != null) {
                batch.setColor(0.82f, 0.78f, 1f, 1f);
                batch.draw(f, x - w / 2f, y - 2 * k, w, h);
            }
            TextureRegion c = storyFrame("crystal_prison", "team/sunwoo/crystal_prison.png", 2, (int) ((time + phase) / 0.6f));
            if (c != null) {
                batch.setColor(1, 1, 1, 0.9f + 0.1f * MathUtils.sin(time * 2f + phase * 10));
                batch.draw(c, x - 22 * k, y - 6 * k, 44 * k, 60 * k);
            }
        } else {
            float t = (time + phase * 7) % 4.8f;
            float p = HeroMotion.clamp((t - 3.8f) / 1f);
            float cheer = HeroMotion.smooth(p / .16f) * (1 - HeroMotion.smooth((p - .8f) / .2f));
            float hop = MathUtils.sin(p * MathUtils.PI);
            hop *= hop * 3;
            TextureRegion f = storyFrame("villager_" + v.kind, path, 3, 0);
            TextureRegion raised = storyFrame("villager_" + v.kind, path, 3, 1);
            if (f != null) {
                batch.setColor(Color.WHITE);
                spriteBlend.draw(batch, f, raised, cheer, x - w / 2f, y - 2 * k + hop, w, h);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * 바닥에 떨어진 회복 포션: 붉은 빛 위에 둥실 떠 있는 병. sprites/potion.png 그림이 있으면 그것을 쓰고,
     * 없으면 임시로 코드로 그린 병 (docs/ART_REQUESTS.md 0-7).
     */
    private void drawPotionDrop(float x, float y) {
        float bob = 3f * MathUtils.sin(time * 3f + x * 0.05f);
        batch.setColor(1f, 0.25f, 0.3f, 0.35f + 0.1f * MathUtils.sin(time * 4f));
        batch.draw(art.glow, x - 26, y - 14, 52, 40);
        batch.setColor(0, 0, 0, 0.35f);
        batch.draw(art.glow, x - 10, y - 5, 20, 8);
        float by = y + 4 + bob;
        Texture pic = potionArt();
        if (pic != null) {
            batch.setColor(Color.WHITE);
            float h = 30, w = h * pic.getWidth() / pic.getHeight();
            batch.draw(pic, x - w / 2, by, w, h);
        } else {
            rectW(x - 3, by + 22, 6, 4, new Color(0.55f, 0.38f, 0.22f, 1f));
            rectW(x - 2.5f, by + 17, 5, 6, new Color(0.75f, 0.8f, 0.85f, 0.9f));
            rectW(x - 8, by, 16, 18, new Color(0.75f, 0.8f, 0.85f, 0.9f));
            rectW(x - 7, by + 1, 14, 11, new Color(0.85f, 0.12f, 0.18f, 1f));
            rectW(x - 5, by + 13, 2, 4, new Color(1f, 1f, 1f, 0.5f));
        }
        batch.setColor(Color.WHITE);
    }

    private Texture potionTex;
    private boolean potionChecked;

    private Texture potionArt() {
        if (!potionChecked) {
            potionChecked = true;
            if (Gdx.files.internal("sprites/potion.png").exists()) {
                potionTex = new Texture(Gdx.files.internal("sprites/potion.png"), true);
                potionTex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            }
        }
        return potionTex;
    }

    private void rectW(float x, float y, float w, float h, Color c) {
        batch.setColor(c);
        batch.draw(art.white(), x, y, w, h);
    }

    /** Same 44x40 world footprint for all atlas resolutions and opening frames. */
    private void drawChest(String dungeonId, float x, float y, int frame, float alpha) {
        String name = "chest_" + dungeonId.toLowerCase(java.util.Locale.ROOT) + ".png";
        TextureRegion t = art.cell("sprites/rewards/" + name, 3, 1, frame);
        if (t == null) t = art.frame("team/sunwoo/" + name, 3, frame);
        if (t == null) return;
        float w = 44f, h = 40f;
        drawShadow(x, y, w * 0.9f * alpha);
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(t, x - w / 2f, y - 4, w, h);
        if (frame == 0) {
            Particles.additive(batch);
            batch.setColor(1f, 0.9f, 0.5f, 0.05f + 0.04f * MathUtils.sin(time * 4f));
            batch.draw(t, x - w / 2f, y - 4, w, h);
            Particles.normal(batch);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawShadow(float x, float y, float w) {
        batch.setColor(Color.WHITE);
        batch.draw(art.world("shadow"), x - w / 2f, y - w * 0.18f, w, w * 0.38f);
    }

    /** Broad low-contrast grounding, plus a smaller contact shadow under planted feet. */
    private void drawActorShadow(float x, float y, float width, float alpha, boolean flying) {
        TextureRegion shadow = art.world("shadow");
        float w = width * (flying ? .72f : 1.18f);
        batch.setColor(1, 1, 1, alpha * (flying ? .65f : .85f));
        batch.draw(shadow, x-w/2, y-w*.16f, w, w*.34f);
        if (!flying) {
            float contact = width*.58f;
            batch.setColor(1, 1, 1, alpha);
            batch.draw(shadow, x-contact/2, y-contact*.13f, contact, contact*.28f);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawProp(Prop p) {
        batch.setColor(Color.WHITE);
        float px = p.x(T), py = p.y(T);
        switch (p.kind) {
            case FORGE: {
                TextureRegion r = art.world("forge");
                drawShadow(px + 28, py + 2, 64);
                batch.draw(r, px - 8, py - 6, art.width(r) * S, art.height(r) * S);
                break;
            }
            case BED: {
                TextureRegion r = art.world("bed");
                batch.draw(r, px - 4, py - 22, art.width(r) * S, art.height(r) * S);
                break;
            }
            default: {
                TextureRegion r = art.world(((int) (time * 6)) % 2 == 0 ? "campfire" : "campfire2");
                batch.draw(r, px, py - 4, art.width(r) * S, art.height(r) * S);
            }
        }
    }

    private float veinX(Vein v) {
        return v.painted() ? v.spotX : (v.tileX + 0.5f) * T;
    }

    private float veinY(Vein v) {
        return v.painted() ? v.spotY : (v.tileY + 0.5f) * T;
    }

    private void drawVein(Vein v) {
        TextureRegion r = art.world("vein_" + v.ore.name());
        float px = v.tileX * T, py = v.tileY * T;
        int maxHits = g.balance.mining.hitsToBreak;
        drawShadow(px + T / 2f, py + 4, 30);
        float shade = 0.7f + 0.3f * v.hitsLeft / (float) maxHits;
        batch.setColor(shade, shade, shade, 1f);
        float jiggle = v.hitsLeft < maxHits ? MathUtils.sin(time * 40f) * 0.6f : 0;
        batch.draw(r, px + jiggle, py, T, T);
        batch.setColor(Color.WHITE);
    }

    private boolean hasHeroMotion() {
        return heroMotionAvailable;
    }

    private TextureRegion motionFrame(String action, Facing facing, int frame) {
        TextureRegion authored = art.heroMotion(action, facing, frame);
        if (authored != null) return authored;
        if (!heroMotionAvailable) return null;
        return art.cell("sprites/hero-motion/" + action + ".png", 6, 3, HeroMotion.row(facing) * 6 + frame);
    }

    private TextureRegion heroRegion(HeroActor h) {
        TextureRegion animated = motionFrame("walk", h.facing,
                h.moving ? HeroMotion.walkFrame(h.walkTime, art.heroWalkCycle(h.facing)) : 2);
        if (animated != null) return animated;
        boolean walk = h.moving && ((int) (h.walkTime * 8)) % 2 == 1;
        String name;
        switch (HeroMotion.row(h.facing)) {
            case 1: name = "hero_up"; break;
            case 2: name = "hero_side"; break;
            default: name = "hero_down";
        }
        return art.world(walk ? name + "_walk" : name);
    }

    private void drawHero() {
        HeroActor h = g.hero;
        if (g.sleeping()) {
            drawSleepingHero(g.sleepBed());
            return;
        }
        drawActorShadow(h.x, h.y, 26 * HERO_SCALE, 1, false);
        if (h.rolling()) {
            drawRoll(h);
            return;
        }
        boolean blink = h.invuln > 0 && h.stunT <= 0 && ((int) (h.invuln * 20)) % 2 == 0;
        float alpha = blink ? 0.35f : 1f;
        boolean flip = h.facing.leftward();
        TextureRegion r = heroRegion(h);
        boolean motion = hasHeroMotion();
        float w = motion ? HeroMotion.WIDTH : art.width(r) * S * HERO_SCALE;
        float hgt = motion ? HeroMotion.HEIGHT : art.height(r) * S * HERO_SCALE;
        float foot = motion ? HeroMotion.FOOT_OFFSET : 2;
        boolean swordBehind = h.facing.sy > 0;
        // 휘두를 때 몸을 살짝 찌그러뜨려 힘이 들어간 느낌을 준다
        float squash = h.swingT > 0 && h.swingKind == 0
                ? 1f - .035f * MathUtils.sin(MathUtils.PI * HeroMotion.clamp(h.swingT / .18f)) : 1f;
        float bob = heroBob(h, r instanceof HeroPose);
        float dw = w * (2f - squash), dh = hgt * squash;
        if (swordBehind) drawSword(h, alpha, r, squash, bob);
        if (h.stunT > 0) batch.setColor(0.7f, 0.55f, 0.85f, alpha); // 눈알에 굳음: 잿빛 보라
        else batch.setColor(1f, 1f, 1f, alpha);
        // One discrete walk/idle pose: the sword uses this exact pose's hand anchor.
        actorInk.draw(batch, r, h.x + (flip ? dw/2 : -dw/2), h.y-foot*squash+bob, flip ? -dw : dw, dh);
        if (h.flash > 0 && !blink) {
            Particles.additive(batch);
            batch.setColor(1f, 0.3f, 0.3f, Math.min(1f, h.flash * 3f) * 0.6f);
            actorInk.draw(batch, r, h.x + (flip ? dw/2 : -dw/2), h.y-foot*squash+bob, flip ? -dw : dw, dh);
            Particles.normal(batch);
        }
        if (!swordBehind) drawSword(h, alpha, r, squash, bob);
        batch.setColor(Color.WHITE);
    }

    /**
     * 침대에 누운 용사. 그림(sprites/hero_sleep.png)이 오면 그걸 침대 가운데에 그리고,
     * 없으면 옆모습을 눕혀 이불을 덮어 둔다 (docs/ART_REQUESTS.md).
     */
    private void drawSleepingHero(com.yongsa.map.Prop bed) {
        float cx = Float.isNaN(bed.sleepX) ? bed.centerX(T) : bed.sleepX;
        float cy = Float.isNaN(bed.sleepY) ? bed.centerY(T) : bed.sleepY;
        if (Gdx.files.internal("sprites/hero_sleep.png").exists()) {
            TextureRegion r = storyFrame("hero_sleep", "sprites/hero_sleep.png", 1, 0);
            float w = 48 * HERO_SCALE, hgt = 56 * HERO_SCALE;
            batch.setColor(Color.WHITE);
            batch.draw(r, cx - w / 2f, cy - hgt / 2f, w, hgt);
            return;
        }
        // 임시: 앞모습을 베개에 눕히고 그림 속 이불 색으로 가슴 아래를 덮는다
        TextureRegion r = art.world("hero_down");
        float w = art.width(r) * S * HERO_SCALE, hgt = art.height(r) * S * HERO_SCALE;
        float breathe = 0.03f * MathUtils.sin(time * 2.4f);
        batch.setColor(Color.WHITE);
        batch.draw(r, cx - w / 2f, cy - hgt / 2f, w, hgt);
        float bw = w * 1.15f, bh = hgt * (0.55f + breathe);
        batch.setColor(62 / 255f, 88 / 255f, 172 / 255f, 1f);
        batch.draw(art.white(), cx - bw / 2f, cy - hgt / 2f - 2, bw, bh);
        batch.setColor(225 / 255f, 225 / 255f, 235 / 255f, 1f);
        batch.draw(art.white(), cx - bw / 2f, cy - hgt / 2f - 2 + bh - 3, bw, 3);
        batch.setColor(Color.WHITE);
    }

    /** Authored crouch/tuck/tumble/recovery poses; the roll follows travel, not auto-aim. */
    private void drawRoll(HeroActor h) {
        float p = HeroMotion.clamp(1f - h.rollT / h.rollTotal);
        TextureRegion r = motionFrame("roll", h.rollFacing, HeroMotion.rollFrame(p));
        TextureRegion upright = motionFrame("walk", h.rollFacing, 2);
        if (r == null || upright == null) {
            int row = HeroMotion.row(h.rollFacing);
            TextureRegion fallback = art.world(row == 1 ? "hero_up" : row == 0 ? "hero_down" : "hero_side");
            float w = art.width(fallback) * S * HERO_SCALE, height = art.height(fallback) * S * HERO_SCALE;
            boolean left = h.rollFacing.leftward();
            batch.setColor(Color.WHITE);
            actorInk.draw(batch, fallback, h.x + (left ? w/2 : -w/2), h.y - 2, left ? -w : w,
                    height * (1 - .23f * MathUtils.sin(p * MathUtils.PI)));
            return;
        }
        float weight = HeroMotion.rollWeight(p);
        float w = HeroMotion.WIDTH, height = HeroMotion.HEIGHT;
        boolean flip = h.rollFacing.leftward();
        float x = h.x + (flip ? w / 2 : -w / 2), signedW = flip ? -w : w;
        float y = h.y - HeroMotion.FOOT_OFFSET;
        batch.setColor(Color.WHITE);
        actorInk.draw(batch, upright, r, weight, x, y, signedW, height);
        // Hands are tucked/occluded during a roll; don't attach a sword to the walking pose.
        batch.setColor(Color.WHITE);
    }

    private void drawSword(HeroActor h, float alpha, TextureRegion pose, float squash, float bob) {
        Weapon w = g.state.equipped();
        if (w == null) return;
        HeroSword sword = pose instanceof HeroPose ? art.heroSword(w.defId, w.awakened) : null;
        TextureRegion r = sword != null ? sword : art.worldSword(w.defId, w.awakened);
        float aim = MathUtils.atan2(h.aimY, h.aimX) * MathUtils.radDeg;
        float rest = sword != null ? HeroMotion.attachedSwordAngle(h.facing) : HeroMotion.restAngle(h.facing);
        rest += MathUtils.sin(time * 2.5f) * 3f;
        float angle = rest;
        float scale = 1f;
        if (h.mineSwingT > 0) {
            float p = HeroMotion.clamp(1f - h.mineSwingT / .22f);
            angle = HeroMotion.swordAngle(rest, aim, p, 2);
        } else if (h.swingT > 0 && h.swingKind == 0) {
            float p = HeroMotion.clamp(1f - h.swingT / .18f);
            angle = HeroMotion.swordAngle(rest, aim, p, 0);
            scale = 1 + .15f * MathUtils.sin(p * MathUtils.PI);
        } else if (h.swingT > 0 && h.swingKind == 1) {
            float p = HeroMotion.clamp(1f - h.swingT / .25f);
            angle = HeroMotion.swordAngle(rest, aim, p, 1);
            scale = 1 + .3f * MathUtils.sin(p * MathUtils.PI);
        }
        batch.setColor(1f, 1f, 1f, alpha);
        if (sword != null && pose instanceof HeroPose heroPose) {
            var frame = heroPose.frame;
            if (!frame.handVisible()) return;
            var data = sword.data;
            float hx = h.x + frame.handOffsetX(h.facing.leftward(), squash);
            float hy = h.y + frame.handOffsetY(squash) + bob;
            batch.draw(sword, hx-data.originX(), hy-data.originY(), data.originX(), data.originY(),
                    data.worldWidth(), data.worldHeight(), scale, scale, angle-data.angleDegrees());
            return; // The painted sealed holy sword already includes its two bindings.
        }
        float hx = h.x + (h.facing.leftward() ? -8 : 8) * HERO_SCALE;
        float hy = h.y + 16 * HERO_SCALE + bob;
        float grip = 6 * HERO_SCALE;
        // 그림 속 칼날은 45도로 누워 있으므로 그만큼 빼고 돌린다. 손잡이(왼쪽 아래)를 회전 중심으로.
        batch.draw(r, hx - grip, hy - grip, grip, grip, 32 * HERO_SCALE, 32 * HERO_SCALE, scale, scale, angle - 45);
        if ("holy_sealed".equals(w.defId) && !art.hasHd("sword_holy") && h.swingT <= 0) {
            batch.setColor(1f, 1f, 1f, alpha * 0.8f);
            batch.draw(art.world("chain"), hx - 4 * HERO_SCALE, hy, 4 * HERO_SCALE, 4 * HERO_SCALE,
                    18 * HERO_SCALE, 18 * HERO_SCALE, 1f, 1f, angle - 45);
        }
    }

    private float heroBob(HeroActor h, boolean measuredWalk) {
        // The measured contact/passing poses already contain the rise of a step.
        // Large extra bobbing would lift the planted foot and look like hopping.
        float amplitude = measuredWalk ? .10f : .65f;
        return walkBlend * amplitude * (1 - MathUtils.cos(h.walkTime * MathUtils.PI2 * 4));
    }

    /** Reserve the top HUD strip for a large boss, without zooming or losing the hero below. */
    public float cameraTargetY(float normalY, float viewHeight, float zoom) {
        Monster boss = g.activeBoss();
        if (boss == null || boss.dead) return normalY;
        // A steady envelope avoids making the camera bob with each animation frame.
        float top = boss.y + art.height(art.world(boss.def.sprite)) * enemyPainter.monsterScale(boss) * 1.12f + 10;
        float required = top + 120 * zoom - viewHeight / 2;
        return MathUtils.clamp(required, normalY, normalY + viewHeight * .23f);
    }

    private void drawGhosts() {
        Particles.additive(batch);
        for (Ghost gh : ghosts) {
            float a = 0.12f * (1f - gh.t / 0.2f);
            batch.setColor(0.45f, 0.7f, 1f, a);
            actorInk.draw(batch, gh.region, gh.x + (gh.flip ? gh.w/2 : -gh.w/2),
                    gh.y-gh.footOffset, gh.flip ? -gh.w : gh.w, gh.h);
        }
        Particles.normal(batch);
        batch.setColor(Color.WHITE);
    }

    // ───────────────────────── 빛나는 효과 (더하기)

    private void drawGlow() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        batch.begin();
        Particles.additive(batch);
        mapPainter.drawGlow();
        // 광맥 반짝임
        for (Room r : map.rooms) {
            if (r.type != RoomType.ORE) continue;
            List<Vein> veins = g.veinsOf(r);
            if (veins == null) continue;
            for (Vein v : veins) {
                float px = veinX(v), py = veinY(v);
                if (!inView(px, py)) continue;
                Color c = oreColor(v.ore);
                float tw = MathUtils.sin(time * 4f + v.tileX * 1.7f + v.tileY);
                float gs = v.painted() ? 84 : 52;
                batch.setColor(c.r, c.g, c.b, 0.25f + 0.15f * tw);
                batch.draw(art.glow, px - gs / 2, py - gs * 0.42f, gs, gs);
                if (tw > 0.85f) {
                    batch.setColor(1f, 1f, 1f, (tw - 0.85f) * 5f);
                    batch.draw(art.white(), px - 1 + (v.tileX % 3) * 4 - 4, py + 8, 2, 6);
                    batch.draw(art.white(), px - 3 + (v.tileX % 3) * 4 - 4, py + 10, 6, 2);
                }
            }
        }
        enemyPainter.drawGlow();
        drawSkillShots();
        particles.drawGlow(batch);
        batch.setColor(Color.WHITE);
        batch.end();

        // 칼 궤적과 충격파 (SpriteBatch.end 가 섞기를 꺼 두므로 다시 켠다)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawSwingTrail();
        for (Ring r : rings) {
            float p = r.t / r.dur;
            float e = 1f - (1f - p) * (1f - p);
            float rad = r.r0 + (r.r1 - r.r0) * e;
            drawRing(r.x, r.y, rad, Math.max(2f, 8f * (1f - p)), r.color, 0.45f * (1f - p));
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Particles.normal(batch);
    }

    /**
     * 검 스킬 (더하기 섞기 안에서 그린다). 지금은 빛 덩어리를 겹쳐 모양만 낸 임시 효과다.
     * 그림이 오면 바꾼다 (docs/ART_REQUESTS.md "검 스킬 효과").
     */
    private void drawSkillShots() {
        for (HeroShot h : g.shots) {
            Color c = skillColor(h.kind);
            if (h.strike()) {
                // 떨어질 자리 예고: 바닥에 납작한 빛
                float p = Math.min(1f, h.age / Math.max(0.01f, h.age + h.delay));
                batch.setColor(c.r, c.g, c.b, 0.25f + 0.35f * p);
                batch.draw(art.glow, h.x - h.radius, h.y - h.radius * 0.6f, h.radius * 2, h.radius * 1.2f);
                continue;
            }
            float vl = (float) Math.sqrt(h.vx * h.vx + h.vy * h.vy);
            float ux = h.vx / vl, uy = h.vy / vl;
            float r = h.radius;
            if ("cleave".equals(h.kind)) {
                // 땅을 가르는 충격파: 지나간 자리를 따라 흙빛 조각이 남는다
                for (int k = 0; k < 6; k++) {
                    float tx = h.x - ux * r * 0.7f * k, ty = h.y - uy * r * 0.7f * k - 6;
                    float a = 0.7f - k * 0.1f;
                    batch.setColor(c.r, c.g, c.b, a);
                    batch.draw(art.glow, tx - r * 1.1f, ty - r * 0.5f, r * 2.2f, r);
                }
                if (MathUtils.random() < 0.6f) particles.debris(h.x, h.y - 8, 1, new Color(0.45f, 0.38f, 0.3f, 1f), 90, 3);
            } else {
                // 초승달·빛의 검기: 진행 방향에 수직으로 길쭉한 빛을 여러 겹
                float px = -uy, py = ux;
                int n = "beam".equals(h.kind) ? 9 : 7;
                for (int k = 0; k < n; k++) {
                    float t = (k - (n - 1) / 2f) / ((n - 1) / 2f);
                    float bend = "crescent".equals(h.kind) ? (1f - t * t) * r * 0.9f : 0f;
                    float sx = h.x + px * t * r * 1.6f + ux * bend, sy = h.y + py * t * r * 1.6f + uy * bend;
                    float sz = r * (1.1f - 0.5f * Math.abs(t));
                    batch.setColor(c.r, c.g, c.b, 0.75f);
                    batch.draw(art.glow, sx - sz, sy - sz, sz * 2, sz * 2);
                }
                batch.setColor(1f, 1f, 1f, 0.8f);
                batch.draw(art.glow, h.x - r * 0.6f, h.y - r * 0.6f, r * 1.2f, r * 1.2f);
            }
        }
        for (float[] b : bolts) {
            float a = 1f - b[2] / 0.3f;
            Color c = skillColor("strike");
            batch.setColor(c.r, c.g, c.b, 0.8f * a);
            batch.draw(art.white(), b[0] - 5, b[1], 10, 520);
            batch.setColor(1f, 1f, 1f, a);
            batch.draw(art.white(), b[0] - 2, b[1], 4, 520);
        }
    }

    /** 바닥에 납작하게 깔리는 고리 (세로를 줄여 비스듬히 보이게). */
    private void drawRing(float cx, float cy, float rad, float thick, Color c, float a) {
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            float a0 = MathUtils.PI2 * i / seg, a1 = MathUtils.PI2 * (i + 1) / seg;
            float ox0 = MathUtils.cos(a0), oy0 = MathUtils.sin(a0) * 0.6f;
            float ox1 = MathUtils.cos(a1), oy1 = MathUtils.sin(a1) * 0.6f;
            float ri = rad - thick;
            shapes.setColor(c.r, c.g, c.b, a);
            shapes.triangle(cx + ox0 * rad, cy + oy0 * rad, cx + ox1 * rad, cy + oy1 * rad, cx + ox0 * ri, cy + oy0 * ri);
            shapes.triangle(cx + ox1 * rad, cy + oy1 * rad, cx + ox1 * ri, cy + oy1 * ri, cx + ox0 * ri, cy + oy0 * ri);
        }
    }

    /** 칼이 지나간 자리에 남는 초승달 모양 잔상. 칼끝 쪽이 진하고 뒤로 갈수록 옅다. */
    private void drawSwingTrail() {
        HeroActor h = g.hero;
        if (h.swingT <= 0) return;
        Weapon w = g.state.equipped();
        Color c = swingColor(w == null ? "" : w.defId);
        float cx = h.x, cy = h.centerY();
        float aim = MathUtils.atan2(h.aimY, h.aimX) * MathUtils.radDeg;
        float start, end, outer, inner, alpha;
        if (h.swingKind == 0) {
            float p = 1f - h.swingT / 0.18f;
            float e = 1f - (1f - p) * (1f - p) * (1f - p);
            float arc = g.balance.attack.basicArcDegrees;
            start = aim + arc / 2f;
            end = aim + arc / 2f - arc * e;
            outer = g.basicRange();
            inner = outer * 0.5f;
            alpha = 0.85f * Math.min(1f, h.swingT / 0.08f);
        } else {
            return;
        }
        int seg = 22;
        for (int i = 0; i < seg; i++) {
            float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
            float a0 = (start + (end - start) * t0) * MathUtils.degRad, a1 = (start + (end - start) * t1) * MathUtils.degRad;
            float al0 = alpha * t0 * t0, al1 = alpha * t1 * t1;
            float o0x = cx + MathUtils.cos(a0) * outer, o0y = cy + MathUtils.sin(a0) * outer;
            float o1x = cx + MathUtils.cos(a1) * outer, o1y = cy + MathUtils.sin(a1) * outer;
            float i0x = cx + MathUtils.cos(a0) * inner, i0y = cy + MathUtils.sin(a0) * inner;
            float i1x = cx + MathUtils.cos(a1) * inner, i1y = cy + MathUtils.sin(a1) * inner;
            Color co0 = new Color(c.r, c.g, c.b, al0), co1 = new Color(c.r, c.g, c.b, al1);
            Color ci0 = new Color(c.r, c.g, c.b, 0f), ci1 = new Color(c.r, c.g, c.b, 0f);
            shapes.triangle(o0x, o0y, o1x, o1y, i0x, i0y, co0, co1, ci0);
            shapes.triangle(o1x, o1y, i1x, i1y, i0x, i0y, co1, ci1, ci0);
            // 칼끝을 따라 밝은 선
            Color w0 = new Color(1f, 1f, 1f, al0 * 0.9f), w1 = new Color(1f, 1f, 1f, al1 * 0.9f);
            float m0x = cx + MathUtils.cos(a0) * (outer - 5), m0y = cy + MathUtils.sin(a0) * (outer - 5);
            float m1x = cx + MathUtils.cos(a1) * (outer - 5), m1y = cy + MathUtils.sin(a1) * (outer - 5);
            shapes.triangle(o0x, o0y, o1x, o1y, m0x, m0y, w0, w1, w0);
            shapes.triangle(o1x, o1y, m1x, m1y, m0x, m0y, w1, w1, w0);
        }
    }

    // ───────────────────────── 체력 막대

    /** 잠 (선우 버전): 천천히 어두워졌다가 밝아지고, 침대 위로 Zzz 가 올라간다. */
    private void drawSleepOverlay(OrthographicCamera cam) {
        float p = g.sleepProgress();
        batch.setColor(0.01f, 0.01f, 0.04f, MathUtils.sin(p * MathUtils.PI) * 0.85f);
        batch.draw(art.white(), 0, 0, cam.viewportWidth, cam.viewportHeight);
        batch.setColor(Color.WHITE);
        com.yongsa.map.Prop bed = g.sleepBed();
        float bx = Float.isNaN(bed.sleepX) ? bed.centerX(T) : bed.sleepX;
        float by = (Float.isNaN(bed.sleepY) ? bed.centerY(T) : bed.sleepY) + 20;
        for (int i = 0; i < 3; i++) {
            float ph = (p * 3 + i / 3f) % 1f;
            BitmapFont f = i == 0 ? fonts.large : fonts.normal;
            outlined(f, i == 0 ? "Z" : "z", sx(cam, bx + 14 + ph * 22), sy(cam, by + 10 + ph * 40), SLEEP_Z, 1f - ph);
        }
    }

    private static final Color SLEEP_Z = new Color(0.85f, 0.9f, 1f, 1f);

    // ───────────────────────── 글자 (화면 좌표로 그려 글자가 흐려지지 않게 한다)

    private float sx(OrthographicCamera cam, float wx) {
        return (wx - viewX0) / cam.zoom;
    }

    private float sy(OrthographicCamera cam, float wy) {
        return (wy - viewY0) / cam.zoom;
    }

    private void drawTexts(OrthographicCamera cam) {
        if (g.sleeping()) drawSleepOverlay(cam);
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            if (m.warning()) {
                float top = enemyPainter.monsterTop(m) + 16;
                float pop = 1f + 0.15f * MathUtils.sin(time * 30f);
                layout.setText(fonts.large, "!");
                outlined(fonts.large, "!", sx(cam, m.x) - layout.width / 2f, sy(cam, top) + layout.height * pop, Ui.RED, 1f);
            }
        }
        for (FloatText ft : floats) {
            BitmapFont font = ft.big ? fonts.large : fonts.normal;
            float a = 1f - Math.max(0, ft.t - 0.6f) / 0.3f;
            layout.setText(font, ft.text);
            outlined(font, ft.text, sx(cam, ft.x) - layout.width / 2f, sy(cam, ft.y) + layout.height, ft.color, a);
        }
        for (Speech sp : speeches) {
            float a = Math.min(1f, sp.t * 4f) * Math.min(1f, (3.6f - sp.t) * 2f);
            layout.setText(fonts.normal, sp.text);
            float w = layout.width + 28, hgt = layout.height + 20;
            float x = sx(cam, sp.x) - w / 2f, y = sy(cam, sp.y);
            boolean friend = sp.speaker != null; // 용사·마을 사람은 금빛 테두리와 이름
            batch.setColor(friend ? 0.05f : 0.06f, friend ? 0.04f : 0.02f, friend ? 0.07f : 0.1f, 0.85f * a);
            batch.draw(art.white(), x, y, w, hgt);
            batch.draw(art.white(), x + w / 2f - 6, y - 8, 12, 8);
            Color edge = friend ? Ui.GOLD : EnemyPainter.PROJ_DARK;
            batch.setColor(edge.r, edge.g, edge.b, 0.9f * a);
            batch.draw(art.white(), x, y + hgt - 2, w, 2);
            batch.setColor(Color.WHITE);
            if (friend) {
                fonts.small.setColor(Ui.GOLD.r, Ui.GOLD.g, Ui.GOLD.b, a);
                fonts.small.draw(batch, sp.speaker, x + 10, y + hgt + 20);
                fonts.small.setColor(Color.WHITE);
            }
            fonts.normal.setColor(friend ? 1f : 1f, friend ? 0.97f : 0.9f, friend ? 0.9f : 1f, a);
            fonts.normal.draw(batch, sp.text, x + 14, y + hgt - 10);
        }
        if (g.prompt != null) {
            layout.setText(fonts.normal, g.prompt);
            float w = layout.width + 24, hgt = layout.height + 16;
            float x = sx(cam, g.promptX) - w / 2f, y = sy(cam, g.promptY);
            batch.setColor(0.05f, 0.04f, 0.07f, 0.8f);
            batch.draw(art.white(), x, y, w, hgt);
            batch.setColor(Ui.GOLD.r, Ui.GOLD.g, Ui.GOLD.b, 0.8f);
            batch.draw(art.white(), x, y, w, 2);
            batch.setColor(Color.WHITE);
            fonts.normal.setColor(Ui.GOLD);
            fonts.normal.draw(batch, g.prompt, x + 12, y + hgt - 8);
        }
        fonts.normal.setColor(Color.WHITE);
        fonts.large.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);
    }

    private void outlined(BitmapFont font, String text, float x, float y, Color c, float a) {
        font.setColor(0, 0, 0, 0.85f * a);
        font.draw(batch, text, x - 2, y);
        font.draw(batch, text, x + 2, y);
        font.draw(batch, text, x, y - 2);
        font.draw(batch, text, x, y + 2);
        font.setColor(c.r, c.g, c.b, a);
        font.draw(batch, text, x, y);
    }

    // ───────────────────────── 색

    private Color swingColor(String defId) {
        switch (defId) {
            case "blackiron": return new Color(0.75f, 0.85f, 1f, 1f);
            case "bloodmoon": return new Color(1f, 0.35f, 0.45f, 1f);
            case "skyfall": return new Color(0.45f, 0.85f, 1f, 1f);
            case "holy_awakened": return new Color(1f, 0.92f, 0.5f, 1f);
            default: return new Color(0.95f, 0.95f, 1f, 1f);
        }
    }

    private static Color oreColor(OreType t) {
        if (t == null) return new Color(1f, 1f, 1f, 1f);
        switch (t) {
            case BLACKIRON: return new Color(0.62f, 0.72f, 0.95f, 1f);
            case BLOODMOON: return new Color(1f, 0.3f, 0.4f, 1f);
            default: return new Color(0.45f, 0.85f, 1f, 1f);
        }
    }

    @Override
    public void dispose() {
        lighting.dispose();
        mapPainter.dispose();
        spriteBlend.dispose();
        actorInk.dispose();
        if (potionTex != null) potionTex.dispose();
    }
}
