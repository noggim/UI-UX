package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.combat.Facing;
import com.yongsa.enemy.Monster;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;

/** Real game renderer, fixed camera/seed/poses, isolated saves. Actors are staged for comparison. */
public final class ActorScaleLauncher {
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Output directory required");
        System.setProperty("yongsa.saveDir", new java.io.File(args[0], "qa-save").getAbsolutePath());
        var config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(1280,720); config.setInitialVisible(false); config.disableAudio(true);
        config.setForegroundFPS(60); config.useVsync(false);
        new Lwjgl3Application(new Capture(args[0]), config);
    }

    private static final class Capture extends YongsaGame {
        private final FileHandle output;
        private final String[] names = {"camp", "lineup", "mine", "castle", "tower", "warning", "stunned", "death", "walk", "left", "up", "attack", "roll",
                "golem", "knight", "warden", "demonlord", "golem-slam", "golem-shield", "golem-death", "demonlord-shift", "demonlord-phase2"};
        private OrthographicCamera camera;
        private int scene;
        private GameSession session;
        private WorldRenderer renderer;
        Capture(String directory) { output = new FileHandle(directory); output.mkdirs(); }

        @Override public void create() { super.create(); camera = new OrthographicCamera(1280,720); }

        private void setup() {
            if (renderer != null) renderer.dispose();
            MathUtils.random.setSeed(99);
            var state = NewGame.create(balance);
            var map = newMap();
            String id = scene == 2 ? "L1" : scene == 3 ? "R1" : scene == 4 ? "T1" : "CAMP";
            if (scene >= 13) id = scene == 14 ? "R6" : scene == 15 ? "T6" : scene == 16 || scene >= 20 ? "F" : "L6";
            var room = map.room(id);
            state.bossesDefeated.addAll(java.util.List.of("LEFT", "RIGHT", "TOP"));
            state.mapVersion = com.yongsa.map.WorldMap.VERSION;
            state.resumeX = room.spawnX(balance.tileSize); state.resumeY = room.spawnY(balance.tileSize);
            session = new GameSession(balance, map, state, 42);
            session.monsters.clear();
            float cx = session.hero.x, cy = session.hero.y;
            if (scene >= 13) {
                var boss = new Monster("F".equals(id) ? balance.finalBoss : balance.dungeon(room.dungeonId).boss,
                        true, "F".equals(id), balance.tileSize);
                boolean placed = false;
                for (int ring=0; ring<12 && !placed; ring++) for (int dx=-ring; dx<=ring && !placed; dx++)
                    for (int dy=-ring; dy<=ring && !placed; dy++) {
                        float x=room.originX+room.widthPx*.5f+dx*balance.tileSize;
                        float y=room.originY+room.heightPx*.45f+dy*balance.tileSize;
                        if (map.free(x-75,y,9,6) && map.free(x+65,y+40,boss.hw,boss.hh)) { cx=x; cy=y; placed=true; }
                    }
                if (!placed) throw new AssertionError("No walkable boss comparison area: " + id);
                session.hero.x=cx-75; session.hero.y=cy;
                boss.x=cx+65; boss.y=cy+40; session.monsters.add(boss);
                if (scene==17) { boss.enter(Monster.State.SLAM_TELEGRAPH,1); boss.stateT=.3f; boss.slamX=boss.x; boss.slamY=boss.y; boss.slamRadius=balance.monster.bossSlamRadiusTiles*balance.tileSize; }
                if (scene==18) boss.shield=boss.shieldMax=500;
                if (scene>=20) { boss.phase2=true; if(scene==20) { boss.enter(Monster.State.PHASE_SHIFT,1); boss.stateT=.5f; } }
            }
            if (scene >= 1 && scene <= 7) {
                if (!"CAMP".equals(id)) {
                    // Use the first broad, walkable area near the room center.
                    boolean placed = false;
                    for (int ring=0; ring<10 && !placed; ring++) for (int dx=-ring; dx<=ring && !placed; dx++)
                        for (int dy=-ring; dy<=ring && !placed; dy++) {
                            float x=room.originX + room.widthPx*.5f + dx*balance.tileSize;
                            float y=room.originY + room.heightPx*.45f + dy*balance.tileSize;
                            if (map.free(x-100,y,9,6) && map.free(x,y,9,6) && map.free(x+100,y,9,6)) {
                                cx=x; cy=y; placed=true;
                            }
                        }
                    if (!placed) throw new AssertionError("No walkable comparison area: " + id);
                }
                session.hero.x=cx-150; session.hero.y=cy;
                for (int i=0; i<3; i++) {
                    var m=new Monster(balance.dungeons.get(i).mob,false,false,balance.tileSize);
                    m.x=cx-65+i*90; m.y=cy; m.hp=Math.max(1,m.maxHp*2/3);
                    if (scene==5) { m.enter(i==0 ? Monster.State.SWOOP_TELEGRAPH : i==1 ? Monster.State.TELEGRAPH : Monster.State.CAST_TELEGRAPH,1); m.stateT=.35f; m.lockX=-1; }
                    if (scene==6) m.enter(Monster.State.STUNNED,1);
                    session.monsters.add(m);
                }
                if ("CAMP".equals(id)) {
                    var boss=new Monster(balance.dungeon("LEFT").boss,true,false,balance.tileSize);
                    boss.x=cx+240; boss.y=cy; session.monsters.add(boss);
                }
            }
            session.hero.facing = scene==9 ? Facing.LEFT : scene==10 ? Facing.UP : Facing.DOWN;
            session.hero.moving=scene==8; session.hero.walkTime=.2f;
            if (scene==11) { session.hero.swingT=.12f; session.hero.aimX=1; session.hero.aimY=0; }
            if (scene==12) { session.hero.rollT=.20f; session.hero.rollX=1; }
            renderer = new WorldRenderer(session,art,fonts,batch,shapes);
            renderer.update(scene==12 ? .045f : 0);
            if (scene==7 || scene==19) {
                for (var m:session.monsters) {
                    var e=GameEvent.fx(GameEvent.Type.MONSTER_DIED,m.x,m.y);
                    e.sprite=m.def.sprite; e.boss=m.boss; e.size=m.boss?1.7f:1f;
                    renderer.onEvent(e); m.dead=true;
                }
            }
            camera.zoom=2f/3f*100f/(scene>=13 ? 100f : 115f);
            camera.position.set(Math.round(cx/camera.zoom)*camera.zoom, Math.round((cy+16)/camera.zoom)*camera.zoom,0);
            camera.update();
        }

        @Override public void render() {
            setup();
            renderer.renderLights(camera);
            Gdx.gl.glClearColor(.02f,.018f,.03f,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            Gdx.gl.glViewport(0,0,1280,720); renderer.render(camera);
            var pixels=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);
            var writer=new PixmapIO.PNG(); writer.setFlipY(true);
            try { writer.write(output.child(names[scene]+".png"),pixels); }
            catch (java.io.IOException ex) { throw new RuntimeException(ex); }
            finally { writer.dispose(); pixels.dispose(); }
            System.out.println("ACTOR_CAPTURE_OK " + names[scene]);
            if (++scene==names.length) Gdx.app.exit();
        }

        @Override public void dispose() { if (renderer!=null) renderer.dispose(); super.dispose(); }
    }
}
