package com.vandorlabs.client;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** Native leaf bounds and hinge quads, shared by all replacement materials. */
final class SelectedDoorGeometry implements IBakedModel {
    private final IBakedModel original;
    private final List<List<BakedQuad>> quads=new ArrayList<>(7);
    final double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
    SelectedDoorGeometry(IBakedModel original) {
        this.original=original;
        // These native OBJ item models are independent of state and random seed.
        for(int side=-1;side<6;side++) {
            List<BakedQuad> filtered=new ArrayList<>();
            for(BakedQuad quad:original.getQuads(null,side<0?null:EnumFacing.getFront(side),42))
                if(hinge(quad) || quad.getFace().getAxis()!=EnumFacing.Axis.Z)filtered.add(quad);
            quads.add(Collections.unmodifiableList(filtered));
        }
        for(int side=-1;side<6;side++)for(BakedQuad quad:original.getQuads(null,side<0?null:EnumFacing.getFront(side),0)) {
            if(hinge(quad))continue;
            int[] data=quad.getVertexData();int stride=data.length/4;
            for(int v=0;v<4;v++)for(int axis=0;axis<3;axis++){double value=Float.intBitsToFloat(data[v*stride+axis]);bounds[axis]=Math.min(bounds[axis],value);bounds[axis+3]=Math.max(bounds[axis+3],value);}
        }
        if(!Double.isFinite(bounds[0]))throw new IllegalStateException("Door model has no material surface");
    }
    private static boolean hinge(BakedQuad quad){return quad.getSprite().getIconName().endsWith("/hinge");}
    public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long seed){return quads.get(side==null?0:side.getIndex()+1);}
    public boolean isAmbientOcclusion(){return original.isAmbientOcclusion();}
    public boolean isGui3d(){return original.isGui3d();}
    public boolean isBuiltInRenderer(){return false;}
    public TextureAtlasSprite getParticleTexture(){return original.getParticleTexture();}
    public ItemCameraTransforms getItemCameraTransforms(){return original.getItemCameraTransforms();}
    public ItemOverrideList getOverrides(){return ItemOverrideList.NONE;}
}
