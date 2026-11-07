package com.vandorlabs.programmer.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import java.util.*;
import org.lwjgl.util.vector.Vector3f;

/** The authored empty console, fitted after GUI rotation with visible slot padding. */
final class ProgrammerItemModel implements IBakedModel {
    private final List<BakedQuad> quads=new ArrayList<>();
    private final TextureAtlasSprite particle;
    private final ItemCameraTransforms transforms;
    ProgrammerItemModel(IBakedModel source){
        particle=source.getParticleTexture();
        for(EnumFacing face:new EnumFacing[]{null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST})for(BakedQuad q:source.getQuads(null,face,0)){
            int[] data=q.getVertexData().clone();int stride=data.length/4;
            com.vandorlabs.programmer.ProgrammerItemGeometry.normalize(data,stride);
            quads.add(new BakedQuad(data,q.getTintIndex(),q.getFace(),q.getSprite(),q.shouldApplyDiffuseLighting(),q.getFormat()));
        }
        com.vandorlabs.programmer.ProgrammerItemGeometry.Bounds bounds=new com.vandorlabs.programmer.ProgrammerItemGeometry.Bounds();
        for(BakedQuad q:quads)bounds.include(q.getVertexData(),q.getVertexData().length/4);
        float[] values=bounds.gui();ItemTransformVec3f gui=new ItemTransformVec3f(new Vector3f(values[0],values[1],values[2]),new Vector3f(values[3],values[4],values[5]),new Vector3f(values[6],values[7],values[8]));
        transforms=new ItemCameraTransforms(ItemTransformVec3f.DEFAULT,ItemTransformVec3f.DEFAULT,ItemTransformVec3f.DEFAULT,ItemTransformVec3f.DEFAULT,ItemTransformVec3f.DEFAULT,gui,ItemTransformVec3f.DEFAULT,ItemTransformVec3f.DEFAULT);
    }
    @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long random){return side==null?Collections.unmodifiableList(quads):Collections.emptyList();}
    @Override public boolean isAmbientOcclusion(){return false;}
    @Override public boolean isGui3d(){return true;}
    @Override public boolean isBuiltInRenderer(){return false;}
    @Override public TextureAtlasSprite getParticleTexture(){return particle;}
    @Override public ItemCameraTransforms getItemCameraTransforms(){return transforms;}
    @Override public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
}
