package com.vandorlabs.coupler;

import com.vandorlabs.geometry.CardinalFrame;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/** Only the Coupler's own perpendicular-up field is interpreted; opaque data stays untouched. */
public final class CouplerFrameData {
    public static final String KEY="CouplerFrameUp";
    private CouplerFrameData(){}
    static Integer read(NBTTagCompound tag){
        if(!tag.hasKey(KEY))return null;
        if(!tag.hasKey(KEY,3)||tag.getInteger(KEY)<0||tag.getInteger(KEY)>5)throw new IllegalArgumentException("Malformed Coupler frame");
        return tag.getInteger(KEY);
    }
    public static EnumFacing up(IBlockState state,NBTTagCompound tag){return up(state,read(tag));}
    public static EnumFacing up(com.vandorlabs.vehicle.VehicleStructure.Cell cell){
        if(cell.hasTileKey(KEY)&&!cell.hasTileKey(KEY,3))throw new IllegalArgumentException("Malformed Coupler frame");
        return up(cell.state,cell.hasTileKey(KEY)?Integer.valueOf(cell.tileInteger(KEY)):null);
    }
    static EnumFacing up(IBlockState state,Integer stored){
        EnumFacing forward=state.getValue(BlockCoupler.FACING);int up=stored==null?CardinalFrame.defaultUp(forward.getIndex()):stored;
        CardinalFrame.of(forward.getIndex(),up);
        if(stored!=null&&stored==CardinalFrame.defaultUp(forward.getIndex()))throw new IllegalArgumentException("Noncanonical Coupler frame");
        if(state.getBlock() instanceof BlockCouplerSlab&&EnumFacing.getFront(up).getAxis()!=EnumFacing.Axis.Y)throw new IllegalArgumentException("Slab Coupler must remain horizontal");
        return EnumFacing.getFront(up);
    }
    public static NBTTagCompound mapped(IBlockState before,NBTTagCompound original,int[] basis){
        if(basis==null||basis.length!=9)throw new IllegalArgumentException("Coupler frame basis unavailable");
        EnumFacing forward=map(before.getValue(BlockCoupler.FACING),basis),up=map(up(before,original),basis);
        CardinalFrame.of(forward.getIndex(),up.getIndex());
        NBTTagCompound next=original.copy();
        if(up.getIndex()==CardinalFrame.defaultUp(forward.getIndex()))next.removeTag(KEY);else next.setInteger(KEY,up.getIndex());
        return next;
    }
    public static void yaw(IBlockState before,NBTTagCompound data,net.minecraft.util.Rotation rotation){
        EnumFacing forward=rotation.rotate(before.getValue(BlockCoupler.FACING)),up=rotation.rotate(up(before,data));
        if(up.getIndex()==CardinalFrame.defaultUp(forward.getIndex()))data.removeTag(KEY);else data.setInteger(KEY,up.getIndex());
    }
    /** Control edits retain the geometry belonging to that exact canonical/native occurrence. */
    public static void retain(NBTTagCompound geometry,NBTTagCompound controls){
        if(geometry.hasKey(KEY)){read(geometry);controls.setTag(KEY,geometry.getTag(KEY).copy());}else controls.removeTag(KEY);
    }
    private static EnumFacing map(EnumFacing side,int[] m){int x=side.getFrontOffsetX(),y=side.getFrontOffsetY(),z=side.getFrontOffsetZ();return EnumFacing.getFacingFromVector(m[0]*x+m[1]*y+m[2]*z,m[3]*x+m[4]*y+m[5]*z,m[6]*x+m[7]*y+m[8]*z);}
}
