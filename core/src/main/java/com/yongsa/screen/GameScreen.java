package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.yongsa.save.KeyBindings;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.YongsaGame;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.ui.BagWindow;
import com.yongsa.ui.ConfirmDialog;
import com.yongsa.ui.DevMenu;
import com.yongsa.ui.ForgeWindow;
import com.yongsa.ui.Hud;
import com.yongsa.ui.MessageWindow;
import com.yongsa.ui.ModalStack;
import com.yongsa.ui.PauseMenu;
import com.yongsa.ui.Ui;
import com.yongsa.ui.UnsealScene;
import com.yongsa.map.CameraFollow;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import com.yongsa.ui.EndingScreen;
import com.yongsa.ui.StoryScreen;
import com.yongsa.ui.TitleScreen;

/**
 * 플레이 화면. 창이 열려 있으면 게임 시간(이동, 전투, 쿨타임)은 멈추고 UI 시간(버튼, 연출)만 흐른다:
 * Stage.act 는 항상 부르고 GameSession.update 는 창이 없을 때만 부른다.
 */
public class GameScreen extends ScreenAdapter {
    public final YongsaGame game;
    public final GameSession session;
    public final Hud hud;
    public final ModalStack modals;

    private final OrthographicCamera worldCam = new OrthographicCamera();
    private final FitViewport worldViewport = new FitViewport(YongsaGame.WIDTH, YongsaGame.HEIGHT, worldCam);
    private final Stage stage;
    private final WorldRenderer renderer;
    private final CameraFollow follow = new CameraFollow();
    private final Controls controls = new Controls();
    private boolean interactPressed, rollPressed, attackPressed;
    /** 누른 스킬 칸 (0=L, 1=U, 2=I, 3=O), 없으면 -1. */
    private int skillPressed = -1;
    private float endingDelay = -1;
    /** 화면 흔들림 세기 (0~1), 타격 순간 잠깐 멈추는 시간, 화면 전환용 검은 막. */
    private float trauma, hitStop, shakeTime;
    private float fade = 1f, fadeTarget = 0f;
    private boolean warping;
    /** 화면 전환이 포탈(캠프로)이 아니라 문양에서 심연으로 떨어지는 것. */
    private boolean falling;
    /** 화면 전환이 문을 지나 옆 방으로 가는 것 (짧게 어두워졌다 밝아진다). */
    private boolean doorWarp;
    /** 개발 메뉴의 보스방 바로 가기: 화면이 어두워지면 이 던전(또는 FINAL)의 보스방 입구로 옮긴다. */
    private String devWarpTarget;
    /** 기본 배율(100%): 한 칸의 그림 픽셀이 화면 3픽셀이 되도록 맵을 1.5배로 확대해서 본다. */
    public static final float ZOOM = 2f / 3f;
    /** 휠 / +,- 로 고르는 확대 단계 (기본 배율 대비 %). 일반 구간과 보스방이 따로 기억된다.
     *  1280×720 기준 100%는 그림 1픽셀 = 화면 3픽셀, 133%는 4픽셀로 딱 떨어진다 (1080p 전체화면이면 133%만 6픽셀로 딱 떨어짐). 나머지는 픽셀 크기가 조금씩 고르지 않다. */
    public static final int[] ZOOM_STEPS = {80, 90, 100, 108, 115, 125, 133, 150};
    /** 지금 화면 배율 (카메라 zoom 값, 작을수록 크게 보임). 목표 배율로 부드럽게 따라간다. */
    private float zoom;

    public GameScreen(YongsaGame game, GameState state) {
        this.game = game;
        this.session = new GameSession(game.balance, game.newMap(), state, System.nanoTime());
        session.deferDoors = true;
        this.stage = new Stage(new FitViewport(YongsaGame.WIDTH, YongsaGame.HEIGHT), game.batch);
        this.modals = new ModalStack(stage);
        this.renderer = new WorldRenderer(session, game.art, game.fonts, game.batch, game.shapes);
        this.hud = new Hud(session, game.art, game.fonts, game.batch, game.shapes);
        zoom = targetZoom();
        worldCam.zoom = zoom;
        applyKeyNames();
        if (session.inCamp()) hud.banner("용사의 캠프");
    }

    @Override
    public void show() {
        InputAdapter keys = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.F1 && endingDelay < 0) {
                    toggleDevMenu();
                    return true;
                }
                if (!modals.isEmpty()) {
                    boolean handled = modals.keyDown(keycode);
                    if (handled) game.sfx.play("select");
                    return true;
                }
                if (endingDelay >= 0) return true;
                // 지도가 열려 있으면 Esc 는 지도부터 닫는다 (메뉴 스택처럼 위에 열린 것이 먼저)
                if (keycode == Input.Keys.ESCAPE && hud.mapOpen()) {
                    hud.closeMap();
                    game.sfx.play("select");
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    openPause();
                    return true;
                }
                // 조작 키는 사용자가 바꿀 수 있다 (설정 → 조작 키)
                KeyBindings.Action a = game.settings.keys().owner(keycode);
                if (a == null) return false;
                switch (a) {
                    case BAG: openBag(); return true;
                    case MAP:
                        hud.toggleMap();
                        game.sfx.play("select");
                        return true;
                    case INTERACT: interactPressed = true; return true;
                    case ROLL: rollPressed = true; return true;
                    case ATTACK: attackPressed = true; return true;
                    case SKILL_1: skillPressed = 0; return true;
                    case SKILL_2: skillPressed = 1; return true;
                    case SKILL_3: skillPressed = 2; return true;
                    case SKILL_HOLY: skillPressed = 3; return true;
                    case ZOOM_IN: stepZoom(1); return true;
                    case ZOOM_OUT: stepZoom(-1); return true;
                    default: return false; // 이동은 readControls 에서 누르고 있는지 본다
                }
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (!modals.isEmpty() || endingDelay >= 0 || amountY == 0) return false;
                stepZoom(amountY < 0 ? 1 : -1);
                return true;
            }
        };
        Gdx.input.setInputProcessor(new InputMultiplexer(keys, stage));
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 1 / 20f);
        // 지도를 보는 동안에도 게임 시간은 멈춘다 (몬스터가 지도 뒤에서 때리지 않게)
        boolean paused = !modals.isEmpty() || endingDelay >= 0 || warping || hud.mapOpen();
        boolean frozen = hitStop > 0;
        hitStop -= dt;
        session.devGodMode = game.settings.devGodMode;
        if (game.settings.devInfiniteOre) session.devFillOres(DevMenu.INFINITE_ORE);
        if (!paused && !frozen) {
            readControls();
            session.update(dt, controls);
        }
        if (!frozen) {
            interactPressed = rollPressed = attackPressed = false;
            skillPressed = -1;
        }
        handleEvents();
        if (!paused) renderer.update(frozen ? dt * 0.15f : dt);
        hud.update(dt);
        updateFade(delta);
        trauma = Math.max(0f, trauma - dt * 1.7f);
        shakeTime += dt;
        if (endingDelay >= 0) {
            endingDelay -= delta;
            renderer.update(dt);
            if (endingDelay < 0) {
                // 엔딩 이야기 그림 3장 → 기록 화면
                GameState done = session.state;
                game.setScreen(new StoryScreen(game, game.story.ending, () -> game.setScreen(new EndingScreen(game, done))));
                dispose();
                return;
            }
        }

        int T = game.balance.tileSize;
        float want = targetZoom();
        zoom += (want - zoom) * Math.min(1f, dt * 6f);
        if (Math.abs(want - zoom) < 0.0005f) zoom = want;
        worldCam.zoom = zoom;
        float cameraY = renderer.cameraTargetY(session.hero.y + 16, worldCam.viewportHeight * zoom, zoom);
        follow.update(session.hero.x, cameraY, dt, worldCam.viewportWidth * zoom, worldCam.viewportHeight * zoom,
                session.map.width * T, session.map.height * T);
        float shake = trauma * trauma * 12f;
        float ox = shake * (MathUtils.sin(shakeTime * 47f) + MathUtils.sin(shakeTime * 83f) * 0.5f) / 1.5f;
        float oy = shake * (MathUtils.cos(shakeTime * 53f) + MathUtils.sin(shakeTime * 71f) * 0.5f) / 1.5f;
        // 화면 픽셀 단위로 맞춰 그림이 떨리지 않게 한다
        worldCam.position.set(Math.round((follow.x + ox) / zoom) * zoom, Math.round((follow.y + oy) / zoom) * zoom, 0);
        worldCam.update();

        renderer.renderLights(worldCam);
        Gdx.gl.glClearColor(0.02f, 0.018f, 0.03f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        worldViewport.apply();
        renderer.showWalkable = game.settings.devShowWalkable;
        renderer.render(worldCam);
        stage.getViewport().apply();
        hud.showWalkMasks = game.settings.devShowWalkable;
        hud.flashEnabled = game.settings.screenFlash;
        if (!modals.isEmpty()) hud.closeMap(); // 창이 뜨면 지도는 접는다
        hud.render(stage.getCamera().combined, YongsaGame.WIDTH, YongsaGame.HEIGHT);
        hud.renderFade(fade);
        stage.act(delta);
        stage.draw();
    }

    /** 보스방(최종 보스방 포함)에 있는지. 확대 배율을 일반 구간과 따로 쓴다. */
    private boolean inBossArea() {
        Room r = session.heroRoom();
        return r != null && (r.type == RoomType.BOSS || r.type == RoomType.FINAL);
    }

    private float targetZoom() {
        int pct = inBossArea() ? game.settings.zoomBoss : game.settings.zoomNormal;
        if (pct == 133) return ZOOM * 0.75f; // 정확히 4/3배라야 픽셀이 딱 떨어진다
        return ZOOM * 100f / Math.max(50, pct);
    }

    /** 확대 단계를 한 칸 올리거나(+1) 내린다(-1). 지금 있는 구간(일반/보스방)의 값만 바뀐다. */
    private void stepZoom(int dir) {
        boolean boss = inBossArea();
        int cur = boss ? game.settings.zoomBoss : game.settings.zoomNormal;
        int i = 0;
        while (i < ZOOM_STEPS.length - 1 && ZOOM_STEPS[i] < cur) i++;
        if (ZOOM_STEPS[i] != cur && dir < 0) i++; // 목록에 없는 값이면 가까운 쪽부터
        int next = ZOOM_STEPS[MathUtils.clamp(i + dir, 0, ZOOM_STEPS.length - 1)];
        if (boss) game.settings.zoomBoss = next;
        else game.settings.zoomNormal = next;
        game.saveSettings();
        hud.toast("화면 배율 " + next + "% · " + (boss ? "보스방" : "일반 구간"));
    }

    private void updateFade(float delta) {
        float speed = doorWarp ? 6.5f : warping ? 3f : 1.8f;
        if (fade < fadeTarget) fade = Math.min(fadeTarget, fade + delta * speed);
        else if (fade > fadeTarget) fade = Math.max(fadeTarget, fade - delta * speed);
        if (warping && fade >= 1f) {
            if (doorWarp) {
                session.passPendingDoor();
                doorWarp = false;
            } else if (devWarpTarget != null) {
                session.devWarpToBoss(devWarpTarget);
                devWarpTarget = null;
            } else if (falling) {
                session.dropToAbyss();
                hud.banner("심연 · 마왕의 옥좌");
                shake(0.8f);
                falling = false;
            } else {
                session.warpToCamp();
                hud.banner("용사의 캠프");
            }
            follow.snap();
            warping = false;
            fadeTarget = 0f;
        }
    }

    private void shake(float amount) {
        if (!game.settings.screenShake) return;
        trauma = Math.min(1f, trauma + amount);
    }

    private void stop(float seconds) {
        hitStop = Math.max(hitStop, seconds);
    }

    private void readControls() {
        controls.clear();
        KeyBindings keys = game.settings.keys();
        boolean l = held(keys, KeyBindings.Action.MOVE_LEFT);
        boolean r = held(keys, KeyBindings.Action.MOVE_RIGHT);
        boolean u = held(keys, KeyBindings.Action.MOVE_UP);
        boolean d = held(keys, KeyBindings.Action.MOVE_DOWN);
        controls.moveX = (r ? 1 : 0) - (l ? 1 : 0);
        controls.moveY = (u ? 1 : 0) - (d ? 1 : 0);
        controls.attack = attackPressed || held(keys, KeyBindings.Action.ATTACK);
        controls.roll = rollPressed;
        controls.skill = skillPressed;
        controls.interact = interactPressed;
    }

    /** 이 동작의 키 중 하나라도 누르고 있나. */
    private static boolean held(KeyBindings keys, KeyBindings.Action a) {
        for (int i = 0; i < KeyBindings.SLOTS; i++) {
            int k = keys.key(a, i);
            if (k != KeyBindings.NONE && Gdx.input.isKeyPressed(k)) return true;
        }
        return false;
    }

    /** 화면 안내 문구에 쓸 키 이름을 세션에 알려 준다 (조작 키를 바꾼 뒤에도 부른다). */
    public void applyKeyNames() {
        KeyBindings keys = game.settings.keys();
        session.interactKey = keys.primaryName(KeyBindings.Action.INTERACT);
        for (int i = 0; i < KeyBindings.SKILLS.length; i++) session.skillKeys[i] = keys.primaryShort(KeyBindings.SKILLS[i]);
        hud.attackKey = keys.primaryShort(KeyBindings.Action.ATTACK);
        hud.rollKey = keys.primaryShort(KeyBindings.Action.ROLL);
        hud.mapKey = keys.primaryName(KeyBindings.Action.MAP);
        hud.bagKey = keys.primaryName(KeyBindings.Action.BAG);
        session.bagKey = hud.bagKey;
        hud.moveKeys = keys.primaryShort(KeyBindings.Action.MOVE_UP) + " " + keys.primaryShort(KeyBindings.Action.MOVE_LEFT) + " "
                + keys.primaryShort(KeyBindings.Action.MOVE_DOWN) + " " + keys.primaryShort(KeyBindings.Action.MOVE_RIGHT);
    }

    private void handleEvents() {
        for (GameEvent e : session.pollEvents()) {
            renderer.onEvent(e);
            switch (e.type) {
                case HIT_FX:
                    if (e.tone == GameEvent.Tone.HERO_HIT) {
                        shake(0.45f);
                        stop(0.06f);
                        hud.hurt();
                    } else {
                        shake(e.boss ? 0.14f : 0.1f);
                        stop(0.035f);
                    }
                    break;
                case MONSTER_DIED:
                    game.sfx.play("kill");
                    shake(e.boss ? 0.9f : 0.2f);
                    stop(e.boss ? 0.3f : 0.07f);
                    break;
                case SLAM_FX:
                    shake(0.55f);
                    break;
                case ORE_GAINED:
                    shake(0.12f);
                    break;
                case BOSS_FIGHT_START:
                    game.sfx.play("roar");
                    shake(0.5f);
                    hud.bossIntro(e.text, e.boss);
                    break;
                case BOSS_ENRAGED:
                    shake(0.6f);
                    hud.banner(e.text + " · 분노");
                    break;
                case WARP:
                    warping = true;
                    fadeTarget = 1f;
                    break;
                case DOOR_ENTER:
                    warping = true;
                    doorWarp = true;
                    fadeTarget = 1f;
                    game.sfx.play("step");
                    break;
                case FALL:
                    startFall();
                    break;
                case BOSS_SPEECH: {
                    // 말풍선이 화면 밖이면 자막으로도 보여 준다 (용사는 늘 화면 가운데라 필요 없다)
                    if ("용사".equals(e.speaker)) break;
                    float hw = worldCam.viewportWidth * worldCam.zoom / 2f, hh = worldCam.viewportHeight * worldCam.zoom / 2f;
                    if (Math.abs(e.x - worldCam.position.x) > hw - 40 || Math.abs(e.y - worldCam.position.y) > hh - 60) {
                        hud.toast((e.speaker == null ? "마왕" : e.speaker) + ": “" + e.text + "”");
                    }
                    break;
                }
                case BOSS_PHASE2:
                    shake(0.9f);
                    stop(0.25f);
                    hud.banner(e.text + " · 진정한 모습");
                    break;
                case TOAST:
                    hud.toast(e.text);
                    break;
                case SOUND:
                    game.sfx.play(e.text);
                    break;
                case ROOM_LOCKED:
                    hud.toast("문이 잠겼다! 몬스터를 모두 쓰러뜨리자");
                    break;
                case ROOM_CLEARED:
                    if (!e.boss) hud.toast("방을 정리했다 · 길이 다시 열렸다");
                    break;
                case OPEN_FORGE:
                    modals.push(new ForgeWindow(this));
                    break;
                case BED_USED:
                    hud.toast("푹 쉬었다. 체력이 가득 찼다");
                    hud.toast("보스의 사념이 던전에 몬스터와 광맥을 다시 채웠다");
                    break;
                case AUTOSAVE:
                    save(false);
                    break;
                case BOSS_DEFEATED:
                    showBossResult(e);
                    break;
                case FINAL_DEFEATED:
                    hud.banner("마왕을 쓰러뜨렸다!");
                    game.sfx.play("unseal");
                    endingDelay = 2.5f;
                    break;
                case HERO_DIED:
                    game.sfx.play("death");
                    follow.snap();
                    java.util.List<ConfirmDialog.Line> lines = new java.util.ArrayList<>();
                    if (e.text != null) lines.add(new ConfirmDialog.Line("마왕: “" + e.text + "”", Ui.PURPLE));
                    lines.add(new ConfirmDialog.Line("캠프로 돌아갑니다.", null));
                    lines.add(new ConfirmDialog.Line("무기와 광물은 그대로다. 싸우던 방만 처음 상태로 돌아간다.", Ui.DIM));
                    modals.push(new MessageWindow(game.skin, "쓰러졌다…", Ui.RED, lines.toArray(new ConfirmDialog.Line[0]), "확인"));
                    break;
                case UNSEAL_READY:
                    modals.push(new UnsealScene(this));
                    break;
                case ENTERED_DUNGEON:
                    hud.banner(e.text);
                    break;
                case WAVE_STARTED:
                    hud.banner(e.text);
                    trauma = Math.min(1f, trauma + 0.25f);
                    break;
                case CHEST_OPENED:
                    game.sfx.play("awaken");
                    hud.toast("보물상자에서 " + e.ore.displayName + " " + e.amount + "개"
                            + "를 얻었다" + (e.potions > 0 ? " · 포션 " + e.potions + "개가 바닥에 떨어졌다" : ""));
                    break;
                default:
                    break;
            }
        }
    }

    private void showBossResult(GameEvent e) {
        game.sfx.play("awaken");
        if (!e.first) {
            // 침대 뒤 다시 생긴 보스: 힘은 이미 흡수했으니 상자만 나온다
            hud.banner(e.text + "을(를) 다시 쓰러뜨렸다 · 보물상자가 나타났다");
            return;
        }
        int maxHp = session.rules.maxHp(session.state);
        ConfirmDialog.Line[] lines = {
                new ConfirmDialog.Line(e.text + "의 힘을 흡수했다.", Ui.GOLD),
                new ConfirmDialog.Line("최대 체력 +" + e.hpGain + "  (이제 " + maxHp + ")", Ui.GREEN),
                new ConfirmDialog.Line("보상: 보물상자가 나타났다 (" + session.interactKey + " 로 열면 " + e.ore.displayName + " "
                        + game.balance.reward.chestOreMin + "~" + game.balance.reward.chestOreMax + "개)", null),
                new ConfirmDialog.Line("열림: " + e.opened, Ui.BLUE),
                new ConfirmDialog.Line(nextWeaponHint(), Ui.DIM),
                new ConfirmDialog.Line("방 위쪽에 열린 귀환문(" + session.interactKey + ")으로 캠프에 돌아갈 수 있다", Ui.PURPLE)
        };
        modals.push(new MessageWindow(game.skin, "던전 클리어!", Ui.GOLD, lines, "확인"));
    }

    private String nextWeaponHint() {
        for (String id : com.yongsa.model.AbsorbService.BOSS_ORDER) {
            if (session.state.bossDefeated(id)) continue;
            com.yongsa.config.Balance.DungeonDef d = game.balance.dungeon(id);
            com.yongsa.config.Balance.WeaponDef w = game.balance.weaponForOre(d.ore);
            return "다음에 만들 무기: " + w.name + " (" + com.yongsa.model.OreType.valueOf(d.ore).displayName + " " + w.craftCost + "개)";
        }
        return "이제 캠프로 돌아가면 성검의 봉인을 풀 수 있다";
    }

    /** 문양이 갈라져 심연으로 떨어진다: 화면이 어두워진 뒤 마왕의 방으로 옮긴다. */
    public void startFall() {
        if (warping) return;
        falling = true;
        warping = true;
        fadeTarget = 1f;
        shake(0.7f);
        game.sfx.play("slam");
    }

    /** 개발용: 보스방(또는 심연) 입구로 바로 간다. 싸우는 중이거나 다른 이동 중이면 false. */
    public boolean devWarpToBoss(String dungeonId) {
        if (warping || session.fighting()) return false;
        devWarpTarget = dungeonId;
        warping = true;
        fadeTarget = 1f;
        return true;
    }

    /** 개발용 메뉴 (F1): 보스 부활, 보스방 바로 가기, 광물 무한, 무적. */
    public void toggleDevMenu() {
        if (modals.top() instanceof ForgeWindow forge && forge.isWorking()) return;
        if (modals.top() instanceof DevMenu) modals.top().close();
        else modals.push(new DevMenu(this));
        game.sfx.play("select");
    }

    public void openPause() {
        hud.closeMap();
        modals.push(new PauseMenu(this));
    }

    public void openBag() {
        hud.closeMap();
        modals.push(new BagWindow(this));
    }

    /** 이어하기 위치를 정하고 저장한다. */
    public void save(boolean announce) {
        session.prepareSave();
        boolean ok = game.save(session.state);
        if (!ok) hud.toast("저장하지 못했습니다");
        else if (announce) hud.toast("저장했다");
    }

    public void toTitle() {
        game.setScreen(new TitleScreen(game));
        dispose();
    }

    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height);
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        renderer.dispose();
        hud.dispose();
    }
}
