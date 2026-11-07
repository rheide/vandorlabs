package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import net.minecraft.nbt.*;
import net.minecraft.util.math.BlockPos;

public final class GearConfigurationCacheChecks {
    private static volatile Object sink;
    private static int checks;
    private static void need(boolean v,String why){checks++;if(!v)throw new AssertionError("Gear config cache: "+why);}
    private static VehicleStructure.Cell cell(int salt,int bytes){NBTTagCompound tag=new NBTTagCompound();tag.setString("id","vandorlabs:modular_gear");tag.setInteger("x",0);tag.setInteger("y",0);tag.setInteger("z",0);tag.setInteger("GearRevision",salt);tag.setDouble("GearProgress",.5);tag.setBoolean("GearParkingHold",true);tag.setString("GearError","retained diagnostic");tag.setInteger("ArmActiveExtension",salt);NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("Opaque",new byte[bytes]);tag.setTag("ForgeCaps",caps);return new VehicleStructure.Cell(BlockPos.ORIGIN,GearRegistry.GEAR_EXTENDER.getDefaultState(),tag);}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new net.minecraft.util.ResourceLocation("vandorlabs:modular_gear"));
        VehicleGearConfigurationCache cache=new VehicleGearConfigurationCache();VehicleStructure.Cell first=cell(1,32768);NBTTagCompound value=cache.configuration(first);need(!value.hasKey("GearProgress")&&!value.hasKey("GearRevision")&&!value.hasKey("GearParkingHold")&&value.getString("GearError").equals("retained diagnostic"),"only approved dynamic scalars are normalized");need(value.getCompoundTag("ForgeCaps").equals(first.tileData().getCompoundTag("ForgeCaps")),"opaque bytes unchanged");GearTileEntity gear=new GearTileEntity();gear.setPos(BlockPos.ORIGIN);gear.captureProgress(.75);VehicleStructure.Cell pose=first.withGearPose(gear);need(cache.configuration(pose)==value&&cache.size()==1,"pose revisions reuse same normalized configuration");VehicleStructure.Cell changed=cell(2,32768);need(cache.configuration(changed)!=value&&cache.configuration(changed).getInteger("ArmActiveExtension")==2,"normal replacement invalidates even at same address");
        com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);for(int i=0;i<20000;i++)sink=cache.configuration(pose);long thread=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<10000;i++)sink=cache.configuration(pose);long allocated=bean.getThreadAllocatedBytes(thread)-before;need(allocated==0,"warm lookup allocation="+allocated);
        for(int i=0;i<100;i++){cache.configuration(cell(i,256*1024));need(cache.retainedBytes()<=VehicleGearConfigurationCache.MAX_BYTES,"byte pressure bounded at every insertion");}need(cache.size()<100,"variable-size eviction precedes count cap");VehicleStructure.Cell oversize=cell(7,5*1024*1024);long retained=cache.retainedBytes();need(cache.configuration(oversize)!=cache.configuration(oversize)&&cache.retainedBytes()==retained,"oversized entries remain unretained");cache.clear();need(cache.size()==0&&cache.retainedBytes()==0,"world/config reset clears all retained trees");
        System.out.println("Gear configuration cache PASS checks="+checks+" warm10000="+allocated+"B/pose identity/config replacement/opaque tree/byte pressure/oversize/reset");
    }
}
