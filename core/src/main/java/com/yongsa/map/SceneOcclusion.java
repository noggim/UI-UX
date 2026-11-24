package com.yongsa.map;

import com.google.gson.Gson;
import java.util.*;

/** Traced silhouettes in the original artwork; depth is the object's ground contact, not its top. */
public final class SceneOcclusion {
    public int version;
    public Map<String, List<Mask>> scenes = new LinkedHashMap<>();

    public static final class Mask {
        public String id;
        public float depth;
        public float[] polygon;
        /** Region bounds stay in original image coordinates even when the room is mirrored. */
        public float minX() { float v=1; for(int i=0;i<polygon.length;i+=2)v=Math.min(v,polygon[i]); return v; }
        public float maxX() { float v=0; for(int i=0;i<polygon.length;i+=2)v=Math.max(v,polygon[i]); return v; }
        public float minY() { float v=1; for(int i=1;i<polygon.length;i+=2)v=Math.min(v,polygon[i]); return v; }
        public float maxY() { float v=0; for(int i=1;i<polygon.length;i+=2)v=Math.max(v,polygon[i]); return v; }
        public boolean contains(float x,float y) {
            boolean inside=false;
            for(int i=0,j=polygon.length-2;i<polygon.length;j=i,i+=2) {
                float xi=polygon[i],yi=polygon[i+1],xj=polygon[j],yj=polygon[j+1];
                if((yi>y)!=(yj>y) && x<(xj-xi)*(y-yi)/(yj-yi)+xi)inside=!inside;
            }
            return inside;
        }
    }

    public static SceneOcclusion parse(String json) {
        SceneOcclusion data=new Gson().fromJson(json,SceneOcclusion.class);
        if(data==null || data.version!=1 || data.scenes==null)throw new IllegalArgumentException("Invalid scene occlusion manifest");
        for(var scene:data.scenes.entrySet()) {
            if(scene.getValue()==null || scene.getValue().isEmpty())throw new IllegalArgumentException("Empty occlusion scene: "+scene.getKey());
            Set<String> ids=new HashSet<>();
            for(Mask mask:scene.getValue()) {
                if(mask==null || mask.id==null || !ids.add(mask.id) || mask.polygon==null || mask.polygon.length<6 || mask.polygon.length%2!=0
                        || !Float.isFinite(mask.depth) || mask.depth<0 || mask.depth>1)throw new IllegalArgumentException("Invalid occluder: "+scene.getKey());
                for(float v:mask.polygon)if(!Float.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("Occluder outside image: "+mask.id);
                if(mask.minX()>=mask.maxX() || mask.minY()>=mask.maxY() || mask.depth<mask.minY() || mask.depth>mask.maxY()+0.04f)
                    throw new IllegalArgumentException("Invalid occluder footprint: "+mask.id);
            }
        }
        return data;
    }
}
