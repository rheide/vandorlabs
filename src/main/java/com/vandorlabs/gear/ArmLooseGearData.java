package com.vandorlabs.gear;

import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Exact frame mapping for an unowned landing foot and its native component cells; opaque capabilities remain untouched. */
final class ArmLooseGearData {
    private static final String[] OWNERSHIP={"GearOwnerOffset","ArmPayload","ArmStoredSections",GearTaxiState.KEY,"GearParkingHold"};
    private static final String[] LOCAL_FACINGS={"GearFootFacing","GearSourceFootFacing","ArmFootFacing"};
    static boolean largeWheel(IBlockState state){return state.getBlock().getClass()==LargeGearBlock.class&&((GearBlock)state.getBlock()).kind()==GearBlock.Kind.WHEEL&&state.getPropertyKeys().size()==3;}
    static boolean foot(IBlockState state){if(largeWheel(state))return true;if(state.getBlock().getClass()!=GearBlock.class||state.getPropertyKeys().size()!=2)return false;GearBlock block=(GearBlock)state.getBlock();return block.sizeClass()==1&&(block.kind()==GearBlock.Kind.PLATE||block.kind()==GearBlock.Kind.SKI||block.kind()==GearBlock.Kind.WHEEL);}
    static boolean admitted(VehicleStructure.Cell cell){
        if(!foot(cell.state)||!cell.hasTileData()||cell.state.getValue(GearBlock.ASSEMBLED)||cell.tileBoolean("GearController"))return false;
        net.minecraft.util.ResourceLocation id=TileEntity.getKey(GearTileEntity.class);if(id==null||!id.toString().equals(cell.tileString("id"))||!cell.hasTileKey("ArmMountDirection",8))return false;
        for(String key:OWNERSHIP)if(cell.hasTileKey(key))return false;
        if(!ArmFootComponentFrame.valid(cell.tileData())||largeWheel(cell.state)&&cell.hasTileKey(ArmFootComponentFrame.KEY)&&ArmFootComponentFrame.frame(cell)==EnumFacing.DOWN)return false;
        try{EnumFacing.valueOf(cell.tileString("ArmMountDirection"));if(cell.hasTileKey("GearDirection")&&!EnumFacing.valueOf(cell.tileString("GearDirection")).getAxis().isHorizontal())return false;}catch(IllegalArgumentException invalid){return false;}
        if(!largeWheel(cell.state)&&cell.hasTileKey(ArmFootComponentFrame.KEY,8)&&!cell.tileString(ArmFootComponentFrame.KEY).equals(cell.tileString("ArmMountDirection")))return false;
        for(String key:LOCAL_FACINGS)if(cell.hasTileKey(key)&&(!cell.hasTileKey(key,3)||cell.tileInteger(key)<0||cell.tileInteger(key)>3))return false;
        return true;
    }
    static EnumFacing mapFacing(EnumFacing facing,int[] basis){int x=facing.getFrontOffsetX(),y=facing.getFrontOffsetY(),z=facing.getFrontOffsetZ();return EnumFacing.getFacingFromVector(basis[0]*x+basis[1]*y+basis[2]*z,basis[3]*x+basis[4]*y+basis[5]*z,basis[6]*x+basis[7]*y+basis[8]*z);}
    static EnumFacing localFacing(EnumFacing oldMount,EnumFacing newMount,EnumFacing local,int[] basis){
        BlockPos world=ArmOrientation.vector(oldMount,new BlockPos(local.getDirectionVec()));BlockPos moved=new BlockPos(basis[0]*world.getX()+basis[1]*world.getY()+basis[2]*world.getZ(),basis[3]*world.getX()+basis[4]*world.getY()+basis[5]*world.getZ(),basis[6]*world.getX()+basis[7]*world.getY()+basis[8]*world.getZ());BlockPos next=ArmOrientation.inverse(newMount,moved);EnumFacing result=EnumFacing.getFacingFromVector(next.getX(),next.getY(),next.getZ());if(!result.getAxis().isHorizontal())throw new IllegalArgumentException("Landing foot frame is not planar");return result;
    }
    static IBlockState state(VehicleStructure.Cell cell,int[] basis){
        if(!admitted(cell))return null;EnumFacing mount=largeWheel(cell.state)?ArmFootComponentFrame.frame(cell):EnumFacing.valueOf(cell.tileString("ArmMountDirection")),next=mapFacing(mount,basis);return cell.state.withProperty(GearBlock.FACING,localFacing(mount,next,cell.state.getValue(GearBlock.FACING),basis));
    }
    static NBTTagCompound data(VehicleStructure.Cell cell,int[] basis){
        if(!admitted(cell))throw new IllegalArgumentException("Only an unowned supported landing foot can rotate at an arm endpoint");
        EnumFacing mount=EnumFacing.valueOf(cell.tileString("ArmMountDirection")),next=mapFacing(mount,basis);NBTTagCompound data=cell.tileData();data.setString("ArmMountDirection",next.name());
        if(data.hasKey("GearDirection",8))data.setString("GearDirection",localFacing(mount,next,EnumFacing.valueOf(data.getString("GearDirection")),basis).name());
        if(data.hasKey("ArmKinkDirection")){if(!data.hasKey("ArmKinkDirection",8))throw new IllegalArgumentException("Invalid kink direction");data.setString("ArmKinkDirection",localFacing(mount,next,EnumFacing.valueOf(data.getString("ArmKinkDirection")),basis).name());}
        for(String key:LOCAL_FACINGS)if(data.hasKey(key,3))data.setInteger(key,localFacing(mount,next,EnumFacing.getHorizontal(data.getInteger(key)),basis).getHorizontalIndex());
        if(largeWheel(cell.state))ArmFootComponentFrame.write(data,mapFacing(ArmFootComponentFrame.frame(cell),basis));
        else if(cell.hasTileKey(ArmFootComponentFrame.KEY,8))data.setString(ArmFootComponentFrame.KEY,mapFacing(ArmFootComponentFrame.frame(cell),basis).name());
        return data;
    }
    static int[] inverse(int[] basis){int[] result=new int[9];for(int row=0;row<3;row++)for(int col=0;col<3;col++)result[row*3+col]=basis[col*3+row];return result;}
    private ArmLooseGearData(){}
}
