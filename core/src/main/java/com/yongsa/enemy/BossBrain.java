package com.yongsa.enemy;

import com.yongsa.combat.HeroShot;
import com.yongsa.combat.Physics;
import com.yongsa.config.Balance;
import com.yongsa.config.Balance.DungeonDef;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import com.yongsa.map.WorldMap;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 보스 패턴 (보스·몬스터 담당): 패턴 고르기와 시작, 긴 패턴(채널), 바닥 공격(Hazard),
 * 실명, 마왕 변신, 말풍선, 순간이동. 공통 상태 기계와 쏘기는 MonsterBrain.
 */
public class BossBrain extends EnemyContext {
    MonsterBrain mobs;

    /** 매 프레임 쿨타임. */
    public void tick(float dt) {
        blindCd -= dt;
    }

    public BossBrain(GameSession g, List<GameEvent> events, MonsterBrain mobs) {
        super(g, events);
        this.mobs = mobs;
        mobs.boss = this;
    }

    /** 피웅덩이에서 닳은 체력 (1 이 넘으면 깎는다), 숫자를 띄울 때까지 모은 양. */
    float poolAcc, poolShown, poolTextCd;

    float blindCd;

    /**
     * 구석 몰이 방지: 벽 가까이 있는 보스에게 용사가 바짝 붙어 있는 시간이 쌓이면
     * 멀리 순간이동하고 세 갈래 탄을 쏜다. 순간이동했으면 true.
     */
    boolean cornered(Monster m, float dist, float reach, float dt) {
        Balance.MonsterCfg mc = balance.monster;
        boolean idle = m.state == Monster.State.CHASE || m.state == Monster.State.RECOVER
                || m.state == Monster.State.TELEGRAPH;
        if (idle && dist < reach * 1.6f && nearWall(m)) m.cornerT += dt;
        else m.cornerT = Math.max(0f, m.cornerT - dt * 0.5f);
        if (m.cornerT < mc.cornerTime || m.blinkCd > 0 || !idle) return false;
        m.cornerT = 0;
        if (m.def.cornerLeap) {
            // 혈월의 기사: 순간이동 대신 뒤로 크게 도약한다
            if (!leapAway(m)) return false;
            m.blinkCd = mc.cornerCooldown;
            return true;
        }
        if (!blinkAway(m)) return false;
        m.blinkCd = mc.cornerCooldown;
        mobs.aimAtHero(m);
        m.castKind = "tri";
        mobs.fire(m);
        m.enter(Monster.State.RECOVER, 0.5f);
        return true;
    }

    boolean nearWall(Monster m) {
        float r = m.hurtRadius + T * 0.9f;
        for (int i = 0; i < 8; i++) {
            float a = 6.283f * i / 8f;
            if (map.solid(tileX(m.x + (float) Math.cos(a) * r), tileY(m.y + (float) Math.sin(a) * r))) return true;
        }
        return false;
    }

    /** 보스: 다가가 근접 공격을 하다가, 패턴 쿨타임이 차면 고유 패턴을 하나 고른다. */
    void chaseBoss(Monster m, float dist, float reach, float dt, List<Monster> summoned) {
        Balance.MonsterCfg mc = balance.monster;
        if (m.patternCd <= 0 && dist < 11 * T) {
            String p = pickPattern(m);
            m.lastPattern = p;
            m.patternCd = mc.bossPatternGap * (m.lastStand ? mc.lastStandGapMultiplier : 1f);
            if (startPattern(m, p, summoned)) return;
        }
        if (m.finalBoss && !m.phase2 && m.def.noMeleePhase1) {
            // 마왕 1페이즈: 기본 근접 공격 없이 거리를 두고 패턴만 쓴다
            float keep = mc.demonKeepTiles * T, k = m.speed * dt;
            if (dist > keep * 1.3f) {
                Physics.move(map, m, mobs.pathX * k, mobs.pathY * k, T);
                m.walkTime += dt;
            } else if (dist < keep * 0.8f && dist > 0.01f) {
                Physics.move(map, m, (m.x - hero.x) / dist * k, (m.y - hero.y) / dist * k, T);
                m.walkTime += dt;
            }
            return;
        }
        if (dist <= reach && m.attackCd <= 0) {
            m.enter(Monster.State.TELEGRAPH, mc.telegraphSeconds);
        } else if (dist > reach * 0.7f) {
            float k = m.speed * dt * (m.enraged ? 1.15f : 1f);
            Physics.move(map, m, mobs.pathX * k, mobs.pathY * k, T);
            m.walkTime += dt;
        }
    }

    String pickPattern(Monster m) {
        List<String> pool = new ArrayList<>();
        if (m.finalBoss && m.phase2) {
            // 마왕 2페이즈는 패턴이 통째로 바뀐다
            if (m.def.phase2Patterns != null) pool.addAll(m.def.phase2Patterns);
        } else {
            if (m.def.patterns != null) pool.addAll(m.def.patterns);
            if (m.enraged && m.def.phase2Patterns != null) pool.addAll(m.def.phase2Patterns);
        }
        if (m.shield > 0) pool.removeIf("shield"::equals); // 방패가 남아 있으면 다시 쓰지 않는다
        if (pool.isEmpty()) return "slam";
        if (pool.size() > 1) pool.removeIf(p -> p.equals(m.lastPattern));
        if (pool.isEmpty()) return "slam";
        return pool.get(random.nextInt(pool.size()));
    }

    boolean startPattern(Monster m, String p, List<Monster> summoned) {
        Balance.MonsterCfg mc = balance.monster;
        switch (p) {
            case "slam":
                m.slamX = hero.x;
                m.slamY = hero.y;
                m.slamRadius = mc.bossSlamRadiusTiles * T * (m.finalBoss ? 1.3f : 1f);
                m.enter(Monster.State.SLAM_TELEGRAPH, mc.bossSlamTelegraph);
                return true;
            case "charge":
                m.rushLeft = 0;
                mobs.aimAtHero(m);
                m.enter(Monster.State.CHARGE_TELEGRAPH, mc.chargeTelegraph);
                events.add(GameEvent.sound("roar"));
                return true;
            case "rush":
                m.rushLeft = mc.rushCount;
                mobs.aimAtHero(m);
                m.enter(Monster.State.CHARGE_TELEGRAPH, mc.rushTelegraph * 1.4f);
                events.add(GameEvent.sound("roar"));
                return true;
            case "combo":
                m.comboLeft = mc.comboHits;
                comboStep(m);
                return true;
            case "volley":
            case "ring":
                m.castKind = p;
                m.enter(Monster.State.CAST_TELEGRAPH, mc.volleyTelegraph);
                return true;
            case "rockfall":
                startChannel(m, p, mc.rockCount, 0.2f);
                events.add(GameEvent.sound("roar"));
                events.add(GameEvent.toast("천장이 무너진다! 계속 움직이자"));
                return true;
            case "lightning":
                startChannel(m, p, mc.strikeCount, 0.25f);
                events.add(GameEvent.sound("roar"));
                return true;
            case "firespin":
                startChannel(m, p, 0, 0f);
                m.stateT = m.stateTotal = mc.spinMin + random.nextFloat() * (mc.spinMax - mc.spinMin);
                m.spinAngle = random.nextFloat() * 6.283f;
                m.orbitSign = random.nextBoolean() ? 1 : -1;
                events.add(GameEvent.sound("roar"));
                return true;
            case "spearstand":
                // 혈월의 기사 창 찌르기: 멈춰 선 채로만 창이 나온다 (channel)
                startChannel(m, p, mc.spearStandVolleys, 0.25f);
                events.add(GameEvent.toast(m.def.name + "이(가) 멈춰 서서 창을 겨눈다!"));
                events.add(GameEvent.sound("cast"));
                return true;
            case "bloodpool":
                spawnPools(m, "blood");
                startChannel(m, p, 1, mc.spearDelay);
                events.add(GameEvent.sound("cast"));
                return true;
            case "shield":
                m.shield = m.shieldMax = Math.round(m.maxHp * mc.shieldRatio);
                m.enter(Monster.State.RECOVER, 0.6f);
                events.add(GameEvent.toast(m.def.name + "이(가) 방패를 둘렀다. 깨기 전엔 체력이 닳지 않는다"));
                events.add(GameEvent.sound("block"));
                GameEvent fx = GameEvent.fx(GameEvent.Type.SHIELD_UP, m.x, m.centerY());
                fx.size = m.hurtRadius * 2.4f;
                events.add(fx);
                return true;
            case "tentacle": {
                float rr = mc.tentacleRingTiles * T;
                float off = random.nextFloat() * 6.283f;
                circleHazard("tentacle", hero.x, hero.y, mc.tentacleRadiusTiles * T, mc.tentacleWarning, m.attack(), mc.tentacleMultiplier);
                for (int i = 1; i < mc.tentacleCount; i++) {
                    float a = off + 6.283f * i / (mc.tentacleCount - 1);
                    float tx = hero.x + (float) Math.cos(a) * rr, ty = hero.y + (float) Math.sin(a) * rr;
                    if (map.solid(tileX(tx), tileY(ty))) continue;
                    circleHazard("tentacle", tx, ty, mc.tentacleRadiusTiles * T, mc.tentacleWarning + 0.15f, m.attack(), mc.tentacleMultiplier);
                }
                m.enter(Monster.State.RECOVER, mc.tentacleWarning + 0.5f);
                events.add(GameEvent.sound("roar"));
                return true;
            }
            case "summon": {
                int count = m.def.summonCount > 0 ? m.def.summonCount : mc.summonCount;
                if (m.def.summonCountMax > count) count += random.nextInt(m.def.summonCountMax - count + 1); // 2~3마리 무작위
                int alive = 0;
                for (Monster o : monsters) if (!o.dead && !o.boss) alive++;
                if (alive >= count) return false;
                Room r = g.fightRoom();
                if (r == null) return false;
                DungeonDef d = balance.dungeon(m.def.summonFrom != null ? m.def.summonFrom : r.dungeonId);
                for (int i = 0; i < count - alive; i++) {
                    Monster s = g.makeMonster(r, d.mob, false, false, false);
                    if (s != null) summoned.add(s);
                }
                events.add(GameEvent.toast(m.def.name + "이(가) 부하를 불렀다!"));
                events.add(GameEvent.sound("roar"));
                m.enter(Monster.State.RECOVER, 0.8f);
                blind(m);
                return true;
            }
            case "bonespike": {
                // 용사 발밑 하나 + 주변에 흩어진 뼈 가시
                float spread = mc.spikeSpreadTiles * T;
                circleHazard("bone", hero.x, hero.y, mc.spikeRadiusTiles * T, mc.spikeWarning, m.attack(), mc.spikeMultiplier);
                for (int i = 1; i < mc.spikeCount; i++) {
                    for (int tries = 0; tries < 8; tries++) {
                        float a = random.nextFloat() * 6.283f, d = spread * (0.45f + 0.55f * random.nextFloat());
                        float tx = hero.x + (float) Math.cos(a) * d, ty = hero.y + (float) Math.sin(a) * d;
                        if (map.solid(tileX(tx), tileY(ty))) continue;
                        circleHazard("bone", tx, ty, mc.spikeRadiusTiles * T, mc.spikeWarning + 0.1f * i, m.attack(), mc.spikeMultiplier);
                        break;
                    }
                }
                m.slamX = hero.x;
                m.slamY = hero.y;
                m.castKind = "bonespike";
                m.enter(Monster.State.RECOVER, mc.spikeWarning + 0.4f);
                events.add(GameEvent.sound("cast"));
                blind(m);
                return true;
            }
            case "whip": {
                // 앞쪽 부채꼴을 한쪽 끝에서 다른 끝으로 휩쓴다: 선 여러 개가 차례로 터진다
                mobs.aimAtHero(m);
                float base = (float) Math.atan2(m.dirY, m.dirX);
                float arc = (float) Math.toRadians(mc.whipArcDegrees);
                boolean cw = random.nextBoolean();
                for (int i = 0; i < mc.whipLines; i++) {
                    float t = mc.whipLines == 1 ? 0.5f : i / (float) (mc.whipLines - 1);
                    float a = base + arc * ((cw ? t : 1 - t) - 0.5f);
                    Hazard h = new Hazard(Hazard.Kind.LINE, "whip");
                    h.x = m.x;
                    h.y = m.centerY();
                    h.x2 = m.x + (float) Math.cos(a) * mc.whipRadiusTiles * T;
                    h.y2 = h.y + (float) Math.sin(a) * mc.whipRadiusTiles * T;
                    h.radius = T * 0.55f;
                    h.delay = h.warning = mc.whipWarning + mc.whipSweep * t;
                    h.damage = m.attack();
                    h.multiplier = mc.whipMultiplier;
                    hazards.add(h);
                }
                m.enter(Monster.State.SLAM_TELEGRAPH, mc.whipWarning);
                m.slamX = m.x;
                m.slamY = m.y;
                m.slamRadius = 0;
                events.add(GameEvent.sound("roar"));
                return true;
            }
            case "boulder": {
                // 바위 던지기: 용사 자리에 큰 바위가 떨어지고, 파편이 사방으로 튄다 (updateHazards 의 shatter)
                mobs.aimAtHero(m);
                circleHazard("boulder", hero.x, hero.y, mc.boulderRadiusTiles * T, mc.boulderWarning, m.attack(), mc.boulderMultiplier);
                m.enter(Monster.State.RECOVER, 0.9f);
                events.add(GameEvent.sound("roar"));
                return true;
            }
            case "pound": {
                // 연속 내려찍기: 용사 쪽으로 쿵, 쿵. 찍을 때마다 그 자리에서 지진 파동이 퍼지고 (quakeFrom),
                // 두 번째는 첫 파동의 첫 고리가 사라지는 순간 떨어진다. 다 찍으면 오래 숨을 고른다 (반격 기회)
                mobs.aimAtHero(m);
                float last = -1;
                for (int i = 0; i < mc.poundCount; i++) {
                    float d = (mc.poundStartTiles + mc.poundStepTiles * i) * T;
                    float px = m.x + m.dirX * d, py = m.y + m.dirY * d;
                    if (map.solid(tileX(px), tileY(py))) break; // 벽 너머로는 찍지 않는다
                    last = mc.poundWarning + mc.quakeGap * i;
                    circleHazard("pound", px, py, (mc.poundRadiusTiles + mc.poundRadiusGrowTiles * i) * T, last, m.attack(), mc.poundMultiplier);
                }
                if (last < 0) return false; // 바로 앞이 벽이면 다른 패턴
                m.enter(Monster.State.RECOVER, last + mc.quakeGap * mc.quakeWaves + mc.poundRecover);
                events.add(GameEvent.toast(m.def.name + "이(가) 주먹을 높이 든다! 옆으로 비키자"));
                events.add(GameEvent.sound("roar"));
                return true;
            }
            case "spinslash": {
                // 회전 베기: 보스 둘레 원을 예고 뒤 한 번에 벤다
                circleHazard("spin", m.x, m.y, mc.spinSlashRadiusTiles * T, mc.spinSlashWarning, m.attack(), mc.spinSlashMultiplier);
                m.enter(Monster.State.RECOVER, mc.spinSlashWarning + 0.5f);
                events.add(GameEvent.sound("roar"));
                return true;
            }
            case "crescent":
                // 혈월 검기: 모았다가 초승달 검기를 부채꼴로 날린다 (fire)
                m.castKind = p;
                m.enter(Monster.State.CAST_TELEGRAPH, mc.volleyTelegraph);
                return true;
            case "cage": {
                // 번개 감옥: 용사 둘레 사각형 번개 벽이 여러 번 번쩍이고, 가운데가 터진다.
                // 한쪽 벽(open)만 가운데가 터진 뒤에 터지니 그쪽으로 빠져나가야 한다.
                float hs = mc.cageHalfTiles * T, cx = hero.x, cy = hero.y;
                float[][] c = {{cx - hs, cy - hs}, {cx + hs, cy - hs}, {cx + hs, cy + hs}, {cx - hs, cy + hs}};
                int open = random.nextInt(4);
                for (int i = 0; i < 4; i++) {
                    int pulses = i == open ? 1 : mc.cagePulses;
                    for (int k = 0; k < pulses; k++) {
                        Hazard h = new Hazard(Hazard.Kind.LINE, "cage");
                        h.x = c[i][0];
                        h.y = c[i][1];
                        h.x2 = c[(i + 1) % 4][0];
                        h.y2 = c[(i + 1) % 4][1];
                        h.radius = mc.cageWidthTiles * T / 2f;
                        h.delay = h.warning = i == open ? mc.cageOpenDelay : mc.cageWarning + mc.cagePulseGap * k;
                        h.damage = m.attack();
                        h.multiplier = mc.cageMultiplier;
                        hazards.add(h);
                    }
                }
                circleHazard("bolt", cx, cy, hs, mc.cageCoreWarning, m.attack(), mc.cageCoreMultiplier);
                m.enter(Monster.State.RECOVER, mc.cageCoreWarning + 0.3f);
                events.add(GameEvent.toast("번개 감옥! 늦게 빛나는 벽으로 빠져나가자"));
                events.add(GameEvent.sound("cast"));
                return true;
            }
            case "homing":
                // 유도 마탄: 모았다가 천천히 따라오는 탄을 쏜다 (fire)
                m.castKind = p;
                m.enter(Monster.State.CAST_TELEGRAPH, mc.volleyTelegraph);
                return true;
            case "darkorb":
                // 어둠 구체: 모았다가 느린 구체를 쏜다. 날아가다 터지며 사방으로 탄 (fire, updateProjectiles)
                m.castKind = "orb";
                m.enter(Monster.State.CAST_TELEGRAPH, mc.volleyTelegraph);
                return true;
            case "brand": {
                // 마왕의 낙인: 용사를 따라다니다 멈추고, 잠시 뒤 그 자리에서 폭발한다
                Hazard h = new Hazard(Hazard.Kind.CIRCLE, "brand");
                h.x = hero.x;
                h.y = hero.y;
                h.radius = mc.brandRadiusTiles * T;
                h.followT = mc.brandFollow;
                h.delay = h.warning = mc.brandFollow + mc.brandLock;
                h.damage = m.attack();
                h.multiplier = mc.brandMultiplier;
                hazards.add(h);
                m.enter(Monster.State.RECOVER, 0.6f);
                events.add(GameEvent.toast("마왕의 낙인이 찍혔다! 멈추는 순간 자리를 벗어나자"));
                events.add(GameEvent.sound("cast"));
                return true;
            }
            case "eyes": {
                // 눈알이 차례로 뜬다: 뜨는 순간 그쪽을 보고 있으면 안 된다
                Room r = g.fightRoom();
                for (int i = 0; i < mc.eyeCount; i++) {
                    for (int tries = 0; tries < 12; tries++) {
                        float a = random.nextFloat() * 6.283f, d = mc.eyeDistanceTiles * T * (0.7f + 0.3f * random.nextFloat());
                        float tx = hero.x + (float) Math.cos(a) * d, ty = hero.y + (float) Math.sin(a) * d;
                        if (map.solid(tileX(tx), tileY(ty)) || (r != null && !r.inside(tileX(tx), tileY(ty)))) continue;
                        Hazard h = new Hazard(Hazard.Kind.EYE, "eye");
                        h.x = tx;
                        h.y = ty;
                        h.radius = T * 0.9f;
                        h.delay = h.warning = mc.eyeWarning + mc.eyeGap * i;
                        hazards.add(h);
                        break;
                    }
                }
                m.enter(Monster.State.RECOVER, mc.eyeWarning + 0.5f);
                events.add(GameEvent.toast("눈알이 뜬다! 눈을 돌려라"));
                events.add(GameEvent.sound("roar"));
                return true;
            }
            default:
                return false;
        }
    }

    void startChannel(Monster m, String kind, int count, float firstDelay) {
        m.channelKind = kind;
        m.channelLeft = count;
        m.channelT = firstDelay;
        m.enter(Monster.State.CHANNEL, 99f);
    }

    /** 제자리에서 이어 가는 패턴 한 프레임. */
    void channel(Monster m, float dt) {
        Balance.MonsterCfg mc = balance.monster;
        m.channelT -= dt;
        switch (m.channelKind) {
            case "rockfall":
            case "lightning": {
                boolean rock = "rockfall".equals(m.channelKind);
                if (m.channelT > 0) return;
                if (m.channelLeft <= 0) {
                    m.enter(Monster.State.RECOVER, rock ? 0.9f : 0.7f);
                    return;
                }
                m.channelLeft--;
                // 그 순간 용사가 있는 자리를 노린다: 멈춰 있으면 맞는다
                float jitter = rock ? 0 : T * 0.4f;
                float tx = hero.x + (random.nextFloat() * 2 - 1) * jitter, ty = hero.y + (random.nextFloat() * 2 - 1) * jitter;
                if (rock) circleHazard("rock", tx, ty, mc.rockRadiusTiles * T, mc.rockWarning, m.attack(), mc.rockMultiplier);
                else circleHazard("bolt", tx, ty, mc.strikeRadiusTiles * T, mc.strikeWarning, m.attack(), mc.strikeMultiplier);
                m.channelT = m.channelLeft > 0 ? (rock ? mc.rockInterval : mc.strikeInterval) : (rock ? mc.rockWarning : mc.strikeWarning);
                return;
            }
            case "firespin": {
                m.stateT -= dt;
                m.spinAngle += mc.spinTurnSpeed * m.orbitSign * dt;
                m.dirX = (float) Math.cos(m.spinAngle);
                m.dirY = (float) Math.sin(m.spinAngle);
                while (m.channelT <= 0) {
                    m.channelT += mc.spinRate;
                    float speed = mc.spinProjectileSpeedTiles * T;
                    int dmg = Math.max(1, Math.round(m.attack() * mc.spinMultiplier));
                    mobs.shoot(m, m.x, m.centerY(), m.spinAngle, speed, dmg, "fire");
                    mobs.shoot(m, m.x, m.centerY(), m.spinAngle + 3.1416f, speed, dmg, "fire");
                }
                if (m.stateT <= 0) m.enter(Monster.State.RECOVER, 1.1f);
                return;
            }
            case "spearstand": {
                if (m.channelT > 0) return;
                if (m.channelLeft <= 0) {
                    // 마지막 창이 다 터질 때까지 서 있다가 다시 움직인다 (2페이즈 빠른 시계에서도 창이 잘리지 않게)
                    for (Hazard h : hazards) if (h.standingOwner == m && !h.dead && h.delay > 0) return;
                    m.enter(Monster.State.RECOVER, 0.4f);
                    return;
                }
                m.channelLeft--;
                mobs.aimAtHero(m);
                float base = (float) Math.atan2(m.dirY, m.dirX);
                float spread = (float) Math.toRadians(mc.spearSpreadDegrees);
                for (int i = 0; i < mc.spearCount; i++) {
                    float a = base + spread * (i - (mc.spearCount - 1) / 2f);
                    Hazard h = new Hazard(Hazard.Kind.LINE, "spear");
                    h.x = m.x;
                    h.y = m.y;
                    h.x2 = m.x + (float) Math.cos(a) * mc.spearLengthTiles * T;
                    h.y2 = m.y + (float) Math.sin(a) * mc.spearLengthTiles * T;
                    h.radius = mc.spearWidthTiles * T / 2f;
                    h.delay = h.warning = mc.spearStandWarning;
                    h.damage = m.attack();
                    h.multiplier = mc.spearMultiplier;
                    h.standingOwner = m;
                    hazards.add(h);
                }
                // 마지막 줄은 창이 다 터질 때까지 서 있다가 움직인다
                m.channelT = m.channelLeft > 0 ? mc.spearStandInterval : mc.spearStandWarning + 0.05f;
                return;
            }
            case "bloodpool": {
                if (m.channelT > 0) return;
                if (m.channelLeft > 0) {
                    m.channelLeft = 0;
                    mobs.aimAtHero(m);
                    float base = (float) Math.atan2(m.dirY, m.dirX);
                    float spread = (float) Math.toRadians(mc.spearSpreadDegrees);
                    for (int i = 0; i < mc.spearCount; i++) {
                        float a = base + spread * (i - (mc.spearCount - 1) / 2f);
                        Hazard h = new Hazard(Hazard.Kind.LINE, "spear");
                        h.x = m.x;
                        h.y = m.y;
                        h.x2 = m.x + (float) Math.cos(a) * mc.spearLengthTiles * T;
                        h.y2 = m.y + (float) Math.sin(a) * mc.spearLengthTiles * T;
                        h.radius = mc.spearWidthTiles * T / 2f;
                        h.delay = h.warning = mc.spearWarning;
                        h.damage = m.attack();
                        h.multiplier = mc.spearMultiplier;
                        hazards.add(h);
                    }
                    m.channelT = mc.spearWarning + 0.3f;
                    return;
                }
                m.enter(Monster.State.RECOVER, 0.4f);
                return;
            }
            default:
                m.enter(Monster.State.RECOVER, 0.5f);
        }
    }

    /** 골렘이 내려찍은 자리에서 지진 파동: 둘레 고리 quakeWaves 겹이 quakeGap 간격으로 안에서 밖으로 터진다. */
    void quakeFrom(Hazard slam, List<Hazard> out) {
        Balance.MonsterCfg mc = balance.monster;
        float w = mc.quakeWaveTiles * T;
        for (int i = 0; i < mc.quakeWaves; i++) {
            Hazard h = new Hazard(Hazard.Kind.CIRCLE, "quake");
            h.x = slam.x;
            h.y = slam.y;
            h.innerRadius = slam.radius + w * i;
            h.radius = slam.radius + w * (i + 1);
            h.delay = h.warning = mc.quakeGap * (i + 1);
            h.damage = slam.damage;
            h.multiplier = mc.quakeMultiplier;
            out.add(h);
        }
    }

    /** 바위가 떨어진 자리에서 파편이 사방으로 튄다. */
    void shatter(Hazard h) {
        Balance.MonsterCfg mc = balance.monster;
        float speed = mc.shardSpeedTiles * T;
        int dmg = Math.max(1, Math.round(h.damage * mc.shardMultiplier));
        float off = random.nextFloat() * 6.283f;
        for (int i = 0; i < mc.shardCount; i++) {
            float a = off + 6.283f * i / mc.shardCount;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            Projectile p = new Projectile();
            p.x = h.x + cos * h.radius * 0.4f;
            p.y = h.y + sin * h.radius * 0.4f;
            p.vx = cos * speed;
            p.vy = sin * speed;
            p.radius = mc.projectileRadius;
            p.life = mc.shardLife;
            p.damage = dmg;
            p.source = "golem";
            projectiles.add(p);
        }
    }

    void circleHazard(String style, float x, float y, float radius, float warning, int damage, float mult) {
        Hazard h = new Hazard(Hazard.Kind.CIRCLE, style);
        h.x = x;
        h.y = y;
        h.radius = radius;
        h.delay = h.warning = warning;
        h.damage = damage;
        h.multiplier = mult;
        hazards.add(h);
    }

    /** 실명 (해골 사제): 쿨타임이 지났으면 화면을 어둡게 한다. */
    void blind(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        // 실명은 뼈 가시를 쓰는 보스(선우 버전 리치)만 건다
        if (m.def.patterns == null || !m.def.patterns.contains("bonespike") || blindCd > 0 || mc.blindSeconds <= 0) return;
        g.blindT = mc.blindSeconds;
        blindCd = mc.blindCooldown;
        events.add(GameEvent.toast("앞이 캄캄해졌다! 바닥의 빛을 보고 피하자"));
    }

    /** 피웅덩이: 용사 발밑 하나와 방 안 아무 데나 몇 개. */
    void spawnPools(Monster m, String style) {
        Balance.MonsterCfg mc = balance.monster;
        Room r = g.fightRoom();
        for (int i = 0; i < mc.poolCount; i++) {
            float px = hero.x, py = hero.y;
            if (i > 0 && r != null) {
                for (int tries = 0; tries < 30; tries++) {
                    int tx = r.x0 + 2 + random.nextInt(Math.max(1, r.x1 - r.x0 - 3));
                    int ty = r.y0 + 2 + random.nextInt(Math.max(1, r.y1 - r.y0 - 3));
                    if (!r.inside(tx, ty)) continue;
                    px = center(tx);
                    py = center(ty);
                    break;
                }
            }
            Hazard h = new Hazard(Hazard.Kind.POOL, style);
            h.x = px;
            h.y = py;
            h.startRadius = h.radius = mc.poolStartRadiusTiles * T;
            h.maxRadius = mc.poolMaxRadiusTiles * T;
            h.grow = mc.poolGrowTime;
            h.delay = h.warning = 0.35f;
            h.life = mc.poolLife;
            hazards.add(h);
        }
    }

    /** 바닥 공격: 예고가 끝나면 터지고, 피웅덩이는 남아서 밟으면 닳고 느려진다. */
    public void updateHazards(float dt) {
        Balance.MonsterCfg mc = balance.monster;
        boolean inPool = false;
        List<Hazard> spawned = new ArrayList<>();
        for (Hazard h : hazards) {
            if (h.dead) continue;
            if (h.standingOwner != null && h.delay > 0
                    && (h.standingOwner.dead || h.standingOwner.state != Monster.State.CHANNEL)) {
                // 기사가 다시 움직이면 아직 안 나온 창은 나오지 않는다
                h.dead = true;
                continue;
            }
            h.age += dt;
            if (h.followT > 0) {
                h.followT -= dt;
                h.x = hero.x;
                h.y = hero.y;
            }
            if (h.delay > 0) {
                h.delay -= dt;
                if (h.delay > 0) continue;
                h.triggered = true;
                if (h.kind == Hazard.Kind.EYE) {
                    eyeOpens(h);
                    h.dead = true;
                    if (g.heroJustDied()) return;
                    continue;
                }
                if (h.kind != Hazard.Kind.POOL) {
                    if (h.multiplier > 0 && h.covers(hero.x, hero.y, hero.hw * 0.6f)) hitHero(h.damage, h.multiplier, h.x, h.y);
                    GameEvent fx = GameEvent.fx(GameEvent.Type.HAZARD_FX, h.x, h.y);
                    fx.text = h.style;
                    fx.size = h.radius;
                    fx.dirX = h.x2;
                    fx.dirY = h.y2;
                    events.add(fx);
                    events.add(GameEvent.sound("bolt".equals(h.style) ? "cast" : "slam"));
                    if ("pound".equals(h.style) || "boulder".equals(h.style)) {
                        // 골렘: 무거운 한 방마다 화면이 쿵 울린다
                        GameEvent shake = GameEvent.fx(GameEvent.Type.SLAM_FX, h.x, h.y);
                        shake.size = h.radius;
                        events.add(shake);
                    }
                    h.dead = true;
                    if (g.heroJustDied()) return;
                    if ("boulder".equals(h.style)) shatter(h);
                    if ("pound".equals(h.style)) quakeFrom(h, spawned);
                    continue;
                }
            }
            if (h.kind == Hazard.Kind.POOL) {
                h.life -= dt;
                float g = Math.min(1f, (h.age - h.warning) / Math.max(0.01f, h.grow));
                h.radius = h.startRadius + (h.maxRadius - h.startRadius) * Math.max(0f, g);
                if (h.life <= 0) h.dead = true;
                else if (h.covers(hero.x, hero.y, 0)) inPool = true;
            }
        }
        hazards.removeIf(h -> h.dead);
        hazards.addAll(spawned);
        poolTextCd -= dt;
        if (inPool) {
            if (hero.slowT <= 0.2f) hero.slowT = mc.slowSeconds;
            if (!g.devGodMode) {
                poolAcc += rules.maxHp(state) * mc.poolDamageRatio * dt;
                int take = (int) poolAcc;
                if (take > 0) {
                    poolAcc -= take;
                    poolShown += take;
                    state.hp -= take;
                    if (state.hp <= 0) {
                        g.die();
                        return;
                    }
                }
                if (poolTextCd <= 0 && poolShown > 0) {
                    poolTextCd = 0.6f;
                    events.add(GameEvent.floatText(hero.x, hero.y + 40, String.valueOf((int) poolShown), GameEvent.Tone.HERO_HIT));
                    poolShown = 0;
                }
            }
        }
    }

    /** 눈알이 뜬다: 용사가 그쪽을 보고 있으면 (구르는 중이 아니면) 지금 체력의 절반을 잃고 굳는다. */
    /** 지금 용사가 이 눈알 쪽을 보고 있나 (뜨는 순간 이러면 맞는다). 화면에서 위험 표시에 쓴다. */
    public boolean lookingAt(Hazard h) {
        float dx = h.x - hero.x, dy = h.y - hero.centerY(), l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 0.01f) return false;
        float cos = (dx * hero.aimX + dy * hero.aimY) / l;
        return cos >= (float) Math.cos(Math.toRadians(balance.monster.eyeAngleDegrees / 2f));
    }

    void eyeOpens(Hazard h) {
        Balance.MonsterCfg mc = balance.monster;
        GameEvent fx = GameEvent.fx(GameEvent.Type.HAZARD_FX, h.x, h.y);
        fx.text = h.style;
        fx.size = h.radius;
        events.add(fx);
        events.add(GameEvent.sound("cast"));
        float dx = h.x - hero.x, dy = h.y - hero.centerY(), l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 0.01f || hero.rolling() || hero.invuln > 0 || g.devGodMode) return;
        if (!lookingAt(h)) return;
        int take = state.hp / 2; // 절반이라 이것만으로는 쓰러지지 않는다
        state.hp -= take;
        hero.stunT = mc.eyeStun;
        hero.flash = 0.3f;
        hero.invuln = Math.max(hero.invuln, 0.4f);
        events.add(GameEvent.floatText(hero.x, hero.y + 40, String.valueOf(take), GameEvent.Tone.HERO_HIT));
        events.add(GameEvent.toast("눈이 마주쳤다! 몸이 굳는다"));
        events.add(GameEvent.sound("hurt"));
        GameEvent hit = GameEvent.fx(GameEvent.Type.HIT_FX, hero.x, hero.centerY());
        hit.tone = GameEvent.Tone.HERO_HIT;
        events.add(hit);
    }

    /** 마왕이 처음 쓰러지면: 무적으로 변신하며 체력이 다시 차고 2페이즈가 된다. */
    public void startPhaseShift(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        m.phase2 = true;
        m.maxHp = Math.round(m.def.hp * mc.finalPhase2HpMultiplier);
        m.hp = 1;
        m.attackMul = mc.finalPhase2AttackMultiplier;
        m.shield = m.shieldMax = 0;
        m.rushLeft = 0;
        m.enter(Monster.State.PHASE_SHIFT, mc.phaseShiftSeconds);
        // 스킬·탄환·장판 목록을 도는 중에 불릴 수 있다 (스킬로 1페이즈를 마무리할 때).
        // 목록을 바로 비우면 ConcurrentModificationException 으로 게임이 꺼지므로, 끝남 표시만 하고 각 update 끝의 removeIf 가 치운다.
        for (Hazard h : hazards) h.dead = true;
        g.blindT = 0;
        hero.stunT = 0;
        for (Projectile p : projectiles) p.dead = true;
        for (HeroShot h : g.shots) h.dead = true;
        speech(m, "이번 용사는 강하군. 나도 본격적으로 해주마!");
        GameEvent e = new GameEvent(GameEvent.Type.BOSS_PHASE2);
        e.text = m.def.name;
        e.x = m.x;
        e.y = m.centerY();
        events.add(e);
        events.add(GameEvent.sound("roar"));
    }

    public void speech(Monster m, String text) {
        GameEvent e = new GameEvent(GameEvent.Type.BOSS_SPEECH);
        e.text = text;
        e.x = m.x;
        e.y = m.y + m.hurtRadius * 3.2f;
        events.add(e);
    }

    /** 연속 베기 한 번 예고: 그때 용사 쪽을 다시 노린다. */
    void comboStep(Monster m) {
        float dx = hero.x - m.x, dy = hero.y - m.y, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l > 0.01f) {
            m.dirX = dx / l;
            m.dirY = dy / l;
        }
        float t = balance.monster.comboTelegraph;
        // 첫 번째는 조금 길게 예고한다
        m.enter(Monster.State.COMBO_TELEGRAPH, m.comboLeft == balance.monster.comboHits ? t * 1.6f : t);
    }

    /** 너무 가까우면 용사 반대쪽 멀리 순간이동한다 (마족병, 구석에 몰린 보스). */
    boolean blinkAway(Monster m) {
        if (!m.boss) m.blinkCd = balance.monster.blinkCooldown;
        float[] spot = farSpot(m);
        if (spot == null) return false;
        events.add(GameEvent.fx(GameEvent.Type.BLINK_FX, m.x, m.centerY()));
        m.x = spot[0];
        m.y = spot[1];
        events.add(GameEvent.fx(GameEvent.Type.BLINK_FX, m.x, m.centerY()));
        events.add(GameEvent.sound("warp"));
        m.attackCd = Math.max(m.attackCd, 0.5f);
        return true;
    }

    /** 방 안에서 용사와 4.5칸 넘게 떨어진 빈자리 (순간이동·도약할 곳). 못 찾으면 null. */
    float[] farSpot(Monster m) {
        Room r = g.fightRoom();
        if (r == null) return null;
        for (int tries = 0; tries < 40; tries++) {
            int tx = r.x0 + 2 + random.nextInt(Math.max(1, r.x1 - r.x0 - 3));
            int ty = r.y0 + 2 + random.nextInt(Math.max(1, r.y1 - r.y0 - 3));
            if (!r.inside(tx, ty)) continue;
            float px = center(tx), py = center(ty);
            float hx = px - hero.x, hy = py - hero.y;
            if (hx * hx + hy * hy < (4.5f * T) * (4.5f * T)) continue;
            if (!Physics.free(map, px, py, m, T)) continue;
            return new float[]{px, py};
        }
        return null;
    }

    /** 뒤로 도약: 용사에게서 먼 빈자리로 뛰어오른다. 착지할 자리에 충격파 예고가 먼저 뜬다. */
    boolean leapAway(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        float[] spot = farSpot(m);
        if (spot == null) return false;
        m.leapX0 = m.x;
        m.leapY0 = m.y;
        m.leapX1 = spot[0];
        m.leapY1 = spot[1];
        circleHazard("leap", spot[0], spot[1], mc.leapLandRadiusTiles * T, mc.leapTime, m.attack(), mc.leapLandMultiplier);
        m.enter(Monster.State.LEAP, mc.leapTime);
        m.attackCd = Math.max(m.attackCd, 0.6f);
        events.add(GameEvent.sound("dash"));
        return true;
    }
}
