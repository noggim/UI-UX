package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.model.GameSession;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import java.util.*;

/** Permanent northern doorway with the same theme barriers used at dungeon entrances. */
final class BossReturnDoors implements Disposable {
    private final GameSession game;
    private Texture arch;
    private final Map<String,Texture> gates = new HashMap<>();
    private final Map<Room,Float> closure = new IdentityHashMap<>();
    BossReturnDoors(GameSession game) {this.game=game;}
    void update(float dt) {
        for(Room room:game.map.rooms) if(room.type==RoomType.BOSS) {
            float target=game.hasPortal(room)?0:1;
            float before=closure.getOrDefault(room,target);
            closure.put(room,before+MathUtils.clamp(target-before,-dt/.55f,dt/.35f));
        }
    }
    void draw(SpriteBatch batch,Room room) {
        if(arch==null){arch=new Texture(Gdx.files.internal("world/portals/return-door.png"),true);arch.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);}
        float x=game.portalX(room),y=game.portalY(room),size=180;
        batch.setColor(Color.WHITE);
        batch.draw(arch,x-size/2,y-size*.16f,size,size);
        float close=closure.getOrDefault(room,game.hasPortal(room)?0f:1f);
        if(close<=0)return;
        Texture gate=gates.computeIfAbsent(room.dungeonId,key->{Texture t=new Texture(Gdx.files.internal("sprites/gate_"+key+".png"),true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return t;});
        float width=size*.43f,height=width*gate.getHeight()/gate.getWidth();
        float reveal=close*close*(3-2*close);
        // Lower the existing barrier into the sill; UV clipping avoids stretching its bars.
        batch.draw(gate,x-width/2,y+8,width,height*reveal,0,1-reveal,1,1);
    }
    public void dispose(){if(arch!=null)arch.dispose();for(Texture t:gates.values())t.dispose();}
}
