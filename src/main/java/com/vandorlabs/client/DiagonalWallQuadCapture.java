package com.vandorlabs.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Captures the existing pixel-space emitter without GL calls or shared mutable buffers. */
final class DiagonalWallQuadCapture extends BufferBuilder {
    private final List<BakedQuad> quads=new ArrayList<>();
    private final double[][] points=new double[4][8];
    private final int turns;
    private int vertex;
    private TextureAtlasSprite sprite;
    DiagonalWallQuadCapture(EnumFacing facing){super(16);turns=((int)(180-facing.getHorizontalAngle())/90)&3;}
    void texture(TextureAtlasSprite sprite){this.sprite=sprite;}
    @Override public BufferBuilder pos(double x,double y,double z) {
        double a=x/16,c=z/16;
        points[vertex][0]=turns==1?c:turns==2?1-a:turns==3?1-c:a;
        points[vertex][1]=y/16;
        points[vertex][2]=turns==1?1-a:turns==2?1-c:turns==3?a:c;return this;
    }
    @Override public BufferBuilder tex(double u,double v){points[vertex][3]=u;points[vertex][4]=v;return this;}
    @Override public BufferBuilder color(int r,int g,int b,int a){return this;}
    @Override public BufferBuilder lightmap(int sky,int block){return this;}
    @Override public BufferBuilder normal(float x,float y,float z) {
        // Match BufferBuilder's signed-byte truncation before Forge packs the quad.
        x=(byte)(int)(x*127)/127F;y=(byte)(int)(y*127)/127F;z=(byte)(int)(z*127)/127F;
        points[vertex][5]=turns==1?z:turns==2?-x:turns==3?-z:x;
        points[vertex][6]=y;points[vertex][7]=turns==1?-x:turns==2?-z:turns==3?x:z;return this;
    }
    @Override public void endVertex() {
        if(++vertex==4) {
            add(false);add(true); // The old tile renderer explicitly disables back-face culling.
            vertex=0;
        }
    }
    private void add(boolean reverse) {
        // Baked models use the standard ITEM layout: position, color, UV and normal.
        // The vanilla block renderer copies this packed array directly into BLOCK
        // buffers and replaces its last word with lighting. A TESR layout with
        // both lightmap and normal is wider and corrupts that raw-copy path.
        VertexFormat format=net.minecraft.client.renderer.vertex.DefaultVertexFormats.ITEM;
        UnpackedBakedQuad.Builder builder=new UnpackedBakedQuad.Builder(format);
        builder.setTexture(sprite);builder.setApplyDiffuseLighting(false);
        builder.setQuadOrientation(EnumFacing.getFacingFromVector((float)points[0][5],(float)points[0][6],(float)points[0][7]));
        for(int v=0;v<4;v++) {
            double[] p=points[reverse?(4-v)&3:v];
            for(int e=0;e<format.getElementCount();e++)switch(format.getElement(e).getUsage()) {
                case POSITION:builder.put(e,(float)p[0],(float)p[1],(float)p[2],1);break;
                case COLOR:builder.put(e,1,1,1,1);break;
                case UV:if(format.getElement(e).getIndex()==0)builder.put(e,(float)p[3],(float)p[4],0,1);else builder.put(e,0,0,0,1);break;
                case NORMAL:builder.put(e,(float)p[5],(float)p[6],(float)p[7],0);break;
                default:builder.put(e);break;
            }
        }
        BakedQuad quad=builder.build();quad.getVertexData(); // Eager packing before publication to any renderer.
        quads.add(quad);
    }
    List<BakedQuad> finish(){if(vertex!=0)throw new IllegalStateException("Incomplete diagonal quad");return Collections.unmodifiableList(quads);}
}
