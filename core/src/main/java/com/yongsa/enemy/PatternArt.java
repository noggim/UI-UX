package com.yongsa.enemy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.DepthSink;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 패턴 그림 (김선우). 보스 패턴마다 한 줄씩 있는 동작 시트, 일반 몹 동작 시트, 효과 시트를 그린다.
 *
 * <p>몬스터 몸과 효과(검기·탄·구체·돌·창·충격파·번개·촉수)는 따로 그린다. 몸은 패턴의 예고 동안 시트 줄의
 * 앞 4칸(준비), 공격이 나간 뒤 뒤 4칸(던지기·휘두르기 → 회복)을 넘긴다. 효과는 게임이 실제로 투사체를 쏘거나
 * 장판이 터지는 순간에만 나오므로, 몸이 던지는 칸에 닿기 전에 공격이 먼저 보이지 않는다.
 *
 * <p>효과는 날아가는 실제 방향(360도)으로 돌려서 그린다. 몸은 용사 쪽으로 좌우를 뒤집는다.
 *
 * <p>모든 시트: 256px 칸, 8열, 왼쪽을 보는 그림, 발은 칸의 (128, 224). 파일이 없으면 예전 그림으로 그린다.
 */
public class PatternArt {
    static final int CELL = 256, FOOT = 224, BODY = 132;

    // ---------- 보스 패턴 시트 ----------

    /** 보스 그림 이름(+2페이즈) → 패턴 시트. */
    private static final class PatSheet {
        final String path;
        final int rows;
        final Map<String, Integer> row = new HashMap<>();

        PatSheet(String path, String... names) {
            this.path = path;
            this.rows = names.length;
            for (int i = 0; i < names.length; i++) row.put(names[i], i);
        }
    }

    private static final PatSheet GOLEM = new PatSheet("team/noggim/boss_golem_patterns.png", "rush", "pound", "boulder", "stun");
    private static final PatSheet KNIGHT = new PatSheet("team/noggim/boss_knight_patterns.png", "crescent", "spearstand", "spinslash", "leap", "shield");
    private static final PatSheet LICH = new PatSheet("team/noggim/boss_lich_patterns.png", "darkorb", "ring", "brand");
    private static final PatSheet LICH2 = new PatSheet("team/noggim/boss_lich2_patterns.png", "tentacle", "eyes");
    /** 수문장은 예전 동작 시트가 없어서 0줄(앞 4칸 대기, 뒤 4칸 걷기)도 이 시트에 있다. */
    private static final PatSheet WARDEN = new PatSheet("team/noggim/boss_warden_patterns.png", "base", "cage", "homing", "firespin", "summon");

    // ---------- 일반 몹 시트 ----------

    private static final PatSheet BAT = new PatSheet("team/noggim/mob_bat.png", "fly", "swoop", "spit");
    private static final PatSheet SKELETON = new PatSheet("team/noggim/mob_skeleton.png", "base", "attack", "guarddown");
    private static final PatSheet IMP = new PatSheet("team/noggim/mob_imp.png", "base", "cast");

    // ---------- 효과 시트 (5줄 x 8칸) ----------

    static final String FX = "team/noggim/fx_patterns.png";

    /** 효과 이름 → {줄, 첫 칸, 칸 수, 바닥 기준(1)/가운데 기준(0), 그림이 향한 각도(도)}. */
    private static final Map<String, int[]> FXC = new HashMap<>();

    static {
        FXC.put("crescent", new int[]{0, 0, 4, 0, 180});
        FXC.put("spear", new int[]{0, 4, 4, 1, 0});
        FXC.put("darkorb", new int[]{1, 0, 4, 0, 0});
        FXC.put("mbolt", new int[]{1, 4, 2, 0, 180});
        FXC.put("sigil", new int[]{1, 6, 2, 1, 0});
        FXC.put("homing", new int[]{2, 0, 4, 0, 207});
        FXC.put("wfire", new int[]{2, 4, 4, 0, 207});
        FXC.put("boulder", new int[]{3, 0, 4, 0, 0});
        FXC.put("shock", new int[]{3, 4, 4, 1, 0});
        FXC.put("pellet", new int[]{4, 0, 2, 0, 0});
        FXC.put("impfire", new int[]{4, 2, 2, 0, 0});
        FXC.put("lightning", new int[]{4, 4, 2, 0, 90});
        FXC.put("tentacle", new int[]{4, 6, 2, 1, 0});
    }

    private final GameSession g;
    private final Art art;
    private final SpriteBatch batch;
    private final int T;
    private float time;
    private final Map<String, Boolean> exists = new HashMap<>();

    public PatternArt(GameSession g, Art art, SpriteBatch batch) {
        this.g = g;
        this.art = art;
        this.batch = batch;
        this.T = g.balance.tileSize;
    }

    private boolean has(String path) {
        return exists.computeIfAbsent(path, k -> Gdx.files.internal(k).exists());
    }

    // =====================================================================
    // 몸 동작
    // =====================================================================

    /** 몬스터마다 지난 상태와, 지금 돌리는 줄을 기억한다 (회복 동안 앞 동작을 이어 가려고). */
    private static final class Track {
        Monster.State last;
        String recRow;     // 회복 동안 이어서 돌릴 줄 (null = 보통 대기)
        boolean recFull;   // true: 회복 동안 8칸 전부, false: 뒤 4칸만
        float lx, ly, moveT;
    }

    private final Map<Monster, Track> tracks = new IdentityHashMap<>();

    private Track track(Monster m) {
        if (tracks.size() > 48) tracks.keySet().removeIf(x -> x.dead);
        Track t = tracks.computeIfAbsent(m, k -> {
            Track n = new Track();
            n.lx = m.x;
            n.ly = m.y;
            return n;
        });
        return t;
    }

    /** 패턴이 예고 없이 바로 회복 상태로 들어가 장판으로 공격하는 패턴 (회복 동안 8칸 전부). */
    private static boolean immediate(String p) {
        switch (p) {
            case "boulder": case "pound": case "spinslash": case "tentacle": case "summon":
            case "brand": case "eyes": case "cage": case "shield":
                return true;
            default:
                return false;
        }
    }

    private PatSheet bossSheet(Monster m) {
        switch (m.def.sprite) {
            case "golem": return GOLEM;
            case "knight": return KNIGHT;
            case "warden": return WARDEN;
            case "demonlord": return m.phase2 ? LICH2 : LICH;
            default: return null;
        }
    }

    /** 보스 패턴 칸. 패턴 중이 아니면 null (예전 시트의 대기·이동·기본 공격을 쓴다). 수문장은 늘 이 시트. */
    public TextureRegion bossPatternCell(Monster m) {
        PatSheet s = bossSheet(m);
        if (s == null || !has(s.path)) return null;
        Track t = track(m);
        boolean moved = Math.abs(m.x - t.lx) + Math.abs(m.y - t.ly) > 0.05f;
        t.lx = m.x;
        t.ly = m.y;
        if (moved) t.moveT += Gdx.graphics.getDeltaTime();
        Monster.State prev = t.last;
        t.last = m.state;
        String p = m.lastPattern;
        Integer row = p == null ? null : s.row.get(p);
        float pr = MathUtils.clamp(m.progress(), 0f, 0.999f);
        int four = (int) (pr * 4), eight = (int) (pr * 8);
        switch (m.state) {
            case CAST_TELEGRAPH:
            case SLAM_TELEGRAPH:
                if (row == null) break;
                return cell(s, row, four);
            case CHARGE_TELEGRAPH:
                if (s.row.containsKey("rush")) return cell(s, s.row.get("rush"), Math.min(1, (int) (pr * 2)));
                break;
            case CHARGING:
                if (s.row.containsKey("rush")) return cell(s, s.row.get("rush"), 2 + ((int) (time * 12)) % 4);
                break;
            case STUNNED:
                if (s.row.containsKey("stun")) return cell(s, s.row.get("stun"), 2 + ((int) (time * 6)) % 6);
                break;
            case LEAP:
                if (s.row.containsKey("leap")) return cell(s, s.row.get("leap"), eight);
                break;
            case CHANNEL:
                if (row != null) {
                    if ("spearstand".equals(m.channelKind)) return cell(s, row, 3 + ((int) (time * 6)) % 3);
                    return cell(s, row, 2 + ((int) (time * 10)) % 4);
                }
                break;
            case RECOVER: {
                if (prev != Monster.State.RECOVER) {
                    // 회복에 막 들어왔다: 무엇을 하고 들어왔나
                    t.recRow = null;
                    if (row != null) {
                        if (prev == Monster.State.CAST_TELEGRAPH || prev == Monster.State.SLAM_TELEGRAPH) {
                            t.recRow = p;
                            t.recFull = false;
                        } else if (prev == Monster.State.CHANNEL) {
                            t.recRow = p;
                            t.recFull = false;
                        } else if (immediate(p) && prev != Monster.State.TELEGRAPH && prev != Monster.State.CHARGING) {
                            t.recRow = p;
                            t.recFull = true;
                        }
                    }
                }
                if (t.recRow != null && s.row.containsKey(t.recRow)) {
                    int r = s.row.get(t.recRow);
                    return cell(s, r, t.recFull ? eight : 4 + four);
                }
                break;
            }
            default:
                break;
        }
        if (s == WARDEN) {
            // 수문장 기본 동작: 걷기·대기, 기본 근접 공격은 유도 마탄 줄의 팔 뻗기로
            if (m.state == Monster.State.TELEGRAPH || m.state == Monster.State.COMBO_TELEGRAPH) return cell(s, 2, four);
            if (m.state == Monster.State.RECOVER) return cell(s, 2, 4 + four);
            if (moved) return cell(s, 0, 4 + ((int) (t.moveT * 7)) % 4);
            return cell(s, 0, ((int) (time * 5.5f)) % 4);
        }
        return null;
    }

    private TextureRegion cell(PatSheet s, int row, int col) {
        return art.cell(s.path, 8, s.rows, row * 8 + MathUtils.clamp(col, 0, 7));
    }

    /** 수문장처럼 예전 동작 시트가 없어도 패턴 시트로 그릴 수 있나. */
    public boolean hasOwnSheet(Monster m) {
        return m.boss && "warden".equals(m.def.sprite) && has(WARDEN.path);
    }

    // ---------- 일반 몹 ----------

    private PatSheet mobSheet(String sprite) {
        switch (sprite) {
            case "bat": return BAT;
            case "skeleton": return SKELETON;
            case "imp": return IMP;
            default: return null;
        }
    }

    public boolean hasMobSheet(Monster m) {
        PatSheet s = m.boss ? null : mobSheet(m.def.sprite);
        return s != null && has(s.path);
    }

    /** 일반 몹의 지금 칸. 8칸을 시간에 맞춰 넘겨 뚝뚝 끊기지 않게 한다. */
    public TextureRegion mobCell(Monster m) {
        PatSheet s = mobSheet(m.def.sprite);
        if (s == null || !has(s.path)) return null;
        Track t = track(m);
        boolean moved = Math.abs(m.x - t.lx) + Math.abs(m.y - t.ly) > 0.05f;
        t.lx = m.x;
        t.ly = m.y;
        if (moved) t.moveT += Gdx.graphics.getDeltaTime();
        Monster.State prev = t.last;
        t.last = m.state;
        float pr = MathUtils.clamp(m.progress(), 0f, 0.999f);
        int four = (int) (pr * 4);
        if (m.state == Monster.State.RECOVER && prev != Monster.State.RECOVER) {
            t.recRow = null;
            if (prev == Monster.State.TELEGRAPH || prev == Monster.State.COMBO_TELEGRAPH) t.recRow = "attack";
            else if (prev == Monster.State.CAST_TELEGRAPH) t.recRow = "cast";
            else if (prev == Monster.State.SWOOPING || prev == Monster.State.SWOOP_TELEGRAPH) t.recRow = "swoop";
        }
        switch (m.def.sprite) {
            case "bat":
                switch (m.state) {
                    case SWOOP_TELEGRAPH: case TELEGRAPH: return cell(s, 1, four);
                    case SWOOPING: return cell(s, 1, 4 + Math.min(2, (int) (pr * 3)));
                    case CAST_TELEGRAPH: return cell(s, 2, four);
                    case RECOVER:
                        if ("cast".equals(t.recRow)) return cell(s, 2, 4 + four);
                        if ("swoop".equals(t.recRow) || "attack".equals(t.recRow)) return cell(s, 1, 6 + Math.min(1, (int) (pr * 2)));
                        break;
                    default: break;
                }
                return cell(s, 0, ((int) (time * 12 + m.x * 0.01f)) % 8);
            case "skeleton":
                switch (m.state) {
                    case TELEGRAPH: case COMBO_TELEGRAPH: return cell(s, 1, four);
                    case RECOVER:
                        if ("attack".equals(t.recRow)) return cell(s, 1, 4 + four);
                        break;
                    case DOWNED: {
                        // 무너지기(앞 0.2) → 뼈 무더기 → 일어나기(끝 0.15, 거꾸로)
                        float p = m.progress();
                        int f = p < 0.2f ? 4 + (int) (p / 0.2f * 4) : p > 0.85f ? 7 - (int) ((p - 0.85f) / 0.15f * 4) : 7;
                        return cell(s, 2, f);
                    }
                    default: break;
                }
                if (m.guardActive()) return cell(s, 2, Math.min(3, 1 + ((int) (time * 2)) % 3));
                if (moved) return cell(s, 0, 4 + ((int) (t.moveT * 8)) % 4);
                return cell(s, 0, ((int) (time * 5f)) % 4);
            case "imp":
                switch (m.state) {
                    case CAST_TELEGRAPH: case TELEGRAPH: return cell(s, 1, four);
                    case RECOVER:
                        if ("cast".equals(t.recRow) || "attack".equals(t.recRow)) return cell(s, 1, 4 + four);
                        break;
                    default: break;
                }
                if (moved) return cell(s, 0, 4 + ((int) (t.moveT * 9)) % 4);
                return cell(s, 0, ((int) (time * 5f)) % 4);
            default:
                return null;
        }
    }

    /** 일반 몹 시트를 그릴 크기(칸 한 변). 예전 한 장 그림과 비슷한 키가 되게 맞춘다. */
    public float mobCellSize(Monster m, float oldHeight) {
        return oldHeight * 0.9f / BODY * CELL;
    }

    // =====================================================================
    // 효과
    // =====================================================================

    private TextureRegion fx(String name, int frame) {
        int[] c = FXC.get(name);
        if (c == null || !has(FX)) return null;
        return art.cell(FX, 8, 5, c[0] * 8 + c[1] + Math.floorMod(frame, c[2]));
    }

    /** 효과 한 칸을 (x, y)에 size 크기로, 날아가는 각도 heading(도)에 맞춰 돌려 그린다. */
    public boolean drawFx(String name, int frame, float x, float y, float size, float headingDeg, float alpha) {
        TextureRegion r = fx(name, frame);
        if (r == null) return false;
        int[] c = FXC.get(name);
        float rot = Float.isNaN(headingDeg) ? 0f : headingDeg - c[4];
        // 그림이 왼쪽을 향하면서 뒤집혀 보이지 않게: 오른쪽으로 날 때는 위아래를 뒤집어 돌린다
        boolean flipY = !Float.isNaN(headingDeg) && c[4] == 180 && Math.cos(Math.toRadians(headingDeg)) > 0;
        float oy = c[3] == 1 ? size * FOOT / CELL : size / 2f;
        batch.setColor(1f, 1f, 1f, MathUtils.clamp(alpha, 0f, 1f));
        batch.draw(r, x - size / 2f, y - oy, size / 2f, oy, size, size, 1f, flipY ? -1f : 1f, rot);
        batch.setColor(Color.WHITE);
        return true;
    }

    private int anim(float speed) {
        return (int) (time * speed);
    }

    /** 투사체 그림. 맡은 투사체면 true (아니면 예전 빛 구슬로 그린다). */
    public boolean drawProjectile(Projectile p) {
        float deg = MathUtils.atan2(p.vy, p.vx) * MathUtils.radiansToDegrees;
        String src = p.source == null ? "" : p.source;
        if ("crescent".equals(src)) return drawFx("crescent", anim(12), p.x, p.y, p.radius * 6f, deg, 1f);
        if (p.burst > 0) return drawFx("darkorb", anim(10), p.x, p.y, p.radius * 6.5f, Float.NaN, 1f);
        if (p.homing > 0) return drawFx("homing", anim(10), p.x, p.y, p.radius * 9f, deg, 1f);
        switch (src) {
            case "bat": return drawFx("pellet", anim(10), p.x, p.y, p.radius * 10f, deg, 1f);
            case "imp": return drawFx("impfire", anim(10), p.x, p.y, p.radius * 9f, deg, 1f);
            case "fire": case "warden": return drawFx("wfire", anim(10), p.x, p.y, p.radius * 8f, deg, 1f);
            case "demonlord": return drawFx("mbolt", anim(10), p.x, p.y, p.radius * 9f, deg, 1f);
            case "golem": return drawFx("boulder", anim(8), p.x, p.y, p.radius * 6f, Float.NaN, 1f);
            default: return false;
        }
    }

    // ---------- 장판이 터질 때 잠깐 나오는 효과 ----------

    private static final class Burst {
        String name;
        float x, y, size, heading = Float.NaN, t, dur;
        int f0, frames;
        float spin; // 0이 아니면 둘레를 도는 검기 (회전 베기)
        float radius;
    }

    private final List<Burst> bursts = new ArrayList<>();

    private Burst burst(String name, float x, float y, float size, float dur, int f0, int frames) {
        Burst b = new Burst();
        b.name = name;
        b.x = x;
        b.y = y;
        b.size = size;
        b.dur = dur;
        b.f0 = f0;
        b.frames = frames;
        bursts.add(b);
        return b;
    }

    /** 장판이 터지는 순간 (HAZARD_FX). 그림이 없으면 아무것도 하지 않는다 (예전 입자 효과는 그대로 나온다). */
    public void onHazardFx(GameEvent e) {
        if (!has(FX)) return;
        String style = e.text == null ? "rock" : e.text;
        switch (style) {
            case "spear": case "cage": {
                // 선을 따라 창(바닥에서 솟음)이나 번개 조각을 늘어놓는다
                float dx = e.dirX - e.x, dy = e.dirY - e.y, len = (float) Math.sqrt(dx * dx + dy * dy);
                int n = Math.max(1, Math.round(len / (T * ("spear".equals(style) ? 0.9f : 1.1f))));
                float head = MathUtils.atan2(dy, dx) * MathUtils.radiansToDegrees;
                for (int i = 0; i < n; i++) {
                    float k = (i + 0.5f) / n;
                    if ("spear".equals(style)) burst("spear", e.x + dx * k, e.y + dy * k, T * 2.2f, 0.45f, 1, 3).t = -0.03f * i;
                    else {
                        Burst b = burst("lightning", e.x + dx * k, e.y + dy * k, len / n * 1.25f, 0.3f, 0, 2);
                        b.heading = head;
                    }
                }
                break;
            }
            case "pound": case "quake": case "boulder": case "leap":
                burst("shock", e.x, e.y, Math.max(T * 2f, e.size * 2.6f), 0.45f, 0, 4);
                break;
            case "tentacle":
                burst("tentacle", e.x, e.y, Math.max(T * 2.4f, e.size * 2.4f), 0.55f, 0, 2);
                break;
            case "spin": {
                for (int i = 0; i < 4; i++) {
                    Burst b = burst("crescent", e.x, e.y, T * 2.4f, 0.35f, 0, 4);
                    b.spin = MathUtils.PI2 * i / 4f;
                    b.radius = Math.max(T, e.size * 0.75f);
                }
                break;
            }
            case "brand":
                burst("sigil", e.x, e.y, Math.max(T * 3f, e.size * 2.4f), 0.4f, 1, 1);
                burst("darkorb", e.x, e.y + T * 0.6f, Math.max(T * 2.4f, e.size * 2f), 0.4f, 0, 4);
                break;
            case "flame":
                burst("wfire", e.x, e.y + T * 0.5f, T * 2.4f, 0.45f, 0, 4).heading = 90f + 207f - 180f;
                break;
            case "bolt":
                burst("lightning", e.x, e.y + T * 1.5f, T * 3.2f, 0.3f, 0, 2).heading = 90f;
                break;
            default:
                break;
        }
    }

    public void update(float dt, float time) {
        this.time = time;
        for (Iterator<Burst> it = bursts.iterator(); it.hasNext(); ) {
            Burst b = it.next();
            b.t += dt;
            if (b.t >= b.dur) it.remove();
        }
    }

    public void clear() {
        bursts.clear();
    }

    /** 깊이 정렬: 터지는 효과, 떨어지는 바위, 낙인 마법진. */
    public void collect(DepthSink out) {
        if (!has(FX)) return;
        for (Burst b : bursts) {
            if (b.t < 0) continue;
            out.add(b.y, () -> drawBurst(b));
        }
        for (Hazard h : g.hazards) {
            if (h.dead) continue;
            if ("boulder".equals(h.style) && h.delay > 0) {
                // 바위가 하늘에서 떨어진다: 예고가 끝날 때 바닥에 닿는다
                float p = h.progress();
                if (p > 0.35f) {
                    float k = (p - 0.35f) / 0.65f;
                    float fall = (1f - k) * (1f - k) * T * 9f;
                    out.add(h.y - 1, () -> drawFx("boulder", anim(8), h.x, h.y + fall + h.radius * 0.3f, h.radius * 2.6f, Float.NaN, Math.min(1f, k * 3f)));
                }
            } else if ("brand".equals(h.style) && h.delay > 0) {
                boolean locked = h.age > h.followT;
                out.add(h.y + 40, () -> drawFx("sigil", locked ? 1 : 0, h.x, h.y - h.radius * 0.35f, h.radius * 2.6f, Float.NaN, 0.85f));
            }
        }
    }

    private void drawBurst(Burst b) {
        float p = MathUtils.clamp(b.t / b.dur, 0f, 0.999f);
        int f = b.f0 + (int) (p * b.frames);
        float a = p < 0.7f ? 1f : 1f - (p - 0.7f) / 0.3f;
        if (b.spin != 0 || b.radius > 0) {
            float ang = b.spin + p * MathUtils.PI * 1.5f;
            float x = b.x + MathUtils.cos(ang) * b.radius, y = b.y + MathUtils.sin(ang) * b.radius * 0.6f + T * 0.6f;
            drawFx(b.name, f, x, y, b.size, ang * MathUtils.radiansToDegrees + 90f, a);
            return;
        }
        drawFx(b.name, f, b.x, b.y, b.size, b.heading, a);
    }
}
