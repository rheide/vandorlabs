package com.vandorlabs.blocks;

import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.*;

/** Two independent bucket volumes; full fluid NBT survives capture and save/load. */
public final class ContainedFluids {
    private static final java.util.concurrent.atomic.AtomicLong REVISION=new java.util.concurrent.atomic.AtomicLong();
    public static long revision(){return REVISION.get();}
    private final TileEntity owner;
    private final NBTTagCompound[] sides=new NBTTagCompound[2];
    WaterloggedParts.Volume geometry;
    net.minecraft.block.state.IBlockState geometryState;
    int geometryMapping;
    net.minecraft.util.math.BlockPos geometryPos;
    long geometryTick=Long.MIN_VALUE;
    public ContainedFluids(TileEntity owner){this.owner=owner;}
    public boolean any(){return sides[0]!=null||sides[1]!=null;}
    public FluidStack get(int side){return sides[side]==null?null:FluidStack.loadFluidStackFromNBT(sides[side]);}
    public boolean occupied(int side){return sides[side]!=null;}
    public void set(int side,FluidStack fluid){sides[side]=fluid==null?null:fluid.copy().writeToNBT(new NBTTagCompound());REVISION.incrementAndGet();owner.markDirty();geometryTick=Long.MIN_VALUE;if(owner.getWorld()!=null){net.minecraft.world.World world=owner.getWorld();net.minecraft.util.math.BlockPos pos=owner.getPos();world.checkLight(pos);world.notifyBlockUpdate(pos,world.getBlockState(pos),world.getBlockState(pos),3);}}
    public void write(NBTTagCompound tag){NBTTagCompound data=new NBTTagCompound();for(int i=0;i<2;i++)if(sides[i]!=null)data.setTag("Side"+i,sides[i].copy());tag.setTag("ContainedFluids",data);}
    public void read(NBTTagCompound tag){NBTTagCompound a=sides[0],b=sides[1];for(int i=0;i<2;i++)sides[i]=tag.getCompoundTag("ContainedFluids").hasKey("Side"+i,10)?tag.getCompoundTag("ContainedFluids").getCompoundTag("Side"+i).copy():null;if(!tag.hasKey("ContainedFluids",10)&&tag.getBoolean("Waterlogged"))sides[0]=new FluidStack(FluidRegistry.WATER,1000).writeToNBT(new NBTTagCompound());if(!java.util.Objects.equals(a,sides[0])||!java.util.Objects.equals(b,sides[1]))REVISION.incrementAndGet();geometryTick=Long.MIN_VALUE;geometry=null;}
}
