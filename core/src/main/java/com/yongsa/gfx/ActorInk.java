package com.yongsa.gfx;
import com.yongsa.combat.Actor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** A restrained silhouette edge in world pixels; source art, feet and lighting stay intact. */
public final class ActorInk implements Disposable {
    private static final float EDGE = .8f;
    private final ShaderProgram shader = new ShaderProgram("""
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_texCoords;
            void main() {
                v_color = a_color;
                v_color.a *= 255.0 / 254.0;
                v_texCoords = a_texCoord0;
                gl_Position = u_projTrans * a_position;
            }
            """, """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_texCoords;
            uniform sampler2D u_texture;
            uniform sampler2D u_other;
            uniform vec4 u_firstUV;
            uniform vec4 u_secondUV;
            uniform vec2 u_firstInset;
            uniform vec2 u_secondInset;
            uniform vec4 u_firstPlacement;
            uniform vec4 u_secondPlacement;
            uniform vec2 u_step;
            uniform float u_weight;
            uniform float u_inkStrength;
            uniform float u_shadowLift;
            vec4 pose(vec2 p) {
                // Clamp to this cell, never the neighbouring frame of an animation atlas.
                // Sample even outside the cell: a per-fragment early return makes implicit
                // mip derivatives undefined at the quad edge on desktop GPUs.
                vec2 ap = (p-u_firstPlacement.xy)/u_firstPlacement.zw;
                float inside = step(0.0,ap.x)*step(ap.x,1.0)*step(0.0,ap.y)*step(ap.y,1.0);
                vec2 aUV = clamp(ap, u_firstInset, 1.0-u_firstInset);
                vec4 a = texture2D(u_texture, u_firstUV.xy + aUV*u_firstUV.zw);
                a.a *= inside;
                if (u_weight <= 0.0) return a;
                vec2 bp = (p-u_secondPlacement.xy)/u_secondPlacement.zw;
                vec2 bUV = clamp(bp, u_secondInset, 1.0-u_secondInset);
                vec4 b = texture2D(u_other, u_secondUV.xy + bUV*u_secondUV.zw);
                b.a *= step(0.0,bp.x)*step(bp.x,1.0)*step(0.0,bp.y)*step(bp.y,1.0);
                float alpha = mix(a.a, b.a, u_weight);
                return vec4(mix(a.rgb*a.a, b.rgb*b.a, u_weight)/max(alpha, .00001), alpha);
            }
            void main() {
                vec2 p = (v_texCoords-u_firstUV.xy)/u_firstUV.zw;
                vec4 body = pose(p);
                float left = pose(p-vec2(u_step.x,0.0)).a;
                float right = pose(p+vec2(u_step.x,0.0)).a;
                float top = pose(p-vec2(0.0,u_step.y)).a;
                float bottom = pose(p+vec2(0.0,u_step.y)).a;
                float surround = max(max(left,right),max(top,bottom));
                float ink = surround*u_inkStrength*(1.0-body.a);
                float alpha = body.a+ink;
                // A small inner highlight separates dark cloth/stone from dark scenery.
                // It is shaded by the same world light map as the actor, never an emissive glow.
                float edge = clamp(body.a-min(min(left,right),min(top,bottom)),0.0,1.0);
                vec3 base = mix(body.rgb, sqrt(max(body.rgb,vec3(0.0))), u_shadowLift);
                vec3 color = mix(base, max(base,vec3(.66,.64,.59)),edge*.14);
                vec3 rgb = (color*body.a+vec3(.07,.08,.10)*ink)/max(alpha,.00001);
                gl_FragColor = v_color*vec4(rgb,alpha);
            }
            """);

    public ActorInk() {
        if (!shader.isCompiled()) throw new IllegalStateException("Actor ink shader: " + shader.getLog());
    }

    public void draw(SpriteBatch batch, TextureRegion region, float x, float y, float width, float height) {
        draw(batch, region, region, 0, x, y, width, height);
    }

    /** Blend rolling poses before finding the silhouette so their edges never double up. */
    public void draw(SpriteBatch batch, TextureRegion a, TextureRegion b, float weight,
                     float x, float y, float width, float height) {
        if (width == 0 || height == 0) return;
        boolean paintedHero = a instanceof HeroPose || b instanceof HeroPose;
        float edgeWidth = paintedHero ? .45f : EDGE;
        float dx = edgeWidth/Math.abs(width), dy = edgeWidth/Math.abs(height);
        ShaderProgram previous = batch.getShader();
        batch.setShader(shader);
        b.getTexture().bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        shader.setUniformi("u_other",1);
        shader.setUniformf("u_weight",weight);
        // Painted hero art already has a contour; retain its detail in dark rooms.
        shader.setUniformf("u_inkStrength",paintedHero ? .20f : .48f);
        shader.setUniformf("u_shadowLift",paintedHero ? .12f : 0f);
        shader.setUniformf("u_step",dx,dy);
        shader.setUniformf("u_firstUV",a.getU(),a.getV(),a.getU2()-a.getU(),a.getV2()-a.getV());
        shader.setUniformf("u_secondUV",b.getU(),b.getV(),b.getU2()-b.getU(),b.getV2()-b.getV());
        shader.setUniformf("u_firstInset",.5f/a.getRegionWidth(),.5f/a.getRegionHeight());
        shader.setUniformf("u_secondInset",.5f/b.getRegionWidth(),.5f/b.getRegionHeight());
        placement("u_firstPlacement", a);
        placement("u_secondPlacement", b);
        float du=(a.getU2()-a.getU())*dx, dv=(a.getV2()-a.getV())*dy;
        // Padding exposes the outer edge without enlarging the actual sprite.
        batch.draw(a.getTexture(),x-Math.signum(width)*edgeWidth,y-Math.signum(height)*edgeWidth,
                width+Math.signum(width)*2*edgeWidth,height+Math.signum(height)*2*edgeWidth,
                a.getU()-du,a.getV2()+dv,a.getU2()+du,a.getV()-dv);
        batch.setShader(previous);
    }

    private void placement(String uniform, TextureRegion region) {
        if (region instanceof HeroPose p) shader.setUniformf(uniform,p.left,p.top,p.width,p.height);
        else shader.setUniformf(uniform,0,0,1,1);
    }

    @Override public void dispose() { shader.dispose(); }
}
