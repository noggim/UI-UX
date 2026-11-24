package com.yongsa.enemy;

import com.yongsa.combat.Physics;
import com.yongsa.config.Balance;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import com.yongsa.map.WorldMap;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 몬스터 AI (보스·몬스터 담당): 매 프레임 상태 기계(추격·예고·공격·돌진·기절), 길찾기,
 * 근접 공격, 마법탄 쏘기와 날아가기, 몬스터끼리 밀어내기. 보스만의 패턴은 BossBrain.
 */
public class MonsterBrain extends EnemyContext {
    BossBrain boss;

    public MonsterBrain(GameSession g, List<GameEvent> events) {
        super(g, events);
    }

    /** 싸우는 방의 길찾기 (용사 칸까지의 거리). */
    int[] flow;

    int flowHeroX = Integer.MIN_VALUE, flowHeroY;

    Room flowRoom;

    float pathX, pathY;

    public void updateMonsters(float dt) {
        Balance.MonsterCfg mc = balance.monster;
        List<Monster> summoned = new ArrayList<>();
        for (Monster m : monsters) {
            if (m.dead) continue;
            m.flash -= dt;
            m.immuneMsgCd -= dt;
            m.blinkCd -= dt;
            if (m.def.guard && !m.boss) {
                m.guardT -= dt;
                if (m.guardT <= 0) {
                    m.guarding = !m.guarding;
                    m.guardT = m.guarding ? mc.guardUp : mc.guardDown;
                }
            }
            if (m.kbT > 0) {
                Physics.move(map, m, m.kbVX * dt, m.kbVY * dt, T);
                m.kbT -= dt;
            }
            if (m.state == Monster.State.SPAWN) {
                m.stateT -= dt;
                if (m.stateT <= 0) m.state = Monster.State.CHASE;
                continue;
            }
            float speedUp = m.enraged ? mc.phase2Speed : 1f;
            m.attackCd -= dt * speedUp;
            m.patternCd -= dt * speedUp;
            float dx = hero.x - m.x, dy = hero.y - m.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float reach = mc.attackReachTiles * T + m.hw + hero.hw;
            if (m.boss && (m.def.cornerBlink || m.def.cornerLeap) && boss.cornered(m, dist, reach, dt)) continue;
            switch (m.state) {
                case CHASE:
                    if (dist > 0.01f && m.guardActive()) {
                        // 방패를 든 해골병은 천천히 돌아선다: 옆이나 뒤로 돌아 들어갈 틈
                        turnToward(m, dx / dist, dy / dist, mc.guardTurn * dt);
                    } else if (dist > 0.01f) {
                        m.dirX = dx / dist;
                        m.dirY = dy / dist;
                    }
                    steer(m);
                    if (m.boss) boss.chaseBoss(m, dist, reach, dt, summoned);
                    else chaseMob(m, dist, reach, dt);
                    break;
                case TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        // 예고한 방향으로 친다. 그 사이 걸어서 벗어났으면 빗나간다.
                        if ("brute".equals(m.ai())) Physics.move(map, m, m.dirX * mc.lungeTiles * T, m.dirY * mc.lungeTiles * T, T);
                        meleeStrike(m, reach, 1f);
                        m.enter(Monster.State.RECOVER, m.boss ? 0.45f : "brute".equals(m.ai()) ? 0.5f : 0.3f);
                        m.attackCd = m.cooldown(mc.attackCooldown);
                    }
                    break;
                case COMBO_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        Physics.move(map, m, m.dirX * mc.comboStepTiles * T, m.dirY * mc.comboStepTiles * T, T);
                        meleeStrike(m, reach + 0.6f * T, 1.1f);
                        if (--m.comboLeft > 0) {
                            boss.comboStep(m);
                        } else {
                            m.enter(Monster.State.RECOVER, 0.9f);
                        }
                    }
                    break;
                case SWOOP_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        m.enter(Monster.State.SWOOPING, mc.swoopTime);
                        m.dashLeft = mc.swoopDistanceTiles * T;
                        m.dashHit = false;
                        events.add(GameEvent.sound("dash"));
                    }
                    break;
                case SWOOPING:
                case CHARGING: {
                    boolean charge = m.state == Monster.State.CHARGING;
                    float spd = charge ? mc.chargeSpeedTiles * T : mc.swoopDistanceTiles * T / mc.swoopTime;
                    float step = Math.min(m.dashLeft, spd * dt);
                    boolean blocked = Physics.move(map, m, m.lockX * step, m.lockY * step, T);
                    m.dashLeft -= step;
                    m.stateT -= dt;
                    float hx = hero.x - m.x, hy = hero.y - m.y;
                    float touch = m.hurtRadius + hero.hw + 4;
                    if (!m.dashHit && hx * hx + hy * hy < touch * touch) {
                        m.dashHit = true;
                        hitHero(m.attack(), charge ? mc.chargeMultiplier : 1f, m.x, m.y);
                        if (g.heroJustDied()) return;
                    }
                    boolean over = m.dashLeft <= 0.5f || m.stateT <= 0 || blocked;
                    if (charge && over && m.rushLeft > 1) {
                        // 연속 돌진 첫째·둘째: 벽 앞에서 멈추고(부딪히지 않는다) 다시 조준한다. 기절은 없다
                        m.rushLeft--;
                        aimAtHero(m);
                        m.enter(Monster.State.CHARGE_TELEGRAPH, mc.rushTelegraph);
                    } else if (charge && over && (blocked || m.rushLeft == 1)) {
                        // 벽에 부딪히거나 연속 돌진을 다 쓰면 휘청인다: 반격할 기회
                        boolean lastRush = m.rushLeft == 1;
                        m.rushLeft = 0;
                        m.enter(Monster.State.STUNNED, lastRush ? Math.max(mc.rushStun, blocked ? mc.chargeStun : 0) : mc.chargeStun);
                        if (blocked) wallBump(m);
                        else events.add(GameEvent.sound("block"));
                    } else if (over) {
                        m.rushLeft = 0;
                        m.enter(Monster.State.RECOVER, charge ? 0.6f : mc.swoopRecover);
                        m.attackCd = m.cooldown(mc.attackCooldown);
                    }
                    break;
                }
                case CAST_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (dist > 0.01f && !"ring".equals(m.castKind)) {
                        m.dirX = dx / dist;
                        m.dirY = dy / dist;
                    }
                    if (m.stateT <= 0) {
                        fire(m);
                        m.enter(Monster.State.RECOVER, m.boss ? 0.6f : 0.4f);
                        m.attackCd = m.boss ? mc.attackCooldown : m.cooldown(mc.castCooldown);
                    }
                    break;
                case SLAM_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        float sx = hero.x - m.slamX, sy = hero.y - m.slamY;
                        if (sx * sx + sy * sy <= m.slamRadius * m.slamRadius) {
                            hitHero(m.attack(), mc.bossSlamMultiplier, m.slamX, m.slamY);
                        }
                        m.enter(Monster.State.RECOVER, 0.7f);
                        events.add(GameEvent.sound("slam"));
                        GameEvent fx = GameEvent.fx(GameEvent.Type.SLAM_FX, m.slamX, m.slamY);
                        fx.size = m.slamRadius;
                        events.add(fx);
                    }
                    break;
                case CHARGE_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        m.enter(Monster.State.CHARGING, 3f);
                        m.dashLeft = mc.chargeMaxTiles * T;
                        if (m.rushLeft > 1) {
                            // 연속 돌진 첫째·둘째: 벽 앞에서 멈춘다
                            m.dashLeft = Math.min(m.dashLeft, Math.max(T * 0.5f, clearRun(m) - mc.rushWallMarginTiles * T));
                        } else if (m.rushLeft == 1) {
                            // 셋째: 벽에 닿을 때까지 달린다 (반드시 부딪혀서 기절)
                            m.dashLeft = 99 * T;
                            m.stateT = m.stateTotal = 6f;
                        }
                        m.dashHit = false;
                        events.add(GameEvent.sound("dash"));
                    }
                    break;
                case CHANNEL:
                    boss.channel(m, dt * speedUp);
                    break;
                case LEAP: {
                    m.stateT -= dt;
                    float p = m.progress();
                    m.x = m.leapX0 + (m.leapX1 - m.leapX0) * p;
                    m.y = m.leapY0 + (m.leapY1 - m.leapY0) * p;
                    if (m.stateT <= 0) {
                        m.x = m.leapX1;
                        m.y = m.leapY1;
                        aimAtHero(m);
                        m.enter(Monster.State.RECOVER, 0.5f);
                        events.add(GameEvent.sound("slam"));
                    }
                    break;
                }
                case PHASE_SHIFT:
                    m.stateT -= dt;
                    // 변신하는 동안 체력이 다시 차오른다
                    m.hp = Math.max(1, Math.round(m.maxHp * m.progress()));
                    if (m.stateT <= 0) {
                        m.hp = m.maxHp;
                        m.enraged = true;
                        m.patternCd = 0.8f;
                        m.state = Monster.State.CHASE;
                    }
                    break;
                case DOWNED:
                    m.stateT -= dt;
                    if (m.stateT <= 0) {
                        m.hp = Math.max(1, Math.round(m.maxHp * mc.reviveHpRatio));
                        m.enter(Monster.State.RECOVER, 0.6f);
                        events.add(GameEvent.floatText(m.x, m.y + m.hurtRadius * 2.4f, "다시 일어났다!", GameEvent.Tone.INFO));
                        events.add(GameEvent.sound("monsterAttack"));
                    }
                    break;
                case STUNNED:
                case RECOVER:
                    m.stateT -= dt;
                    if (m.stateT <= 0) m.state = Monster.State.CHASE;
                    break;
                default:
                    break;
            }
            if (g.heroJustDied()) return; // g.die() 가 몬스터 목록을 비웠다
        }
        monsters.addAll(summoned);
        separate();
    }

    /** 바라보는 방향을 (tx, ty) 쪽으로 최대 maxTurn 라디안만 돌린다. */
    static void turnToward(Monster m, float tx, float ty, float maxTurn) {
        float cur = (float) Math.atan2(m.dirY, m.dirX), want = (float) Math.atan2(ty, tx);
        float diff = want - cur;
        while (diff > Math.PI) diff -= (float) (2 * Math.PI);
        while (diff < -Math.PI) diff += (float) (2 * Math.PI);
        cur += Math.max(-maxTurn, Math.min(maxTurn, diff));
        m.dirX = (float) Math.cos(cur);
        m.dirY = (float) Math.sin(cur);
    }

    /** 돌진 방향(lockX, lockY)으로 벽에 닿기 전까지 갈 수 있는 거리. */
    float clearRun(Monster m) {
        float step = T * 0.25f, d = 0;
        while (d < balance.monster.chargeMaxTiles * T) {
            float nx = m.x + m.lockX * (d + step), ny = m.y + m.lockY * (d + step);
            if (!Physics.free(map, nx, ny, m, T)) break;
            d += step;
        }
        return d;
    }

    void wallBump(Monster m) {
        events.add(GameEvent.sound("slam"));
        GameEvent fx = GameEvent.fx(GameEvent.Type.SLAM_FX, m.x + m.lockX * m.hw, m.y + m.lockY * m.hh);
        fx.size = T * 1.4f;
        events.add(fx);
    }

    void aimAtHero(Monster m) {
        float dx = hero.x - m.x, dy = hero.y - m.y, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l > 0.01f) {
            m.dirX = dx / l;
            m.dirY = dy / l;
        }
        m.lockX = m.dirX;
        m.lockY = m.dirY;
    }

    /** 용사에게 곧장 갈 수 없으면 바닥 칸을 따라 돌아간다. pathX, pathY 에 걸어갈 방향을 넣는다. */
    void steer(Monster m) {
        pathX = m.dirX;
        pathY = m.dirY;
        Room r = g.fightRoom();
        if (r == null || r.scene == null || clearLine(r, m.x, m.y, hero.x, hero.y)) return;
        updateFlow(r);
        int mx = tileX(m.x), my = tileY(m.y);
        int best = flowAt(r, mx, my);
        if (best < 0) best = Integer.MAX_VALUE;
        int bx = mx, by = my;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) continue;
                if (ox != 0 && oy != 0 && (flowAt(r, mx + ox, my) < 0 || flowAt(r, mx, my + oy) < 0)) continue;
                int f = flowAt(r, mx + ox, my + oy);
                if (f >= 0 && f < best) {
                    best = f;
                    bx = mx + ox;
                    by = my + oy;
                }
            }
        }
        if (bx == mx && by == my) return;
        float tx = center(bx) - m.x, ty = center(by) - m.y, l = (float) Math.sqrt(tx * tx + ty * ty);
        if (l < 0.01f) return;
        pathX = tx / l;
        pathY = ty / l;
    }

    /** 두 점 사이가 벽에서 넉넉히 떨어진 바닥으로만 이어지는가. */
    boolean clearLine(Room r, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        int n = Math.max(1, (int) (len / (T * 0.5f)));
        for (int i = 1; i < n; i++) {
            int tx = tileX(x0 + dx * i / n), ty = tileY(y0 + dy * i / n);
            if (!r.inside(tx, ty) || map.isBlockedOverlay(tx, ty)) return false;
        }
        return true;
    }

    /** 용사 칸에서 넓이 우선으로 퍼진 거리 (용사 칸이 바뀔 때만 다시 계산). */
    void updateFlow(Room r) {
        int hx = tileX(hero.x), hy = tileY(hero.y);
        if (r == flowRoom && hx == flowHeroX && hy == flowHeroY) return;
        flowRoom = r;
        flowHeroX = hx;
        flowHeroY = hy;
        int w = r.x1 - r.x0 + 1, h = r.y1 - r.y0 + 1;
        if (flow == null || flow.length < w * h) flow = new int[w * h];
        java.util.Arrays.fill(flow, 0, w * h, -1);
        if (!r.contains(hx, hy)) return;
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        flow[(hx - r.x0) + (hy - r.y0) * w] = 0;
        q.add(new int[]{hx, hy});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int d0 = flow[(p[0] - r.x0) + (p[1] - r.y0) * w];
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (!r.floor(nx, ny) || map.isBlockedOverlay(nx, ny)) continue;
                int i = (nx - r.x0) + (ny - r.y0) * w;
                if (flow[i] >= 0) continue;
                flow[i] = d0 + 1;
                q.add(new int[]{nx, ny});
            }
        }
    }

    int flowAt(Room r, int x, int y) {
        if (r != flowRoom || !r.contains(x, y)) return -1;
        return flow[(x - r.x0) + (y - r.y0) * (r.x1 - r.x0 + 1)];
    }

    /** 잡몹: 종류마다 다르게 다가오고 공격한다. */
    void chaseMob(Monster m, float dist, float reach, float dt) {
        Balance.MonsterCfg mc = balance.monster;
        switch (m.ai()) {
            case "swoop": {
                // 용사 둘레를 빙글빙글 돌다가 방향을 정하고 덮친다.
                float orbit = mc.orbitTiles * T;
                if (m.attackCd <= 0 && dist < orbit * 1.8f) {
                    if (random.nextFloat() < mc.pelletChance) {
                        // 콩알탄: 가끔 덮치는 대신 작고 빠른 탄을 뱉는다 (fire)
                        m.castKind = "pellet";
                        m.enter(Monster.State.CAST_TELEGRAPH, mc.pelletTelegraph);
                        return;
                    }
                    m.lockX = m.dirX;
                    m.lockY = m.dirY;
                    m.enter(Monster.State.SWOOP_TELEGRAPH, m.telegraph(mc.swoopTelegraph));
                    packSwoop(m);
                    return;
                }
                float tx = -m.dirY * m.orbitSign, ty = m.dirX * m.orbitSign;
                float pull = (dist - orbit) / orbit;
                float mx = tx + m.dirX * pull * 2f, my = ty + m.dirY * pull * 2f;
                float l = (float) Math.sqrt(mx * mx + my * my);
                if (l > 0.01f && Physics.move(map, m, mx / l * m.speed * dt, my / l * m.speed * dt, T)) m.orbitSign = -m.orbitSign;
                m.walkTime += dt;
                return;
            }
            case "caster": {
                float keep = mc.keepAwayTiles * T;
                if (dist < keep * 0.6f && m.blinkCd <= 0 && boss.blinkAway(m)) {
                    // 순간이동하자마자 곧바로 한 발
                    m.castKind = "cast";
                    m.enter(Monster.State.CAST_TELEGRAPH, mc.blinkShotTelegraph);
                    return;
                }
                if (m.attackCd <= 0 && dist < mc.castRangeTiles * T) {
                    if (random.nextFloat() < mc.flameChance) {
                        // 불기둥: 불덩이 대신 용사 발밑에 (fire)
                        m.castKind = "flame";
                    } else {
                        m.castCount++;
                        m.castKind = mc.triEvery > 0 && m.castCount % mc.triEvery == 0 ? "tri" : "cast";
                    }
                    m.enter(Monster.State.CAST_TELEGRAPH, m.telegraph(mc.castTelegraph));
                    return;
                }
                float dir = dist < keep ? -1f : dist > keep * 1.4f ? 1f : 0f;
                if (dir != 0) {
                    float sx = dir > 0 ? pathX : -m.dirX, sy = dir > 0 ? pathY : -m.dirY;
                    Physics.move(map, m, sx * m.speed * dt, sy * m.speed * dt, T);
                    m.walkTime += dt;
                }
                return;
            }
            default:
                // melee, brute: 다가가서 휘두른다
                if (dist <= reach * ("brute".equals(m.ai()) ? 1.6f : 1f) && m.attackCd <= 0) {
                    m.enter(Monster.State.TELEGRAPH, m.telegraph(mc.telegraphSeconds));
                } else if (dist > reach * 0.7f) {
                    float spd = m.speed * (m.guardActive() ? mc.guardSpeed : 1f);
                    Physics.move(map, m, pathX * spd * dt, pathY * spd * dt, T);
                    m.walkTime += dt;
                }
        }
    }

    /** 떼 지어 덮치기: 근처에서 맴돌던 박쥐 하나가 조금 늦게 같이 덮친다. */
    void packSwoop(Monster lead) {
        Balance.MonsterCfg mc = balance.monster;
        if (random.nextFloat() >= mc.packChance) return;
        for (Monster o : monsters) {
            if (o == lead || o.dead || !"swoop".equals(o.ai()) || o.state != Monster.State.CHASE) continue;
            float dx = hero.x - o.x, dy = hero.y - o.y, d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 0.01f || d > mc.packRangeTiles * T) continue;
            o.dirX = o.lockX = dx / d;
            o.dirY = o.lockY = dy / d;
            o.enter(Monster.State.SWOOP_TELEGRAPH, o.telegraph(mc.swoopTelegraph) + mc.packDelay);
            return;
        }
    }

    /** 앞쪽 부채꼴 안에 용사가 있으면 맞힌다. */
    void meleeStrike(Monster m, float reach, float mult) {
        float dx = hero.x - m.x, dy = hero.y - m.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float dot = dist > 0.01f ? (dx * m.dirX + dy * m.dirY) / dist : 1f;
        if (dist <= reach + 4 && dot > 0.2f) hitHero(m.attack(), mult, m.x, m.y);
        events.add(GameEvent.sound("monsterAttack"));
    }

    /** 모은 투사체를 쏜다: cast(한 발), tri(세 갈래), volley(부채꼴), ring(사방). */
    void fire(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        float speed = mc.projectileSpeedTiles * T * (m.boss ? 1.1f : 1f);
        float base = (float) Math.atan2(m.dirY, m.dirX);
        float sy = m.centerY();
        String kind = m.castKind == null ? "cast" : m.castKind;
        int dmg = m.attack();
        if ("pellet".equals(kind)) {
            // 박쥐 콩알탄: 작고 빠른 탄 하나 (약하다)
            shoot(m, m.x, sy, base, mc.pelletSpeedTiles * T, Math.max(1, Math.round(dmg * mc.pelletMultiplier)), m.def.sprite);
            projectiles.get(projectiles.size() - 1).radius *= mc.pelletRadiusScale;
        } else if ("flame".equals(kind)) {
            // 마족병 불기둥: 지금 용사가 있는 자리에 예고 뒤 솟는다
            boss.circleHazard("flame", hero.x, hero.y, mc.flameRadiusTiles * T, mc.flameWarning, dmg, mc.flameMultiplier);
        } else if ("ring".equals(kind)) {
            int n = mc.ringCount + (m.finalBoss ? 4 : 0);
            float off = random.nextFloat() * 6.283f;
            for (int i = 0; i < n; i++) shoot(m, m.x, sy, off + 6.283f * i / n, speed * 0.8f, dmg, m.def.sprite);
        } else if ("crescent".equals(kind)) {
            // 혈월 검기: 크고 느린 붉은 검기 여러 줄
            int n = mc.crescentCount;
            float spread = (float) Math.toRadians(mc.crescentSpreadDegrees) * (n - 1);
            float cs = mc.crescentSpeedTiles * T;
            int cd = Math.max(1, Math.round(m.attack() * mc.crescentMultiplier));
            for (int i = 0; i < n; i++) {
                float a = base - spread / 2f + (n > 1 ? spread * i / (n - 1) : 0);
                shoot(m, m.x, sy, a, cs, cd, "crescent");
                projectiles.get(projectiles.size() - 1).radius *= mc.crescentRadiusScale;
            }
        } else if ("homing".equals(kind)) {
            // 유도 마탄: 느리게 날며 용사 쪽으로 방향을 튼다. 벽에 닿으면 사라진다
            int n = mc.homingCount;
            float spread = (float) Math.toRadians(mc.homingSpreadDegrees);
            for (int i = 0; i < n; i++) {
                float a = base - spread / 2f + (n > 1 ? spread * i / (n - 1) : 0);
                shoot(m, m.x, sy, a, mc.homingSpeedTiles * T, dmg, m.def.sprite);
                Projectile p = projectiles.get(projectiles.size() - 1);
                p.homing = mc.homingTurn;
                p.dodgeVanish = true;
                p.life = mc.homingLife;
                p.radius *= 1.2f;
            }
        } else if ("orb".equals(kind)) {
            // 어둠 구체: 크고 느린 구체 하나. orbFuse 초 뒤나 벽에 닿으면 터진다
            shoot(m, m.x, sy, base, mc.orbSpeedTiles * T, dmg, m.def.sprite);
            Projectile p = projectiles.get(projectiles.size() - 1);
            p.radius *= mc.orbRadiusScale;
            p.life = mc.orbFuse;
            p.burst = mc.orbBurst;
        } else if ("volley".equals(kind) || "tri".equals(kind)) {
            int n = "tri".equals(kind) ? 3 : mc.volleyCount;
            float spread = (float) Math.toRadians("tri".equals(kind) ? 30 : mc.volleySpreadDegrees);
            for (int i = 0; i < n; i++) shoot(m, m.x, sy, base - spread / 2f + spread * i / Math.max(1, n - 1), speed, dmg, m.def.sprite);
        } else {
            shoot(m, m.x, sy, base, speed, dmg, m.def.sprite);
        }
        events.add(GameEvent.sound("cast"));
    }

    void shoot(Monster m, float x, float y, float angle, float speed, int damage, String source) {
        Projectile p = new Projectile();
        p.x = x + (float) Math.cos(angle) * m.hurtRadius;
        p.y = y + (float) Math.sin(angle) * m.hurtRadius;
        p.vx = (float) Math.cos(angle) * speed;
        p.vy = (float) Math.sin(angle) * speed;
        p.radius = balance.monster.projectileRadius * (m.boss ? 1.4f : 1f);
        p.life = 4f;
        p.damage = damage;
        p.source = source;
        projectiles.add(p);
    }

    public void updateProjectiles(float dt) {
        List<Projectile> bursts = new ArrayList<>();
        for (Projectile p : projectiles) {
            if (p.dead) continue;
            p.age += dt;
            p.life -= dt;
            if (p.homing > 0) steerHoming(p, dt);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.life <= 0 || map.solid(tileX(p.x), tileY(p.y - 6))) {
                p.dead = true;
                if (p.burst > 0) burst(p, p.x - p.vx * dt, p.y - p.vy * dt, bursts);
                continue;
            }
            float hx = hero.x - p.x, hy = hero.centerY() - p.y;
            float r = p.radius + hero.hw;
            if (p.homing > 0) {
                // 유도 마탄: 가까이 오면 더 꺾지 않고 곧게 날아간다 (옆으로 비키거나 굴러서 피할 수 있다)
                float stop = balance.monster.homingStopTiles * T;
                if (hx * hx + hy * hy < stop * stop) p.homing = 0;
            }
            if (hx * hx + hy * hy < r * r) {
                if (hero.rolling()) {
                    // 굴러서 피한다. 유도 마탄은 한 번 피하면 사라진다
                    if (p.dodgeVanish) p.dead = true;
                    continue;
                }
                p.dead = true;
                hitHero(p.damage, 1f, p.x - p.vx, p.y - p.vy);
                if (g.heroJustDied()) return;
                if (p.burst > 0) burst(p, p.x, p.y, bursts);
            }
        }
        projectiles.removeIf(p -> p.dead);
        projectiles.addAll(bursts);
    }

    /** 어둠 구체가 터진다: 그 자리에서 작은 탄이 사방으로 퍼진다. */
    void burst(Projectile orb, float x, float y, List<Projectile> out) {
        Balance.MonsterCfg mc = balance.monster;
        float speed = mc.projectileSpeedTiles * T * 0.8f;
        int dmg = Math.max(1, Math.round(orb.damage * mc.orbShardMultiplier));
        float off = random.nextFloat() * 6.283f;
        for (int i = 0; i < orb.burst; i++) {
            float a = off + 6.283f * i / orb.burst;
            Projectile p = new Projectile();
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(a) * speed;
            p.vy = (float) Math.sin(a) * speed;
            p.radius = mc.projectileRadius * 1.2f;
            p.life = 3f;
            p.damage = dmg;
            p.source = orb.source;
            out.add(p);
        }
        events.add(GameEvent.sound("cast"));
    }

    /** 유도탄: 속도는 그대로, 방향만 용사 쪽으로 조금씩 튼다. */
    void steerHoming(Projectile p, float dt) {
        float sp = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
        float cur = (float) Math.atan2(p.vy, p.vx);
        float want = (float) Math.atan2(hero.centerY() - p.y, hero.x - p.x);
        float diff = want - cur;
        while (diff > Math.PI) diff -= (float) (2 * Math.PI);
        while (diff < -Math.PI) diff += (float) (2 * Math.PI);
        float step = p.homing * dt;
        cur += Math.max(-step, Math.min(step, diff));
        p.vx = (float) Math.cos(cur) * sp;
        p.vy = (float) Math.sin(cur) * sp;
    }

    /** 몬스터끼리 겹치지 않게 살짝 민다. */
    void separate() {
        for (int i = 0; i < monsters.size(); i++) {
            Monster a = monsters.get(i);
            if (a.dead) continue;
            for (int j = i + 1; j < monsters.size(); j++) {
                Monster b = monsters.get(j);
                if (b.dead) continue;
                float dx = b.x - a.x, dy = b.y - a.y;
                float min = a.hw + b.hw;
                float d2 = dx * dx + dy * dy;
                if (d2 >= min * min || d2 < 0.0001f) continue;
                float d = (float) Math.sqrt(d2);
                float push = (min - d) / 2f;
                Physics.move(map, a, -dx / d * push, -dy / d * push, T);
                Physics.move(map, b, dx / d * push, dy / d * push, T);
            }
        }
    }
}
