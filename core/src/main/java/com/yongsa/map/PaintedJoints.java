package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.map.*;
import java.util.*;

/** New artwork, drawn once over both original scenes, before actors and locked gates. */
final class PaintedJoints implements Disposable {
    static final class Front {
        final Texture texture;
        final float x,y,width,height,depth;
        Front(Texture texture,float x,float y,float width,float height,float depth) {
            this.texture=texture;this.x=x;this.y=y;this.width=width;this.height=height;this.depth=depth;
        }
        void draw(SpriteBatch batch) {batch.setColor(Color.WHITE);batch.draw(texture,x,y,width,height);}
    }
    private static final class Painting {
        final PaintedJointLayout.Joint joint;
        Texture texture;
        final List<Front> fronts=new ArrayList<>();
        Painting(PaintedJointLayout.Joint joint){this.joint=joint;}
    }
    private final List<Painting> paintings=new ArrayList<>();

    PaintedJoints(WorldMap map) {
        if(Boolean.getBoolean("yongsa.disablePaintedJoints"))return;
        for(var j:map.paintedJoints)paintings.add(new Painting(j));
    }

    float originalForegroundAlpha(float x,float y) {
        float alpha=1;
        for(var p:paintings)alpha*=1-p.joint.alpha(x,y);
        return alpha;
    }

    void draw(SpriteBatch batch,float x0,float y0,float x1,float y1) {
        batch.setColor(Color.WHITE);
        // A full-map tour must not retain every high-resolution joint in VRAM.
        // Keep a small margin so walking back over an edge does not reload it.
        for(var p:paintings)if(p.texture!=null && !p.joint.visible(x0-512,y0-512,x1+512,y1+512))
            unload(p);
        for(var p:paintings)if(p.joint.visible(x0,y0,x1,y1)) {
            load(p);float[] r=p.joint.rect;batch.draw(p.texture,r[0],r[1],r[2],r[3]);
        }
    }

    List<Front> fronts(float x0,float y0,float x1,float y1) {
        var result=new ArrayList<Front>();
        for(var p:paintings)if(p.joint.visible(x0,y0,x1,y1)){load(p);result.addAll(p.fronts);}
        return result;
    }

    private void load(Painting p) {
        if(p.texture!=null)return;
        var j=p.joint;Pixmap input=new Pixmap(Gdx.files.internal(j.image));
        Pixmap src=new Pixmap(input.getWidth(),input.getHeight(),Pixmap.Format.RGBA8888);
        src.setBlending(Pixmap.Blending.None);src.drawPixmap(input,0,0);input.dispose();
        try {
            int w=src.getWidth(),h=src.getHeight();var pixels=src.getPixels();
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                int i=(y*w+x)*4+3;
                pixels.put(i,(byte)Math.round((pixels.get(i)&255)*j.alpha(j.x((x+.5f)/w),j.y((y+.5f)/h))));
            }
            p.texture=texture(src);
            for(var mask:j.foregrounds) {
                int x0=(int)(mask.minX()*w),y0=(int)(mask.minY()*h);
                int x1=Math.min(w,(int)Math.ceil(mask.maxX()*w)),y1=Math.min(h,(int)Math.ceil(mask.maxY()*h));
                Pixmap cut=new Pixmap(x1-x0,y1-y0,Pixmap.Format.RGBA8888);
                try {
                    cut.setBlending(Pixmap.Blending.None);
                    for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
                        int pixel=src.getPixel(x,y);
                        if((pixel&255)==255 && mask.contains((x+.5f)/w,(y+.5f)/h)) {
                            float opacity=1,wx=j.x((x+.5f)/w),wy=j.y((y+.5f)/h);
                            for(int k=paintings.indexOf(p)+1;k<paintings.size();k++)
                                opacity*=1-paintings.get(k).joint.alpha(wx,wy);
                            cut.drawPixel(x-x0,y-y0,(pixel&0xffffff00)|Math.round(255*opacity));
                        }
                    }
                    p.fronts.add(new Front(texture(cut),j.x(x0/(float)w),j.y(y1/(float)h),
                            (x1-x0)*j.rect[2]/w,(y1-y0)*j.rect[3]/h,j.y(mask.depth)));
                }finally{cut.dispose();}
            }
        } finally {src.dispose();}
    }
    private static Texture texture(Pixmap pixels) {
        Texture t=new Texture(pixels,true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return t;
    }
    private static void unload(Painting p) {
        if(p.texture!=null){p.texture.dispose();p.texture=null;}
        for(var f:p.fronts)f.texture.dispose();
        p.fronts.clear();
    }
    public void dispose() {
        for(var p:paintings)unload(p);
        paintings.clear();
    }
}
