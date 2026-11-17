package com.vandorlabs.neoforge.appliances;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.renderer.texture.OverlayTexture;
/** Actual attachment vertices must sit outside the housing at the authored control locations. */
public final class NativeApplianceRenderChecks {
 private static final class Sink implements VertexConsumer {
  final Direction facing;int reader,button,top,vertices,maxLight;
  Sink(Direction f){facing=f;}
  public VertexConsumer addVertex(float x,float y,float z){if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new AssertionError("Nonfinite appliance vertex");vertices++;double localX=switch(facing){case EAST->z;case SOUTH->1-x;case WEST->1-z;default->x;},localZ=switch(facing){case EAST->1-x;case SOUTH->1-z;case WEST->x;default->z;};if(localZ<-.001&&y>.5){if(localX>.5)reader++;else button++;}if(y>1.0001)top++;return this;}
  public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){maxLight=Math.max(maxLight,u|(v<<16));return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}
 }
 public static void run(){var mc=Minecraft.getInstance();int draws=0;for(var block:NativeApplianceBindings.BLOCKS.values())for(var facing:Direction.Plane.HORIZONTAL){var state=block.defaultBlockState().setValue(NativeApplianceBlock.FACING,facing);var tile=new NativeApplianceEntity(BlockPos.ZERO,state);var tag=new CompoundTag();tag.putInt("ApplianceVersion",1);tag.putBoolean("AppliancePublic",true);tag.putInt("ApplianceChipLogo",-1);tile.loadWithComponents(tag,mc.level.registryAccess());tile.setLevel(mc.level);if(tile.inert())throw new AssertionError("Initial public attachment tile inert");var sink=new Sink(facing);var renderer=mc.getBlockEntityRenderDispatcher().getRenderer(tile);if(renderer==null)throw new AssertionError("Missing appliance renderer");renderer.render(tile,0,new PoseStack(),layer->sink,0xf000f0,OverlayTexture.NO_OVERLAY);if(sink.reader<24||sink.button<24||block.scanner()&&sink.top==0||!block.scanner()&&sink.top!=0)throw new AssertionError("Attachment placement "+block.scanner()+"/"+facing+" vertices="+sink.vertices+" reader="+sink.reader+" button="+sink.button+" top="+sink.top);draws++;}System.out.println("[vandorlabs][appliances] attachments PASS draws="+draws+" actualReaderAndHalfButtonOutsideFront/TopFrameScannerOnly/all4Facings/initialPublicLoad");}
 public static void placed(NativeApplianceEntity tile){var mc=Minecraft.getInstance();var sink=new Sink(tile.getBlockState().getValue(NativeApplianceBlock.FACING));mc.getBlockEntityRenderDispatcher().getRenderer(tile).render(tile,0,new PoseStack(),layer->sink,net.minecraft.client.renderer.LevelRenderer.getLightColor(tile.getLevel(),tile.getBlockPos()),OverlayTexture.NO_OVERLAY);if((sink.maxLight&0xffff0000)==0)throw new AssertionError("Outside appliance widgets inherit dark opaque housing light: "+sink.maxLight);}
 private NativeApplianceRenderChecks(){}
}
