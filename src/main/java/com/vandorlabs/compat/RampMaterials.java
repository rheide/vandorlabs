package com.vandorlabs.compat;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Narrow support for IE's inert slab tile; inventories and machines stay excluded. */
public final class RampMaterials {
    private RampMaterials() { }
    /** These families have an entire cube of glazing; panes and programmable panels do not. */
    public static boolean isFullGlass(net.minecraft.block.state.IBlockState state) {
        net.minecraft.block.Block block=state.getBlock();
        return block==net.minecraft.init.Blocks.GLASS || block==net.minecraft.init.Blocks.STAINED_GLASS
                || block instanceof com.vandorlabs.blocks.BlockCockpitGlass;
    }
    public static boolean isIESlab(World world, BlockPos pos) {
        TileEntity tile=world.getTileEntity(pos);
        return tile!=null && tile.getClass().getName().equals(
                "blusunrize.immersiveengineering.common.blocks.TileEntityIESlab");
    }
    /** IE scaffold blocks are full-width inert structures despite reporting a non-full cube. */
    public static boolean isScaffolding(net.minecraft.block.state.IBlockState state){
        net.minecraft.util.ResourceLocation id=state.getBlock().getRegistryName();
        if(id==null||!id.getResourceDomain().equals("immersiveengineering")||state.getBlock().hasTileEntity(state))return false;
        int meta=state.getBlock().getMetaFromState(state);
        if(id.getResourcePath().equals("wooden_decoration"))return meta==1;
        return id.getResourcePath().equals("metal_decoration1")&&(meta>=1&&meta<=3||meta>=5&&meta<=7);
    }
    public static boolean matches(World world, BlockPos seed, BlockPos candidate) {
        return matches(world, seed, candidate, true);
    }
    public static boolean matches(World world, BlockPos seed, BlockPos candidate, boolean matchTextures) {
        if (!world.getBlockState(seed).equals(world.getBlockState(candidate))) return false;
        TileEntity a=world.getTileEntity(seed),b=world.getTileEntity(candidate);
        if (matchTextures && a instanceof TileEntityAnimatedScreenSelector) {
            if (!(b instanceof TileEntityAnimatedScreenSelector)) return false;
            TileEntityAnimatedScreenSelector x=(TileEntityAnimatedScreenSelector)a,y=(TileEntityAnimatedScreenSelector)b;
            if(world.getBlockState(seed).getBlock() instanceof com.vandorlabs.blocks.BlockProgrammableStairs
                    && x.getSideTexture()!=y.getSideTexture())return false;
            if (x.getMainTextureLayout()!=y.getMainTextureLayout()) return false;
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
