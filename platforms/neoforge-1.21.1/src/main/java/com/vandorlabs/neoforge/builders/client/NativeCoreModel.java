package com.vandorlabs.neoforge.builders.client;

import com.vandorlabs.geometry.CoreMesh;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** Identical procedural scaffold in terrain, items and the fabrication cache. */
public final class NativeCoreModel extends BakedModelWrapper<BakedModel> {
    private final List<BakedQuad> faces;
    private final net.minecraft.client.renderer.block.model.ItemTransforms transforms;
    public NativeCoreModel(BakedModel source,String id,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite){super(source);var base=source.getTransforms();var gui=new net.minecraft.client.renderer.block.model.ItemTransform(base.gui.rotation,base.gui.translation,new org.joml.Vector3f(CoreMesh.ITEM_GUI_SCALE));transforms=new net.minecraft.client.renderer.block.model.ItemTransforms(base.thirdPersonLeftHand,base.thirdPersonRightHand,base.firstPersonLeftHand,base.firstPersonRightHand,base.head,gui,base.ground,base.fixed,base.moddedTransforms);var result=new ArrayList<BakedQuad>();
        for(var face:CoreMesh.faces(id)){int[] data=new int[32];int rgb=face.rgb,color=0xff000000|(rgb&255)<<16|(rgb&0xff00)|(rgb>>>16);int normal=((int)(face.nx*127)&255)|((int)(face.ny*127)&255)<<8|((int)(face.nz*127)&255)<<16;
            for(int vertex=0;vertex<4;vertex++){int p=vertex*8;for(int axis=0;axis<3;axis++)data[p+axis]=Float.floatToRawIntBits((float)face.coordinate(vertex,axis));data[p+3]=color;data[p+4]=Float.floatToRawIntBits(sprite.getU(.5F));data[p+5]=Float.floatToRawIntBits(sprite.getV(.5F));data[p+7]=normal;}
            result.add(new BakedQuad(data,-1,Direction.getNearest((float)face.nx,(float)face.ny,(float)face.nz),sprite,true));
        }faces=List.copyOf(result);
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return side==null?faces:List.of();}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,net.neoforged.neoforge.client.model.data.ModelData data,net.minecraft.client.renderer.RenderType layer){return side==null?faces:List.of();}
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return this;}
    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return List.of(this);}
    @Override public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms(){return transforms;}
    @Override public boolean useAmbientOcclusion(){return false;}
}
