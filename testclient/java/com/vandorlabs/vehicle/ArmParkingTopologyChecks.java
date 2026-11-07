package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearMotion;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Detached parking queries retain parallel constraints without enabling an incomplete live solver. */
public final class ArmParkingTopologyChecks {
 private static int checks;private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Arm parking topology: "+why);}
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new ResourceLocation("vandorlabs:square_coupler"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));BlockCoupler block=new BlockCoupler();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(GearRegistry.GEAR_EXTENDER);
  Constructor<?> worldCtor=Class.forName("com.vandorlabs.client.NonRenderingChecks$MemoryWorld").getDeclaredConstructor(boolean.class);worldCtor.setAccessible(true);World parent=(World)worldCtor.newInstance(false);Constructor<ArmPayload> ctor=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);ctor.setAccessible(true);Method tagger=ArmCouplerHeadChecks.class.getDeclaredMethod("gearTag",BlockPos.class,EnumFacing.class,GearMotion.Mode.class,double.class,NBTTagCompound.class);tagger.setAccessible(true);
  for(double phase:new double[]{1,.875,.5,0}){
   List<VehicleStructure.Cell> cells=new ArrayList<>();for(int n=0;n<2;n++){BlockPos root=new BlockPos(16,16+2*n,16),offset=new BlockPos(2,2,2),head=root.add(offset),mate=head.east();NBTTagCompound data=new NBTTagCompound();data.setString("id","vandorlabs:square_coupler");data.setBoolean("CouplerArticulation",true);data.setBoolean("CouplerRequested",true);NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("Opaque",new byte[8192]);data.setTag("ForgeCaps",caps);net.minecraft.block.state.IBlockState state=block.getDefaultState().withProperty(BlockCoupler.FACING,EnumFacing.EAST).withProperty(BlockCoupler.LOCKED,true);ArmPayload payload=ctor.newInstance(VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,data))),offset,false,offset);NBTTagCompound arm=(NBTTagCompound)tagger.invoke(null,root,EnumFacing.DOWN,GearMotion.Mode.EXTEND,phase,payload.write());cells.add(new VehicleStructure.Cell(root,GearRegistry.GEAR_EXTENDER.getDefaultState(),arm));cells.add(new VehicleStructure.Cell(mate,state.withProperty(BlockCoupler.FACING,EnumFacing.WEST),data));}
   cells.add(new VehicleStructure.Cell(new BlockPos(16,17,16),net.minecraft.init.Blocks.STONE.getDefaultState(),null));cells.add(new VehicleStructure.Cell(new BlockPos(19,19,18),net.minecraft.init.Blocks.STONE.getDefaultState(),null));VehicleStructure structure=VehicleStructure.display(cells);VehicleWorld view=new VehicleWorld(parent,structure);NBTTagCompound before=structure.write();VehicleArmCouplers.Snapshot observations=VehicleArmCouplers.observe(structure,view);VehicleArmCouplerPairs.Snapshot pairs=VehicleArmCouplerPairs.discoverForParking(observations);need(pairs.pairs.size()==2,"both saved pairs retained at phase "+phase);VehicleCouplerTopology topology=VehicleCouplerTopology.forParking(structure,view,pairs);need(topology.sections.size()==2&&topology.links.size()==2,"parallel constraints remain distinct, not rigidly folded");need(topology.links.get(0).first==topology.links.get(1).first&&topology.links.get(0).second==topology.links.get(1).second,"same two canonical carrier sections");boolean refused=false;try{VehicleCouplerTopology.withArmPairs(structure,view,pairs);}catch(IllegalArgumentException expected){refused=expected.getMessage().contains("parallel");}need(refused,"live bridge-only admission remains strict");need(structure.couplerTopology==null&&structure.write().equals(before),"query does not install topology or mutate saved opaque data");
  }
  need(!VehicleArmCouplers.runtime(),"parking query did not enable private driving");System.out.println("Detached arm parking topology PASS checks="+checks+" four saved phases/parallel constraints/opaque original/default runtime unchanged; whole-layout solving separate");
 }
}
