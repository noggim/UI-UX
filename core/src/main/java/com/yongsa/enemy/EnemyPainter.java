package com.yongsa.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.yongsa.gfx.ActorInk;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.DepthSink;
import com.yongsa.gfx.Effects;
import com.yongsa.gfx.Lighting;
import com.yongsa.gfx.Particles;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 몬스터·보스 그리기 (보스·몬스터 담당). 몬스터 그림과 보스 동작 시트, 쓰러지는 모습,
 * 바닥 공격 예고, 투사체와 기운의 빛, 몬스터 체력 막대, 몬스터·보스 이벤트 효과를 맡는다.
 * WorldRenderer 가 그리는 순서에 맞춰 부른다.
 */
public class EnemyPainter {
    private static final int S = Art.SCALE;

    private final GameSession g;
    private final Art art;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final ActorInk actorInk;
    private final Effects fx;
    private final Particles particles;
    private final int T;
    private float time;
    private float viewX0, viewY0, viewX1, viewY1;

    public EnemyPainter(GameSession g, Art art, SpriteBatch batch, ShapeRenderer shapes, ActorInk actorInk, Effects fx) {
        this.g = g;
        this.art = art;
        this.batch = batch;
        this.shapes = shapes;
        this.actorInk = actorInk;
        this.fx = fx;
        this.particles = fx.particles();
        this.T = g.balance.tileSize;
    }

    public void setView(float x0, float y0, float x1, float y1) {
        viewX0 = x0;
        viewY0 = y0;
        viewX1 = x1;
        viewY1 = y1;
    }

    private boolean inView(float x, float y) {
        return x > viewX0 - 64 && x < viewX1 + 64 && y > viewY0 - 64 && y < viewY1 + 64;
    }

    private void flash(float x, float y, float radius, Color c, float dur) {
        fx.flash(x, y, radius, c, dur);
    }

    private void ring(float x, float y, float r0, float r1, float dur, Color c) {
        fx.ring(x, y, r0, r1, dur, c);
    }

    private void drawActorShadow(float x, float y, float width, float alpha, boolean flying) {
        fx.actorShadow(x, y, width, alpha, flying);
    }

    /** 몬스터·보스 이벤트의 효과. 맡은 이벤트면 true. */
    public boolean onEvent(GameEvent e) {
        switch (e.type) {
            case MONSTER_DIED: {
                Corpse c = new Corpse();
                c.region = art.world(e.sprite);
                float scale = S * e.size * (e.boss ? bossScale(e.sprite) : mobScale(e.sprite));
                c.w = art.width(c.region) * scale;
                c.h = art.height(c.region) * scale;
                c.x = e.x;
                c.y = e.y;
                c.flip = e.flip;
                c.boss = e.boss;
                c.dur = e.boss ? 1.4f : 0.4f;
                if (e.boss && bossSheet(e.sprite, e.phase2) != null) {
                    c.sheet = bossSheet(e.sprite, e.phase2);
                    c.sheetScale = bossSheetScale("demonlord".equals(e.sprite), e.phase2);
                    c.sheetFaceRight = e.dirX > 0.2f;
                    c.dur = 2.2f;
                    c.h = BOSS_BODY * c.sheetScale;
                    c.w = BOSS_BODY * c.sheetScale;
                }
                corpses.add(c);
                Color tint = deathColor(e.sprite);
                particles.smoke(e.x, e.y + c.h * 0.3f, e.boss ? 26 : 9, new Color(0.25f, 0.22f, 0.3f, 0.7f), c.w * 0.4f, e.boss ? 26 : 12, e.boss ? 1.4f : 0.7f);
                particles.burst(e.x, e.y + c.h * 0.35f, e.boss ? 60 : 16, tint, e.boss ? 380 : 220, 0.6f, 3, true);
                particles.debris(e.x, e.y + 4, e.boss ? 24 : 6, tint, e.boss ? 200 : 120, 4);
                flash(e.x, e.y + c.h * 0.35f, e.boss ? 400 : 120, tint, e.boss ? 0.8f : 0.25f);
                if (e.boss) ring(e.x, e.y, 10, 360, 0.9f, tint);
                break;
            }
            case SLAM_FX:
                ring(e.x, e.y, e.size * 0.3f, e.size * 1.15f, 0.45f, new Color(1f, 0.55f, 0.3f, 1f));
                particles.debris(e.x, e.y, 18, new Color(0.45f, 0.4f, 0.38f, 1f), 260, 5);
                particles.smoke(e.x, e.y, 14, new Color(0.4f, 0.36f, 0.34f, 0.55f), e.size * 0.8f, 18, 0.8f);
                flash(e.x, e.y, e.size * 2.2f, new Color(1f, 0.5f, 0.25f, 1f), 0.3f);
                break;
            case BLINK_FX:
                particles.burst(e.x, e.y, 18, new Color(0.75f, 0.4f, 1f, 1f), 180, 0.45f, 3, true);
                particles.smoke(e.x, e.y - 8, 5, new Color(0.3f, 0.15f, 0.4f, 0.6f), 14, 12, 0.5f);
                flash(e.x, e.y, 90, new Color(0.7f, 0.35f, 1f, 1f), 0.2f);
                break;
            case SHIELD_UP:
                ring(e.x, e.y - 10, e.size * 1.4f, e.size * 0.6f, 0.5f, SHIELD_COLOR);
                particles.burst(e.x, e.y, 24, SHIELD_COLOR, 160, 0.6f, 3, true);
                flash(e.x, e.y, e.size * 2.5f, SHIELD_COLOR, 0.4f);
                break;
            case SHIELD_BREAK:
                ring(e.x, e.y - 10, e.size * 0.5f, e.size * 2.2f, 0.6f, SHIELD_COLOR);
                particles.debris(e.x, e.y, 26, SHIELD_COLOR, 300, 5);
                particles.burst(e.x, e.y, 30, Color.WHITE, 260, 0.5f, 3, true);
                flash(e.x, e.y, e.size * 3f, SHIELD_COLOR, 0.5f);
                break;
            case HAZARD_FX:
                hazardFx(e);
                break;
            case BOSS_PHASE2:
                ring(e.x, e.y - 20, 20, 420, 1.2f, PROJ_DARK);
                ring(e.x, e.y - 20, 10, 260, 0.9f, new Color(1f, 0.3f, 0.6f, 1f));
                particles.burst(e.x, e.y, 70, PROJ_DARK, 380, 0.9f, 3, true);
                particles.smoke(e.x, e.y, 30, new Color(0.25f, 0.08f, 0.35f, 0.75f), 80, 28, 1.6f);
                flash(e.x, e.y, 520, PROJ_DARK, 1.0f);
                break;
            case BOSS_ENRAGED:
                ring(e.x, e.y - 20, 20, 300, 0.8f, new Color(1f, 0.25f, 0.15f, 1f));
                particles.burst(e.x, e.y, 40, new Color(1f, 0.3f, 0.2f, 1f), 320, 0.7f, 3, true);
                flash(e.x, e.y, 360, new Color(1f, 0.2f, 0.1f, 1f), 0.6f);
                break;
            default:
                return false;
        }
        return true;
    }

    /** 용사가 쓰러지면 남은 효과를 지운다. */
    public void clear() {
        eyeFlashes.clear();
        corpses.clear();
    }

    /** 잠을 자면 눈 효과만 지운다. */
    public void clearEyes() {
        eyeFlashes.clear();
    }

    /** 매 프레임: 눈·시체·등장 효과와 피웅덩이 거품. */
    public void update(float dt, float time) {
        this.time = time;
        for (float[] e : eyeFlashes) e[3] += dt;
        eyeFlashes.removeIf(e -> e[3] >= 0.45f);
        // 피웅덩이에서 피어오르는 거품
        for (Hazard h : g.hazards) {
            if (h.kind == Hazard.Kind.POOL && h.triggered && MathUtils.random() < dt * 6f) {
                float a = MathUtils.random(MathUtils.PI2), r = MathUtils.random(h.radius * 0.8f);
                particles.burst(h.x + MathUtils.cos(a) * r, h.y + MathUtils.sin(a) * r * 0.6f, 2, PROJ_BLOOD, 40, 0.5f, 2, false);
            }
        }
        for (Iterator<Corpse> it = corpses.iterator(); it.hasNext(); ) {
            Corpse c = it.next();
            c.t += dt;
            if (c.boss && c.t < c.dur * 0.8f && MathUtils.random() < dt * 14f) {
                float px = c.x + MathUtils.random(-c.w / 3f, c.w / 3f), py = c.y + MathUtils.random(c.h * 0.7f);
                particles.burst(px, py, 8, new Color(1f, 0.6f, 0.35f, 1f), 200, 0.4f, 3, true);
                flash(px, py, 120, new Color(1f, 0.7f, 0.4f, 1f), 0.15f);
            }
            if (c.t > c.dur) it.remove();
        }
        // 몬스터 등장
        for (Monster m : g.monsters) {
            if (m.dead || ages.containsKey(m)) continue;
            if (m.state == Monster.State.SPAWN) {
                // 마법진 위로 빛 알갱이가 모인다
                if (MathUtils.random() < 0.5f) {
                    Color c = spawnColor(m);
                    Particles.P p = particles.spawn(m.x + MathUtils.random(-m.hurtRadius, m.hurtRadius), m.y + MathUtils.random(-4f, 4f), 0.5f, 2, c);
                    p.vz = 60f;
                    p.glow = true;
                }
                continue;
            }
            ages.put(m, 0f);
            particles.smoke(m.x, m.y + 8, m.boss ? 22 : 8, new Color(0.3f, 0.25f, 0.4f, 0.75f), m.boss ? 40 : 12, m.boss ? 22 : 12, 0.7f);
            particles.burst(m.x, m.y + 10, m.boss ? 30 : 8, new Color(0.7f, 0.5f, 1f, 1f), 160, 0.5f, 3, true);
        }
        for (Iterator<Map.Entry<Monster, Float>> it = ages.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Monster, Float> en = it.next();
            if (!g.monsters.contains(en.getKey())) it.remove();
            else en.setValue(en.getValue() + dt);
        }
    }

    /** 몬스터·보스와 바닥 공격의 불빛. blind 는 실명 정도 (0~1). */
    public void addLights(Lighting lighting, float blind) {
        if (blind > 0) {
            for (Monster m : g.monsters) {
                if (m.dead || !m.boss) continue;
                lighting.add(m.x, m.y + 70, 70, 1f, 0.15f, 0.2f, 0.9f * blind);
            }
            // 캄캄한 동안 마법탄도 붉게 빛난다
            for (Projectile p : g.projectiles) {
                if (!inView(p.x, p.y)) continue;
                lighting.add(p.x, p.y, 40 + p.radius * 3f, 1f, 0.15f, 0.2f, 0.8f * blind);
            }
        }
        for (Hazard hz : g.hazards) {
            if (!inView(hz.x, hz.y)) continue;
            Color c = hazardColor(hz.style);
            if (hz.kind == Hazard.Kind.POOL) lighting.add(hz.x, hz.y, hz.radius * 1.6f, c.r, c.g, c.b, 0.35f);
            else lighting.add(hz.x, hz.y, 60 + 60 * hz.progress(), c.r, c.g, c.b, 0.25f + 0.3f * hz.progress());
        }
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            if (m.state == Monster.State.SLAM_TELEGRAPH) {
                lighting.add(m.slamX, m.slamY, m.slamRadius * 1.6f, 1f, 0.25f, 0.15f, 0.55f);
            }
            if (m.finalBoss) lighting.add(m.x, m.y + 40, 260, 0.7f, 0.3f, 1f, 0.6f);
            if (m.enraged) lighting.add(m.x, m.y + 30, 200, 1f, 0.2f, 0.15f, 0.35f + 0.15f * MathUtils.sin(time * 8f));
            if (m.state == Monster.State.SPAWN) {
                Color c = spawnColor(m);
                lighting.add(m.x, m.y, 110, c.r, c.g, c.b, 0.6f * m.progress());
            }
        }
        for (Projectile p : g.projectiles) {
            Color c = projectileColor(p.source);
            lighting.add(p.x, p.y, 90, c.r, c.g, c.b, 0.7f);
        }
    }

    /** 깊이 정렬에 끼는 것: 마왕의 눈, 몬스터, 쓰러지는 몬스터. */
    public void collect(DepthSink out) {
        for (Hazard h : g.hazards) {
            if (h.kind != Hazard.Kind.EYE || h.dead || !inView(h.x, h.y)) continue;
            // The fully-open frame is reserved for the actual trigger, not the warning.
            int frame = Math.min(2, (int) (MathUtils.clamp(h.progress(), 0f, 1f) * 3f));
            out.add(h.y, () -> drawEye(h.x, h.y, h.radius, frame, 1f));
        }
        for (float[] e : eyeFlashes) {
            if (!inView(e[0], e[1])) continue;
            out.add(e[1], () -> drawEye(e[0], e[1], e[2], 3, Math.min(1f, (0.45f - e[3]) / 0.18f)));
        }
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            out.add(m.y, () -> drawMonster(m));
        }
        for (Corpse c : corpses) out.add(c.y, () -> drawCorpse(c));
    }

    /** 빛나는 효과 (더하기 섞기 batch 안에서): 기운, 방패, 모으는 빛, 낙뢰 예고, 투사체. */
    public void drawGlow() {
        // 마왕의 기운
        for (Monster m : g.monsters) {
            if (m.dead || !m.finalBoss) continue;
            float k = (m.phase2 || m.state == Monster.State.PHASE_SHIFT ? 1.4f : 1f) * bossScale(m.def.sprite);
            batch.setColor(0.6f, 0.2f, 1f, (0.35f + 0.1f * MathUtils.sin(time * 5f)) * k);
            batch.draw(art.glow, m.x - 90 * k, m.y - 20, 180 * k, 160 * k);
        }
        // 방패, 긴 패턴의 기운
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            if (m.shield > 0) {
                float fr = m.shieldMax > 0 ? (float) m.shield / m.shieldMax : 1f;
                float r = m.hurtRadius * 2.6f * (m.boss ? bossScale(m.def.sprite) : mobScale(m.def.sprite));
                float pulse = 0.8f + 0.2f * MathUtils.sin(time * 4f);
                batch.setColor(SHIELD_COLOR.r, SHIELD_COLOR.g, SHIELD_COLOR.b, (0.18f + 0.25f * fr) * pulse);
                batch.draw(art.glow, m.x - r, m.y - r * 0.2f, r * 2, r * 1.9f);
            }
            if (m.guardActive()) {
                // 해골병 방패: 바라보는 쪽에 막이 보인다
                float gr = m.hurtRadius * 1.6f * mobScale(m.def.sprite);
                float gx = m.x + m.dirX * m.hurtRadius * 1.2f, gy = m.centerY() + m.dirY * m.hurtRadius * 0.8f;
                batch.setColor(SHIELD_COLOR.r, SHIELD_COLOR.g, SHIELD_COLOR.b, 0.45f);
                batch.draw(art.glow, gx - gr, gy - gr, gr * 2, gr * 2);
            }
            if (m.state == Monster.State.CHANNEL) {
                Color c = "lightning".equals(m.channelKind) ? PROJ_SKY : "firespin".equals(m.channelKind) ? PROJ_FIRE
                        : "bloodpool".equals(m.channelKind) || "spearstand".equals(m.channelKind) ? PROJ_BLOOD : new Color(1f, 0.6f, 0.35f, 1f);
                float r = m.hurtRadius * 2.4f * (m.boss ? bossScale(m.def.sprite) : mobScale(m.def.sprite)) * (0.9f + 0.1f * MathUtils.sin(time * 10f));
                batch.setColor(c.r, c.g, c.b, 0.4f);
                batch.draw(art.glow, m.x - r, m.centerY() - r, r * 2, r * 2);
            }
        }
        // 낙뢰가 떨어지기 직전 하늘에서 모이는 빛
        for (Hazard h : g.hazards) {
            if (h.kind != Hazard.Kind.CIRCLE || !inView(h.x, h.y)) continue;
            Color c = hazardColor(h.style);
            float p = h.progress();
            if ("bolt".equals(h.style)) {
                batch.setColor(c.r, c.g, c.b, 0.15f + 0.45f * p);
                batch.draw(art.glow, h.x - 10, h.y, 20, 260 * p);
            }
            batch.setColor(c.r, c.g, c.b, 0.2f * p);
            batch.draw(art.glow, h.x - h.radius, h.y - h.radius * 0.6f, h.radius * 2, h.radius * 1.2f);
        }
        // 몬스터 투사체와 모으는 빛, 기절 별
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            if (m.state == Monster.State.SPAWN) {
                Color c = spawnColor(m);
                float p = m.progress();
                float r = m.hurtRadius * 2.2f * (m.boss ? bossScale(m.def.sprite) : mobScale(m.def.sprite));
                batch.setColor(c.r, c.g, c.b, 0.55f * Math.min(1f, p * 2.5f));
                batch.draw(art.glow, m.x - r, m.y - r * 0.45f, r * 2, r * 0.9f);
            } else if (m.state == Monster.State.CAST_TELEGRAPH) {
                Color c = projectileColor(m.def.sprite);
                float p = m.progress();
                float sz = (m.boss ? 70 * bossScale(m.def.sprite) : 34 * mobScale(m.def.sprite)) * (0.4f + p);
                batch.setColor(c.r, c.g, c.b, 0.35f + 0.5f * p);
                batch.draw(art.glow, m.x - sz / 2f, m.centerY() + m.hurtRadius * 0.4f - sz / 2f, sz, sz);
            } else if (m.state == Monster.State.STUNNED) {
                float top = monsterTop(m) + 4;
                for (int i = 0; i < 3; i++) {
                    float a = time * 5f + i * MathUtils.PI2 / 3f;
                    float px = m.x + MathUtils.cos(a) * m.hurtRadius * 0.7f, py = top + MathUtils.sin(a) * 5f;
                    batch.setColor(1f, 0.9f, 0.4f, 0.9f);
                    batch.draw(art.glow, px - 7, py - 7, 14, 14);
                    batch.draw(art.white(), px - 1, py - 1, 3, 3);
                }
            }
        }
        for (Projectile p : g.projectiles) {
            if ("crescent".equals(p.source) && drawCrescent(p)) continue;
            Color c = projectileColor(p.source);
            float r = p.radius;
            float pulse = 1f + 0.15f * MathUtils.sin(time * 25f + p.x);
            batch.setColor(c.r, c.g, c.b, 0.75f);
            batch.draw(art.glow, p.x - r * 3.2f * pulse, p.y - r * 3.2f * pulse, r * 6.4f * pulse, r * 6.4f * pulse);
            // 꼬리
            float vl = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
            for (int k = 1; k <= 3; k++) {
                float tx = p.x - p.vx / vl * r * 1.4f * k, ty = p.y - p.vy / vl * r * 1.4f * k;
                float ts = r * (2.2f - k * 0.45f);
                batch.setColor(c.r, c.g, c.b, 0.45f - k * 0.1f);
                batch.draw(art.glow, tx - ts, ty - ts, ts * 2, ts * 2);
            }
            batch.setColor(1f, 0.95f, 0.85f, 1f);
            batch.draw(art.glow, p.x - r, p.y - r, r * 2, r * 2);
        }
    }

    private static final float DEFAULT_MOB_SCALE = 1.25f;

    private final List<Corpse> corpses = new ArrayList<>();

    private final Map<Monster, Float> ages = new IdentityHashMap<>();

    private final List<float[]> eyeFlashes = new ArrayList<>(); // x, y, radius, age

    private static class Corpse {
        TextureRegion region;
        float x, y, w, h, t, dur;
        boolean flip, boss;
        /** 지용 버전 보스 동작 시트가 있으면 쓰러지는 줄을 돌린다. */
        String sheet;
        float sheetScale;
        boolean sheetFaceRight;
    }

    // 256px 칸 8열 x 5행, 왼쪽을 보는 그림, 발은 칸의 (128, 224).
    // 1행: 대기 4 + 피격 4 · 2행: 이동 8 · 3행: 기본 공격 8 (앞 4칸 준비) · 4행: 특수 공격 8 · 5행: 쓰러짐 8
    private static final int BOSS_CELL = 256, BOSS_FOOT = 224, BOSS_BODY = 128;

    private static final float BOSS_SHEET_SCALE = 0.75f;

    private final java.util.IdentityHashMap<Monster, float[]> bossAnim = new java.util.IdentityHashMap<>();

    /** 보스 그림 이름 → 동작 시트 (파일이 없으면 null: 한 장짜리 그림을 쓴다). */
    private static String bossSheet(String sprite, boolean phase2) {
        if ("demonlord".equals(sprite) && phase2) {
            String trueForm = "team/jiyong/boss_lich2.png";
            if (SHEET_EXISTS.computeIfAbsent(trueForm, k -> Gdx.files.internal(k).exists())) return trueForm;
        }
        String path;
        switch (sprite) {
            case "golem": path = "team/jiyong/boss_golem.png"; break;
            case "knight": path = "team/jiyong/boss_knight.png"; break;
            // 지용 버전 리치(해골 사제) 그림은 마왕으로 쓴다 (2026-10-14 해성 결정). 탑 보스는 원래 그림.
            case "demonlord": path = "team/jiyong/boss_lich.png"; break;
            default: return null;
        }
        return SHEET_EXISTS.computeIfAbsent(path, k -> Gdx.files.internal(k).exists()) ? path : null;
    }

    private static final java.util.Map<String, Boolean> SHEET_EXISTS = new java.util.HashMap<>();

    private TextureRegion bossCell(String sheet, int row, int col) {
        return art.cell(sheet, 8, 5, row * 8 + Math.min(7, Math.max(0, col)));
    }

    /**
     * 보스의 지금 자세 (행, 열). 공격은 준비 동안 앞 4칸, 친 뒤(RECOVER)에는 뒤 4칸.
     * 기본 공격(2행)과 특수 공격(4행 · 내려찍기·마법·긴 패턴)을 나눈다.
     */
    private int[] bossPose(Monster m) {
        if (bossAnim.size() > 16) bossAnim.keySet().removeIf(x -> x.dead);
        float[] a = bossAnim.computeIfAbsent(m, k -> new float[]{m.x, m.y, 2, 0, 0});
        // a: 지난 x, y, 마지막 공격 줄, 움직인 시간, 지난 상태
        boolean moved = Math.abs(m.x - a[0]) + Math.abs(m.y - a[1]) > 0.05f;
        a[0] = m.x;
        a[1] = m.y;
        if (moved) a[3] += Gdx.graphics.getDeltaTime();
        int four = Math.min(3, (int) (m.progress() * 4));
        switch (m.state) {
            case TELEGRAPH: case COMBO_TELEGRAPH: case CHARGE_TELEGRAPH:
                a[2] = 2;
                return new int[]{2, four};
            case SLAM_TELEGRAPH: case CAST_TELEGRAPH:
                a[2] = 3;
                return new int[]{3, four};
            case RECOVER:
                return new int[]{(int) a[2], 4 + four};
            case CHANNEL:
                return new int[]{3, 4 + ((int) (time * 8)) % 4};
            case CHARGING:
                return new int[]{1, ((int) (time * 14)) % 8};
            case STUNNED:
                return new int[]{0, 4 + ((int) (time * 5)) % 4};
            default:
                if (moved) return new int[]{1, ((int) (a[3] * 7)) % 8};
                return new int[]{0, ((int) (time * 5.5f)) % 4};
        }
    }

    /** 동작 시트 크기: 마왕은 다른 보스보다 크고, 2페이즈(본모습)에서 더 커진다. */
    private float sheetScale(Monster m) {
        return bossSheetScale(m.finalBoss, m.phase2);
    }

    private static float bossSheetScale(boolean finalBoss, boolean phase2) {
        return BOSS_SHEET_SCALE * (finalBoss ? (phase2 ? 1.35f : 1.18f) : 1f);
    }

    private void drawBossSheet(Monster m, String sheet) {
        int[] pose = bossPose(m);
        TextureRegion r = bossCell(sheet, pose[0], pose[1]);
        float appear = m.state == Monster.State.SPAWN ? Math.max(0f, (m.progress() - 0.35f) / 0.65f) : 1f;
        if (appear <= 0f) return;
        float k = sheetScale(m) * appear;
        float size = BOSS_CELL * k;
        drawActorShadow(m.x, m.y, BOSS_BODY * k * 0.8f, appear, false);
        float jx = m.state == Monster.State.STUNNED ? MathUtils.sin(time * 14f) * 1.5f
                : m.state == Monster.State.CHARGE_TELEGRAPH ? MathUtils.sin(time * 60f) * 2f * m.progress() : 0;
        boolean faceRight = m.dirX > 0.2f;
        float lift = m.state == Monster.State.LEAP ? MathUtils.sin(m.progress() * MathUtils.PI) * 70f : 0f; // 뒤로 도약
        float x0 = m.x + jx - size / 2f, y0 = m.y - (BOSS_CELL - BOSS_FOOT) * k + lift;
        if (m.warning()) batch.setColor(1f, 0.8f, 0.7f, appear);
        else if (m.finalBoss && m.phase2 && sheet.endsWith("boss_lich.png")) batch.setColor(0.95f, 0.62f + 0.08f * MathUtils.sin(time * 6f), 0.95f, appear); // 시트가 없는 경우만 임시 색상을 쓴다.
        else if (m.enraged) batch.setColor(1f, 0.75f + 0.1f * MathUtils.sin(time * 8f), 0.7f, appear);
        else batch.setColor(1f, 1f, 1f, appear);
        actorInk.draw(batch, r, faceRight ? x0 + size : x0, y0, faceRight ? -size : size, size);
        if (m.flash > 0) {
            Particles.additive(batch);
            batch.setColor(1f, 1f, 1f, Math.min(1f, m.flash / 0.15f));
            for (int i = 0; i < 2; i++) {
                if (faceRight) batch.draw(r, x0 + size, y0, -size, size);
                else batch.draw(r, x0, y0, size, size);
            }
            Particles.normal(batch);
        }
        batch.setColor(Color.WHITE);
    }

    public void drawGroundEffects() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawHazards();
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            float prog = m.progress();
            float blink = 0.16f + 0.08f * MathUtils.sin(time * 20f);
            switch (m.state) {
                case SLAM_TELEGRAPH:
                    shapes.setColor(0.9f, 0.1f, 0.05f, blink);
                    shapes.circle(m.slamX, m.slamY, m.slamRadius, 56);
                    shapes.setColor(1f, 0.2f, 0.1f, 0.3f);
                    shapes.circle(m.slamX, m.slamY, m.slamRadius * prog, 56);
                    break;
                case CHARGE_TELEGRAPH:
                case SWOOP_TELEGRAPH: {
                    // 돌진할 길을 바닥에 미리 보여 준다
                    boolean charge = m.state == Monster.State.CHARGE_TELEGRAPH;
                    float len = charge ? g.balance.monster.chargeMaxTiles * T : g.balance.monster.swoopDistanceTiles * T;
                    float wid = m.hurtRadius * 1.7f;
                    float ex = m.x + m.lockX * len, ey = m.y + m.lockY * len;
                    shapes.setColor(0.9f, 0.1f, 0.05f, blink);
                    shapes.rectLine(m.x, m.y, ex, ey, wid);
                    shapes.setColor(1f, 0.25f, 0.1f, 0.32f);
                    shapes.rectLine(m.x, m.y, m.x + m.lockX * len * prog, m.y + m.lockY * len * prog, wid);
                    break;
                }
                case TELEGRAPH:
                case COMBO_TELEGRAPH: {
                    // 휘두를 앞쪽 부채꼴
                    float reach = g.balance.monster.attackReachTiles * T + m.hw + 10
                            + (m.state == Monster.State.COMBO_TELEGRAPH ? g.balance.monster.comboStepTiles * T + 0.6f * T : 0);
                    if ("brute".equals(m.ai())) reach += g.balance.monster.lungeTiles * T;
                    float ang = MathUtils.atan2(m.dirY, m.dirX) * MathUtils.radDeg;
                    shapes.setColor(0.9f, 0.1f, 0.05f, blink * 0.9f);
                    shapes.arc(m.x, m.y, reach, ang - 78, 156, 24);
                    shapes.setColor(1f, 0.25f, 0.1f, 0.25f);
                    shapes.arc(m.x, m.y, reach * prog, ang - 78, 156, 24);
                    break;
                }
                case SPAWN: {
                    Color c = spawnColor(m);
                    float r = m.hurtRadius * 1.6f;
                    shapes.setColor(c.r * 0.3f, c.g * 0.3f, c.b * 0.3f, 0.5f * Math.min(1f, prog * 3f));
                    shapes.ellipse(m.x - r, m.y - r * 0.5f, r * 2, r, 32);
                    break;
                }
                default:
                    break;
            }
        }
        shapes.end();
    }

    /** 바닥 공격 예고: 원(낙석·낙뢰·촉수)과 선(창)은 차오르는 예고, 피웅덩이는 넓어지는 웅덩이. */
    private void drawHazards() {
        float blink = 0.16f + 0.08f * MathUtils.sin(time * 20f);
        for (Hazard h : g.hazards) {
            if (!inView(h.x, h.y) && h.kind != Hazard.Kind.LINE) continue;
            Color c = hazardColor(h.style);
            float p = h.progress();
            switch (h.kind) {
                case POOL: {
                    float r = h.radius;
                    float fade = Math.min(1f, h.life / 0.8f) * Math.min(1f, h.age / 0.35f);
                    shapes.setColor(0.25f, 0.02f, 0.05f, 0.75f * fade);
                    shapes.ellipse(h.x - r, h.y - r * 0.6f, r * 2, r * 1.2f, 40);
                    shapes.setColor(0.6f, 0.05f, 0.1f, (0.45f + 0.1f * MathUtils.sin(time * 4f + h.x)) * fade);
                    shapes.ellipse(h.x - r * 0.8f, h.y - r * 0.48f, r * 1.6f, r * 0.96f, 40);
                    break;
                }
                case EYE: {
                    if (h == nextEye()) {
                        // 다음에 뜰 눈알 쪽으로 '시선 줄': 지금 그쪽을 보고 있으면 빨갛게, 아니면 흐리게
                        boolean looking = g.lookingAt(h);
                        float hx = g.hero.x, hy = g.hero.centerY();
                        float ex = hx + (h.x - hx) * 0.82f, ey = hy + (h.y - hy) * 0.82f;
                        if (looking) shapes.setColor(EYE_RED.r, EYE_RED.g, EYE_RED.b, 0.35f + 0.25f * blink * 4f);
                        else shapes.setColor(0.8f, 0.8f, 0.85f, 0.14f);
                        for (int i = 0; i < 10; i += 2) {
                            float t0 = i / 10f, t1 = (i + 1) / 10f;
                            shapes.rectLine(hx + (ex - hx) * t0, hy + (ey - hy) * t0, hx + (ex - hx) * t1, hy + (ey - hy) * t1, looking ? 4f : 2.5f);
                        }
                    }
                    if (eyeRegion(0) != null) break;
                    // 감긴 눈이 천천히 떠진다
                    float r = h.radius, open = Math.max(0.08f, p * p);
                    shapes.setColor(0.12f, 0.02f, 0.08f, 0.85f);
                    shapes.ellipse(h.x - r * 1.15f, h.y - r * 0.62f, r * 2.3f, r * 1.24f, 32);
                    shapes.setColor(0.95f, 0.9f, 0.85f, 0.9f);
                    shapes.ellipse(h.x - r, h.y - r * 0.5f * open, r * 2f, r * open, 32);
                    shapes.setColor(EYE_RED.r, EYE_RED.g, EYE_RED.b, 0.95f);
                    float pr = r * 0.32f * open;
                    shapes.circle(h.x, h.y, pr, 20);
                    break;
                }
                case LINE: {
                    float w = h.radius * 2f;
                    shapes.setColor(c.r * 0.9f, c.g * 0.3f, c.b * 0.3f, blink + 0.05f);
                    shapes.rectLine(h.x, h.y, h.x2, h.y2, w);
                    shapes.setColor(c.r, c.g * 0.5f, c.b * 0.5f, 0.3f);
                    shapes.rectLine(h.x, h.y, h.x + (h.x2 - h.x) * p, h.y + (h.y2 - h.y) * p, w);
                    break;
                }
                default: {
                    float r = h.radius;
                    if (h.innerRadius > 0) {
                        // 가운데가 빈 고리 (지진 파동)
                        float mid = (r + h.innerRadius) / 2f, th = r - h.innerRadius;
                        ringBand(h.x, h.y, mid, th, c.r * 0.9f, c.g * 0.35f, c.b * 0.35f, blink);
                        ringBand(h.x, h.y, mid, th * p, c.r, c.g * 0.6f, c.b * 0.6f, 0.32f);
                        break;
                    }
                    shapes.setColor(c.r * 0.9f, c.g * 0.35f, c.b * 0.35f, blink);
                    shapes.ellipse(h.x - r, h.y - r * 0.6f, r * 2, r * 1.2f, 40);
                    shapes.setColor(c.r, c.g * 0.6f, c.b * 0.6f, 0.32f);
                    shapes.ellipse(h.x - r * p, h.y - r * 0.6f * p, r * 2 * p, r * 1.2f * p, 40);
                }
            }
        }
    }

    /** 납작한 고리 띠: 지름 mid, 두께 th. */
    private void ringBand(float x, float y, float mid, float th, float cr, float cg, float cb, float ca) {
        if (th <= 0.5f) return;
        shapes.setColor(cr, cg, cb, ca);
        int n = 40;
        for (int i = 0; i < n; i++) {
            float a0 = MathUtils.PI2 * i / n, a1 = MathUtils.PI2 * (i + 1) / n;
            shapes.rectLine(x + MathUtils.cos(a0) * mid, y + MathUtils.sin(a0) * mid * 0.6f,
                    x + MathUtils.cos(a1) * mid, y + MathUtils.sin(a1) * mid * 0.6f, th * 0.8f);
        }
    }

    private void hazardFx(GameEvent e) {
        String style = e.text == null ? "rock" : e.text;
        Color c = hazardColor(style);
        switch (style) {
            case "bolt":
                // 하늘에서 내리꽂는 번개 줄기
                for (int i = 0; i < 6; i++) {
                    float y = e.y + i * 60;
                    particles.burst(e.x + MathUtils.random(-6f, 6f), y, 3, c, 60, 0.25f, 2, true);
                }
                particles.burst(e.x, e.y, 22, Color.WHITE, 260, 0.35f, 3, true);
                ring(e.x, e.y, e.size * 0.3f, e.size * 1.1f, 0.35f, c);
                flash(e.x, e.y + 60, e.size * 4f, c, 0.25f);
                break;
            case "tentacle":
                particles.debris(e.x, e.y, 14, new Color(0.35f, 0.12f, 0.4f, 1f), 240, 5);
                particles.burst(e.x, e.y + 10, 18, c, 220, 0.5f, 3, true);
                ring(e.x, e.y, e.size * 0.3f, e.size, 0.4f, c);
                flash(e.x, e.y, e.size * 2.5f, c, 0.3f);
                break;
            case "bone":
                // 바닥에서 솟는 뼈 가시
                particles.debris(e.x, e.y + 10, 14, BONE, 260, 4);
                particles.burst(e.x, e.y + 8, 10, new Color(0.6f, 1f, 0.5f, 1f), 160, 0.4f, 3, true);
                ring(e.x, e.y, e.size * 0.3f, e.size, 0.3f, BONE);
                break;
            case "pound":
                // 골렘 내려찍기: 흙과 돌이 크게 튄다
                particles.debris(e.x, e.y + 6, 18, c, 240, 4);
                ring(e.x, e.y, e.size * 0.2f, e.size * 1.1f, 0.35f, c);
                break;
            case "flame":
                // 마족병 불기둥
                particles.burst(e.x, e.y + 10, 18, c, 220, 0.5f, 3, true);
                ring(e.x, e.y, e.size * 0.3f, e.size, 0.35f, c);
                flash(e.x, e.y + 20, e.size * 2.5f, c, 0.3f);
                break;
            case "eye":
                // 눈알은 범위 공격이 아니라 '보고 있었나'로 맞는다: 넓은 고리 대신 눈 둘레만 번쩍인다
                eyeFlashes.add(new float[]{e.x, e.y, e.size, 0f});
                flash(e.x, e.y, e.size * 2.2f, EYE_RED, 0.3f);
                break;
            case "whip":
            case "cage":
            case "spear": {
                float dx = e.dirX - e.x, dy = e.dirY - e.y;
                for (int i = 0; i <= 10; i++) {
                    float px = e.x + dx * i / 10f, py = e.y + dy * i / 10f;
                    if (!inView(px, py)) continue;
                    particles.burst(px, py, 3, c, 140, 0.35f, 3, true);
                }
                break;
            }
            default:
                particles.debris(e.x, e.y + 30, 16, new Color(0.45f, 0.4f, 0.38f, 1f), 220, 5);
                particles.smoke(e.x, e.y, 10, new Color(0.4f, 0.36f, 0.34f, 0.55f), e.size * 0.8f, 16, 0.7f);
                ring(e.x, e.y, e.size * 0.3f, e.size * 1.1f, 0.4f, c);
                flash(e.x, e.y, e.size * 2f, c, 0.25f);
        }
    }

    public static Color hazardColor(String style) {
        switch (style) {
            case "bolt": case "cage": return PROJ_SKY;
            case "tentacle": return PROJ_DARK;
            case "spear": case "blood": case "spin": case "leap": return PROJ_BLOOD;
            case "bone": return BONE;
            case "brand": return PROJ_DARK;
            case "whip": return WHIP;
            case "eye": return EYE_RED;
            case "quake": case "boulder": case "pound": return new Color(0.85f, 0.62f, 0.4f, 1f);
            case "flame": return PROJ_FIRE;
            default: return new Color(1f, 0.6f, 0.35f, 1f);
        }
    }

    private TextureRegion eyeRegion(int frame) {
        TextureRegion t = art.cell("sprites/rewards/demon_eye.png", 4, 1, frame);
        return t != null ? t : art.frame("sprites/demon_eye.png", 4, frame);
    }

    private void drawEye(float x, float y, float radius, int frame, float alpha) {
        TextureRegion t = eyeRegion(frame);
        if (t == null) return;
        float w = radius * 2.6f, h = w * 32f / 48f;
        batch.setColor(1f, 1f, 1f, MathUtils.clamp(alpha, 0f, 1f));
        batch.draw(t, x - w / 2f, y - h / 2f, w, h);
        batch.setColor(Color.WHITE);
    }

    /** Species silhouette at rest: bat ~half hero height, imp ~three quarters, skeleton ~hero. */
    public float mobScale(String sprite) {
        if ("bat".equals(sprite)) return .70f;
        if ("imp".equals(sprite)) return 1.05f;
        if ("skeleton".equals(sprite)) return 1.30f;
        return DEFAULT_MOB_SCALE;
    }

    public float bossScale(String sprite) {
        return "golem".equals(sprite) ? 1.25f : 1.15f;
    }

    public float monsterScale(Monster m) {
        float presence = bossScale(m.def.sprite);
        if (m.state == Monster.State.PHASE_SHIFT) return S * (2f + .3f * m.progress()) * presence;
        return S * (m.finalBoss ? (m.phase2 ? 2.3f : 2f) * presence : m.boss ? 1.7f * presence : mobScale(m.def.sprite));
    }

    private float monsterBob(Monster m) {
        float bob = m.state == Monster.State.CHASE ? Math.abs(MathUtils.sin(m.walkTime * 9f)) * 3f : 0;
        if ("bat".equals(m.def.sprite)) bob = 6 + MathUtils.sin(time * 8f + m.x) * 4f;
        if (m.state == Monster.State.SLAM_TELEGRAPH || m.state == Monster.State.CAST_TELEGRAPH)
            bob += (m.state == Monster.State.SLAM_TELEGRAPH ? 10 : 4) * m.progress();
        return bob * (m.boss ? 1f : mobScale(m.def.sprite));
    }

    private float monsterStretchY(Monster m) {
        if (m.state == Monster.State.SLAM_TELEGRAPH || m.state == Monster.State.CAST_TELEGRAPH) return 1f + .12f * m.progress();
        if (m.warning()) return 1f - .1f * m.progress();
        if (m.state == Monster.State.CHARGING || m.state == Monster.State.SWOOPING) return .9f;
        if (m.state == Monster.State.STUNNED) return .92f;
        if (m.state == Monster.State.PHASE_SHIFT) return 1f + .05f * MathUtils.sin(time * 9f);
        if (m.state == Monster.State.CHANNEL) return 1f + .04f * MathUtils.sin(time * 12f);
        return 1f;
    }

    /** Overhead UI follows the same pose as the sprite, not the lower-body combat circle. */
    public float monsterTop(Monster m) {
        if (m.boss && bossSheet(m.def.sprite, m.phase2) != null) return m.y + BOSS_BODY * sheetScale(m);
        return m.y - 2 + monsterBob(m) + art.height(art.world(m.def.sprite)) * monsterScale(m) * monsterStretchY(m);
    }

    private void drawMonster(Monster m) {
        String sheet = m.boss ? bossSheet(m.def.sprite, m.phase2) : null;
        if (sheet != null) {
            drawBossSheet(m, sheet);
            return;
        }
        TextureRegion r = art.world(m.def.sprite);
        float scale = monsterScale(m);
        float appear = m.state == Monster.State.SPAWN ? Math.max(0f, (m.progress() - 0.35f) / 0.65f) : 1f;
        if (appear <= 0f) return;
        float w = art.width(r) * scale, hgt = art.height(r) * scale;
        drawActorShadow(m.x, m.y, w * 0.8f * appear, appear, "bat".equals(m.def.sprite));
        float bob = monsterBob(m);
        // 공격 예고: 뒤로 살짝 움츠렸다가 친다
        float sx = 1f, sy = monsterStretchY(m);
        float jx = 0;
        if (m.state == Monster.State.SLAM_TELEGRAPH || m.state == Monster.State.CAST_TELEGRAPH) {
            float p = m.progress();
            sx = 1f - 0.08f * p;
        } else if (m.warning()) {
            float p = m.progress();
            sx = 1f + 0.12f * p;
            if (m.state == Monster.State.CHARGE_TELEGRAPH) jx = MathUtils.sin(time * 60f) * 2f * p;
        } else if (m.state == Monster.State.CHARGING || m.state == Monster.State.SWOOPING) {
            sx = 1.15f;
        } else if (m.state == Monster.State.STUNNED) {
            jx = MathUtils.sin(time * 14f) * 1.5f;
        } else if (m.state == Monster.State.PHASE_SHIFT) {
            jx = MathUtils.sin(time * 50f) * 3f * (1f - m.progress());
        } else if (m.state == Monster.State.CHANNEL) {
            if ("firespin".equals(m.channelKind)) jx = MathUtils.sin(time * 30f) * 1.5f;
        } else if (m.state == Monster.State.DOWNED) {
            // 무너진 뼈 무더기: 납작하게, 일어날 때가 되면 들썩인다
            sy *= 0.35f + 0.15f * m.progress() * m.progress();
            jx = MathUtils.sin(time * 30f) * 1.2f * m.progress();
        }
        float dw = w * sx * appear, dh = hgt * sy * appear;
        boolean flip = m.dirX < -0.2f;
        float mx = m.x + jx;
        if (m.state == Monster.State.PHASE_SHIFT) batch.setColor(0.7f + 0.3f * MathUtils.sin(time * 20f), 0.4f, 1f, appear);
        else if (m.state == Monster.State.DOWNED) batch.setColor(0.6f, 0.6f, 0.65f, appear);
        else if (m.phase2) batch.setColor(0.8f, 0.6f + 0.08f * MathUtils.sin(time * 6f), 1f, appear);
        else if (m.warning()) batch.setColor(1f, 0.8f, 0.7f, appear);
        else if (m.enraged) batch.setColor(1f, 0.75f + 0.1f * MathUtils.sin(time * 8f), 0.7f, appear);
        else batch.setColor(1f, 1f, 1f, appear);
        float lift = m.state == Monster.State.LEAP ? MathUtils.sin(m.progress() * MathUtils.PI) * 70f : 0f; // 뒤로 도약
        actorInk.draw(batch, r, mx + (flip ? dw/2 : -dw/2), m.y-2+bob+lift, flip ? -dw : dw, dh);
        if (m.flash > 0) {
            // 맞은 순간 하얗게 번쩍인다
            Particles.additive(batch);
            batch.setColor(1f, 1f, 1f, Math.min(1f, m.flash / 0.15f));
            for (int i = 0; i < 2; i++) {
                if (flip) batch.draw(r, mx + dw / 2f, m.y - 2 + bob, -dw, dh);
                else batch.draw(r, mx - dw / 2f, m.y - 2 + bob, dw, dh);
            }
            Particles.normal(batch);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawCorpse(Corpse c) {
        float p = c.t / c.dur;
        if (c.sheet != null) {
            // 쓰러지는 줄 8칸을 돌리고, 마지막 칸에서 천천히 사라진다
            TextureRegion r = bossCell(c.sheet, 4, (int) Math.min(7, p / 0.6f * 8));
            float size = BOSS_CELL * c.sheetScale;
            float x0 = c.x - size / 2f, y0 = c.y - (BOSS_CELL - BOSS_FOOT) * c.sheetScale;
            batch.setColor(1f, 1f, 1f, p < 0.7f ? 1f : 1f - (p - 0.7f) / 0.3f);
            if (c.sheetFaceRight) batch.draw(r, x0 + size, y0, -size, size);
            else batch.draw(r, x0, y0, size, size);
            batch.setColor(Color.WHITE);
            return;
        }
        float a = 1f - p;
        float sy = 1f - 0.5f * p, sx = 1f + 0.25f * p;
        float dw = c.w * sx, dh = c.h * sy;
        batch.setColor(1f, 0.85f, 0.85f, a);
        if (c.flip) batch.draw(c.region, c.x + dw / 2f, c.y - 2, -dw, dh);
        else batch.draw(c.region, c.x - dw / 2f, c.y - 2, dw, dh);
        Particles.additive(batch);
        batch.setColor(1f, 1f, 1f, Math.max(0f, 1f - p * (c.boss ? 1.2f : 2.5f)));
        if (c.flip) batch.draw(c.region, c.x + dw / 2f, c.y - 2, -dw, dh);
        else batch.draw(c.region, c.x - dw / 2f, c.y - 2, dw, dh);
        Particles.normal(batch);
        batch.setColor(Color.WHITE);
    }

    public void drawHpBars() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Monster m : g.monsters) {
            if (m.dead || m.boss || m.hp >= m.maxHp) continue;
            float w = Math.max(24, 30 * mobScale(m.def.sprite)), y = monsterTop(m) + 5;
            shapes.setColor(0, 0, 0, 0.7f);
            shapes.rect(m.x - w / 2 - 1, y - 1, w + 2, 6);
            shapes.setColor(0.9f, 0.25f, 0.2f, 1f);
            shapes.rect(m.x - w / 2, y, w * m.hp / (float) m.maxHp, 4);
        }
        shapes.end();
    }

    public static Color deathColor(String sprite) {
        switch (sprite) {
            case "bat": return new Color(0.75f, 0.5f, 1f, 1f);
            case "skeleton": case "knight": return new Color(1f, 0.4f, 0.4f, 1f);
            case "imp": case "warden": return new Color(0.6f, 0.5f, 1f, 1f);
            case "golem": return new Color(1f, 0.7f, 0.4f, 1f);
            case "demonlord": return new Color(0.9f, 0.4f, 1f, 1f);
            default: return new Color(1f, 0.8f, 0.6f, 1f);
        }
    }

    /** 투사체 색: 쏜 몬스터마다 다르다. */
    public static Color projectileColor(String source) {
        if (source == null) return PROJ_FIRE;
        switch (source) {
            case "knight": return PROJ_BLOOD;
            case "warden": return PROJ_SKY;
            case "demonlord": return PROJ_DARK;
            case "fire": return new Color(1f, 0.42f, 0.12f, 1f);
            case "golem": return new Color(0.8f, 0.62f, 0.42f, 1f);
            case "crescent": return PROJ_BLOOD;
            case "bat": return new Color(0.75f, 0.6f, 1f, 1f);
            default: return PROJ_FIRE;
        }
    }

    public static Color spawnColor(Monster m) {
        if (m.finalBoss) return PROJ_DARK;
        String th = m.def.sprite;
        if ("bat".equals(th) || "golem".equals(th)) return SPAWN_BLUE;
        if ("skeleton".equals(th) || "knight".equals(th)) return PROJ_BLOOD;
        return PROJ_DARK;
    }

    public static final Color PROJ_FIRE = new Color(1f, 0.5f, 0.2f, 1f);

    public static final Color PROJ_BLOOD = new Color(1f, 0.2f, 0.25f, 1f);

    public static final Color BONE = new Color(0.9f, 0.95f, 0.75f, 1f);

    public static final Color WHIP = new Color(0.85f, 0.35f, 0.7f, 1f);

    public static final Color EYE_RED = new Color(1f, 0.15f, 0.2f, 1f);

    public static final Color PROJ_SKY = new Color(0.4f, 0.8f, 1f, 1f);

    public static final Color PROJ_DARK = new Color(0.7f, 0.3f, 1f, 1f);

    public static final Color SPAWN_BLUE = new Color(0.4f, 0.6f, 1f, 1f);

    public static final Color SHIELD_COLOR = new Color(1f, 0.82f, 0.55f, 1f);

    /** 가장 먼저 뜰 눈알 (시선 줄은 하나만 그린다). */
    private Hazard nextEye() {
        Hazard best = null;
        for (Hazard h : g.hazards)
            if (h.kind == Hazard.Kind.EYE && !h.dead && (best == null || h.delay < best.delay)) best = h;
        return best;
    }

    /**
     * 혈월 검기 그림 (sprites/fx/crescent.png, 오른쪽을 보는 초승달 한 장). 그림이 아직 없으면 false 를 돌려
     * 보통 탄처럼 그린다. 그림을 넣기만 하면 날아가는 쪽으로 돌려서 그려진다.
     */
    private boolean drawCrescent(Projectile p) {
        String path = "sprites/fx/crescent.png";
        if (!SHEET_EXISTS.computeIfAbsent(path, k -> Gdx.files.internal(k).exists())) return false;
        TextureRegion t = art.cell(path, 1, 1, 0);
        if (t == null) return false;
        float w = p.radius * 3.2f, h = w * t.getRegionHeight() / Math.max(1f, t.getRegionWidth());
        float deg = MathUtils.atan2(p.vy, p.vx) * MathUtils.radiansToDegrees;
        batch.setColor(Color.WHITE);
        batch.draw(t, p.x - w / 2f, p.y - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, deg);
        return true;
    }
}
