package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityControlledRamp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** The emitted vertices of plain iron fill must stay inside iron's atlas sprite. */
final class RampAtlasBoundsChecks {
    static int check(Minecraft mc,TileEntityControlledRamp tile){
        TextureAtlasSprite sprite=mc.getBlockRendererDispatcher().getModelForState(net.minecraft.init.Blocks.IRON_BLOCK.getDefaultState()).getParticleTexture();
        BufferBuilder b=new BufferBuilder(4096);b.begin(7,BlockSurfaceFormat.get());TEControlledRamp.FaceEmitter emitter=new TEControlledRamp.FaceEmitter(b);TEControlledRamp.MaterialFaces faces=new TEControlledRamp.MaterialFaces(tile);
        for(com.vandorlabs.ramp.RampGeometry.Box box:tile.geometry(0))TEControlledRamp.materialCuboid(emitter,box,tile,0,faces);b.finishDrawing();
        int stride=b.getVertexFormat().getNextOffset(),offset=b.getVertexFormat().getUvOffsetById(0);for(int vertex=0;vertex<b.getVertexCount();vertex++){float u=b.getByteBuffer().getFloat(vertex*stride+offset),v=b.getByteBuffer().getFloat(vertex*stride+offset+4);if(u<sprite.getMinU()-1e-6||u>sprite.getMaxU()+1e-6||v<sprite.getMinV()-1e-6||v>sprite.getMaxV()+1e-6)throw new IllegalStateException("ramp iron atlas escape at "+tile.getPos()+" vertex="+vertex+" uv="+u+","+v+" iron="+sprite.getMinU()+".."+sprite.getMaxU()+","+sprite.getMinV()+".."+sprite.getMaxV());}
        return b.getVertexCount();
    }
    private RampAtlasBoundsChecks(){}
}
