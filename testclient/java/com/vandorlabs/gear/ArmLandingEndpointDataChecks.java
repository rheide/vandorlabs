package com.vandorlabs.gear;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
/** Independent physical frame oracle plus exact opaque NBT round trips for typed loose feet. */
public final class ArmLandingEndpointDataChecks {
 private static int checks,profiles;
 private static void need(boolean ok,String message){checks++;if(!ok)throw new AssertionError("Landing endpoint data: "+message);}
 private static Vec3d vector(EnumFacing f){return new Vec3d(f.getFrontOffsetX(),f.getFrontOffsetY(),f.getFrontOffsetZ());}
 private static Vec3d physical(EnumFacing mount,EnumFacing facing,Vec3d v){double x=v.x,z=v.z;switch(facing){case EAST:v=new Vec3d(1-z,v.y,x);break;case SOUTH:v=new Vec3d(1-x,v.y,1-z);break;case WEST:v=new Vec3d(z,v.y,1-x);break;default:break;}return ArmOrientation.point(mount,v.x,v.y,v.z);}
 public static void main(String[] args){
  net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));GearBlock[] blocks={new GearBlock("endpoint_test_plate",GearBlock.Kind.PLATE),new GearBlock("endpoint_test_ski",GearBlock.Kind.SKI),new GearBlock("endpoint_test_wheel",GearBlock.Kind.WHEEL)};for(GearBlock block:blocks)net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
  for(EnumFacing x:EnumFacing.values())for(EnumFacing y:EnumFacing.values()){Vec3d vx=vector(x),vy=vector(y);if(vx.dotProduct(vy)!=0)continue;Vec3d[] axes={vx,vy,vx.crossProduct(vy)};int[] basis=ArmGridMapping.of(BlockPos.ORIGIN,Vec3d.ZERO,axes).basis();ArmEndpointTiles inverse=new ArmEndpointTiles(axes);
   for(GearBlock block:blocks)for(EnumFacing mount:EnumFacing.values())for(EnumFacing facing:EnumFacing.HORIZONTALS){
    GearTileEntity seed=new GearTileEntity();seed.setPos(BlockPos.ORIGIN);NBTTagCompound raw=seed.writeToNBT(new NBTTagCompound());raw.setString("ArmMountDirection",mount.name());raw.setString("GearDirection",facing.name());raw.setString("FutureChoice","retained");NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("opaque",new byte[]{7,-8,42});raw.setTag("ForgeCaps",caps);IBlockState state=block.getDefaultState().withProperty(GearBlock.FACING,facing);VehicleStructure.Cell cell=new VehicleStructure.Cell(BlockPos.ORIGIN,state,raw);need(ArmLooseGearData.admitted(cell),"ordinary loose foot admitted");IBlockState mapped=ArmGridTiles.state(cell,basis);NBTTagCompound mappedData=ArmGridTiles.data(cell,basis);need(mapped!=null&&raw.equals(cell.tileData())&&mappedData.getCompoundTag("ForgeCaps").equals(caps)&&mappedData.getString("FutureChoice").equals("retained"),"chosen mapping preserves original and opaque bytes");
    EnumFacing nextMount=EnumFacing.valueOf(mappedData.getString("ArmMountDirection"));for(Vec3d point:new Vec3d[]{new Vec3d(.1,0,.2),new Vec3d(.7,.3,.8),new Vec3d(.5,.5,.5)}){Vec3d source=physical(mount,facing,point).subtract(.5,.5,.5);Vec3d expected=new Vec3d(basis[0]*source.x+basis[1]*source.y+basis[2]*source.z+.5,basis[3]*source.x+basis[4]*source.y+basis[5]*source.z+.5,basis[6]*source.x+basis[7]*source.y+basis[8]*source.z+.5),actual=physical(nextMount,mapped.getValue(GearBlock.FACING),point);need(expected.squareDistanceTo(actual)<1e-15,"independent native geometry frame covariance");}
    VehicleStructure.Cell placed=new VehicleStructure.Cell(BlockPos.ORIGIN,mapped,mappedData);need(inverse.constructionState(placed)==state&&inverse.constructionData(placed).equals(raw),"exact inverse returns metadata and opaque original data");ArmGridTiles.validate(VehicleStructure.display(Collections.singletonList(cell)),VehicleStructure.display(Collections.singletonList(placed)),basis);
    NBTTagCompound owned=raw.copy();owned.setLong("GearOwnerOffset",1);need(ArmGridTiles.state(new VehicleStructure.Cell(BlockPos.ORIGIN,state,owned),basis)==null,"bound foot ownership is never adopted");owned=raw.copy();owned.setBoolean("GearController",true);need(ArmGridTiles.state(new VehicleStructure.Cell(BlockPos.ORIGIN,state,owned),basis)==null,"formed foot controller is never adopted");profiles++;
   }
  }
  need(profiles==1728,"all24 rotations six mounts three feet four facings covered");System.out.println("Arm landing endpoint data PASS profiles="+profiles+" checks="+checks+" independent six-face geometry covariance exact inverse opaque caps and ownership refusal");
 }
}
