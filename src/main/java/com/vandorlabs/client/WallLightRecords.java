package com.vandorlabs.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.*;
import org.lwjgl.opengl.GL11;

/** Update only the captured lightmap word; preserve normals and shader payload. */
final class WallLightRecords {
    private static final BufferBuilder PROBE=new BufferBuilder(64);
    final int stride,word;private final boolean swap;
    private WallLightRecords(int stride,int word,boolean swap){this.stride=stride;this.word=word;this.swap=swap;}
    static WallLightRecords forFormat(VertexFormat format){
        if((format.getNextOffset()&3)!=0)return null;int word=-1;
        for(int i=0;i<format.getElementCount();i++){
            VertexFormatElement e=format.getElement(i);
            if(e.getUsage()==VertexFormatElement.EnumUsage.UV&&e.getIndex()==1){
                if(e.getElementCount()!=2||(e.getType()!=VertexFormatElement.EnumType.SHORT&&e.getType()!=VertexFormatElement.EnumType.USHORT)||(format.getOffset(i)&3)!=0)return null;
                word=format.getOffset(i)/4;
            }
        }
        if(word<0)return null;
        // BufferBuilder's short ordering is the oracle, including native endian
        // order. Never assume a particular host or hard-code the shader stride.
        PROBE.begin(GL11.GL_QUADS,format);int actual;
        try{PROBE.pos(0,0,0).color(255,255,255,255).tex(0,0).lightmap(0x31,0x72);if(format.hasNormal())PROBE.normal(0,1,0);PROBE.endVertex();actual=PROBE.getVertexState().getRawBuffer()[word];}
        finally{PROBE.finishDrawing();}
        int logical=0x00310072;if(actual!=logical&&actual!=Integer.rotateLeft(logical,16))return null;
        return new WallLightRecords(format.getNextOffset()/4,word,actual!=logical);
    }
    int packed(int light){return swap?Integer.rotateLeft(light,16):light;}
    void update(int[] records,int[] offsets,int[] previous,int[] current){
        for(int member=0;member<current.length;member++)if(previous[member]!=current[member]){
            int value=packed(current[member]);for(int at=offsets[member]+word;at<offsets[member+1];at+=stride)records[at]=value;
        }
    }
}
