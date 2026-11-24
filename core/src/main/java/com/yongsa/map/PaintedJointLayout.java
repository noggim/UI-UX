package com.yongsa.map;

import com.google.gson.Gson;
import java.util.*;

/** Authored paintings spanning both sides of a specific room/passage connection. */
public final class PaintedJointLayout {
    public List<Joint> joints = new ArrayList<>();

    public static final class Joint {
        public String id, image, room;
        /** World-space lower-left rectangle and the door this painting was authored against. */
        public float[] rect, door;
        public float feather;
        /** Normalized visible floor in the new painting, including solid prop exclusions. */
        public float[] walkable;
        /** 이동 마스크 PNG (assets 아래 경로). 있으면 walkable·blocked 대신 쓴다. */
        public String walkMask;
        public List<float[]> blocked = new ArrayList<>();
        public List<SceneOcclusion.Mask> foregrounds = new ArrayList<>();

        public boolean matches(WorldMap map) {
            Room r = map.room(room);
            return r != null && r.doors.stream().anyMatch(d -> d.link != null && d.link.corridor != null
                    && Math.abs(d.footX-door[0]) < .02f && Math.abs(d.footY-door[1]) < .02f);
        }
        public float x(float u) { return rect[0]+u*rect[2]; }
        public float y(float v) { return rect[1]+(1-v)*rect[3]; }
        public float alpha(float x,float y) {
            float edge=Math.min(Math.min(x-rect[0],rect[0]+rect[2]-x),
                    Math.min(y-rect[1],rect[1]+rect[3]-y));
            float t=Math.max(0,Math.min(1,edge/feather));
            return t*t*(3-2*t);
        }
        public boolean visible(float x0,float y0,float x1,float y1) {
            return rect[0]<x1 && rect[1]<y1 && rect[0]+rect[2]>x0 && rect[1]+rect[3]>y0;
        }
        public boolean owns(float x,float y) {return alpha(x,y)>=1;}
        public boolean allowsFoot(float x,float y,float radius) {
            WalkMask mask=WalkMask.get(walkMask);
            if(mask!=null)return mask.fits((x-rect[0])/rect[2],1-(y-rect[1])/rect[3],radius/rect[2],radius/rect[3]);
            if(!RoomSceneLayout.Scene.fitsPolygon(walkable,x-rect[0],y-rect[1],radius,rect[2],rect[3],true))return false;
            for(float[] polygon:blocked)
                if(!RoomSceneLayout.Scene.fitsPolygon(polygon,x-rect[0],y-rect[1],radius,rect[2],rect[3],false))return false;
            return true;
        }
    }

    public static PaintedJointLayout parse(String json) {
        var layout=new Gson().fromJson(json,PaintedJointLayout.class);
        if(layout==null || layout.joints==null)throw new IllegalArgumentException("Missing joint paintings");
        validate(layout.joints);
        return layout;
    }
    public static void validate(List<Joint> joints) {
        Set<String> ids=new HashSet<>();
        for(var j:joints) {
            if(j==null || j.id==null || !ids.add(j.id) || j.image==null || j.room==null
                    || j.rect==null || j.rect.length!=4 || j.door==null || j.door.length!=2
                    || !Float.isFinite(j.feather) || j.feather<=0 || j.foregrounds==null || j.blocked==null)
                throw new IllegalArgumentException("Invalid joint painting");
            for(float v:j.rect)if(!Float.isFinite(v))throw new IllegalArgumentException(j.id);
            for(float v:j.door)if(!Float.isFinite(v))throw new IllegalArgumentException(j.id);
            if(j.rect[2]<=2*j.feather || j.rect[3]<=2*j.feather || j.alpha(j.door[0],j.door[1])<1)
                throw new IllegalArgumentException("Joint must cover its doorway: "+j.id);
            polygon(j.walkable);
            for(float[] p:j.blocked)polygon(p);
            if(!j.foregrounds.isEmpty()) {
                var data=new SceneOcclusion();data.version=1;data.scenes.put(j.id,j.foregrounds);
                SceneOcclusion.parse(new Gson().toJson(data));
            }
        }
    }
    private static void polygon(float[] p) {
        if(p==null || p.length<6 || p.length%2!=0)throw new IllegalArgumentException("Missing joint floor outline");
        for(float v:p)if(!Float.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("Invalid joint floor outline");
    }
}
