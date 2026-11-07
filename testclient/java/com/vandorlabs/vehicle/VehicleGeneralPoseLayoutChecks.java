package com.vandorlabs.vehicle;

import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearMotion;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Actual Source projection semantics and stable labels, not gameplay/collision admission. */
public final class VehicleGeneralPoseLayoutChecks {
 private static int checks;private static BlockCoupler coupler;private static com.vandorlabs.blocks.BlockVehicleController controller;private static Constructor<ArmPayload> payloadCtor;private static Method tagger;private static World parent;
 private static final BlockPos ROOT=new BlockPos(16,16,16),OFFSET=new BlockPos(2,2,2);
 private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("General layout: "+why);}
 private static final class Fixture {
  final VehicleStructure source;final VehicleWorld view;
  Fixture(List<VehicleStructure.Cell> cells){source=VehicleStructure.display(cells);view=new VehicleWorld(parent,source);}
  VehicleGeneralPoseLayout layout(){VehicleCouplerTopology topology=VehicleCouplerTopology.forParking(source,view,VehicleArmCouplerPairs.discoverForParking(VehicleArmCouplers.observe(source,view)));VehicleArmConstraintFrame frame=VehicleArmConstraintFrame.project(source,view,topology,Collections.singletonList(ROOT));return VehicleGeneralPoseLayout.project(source,view,topology,frame);}
 }
 private static Fixture fixture(EnumFacing mount,EnumFacing face,int change)throws Exception{
  NBTTagCompound data=new NBTTagCompound();data.setString("id","vandorlabs:square_coupler");data.setBoolean("CouplerArticulation",true);data.setBoolean("CouplerRequested",change!=1);data.setBoolean("CouplerReceived",change!=1);data.setString("CouplerStatus",change==1?"changed":"Ready");NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("Opaque",new byte[change==1?16384:8192]);data.setTag("ForgeCaps",caps);
  net.minecraft.block.state.IBlockState state=coupler.getDefaultState().withProperty(BlockCoupler.FACING,face).withProperty(BlockCoupler.LOCKED,true);List<VehicleStructure.Cell> payloadCells=Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,data),new VehicleStructure.Cell(new BlockPos(0,0,1),net.minecraft.init.Blocks.STONE.getDefaultState(),null));ArmPayload payload=payloadCtor.newInstance(VehicleStructure.display(payloadCells),OFFSET,false,OFFSET);NBTTagCompound root=(NBTTagCompound)tagger.invoke(null,ROOT,mount,GearMotion.Mode.EXTEND,1D,payload.write());
  if(change==1){root.setBoolean("GearTargetDeployed",true);root.setBoolean("GearSignalKnown",true);root.setBoolean("GearLastSignal",true);root.setString("GearChannels","67002");root.setInteger("GearTrigger",2);root.setString("GearError","different status");root.setUniqueId("ArmOperator",new UUID(3,4));root.setTag("ForgeCaps",caps.copy());}
  if(change==2)root.setDouble("ArmActiveAngle",105);if(change==3)root.setDouble("ArmActiveExtension",-.25);if(change==4)root.removeTag("ArmRetractingStroke");
  com.vandorlabs.tiles.TileEntityVehicleController control=new com.vandorlabs.tiles.TileEntityVehicleController();control.setPos(ROOT.west());List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(ROOT,GearRegistry.GEAR_EXTENDER.getDefaultState(),root));cells.add(new VehicleStructure.Cell(ROOT.west(),controller.getDefaultState(),control.writeToNBT(new NBTTagCompound())));cells.add(new VehicleStructure.Cell(ROOT.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),null));cells.add(new VehicleStructure.Cell(ROOT.add(OFFSET).offset(face),state.withProperty(BlockCoupler.FACING,face.getOpposite()),data));if(change==5)cells.add(new VehicleStructure.Cell(ROOT.south(),net.minecraft.init.Blocks.STONE.getDefaultState(),null));return new Fixture(cells);
 }
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new ResourceLocation("vandorlabs:square_coupler"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(com.vandorlabs.tiles.TileEntityVehicleController.class,new ResourceLocation("vandorlabs:vehicle_controller"));coupler=new BlockCoupler();controller=new com.vandorlabs.blocks.BlockVehicleController();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(coupler);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(controller);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(GearRegistry.GEAR_EXTENDER);
  Constructor<?> ctor=Class.forName("com.vandorlabs.client.NonRenderingChecks$MemoryWorld").getDeclaredConstructor(boolean.class);ctor.setAccessible(true);parent=(World)ctor.newInstance(false);payloadCtor=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);payloadCtor.setAccessible(true);tagger=ArmCouplerHeadChecks.class.getDeclaredMethod("gearTag",BlockPos.class,EnumFacing.class,GearMotion.Mode.class,double.class,NBTTagCompound.class);tagger.setAccessible(true);
  for(EnumFacing mount:EnumFacing.values())for(EnumFacing face:EnumFacing.values()){
   Fixture base=fixture(mount,face,0);NBTTagCompound original=base.source.write();VehicleGeneralPoseLayout identity=base.layout();need(identity.token()!=0&&identity.bodies()==2,"nonzero two-body semantic identity");need(identity.carrier(identity.rootBody()).equals(ROOT.down()),"minimum owned native root carrier");need(identity.canonicalBody(0)==identity.rootBody()&&identity.bodyForCarrier(ROOT.down())==identity.rootBody(),"canonical/frame body labels roundtrip");need(identity.retainedBytes()<8192,"bounded returned descriptor");
   GearTileEntity owner=(GearTileEntity)base.view.tiles.get(ROOT);for(double phase:new double[]{0,.125,.5,.875,1}){owner.correctProgress(phase);need(base.layout().token()==identity.token(),"phase never changes semantic layout "+mount+"/"+face+"/"+phase);}
   need(fixture(mount,face,1).layout().token()==identity.token(),"target/channels/request/status/actor/opaque bytes excluded");for(int change=2;change<=5;change++)need(fixture(mount,face,change).layout().token()!=identity.token(),"raw geometry/curve/ownership change invalidates "+change);
   List<VehicleStructure.Cell> reversed=new ArrayList<>(base.source.cells);Collections.reverse(reversed);Fixture reordered=new Fixture(reversed);VehicleGeneralPoseLayout other=reordered.layout();need(other.token()==identity.token()&&other.carrier(other.rootBody()).equals(identity.carrier(identity.rootBody())),"source iteration and frame body numbering do not change persistent labels");
   ArmGridPlacement nativePlacement=owner.payload().gridPlacement(owner,1);if(nativePlacement!=null){List<VehicleStructure.Cell> materialized=new ArrayList<>();for(VehicleStructure.Cell cell:base.source.cells){if(cell.pos.equals(ROOT)){NBTTagCompound tag=cell.tileData();tag.setTag("ArmPayload",nativePlacement.payloadTag());materialized.add(new VehicleStructure.Cell(cell.pos,cell.state,tag));}else materialized.add(cell);}boolean unique=true;for(VehicleStructure.Cell leaf:nativePlacement.cells){for(VehicleStructure.Cell cell:materialized)if(cell.pos.equals(leaf.pos))unique=false;materialized.add(leaf);}if(unique)need(new Fixture(materialized).layout().token()!=identity.token(),"native/captured ownership boundary changes layout");}
   need(base.source.write().equals(original),"capture leaves all original opaque source data unchanged");
   VehicleCouplerTopology topology=VehicleCouplerTopology.forParking(base.source,base.view,VehicleArmCouplerPairs.discoverForParking(VehicleArmCouplers.observe(base.source,base.view)));VehicleArmConstraintFrame frame=VehicleArmConstraintFrame.project(base.source,base.view,topology,Collections.singletonList(ROOT));boolean refused=false;try{VehicleGeneralPoseLayout.capture(base.source,base.view,topology,frame);}catch(IllegalStateException expected){refused=true;}need(refused,"public capture refuses CPU world without physical owning thread");
  }
  System.out.println("General layout descriptor PASS profiles=36 checks="+checks+" phase/control/opaque-data invariance, raw-geometry/ownership changes, canonical labels; no collision or gameplay admission");
 }
}
