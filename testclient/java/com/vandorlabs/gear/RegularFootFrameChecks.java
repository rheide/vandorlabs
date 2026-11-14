package com.vandorlabs.gear;

import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** New local-plane foot metadata must remain local when the owning vehicle turns. */
public final class RegularFootFrameChecks {
    private static int checks,profiles;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Regular foot frame: "+why);}
    private static Vec3d vector(EnumFacing f){return new Vec3d(f.getFrontOffsetX(),f.getFrontOffsetY(),f.getFrontOffsetZ());}
    private static Vec3d physical(EnumFacing mount,EnumFacing facing,Vec3d p){double x=p.x,z=p.z;switch(facing){case EAST:p=new Vec3d(1-z,p.y,x);break;case SOUTH:p=new Vec3d(1-x,p.y,1-z);break;case WEST:p=new Vec3d(z,p.y,1-x);break;default:break;}return ArmOrientation.point(mount,p.x,p.y,p.z);}
    public static void main(String[] args){
        net.minecraft.init.Bootstrap.register();net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(GearTileEntity.class,new ResourceLocation("vandorlabs:modular_gear"));
        GearTileEntity seed=new GearTileEntity();seed.setPos(BlockPos.ORIGIN);NBTTagCompound base=seed.writeToNBT(new NBTTagCompound());NBTTagCompound caps=new NBTTagCompound();caps.setByteArray("Opaque",new byte[]{7,-4,6});base.setTag("ForgeCaps",caps);
        for(GearBlock.Kind kind:new GearBlock.Kind[]{GearBlock.Kind.PLATE,GearBlock.Kind.SKI,GearBlock.Kind.WHEEL}){
            GearBlock block=new GearBlock("frame_test_"+kind.name().toLowerCase(java.util.Locale.ROOT),kind);net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.register(block);
            for(EnumFacing mount:EnumFacing.values())for(EnumFacing facing:EnumFacing.HORIZONTALS){
                NBTTagCompound raw=base.copy();raw.setString("ArmMountDirection",mount.name());IBlockState state=block.getDefaultState().withProperty(GearBlock.FACING,facing);ArmFootComponentFrame.writeFoot(raw,state,mount);
                GearTileEntity restored=new GearTileEntity();restored.readFromNBT(raw);NBTTagCompound own=raw.copy(),saved=restored.writeToNBT(new NBTTagCompound());own.removeTag("ForgeCaps");saved.removeTag("ForgeCaps");need(saved.equals(own),"explicit DOWN and all other own fields survive save/load");
                for(EnumFacing x:EnumFacing.values())for(EnumFacing y:EnumFacing.values()){
                    Vec3d vx=vector(x),vy=vector(y);if(vx.dotProduct(vy)!=0)continue;int[] basis=ArmGridMapping.of(BlockPos.ORIGIN,Vec3d.ZERO,new Vec3d[]{vx,vy,vx.crossProduct(vy)}).basis();
                    VehicleStructure.Cell original=new VehicleStructure.Cell(BlockPos.ORIGIN,state,raw);IBlockState next=ArmGridTiles.state(original,basis);NBTTagCompound data=ArmGridTiles.data(original,basis);VehicleStructure.Cell moved=new VehicleStructure.Cell(BlockPos.ORIGIN,next,data);
                    need(ArmGridTiles.state(moved,ArmLooseGearData.inverse(basis))==state&&ArmGridTiles.data(moved,ArmLooseGearData.inverse(basis)).equals(raw),"exact marked-data inverse, including rotations through DOWN");need(data.getCompoundTag("ForgeCaps").equals(caps),"opaque capability retained");profiles++;
                }
                for(int turns=1;turns<4;turns++){
                    NBTTagCompound owned=raw.copy();owned.setLong("GearOwnerOffset",new BlockPos(1,2,-3).toLong());owned.setInteger("GearSourceFootFacing",facing.getHorizontalIndex());if(mount!=EnumFacing.DOWN)owned.setString(ArmFootComponentFrame.SOURCE_KEY,mount.name());
                    VehicleStructure.Cell cell=new VehicleStructure.Cell(BlockPos.ORIGIN,state.withProperty(GearBlock.ASSEMBLED,true),owned);IBlockState next=GearData.rotateComponentState(cell,turns);NBTTagCompound data=owned.copy();GearData.rotate(data,turns);EnumFacing nextMount=EnumFacing.valueOf(data.getString("ArmMountDirection"));
                    for(Vec3d point:new Vec3d[]{new Vec3d(.1,.2,.3),new Vec3d(.7,.4,.8),new Vec3d(.5,.5,.5)}){Vec3d expected=physical(mount,facing,point).subtract(.5,.5,.5);for(int i=0;i<turns;i++)expected=new Vec3d(-expected.z,expected.y,expected.x);expected=expected.addVector(.5,.5,.5);need(expected.squareDistanceTo(physical(nextMount,next.getValue(GearBlock.FACING),point))<1e-15,"actual mounted geometry covariance after bound vehicle yaw");}
                    need(data.getInteger("GearSourceFootFacing")==next.getValue(GearBlock.FACING).getHorizontalIndex(),"cached source facing follows the same local plane");GearData.rotate(data,4-turns);need(data.equals(owned),"bound original data inverse");
                }
                NBTTagCompound legacy=raw.copy();legacy.removeTag(ArmFootComponentFrame.KEY);need(GearData.rotateComponentState(new VehicleStructure.Cell(BlockPos.ORIGIN,state,legacy),1)==null,"old missing marker retains legacy world metadata policy");
            }
        }
        need(profiles==1728,"all three feet six mounts four facings and24 proper rotations");
        System.out.println("Regular foot component frame PASS profiles="+profiles+" checks="+checks+" actual bound geometry covariance, explicit-DOWN persistence, exact opaque inverse, legacy absence");
    }
    private RegularFootFrameChecks(){}
}
