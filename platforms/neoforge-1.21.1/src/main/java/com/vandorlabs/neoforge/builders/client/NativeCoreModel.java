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
    private final String id;
    private final net.minecraft.client.renderer.texture.TextureAtlasSprite flat,legacy;
    private static final Map<String,net.minecraft.client.resources.model.ModelResourceLocation> KEYS;
    static{var keys=new HashMap<String,net.minecraft.client.resources.model.ModelResourceLocation>();for(String id:NativeBuilderModelCache.IDS)keys.put(id,net.minecraft.client.resources.model.ModelResourceLocation.standalone(NativeBuilderModelCache.model(id,true)));KEYS=Map.copyOf(keys);}
    private static final class Scratch {final org.joml.Vector3f position=new org.joml.Vector3f(),normal=new org.joml.Vector3f();}
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    private final net.minecraft.client.renderer.block.model.ItemTransforms transforms;
    public NativeCoreModel(BakedModel source,String id,net.minecraft.client.renderer.texture.TextureAtlasSprite flat,net.minecraft.client.renderer.texture.TextureAtlasSprite legacy){super(source);this.id=id;this.flat=flat;this.legacy=legacy;var base=source.getTransforms();var gui=new net.minecraft.client.renderer.block.model.ItemTransform(base.gui.rotation,base.gui.translation,new org.joml.Vector3f(CoreMesh.ITEM_GUI_SCALE));transforms=new net.minecraft.client.renderer.block.model.ItemTransforms(base.thirdPersonLeftHand,base.thirdPersonRightHand,base.firstPersonLeftHand,base.firstPersonRightHand,base.head,gui,base.ground,base.fixed,base.moddedTransforms);var result=new ArrayList<BakedQuad>();var palette=CoreMesh.palette(id);
        for(var face:CoreMesh.faces(id)){var sprite=face.textured?legacy:flat;int[] data=new int[32];int rgb=face.rgb(palette),color=0xff000000|(rgb&255)<<16|(rgb&0xff00)|(rgb>>>16);int normal=((int)(face.nx*127)&255)|((int)(face.ny*127)&255)<<8|((int)(face.nz*127)&255)<<16;
            for(int vertex=0;vertex<4;vertex++){int p=vertex*8;for(int axis=0;axis<3;axis++)data[p+axis]=Float.floatToRawIntBits((float)face.coordinate(vertex,axis));data[p+3]=color;data[p+4]=Float.floatToRawIntBits(sprite.getU((float)face.u(vertex)));data[p+5]=Float.floatToRawIntBits(sprite.getV((float)face.v(vertex)));data[p+7]=normal;}
            result.add(new BakedQuad(data,-1,Direction.getNearest((float)face.nx,(float)face.ny,(float)face.nz),sprite,face.textured,face.textured));
        }faces=List.copyOf(result);
    }
    /** Fetch the installed atlas generation, then draw the exact shared completed voxel layers. */
    public static void emit(String id,double progress,com.mojang.blaze3d.vertex.PoseStack.Pose pose,com.mojang.blaze3d.vertex.VertexConsumer target,int light,int overlay){var key=KEYS.get(id);if(key==null)return;var current=Minecraft.getInstance().getModelManager().getModel(key);if(current instanceof NativeCoreModel model)model.emitLayer(progress,pose,target,light,overlay);}
    private void emitLayer(double progress,com.mojang.blaze3d.vertex.PoseStack.Pose pose,com.mojang.blaze3d.vertex.VertexConsumer target,int light,int overlay){var scratch=SCRATCH.get();var palette=CoreMesh.palette(id);for(var face:CoreMesh.faces(id,progress)){var sprite=face.textured?legacy:flat;int rgb=face.fabricationRGB(palette);pose.transformNormal((float)face.nx,(float)face.ny,(float)face.nz,scratch.normal);for(int vertex=0;vertex<4;vertex++){pose.pose().transformPosition((float)face.coordinate(vertex,0),(float)face.coordinate(vertex,1),(float)face.coordinate(vertex,2),scratch.position);target.addVertex(scratch.position.x,scratch.position.y,scratch.position.z).setColor(rgb>>16&255,rgb>>8&255,rgb&255,255).setUv(sprite.getU((float)face.u(vertex)),sprite.getV((float)face.v(vertex))).setOverlay(overlay).setLight(light).setNormal(scratch.normal.x,scratch.normal.y,scratch.normal.z);}}}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return side==null?faces:List.of();}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,net.neoforged.neoforge.client.model.data.ModelData data,net.minecraft.client.renderer.RenderType layer){return side==null?faces:List.of();}
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){transforms.getTransform(context).apply(left,pose);return this;}
    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return List.of(this);}
    @Override public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms(){return transforms;}
    @Override public boolean useAmbientOcclusion(){return false;}
}
