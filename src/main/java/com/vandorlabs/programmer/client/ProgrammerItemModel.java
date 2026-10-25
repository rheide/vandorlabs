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
            for(int i=0;i<4;i++){int v=i*stride;data[v]=Float.floatToRawIntBits((Float.intBitsToFloat(data[v])-1)*.5F+.5F);data[v+1]=Float.floatToRawIntBits(Float.intBitsToFloat(data[v+1])*.5F);data[v+2]=Float.floatToRawIntBits((Float.intBitsToFloat(data[v+2])-.5F)*.5F+.5F);}
            quads.add(new BakedQuad(data,q.getTintIndex(),q.getFace(),q.getSprite(),q.shouldApplyDiffuseLighting(),q.getFormat()));
        }
        ItemTransformVec3f gui=new ItemTransformVec3f(new Vector3f(25,225,0),new Vector3f(),new Vector3f(1,1,1));double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(BakedQuad q:quads){int[] d=q.getVertexData();for(int i=0;i<4;i++){int v=i*d.length/4;double x=Float.intBitsToFloat(d[v])-.5,y=Float.intBitsToFloat(d[v+1])-.5,z=Float.intBitsToFloat(d[v+2])-.5;double ry=Math.toRadians(225),rx=Math.toRadians(25);double px=x*Math.cos(ry)+z*Math.sin(ry),pz=z*Math.cos(ry)-x*Math.sin(ry),py=y*Math.cos(rx)-pz*Math.sin(rx);minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);}}
        float scale=(float)(.76/Math.max(maxX-minX,maxY-minY));gui=new ItemTransformVec3f(gui.rotation,new Vector3f((float)(-(minX+maxX)*scale/2),(float)(-(minY+maxY)*scale/2),0),new Vector3f(scale,scale,scale));
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
