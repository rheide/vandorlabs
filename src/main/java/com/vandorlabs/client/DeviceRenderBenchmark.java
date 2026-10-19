package com.vandorlabs.client;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.client.GearRenderer;
import com.vandorlabs.client.constructors.UniversalConstructorModelCache;
import com.vandorlabs.vehicle.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Native CPU vertex-emission comparison, independent of driver/FPS timing. */
final class DeviceRenderBenchmark {
    interface Emitter {void emit(BufferBuilder buffer,boolean cached,int copy);}
    static void run(Minecraft mc,File output){
        try(PrintWriter csv=new PrintWriter(new File(output,"device-render-benchmark.csv"))){
            csv.println("device,pose,count,vertices,reference_p50_ms,cached_p50_ms,reference_p95_ms,cached_p95_ms,reference_allocated_bytes,cached_allocated_bytes");
            GearRenderer renderer=new GearRenderer();int cases=0;
            for(String size:new String[]{"small","large"})for(String arm:new String[]{"mount","extender","swing"})for(String foot:new String[]{"plate","ski","wheel"}){
                String name="gear_"+size+"_"+arm+"_"+foot;
                VehicleStructure snapshot=mc.getIntegratedServer().callFromMainThread(()->snapshot(mc,name)).get();
                VehicleWorld view=new VehicleWorld(mc.world,snapshot);GearTileEntity gear=(GearTileEntity)view.getTileEntity(new BlockPos(2,3,2));
                require(gear!=null&&gear.isOperational(),"benchmark gear formation "+name);
                for(double progress:arm.equals("mount")?new double[]{1}:new double[]{0,.5,1}){
                    gear.captureProgress(progress);gear.captureProgress(progress);
                    Emitter emit=(buffer,cached,copy)->{if(cached)renderer.renderTileEntityFast(gear,copy*4,.2,.3,1,0,1,buffer);else renderer.renderUncached(gear,copy*4,.2,.3,1,buffer);};
                    compare(emit,name,Double.toString(progress),csv);cases++;
                }
            }
            for(String id:new String[]{"assembly_press","synthesis_tower","enclosed_gantry"})for(EnumFacing facing:EnumFacing.HORIZONTALS){
                UniversalConstructorModelCache.Mesh mesh=UniversalConstructorModelCache.get(id,"complete.obj");
                double width=id.equals("assembly_press")?4:id.equals("synthesis_tower")?3:5;
                Emitter emit=(buffer,cached,copy)->{if(cached)mesh.emit(buffer,copy*6,.2,.3,facing,width,3,0,0,0,0x00D000A0);else mesh.emitUncached(buffer,copy*6,.2,.3,facing,width,3,0,0,0,0x00D000A0);};
                compare(emit,id,facing.getName(),csv);cases++;
            }
            System.out.println("[vandorlabs][reprolab] gear stationary cached vertex bytes="+GearRenderer.cachedVertexBytes());
            System.out.println("[vandorlabs][reprolab] device-render-benchmark PASS "+cases+" cases all gear sizes arms feet poses and constructor facings; equivalent vertex output, 16 copies, 15 warmups, 31 samples");
        }catch(Exception error){throw new IllegalStateException("Device rendering benchmark",error);}
    }
    private static VehicleStructure snapshot(Minecraft mc,String name){
        net.minecraft.world.World world=mc.getIntegratedServer().getWorld(0);BlockPos root=new BlockPos(600,90,120);
        GearDocumentationScenes.build(world,name,root);GearTileEntity gear=(GearTileEntity)world.getTileEntity(root);
        BlockPos origin=root.add(-2,-3,-2);
        List<VehicleStructure.Cell> cells=new ArrayList<>();List<BlockPos> members=new ArrayList<>(gear.members());
        for(BlockPos member:members)cells.add(new VehicleStructure.Cell(member.subtract(origin),world.getBlockState(member),VehicleTileData.translated(world.getTileEntity(member).writeToNBT(new NBTTagCompound()),BlockPos.ORIGIN.subtract(origin))));
        for(BlockPos member:members)world.setBlockToAir(member);
        for(int x=-2;x<=3;x++)for(int z=-2;z<=3;z++)world.setBlockToAir(root.add(x,1,z));
        return VehicleStructure.display(cells);
    }
    private static void compare(Emitter emitter,String name,String pose,PrintWriter csv){
        BufferBuilder buffer=new BufferBuilder(2*1024*1024);
        byte[] reference=vertices(emitter,buffer,false),cached=vertices(emitter,buffer,true);
        require(reference.length==cached.length&&reference.length>0,"vertex count "+name);
        ByteBuffer a=ByteBuffer.wrap(reference).order(java.nio.ByteOrder.nativeOrder()),b=ByteBuffer.wrap(cached).order(java.nio.ByteOrder.nativeOrder());
        for(int i=0;i<reference.length;i+=4){
            int field=(i/4)%7;
            if(field<3)require(Math.abs(a.getFloat(i)-b.getFloat(i))<.00001,"vertex position "+name+" at "+i);
            else require(a.getInt(i)==b.getInt(i),"vertex color UV or light "+name+" at "+i);
        }
        double[][] times=new double[2][31];long[][] allocations=new long[2][31];
        for(int sample=-15;sample<31;sample++)for(int order=0;order<2;order++){
            int variant=(sample+order)&1;long allocated=BenchmarkAllocations.currentThreadBytes(),start=System.nanoTime();
            buffer.begin(7,DefaultVertexFormats.BLOCK);for(int copy=0;copy<16;copy++)emitter.emit(buffer,variant==1,copy);buffer.finishDrawing();buffer.reset();
            long elapsed=System.nanoTime()-start,bytes=BenchmarkAllocations.currentThreadBytes()-allocated;
            if(sample>=0){times[variant][sample]=elapsed/1e6;allocations[variant][sample]=bytes;}
        }
        for(double[] samples:times)Arrays.sort(samples);for(long[] samples:allocations)Arrays.sort(samples);
        csv.printf(Locale.ROOT,"%s,%s,16,%d,%.6f,%.6f,%.6f,%.6f,%d,%d%n",name,pose,reference.length/28,times[0][15],times[1][15],times[0][29],times[1][29],allocations[0][15],allocations[1][15]);
    }
    private static byte[] vertices(Emitter emitter,BufferBuilder buffer,boolean cached){
        buffer.begin(7,DefaultVertexFormats.BLOCK);emitter.emit(buffer,cached,0);buffer.finishDrawing();
        ByteBuffer bytes=buffer.getByteBuffer().duplicate();bytes.position(0);bytes.limit(buffer.getVertexCount()*28);byte[] result=new byte[bytes.remaining()];bytes.get(result);buffer.reset();return result;
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
