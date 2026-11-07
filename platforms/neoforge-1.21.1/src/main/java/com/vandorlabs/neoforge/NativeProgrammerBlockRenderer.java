package com.vandorlabs.neoforge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.programmer.*;
import com.vandorlabs.neoforge.programmer.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
/** Sole four-cell owner. Mesh and public token/journal snapshots contain no physical item lookup. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NativeProgrammerBlockRenderer implements BlockEntityRenderer<NativeProgrammerEntity> {
 private static final java.util.Set<NativeProgrammerBlockRenderer> INSTANCES=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
 @net.neoforged.bus.api.SubscribeEvent public static void unloaded(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())clearAll();}
 public static void clearAll(){for(var renderer:INSTANCES)renderer.clear();}
 private static final Matrix4f[][][] DOCKS=new Matrix4f[6][3][2];
 private static final class Tokens{static final ItemStack[][] VALUES=create();private static ItemStack[][] create(){var result=new ItemStack[2][7];for(int chip=0;chip<2;chip++)for(int company=0;company<7;company++)result[chip][company]=chip==1?NativeCredentialItems.dataChip(company):NativeCredentialItems.accessCard(company);return result;}}
 static{for(Direction face:Direction.Plane.HORIZONTAL){var layout=NativeProgrammerLayout.of(face);var a=layout.transform();for(int slot=0;slot<3;slot++)for(int chip=0;chip<2;chip++){double x=slot==0?(chip==1?14.54:16.3):slot==1?28:4,z=slot==0?10.05:chip==1?10.26:8.75;var poses=new PoseStack();poses.translate(layout.originX()+a.x(x/16,z/16),16.28/16,layout.originZ()+a.z(x/16,z/16));poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-face.toYRot()+180));poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));if(slot==0)poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));float scale=chip==1?.29F:.45F;poses.scale(scale,scale,.45F);DOCKS[face.get3DDataValue()][slot][chip]=new Matrix4f(poses.last().pose());}}}
 private static final class Scratch{final PoseStack poses=new PoseStack();final net.minecraft.core.BlockPos.MutableBlockPos sample=new net.minecraft.core.BlockPos.MutableBlockPos();}
 private final Scratch scratch=new Scratch();
 private NativeProgrammerBake observed;
 private NativeFlatWallModel.Bake observedMaterials;
 private NativeProgrammerJournalRenderer journal;
 public NativeProgrammerBlockRenderer(BlockEntityRendererProvider.Context context){INSTANCES.add(this);}
 public void clear(){if(journal!=null)journal.clear();journal=null;observed=null;observedMaterials=null;}
 @Override public void render(NativeProgrammerEntity owner,float partial,PoseStack poses,MultiBufferSource buffers,int passedLight,int overlay){com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();var bake=NativeProgrammerResources.currentBake();var visual=owner.visual();if(bake==null||visual.inert()||owner.isRemoved()||owner.getLevel()==null||!(owner.getBlockState().getBlock() instanceof NativeProgrammerBlock)||owner.getBlockState().getValue(NativeProgrammerBlock.PART)!=0)return;Direction face=owner.getBlockState().getValue(NativeProgrammerBlock.FACING);var layout=NativeProgrammerLayout.of(face);var materials=NativePanelPlaybackRenderer.currentBake();if(observed!=bake||observedMaterials!=materials){clear();observed=bake;observedMaterials=materials;}int light=passedLight;var sample=scratch.sample.setWithOffset(owner.getBlockPos(),layout.originX(),1,layout.originZ());if(owner.getLevel().hasChunkAt(sample))light=LevelRenderer.getLightColor(owner.getLevel(),sample);NativeProgrammerMesh.emit(bake,visual.status(),face,0,poses.last(),buffers.getBuffer(NativePanelPlaybackRenderer.frameRenderType()),layout.originX(),0,layout.originZ(),light,overlay,1);
  for(int slot=0;slot<Math.min(3,visual.slots().size());slot++){var token=visual.slots().get(slot);if(token.kind()<1||token.kind()>2||token.company()<0||token.company()>6)continue;var target=scratch.poses;target.last().pose().set(poses.last().pose());target.last().normal().set(poses.last().normal());target.mulPose(DOCKS[face.get3DDataValue()][slot][token.kind()-1]);Minecraft.getInstance().getItemRenderer().renderStatic(Tokens.VALUES[token.kind()-1][token.company()],ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,target,buffers,owner.getLevel(),0);}
  if(visual.journal()!=null){if(journal==null){if(materials!=null)journal=new NativeProgrammerJournalRenderer(Minecraft.getInstance().font,materials);}if(journal!=null){var target=scratch.poses;target.last().pose().set(poses.last().pose());target.last().normal().set(poses.last().normal());target.translate(layout.originX(),0,layout.originZ());journal.render(visual.journal(),face,target,buffers);}}
 }
 @Override public boolean shouldRenderOffScreen(NativeProgrammerEntity owner){return true;}
 @Override public int getViewDistance(){return 64;}
 @Override public AABB getRenderBoundingBox(NativeProgrammerEntity owner){var layout=NativeProgrammerLayout.of(owner.getBlockState().hasProperty(NativeProgrammerBlock.FACING)?owner.getBlockState().getValue(NativeProgrammerBlock.FACING):owner.visual().facing());var p=owner.getBlockPos();double x=p.getX()+layout.originX(),z=p.getZ()+layout.originZ();return new AABB(x,p.getY(),z,x+layout.width(),p.getY()+2,z+layout.depth());}
}
