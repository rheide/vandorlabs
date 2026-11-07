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
        net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(GearRegistry.GEAR_EXTENDER);
        Constructor<ArmPayload> ctor=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);ctor.setAccessible(true);
        Method tagger=Class.forName("com.vandorlabs.vehicle.ArmCouplerHeadChecks").getDeclaredMethod("gearTag",BlockPos.class,EnumFacing.class,GearMotion.Mode.class,double.class,NBTTagCompound.class);tagger.setAccessible(true);
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long thread=Thread.currentThread().getId();
        for(int count:new int[]{4,256,4096}){
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(int i=0;i<count;i++)cells.add(new VehicleStructure.Cell(new BlockPos(i%16,(i/16)%16,i/256),Blocks.STONE.getDefaultState(),null));
            ArmPayload payload=ctor.newInstance(VehicleStructure.display(cells),BlockPos.ORIGIN,false,BlockPos.ORIGIN);NBTTagCompound envelope=(NBTTagCompound)tagger.invoke(null,BlockPos.ORIGIN,EnumFacing.UP,GearMotion.Mode.EXTEND,.5D,payload.write());NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("OpaqueOriginal",new byte[32768]);envelope.setTag("ForgeCaps",caps);envelope.setString("FutureRoot","retain");GearTileEntity gear=new GearTileEntity();gear.readFromNBT(envelope);
            for(int i=0;i<40;i++){sink=payload.write();sink=gear.currentData(envelope);}
            NBTTagCompound baseline=gear.currentData(envelope);VehicleStructure.Cell immutable=new VehicleStructure.Cell(BlockPos.ORIGIN,GearRegistry.GEAR_EXTENDER.getDefaultState(),baseline);gear.captureProgress(.25);gear.captureBlocked(true);VehicleStructure.Cell projected=immutable.withGearPose(gear);if(!projected.tileData().equals(gear.currentData(baseline))||projected.gearConfigurationIdentity()!=immutable.gearConfigurationIdentity())throw new AssertionError("Scalar pose must match full writer");NBTTagCompound exported=projected.tileData();exported.getCompoundTag("ForgeCaps").setString("ExternalMutation","rejected");if(immutable.tileData().getCompoundTag("ForgeCaps").hasKey("ExternalMutation")||projected.tileData().getCompoundTag("ForgeCaps").hasKey("ExternalMutation"))throw new AssertionError("Frozen private child escaped");
            for(int bits=0;bits<32;bits++){for(String name:new String[]{"signalKnown","lastSignal","deployedTarget"}){Field flag=GearTileEntity.class.getDeclaredField(name);flag.setAccessible(true);flag.setBoolean(gear,(bits&(name.equals("signalKnown")?1:name.equals("lastSignal")?2:4))!=0);}Field hold=GearTileEntity.class.getDeclaredField("parkingHold");hold.setAccessible(true);hold.setBoolean(gear,(bits&16)!=0);gear.captureBlocked((bits&8)!=0);gear.captureProgress((bits%4)/3D);VehicleStructure.Cell scalar=immutable.withGearPose(gear);if(!scalar.tileData().equals(gear.currentData(baseline)))throw new AssertionError("Full scalar writer equivalence bits="+bits);if(!immutable.tileData().equals(baseline))throw new AssertionError("Prior immutable root mutated");}
            VehicleStructure.Cell configChanged=new VehicleStructure.Cell(BlockPos.ORIGIN,GearRegistry.GEAR_EXTENDER.getDefaultState(),baseline);if(configChanged.gearConfigurationIdentity()==immutable.gearConfigurationIdentity())throw new AssertionError("Normal replacement must invalidate configuration identity");
            for(int i=0;i<2000;i++)sink=immutable.withGearPose(gear);
            for(int kind=0;kind<3;kind++){long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();for(int i=0;i<100;i++)sink=kind==0?payload.write():kind==1?gear.currentData(envelope):immutable.withGearPose(gear);long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(thread)-allocated;System.out.println("Arm snapshot cost cells="+count+" writer="+(kind==0?"payload.write":kind==1?"gear.currentData":"cell.withGearPose")+" calls=100 allocatedBytes="+bytes+" nanos="+nanos);}
            VehicleStructure encodedBefore=VehicleStructure.display(Collections.singletonList(immutable));encodedBefore.encode();VehicleStructure encodedAfter=VehicleStructure.display(Collections.singletonList(immutable.withGearPose(gear)));Method inherit=VehicleStructure.class.getDeclaredMethod("inheritEncodedLength",VehicleStructure.class);inherit.setAccessible(true);Object[] argument={encodedBefore};for(int i=0;i<1000;i++)inherit.invoke(encodedAfter,argument);long allocated=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();for(int i=0;i<100;i++)inherit.invoke(encodedAfter,argument);long inheritedBytes=bean.getThreadAllocatedBytes(thread)-allocated;System.out.println("Arm encoded-length cost cells="+count+" calls=100 allocatedBytes="+inheritedBytes+" nanos="+(System.nanoTime()-start));java.util.Map<BlockPos,NBTTagCompound> oldChanged=Collections.singletonMap(BlockPos.ORIGIN,immutable.withGearPose(gear).tileData());long oldAllocated=bean.getThreadAllocatedBytes(thread),oldStart=System.nanoTime();int expectedLength=0;for(int i=0;i<100;i++)expectedLength=encodedBefore.lengthWithTiles(oldChanged);System.out.println("Arm encoded-length old-recount cells="+count+" calls=100 allocatedBytes="+(bean.getThreadAllocatedBytes(thread)-oldAllocated)+" nanos="+(System.nanoTime()-oldStart));byte[] wire=encodedAfter.encode();if(wire.length!=expectedLength)throw new AssertionError("Exact on-demand length disagrees with old scalar recount");VehicleStructure roundtrip=VehicleStructure.readDisplay(net.minecraft.nbt.CompressedStreamTools.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(wire)),new net.minecraft.nbt.NBTSizeTracker(VehicleStructure.MAX_NBT_BYTES*4L)));if(!roundtrip.at(BlockPos.ORIGIN).tileData().equals(immutable.withGearPose(gear).tileData()))throw new AssertionError("On-demand exact pose encode roundtrip");
            NBTTagCompound result=gear.currentData(envelope);if(!result.getCompoundTag("ForgeCaps").equals(caps)||!result.getString("FutureRoot").equals("retain"))throw new AssertionError("Opaque source changed");
        }
        System.out.println("Arm snapshot cost PASS actual writers/three payload sizes/opaque envelope; VehicleSignals flush and full runtime cost separate");
    }
}
