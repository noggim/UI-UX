package com.yongsa.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** Mix poses in one draw so opaque overlapping pixels do not turn translucent. */
public final class SpriteBlend implements Disposable {
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
            uniform float u_weight;
            void main() {
                vec2 localUV = (v_texCoords - u_firstUV.xy) / u_firstUV.zw;
                vec4 a = texture2D(u_texture, v_texCoords);
                vec4 b = texture2D(u_other, u_secondUV.xy + localUV * u_secondUV.zw);
                float alpha = mix(a.a, b.a, u_weight);
                vec3 rgb = mix(a.rgb * a.a, b.rgb * b.a, u_weight) / max(alpha, 0.00001);
                gl_FragColor = v_color * vec4(rgb, alpha);
            }
            """);

    public SpriteBlend() {
        if (!shader.isCompiled()) throw new IllegalStateException("Sprite blend shader: " + shader.getLog());
    }

    public void draw(SpriteBatch batch, TextureRegion a, TextureRegion b, float weight,
                     float x, float y, float width, float height) {
        if (weight <= 0 || b == null) { batch.draw(a, x, y, width, height); return; }
        if (weight >= 1) { batch.draw(b, x, y, width, height); return; }
        ShaderProgram previous = batch.getShader();
        batch.setShader(shader);
        b.getTexture().bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        shader.setUniformi("u_other", 1);
        shader.setUniformf("u_weight", weight);
        shader.setUniformf("u_firstUV", a.getU(), a.getV(), a.getU2()-a.getU(), a.getV2()-a.getV());
        shader.setUniformf("u_secondUV", b.getU(), b.getV(), b.getU2()-b.getU(), b.getV2()-b.getV());
        batch.draw(a, x, y, width, height);
        batch.setShader(previous);
    }

    @Override public void dispose() { shader.dispose(); }
}
