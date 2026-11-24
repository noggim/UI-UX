package com.yongsa.model;

import com.yongsa.combat.Attack;
import com.yongsa.combat.Facing;
import com.yongsa.enemy.Hazard;
import com.yongsa.combat.HeroActor;
import com.yongsa.combat.HeroShot;
import com.yongsa.enemy.BossBrain;
import com.yongsa.enemy.Monster;
import com.yongsa.enemy.MonsterBrain;
import com.yongsa.combat.Physics;
import com.yongsa.enemy.Projectile;
import com.yongsa.config.Balance;
import com.yongsa.config.Balance.DungeonDef;
import com.yongsa.map.Corridor;
import com.yongsa.map.Door;
import com.yongsa.map.Prop;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import com.yongsa.map.VeinGenerator;
import com.yongsa.map.WorldMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;

/**
 * 게임 한 판을 진행한다. 화면은 이동·공격·상호작용 요청(Controls)만 보내고,
 * GameSession 이 규칙을 불러 결과를 이벤트(GameEvent)로 돌려준다.
 * 그리기 코드는 없어서 화면 없이 테스트할 수 있다 (NFR-05).
 */
public class GameSession {
    public final Balance balance;
    public final Rules rules;
    public final EnhanceService enhance;
    public final ForgeService forge;
    public final AbsorbService absorb;
    public final GameState state;
    public final WorldMap map;
    public final HeroActor hero = new HeroActor();
    public final List<Monster> monsters = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    /** 용사의 검 스킬이 만든 검기·낙뢰. */
    public final List<HeroShot> shots = new ArrayList<>();
    /** 바닥에 예고된 보스 공격 (낙석, 낙뢰, 촉수, 창, 피웅덩이). */
    public final List<Hazard> hazards = new ArrayList<>();
    public final Random random;
    /** 몬스터 AI (enemy/MonsterBrain). */
    public final MonsterBrain mobs;
    /** 보스 패턴 (enemy/BossBrain). */
    public final BossBrain bosses;

    private final int T;
    private final List<GameEvent> events = new ArrayList<>();

    /** 마지막으로 들어간 방 (캠프 문 밖에 있으면 직전 방). */
    private Room currentRoom;
    /** 용사가 지금 서 있는 방, 캠프 문 밖이면 null. */
    private Room heroRoom;
    private Room fightRoom;
    private Door entryDoor;
    private float entryX, entryY, outsideX, outsideY;
    /** 문을 지나 막 옮겨 왔으면 잠깐 다시 넘어가지 않는다. */
    private float doorCd;
    /** 화면이 어두워지는 동안 기다리는 문 (deferDoors 일 때). */
    private Door pendingDoor;
    /** true 면 문을 지날 때 바로 옮기지 않고 DOOR_ENTER 를 보낸 뒤 passPendingDoor 를 기다린다 (화면 연출용). */
    public boolean deferDoors;
    private float hpAcc;
    private boolean unsealOffered;
    private Attack basicAttack;
    private float lockedGateMsgCd;
    private boolean justDied;

    /** 개발용: 맞아도 체력이 줄지 않는다. */
    public boolean devGodMode;
    /** 개발용: 다시 싸우려고 되살린 보스의 던전 (저장하지 않는다). 흡수한 힘과 열린 길은 그대로다. */
    private final Set<String> revived = new HashSet<>();

    /** HUD 에 보여줄 상호작용 안내 (없으면 null). */
    public String prompt;
    public float promptX, promptY;
    private Prop targetProp;
    private Vein targetVein;
    private boolean targetPortal;
    private boolean targetChest;
    private Balance.Villager targetVillager;
    /** 다섯 번 되돌아선 뒤 체념하고 나가는 중 (마을에 닿으면 독백). */
    private boolean giveUpPending;
    /** 침대에서 자는 중 (선우 버전 잠 모션): 0 이면 깨어 있다. 중간에 푹 쉬고(useBed) 끝에 일어난다. */
    private float sleepTime;
    private boolean slept;
    private Prop sleepBed;
    public static final float SLEEP_TIME = 3.2f, SLEEP_REST_AT = 1.5f;
    /** 실명 (해골 사제): 남은 시간. 그리는 쪽에서 화면을 어둡게 한다. */
    public float blindT;
    /** 마을 사람마다 몇 번째 대사까지 했는지. */
    private final java.util.Map<String, Integer> talkCount = new java.util.HashMap<>();

    public GameSession(Balance balance, WorldMap map, GameState state, long seed) {
        this.balance = balance;
        this.rules = new Rules(balance);
        this.enhance = new EnhanceService(balance);
        this.forge = new ForgeService(balance);
        this.absorb = new AbsorbService(balance);
        this.state = state;
        this.map = map;
        map.returnDoorOpen = this::hasPortal;
        this.random = new Random(seed);
        this.T = balance.tileSize;
        this.mobs = new MonsterBrain(this, events);
        this.bosses = new BossBrain(this, events, mobs);

        map.updateGates(state);
        absorb.migrateImprints(state);
        enhance.clampMastery(state); // 숙련도가 없던 예전 저장은 0에서 시작한다
        if (state.mapVersion != WorldMap.VERSION) {
            // 방 배치가 바뀐 예전 저장: 광맥 자리와 이어하기 위치는 새 맵에 맞지 않는다
            for (RoomRecord rec : state.rooms.values()) rec.veins = null;
            state.potionDrops.clear(); // 바닥 보상도 이전 맵의 월드 좌표다.
            state.resumeX = state.resumeY = 0;
            state.mapVersion = WorldMap.VERSION;
        }
        applyVeinOverlay();
        if (state.resumeX == 0 && state.resumeY == 0) {
            placeAtCampSpawn();
        } else {
            hero.x = state.resumeX;
            hero.y = state.resumeY;
            if (!Physics.free(map, hero.x, hero.y, hero, T)) placeAtCampSpawn();
        }
        if (state.hp <= 0) state.hp = rules.maxHp(state);
        heroRoom = map.roomAt(tileX(hero.x), tileY(hero.y));
        Corridor cor = map.corridorAtWorld(hero.x, hero.y);
        currentRoom = heroRoom != null ? heroRoom : cor != null ? cor.link.a : map.room("CAMP");
        if (heroRoom != null) {
            Door d = nearestDoor(heroRoom);
            setEntry(d);
        }
    }

    // ───────────────────────── 위치 도우미

    private int tileX(float px) {
        return (int) Math.floor(px / T);
    }

    private int tileY(float py) {
        return (int) Math.floor(py / T);
    }

    private float center(int tile) {
        return tile * T + T / 2f;
    }

    public void placeAtCampSpawn() {
        Room camp = map.room("CAMP");
        hero.x = camp.scene == null ? center((int) camp.centerTileX()) : camp.spawnX(T);
        hero.y = camp.scene == null ? center((int) camp.centerTileY() - 2) : camp.spawnY(T);
        hero.facing = Facing.DOWN;
    }

    /** 이번 프레임에 용사가 쓰러졌으면 true (몬스터 목록이 비워졌다). */
    public boolean heroJustDied() {
        return justDied;
    }

    /** 지금 싸우는 방 (없으면 null). */
    public Room fightRoom() {
        return fightRoom;
    }

    /** 용사가 이 눈알(마왕 2페이즈)을 바라보고 있는가. 판정은 enemy/BossBrain. */
    public boolean lookingAt(Hazard h) {
        return bosses.lookingAt(h);
    }

    public Room heroRoom() {
        return heroRoom;
    }

    public Room currentRoom() {
        return currentRoom;
    }

    public boolean fighting() {
        return fightRoom != null;
    }

    public boolean inCamp() {
        return heroRoom != null && heroRoom.type == RoomType.CAMP;
    }

    public boolean inDungeon() {
        return currentRoom != null && currentRoom.dungeonId != null && !inCamp();
    }

    public Monster activeBoss() {
        for (Monster m : monsters) if (m.boss && !m.dead) return m;
        return null;
    }

    public List<GameEvent> pollEvents() {
        List<GameEvent> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    public String nextTask() {
        return NextTask.describe(state, balance, state.room("L1").visited, bagKey);
    }

    // ───────────────────────── 한 프레임

    public void update(float dt, Controls c) {
        state.stats.playSeconds += dt;
        lockedGateMsgCd -= dt;
        justDied = false;
        if (pendingDoor != null) return; // 화면이 어두워지는 동안은 멈춰 있다
        doorCd -= dt;
        if (sleepTime > 0) {
            // 자는 동안은 조작을 받지 않는다
            sleepTime += dt;
            if (!slept && sleepTime >= SLEEP_REST_AT) {
                slept = true;
                useBed();
            }
            if (sleepTime >= SLEEP_TIME) {
                sleepTime = 0;
                hero.facing = Facing.DOWN;
                hero.aimX = 0;
                hero.aimY = -1;
            }
            c = new Controls();
        }
        noteOpenedSkills();
        updateHero(dt, c);
        if (checkDoors()) return;
        updateRooms();
        mobs.updateMonsters(dt);
        if (!justDied) updateShots(dt);
        if (!justDied) mobs.updateProjectiles(dt);
        if (!justDied) bosses.updateHazards(dt);
        if (!justDied) resolveFight();
        if (!justDied) updatePotionDrops(dt);
        if (inCamp() || inVillage()) {
            hpAcc += rules.maxHp(state) * balance.hero.campRegenFractionPerSec * dt;
            int add = (int) hpAcc;
            if (add > 0) {
                hpAcc -= add;
                state.hp = Math.min(rules.maxHp(state), state.hp + add);
            }
            if (!unsealOffered && absorb.canUnseal(state)) {
                unsealOffered = true;
                events.add(GameEvent.toast("세 보스의 힘이 모였다. 캠프 바닥의 문양에서 성검의 봉인을 풀 수 있다"));
            }
        }
        if (sleepTime > 0) {
            prompt = null;
            return;
        }
        findInteraction();
        if (c.interact) interact();
    }

    private void updateHero(float dt, Controls c) {
        hero.attackCd -= dt;
        for (int i = 0; i < hero.skillCd.length; i++) hero.skillCd[i] -= dt;
        hero.rollCd -= dt;
        hero.mineCd -= dt;
        hero.invuln -= dt;
        hero.flash -= dt;
        hero.swingT -= dt;
        hero.mineSwingT -= dt;
        hero.slowT -= dt;
        hero.stunT -= dt;
        blindT -= dt;
        bosses.tick(dt);

        if (hero.kbT > 0) {
            Physics.move(map, hero, hero.kbVX * dt, hero.kbVY * dt, T);
            hero.kbT -= dt;
        }

        if (hero.rolling()) {
            // Integrate the deceleration curve over this frame, including the partial final frame.
            float total = hero.rollTotal;
            float left = Math.max(0, hero.rollT - dt);
            float a = Math.max(0, 1f - hero.rollT / total), z = 1f - left / total;
            float distance = balance.attack.rollDistanceTiles * T * ((2*z-z*z) - (2*a-a*a));
            Physics.move(map, hero, hero.rollX * distance, hero.rollY * distance, T);
            hero.rollT = left;
            hero.moving = false;
            return;
        }

        if (hero.stunT > 0) {
            hero.moving = false;
            return;
        }
        float mx = c.moveX, my = c.moveY;
        float len = (float) Math.sqrt(mx * mx + my * my);
        hero.moving = len > 0.01f;
        if (hero.moving) { mx /= len; my /= len; }
        if (c.roll && hero.rollCd <= 0) {
            roll(mx, my);
            return;
        }
        if (hero.moving) {
            hero.aimX = mx;
            hero.aimY = my;
            hero.facing = Facing.of(mx, my);
            float speed = balance.hero.moveSpeedTiles * T * (hero.slowT > 0 ? 1f - balance.monster.slowRatio : 1f);
            float oldX = hero.x, oldY = hero.y;
            Physics.move(map, hero, mx * speed * dt, my * speed * dt, T);
            float dx = hero.x - oldX, dy = hero.y - oldY;
            float travelled = (float) Math.sqrt(dx * dx + dy * dy);
            hero.moving = travelled > .001f;
            // A wall or slow pool must not leave the legs running at full speed.
            hero.walkTime += travelled / (balance.hero.moveSpeedTiles * T);
        }
        autoFace();

        if (c.skill >= 0) useSkill(c.skill);
        else if (c.attack && hero.attackCd <= 0) basic();
    }

    /** 가까운 몬스터를 자동으로 바라본다: 걸어가도 몸과 공격은 적을 향한다 (팀 분담: 최해성). */
    private void autoFace() {
        float range = balance.attack.autoFaceTiles * T;
        if (range <= 0) return;
        // 마왕의 눈알이 뜨려는 동안은 걸어가는 쪽을 본다 (스스로 눈을 돌릴 수 있게)
        for (Hazard h : hazards) if (h.kind == Hazard.Kind.EYE && !h.dead) return;
        Monster best = null;
        float bestD = range * range;
        for (Monster m : monsters) {
            if (m.dead || m.state == Monster.State.SPAWN) continue;
            float dx = m.x - hero.x, dy = m.centerY() - hero.centerY();
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        if (best == null) return;
        float dx = best.x - hero.x, dy = best.centerY() - hero.centerY(), l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 0.01f) return;
        hero.aimX = dx / l;
        hero.aimY = dy / l;
        hero.facing = Facing.of(hero.aimX, hero.aimY);
    }

    private void basic() {
        hero.attackCd = balance.attack.basicCooldown * (rules.hasAbility(state, "skyfall") ? 0.8f : 1f);
        hero.swingKind = 0;
        hero.swingT = 0.18f;
        basicAttack = new Attack(Attack.Kind.BASIC, balance.attack.basicMultiplier);
        float range = basicRange();
        float half = (float) Math.toRadians(balance.attack.basicArcDegrees / 2f);
        float hx = hero.x, hy = hero.centerY();
        for (Monster m : monsters) {
            if (m.dead) continue;
            float dx = m.x - hx, dy = m.centerY() - hy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist - m.hurtRadius > range) continue;
            if (dist > m.hurtRadius + 6) {
                float cos = (dx * hero.aimX + dy * hero.aimY) / dist;
                if (Math.acos(Math.max(-1, Math.min(1, cos))) > half) continue;
            }
            if (basicAttack.tryHit(m)) damageMonster(m, basicAttack.multiplier);
        }
        events.add(GameEvent.sound("swing"));
    }

    // ───────────────────────── 검 스킬 (L, U, I, O)

    private float skillMsgCd;

    /** 스킬 칸 키 이름 (화면 안내용). 사용자가 조작 키를 바꾸면 화면이 다시 알려 준다. */
    public final String[] skillKeys = {"L", "U", "I", "O"};
    /** 상호작용 키 이름 (안내 문구 "E  대장간" 의 앞부분). */
    public String interactKey = "E";
    /** 다음 할 일 안내에 쓸 가방 키 이름 (GameScreen 이 조작 키에 맞춰 바꾼다). */
    public String bagKey = "B";

    /**
     * 스킬 칸 하나를 쓴다. 칸은 검 스킬이 열린 차례대로 채워진다 (+10 강화·각성 또는 흡수).
     * 흡수해서 익힌 스킬은 어떤 검을 들어도 쓰고, 아니면 그 검을 들고 있어야 쓴다.
     */
    private void useSkill(int index) {
        if (index < 0 || index >= Rules.SKILL_SLOTS) return;
        Rules.SkillSlot slot = rules.skillSlots(state)[index];
        if (slot == null || !slot.usable) {
            if (skillMsgCd <= 0) {
                skillMsgCd = 1.5f;
                events.add(GameEvent.toast(slot == null ? (index == Rules.HOLY_SLOT ? "성검의 봉인을 풀면 열리는 칸이다"
                        : "검을 +" + balance.enhance.skillUnlockLevel + " 까지 강화하면 스킬 칸이 열린다")
                        : index == Rules.HOLY_SLOT ? "「" + slot.def.name + "」은(는) 깨어난 성검을 들어야 쓴다"
                        : "「" + slot.def.name + "」은(는) " + balance.weapon(slot.weaponId).name + "을 들거나, 대장간에서 흡수하면 늘 쓴다"));
            }
            return;
        }
        Balance.SkillDef sk = slot.def;
        if (hero.skillCd[index] > 0) return;
        hero.skillCd[index] = sk.cooldown;
        hero.swingKind = 1;
        hero.swingT = 0.25f;
        boolean aw = slot.awakened;
        int n = aw ? sk.awakenedCount : sk.count;
        float mult = sk.multiplier * (aw ? sk.awakenedMultiplier : 1f);
        if ("strike".equals(sk.kind)) {
            List<Monster> targets = new ArrayList<>();
            float range = sk.rangeTiles * T;
            for (Monster m : monsters) {
                if (m.dead || m.state == Monster.State.SPAWN) continue;
                if (Math.hypot(m.x - hero.x, m.y - hero.y) <= range) targets.add(m);
            }
            targets.sort((a, b) -> Float.compare(dist2(a), dist2(b)));
            for (int i = 0; i < n; i++) {
                float tx, ty;
                if (i < targets.size()) {
                    tx = targets.get(i).x;
                    ty = targets.get(i).y;
                } else if (i == 0) {
                    tx = hero.x + hero.aimX * 3 * T; // 적이 없으면 바라보는 쪽 앞에
                    ty = hero.y + hero.aimY * 3 * T;
                } else break;
                HeroShot h = new HeroShot();
                h.kind = sk.kind;
                h.x = tx;
                h.y = ty;
                h.radius = sk.widthTiles * T;
                h.delay = 0.3f + i * 0.08f;
                h.life = h.delay + 0.1f;
                h.multiplier = mult;
                shots.add(h);
            }
        } else {
            float base = (float) Math.atan2(hero.aimY, hero.aimX);
            float spread = (float) Math.toRadians(sk.spreadDegrees);
            for (int i = 0; i < n; i++) {
                float a = base + spread * (i - (n - 1) / 2f);
                HeroShot h = new HeroShot();
                h.kind = sk.kind;
                h.x = hero.x + (float) Math.cos(a) * T * 0.6f;
                h.y = hero.centerY() + (float) Math.sin(a) * T * 0.6f;
                float speed = sk.speedTiles * T;
                h.vx = (float) Math.cos(a) * speed;
                h.vy = (float) Math.sin(a) * speed;
                h.radius = sk.widthTiles * T / 2f;
                h.life = sk.rangeTiles / sk.speedTiles;
                h.multiplier = mult;
                h.stun = sk.stunSeconds;
                h.heal = sk.healRatio;
                shots.add(h);
            }
        }
        GameEvent e = new GameEvent(GameEvent.Type.SKILL_CAST);
        e.text = sk.kind;
        e.x = hero.x;
        e.y = hero.centerY();
        e.dirX = hero.aimX;
        e.dirY = hero.aimY;
        events.add(e);
        events.add(GameEvent.sound("strike".equals(sk.kind) ? "cast" : "slam"));
    }

    /** 새로 열린 검 스킬을 빈 칸에 넣고 어느 키인지 알려 준다. */
    private void noteOpenedSkills() {
        int before = state.skillOrder.size();
        if (!rules.noteOpenedSkills(state)) return;
        for (int i = before; i < state.skillOrder.size(); i++) {
            Balance.SkillDef sk = balance.weapon(state.skillOrder.get(i)).skill;
            events.add(GameEvent.toast("새 스킬 「" + sk.name + "」 열림: " + skillKeys[i] + " 키"));
        }
        events.add(GameEvent.sound("awaken"));
    }

    private float dist2(Monster m) {
        float dx = m.x - hero.x, dy = m.y - hero.y;
        return dx * dx + dy * dy;
    }

    private void updateShots(float dt) {
        skillMsgCd -= dt;
        for (HeroShot h : shots) {
            if (h.dead) continue;
            h.age += dt;
            h.life -= dt;
            if (h.strike()) {
                h.delay -= dt;
                if (h.delay > 0) continue;
                for (Monster m : monsters) {
                    if (m.dead || m.state == Monster.State.SPAWN) continue;
                    if (Math.hypot(m.x - h.x, m.y - h.y) <= h.radius + m.hurtRadius) damageMonster(m, h.multiplier);
                }
                GameEvent fx = GameEvent.fx(GameEvent.Type.SKILL_HIT, h.x, h.y);
                fx.text = h.kind;
                fx.size = h.radius;
                events.add(fx);
                events.add(GameEvent.sound("cast"));
                h.dead = true;
                continue;
            }
            h.x += h.vx * dt;
            h.y += h.vy * dt;
            if (h.life <= 0 || map.solid(tileX(h.x), tileY(h.y - 6))) {
                h.dead = true;
                continue;
            }
            for (Monster m : monsters) {
                if (m.dead || m.state == Monster.State.SPAWN || h.hit.contains(m)) continue;
                float dx = m.x - h.x, dy = m.centerY() - h.y, r = h.radius + m.hurtRadius;
                if (dx * dx + dy * dy > r * r) continue;
                h.hit.add(m);
                damageMonster(m, h.multiplier);
                if (h.stun > 0 && !m.dead && !m.boss) m.enter(Monster.State.STUNNED, h.stun);
                if (h.heal > 0) {
                    int add = Math.max(1, Math.round(rules.maxHp(state) * h.heal));
                    state.hp = Math.min(rules.maxHp(state), state.hp + add);
                    events.add(GameEvent.floatText(hero.x, hero.y + 40, "+" + add, GameEvent.Tone.HEAL));
                }
                GameEvent fx = GameEvent.fx(GameEvent.Type.SKILL_HIT, m.x, m.centerY());
                fx.text = h.kind;
                fx.size = h.radius;
                events.add(fx);
            }
        }
        shots.removeIf(h -> h.dead);
    }

    public float basicRange() {
        float r = balance.attack.basicRangeTiles * T;
        if (rules.hasAbility(state, "blackiron")) r *= 1.3f;
        return r;
    }

    /** 구르기: 움직이는 방향(없으면 바라보는 방향)으로 굴러서 공격을 피한다. */
    private void roll(float mx, float my) {
        float len = (float) Math.sqrt(mx * mx + my * my);
        if (len > 0.01f) {
            hero.rollX = mx;
            hero.rollY = my;
            hero.aimX = mx;
            hero.aimY = my;
        } else {
            hero.rollX = hero.aimX;
            hero.rollY = hero.aimY;
        }
        hero.rollCd = balance.attack.rollTime + balance.attack.rollCooldown;
        hero.rollFacing = Facing.of(hero.rollX, hero.rollY);
        hero.facing = hero.rollFacing;
        hero.moving = false;
        hero.rollT = hero.rollTotal = balance.attack.rollTime;
        hero.invuln = Math.max(hero.invuln, balance.attack.rollInvulnTime);
        hero.swingT = 0;
        hero.mineSwingT = 0;
        events.add(GameEvent.sound("dash"));
    }

    private int roll(int base, float mult) {
        float spread = balance.attack.damageSpreadMin
                + random.nextFloat() * (balance.attack.damageSpreadMax - balance.attack.damageSpreadMin);
        return Math.max(1, Math.round(base * mult * spread));
    }

    private void damageMonster(Monster m, float mult) {
        if (m.invulnerable()) return;
        if (m.finalBoss && !Rules.HOLY_AWAKENED.equals(state.equipped().defId)) {
            if (m.immuneMsgCd <= 0) {
                m.immuneMsgCd = 1.2f;
                events.add(GameEvent.floatText(m.x, m.y + m.hurtRadius * 2.2f,
                        "이 검으로는 상처를 낼 수 없다", GameEvent.Tone.BLOCKED));
                events.add(GameEvent.sound("block"));
            }
            return;
        }
        int dmg = roll(rules.attack(state), mult);
        boolean guarded = false;
        if (m.guardActive()) {
            // 해골병 방패: 정면에서 오는 공격은 크게 줄어든다. 뒤나 옆은 그대로
            float gx = hero.x - m.x, gy = hero.y - m.y, gl = (float) Math.sqrt(gx * gx + gy * gy);
            if (gl < 0.01f || (gx * m.dirX + gy * m.dirY) / gl > 0.3f) {
                dmg = Math.max(1, Math.round(dmg * (1f - balance.monster.guardReduction)));
                guarded = true;
            }
        }
        m.flash = 0.15f;
        if (m.shield > 0) {
            // 방패가 남아 있으면 체력 대신 방패가 닳는다
            m.shield -= dmg;
            events.add(GameEvent.floatText(m.x, m.y + m.hurtRadius * 2f, String.valueOf(dmg), GameEvent.Tone.SHIELD));
            events.add(GameEvent.sound("block"));
            if (m.shield <= 0) {
                m.shield = 0;
                m.shieldMax = 0;
                m.enter(Monster.State.STUNNED, balance.monster.shieldStagger);
                events.add(GameEvent.toast(m.def.name + "의 방패가 깨졌다! 지금이 기회다"));
                events.add(GameEvent.sound("slam"));
                GameEvent fx = GameEvent.fx(GameEvent.Type.SHIELD_BREAK, m.x, m.centerY());
                fx.size = m.hurtRadius * 2.4f;
                events.add(fx);
            }
            return;
        }
        m.hp -= dmg;
        // 공격을 준비하거나 돌진하는 중에는 밀리지 않는다: 연타로 공격을 끊을 수 없다
        if (!m.boss && !m.committed()) m.knockback(m.x - hero.x, m.y - hero.y, balance.monster.knockbackTiles * T);
        if (m.boss && !m.finalBoss && !m.enraged && m.hp > 0 && m.hp <= m.maxHp * balance.monster.phase2HpRatio) {
            m.enraged = true;
            m.patternCd = Math.min(m.patternCd, 0.6f);
            GameEvent e = new GameEvent(GameEvent.Type.BOSS_ENRAGED);
            e.text = m.def.name;
            e.x = m.x;
            e.y = m.centerY();
            events.add(e);
            events.add(GameEvent.sound("roar"));
        }
        if (m.finalBoss && m.phase2 && !m.lastStand && m.hp > 0 && m.hp <= m.maxHp * balance.monster.lastStandHpRatio) {
            // 최후의 발악: 화면이 어두워지고 패턴 간격이 줄어든다
            m.lastStand = true;
            m.patternCd = Math.min(m.patternCd, 0.6f);
            bosses.speech(m, "아직... 끝나지 않았다!");
            events.add(GameEvent.toast("마왕이 최후의 발악을 한다! 공격이 더 빨라진다"));
            events.add(GameEvent.sound("roar"));
        }
        events.add(GameEvent.floatText(m.x, m.y + m.hurtRadius * 2f, String.valueOf(dmg), guarded ? GameEvent.Tone.SHIELD : GameEvent.Tone.MONSTER_HIT));
        events.add(GameEvent.sound(guarded ? "block" : "hit"));
        GameEvent fx = GameEvent.fx(GameEvent.Type.HIT_FX, m.x, m.centerY());
        fx.tone = GameEvent.Tone.MONSTER_HIT;
        float ddx = m.x - hero.x, ddy = m.centerY() - hero.centerY(), dl = (float) Math.sqrt(ddx * ddx + ddy * ddy);
        fx.dirX = dl > 0.01f ? ddx / dl : hero.aimX;
        fx.dirY = dl > 0.01f ? ddy / dl : hero.aimY;
        fx.boss = m.boss;
        events.add(fx);
        if (m.hp <= 0 && m.finalBoss && !m.phase2) {
            bosses.startPhaseShift(m);
            return;
        }
        if (m.hp <= 0 && !m.boss && m.def.revive && !m.revived) {
            // 해골병: 처음 쓰러지면 뼈 무더기가 됐다가 다시 일어난다. 그 사이 때리면 완전히 부서진다
            m.revived = true;
            m.hp = 0;
            m.guarding = false;
            m.enter(Monster.State.DOWNED, balance.monster.reviveSeconds);
            events.add(GameEvent.floatText(m.x, m.y + m.hurtRadius * 2.4f, "뼈가 꿈틀거린다", GameEvent.Tone.INFO));
            return;
        }
        if (m.hp <= 0) {
            m.hp = 0;
            m.dead = true;
            if (m.finalBoss) {
                bosses.speech(m, "이런 풋내기에게 당하다니!");
                // 마왕이 쓰러지면 불러낸 부하도 함께 무너진다
                for (Monster o : monsters) if (!o.boss) o.dead = true;
            }
            state.stats.monstersKilled++;
            GameEvent died = GameEvent.fx(GameEvent.Type.MONSTER_DIED, m.x, m.y);
            died.sprite = m.def.sprite;
            died.size = m.finalBoss ? (m.phase2 ? 2.3f : 2.0f) : m.boss ? 1.7f : 1f;
            died.boss = m.boss;
            died.flip = m.dirX < -0.2f;
            died.dirX = m.dirX;
            died.phase2 = m.phase2;
            events.add(died);
            if (rules.hasAbility(state, "bloodmoon")) {
                int heal = Math.max(1, Math.round(rules.maxHp(state) * 0.05f));
                state.hp = Math.min(rules.maxHp(state), state.hp + heal);
                events.add(GameEvent.floatText(hero.x, hero.y + 40, "+" + heal, GameEvent.Tone.HEAL));
            }
        }
    }

    /** 몬스터·보스가 용사를 칠 때 부른다 (무적·넉백·죽음 처리). */
    public void hitHero(int base, float mult, float fromX, float fromY) {
        if (hero.invuln > 0) return;
        if (devGodMode) {
            hero.invuln = balance.hero.invulnAfterHit;
            hero.flash = balance.hero.invulnAfterHit;
            events.add(GameEvent.floatText(hero.x, hero.y + 40, "0", GameEvent.Tone.BLOCKED));
            return;
        }
        int dmg = roll(base, mult);
        state.hp -= dmg;
        hero.invuln = balance.hero.invulnAfterHit;
        hero.flash = balance.hero.invulnAfterHit;
        hero.knockback(hero.x - fromX, hero.y - fromY, balance.hero.knockbackTiles * T);
        events.add(GameEvent.floatText(hero.x, hero.y + 40, String.valueOf(dmg), GameEvent.Tone.HERO_HIT));
        events.add(GameEvent.sound("hurt"));
        GameEvent fx = GameEvent.fx(GameEvent.Type.HIT_FX, hero.x, hero.centerY());
        fx.tone = GameEvent.Tone.HERO_HIT;
        events.add(fx);
        if (state.hp <= 0) die();
    }

    /** 용사가 쓰러진다: 캠프로 돌아가고 싸우던 방을 처음 상태로. */
    public void die() {
        state.hp = 0;
        justDied = true;
        state.stats.deaths++;
        String last = null;
        if (fightRoom != null && fightRoom.type == RoomType.FINAL) {
            Monster b = activeBoss();
            last = b != null && b.phase2 ? "꽤나 즐거운 여흥이었다." : "한심하긴.";
        }
        hazards.clear();
        blindT = 0;
        hero.stunT = 0;
        hero.slowT = 0;
        if (fightRoom != null) {
            fightRoom.setFightLock(false); // 싸우던 방만 처음 상태로 (A8)
            fightRoom = null;
        }
        monsters.clear();
        projectiles.clear();
        shots.clear();
        placeAtCampSpawn();
        heroRoom = currentRoom = map.room("CAMP");
        hero.rollT = 0;
        hero.kbT = 0;
        state.hp = rules.maxHp(state);
        GameEvent died = new GameEvent(GameEvent.Type.HERO_DIED);
        died.text = last;
        events.add(died);
    }

    // ───────────────────────── 방

    private void updateRooms() {
        com.yongsa.map.Corridor walking = map.corridorAtWorld(hero.x, hero.y);
        if (walking != null && !state.seenCorridors.contains(walking.key())) state.seenCorridors.add(walking.key());
        Room r = map.roomAt(tileX(hero.x), tileY(hero.y));
        if (r != heroRoom) {
            Room prevDungeonRoom = currentRoom;
            heroRoom = r;
            if (r != null) setEntry(nearestDoor(r));
            if (r != null && r != currentRoom) {
                currentRoom = r;
                state.room(r.id).visited = true;
                if (r.type == RoomType.ORE) ensureVeins(r);
                if (r.dungeonId != null && r.type != RoomType.FINAL
                        && (prevDungeonRoom == null || prevDungeonRoom.dungeonId == null)) {
                    DungeonDef d = balance.dungeon(r.dungeonId);
                    GameEvent e = new GameEvent(GameEvent.Type.ENTERED_DUNGEON);
                    e.dungeonId = d.id;
                    e.text = NextTask.dungeonWord(d.id) + " 던전 · " + d.name;
                    events.add(e);
                }
                if (r.type == RoomType.VILLAGE) {
                    if (giveUpPending) heroSpeech(balance.village.giveUpLine);
                    else if (!villagersFree()) heroSpeech(balance.village.arriveTrappedLine);
                    giveUpPending = false;
                }
                if (r.type == RoomType.BOSS && !state.bossDefeated(r.dungeonId)) {
                    events.add(GameEvent.toast("보스방이다. 권장 무기: " + balance.dungeon(r.dungeonId).recommended));
                }
            }
        }
        if (fightRoom == null && heroRoom != null && heroRoom.type.hasFight()
                && !state.room(heroRoom.id).cleared && !hasPortal(heroRoom) && steppedIn(heroRoom)) {
            startFight(heroRoom);
        }
    }

    /** Cross the entry plane inward, then take one step beyond the safe arrival point. */
    private boolean steppedIn(Room r) {
        int tx = tileX(hero.x), ty = tileY(hero.y);
        if (r.scene == null) return r.insideWithMargin(tx, ty, 1);
        if (!r.inside(tx, ty)) return false;
        if (entryDoor != null && entryDoor.room == r) {
            // A radial distance also grows when walking sideways along a wide doorway.
            // Only inward progress counts, including for left/right and upper entrances.
            float arrivalDepth = -entryDoor.along(entryDoor.arriveX, entryDoor.arriveY);
            float clearance = Math.max(2.6f*T, arrivalDepth + .5f*T);
            if (entryDoor.along(hero.x, hero.y) > -clearance) return false;
        }
        for (Door d : r.doors) {
            if (d.dead()) continue;
            // Do not close another nearby exit through the hero's footprint either.
            if (d.blocks(hero.x, hero.y, Math.max(hero.hw, hero.hh) + .5f*T)) return false;
        }
        return true;
    }

    private void setEntry(Door d) {
        entryDoor = d;
        if (d == null) {
            entryX = outsideX = hero.x;
            entryY = outsideY = hero.y;
            return;
        }
        entryX = d.arriveX;
        entryY = d.arriveY;
        Door p = d.partner();
        if (d.room.scene != null && p != null) {
            outsideX = p.arriveX; // 그림 방의 '문 밖'은 이어진 방의 문 앞이다
            outsideY = p.arriveY;
        } else {
            outsideX = center(d.outsideX);
            outsideY = center(d.outsideY);
        }
    }

    /**
     * 통로가 없는 이음(심연 계단)의 열린 문을 지나가면 이어진 방의 짝 문 앞으로 옮긴다. 통로가 있으면 걸어서 간다.
     * deferDoors 면 DOOR_ENTER 를 보내고 화면이 어두워진 뒤 passPendingDoor 로 옮긴다. 옮겼으면(또는 기다리면) true.
     */
    private boolean checkDoors() {
        if (fightRoom != null || doorCd > 0 || pendingDoor != null) return false;
        // Developer warps and door arrivals can change coordinates before updateRooms runs.
        // Never trigger an old room's unbounded exit plane from a different room.
        Room r = map.roomAt(tileX(hero.x), tileY(hero.y));
        if (r == null) return false;
        for (Door d : r.doors) {
            if (d.isClosed() || !d.triggers(hero.x, hero.y)) continue;
            if (d.partner().room.type == RoomType.VILLAGE && refuseVillageExit(d)) return false;
            if (d.link.corridor != null) continue; // 통로로 걸어서 간다
            if (deferDoors) {
                pendingDoor = d;
                GameEvent e = new GameEvent(GameEvent.Type.DOOR_ENTER);
                Room to = d.partner().room;
                e.text = to.dungeonId == null ? null : to.id;
                events.add(e);
                return true;
            }
            passDoor(d);
            return false;
        }
        return false;
    }

    /** 기다리던 문을 지나간다 (화면이 다 어두워졌을 때 부른다). */
    public void passPendingDoor() {
        Door d = pendingDoor;
        pendingDoor = null;
        if (d != null) passDoor(d);
    }

    public boolean doorPending() {
        return pendingDoor != null;
    }

    /** 문 d 를 지나 이어진 방의 짝 문 앞에 선다. */
    public void passDoor(Door d) {
        Door to = d.partner();
        if (to == null) return;
        hero.x = to.arriveX;
        hero.y = to.arriveY;
        hero.aimX = -to.dirX;
        hero.aimY = -to.dirY;
        hero.facing = Facing.of(hero.aimX, hero.aimY);
        hero.rollT = 0;
        hero.kbT = 0;
        hero.slowT = 0;
        hazards.clear();
        blindT = 0;
        hero.stunT = 0;
        projectiles.clear();
        shots.clear();
        doorCd = 0.35f;
    }

    private Door nearestDoor(Room r) {
        Door best = null;
        float bestD = Float.MAX_VALUE;
        for (Door d : r.doors) {
            if (d.dead()) continue;
            float dx = d.arriveX - hero.x, dy = d.arriveY - hero.y;
            float dd = dx * dx + dy * dy;
            if (dd < bestD) {
                bestD = dd;
                best = d;
            }
        }
        return best;
    }

    /** 이 싸움에서 아직 안 나온 웨이브 수. */
    private int wavesLeft;
    private int waveTotal;

    private void startFight(Room r) {
        fightRoom = r;
        waveTotal = r.type == RoomType.MOB ? r.waves : 1;
        wavesLeft = waveTotal - 1;
        r.setFightLock(true);
        monsters.clear();
        projectiles.clear();
        shots.clear();
        hazards.clear();
        blindT = 0;
        hero.stunT = 0;
        DungeonDef d = balance.dungeon(r.dungeonId);
        if (r.type == RoomType.MOB) {
            for (int i = 0; i < d.mobsPerRoom; i++) spawn(r, d.mob, false, false, false);
        } else if (r.type == RoomType.BOSS) {
            spawn(r, d.boss, true, false, true);
            GameEvent e = new GameEvent(GameEvent.Type.BOSS_FIGHT_START);
            e.text = d.boss.name;
            events.add(e);
        } else if (r.type == RoomType.FINAL) {
            spawn(r, balance.finalBoss, true, true, true);
            GameEvent e = new GameEvent(GameEvent.Type.BOSS_FIGHT_START);
            e.text = balance.finalBoss.name;
            e.boss = true;
            events.add(e);
            // 심연에는 문이 없다: 잠기는 대신 마왕이 말을 건다
            for (Monster m : monsters) if (m.finalBoss && !m.dead) bosses.speech(m, "봉인을 풀고 여기까지 내려오다니. 어디 한번 놀아 주마.");
            return;
        }
        roomGateEvent(GameEvent.Type.ROOM_LOCKED, r);
        events.add(GameEvent.sound("gateClose_" + r.dungeonId));
    }

    private void roomGateEvent(GameEvent.Type type, Room room) {
        GameEvent e = new GameEvent(type);
        e.text = room.id;
        e.dungeonId = room.dungeonId;
        e.boss = room.type != RoomType.MOB;
        events.add(e);
    }

    /** 다음 웨이브: 한 무리를 다 잡으면 조금 작은 무리가 다시 나온다 (지용 버전). */
    private void nextWave(Room r) {
        wavesLeft--;
        monsters.removeIf(m -> m.dead);
        DungeonDef d = balance.dungeon(r.dungeonId);
        int n = Math.max(2, d.mobsPerRoom - 1);
        for (int i = 0; i < n; i++) spawn(r, d.mob, false, false, false);
        int wave = waveTotal - wavesLeft;
        GameEvent e = new GameEvent(GameEvent.Type.WAVE_STARTED);
        e.text = "몬스터가 또 몰려온다!  웨이브 " + wave + " / " + waveTotal;
        e.amount = wave;
        events.add(e);
        events.add(GameEvent.sound("cast"));
    }

    private void spawn(Room r, Balance.MonsterDef def, boolean boss, boolean fin, boolean farSide) {
        Monster m = makeMonster(r, def, boss, fin, farSide);
        if (m != null) monsters.add(m);
    }

    /** 방 안 빈 자리에 몬스터를 만든다 (목록에 넣지는 않는다). 처음 잠깐은 마법진만 보이고 움직이지 않는다. */
    public Monster makeMonster(Room r, Balance.MonsterDef def, boolean boss, boolean fin, boolean farSide) {
        Monster m = new Monster(def, boss, fin, T);
        boolean placed = false;
        for (int tries = 0; tries < 200; tries++) {
            int tx, ty;
            if (farSide) {
                tx = (int) r.centerTileX();
                ty = (int) r.centerTileY();
                // 문 반대쪽으로 조금 밀어 둔다
                float dx = r.centerTileX() - tileX(hero.x), dy = r.centerTileY() - tileY(hero.y);
                float l = (float) Math.sqrt(dx * dx + dy * dy);
                if (l > 0) {
                    tx += Math.round(dx / l * 3);
                    ty += Math.round(dy / l * 3);
                }
            } else {
                tx = r.x0 + 2 + random.nextInt(r.x1 - r.x0 - 3);
                ty = r.y0 + 2 + random.nextInt(r.y1 - r.y0 - 3);
            }
            if (!farSide && !r.insideWithMargin(tx, ty, 1)) continue;
            float px = center(tx), py = center(ty);
            float hx = px - hero.x, hy = py - hero.y;
            if (!farSide && hx * hx + hy * hy < (5f * T) * (5f * T)) continue;
            if (!Physics.free(map, px, py, m, T)) continue;
            m.x = px;
            m.y = py;
            placed = true;
            break;
        }
        if (!placed && !farSide) return null;
        if (!placed) {
            m.x = center((int) r.centerTileX());
            m.y = center((int) r.centerTileY());
        }
        if (!boss && r.dungeonId != null && state.bossDefeated(r.dungeonId)) {
            // 보스가 흡수하지 못한 힘이 던전에 퍼져 잡몹이 강해진다
            m.hp = m.maxHp = Math.round(def.hp * balance.monster.clearedMobHpMultiplier);
            m.attackMul = balance.monster.clearedMobAttackMultiplier;
        }
        m.attackCd = 0.6f + random.nextFloat() * 0.6f;
        m.patternCd = 2.5f;
        m.orbitSign = random.nextBoolean() ? 1 : -1;
        m.enter(Monster.State.SPAWN, balance.monster.spawnSeconds * (boss ? 1.4f : 1f) + random.nextFloat() * 0.25f);
        return m;
    }

    private void resolveFight() {
        if (fightRoom == null) return;
        for (Monster m : monsters) if (!m.dead) return;
        if (wavesLeft > 0) {
            nextWave(fightRoom);
            return;
        }
        Room r = fightRoom;
        fightRoom = null;
        r.setFightLock(false);
        monsters.clear();
        projectiles.clear();
        shots.clear();
        hazards.clear();
        blindT = 0;
        hero.stunT = 0;
        state.room(r.id).cleared = true;
        if (r.type != RoomType.FINAL) events.add(GameEvent.sound("gateOpen_" + r.dungeonId));
        roomGateEvent(GameEvent.Type.ROOM_CLEARED, r);
        if (r.type == RoomType.BOSS) {
            DungeonDef d = balance.dungeon(r.dungeonId);
            revived.remove(d.id);
            boolean first = !state.bossDefeated(d.id);
            int gain = absorb.absorbBoss(state, d.id);
            OreType ore = OreType.valueOf(d.ore);
            if (!state.chests.contains(r.id)) state.chests.add(r.id);
            map.updateGates(state);
            GameEvent e = new GameEvent(GameEvent.Type.BOSS_DEFEATED);
            e.dungeonId = d.id;
            e.hpGain = gain;
            e.ore = ore;
            e.first = first;
            e.text = d.boss.name;
            int idx = indexOf(d.id);
            if (idx + 1 < AbsorbService.BOSS_ORDER.length) {
                e.opened = NextTask.dungeonWord(AbsorbService.BOSS_ORDER[idx + 1]) + " 던전 · "
                        + balance.dungeon(AbsorbService.BOSS_ORDER[idx + 1]).name;
            } else {
                e.opened = "세 보스의 힘을 모두 흡수했다. 캠프로 돌아가 성검의 봉인을 풀 수 있다";
            }
            events.add(e);
            events.add(new GameEvent(GameEvent.Type.AUTOSAVE));
        } else if (r.type == RoomType.FINAL) {
            state.finalBossDefeated = true;
            events.add(new GameEvent(GameEvent.Type.FINAL_DEFEATED));
            events.add(new GameEvent(GameEvent.Type.AUTOSAVE));
        } else if (r.type == RoomType.MOB && !state.chests.contains(r.id)
                && random.nextFloat() < balance.reward.mobChestChance) {
            // 잡몹방 상자는 가끔만 (지용 버전은 늘 나왔다, 2026-10-14 줄임)
            state.chests.add(r.id);
            events.add(GameEvent.toast("방을 비웠다! 보물상자가 나타났다"));
            events.add(GameEvent.sound("ore"));
        } else {
            events.add(GameEvent.toast("방을 비웠다! 문이 열렸다"));
        }
    }

    private float potionMsgCd;

    /** 바닥의 포션 위를 지나가면 주워 바로 마신다. 체력이 가득하면 그대로 남는다. */
    private void updatePotionDrops(float dt) {
        potionMsgCd -= dt;
        int max = rules.maxHp(state);
        float reach = balance.potion.pickupRadiusTiles * T;
        for (java.util.Iterator<float[]> it = state.potionDrops.iterator(); it.hasNext(); ) {
            float[] p = it.next();
            if (Math.hypot(p[0] - hero.x, p[1] - hero.y) > reach) continue;
            if (state.hp >= max) {
                if (potionMsgCd <= 0) events.add(GameEvent.toast("체력이 가득해서 포션은 남겨 둔다"));
                potionMsgCd = 4f;
                continue;
            }
            it.remove();
            int heal = Math.min(max - state.hp, Math.max(1, Math.round(max * balance.potion.healRatio)));
            state.hp += heal;
            events.add(GameEvent.floatText(hero.x, hero.y + 40, "+" + heal, GameEvent.Tone.HEAL));
            events.add(GameEvent.sound("potion"));
        }
    }

    private static int indexOf(String dungeonId) {
        for (int i = 0; i < AbsorbService.BOSS_ORDER.length; i++) if (AbsorbService.BOSS_ORDER[i].equals(dungeonId)) return i;
        return -1;
    }

    // ───────────────────────── 광맥

    private void ensureVeins(Room r) {
        RoomRecord rec = state.room(r.id);
        if (rec.veins != null) return;
        Balance.MiningCfg mc = balance.mining;
        boolean cleared = state.bossDefeated(r.dungeonId);
        int count = cleared ? mc.veinsCleared : mc.veins;
        int min = cleared ? mc.oreTotalMinCleared : mc.oreTotalMin;
        int max = cleared ? mc.oreTotalMaxCleared : mc.oreTotalMax;
        int total = min + random.nextInt(max - min + 1);
        if (!state.firstOreRoomDone) total = Math.max(total, mc.firstOreRoomGuarantee);
        OreType ore = OreType.valueOf(balance.dungeon(r.dungeonId).ore);
        if (r.scene != null && !r.scene.oreSpots.isEmpty()) {
            // 그림 속 광석 덩어리 앞에 몇 개만, 대신 한 곳에서 많이 나온다
            rec.veins = VeinGenerator.generateAtSpots(r, random, count, ore, total, mc.hitsToBreak, T);
        } else {
            rec.veins = VeinGenerator.generate(r, random, count, ore, 1, 1, mc.hitsToBreak, 0);
            VeinGenerator.spread(rec.veins, total, random);
        }
        state.firstOreRoomDone = true;
        for (Vein v : rec.veins) if (!v.painted()) map.setBlocked(v.tileX, v.tileY, true);
    }

    private void applyVeinOverlay() {
        for (Room r : map.rooms) {
            if (r.type != RoomType.ORE) continue;
            clearOverlay(r);
            RoomRecord rec = state.rooms.get(r.id);
            if (rec != null && rec.veins != null) {
                for (Vein v : rec.veins) if (!v.painted()) map.setBlocked(v.tileX, v.tileY, true);
            }
        }
    }

    private void clearOverlay(Room r) {
        for (int y = r.y0; y <= r.y1; y++)
            for (int x = r.x0; x <= r.x1; x++) map.setBlocked(x, y, false);
    }

    public List<Vein> veinsOf(Room r) {
        RoomRecord rec = state.rooms.get(r.id);
        return rec == null || rec.veins == null ? null : rec.veins;
    }

    // ───────────────────────── 상호작용

    private void findInteraction() {
        prompt = null;
        targetProp = null;
        targetVein = null;
        targetPortal = false;
        targetChest = false;
        targetVillager = null;
        float best = Float.MAX_VALUE;
        if (inVillage()) {
            Balance.Villager near = null;
            float bestV = 1.4f * T;
            for (Balance.Villager v : balance.village.villagers) {
                float d = (float) Math.hypot(villagerX(v) - hero.x, villagerY(v) - hero.y);
                if (d < bestV) {
                    bestV = d;
                    near = v;
                }
            }
            if (near != null) {
                targetVillager = near;
                prompt = villagersFree() ? interactKey + "  " + near.name + "와(과) 이야기하기" : interactKey + "  " + near.name + " 살펴보기";
                promptX = villagerX(near);
                promptY = villagerY(near) + 2.2f * T;
                return;
            }
        }
        if (heroRoom != null && hasChest(heroRoom) && fightRoom == null) {
            float d = (float) Math.hypot(chestX(heroRoom) - hero.x, chestY(heroRoom) - hero.y);
            if (d < 1.3f * T) {
                targetChest = true;
                prompt = interactKey + "  보물상자 열기";
                promptX = chestX(heroRoom);
                promptY = chestY(heroRoom) + 1.6f * T;
                return;
            }
        }
        if (heroRoom != null && hasPortal(heroRoom) && fightRoom == null) {
            float d = (float) Math.hypot(portalX(heroRoom) - hero.x, portalY(heroRoom) - hero.y);
            if (d < 1.1f * T) {
                targetPortal = true;
                prompt = interactKey + "  캠프로 귀환";
                promptX = portalX(heroRoom);
                promptY = portalY(heroRoom) + 1.6f * T;
                return;
            }
        }
        for (Prop p : map.props) {
            float px = Math.max(p.x(T), Math.min(hero.x, p.x(T) + p.width(T)));
            float py = Math.max(p.y(T), Math.min(hero.y, p.y(T) + p.height(T)));
            float d = (float) Math.hypot(px - hero.x, py - hero.y);
            if (d < 0.9f * T && d < best) {
                best = d;
                targetProp = p;
            }
        }
        if (targetProp != null) {
            switch (targetProp.kind) {
                case FORGE: prompt = interactKey + "  대장간"; break;
                case BED: prompt = interactKey + "  침대 (회복·저장)"; break;
                case SEAL:
                    if (absorb.canUnseal(state)) prompt = interactKey + "  성검의 봉인 풀기";
                    else if (state.unsealed && !state.finalBossDefeated) prompt = interactKey + "  심연으로 내려가기 (마왕)";
                    else if (state.unsealed) prompt = interactKey + "  심연 (마왕 다시 보기는 개발 메뉴)";
                    else prompt = "봉인된 문양 · 세 보스의 힘이 필요하다";
                    break;
                default: prompt = interactKey + "  모닥불"; break;
            }
            promptX = targetProp.centerX(T);
            promptY = targetProp.y(T) + targetProp.height(T) + 18;
            return;
        }
        if (heroRoom != null && heroRoom.type == RoomType.ORE) {
            List<Vein> veins = veinsOf(heroRoom);
            if (veins != null) {
                for (Vein v : veins) {
                    float d = (float) Math.hypot(center(v.tileX) - hero.x, center(v.tileY) - hero.y);
                    if (d < 1.35f * T && d < best) {
                        best = d;
                        targetVein = v;
                    }
                }
            }
            if (targetVein != null) {
                prompt = interactKey + "  채굴 (" + targetVein.hitsLeft + "번 남음)";
                promptX = center(targetVein.tileX);
                promptY = (targetVein.tileY + 1) * T + 14;
                return;
            }
        }
        // 잠긴 던전 입구 (FR-맵-08)
        for (com.yongsa.map.Link l : map.links) {
            if (!l.gateClosed) continue;
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                float dist = (float) Math.hypot(d.footX - hero.x, d.footY - hero.y);
                if (dist < 2.4f * T) {
                    prompt = "FINAL".equals(l.gate) ? "성검의 봉인을 풀어야 열립니다" : "앞 던전의 보스를 잡아야 열립니다";
                    promptX = d.footX;
                    promptY = d.footY + T;
                    return;
                }
            }
        }
    }

    private void interact() {
        if (targetVillager != null) {
            talk(targetVillager);
            return;
        }
        if (targetChest) {
            openChest(heroRoom);
            return;
        }
        if (targetPortal) {
            events.add(new GameEvent(GameEvent.Type.WARP));
            events.add(GameEvent.sound("warp"));
            return;
        }
        if (targetProp != null) {
            switch (targetProp.kind) {
                case FORGE:
                    events.add(new GameEvent(GameEvent.Type.OPEN_FORGE));
                    break;
                case BED:
                    startSleep(targetProp);
                    break;
                case SEAL:
                    if (absorb.canUnseal(state)) {
                        events.add(new GameEvent(GameEvent.Type.UNSEAL_READY));
                    } else if (state.unsealed && !state.finalBossDefeated) {
                        events.add(new GameEvent(GameEvent.Type.FALL));
                        events.add(GameEvent.sound("slam"));
                    } else if (!state.unsealed) {
                        int n = 0;
                        for (String id : AbsorbService.BOSS_ORDER) if (state.bossDefeated(id)) n++;
                        events.add(GameEvent.toast("문양 아래에서 무언가 꿈틀댄다. 흡수한 보스의 힘 " + n + "/3"));
                    }
                    break;
                default:
                    events.add(GameEvent.toast("따뜻하다. 캠프에 있으면 체력이 천천히 찬다"));
            }
        } else if (targetVein != null) {
            mine(targetVein);
        }
    }

    private void mine(Vein v) {
        if (hero.mineCd > 0) return;
        hero.mineCd = 0.28f;
        hero.mineSwingT = 0.22f;
        float vx = v.painted() ? v.spotX : center(v.tileX), vy = v.painted() ? v.spotY : center(v.tileY);
        float dx = vx - hero.x, dy = vy - hero.y;
        if (Math.abs(dx) + Math.abs(dy) > 1) hero.facing = Facing.of(dx, dy);
        v.hitsLeft--;
        events.add(GameEvent.sound("mine"));
        GameEvent fx = GameEvent.fx(GameEvent.Type.MINE_FX, vx, vy);
        fx.ore = v.ore;
        fx.size = v.hitsLeft;
        events.add(fx);
        if (v.hitsLeft > 0) return;
        state.addOre(v.ore, v.amount);
        state.stats.oreMined += v.amount;
        veinsOf(heroRoom).remove(v);
        if (!v.painted()) map.setBlocked(v.tileX, v.tileY, false);
        GameEvent e = new GameEvent(GameEvent.Type.ORE_GAINED);
        e.ore = v.ore;
        e.amount = v.amount;
        e.x = center(v.tileX);
        e.y = center(v.tileY) + T / 2f;
        events.add(e);
        events.add(GameEvent.floatText(e.x, e.y, v.ore.displayName + " +" + v.amount, GameEvent.Tone.ORE));
        events.add(GameEvent.sound("ore"));
    }

    // ───────────────────────── 던전 밖 마을 (선우 버전)

    public boolean inVillage() {
        return heroRoom != null && heroRoom.type == RoomType.VILLAGE;
    }

    /** 마을 사람들은 마왕을 쓰러뜨리면 수정에서 풀려난다. */
    public boolean villagersFree() {
        return state.finalBossDefeated;
    }

    public Room village() {
        for (Room r : map.rooms) if (r.type == RoomType.VILLAGE) return r;
        return null;
    }

    public float villagerX(Balance.Villager v) {
        return village().sceneX(v.at[0]);
    }

    public float villagerY(Balance.Villager v) {
        return village().sceneY(v.at[1]);
    }

    /**
     * 캠프 아래 계단으로 마을에 나가려 할 때: 마왕을 쓰러뜨렸으면 그냥 나간다.
     * 아니면 다섯 번까지는 독백하며 되돌아서고, 여섯 번째에는 체념하고 나간다 (그 뒤로는 그냥 나간다).
     * 되돌아섰으면 true.
     */
    private boolean refuseVillageExit(Door d) {
        Balance.VillageCfg v = balance.village;
        if (state.finalBossDefeated || state.exitRefusals > v.exitRefusals) return false;
        if (state.exitRefusals == v.exitRefusals) {
            state.exitRefusals++;
            giveUpPending = true;
            return false;
        }
        heroSpeech(v.exitLines.get(Math.min(state.exitRefusals, v.exitLines.size() - 1)));
        state.exitRefusals++;
        // 계단에서 한 걸음 물러선다
        float bx = hero.x - d.dirX * 1.3f * T, by = hero.y - d.dirY * 1.3f * T;
        if (Physics.free(map, bx, by, hero, T)) {
            hero.x = bx;
            hero.y = by;
        }
        hero.aimX = -d.dirX;
        hero.aimY = -d.dirY;
        hero.facing = Facing.of(hero.aimX, hero.aimY);
        hero.rollT = 0;
        hero.kbT = 0;
        doorCd = 0.5f;
        events.add(GameEvent.sound("block"));
        return true;
    }

    private void heroSpeech(String text) {
        if (text == null) return;
        GameEvent e = new GameEvent(GameEvent.Type.BOSS_SPEECH);
        e.text = text;
        e.speaker = "용사";
        e.x = hero.x;
        e.y = hero.y + 2.4f * T;
        events.add(e);
    }

    private void talk(Balance.Villager v) {
        float dx = villagerX(v) - hero.x, dy = villagerY(v) - hero.y;
        if (Math.abs(dx) + Math.abs(dy) > 1) {
            hero.facing = Facing.of(dx, dy);
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            hero.aimX = dx / l;
            hero.aimY = dy / l;
        }
        if (!villagersFree()) {
            heroSpeech(String.format(balance.village.trappedLine, v.name));
            return;
        }
        int n = talkCount.merge(v.kind, 1, Integer::sum) - 1;
        GameEvent e = new GameEvent(GameEvent.Type.BOSS_SPEECH);
        e.text = v.freeLines.get(n % v.freeLines.size());
        e.speaker = v.name;
        e.x = villagerX(v);
        e.y = villagerY(v) + 2.2f * T;
        events.add(e);
        events.add(GameEvent.sound("select"));
    }

    // ───────────────────────── 보물상자 (선우·지용 버전의 보스 보상)

    public boolean hasChest(Room r) {
        return r != null && state.chests.contains(r.id);
    }

    /** 보스방 상자는 방 가운데 오른쪽 옆 (바닥이 아니면 왼쪽), 잡몹방 상자는 방 가운데에서 가장 가까운 넓은 바닥. */
    public float chestX(Room r) {
        return center(chestTile(r)[0]);
    }

    public float chestY(Room r) {
        return center(chestTile(r)[1]);
    }

    private int[] chestTile(Room r) {
        int cx = (int) r.centerTileX(), cy = (int) r.centerTileY();
        if (r.type == RoomType.BOSS) return new int[]{cx + (r.floor(cx + 3, cy) ? 3 : -3), cy};
        for (int rad = 0; rad < 12; rad++)
            for (int dy = -rad; dy <= rad; dy++)
                for (int dx = -rad; dx <= rad; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != rad) continue;
                    int tx = cx + dx, ty = cy + dy;
                    if (r.floor(tx, ty) && r.floor(tx - 1, ty) && r.floor(tx + 1, ty) && r.floor(tx, ty - 1) && r.floor(tx, ty + 1)
                            && !map.solid(tx, ty)) return new int[]{tx, ty};
                }
        return new int[]{cx, cy};
    }

    /** 상자를 열면 그 던전 광물을 받고, 상자 앞 바닥에 회복 포션이 떨어진다 (보스방은 광물이 많고, 잡몹방은 조금). */
    public void openChest(Room r) {
        if (!hasChest(r)) return;
        state.chests.remove(r.id);
        Balance.RewardCfg rw = balance.reward;
        boolean boss = r.type == RoomType.BOSS;
        OreType ore = OreType.valueOf(balance.dungeon(r.dungeonId).ore);
        int lo = boss ? rw.chestOreMin : rw.mobChestOreMin, hi = boss ? rw.chestOreMax : rw.mobChestOreMax;
        int n = lo + random.nextInt(Math.max(1, hi - lo + 1));
        state.addOre(ore, n);
        int potions = boss ? rw.bossChestPotions : rw.mobChestPotions;
        dropPotions(chestX(r), chestY(r), potions);
        GameEvent e = new GameEvent(GameEvent.Type.CHEST_OPENED);
        e.dungeonId = r.dungeonId;
        e.opened = r.id;
        e.ore = ore;
        e.amount = n;
        e.potions = potions;
        e.x = chestX(r);
        e.y = chestY(r) + T;
        events.add(e);
        events.add(GameEvent.floatText(e.x, e.y, ore.displayName + " +" + n, GameEvent.Tone.ORE));
        events.add(GameEvent.sound("ore"));
        events.add(new GameEvent(GameEvent.Type.AUTOSAVE));
    }

    /** 상자 앞 바닥에 포션 n 개를 떨어뜨린다 (설 수 있는 자리에). */
    private void dropPotions(float cx, float cy, int n) {
        float[][] offs = {{0, -1.2f}, {-1.1f, -0.9f}, {1.1f, -0.9f}, {0, -2.2f}, {-1.4f, 0}, {1.4f, 0}};
        int placed = 0;
        for (float[] o : offs) {
            if (placed >= n) break;
            float x = cx + o[0] * T, y = cy + o[1] * T;
            if (!map.free(x, y, hero.hw, hero.hh)) continue;
            state.potionDrops.add(new float[]{x, y});
            placed++;
        }
        for (; placed < n; placed++) state.potionDrops.add(new float[]{cx, cy - T});
    }

    /** 침대에 눕는다: 화면이 어두워지는 동안 쉬고 (useBed) 다시 밝아지면 일어난다. */
    public void startSleep(Prop bed) {
        if (sleepTime > 0) return;
        sleepTime = 0.0001f;
        slept = false;
        sleepBed = bed;
        events.add(GameEvent.sound("select"));
    }

    public boolean sleeping() {
        return sleepTime > 0;
    }

    /** 0~1: 잠든 정도의 진행 (그리는 쪽에서 어둡게 하고 Zzz 를 띄운다). */
    public float sleepProgress() {
        return sleepTime > 0 ? Math.min(1f, sleepTime / SLEEP_TIME) : 0f;
    }

    /** 자고 있는 침대 (자고 있지 않으면 null). */
    public Prop sleepBed() {
        return sleepTime > 0 ? sleepBed : null;
    }

    /**
     * 침대: 체력을 채우고, 모든 던전의 잡몹·광맥과 (마왕이 아닌) 보스를 다시 채운다 (FR-던전-05, 선우 버전 보스 재생성).
     * 다시 잡아도 힘(최대 체력)은 한 번만 오르고, 상자는 매번 나온다. 열지 않은 상자가 있는 보스방은 그대로 둔다.
     */
    public void useBed() {
        state.hp = rules.maxHp(state);
        for (Room r : map.rooms) {
            RoomRecord rec = state.room(r.id);
            if (r.type == RoomType.BOSS && hasChest(r)) continue;
            if (r.type == RoomType.FINAL && state.finalBossDefeated) continue;
            rec.cleared = false;
            if (r.type == RoomType.ORE) {
                rec.veins = null;
                clearOverlay(r);
            }
        }
        events.add(new GameEvent(GameEvent.Type.BED_USED));
        events.add(new GameEvent(GameEvent.Type.AUTOSAVE));
    }

    // ───────────────────────── 메뉴에서 부르는 것

    public boolean canExitDungeon() {
        return inDungeon() && fightRoom == null;
    }

    /** Esc 메뉴의 던전 나가기 (FR-던전-11). 문이 잠긴 전투 중에는 안 된다. */
    public boolean exitDungeon() {
        if (!canExitDungeon()) return false;
        placeAtCampSpawn();
        heroRoom = currentRoom = map.room("CAMP");
        return true;
    }

    /** 보스를 쓰러뜨리면 보스방 위쪽의 캠프 귀환문이 열린다. */
    public boolean hasPortal(Room r) {
        return r.type == RoomType.BOSS && state.bossDefeated(r.dungeonId) && state.room(r.id).cleared
                && !revived.contains(r.dungeonId);
    }

    // ───────────────────────── 개발용 (F1)

    /**
     * 쓰러뜨린 보스를 다시 싸울 수 있게 되살린다. 이미 흡수한 힘·열린 던전·광물은 그대로 두고,
     * 보스방에 다시 들어가면 싸움이 시작된다. dungeonId 가 "FINAL" 이면 마왕.
     * 싸우는 중이면 되살리지 않는다. 되살렸으면 true.
     */
    public boolean devReviveBoss(String dungeonId) {
        if (fightRoom != null) return false;
        if ("FINAL".equals(dungeonId)) {
            if (!state.finalBossDefeated) return false;
            state.finalBossDefeated = false;
            for (Room r : map.rooms) if (r.type == RoomType.FINAL) state.room(r.id).cleared = false;
            return true;
        }
        if (!state.bossDefeated(dungeonId) || revived.contains(dungeonId)) return false;
        revived.add(dungeonId);
        for (Room r : map.rooms) {
            if (r.type == RoomType.BOSS && dungeonId.equals(r.dungeonId)) state.room(r.id).cleared = false;
        }
        return true;
    }

    /** 되살아나 보스방에서 기다리는 보스인가 (개발 메뉴 표시용). */
    public boolean devRevived(String dungeonId) {
        return revived.contains(dungeonId);
    }

    /**
     * 개발용: 보스방(dungeonId 가 "FINAL" 이면 심연) 입구로 바로 옮긴다. 조금 걸어 들어가면 싸움이 시작된다.
     * 싸우는 중이면 옮기지 않는다. 옮겼으면 true.
     */
    public boolean devWarpToBoss(String dungeonId) {
        if (fightRoom != null) return false;
        Room target = null;
        for (Room r : map.rooms) {
            boolean match = "FINAL".equals(dungeonId) ? r.type == RoomType.FINAL
                    : r.type == RoomType.BOSS && dungeonId.equals(r.dungeonId);
            if (match) target = r;
        }
        if (target == null) return false;
        Door in = null;
        for (Door d : target.doors) if (!d.dead()) in = d;
        hero.x = in != null ? in.arriveX : target.spawnX(T);
        hero.y = in != null ? in.arriveY : target.spawnY(T);
        hero.aimX = in != null ? -in.dirX : 0;
        hero.aimY = in != null ? -in.dirY : 1;
        hero.facing = Facing.of(hero.aimX, hero.aimY);
        hero.rollT = 0;
        hero.kbT = 0;
        hero.slowT = 0;
        hazards.clear();
        blindT = 0;
        hero.stunT = 0;
        projectiles.clear();
        shots.clear();
        pendingDoor = null;
        doorCd = 0.35f;
        return true;
    }

    /** 광물 무한: 모자라면 채워 둔다. */
    public void devFillOres(int amount) {
        for (OreType t : OreType.values()) if (state.ore(t) < amount) state.ores.put(t, amount);
    }

    public float portalX(Room r) {
        return r.scene != null && r.scene.returnDoor != null ? r.sceneX(r.scene.returnDoor[0]) : center((int) r.centerTileX());
    }

    public float portalY(Room r) {
        return r.scene != null && r.scene.returnDoor != null ? r.sceneY(r.scene.returnDoor[1]) : center((int) r.centerTileY());
    }

    /** 마왕이 기다리는 심연. 봉인을 풀면 캠프 아래 계단과도 이어진다. */
    public Room abyss() {
        for (Room r : map.rooms) if (r.type == RoomType.FINAL) return r;
        return null;
    }

    /** 캠프 문양에서 땅이 갈라져 심연으로 떨어진다. 떨어진 자리에서 조금 걸어 들어가면 싸움이 시작된다. */
    public void dropToAbyss() {
        Room f = abyss();
        if (f == null || fightRoom != null) return;
        hero.x = f.spawnX(T);
        hero.y = f.spawnY(T);
        hero.facing = Facing.UP;
        hero.aimX = 0;
        hero.aimY = 1;
        hero.rollT = 0;
        hero.kbT = 0;
        heroRoom = currentRoom = f;
        state.room(f.id).visited = true;
        setEntry(null);
    }

    /** 귀환문으로 캠프에 돌아간다. */
    public void warpToCamp() {
        if (fightRoom != null) return;
        placeAtCampSpawn();
        heroRoom = currentRoom = map.room("CAMP");
        hero.rollT = 0;
        hero.kbT = 0;
    }

    /** 저장 직전에 이어하기 위치를 정한다 (FR-저장-03, A25). */
    public void prepareSave() {
        if (heroRoom != null && heroRoom.type == RoomType.FINAL) {
            // 심연에서는 캠프(문양 옆)에서 이어 한다
            Room camp = map.room("CAMP");
            state.resumeX = camp.scene == null ? center((int) camp.centerTileX()) : camp.spawnX(T);
            state.resumeY = camp.scene == null ? center((int) camp.centerTileY() - 2) : camp.spawnY(T);
        } else if (inCamp() || inVillage()) {
            state.resumeX = hero.x;
            state.resumeY = hero.y;
        } else if (fightRoom != null) {
            state.resumeX = outsideX; // 잠긴 방은 처음 상태로 돌아가고 문 밖에서 시작
            state.resumeY = outsideY;
        } else {
            state.resumeX = entryX;
            state.resumeY = entryY;
        }
        if (state.hp <= 0) state.hp = rules.maxHp(state);
    }

    /** 봉인 해제 장면이 끝나면 부른다. */
    public Weapon unseal() {
        Weapon w = absorb.unseal(state);
        map.updateGates(state);
        return w;
    }

    public void onWeaponsChanged() {
        state.hp = Math.min(state.hp, rules.maxHp(state));
    }

    public void emit(GameEvent e) {
        events.add(e);
    }

    /** 테스트·디버그용: 방을 바로 비운 것으로 만든다. */
    public void debugKillAll() {
        for (Monster m : monsters) {
            m.hp = 0;
            m.dead = true;
        }
    }
}
