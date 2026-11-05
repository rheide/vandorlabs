package com.vandorlabs.client;

import com.vandorlabs.render.DiagonalPortholeProjection;
import com.vandorlabs.render.PanelQuadSink;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Legacy atlas/normal adapter around the current shared sloped aperture projection. */
final class DiagonalPortholeMesh extends DiagonalPortholeProjection {
    private static final PanelQuadSink<BufferBuilder,TextureAtlasSprite> OUTPUT=new PanelQuadSink<BufferBuilder,TextureAtlasSprite>(){
        public void quad(BufferBuilder buffer,TextureAtlasSprite material,boolean reverse,double[] a,double[] b,double[] c,double[] d){WorldSurface.quad(buffer,material,reverse,a,b,c,d);}
    };
    DiagonalPortholeMesh(int mode,boolean inverted,double lower,double upper,double[] bounds){this(mode,inverted,lower,upper,bounds,null);}
    DiagonalPortholeMesh(int mode,boolean inverted,double lower,double upper,double[] bounds,com.vandorlabs.blocks.BlockProgrammableWall.Corner corner){super(mode,inverted,lower,upper,bounds,corner);}
    static void quad(BufferBuilder buffer,TextureAtlasSprite material,boolean textured,DiagonalPortholeMesh mesh,double[]... vertices){quad(buffer,material,textured,mesh,false,vertices);}
    static void quad(BufferBuilder buffer,TextureAtlasSprite material,boolean textured,DiagonalPortholeMesh mesh,boolean reverse,double[]... vertices){
        // Preserve the existing flat fast path without adding a target varargs array.
        if(mesh==null&&vertices.length==4){WorldSurface.quad(buffer,material,reverse,vertices);return;}
        DiagonalPortholeProjection.quad(OUTPUT,buffer,material,mesh,reverse,vertices);
    }
}
