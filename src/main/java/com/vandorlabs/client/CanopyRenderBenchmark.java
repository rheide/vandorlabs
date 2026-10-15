package com.vandorlabs.client;

import com.vandorlabs.canopy.*;
import java.io.*;
import java.util.*;

/** CPU geometry benchmark: real drawing preparation, including animated contact clipping. */
final class CanopyRenderBenchmark {
    private static volatile int sink;
    static void run(File output)throws IOException{
        try(PrintWriter csv=new PrintWriter(new File(output,"canopy-render-geometry-benchmark.csv"))){
            csv.println("kind,width,depth,motion,variant,source_faces,draw_faces,prepare_ms,pose_p50_us,pose_p95_us,allocated_bytes_p50");
            for(String kind:new String[]{"canopy","long_canopy"})for(int width:new int[]{1,3,9})for(int motion:new int[]{0,1})measure(csv,kind,width,kind.equals("long_canopy")?48:32,motion);
            for(String kind:new String[]{"front_canopy_glass","large_front_canopy_glass"})for(int depth:new int[]{16,24,80})measure(csv,kind,kind.equals("front_canopy_glass")?3:5,depth,1);
        }
        System.out.println("[vandorlabs][reprolab] canopy-render-benchmark PASS stationary-vs-animated width1-3-9 short-long front3-5 depth16-24-80 timing-allocation");
    }
    private static void measure(PrintWriter csv,String kind,int width,int depth,int motion){
        LargeCanopySettings settings=new LargeCanopySettings();settings.depth=depth;settings.angle=2;settings.motion=motion;settings.slideForward=true;settings.openable=true;
        List<CanopyMesh.Face> source=LargeCanopySettings.supports(kind)?LargeCanopyGeometry.faces(width,settings,true):OpeningCanopyGeometry.faces(width,kind.equals("long_canopy")?3:2,settings.angle,false);
        source=CanopyFinishes.faces(source,settings.shade,settings.tint,settings.material,settings.tileMaterial);CanopyRenderGeometry geometry=new CanopyRenderGeometry(source);
        long began=System.nanoTime();geometry.posed(kind,width,settings,.5,true);double prepare=(System.nanoTime()-began)/1e6;
        measureLookup(csv,kind,width,depth,motion,settings);
        for(boolean moving:new boolean[]{false,true}){
            long[] times=new long[31],bytes=new long[31];int faces=0;
            for(int sample=-15;sample<31;sample++){
                long allocated=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
                for(int frame=0;frame<8;frame++){double pose=moving?.15+((sample+15)*8+frame)%97*.007:.5;List<CanopyMesh.Face> drawable=geometry.posed(kind,width,settings,pose,true);faces=drawable.size();sink+=faces;}
                long duration=System.nanoTime()-start,after=BenchmarkAllocations.currentThreadBytes();if(sample>=0){times[sample]=duration/8;bytes[sample]=allocated<0||after<0?-1:(after-allocated)/8;}
            }
            Arrays.sort(times);Arrays.sort(bytes);csv.printf(Locale.ROOT,"%s,%d,%d,%d,%s,%d,%d,%.6f,%.6f,%.6f,%d%n",kind,width,depth,motion,moving?"animated":"stationary",source.size(),faces,prepare,times[15]/1000D,times[29]/1000D,bytes[15]);csv.flush();
        }
    }
    private static void measureLookup(PrintWriter csv,String kind,int width,int depth,int motion,LargeCanopySettings settings){
        net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:"+kind);
        List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new ArrayList<>();boolean joined=kind.equals("canopy")||kind.equals("long_canopy");
        for(int x=0;x<(joined?width:1);x++){
            net.minecraft.util.math.BlockPos pos=new net.minecraft.util.math.BlockPos(x,0,0);net.minecraft.nbt.NBTTagCompound tag=new net.minecraft.nbt.NBTTagCompound();settings.write(tag);tag.setString("id","vandorlabs:canopy");tag.setInteger("x",x);tag.setInteger("y",0);tag.setInteger("z",0);tag.setLong("AnchorOffset",0);tag.setInteger("CanopyWidth",width);if(joined)tag.setLong("PairOffset",new net.minecraft.util.math.BlockPos(-x,0,0).toLong());
            cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(pos,block.getDefaultState().withProperty(com.vandorlabs.blocks.BlockCanopy.FACING,net.minecraft.util.EnumFacing.NORTH),tag));
        }
        net.minecraft.util.math.BlockPos seat=new net.minecraft.util.math.BlockPos(12,0,12);com.vandorlabs.tiles.TileEntityPilotSeat pilot=new com.vandorlabs.tiles.TileEntityPilotSeat();pilot.setPos(seat);
        cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(seat,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),pilot.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(seat.up(),com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState().withProperty(com.vandorlabs.blocks.BlockPilotSeat.UPPER,true),null));
        com.vandorlabs.vehicle.VehicleWorld world=new com.vandorlabs.vehicle.VehicleWorld(net.minecraft.client.Minecraft.getMinecraft().world,new com.vandorlabs.vehicle.VehicleStructure(cells,seat,net.minecraft.util.EnumFacing.NORTH,1));
        com.vandorlabs.tiles.TileEntityCanopy tile=(com.vandorlabs.tiles.TileEntityCanopy)world.getTileEntity(net.minecraft.util.math.BlockPos.ORIGIN);tile.baseFaces();long[] times=new long[31],bytes=new long[31];int faces=0;
        for(int sample=-15;sample<31;sample++){long allocated=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();for(int frame=0;frame<128;frame++){faces=tile.baseFaces().size();sink+=faces;}long duration=System.nanoTime()-start,after=BenchmarkAllocations.currentThreadBytes();if(sample>=0){times[sample]=duration/128;bytes[sample]=allocated<0||after<0?-1:(after-allocated)/128;}}
        Arrays.sort(times);Arrays.sort(bytes);csv.printf(Locale.ROOT,"%s,%d,%d,%d,tile-basefaces-hit,%d,%d,0,%.6f,%.6f,%d%n",kind,width,depth,motion,faces,faces,times[15]/1000D,times[29]/1000D,bytes[15]);csv.flush();
    }
    private CanopyRenderBenchmark(){}
}
