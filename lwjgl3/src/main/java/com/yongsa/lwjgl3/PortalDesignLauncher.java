package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.yongsa.YongsaGame;
import com.yongsa.model.*;
import com.yongsa.screen.WorldRenderer;
import com.yongsa.map.*;

/** Render the shipped portal/seal states with isolated QA saves, without changing player progress. */
public final class PortalDesignLauncher {
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Output directory required");
        System.setProperty("yongsa.saveDir",new java.io.File(args[0],"qa-save").getAbsolutePath());
        var config=new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(960,640); config.setInitialVisible(false);
        config.disableAudio(true); config.useVsync(false);
        new Lwjgl3Application(new Capture(args[0]),config);
    }

    private static final class Capture extends YongsaGame {
        private final String[] names={"seal-locked","seal-one","seal-two","seal-ready","abyss-open","abyss-cleared",
                "return-mine","return-citadel","return-tower","closed-mine","closed-citadel","closed-tower"};
        private final FileHandle output;
        private GameSession session;
        private WorldRenderer renderer;
        private OrthographicCamera camera;
        private int scenario, pose, frame;
        private float centerX, centerY;
        Capture(String path){output=new FileHandle(path);output.mkdirs();}
        @Override public void create(){super.create();camera=new OrthographicCamera(640,426.6667f);setup();}
        private void setup(){
            if(renderer!=null)renderer.dispose();
            var state=NewGame.create(balance);
            state.mapVersion=WorldMap.VERSION;
            int cleared=scenario<4?scenario:3;
            for(int i=0;i<cleared;i++)state.bossesDefeated.add(AbsorbService.BOSS_ORDER[i]);
            state.unsealed=scenario==4||scenario==5;
            state.finalBossDefeated=scenario==5;
            session=new GameSession(balance,newMap(),state,42);session.monsters.clear();session.devGodMode=true;
            if(scenario<6){
                Prop seal=session.map.props.stream().filter(p->p.kind==Prop.Kind.SEAL).findFirst().orElseThrow();
                centerX=seal.centerX(balance.tileSize);centerY=seal.centerY(balance.tileSize);
            }else{
                String dungeon=AbsorbService.BOSS_ORDER[(scenario-6)%3];
                Room boss=session.map.rooms.stream().filter(r->r.type==RoomType.BOSS&&r.dungeonId.equals(dungeon)).findFirst().orElseThrow();
                state.room(boss.id).cleared=scenario<9;
                if(session.hasPortal(boss)!=(scenario<9))throw new AssertionError("Portal visibility state");
                centerX=session.portalX(boss);centerY=session.portalY(boss);
            }
            session.hero.x=centerX+55;session.hero.y=centerY-32;
            renderer=new WorldRenderer(session,art,fonts,batch,shapes);
        }
        @Override public void render(){
            session.hero.x=centerX+(pose==0?55:0);
            session.hero.y=centerY+(pose==0?-32:pose==1?0:32);
            renderer.update(1/60f);
            camera.position.set(centerX,centerY+28,0);camera.update();
            renderer.renderLights(camera);
            Gdx.gl.glClearColor(.02f,.018f,.03f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderer.render(camera);
            int error=Gdx.gl.glGetError();if(error!=GL20.GL_NO_ERROR)throw new IllegalStateException("GL error "+error);
            if(++frame<20)return;
            var pixels=ScreenUtils.getFrameBufferPixmap(0,0,960,640);
            var png=new PixmapIO.PNG();png.setFlipY(true);
            String name=names[scenario]+"-"+pose;
            try{png.write(output.child(name+".png"),pixels);}catch(java.io.IOException e){throw new RuntimeException(e);}
            finally{png.dispose();pixels.dispose();}
            System.out.println("PORTAL_CAPTURE_OK "+name);
            frame=0;
            if(++pose==3){pose=0;if(++scenario==names.length){System.out.println("PORTAL_CAPTURE_COMPLETE 36");Gdx.app.exit();return;}setup();}
        }
        @Override public void dispose(){if(renderer!=null)renderer.dispose();super.dispose();}
    }
}
