package com.vandorlabs.neoforge;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;

/** Actual connected-client BER path: empty overlays and mount transforms, with caller pose preserved. */
final class StandaloneReaderWorldWarmChecks {
 private static final PoseStack POSES=new PoseStack();
 private static long vertices;
 private static final VertexConsumer DISCARD=new VertexConsumer(){public VertexConsumer addVertex(float x,float y,float z){vertices++;return this;}public VertexConsumer setColor(int r,int g,int b,int a){return this;}public VertexConsumer setUv(float u,float v){return this;}public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}public VertexConsumer setNormal(float x,float y,float z){return this;}};
 private static final MultiBufferSource BUFFERS=layer->DISCARD;
 private static void need(boolean valid,String why){if(!valid)throw new AssertionError("Reader BER warm: "+why);}
 private static void render(NativeCredentialReaderRenderer renderer,NativeCredentialReaderEntity[] samples,int count){for(int i=0;i<count;i++)for(var sample:samples)renderer.render(sample,.5F,POSES,BUFFERS,0x00F000F0,OverlayTexture.NO_OVERLAY);}
 static void run(Minecraft mc){var samples=new NativeCredentialReaderEntity[4];for(int i=0;i<4;i++){boolean chip=i>=2,mounted=(i&1)!=0;var block=StandaloneReaderGeometryChecks.fixture(chip);int rotation=chip?3:0;var state=block.defaultBlockState().setValue(NativeMountedSwitchBlock.FACING,chip?Direction.DOWN:Direction.NORTH).setValue(NativeMountedSwitchBlock.ROTATION,rotation);var sample=new NativeCredentialReaderEntity(StandaloneReaderGeometryChecks.fixtureType(),new BlockPos(5900+i,90,5900),state,chip);var tag=new CompoundTag();tag.putBoolean("ReaderProjection",true);tag.putBoolean("ChipSlot",chip);ControlMountSettings.write(new ModernHousingTags(tag),mounted?1:0,mounted?2:0,mounted?3:0);tag.putInt("MountRotation",rotation);tag.putInt("AllowedTokens",127);tag.putInt("TokenBehavior",0);tag.putInt("TransmitRange",16);tag.putIntArray("TransmitChannels",new int[0]);tag.putBoolean("LocalRedstone",true);tag.putInt("Company",0);tag.putInt("Logo",0);tag.putInt("Age",20);sample.loadWithComponents(tag,mc.level.registryAccess());sample.setLevel(mc.level);need(!sample.visual().inert(),"prepared public empty sample");samples[i]=sample;}
  var renderer=new NativeCredentialReaderRenderer(null);POSES.translate(.23,-.17,.31);POSES.mulPose(new org.joml.Quaternionf().rotationXYZ(.12F,.31F,-.24F));var pose=new org.joml.Matrix4f(POSES.last().pose());var normal=new org.joml.Matrix3f(POSES.last().normal());var meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().threadId();for(int i=0;i<8;i++){meter.getThreadAllocatedBytes(id);render(renderer,samples,10000);meter.getThreadAllocatedBytes(id);}for(int batch=0;batch<3;batch++){long before=meter.getThreadAllocatedBytes(id);render(renderer,samples,10000);long bytes=meter.getThreadAllocatedBytes(id)-before;need(bytes==0,"40000 empty BER draws batch="+batch+" bytes="+bytes);}need(POSES.last().pose().equals(pose)&&POSES.last().normal().equals(normal),"caller affine/normal preserved");need(vertices>0,"shared overlays actually emitted");System.out.println("[vandorlabs][reader-world] warm PASS 3x40000 flat/mounted empty BER draws0B callerPosePreserved");
 }
}
