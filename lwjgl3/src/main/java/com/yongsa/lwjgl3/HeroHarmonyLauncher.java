package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.combat.Facing;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;

/** Deterministic, developer-only real renderer captures; never reads a player's save. */
public final class HeroHarmonyLauncher {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Output directory required; optional comparison: original, nearest or pixel-mip");
        System.setProperty("yongsa.saveDir", new java.io.File(args[0], "qa-save").getAbsolutePath());
        var config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(1280, 720); config.setInitialVisible(false); config.disableAudio(true);
        config.setForegroundFPS(60); config.useVsync(false);
        new Lwjgl3Application(new Capture(args[0], args.length == 2 ? args[1] : null), config);
    }

    private static final class Capture extends YongsaGame {
        private final FileHandle output;
        private final String[] rooms = {"CAMP", "L1", "R1", "T1"};
        private final Facing[] poses = {Facing.DOWN, Facing.UP, Facing.RIGHT, Facing.DOWN, Facing.UP, Facing.RIGHT, Facing.LEFT};
        private OrthographicCamera camera;
        private int scene, pose;
        private GameSession session;
        private WorldRenderer renderer;
        private Texture originalSheet;
        private final String comparisonFilter;
        Capture(String directory, String comparisonFilter) {
            output = new FileHandle(directory); output.mkdirs(); this.comparisonFilter = comparisonFilter;
        }

        @Override public void create() {
            super.create();
            if ("original".equals(comparisonFilter)) {
                originalSheet = new Texture(Gdx.files.internal("world/hero.png"));
                originalSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
                String[] facings = {"down", "up", "side"};
                for (int i=0; i<facings.length; i++) for (int row=0; row<2; row++)
                    art.world("hero_" + facings[i] + (row == 1 ? "_walk" : "")).setRegion(
                            new com.badlogic.gdx.graphics.g2d.TextureRegion(originalSheet, 95+i*495, row == 1 ? 522 : 19, 360, 490));
            } else if (comparisonFilter != null) {
                Texture.TextureFilter min = switch (comparisonFilter) {
                    case "nearest" -> Texture.TextureFilter.Nearest;
                    case "pixel-mip" -> Texture.TextureFilter.MipMapNearestLinear;
                    default -> throw new IllegalArgumentException("Unknown comparison filter");
                };
                art.world("hero_down").getTexture().setFilter(min, Texture.TextureFilter.Nearest);
            }
            camera = new OrthographicCamera(1280,720);
            setup();
        }

        private void setup() {
            if (renderer != null) renderer.dispose();
            MathUtils.random.setSeed(99);
            var state = NewGame.create(balance);
            state.bossesDefeated.addAll(java.util.List.of("LEFT","RIGHT","TOP"));
            var map = newMap(); var room = map.room(rooms[scene]);
            state.mapVersion = com.yongsa.map.WorldMap.VERSION;
            state.resumeX = room.spawnX(balance.tileSize); state.resumeY = room.spawnY(balance.tileSize);
            session = new GameSession(balance, map, state, 42);
            renderer = new WorldRenderer(session, art, fonts, batch, shapes);
            renderer.update(0);
            camera.zoom = 2f / 3f * 100f / 115f;
            camera.position.set(Math.round(session.hero.x / camera.zoom) * camera.zoom,
                    Math.round((session.hero.y + 16) / camera.zoom) * camera.zoom, 0);
            camera.update();
        }

        @Override public void render() {
            session.hero.facing = poses[pose];
            session.hero.moving = pose >= 3 && pose <= 5;
            session.hero.walkTime = .2f;
            renderer.renderLights(camera);
            Gdx.gl.glClearColor(.02f,.018f,.03f,1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            Gdx.gl.glViewport(0,0,1280,720);
            renderer.render(camera);
            var pixels = ScreenUtils.getFrameBufferPixmap(0,0,1280,720);
            var writer = new PixmapIO.PNG(); writer.setFlipY(true);
            String name = rooms[scene].toLowerCase() + "-" + pose;
            try { writer.write(output.child(name + ".png"), pixels); }
            catch (java.io.IOException ex) { throw new RuntimeException(ex); }
            finally { writer.dispose(); pixels.dispose(); }
            System.out.println("HARMONY_CAPTURE_OK " + name);
            if (++pose < poses.length) return;
            pose = 0;
            if (++scene == rooms.length) Gdx.app.exit(); else setup();
        }

        @Override public void dispose() {
            if (renderer != null) renderer.dispose();
            if (originalSheet != null) originalSheet.dispose();
            super.dispose();
        }
    }
}
