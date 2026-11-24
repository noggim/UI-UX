package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.map.*;
import java.util.*;

/** Painted architecture and the sill mark open rooms; the complete barrier rises in combat. */
final class RoomGates implements Disposable {
    record Part(float depth, Runnable draw) {}
    private final WorldMap map;
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<Door, Float> closure = new IdentityHashMap<>();
    private Texture thresholdTexture;
    private TextureRegion threshold;

    RoomGates(WorldMap map) {
        this.map=map;
        for(var r:map.rooms)for(var d:r.doors)closure.put(d,d.isClosed()?1f:0f);
    }

    void update(float dt) {
        for(var r:map.rooms)for(var d:r.doors) {
            float old=closure.getOrDefault(d,d.isClosed()?1f:0f),target=d.isClosed()?1:0;
            float duration=target>old?.28f:.36f;
            closure.put(d,MathUtils.clamp(old+Math.signum(target-old)*Math.min(Math.abs(target-old),Math.max(0,dt)/duration),0,1));
        }
    }

    private String key(Door d) {
        if(d.dead() || d.room.scene==null || d.room.type==RoomType.TUNNEL)return null;
        String theme=d.room.dungeonId;
        if(d.room.type==RoomType.CAMP)theme=d.link.other(d.room).dungeonId;
        if(theme==null || !List.of("LEFT","RIGHT","TOP").contains(theme))return null;
        return "sprites/gate_"+theme+(d.horizontal()?"":"_side")+".png";
    }

    boolean supports(Door d) {return key(d)!=null;}

    /** A shallow permanent sill, drawn underneath actors and both gate states. */
    void drawThresholds(SpriteBatch batch,float x0,float y0,float x1,float y1) {
        if(threshold==null) {
            Pixmap p=new Pixmap(Gdx.files.internal("sprites/room-threshold.png"));
            int left=p.getWidth(),top=p.getHeight(),right=0,bottom=0;
            for(int y=0;y<p.getHeight();y++)for(int x=0;x<p.getWidth();x++)
                if((p.getPixel(x,y)&255)>16){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
            thresholdTexture=new Texture(p,true);p.dispose();
            thresholdTexture.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
            threshold=new TextureRegion(thresholdTexture,left,top,right-left+1,bottom-top+1);
        }
        batch.setColor(.78f,.76f,.71f,.65f);
        for(var r:map.rooms)for(var d:r.doors) {
            if(!supports(d)||r.type==RoomType.CAMP||d.footX<x0-256||d.footX>x1+256||d.footY<y0-256||d.footY>y1+256)continue;
            float width=d.halfWidth*2,height=Math.min(4,width*threshold.getRegionHeight()/threshold.getRegionWidth());
            if(d.horizontal())batch.draw(threshold,d.gateX()-width/2,d.gateY()-height/2,width,height);
            else {
                // A 90-degree rotated stone strip reads as an upright column.
                // Project a thin sill diagonally across the floor, like the gate's foot line.
                float lean=Math.min(30,width*.18f)*(d.dirX<0?1:-1);
                float ax=d.gateX()+lean,ay=d.gateY()-width/2;
                float bx=d.gateX()-lean,by=d.gateY()+width/2;
                float length=(float)Math.hypot(bx-ax,by-ay);
                float nx=-(by-ay)/length*height/2,ny=(bx-ax)/length*height/2;
                float color=batch.getPackedColor();
                batch.draw(thresholdTexture,new float[]{
                    ax-nx,ay-ny,color,threshold.getU(),threshold.getV2(),
                    ax+nx,ay+ny,color,threshold.getU(),threshold.getV(),
                    bx+nx,by+ny,color,threshold.getU2(),threshold.getV(),
                    bx-nx,by-ny,color,threshold.getU2(),threshold.getV2()},0,20);
            }
        }
        batch.setColor(Color.WHITE);
    }

    List<Part> parts(SpriteBatch batch,float x0,float y0,float x1,float y1) {
        var parts=new ArrayList<Part>();
        for(var r:map.rooms)for(var d:r.doors) {
            if(d.footX<x0-256 || d.footX>x1+256 || d.footY<y0-256 || d.footY>y1+256)continue;
            String key=key(d);if(key==null)continue;
            // Closed-gate art is not an open-frame atlas. Cropping its ends leaves
            // slices of bars/magic floating beside the already-painted piers.
            float close=closure.getOrDefault(d,d.isClosed()?1f:0f);
            if(close<=0)continue;
            Texture texture=textures.computeIfAbsent(key,k->{
                Texture t=new Texture(Gdx.files.internal(k),true);
                t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return t;
            });
            boolean side=!d.horizontal(),flip=side&&d.dirX<0;
            float span=d.halfWidth*2+map.tileSize*.5f;
            // Extend the gap between side posts, not the height of the posts themselves.
            float h=side?Math.min(180,span/.57f):span*texture.getHeight()/texture.getWidth();
            float w=side?h*texture.getWidth()/texture.getHeight():span;
            float stretch=side?Math.max(0,span-h*.57f):0;
            float x=d.gateX()-w/2,y=side?d.gateY()-span/2:d.gateY()-map.tileSize*.35f;
            if(close>0) {
                float amount=close*close*(3-2*close);
                parts.add(new Part(d.gateY(),()->{
                    batch.setColor(Color.WHITE);
                    // Reveal the existing artwork from the floor; no rescaling or new art swap.
                    for(float[] band:new float[][]{{0,.38f},{.38f,.70f},{.70f,1}})
                        drawPolygon(batch,texture,x,y,w,h,flip,stretch,
                                new float[]{band[0],1-amount,band[1],1-amount,band[1],1,band[0],1});
                }));
            }
        }
        return parts;
    }

    private static void drawPolygon(SpriteBatch batch,Texture t,float x,float y,float w,float h,boolean flip,float stretch,float[] polygon) {
        short[] indices=new EarClippingTriangulator().computeTriangles(polygon).toArray();
        float[] vertices=new float[20];
        for(int i=0;i<indices.length;i+=3) {
            for(int n=0;n<4;n++) {
                int index=indices[i+Math.min(n,2)]*2,j=n*5;
                float u=polygon[index],v=polygon[index+1];
                vertices[j]=x+(flip?1-u:u)*w;
                vertices[j+1]=y+(1-v)*h+MathUtils.clamp((u-.38f)/.32f,0,1)*stretch;
                vertices[j+2]=Color.WHITE_FLOAT_BITS;vertices[j+3]=u;vertices[j+4]=v;
            }
            batch.draw(t,vertices,0,20);
        }
    }

    public void dispose() {for(var t:textures.values())t.dispose();textures.clear();if(thresholdTexture!=null)thresholdTexture.dispose();}
}
