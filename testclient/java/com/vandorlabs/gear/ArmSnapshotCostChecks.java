package com.vandorlabs.gear;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.gear.core.GearMotion;
import java.lang.reflect.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** Measures the actual retained snapshot writers; not a whole-tick or hardware benchmark. */
public final class ArmSnapshotCostChecks {
    private static volatile Object sink;
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));
        Constructor<ArmPayload> ctor=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);ctor.setAccessible(true);
        Method tagger=Class.forName("com.vandorlabs.vehicle.ArmCouplerHeadChecks").getDeclaredMethod("gearTag",BlockPos.class,EnumFacing.class,GearMotion.Mode.class,double.class,NBTTagCompound.class);tagger.setAccessible(true);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        for(int count:new int[]{4,256,4096}){
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(int i=0;i<count;i++)cells.add(new VehicleStructure.Cell(new BlockPos(i%16,(i/16)%16,i/256),Blocks.STONE.getDefaultState(),null));
            ArmPayload payload=ctor.newInstance(VehicleStructure.display(cells),BlockPos.ORIGIN,false,BlockPos.ORIGIN);NBTTagCompound envelope=(NBTTagCompound)tagger.invoke(null,BlockPos.ORIGIN,EnumFacing.UP,GearMotion.Mode.EXTEND,.5D,payload.write());NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("OpaqueOriginal",new byte[32768]);envelope.setTag("ForgeCaps",caps);envelope.setString("FutureRoot","retain");GearTileEntity gear=new GearTileEntity();gear.readFromNBT(envelope);
            for(int i=0;i<40;i++){sink=payload.write();sink=gear.currentData(envelope);}
            for(int kind=0;kind<2;kind++){long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();for(int i=0;i<100;i++)sink=kind==0?payload.write():gear.currentData(envelope);long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-allocated;System.out.println("Arm snapshot cost cells="+count+" writer="+(kind==0?"payload.write":"gear.currentData")+" calls=100 allocatedBytes="+bytes+" nanos="+nanos);}
            NBTTagCompound result=gear.currentData(envelope);if(!result.getCompoundTag("ForgeCaps").equals(caps)||!result.getString("FutureRoot").equals("retain"))throw new AssertionError("Opaque source changed");
        }
        System.out.println("Arm snapshot cost PASS actual writers/three payload sizes/opaque envelope; VehicleSignals flush and full runtime cost separate");
    }
}
