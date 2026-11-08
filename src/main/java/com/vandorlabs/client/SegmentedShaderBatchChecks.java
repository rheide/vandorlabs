package com.vandorlabs.client;

import com.vandorlabs.client.segmented.SegmentedDoorRenderer;
import com.vandorlabs.segmented.SegmentedDoorTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import java.util.Arrays;

/** Actual OptiFine bulk append hooks, including the empty translucent shadow batch. */
final class SegmentedShaderBatchChecks {
 private static int generation;
 static void run(Minecraft mc)throws Exception{
  if(DefaultVertexFormats.BLOCK.getNextOffset()<=28)throw new AssertionError("OptiFine extended format is required");
  java.lang.reflect.Field shadow=Class.forName("net.optifine.shaders.Shaders").getField("isShadowPass");boolean original=shadow.getBoolean(null);int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass(),cases=0;SegmentedDoorRenderer renderer=new SegmentedDoorRenderer();SegmentedDoorTile tile=SegmentedDoorRenderChecks.tile(mc,EnumFacing.NORTH);tile.from=1;tile.targetOpen=true;
  try{for(boolean shadowPass:new boolean[]{true,false})for(int prefix:new int[]{0,4})for(int pass:new int[]{1,0}){
   shadow.setBoolean(null,shadowPass);net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(7,DefaultVertexFormats.BLOCK);
   if(prefix!=0)for(int corner=0;corner<4;corner++)buffer.pos((corner==1||corner==2)?1:0,corner>=2?1:0,0).color(255,255,255,255).tex((corner==1||corner==2)?1:0,corner>=2?1:0).lightmap(240,240).endVertex();
   int[] before=buffer.getVertexState().getRawBuffer();renderer.renderTileEntityFast(tile,0,0,0,0,-1,1,buffer);int count=buffer.getVertexCount();int[] after=buffer.getVertexState().getRawBuffer();
   if(pass==1&&(count!=prefix||!Arrays.equals(before,after)))throw new AssertionError("Empty Segmented pass changed the shader batch");
   if(pass==0&&(count<=prefix||!Arrays.equals(before,Arrays.copyOf(after,before.length))))throw new AssertionError("Solid Segmented batch lost shader vertices or its prefix");
   buffer.finishDrawing();cases++;
  }}finally{shadow.setBoolean(null,original);net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);}
  System.out.println("[vandorlabs][reprolab] shader-segmented-batches PASS cases="+cases+" generation="+(generation++)+" actualOptiFine/mainShadow/emptyAndPrefilled/solidAndEmpty/prefixPreserved");
 }
 private SegmentedShaderBatchChecks(){}
}
