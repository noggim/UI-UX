package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.map.Room;
import com.yongsa.map.SceneOcclusion;
import java.util.*;

/** Repaint exact source pixels above actors, sorted by each object's foot position. */
final class SceneForegrounds implements Disposable {
    static final class Piece {
        final Texture texture;
        final float left,top,width,height,depth;
        Piece(Texture t,float l,float top,float w,float h,float depth) {
            texture=t;left=l;this.top=top;width=w;height=h;this.depth=depth;
        }
    }
    private static final class Cached {
        final List<Piece> pieces=new ArrayList<>();
        float used;
        void dispose(){for(Piece p:pieces)p.texture.dispose();}
    }
    private final SceneOcclusion data;
    private final PassagePainter passages;
    private final Map<String,Cached> cache=new HashMap<>();

    SceneForegrounds(PassagePainter passages) {
        this.passages = passages;
        var f=Gdx.files.internal("data/scene-occlusion.json");
        data=f.exists()?SceneOcclusion.parse(f.readString("UTF-8")):null;
    }

    List<Piece> pieces(Room room,float time) {
        if(data==null || room.scene==null || Boolean.getBoolean("yongsa.disableSceneOcclusion"))return List.of();
        List<SceneOcclusion.Mask> masks=data.scenes.get(room.scene.id);
        if(masks==null)return List.of();
        Cached entry=cache.get(room.id);
        if(entry==null) {
            entry=new Cached();
            Pixmap source=new Pixmap(Gdx.files.internal(room.scene.image));
            if (passages.active) source = passages.joinForeground(source, room);
            try {
                int w=source.getWidth(),h=source.getHeight();
                for(var mask:masks) {
                    int x0=(int)Math.floor(mask.minX()*w),y0=(int)Math.floor(mask.minY()*h);
                    int x1=Math.min(w,(int)Math.ceil(mask.maxX()*w)),y1=Math.min(h,(int)Math.ceil(mask.maxY()*h));
                    Pixmap cut=new Pixmap(x1-x0,y1-y0,Pixmap.Format.RGBA8888);
                    try {
                        cut.setBlending(Pixmap.Blending.None);
                        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
                            int pixel = source.getPixel(x,y);
                            // Feathered edges and doorway joins already blend with the ground.
                            // Repainting them would darken the blend and reveal a rectangular seam.
                            if ((pixel & 255) == 255 && mask.contains((x+0.5f)/w,(y+0.5f)/h))
                                cut.drawPixel(x-x0,y-y0,pixel);
                        }
                        Texture tex=new Texture(cut,true);
                        tex.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
                        entry.pieces.add(new Piece(tex,x0/(float)w,y0/(float)h,(x1-x0)/(float)w,(y1-y0)/(float)h,mask.depth));
                    } finally {cut.dispose();}
                }
                cache.put(room.id,entry);
            } catch(RuntimeException e) {entry.dispose();throw e;}
            finally {source.dispose();}
        }
        entry.used=time;
        return entry.pieces;
    }

    void draw(SpriteBatch batch,Room room,Piece p) {
        float x=room.originX+(room.flip?1f-p.left-p.width:p.left)*room.widthPx;
        float y=room.originY+(1f-p.top-p.height)*room.heightPx;
        batch.setColor(Color.WHITE);
        batch.draw(p.texture,x,y,p.width*room.widthPx,p.height*room.heightPx,
                0,0,p.texture.getWidth(),p.texture.getHeight(),room.flip,false);
    }

    void expire(float time) {
        var it=cache.values().iterator();
        while(it.hasNext()){Cached c=it.next();if(time-c.used>20f){c.dispose();it.remove();}}
    }
    public void dispose(){for(Cached c:cache.values())c.dispose();cache.clear();}
}
