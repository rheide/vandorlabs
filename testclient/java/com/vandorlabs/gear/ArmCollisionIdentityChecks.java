package com.vandorlabs.gear;

import com.vandorlabs.coupler.*;
import com.vandorlabs.vehicle.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Identity metadata checked against the unchanged released cell collision emission order. */
public final class ArmCollisionIdentityChecks {
    private static int checks;private static volatile VehicleStructure.Cell sink;
    private static void need(boolean v,String why){checks++;if(!v)throw new AssertionError("Arm collision identity: "+why);}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new ResourceLocation("vandorlabs:square_coupler"));BlockCoupler block=new BlockCoupler();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
        Constructor<?> worldCtor=Class.forName("com.vandorlabs.client.NonRenderingChecks$MemoryWorld").getDeclaredConstructor(boolean.class);worldCtor.setAccessible(true);World world=(World)worldCtor.newInstance(false);
        GearTileEntity arm=new GearTileEntity();arm.setWorld(world);arm.setPos(new BlockPos(8,16,8));List<VehicleStructure.Cell> cells=new ArrayList<>();NBTTagCompound data=new NBTTagCompound();data.setString("id","vandorlabs:square_coupler");data.setString("FutureField","verbatim");NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("OpaqueCapability",new byte[32768]);data.setTag("ForgeCaps",caps);
        for(int i=0;i<7;i++)cells.add(new VehicleStructure.Cell(new BlockPos(i,0,0),i==3?block.getDefaultState().withProperty(BlockCoupler.LOCKED,true):i==1||i==5?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),i==3?data:null));
        Constructor<ArmPayload> ctor=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);ctor.setAccessible(true);ArmPayload payload=ctor.newInstance(VehicleStructure.display(cells),BlockPos.ORIGIN,false,BlockPos.ORIGIN);NBTTagCompound before=payload.write();VehicleWorld local=payload.view(arm);List<AxisAlignedBB> old=new ArrayList<>();List<Integer> owners=new ArrayList<>();for(int c=0;c<cells.size();c++){int start=old.size();VehicleStructure.Cell cell=cells.get(c);cell.state.addCollisionBoxToList(local,cell.pos,new AxisAlignedBB(cell.pos).grow(2),old,null,false);for(int i=start;i<old.size();i++)owners.add(c);}
        need(payload.collisionBoxCount(arm)==old.size()&&old.size()>3,"actual released cells emit boxes with empty-cell boundaries");for(int i=0;i<old.size();i++){VehicleStructure.Cell owner=payload.collisionCell(arm,i);need(owner==payload.blocks.cells.get(owners.get(i)),"source box index retains exact original cell");need((owner.state.getBlock() instanceof BlockCoupler)==(owners.get(i)==3),"only actual Coupler components receive a head identity");}
        Field ends=ArmPayload.class.getDeclaredField("collisionCellEnds");ends.setAccessible(true);need(((int[])ends.get(payload)).length==cells.size(),"metadata costs one integer per cell, not per component");need(payload.write().equals(before),"cache construction never rewrites original opaque snapshot");
        for(int invalid:new int[]{-1,old.size()}){boolean refused=false;try{payload.collisionCell(arm,invalid);}catch(IndexOutOfBoundsException expected){refused=true;}need(refused,"invalid box index refuses");}ArmPayload nativeOwner=ctor.newInstance(payload.blocks,BlockPos.ORIGIN,true,BlockPos.ORIGIN);boolean refused=false;try{nativeOwner.collisionCell(arm,0);}catch(IllegalArgumentException expected){refused=true;}need(refused,"native endpoint geometry cannot claim captured box identity");
        java.lang.management.ThreadMXBean base=java.lang.management.ManagementFactory.getThreadMXBean();if(base instanceof com.sun.management.ThreadMXBean){com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)base;if(bean.isThreadAllocatedMemorySupported()){bean.setThreadAllocatedMemoryEnabled(true);for(int i=0;i<20000;i++)sink=payload.collisionCell(arm,i%old.size());long id=Thread.currentThread().getId(),start=bean.getThreadAllocatedBytes(id);for(int i=0;i<10000;i++)sink=payload.collisionCell(arm,i%old.size());long bytes=bean.getThreadAllocatedBytes(id)-start;need(bytes==0,"warmed index metadata lookup allocation="+bytes);System.out.println("Arm collision identity allocation PASS queries=10000 bytes="+bytes+" metadataBytes="+(4*cells.size()));}}
        System.out.println("Arm collision identity PASS checks="+checks+" Source cell emissions="+old.size()+" empty boundaries/exact order/opaque snapshot/native ownership/index guards; active articulation tagging separate");
    }
}
