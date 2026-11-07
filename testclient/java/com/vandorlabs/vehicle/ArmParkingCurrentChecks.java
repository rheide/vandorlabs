package com.vandorlabs.vehicle;

import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearMotion;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;

/** CURRENT cache admission/cost only; actual parking ownership is checked by the live fixture. */
public final class ArmParkingCurrentChecks {
 private static int checks;private static volatile Object sink;
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("CURRENT arm cache: "+why);}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(GearRegistry.GEAR_EXTENDER);
  Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field.get(null);MinecraftServer server=(MinecraftServer)unsafe.allocateInstance(net.minecraft.server.dedicated.DedicatedServer.class);for(Field thread:MinecraftServer.class.getDeclaredFields())if(thread.getType()==Thread.class){thread.setAccessible(true);thread.set(server,Thread.currentThread());}Constructor<?> parentCtor=Class.forName("com.vandorlabs.client.ArmParkingSourceChecks$Parent").getDeclaredConstructor(MinecraftServer.class);parentCtor.setAccessible(true);net.minecraft.world.World parent=(net.minecraft.world.World)parentCtor.newInstance(server);
  Constructor<ArmPayload> payloadCtor=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);payloadCtor.setAccessible(true);Method tagger=ArmCouplerHeadChecks.class.getDeclaredMethod("gearTag",BlockPos.class,EnumFacing.class,GearMotion.Mode.class,double.class,NBTTagCompound.class);tagger.setAccessible(true);
  BlockPos root=new BlockPos(8,16,8);ArmPayload payload=payloadCtor.newInstance(VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,net.minecraft.init.Blocks.STONE.getDefaultState(),null))),new BlockPos(0,-1,0),false,new BlockPos(0,-1,0));NBTTagCompound tag=(NBTTagCompound)tagger.invoke(null,root,EnumFacing.DOWN,GearMotion.Mode.EXTEND,1D,payload.write());tag.setBoolean("GearTargetDeployed",true);VehicleStructure source=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(root,GearRegistry.GEAR_EXTENDER.getDefaultState(),tag)));EntityGroundVehicle craft=new EntityGroundVehicle(parent);craft.structure=source;craft.view=new VehicleWorld(parent,source);craft.view.craft=craft;GearTileEntity arm=(GearTileEntity)craft.view.tiles.get(root);need(arm.isOperational()&&arm.payload()!=null,"cached actual Gear owner installed");NBTTagCompound before=source.write();
  VehicleArmParkingCurrent cached=VehicleArmParkingCurrent.get(craft);need(cached.structure.cells.size()==2&&cached.seats.isEmpty(),"one captured cell materializes without a fake seat");need(craft.structure==source&&source.write().equals(before)&&!craft.transferInProgress,"preview never acquires ownership or changes source");need(VehicleArmParkingCurrent.get(craft)==cached,"stable source reuses complete layout");
  for(int n=0;n<20000;n++)sink=VehicleArmParkingCurrent.get(craft);com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);long id=Thread.currentThread().getId(),allocated=bean.getThreadAllocatedBytes(id),start=System.nanoTime();for(int n=0;n<10000;n++)sink=VehicleArmParkingCurrent.get(craft);long nanos=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(id)-allocated;need(bytes==0,"warmed layout reuse clones/allocates nothing: "+bytes);
  arm.correctProgress(.75);boolean refused=false;try{VehicleArmParkingCurrent.get(craft);}catch(IllegalArgumentException expected){refused=true;}need(refused&&craft.armParkingPreview==null&&source.write().equals(before),"revisionless fractional progress invalidates before reuse");arm.correctProgress(1);VehicleArmParkingCurrent rebuilt=VehicleArmParkingCurrent.get(craft);need(rebuilt!=cached,"return to grid rebuilds retired layout");craft.view.tiles.remove(root);refused=false;try{VehicleArmParkingCurrent.get(craft);}catch(IllegalArgumentException expected){refused=true;}need(refused&&craft.armParkingPreview==null,"missing cached owner cannot reuse layout");
  System.out.println("CURRENT arm cache PASS checks="+checks+" warmedCalls=10000 allocatedBytes="+bytes+" nanos="+nanos+" phase/owner invalidation and unchanged source; actual player parking separate");
 }
}
