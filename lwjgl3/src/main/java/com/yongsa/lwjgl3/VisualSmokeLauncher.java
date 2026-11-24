package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.model.Weapon;
import com.yongsa.screen.GameScreen;
import com.yongsa.ui.BagWindow;
import com.yongsa.ui.ForgeWindow;
import com.yongsa.map.Room;

/** Explicit developer-only entry point. Saves real GL frames using an isolated save directory. */
public final class VisualSmokeLauncher {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Screenshot output directory required; optional mode: camp");
        System.setProperty("yongsa.saveDir", new java.io.File(args[0], "qa-save").getAbsolutePath());
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Yongsa visual verification");
        config.setWindowedMode(1280, 720);
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.setForegroundFPS(60);
        config.setIdleFPS(60);
        config.useVsync(false);
        new Lwjgl3Application(new Capture(args[0], args.length == 2 && "camp".equals(args[1])), config);
    }

    private static final class Capture extends YongsaGame {
        private final FileHandle output;
        private final String[] standardNames = {"01-title", "02-camp", "03-forge-craft", "04-forge-enhance",
                "05-bag", "06-map", "07-mine", "08-castle", "09-tower-boss", "10-final-boss", "11-small-window", "12-weapon-gallery", "13-awakened-forge", "14-full-inventory",
                "15-forge-success", "16-forge-keep", "17-forge-down", "18-forge-destroy", "19-forge-awaken", "20-small-forge", "21-world-weapons"};
        private int scenario, frame;
        private final boolean campOnly;
        private final String[] names;
        Capture(String directory, boolean campOnly) {
            output = new FileHandle(directory); output.mkdirs();
            this.campOnly = campOnly;
            names = campOnly ? new String[]{"camp-spawn", "camp-forge", "camp-bed", "camp-left-gate",
                    "camp-right-locked", "camp-north-locked", "camp-seal", "camp-overview"} : standardNames;
        }

        @Override public void create() {
            super.create();
            if (campOnly) setup();
        }

        @Override public void render() {
            super.render();
            if (!campOnly) exerciseInput();
            if (scenario == 14 && frame == 12) capture("forge-strike");
            if (++frame < 100) return;
            capture(names[scenario]);
            frame = 0;
            if (++scenario == names.length) { Gdx.app.exit(); return; }
            setup();
        }

        private void capture(String name) {
            Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            PixmapIO.PNG writer = new PixmapIO.PNG();
            writer.setFlipY(true);
            try { writer.write(output.child(name + ".png"), pixels); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            finally { writer.dispose(); pixels.dispose(); }
            System.out.println("CAPTURE_OK " + name);
        }

        private void exerciseInput() {
            if (!(getScreen() instanceof GameScreen gameScreen)) return;
            if (scenario >= 14 && scenario <= 18) exerciseSmithing(gameScreen);
            if (scenario == 2 && frame == 10) Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
            if (scenario == 2 && frame == 95) {
                if (gameScreen.session.state.weaponByDef("blackiron") == null || gameScreen.session.state.ore(OreType.BLACKIRON) != 172)
                    throw new AssertionError("Craft input did not update inventory and ore cost");
                System.out.println("UI_ACTION_OK craft");
            }
            if (scenario == 3 && frame == 10) {
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                if (!(gameScreen.modals.top() instanceof com.yongsa.ui.ConfirmDialog)) throw new AssertionError("Missing destruction confirmation");
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.ESCAPE);
                if (gameScreen.session.state.equipped().level != 6 || gameScreen.session.state.ore(OreType.BLACKIRON) != 180)
                    throw new AssertionError("Cancel changed weapon or ore");
                System.out.println("UI_ACTION_OK enhance-risk-cancel");
            }
            if (scenario == 4 && frame == 10) {
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.DOWN);
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                if (!"bloodmoon".equals(gameScreen.session.state.equipped().defId)) throw new AssertionError("Equip did not select bloodmoon");
                System.out.println("UI_ACTION_OK equip");
            }
            if (scenario == 13 && frame == 10) {
                int expectedUid = gameScreen.session.state.weapons.get(14).uid;
                for (int i = 0; i < 13; i++) Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.DOWN);
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                if (gameScreen.session.state.equippedUid != expectedUid) throw new AssertionError("Inventory scrolling lost keyboard selection");
                System.out.println("UI_ACTION_OK scroll-inventory");
            }
            if (scenario == 13 && frame == 20) Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.PAGE_DOWN);
        }

        private void setup() {
            if (campOnly) { setupCamp(); return; }
            if (scenario == 11 || scenario == 20) {
                Gdx.graphics.setWindowedMode(1280, 720);
                Screen previous = getScreen();
                setScreen(new com.badlogic.gdx.ScreenAdapter() {
                    @Override public void render(float delta) {
                        Gdx.gl.glClearColor(0.04f, 0.06f, 0.08f, 1);
                        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
                        batch.setProjectionMatrix(new com.badlogic.gdx.math.Matrix4().setToOrtho2D(0, 0, 1280, 720));
                        batch.begin();
                        fonts.large.setColor(com.yongsa.ui.Ui.GOLD);
                        fonts.large.draw(batch, scenario == 20 ? "인게임용 · 기본형과 각성형" : "상세 그림 · 기본형과 각성형", 40, 684);
                        String[] ids = {"holy_sealed", "blackiron", "bloodmoon", "skyfall", "holy_awakened", "blackiron", "bloodmoon", "skyfall"};
                        for (int i = 0; i < 8; i++) {
                            float x = 48 + i % 4 * 310, y = i < 4 ? 354 : 42;
                            batch.setColor(0.08f, 0.11f, 0.14f, 1);
                            batch.draw(art.white(), x - 8, y - 10, 284, 290);
                            batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
                            batch.draw(scenario == 20 ? art.worldSword(ids[i], i >= 4) : art.sword(ids[i], i >= 4), x + 15, y + 24, 236, 236);
                            fonts.normal.setColor(i < 4 ? com.yongsa.ui.Ui.TEXT : com.yongsa.ui.Ui.GOLD);
                            fonts.normal.draw(batch, i < 4 || i == 4 ? balance.weapon(ids[i]).name : balance.weapon(ids[i]).awakenedName, x + 12, y + 14);
                        }
                        batch.end();
                    }
                });
                previous.dispose();
                return;
            }
            GameState state = NewGame.create(balance);
            for (OreType ore : OreType.values()) state.addOre(ore, 180);
            if (scenario >= 3) {
                Weapon iron = state.newWeapon("blackiron"); iron.level = 6; state.equippedUid = iron.uid;
                Weapon blood = state.newWeapon("bloodmoon"); blood.level = 10; blood.awakened = true;
                Weapon sky = state.newWeapon("skyfall"); sky.level = 3;
                if (scenario == 12 || scenario == 13) { iron.level = 10; iron.awakened = true; }
                if (scenario == 18) iron.level = 9;
                if (scenario == 13) {
                    state.bossesDefeated.addAll(java.util.List.of("LEFT", "RIGHT", "TOP"));
                    state.imprints.addAll(java.util.List.of("blackiron", "bloodmoon", "skyfall"));
                    for (int i = 0; i < 12; i++) state.newWeapon("blackiron");
                }
            }
            String roomId = scenario == 6 ? "L2" : scenario == 7 ? "R1" : scenario == 8 ? "T6" : scenario == 9 ? "F" : "CAMP";
            Room target = newMap().rooms.stream().filter(r -> r.id.equals(roomId)).findFirst().orElseThrow();
            if (scenario == 9) { state.unsealed = true; state.bossesDefeated.addAll(java.util.List.of("LEFT", "RIGHT", "TOP")); }
            GameScreen next = new GameScreen(this, state);
            settings.effectSpeed = 1;
            if (scenario >= 14 && scenario <= 18) {
                var odds = next.session.enhance.odds(state.equipped().level);
                int desired = scenario == 18 ? 0 : scenario - 14;
                if (desired == 2 && odds.down <= 0) desired = 1; // 하락이 없어져서 '그대로'로 대신 찍는다
                for (long seed = 0; ; seed++) {
                    double roll = new java.util.Random(seed).nextDouble() * 100;
                    int outcome = roll < odds.success ? 0 : roll < odds.success + odds.keep ? 1
                            : roll < odds.success + odds.keep + odds.down ? 2 : 3;
                    if (outcome == desired) { next.session.random.setSeed(seed); break; }
                }
            }
            if (!"CAMP".equals(roomId)) {
                next.session.hero.x = (target.centerTileX() + 0.5f) * balance.tileSize;
                next.session.hero.y = (target.centerTileY() - 2) * balance.tileSize;
            }
            Screen previous = getScreen();
            setScreen(next);
            previous.dispose();
            if (scenario == 2 || scenario == 3 || scenario == 12 || scenario >= 14) next.modals.push(new ForgeWindow(next));
            if (scenario == 4 || scenario == 13) next.modals.push(new BagWindow(next));
            if (scenario == 5) next.hud.toggleMap();
            if (scenario == 10) { Gdx.graphics.setWindowedMode(960, 540); next.modals.push(new BagWindow(next)); }
            if (scenario == 19) Gdx.graphics.setWindowedMode(960, 540);
        }

        private void setupCamp() {
            settings.zoomNormal = scenario == 7 ? 65 : 115;
            GameState state = NewGame.create(balance);
            if (scenario == 6) state.unsealed = true;
            GameScreen next = new GameScreen(this, state);
            Room camp = next.session.map.room("CAMP");
            float[][] approach = {{.515f,.65f}, {.306f,.434f}, {.745f,.44f}, {.13f,.455f},
                    {.89f,.57f}, {.508f,.28f}, {.506f,.776f}, {.51f,.59f}};
            next.session.hero.x = camp.sceneX(approach[scenario][0]);
            next.session.hero.y = camp.sceneY(approach[scenario][1]);
            if (scenario >= 3 && scenario <= 5) {
                String name = new String[]{"LEFT", "RIGHT", "UP"}[scenario - 3];
                var door = camp.doors.stream().filter(d -> d.name.equals(name)).findFirst().orElseThrow();
                next.session.hero.x = door.arriveX; next.session.hero.y = door.arriveY;
            }
            if (!next.session.map.free(next.session.hero.x, next.session.hero.y, next.session.hero.hw, next.session.hero.hh))
                throw new AssertionError("Camp capture point is not walkable: " + names[scenario]);
            Screen previous = getScreen(); setScreen(next); previous.dispose();
        }

        private void exerciseSmithing(GameScreen screen) {
            var input = Gdx.input.getInputProcessor();
            if (frame == 10) {
                input.keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                if (!(screen.modals.top() instanceof com.yongsa.ui.ConfirmDialog)) throw new AssertionError("Missing risk dialog");
                input.keyDown(com.badlogic.gdx.Input.Keys.RIGHT);
                input.keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                ForgeWindow forge = (ForgeWindow) screen.modals.top();
                if (!forge.isWorking()) throw new AssertionError("Smithing did not start");
                input.keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                input.keyDown(com.badlogic.gdx.Input.Keys.ESCAPE);
                input.keyDown(com.badlogic.gdx.Input.Keys.F1);
                if (screen.modals.top() != forge) throw new AssertionError("Busy smithing can be interrupted");
                var tab = findButton(forge, "제작");
                var mouse = new com.badlogic.gdx.scenes.scene2d.InputEvent();
                mouse.setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchDown);
                tab.fire(mouse);
                if (!mouse.isCancelled()) throw new AssertionError("Busy mouse input is not blocked");
                if (screen.session.state.stats.enhanceTries != 0) throw new AssertionError("Charged before final strike");
            }
            if (frame == 11) ((ForgeWindow)screen.modals.top()).act(0.32f);
            if (frame == 20) {
                ForgeWindow forge = (ForgeWindow)screen.modals.top();
                forge.act(2); // dropped-frame path must still commit exactly once
                forge.keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                var state = screen.session.state;
                int expectedOre = scenario == 17 ? 181 : 177;
                if (state.stats.enhanceTries != 1 || state.ore(OreType.BLACKIRON) != expectedOre)
                    throw new AssertionError("Smithing charged the wrong number of times");
                var saved = saves.load();
                if (saved.corrupt || saved.state == null || saved.state.stats.enhanceTries != 1
                        || saved.state.ore(OreType.BLACKIRON) != expectedOre
                        || saved.state.equippedUid != state.equippedUid)
                    throw new AssertionError("Smithing result was not saved immediately");
                Weapon iron = state.weaponByDef("blackiron");
                if (scenario == 17) {
                    if (iron != null || !"holy_sealed".equals(state.equipped().defId)) throw new AssertionError("Destroy fallback missing");
                } else {
                    int level = scenario == 14 ? 7 : scenario == 15 || scenario == 16 ? 6 : 10;
                    if (iron.level != level || (scenario == 18 && !iron.awakened)) throw new AssertionError("Unexpected smithing result");
                }
                System.out.println("UI_ACTION_OK " + names[scenario] + " exactly-once, keyboard/mouse/F1 guarded");
            }
            if (frame == 95) {
                ForgeWindow forge = (ForgeWindow)screen.modals.top();
                forge.act(2);
                if (forge.isWorking()) throw new AssertionError("Result animation did not unlock");
                if (screen.session.state.stats.enhanceTries != 1) throw new AssertionError("Result rerolled");
            }
        }

        private com.badlogic.gdx.scenes.scene2d.ui.TextButton findButton(com.badlogic.gdx.scenes.scene2d.Actor actor, String text) {
            if (actor instanceof com.badlogic.gdx.scenes.scene2d.ui.TextButton b && text.contentEquals(b.getText())) return b;
            if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group group)
                for (var child : group.getChildren()) { var result = findButton(child, text); if (result != null) return result; }
            return null;
        }
    }
}
