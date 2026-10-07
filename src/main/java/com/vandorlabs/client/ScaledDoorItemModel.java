package com.vandorlabs.client;

import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ItemTransformVec3f;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.lwjgl.util.vector.Vector3f;

/** Centers the two-block door geometry within its item transforms. */
public final class ScaledDoorItemModel implements IBakedModel {
    private static final ItemTransformVec3f HAND = new ItemTransformVec3f(
            new Vector3f(0, 0, 0), new Vector3f(0, -.25F, 0),
            new Vector3f(.5F, .5F, .5F));
    private static final ItemTransformVec3f GUI = new ItemTransformVec3f(
            new Vector3f(15, 205, 0), new Vector3f(0, -.25F, 0),
            new Vector3f(.5F, .5F, .5F));
    private static final ItemCameraTransforms TRANSFORMS = new ItemCameraTransforms(
            HAND, HAND, HAND, HAND, ItemTransformVec3f.DEFAULT, GUI,
            HAND, HAND);

    private final IBakedModel original;
    private final ItemCameraTransforms transforms;

    public ScaledDoorItemModel(IBakedModel original) { this(original,false); }
    public ScaledDoorItemModel(IBakedModel original,boolean padded) {
        this.original=original;
        ItemTransformVec3f gui=padded?new ItemTransformVec3f(new Vector3f(15,205,0),new Vector3f(0,-.15F,0),new Vector3f(.4F,.4F,.4F)):GUI;
        transforms=padded?new ItemCameraTransforms(HAND,HAND,HAND,HAND,ItemTransformVec3f.DEFAULT,gui,HAND,HAND):TRANSFORMS;
    }
    boolean stableQuads(){return DoorQuadPlan.stableQuads(original);}

    @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing side,
            long rand) { return original.getQuads(state, side, rand); }
    @Override public boolean isAmbientOcclusion() { return original.isAmbientOcclusion(); }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean isBuiltInRenderer() { return original.isBuiltInRenderer(); }
    @Override public TextureAtlasSprite getParticleTexture() {
        return original.getParticleTexture();
    }
    @Override public ItemOverrideList getOverrides() { return original.getOverrides(); }
    @Override public ItemCameraTransforms getItemCameraTransforms() { return transforms; }
}
