package com.yongsa.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.gfx.Art;
import com.yongsa.map.RoomSceneLayout;

/**
 * Standalone asset/layout preview. No GameSession, saves, combat or room links are modified.
 * java -cp yongsa-game-0.1.0.jar com.yongsa.lwjgl3.RoomScenePreviewLauncher
 * Add --capture <folder> for deterministic hidden GL captures of all four scenes.
 */
public final class RoomScenePreviewLauncher {
    public static void main(String[] args) {
        boolean capture = args.length == 2 && "--capture".equals(args[0]);
        if (args.length != 0 && !capture) throw new IllegalArgumentException("Use --capture <folder> or no arguments");
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Yongsa - natural room integration preview");
        config.setWindowedMode(1280, 720);
        config.setInitialVisible(!capture);
        config.disableAudio(true);
        config.setForegroundFPS(60);
        config.useVsync(!capture);
        new Lwjgl3Application(new Preview(capture ? args[1] : null), config);
    }

    private static final class Preview extends ApplicationAdapter {
        private final String captureDirectory;
        private RoomSceneLayout layouts;
        private RoomSceneLayout.Scene scene;
        private final OrthographicCamera camera = new OrthographicCamera();
        private FitViewport viewport;
        private SpriteBatch batch;
        private ShapeRenderer lines;
        private BitmapFont font;
        private Art art;
        private Texture background;
        private int sceneIndex, frame, direction = 0, captures;
        private float worldWidth, worldHeight, x, y, time;
        private boolean walking, debug;
        private final Matrix4 hudMatrix = new Matrix4();

        Preview(String captureDirectory) { this.captureDirectory = captureDirectory; }

        @Override public void create() {
            layouts = RoomSceneLayout.parse(Gdx.files.internal("data/natural-room-scenes.json").readString("UTF-8"));
            batch = new SpriteBatch();
            lines = new ShapeRenderer();
            font = new BitmapFont();
            art = new Art();
            select(0);
        }

        private void select(int index) {
            sceneIndex = Math.floorMod(index, layouts.scenes.size());
            scene = layouts.scenes.get(sceneIndex);
            if (background != null) background.dispose();
            background = new Texture(Gdx.files.internal(scene.image), false);
            background.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            if (background.getWidth() != scene.pixelWidth || background.getHeight() != scene.pixelHeight)
                throw new IllegalStateException("Image geometry changed: " + scene.id);
            worldWidth = scene.suggestedWidthTiles * layouts.referenceTileSize;
            worldHeight = scene.height(worldWidth);
            viewport = new FitViewport(worldWidth, worldHeight, camera);
            viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
            x = scene.x(scene.playerSpawn[0], 0, worldWidth);
            y = scene.y(scene.playerSpawn[1], 0, worldWidth);
            direction = 1;
            time = 0;
        }

        @Override public void resize(int width, int height) {
            if (viewport != null) viewport.update(width, height, true);
        }

        private void input(float delta) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
            for (int i = 0; i < 4; i++)
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) select(i);
            if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) debug = !debug;
            if (Gdx.input.isKeyJustPressed(Input.Keys.R)) select(sceneIndex);
            float dx = 0, dy = 0;
            if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) dx--;
            if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx++;
            if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) dy++;
            if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) dy--;
            walking = dx != 0 || dy != 0;
            if (!walking) return;
            direction = Math.abs(dx) > 0 ? (dx < 0 ? 2 : 3) : (dy > 0 ? 1 : 0);
            float length = (float) Math.hypot(dx, dy);
            // Preview-only walking speed. Substeps prevent crossing thin boundaries after a slow frame.
            float distance = 140 * delta;
            int steps = Math.max(1, (int) Math.ceil(distance / 2));
            float sx = dx / length * distance / steps, sy = dy / length * distance / steps;
            for (int i = 0; i < steps; i++) {
                if (scene.allowsFoot(x + sx, y, layouts.footRadiusWorld, 0, 0, worldWidth)) x += sx;
                if (scene.allowsFoot(x, y + sy, layouts.footRadiusWorld, 0, 0, worldWidth)) y += sy;
            }
        }

        @Override public void render() {
            float delta = Math.min(Gdx.graphics.getDeltaTime(), .1f);
            time += delta;
            if (captureDirectory == null) input(delta);
            else {
                walking = false;
                debug = frame >= 5;
            }
            ScreenUtils.clear(.015f, .02f, .03f, 1);
            viewport.apply();
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            batch.setColor(Color.WHITE);
            batch.draw(background, 0, 0, worldWidth, worldHeight);
            drawHero();
            batch.end();
            if (debug) drawDebug();
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            hudMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            batch.setProjectionMatrix(hudMatrix);
            batch.begin();
            batch.setColor(.015f, .02f, .03f, .86f);
            batch.draw(art.white(), 12, Gdx.graphics.getHeight() - 46, 655, 32);
            batch.setColor(Color.WHITE);
            font.draw(batch, "ROOM PREVIEW  |  " + scene.id + "  |  1-4 room   WASD move   TAB bounds   R reset",
                    22, Gdx.graphics.getHeight() - 25);
            batch.end();
            if (captureDirectory != null) {
                if (frame == 4) capture(scene.id + "-java");
                if (frame == 8) capture(scene.id + "-bounds");
                if (++frame == 10) {
                    frame = 0;
                    if (sceneIndex + 1 < layouts.scenes.size()) select(sceneIndex + 1);
                    else { System.out.println("SCENE_PREVIEW_OK captures=" + captures); Gdx.app.exit(); }
                }
            }
        }

        private void drawHero() {
            TextureRegion shadow = art.world("shadow");
            batch.setColor(1, 1, 1, .5f);
            batch.draw(shadow, x - 13, y - 5, 26, 10);
            batch.setColor(Color.WHITE);
            boolean walkFrame = walking && ((int) (time * 8)) % 2 == 1;
            String name = direction == 1 ? "hero_up" : direction == 0 ? "hero_down" : "hero_side";
            TextureRegion hero = art.world(name + (walkFrame ? "_walk" : ""));
            float w = art.width(hero) * Art.SCALE, h = art.height(hero) * Art.SCALE;
            if (direction == 1) drawSword();
            float bob = walkFrame ? 2 : 0;
            if (direction == 2) batch.draw(hero, x + w / 2, y - 2 + bob, -w, h);
            else batch.draw(hero, x - w / 2, y - 2 + bob, w, h);
            if (direction != 1) drawSword();
        }

        private void drawSword() {
            TextureRegion sword = art.worldSword("holy_sealed", false);
            float angle = direction == 2 ? 125 : direction == 3 ? 55 : direction == 1 ? 100 : 75;
            float hx = x + (direction == 2 ? -8 : 8), hy = y + 16;
            batch.draw(sword, hx - 6, hy - 6, 6, 6, 32, 32, 1, 1, angle - 45);
        }

        private void drawDebug() {
            lines.setProjectionMatrix(camera.combined);
            lines.begin(ShapeRenderer.ShapeType.Line);
            lines.setColor(.3f, 1f, .6f, 1);
            drawPolygon(scene.walkable);
            lines.circle(x, y, layouts.footRadiusWorld, 24);
            lines.setColor(1f, .25f, .35f, 1);
            for (float[] polygon : scene.blocked) drawPolygon(polygon);
            lines.setColor(.25f, .8f, 1, 1);
            for (RoomSceneLayout.Portal p : scene.portals)
                lines.circle(scene.x(p.foot[0], 0, worldWidth), scene.y(p.foot[1], 0, worldWidth), 8, 20);
            lines.setColor(1, .5f, .3f, 1);
            for (float[] p : scene.enemySpawns)
                lines.circle(scene.x(p[0], 0, worldWidth), scene.y(p[1], 0, worldWidth), 8, 20);
            lines.end();
        }

        private void drawPolygon(float[] polygon) {
            int count = polygon.length / 2;
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                lines.line(scene.x(polygon[2*i], 0, worldWidth), scene.y(polygon[2*i+1], 0, worldWidth),
                        scene.x(polygon[2*j], 0, worldWidth), scene.y(polygon[2*j+1], 0, worldWidth));
            }
        }

        private void capture(String name) {
            FileHandle folder = new FileHandle(captureDirectory);
            folder.mkdirs();
            Pixmap pixels = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            PixmapIO.PNG writer = new PixmapIO.PNG();
            writer.setFlipY(true);
            try { writer.write(folder.child(name + ".png"), pixels); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
            finally { writer.dispose(); pixels.dispose(); }
            captures++;
            System.out.println("CAPTURE_OK " + name);
        }

        @Override public void dispose() {
            if (background != null) background.dispose();
            if (art != null) art.dispose();
            if (batch != null) batch.dispose();
            if (lines != null) lines.dispose();
            if (font != null) font.dispose();
        }
    }
}
