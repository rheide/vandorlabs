package com.vandorlabs.vehicle;
import com.vandorlabs.coupler.*;
import com.vandorlabs.gear.*;
import com.vandorlabs.gear.core.GearMotion;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
/** Released legacy paired snapshot with its actual mate removed, not a fabricated standalone proof. */
public final class ArmCouplerAssociationGuardChecks {
 public static void main(String[] args)throws Exception{
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileCoupler.class,new ResourceLocation("vandorlabs:square_coupler"));net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));BlockCoupler block=new BlockCoupler();net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(GearRegistry.GEAR_EXTENDER);
  Constructor<?> wc=Class.forName("com.vandorlabs.client.NonRenderingChecks$MemoryWorld").getDeclaredConstructor(boolean.class);wc.setAccessible(true);World parent=(World)wc.newInstance(false);Constructor<ArmPayload> pc=ArmPayload.class.getDeclaredConstructor(VehicleStructure.class,BlockPos.class,boolean.class,BlockPos.class);pc.setAccessible(true);Method tagger=ArmCouplerHeadChecks.class.getDeclaredMethod("gearTag",BlockPos.class,EnumFacing.class,GearMotion.Mode.class,double.class,NBTTagCompound.class);tagger.setAccessible(true);
  BlockPos root=new BlockPos(8,8,8),offset=new BlockPos(0,-2,0),mate=root.add(1,-2,0);NBTTagCompound head=new NBTTagCompound();head.setString("id","vandorlabs:square_coupler");head.setBoolean("CouplerArticulation",true);head.setBoolean("CouplerRequested",true);NBTTagCompound cap=new NBTTagCompound();cap.setByteArray("opaque",new byte[]{3,1,4});head.setTag("ForgeCaps",cap);net.minecraft.block.state.IBlockState state=block.getDefaultState().withProperty(BlockCoupler.FACING,EnumFacing.EAST).withProperty(BlockCoupler.LOCKED,true);ArmPayload payload=pc.newInstance(VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,head))),offset,false,offset);NBTTagCompound rootData=(NBTTagCompound)tagger.invoke(null,root,EnumFacing.DOWN,GearMotion.Mode.EXTEND,1D,payload.write());VehicleStructure.Cell arm=new VehicleStructure.Cell(root,GearRegistry.GEAR_EXTENDER.getDefaultState(),rootData),other=new VehicleStructure.Cell(mate,state.withProperty(BlockCoupler.FACING,EnumFacing.WEST),VehicleTileData.translated(head,mate));
  VehicleStructure complete=VehicleStructure.display(Arrays.asList(arm,other));VehicleArmCouplerPairs.Snapshot pairs=VehicleArmCouplerPairs.discoverForParking(VehicleArmCouplers.observe(complete,new VehicleWorld(parent,complete)));if(pairs.pairs.size()!=1)throw new AssertionError("legacy saved pair must exist before missing-mate negative");NBTTagCompound original=complete.write();VehicleStructure missing=VehicleStructure.display(Collections.singletonList(arm));boolean refused=false;try{VehicleArmCouplerPairs.discoverForParking(VehicleArmCouplers.observe(missing,new VehicleWorld(parent,missing)));}catch(IllegalArgumentException expected){refused=true;}if(!refused||!complete.write().equals(original))throw new AssertionError("legacy captured locked missing mate must refuse without mutating saved source");System.out.println("Coupler association legacy guard PASS actual pair before/removed mate refusal/opaque source unchanged");
 }
}
