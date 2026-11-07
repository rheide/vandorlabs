package com.vandorlabs.client;

import com.vandorlabs.client.segmented.*;
import com.vandorlabs.segmented.*;
import com.vandorlabs.vehicle.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.Field;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/** Actual renderer submissions for many equivalent columns; CPU/retention evidence, not hardware FPS. */
final class SegmentedDoorDensityChecks {
 private static void need(boolean value,String why){if(!value)throw new IllegalStateException("Segmented density: "+why);}
 static boolean run(Minecraft mc,File output)throws Exception{
  final int count=512,height=3;Block block=Block.getBlockFromName("vandorlabs:segmented_door");
  List<VehicleStructure.Cell> cells=new ArrayList<>();for(int i=0;i<count;i++)for(int row=0;row<height;row++)cells.add(new VehicleStructure.Cell(new BlockPos((i%32)*2,height-1-row,(i/32)*3),block.getDefaultState(),null));
  VehicleWorld world=new VehicleWorld(mc.world,VehicleStructure.display(cells));world.origin=new BlockPos(20000,180,20000);List<SegmentedDoorTile> roots=new ArrayList<>();
  for(VehicleStructure.Cell cell:world.structure.cells){SegmentedDoorTile tile=(SegmentedDoorTile)block.createTileEntity(world,cell.state);tile.setWorld(world);tile.setPos(cell.pos);tile.height=height;tile.topOffset=height-1-cell.pos.getY();tile.formed=true;tile.settings=new SegmentedDoorSettings(0,false,1,0,true,0,false,SegmentedDoorSettings.DEFAULT.glass);tile.readFromNBT(tile.writeToNBT(new NBTTagCompound()));world.tiles.put(cell.pos,tile);if(tile.isRoot())roots.add(tile);}
  need(roots.size()==count,"512 complete equivalent owners");boolean authored=SegmentedDoorVisuals.authored;SegmentedDoorVisuals.authored=true;SegmentedDoorRenderer renderer=new SegmentedDoorRenderer();BufferBuilder buffer=new BufferBuilder(16*1024*1024);
  Field columns=SegmentedDoorRenderer.class.getDeclaredField("COLUMNS");columns.setAccessible(true);Map<?,?> cache=(Map<?,?>)columns.get(null);Field config=null;
  com.sun.management.ThreadMXBean meter=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();meter.setThreadAllocatedMemoryEnabled(true);long bytes=0,vertices=0;int maximumDescriptors=0;
  try(PrintWriter csv=new PrintWriter(new File(output,"segmented-density.csv"))){csv.println("frame,owners,descriptors,allocated_bytes,vertices,retained_bytes");
   for(int frame=0;frame<8;frame++){double pose=.2+frame*.05;for(SegmentedDoorTile root:roots){root.from=pose;root.targetOpen=true;root.started=world.getTotalWorldTime();root.duration=100000;}
    buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);long start=meter.getThreadAllocatedBytes(Thread.currentThread().getId());for(SegmentedDoorTile root:roots)renderer.renderTileEntityFast(root,root.getPos().getX(),root.getPos().getY(),root.getPos().getZ(),0,-1,1,buffer);long allocated=meter.getThreadAllocatedBytes(Thread.currentThread().getId())-start;buffer.finishDrawing();
    Set<Object> descriptors=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());for(SegmentedDoorTile root:roots){Object[] passes=(Object[])cache.get(root);need(passes!=null&&passes[0]!=null,"actual cached column");if(config==null){config=passes[0].getClass().getDeclaredField("config");config.setAccessible(true);}descriptors.add(config.get(passes[0]));}
    csv.println(frame+","+count+","+descriptors.size()+","+allocated+","+buffer.getVertexCount()+","+SegmentedDoorRenderer.retainedBytes());csv.flush();if(frame>=4){bytes+=allocated;vertices+=buffer.getVertexCount();maximumDescriptors=Math.max(maximumDescriptors,descriptors.size());}
   }
   System.out.println("[vandorlabs][reprolab] segmented-density owners="+count+" maxDescriptors="+maximumDescriptors+" fourFrameAllocatedBytes="+bytes+" emittedVertices="+vertices+" retainedBytes="+SegmentedDoorRenderer.retainedBytes());
   need(maximumDescriptors<=4,"equivalent moving owners must share immutable descriptors, got "+maximumDescriptors);need(SegmentedDoorRenderer.retainedBytes()<=18*1024*1024,"bounded renderer retention");
   SegmentedDoorBenchmark.contracts(mc,output);
   SegmentedDoorRenderChecks.run(mc,output);
   System.out.println("[vandorlabs][reprolab] utility26-focused PASS segmented-density-only");return true;
  }finally{SegmentedDoorVisuals.authored=authored;}
 }
 private SegmentedDoorDensityChecks(){}
}
