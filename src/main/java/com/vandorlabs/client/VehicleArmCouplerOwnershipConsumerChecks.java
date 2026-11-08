package com.vandorlabs.client;
import com.vandorlabs.gear.GearTileEntity;
import com.vandorlabs.vehicle.*;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
/** Read-only diagnostic continuation: preserve the exact scene's first failure while pending Source work completes. */
final class VehicleArmCouplerOwnershipConsumerChecks {
 private static Exception original;private static int waiting,records;private static String last="";
 private static int[] state(EntityGroundVehicle craft){
  if(craft==null||craft.structure==null||craft.view==null)return new int[]{-1,-1,0,0};
  int heads=VehicleArmCouplerPairProbe.headCount(craft.structure,craft.view);
  boolean coherent=craft.view.structure==craft.structure;
  Object tile=craft.view.tiles.get(new BlockPos(3,1,1));
  boolean captured=tile instanceof GearTileEntity&&((GearTileEntity)tile).payload()!=null&&!((GearTileEntity)tile).payload().materialized();
  return new int[]{craft.structure.cells.size(),heads,coherent?1:0,captured?1:0};
 }
 static boolean tick(Minecraft mc,File output)throws Exception{
  if(original==null)try{return VehicleArmCouplerMultiLiveChecks.tick(mc,output);}catch(Exception failure){original=failure;System.err.println("[vandorlabs][reprolab] multi-consumer retained original scene failure: "+failure);}
  // No further fixture commands, phase assignments or body transforms. Only normal game/network ticks continue.
  EntityGroundVehicle client=mc.player!=null&&mc.player.getRidingEntity() instanceof EntityGroundVehicle?(EntityGroundVehicle)mc.player.getRidingEntity():null;
  final java.util.UUID id=client==null?null:client.getUniqueID();
  int[] server=mc.getIntegratedServer().callFromMainThread(()->{net.minecraft.entity.Entity raw=id==null?null:mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id);return state(raw instanceof EntityGroundVehicle?(EntityGroundVehicle)raw:null);}).get();
  int[] local=state(client);String status=java.util.Arrays.toString(server)+"/"+java.util.Arrays.toString(local);
  if(!status.equals(last)&&records++<8){last=status;System.out.println("[vandorlabs][reprolab] multi-consumer source/head/coherent/captured server="+java.util.Arrays.toString(server)+" client="+java.util.Arrays.toString(local)+" sourcePending="+(id!=null&&VehicleClient.snapshotPending(id)));}
  if(local[0]==70&&local[1]==5){IllegalStateException mixed=new IllegalStateException("Ownership consumer still installed mixed 70-cell/five-head source");mixed.addSuppressed(original);throw mixed;}
  if(server[0]==69&&server[1]==4&&server[2]==1&&local[0]==69&&local[1]==4&&local[2]==1&&local[3]==1){
   if(client.structure.at(new BlockPos(4,1,1))!=null)throw new IllegalStateException("Captured head still has a native source owner",original);
   VehicleArmCouplerPairProbe.before(client.structure,client.view);
   mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle current=(EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id);VehicleArmCouplerPairProbe.before(current.structure,current.view);}).get();
   System.out.println("[vandorlabs][reprolab] multi-consumer ownership-boundary PASS server69/4 client69/4 coherent installed Source/view captured1 oldNativeAbsent; original independent physics failure remains");
   throw original;
  }
  if(++waiting>=120){original.addSuppressed(new IllegalStateException("Bounded consumer observation did not establish both Source69/four-head snapshots; final="+status));throw original;}
  return false;
 }
 private VehicleArmCouplerOwnershipConsumerChecks(){}
}
