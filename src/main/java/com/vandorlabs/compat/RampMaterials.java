package com.vandorlabs.compat;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Narrow support for IE's inert slab tile; inventories and machines stay excluded. */
public final class RampMaterials {
    private RampMaterials() { }
    public static boolean isIESlab(World world, BlockPos pos) {
        TileEntity tile=world.getTileEntity(pos);
        return tile!=null && tile.getClass().getName().equals(
                "blusunrize.immersiveengineering.common.blocks.TileEntityIESlab");
    }
    public static boolean matches(World world, BlockPos seed, BlockPos candidate) {
        if (!world.getBlockState(seed).equals(world.getBlockState(candidate))) return false;
        TileEntity a=world.getTileEntity(seed),b=world.getTileEntity(candidate);
        if (a instanceof TileEntityAnimatedScreenSelector) {
            if (!(b instanceof TileEntityAnimatedScreenSelector)) return false;
            TileEntityAnimatedScreenSelector x=(TileEntityAnimatedScreenSelector)a,y=(TileEntityAnimatedScreenSelector)b;
            if (x.isSlabTileSides()!=y.isSlabTileSides()) return false;
            for (int face=0;face<6;face++)
                if (x.getFaceTextures().texture(face,x.getHousingTexture())
                        != y.getFaceTextures().texture(face,y.getHousingTexture())) return false;
        }
        if (isIESlab(world,seed)) {
            if (!isIESlab(world,candidate)) return false;
            return a.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).getInteger("slabType")
                    == b.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).getInteger("slabType");
        }
        return true;
    }
}
