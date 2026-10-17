package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

final class WaterloggedRuntimeChecks {
    static void run(World world){
        BlockPos pos=new BlockPos(146,230,146);
        try{
            for(Block block:new Block[]{ModBlocks.PROGRAMMABLE_SLAB,ModBlocks.PROGRAMMABLE_STAIRS})for(boolean upper:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.HORIZONTALS){
                IBlockState state=block.getDefaultState();
                state=block instanceof BlockProgrammableSlab?state.withProperty(BlockProgrammableSlab.HALF,upper?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM):state.withProperty(BlockStairs.HALF,upper?BlockStairs.EnumHalf.TOP:BlockStairs.EnumHalf.BOTTOM).withProperty(BlockStairs.FACING,facing);
                world.setBlockState(pos,state);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);tile.setWaterlogged(true);
                double volume=0;for(AxisAlignedBB gap:WaterloggedParts.gaps(world,pos))volume+=(gap.maxX-gap.minX)*(gap.maxY-gap.minY)*(gap.maxZ-gap.minZ);
                require(Math.abs(volume-(block instanceof BlockProgrammableSlab?.5:.25))<1e-6,"water volume follows solid shape");
                require(Boolean.TRUE.equals(block.isAABBInsideMaterial(world,pos,new AxisAlignedBB(pos),Material.WATER)),"water physics hook");
                TileEntityAnimatedScreenSelector copy=new TileEntityAnimatedScreenSelector();copy.readFromNBT(tile.writeToNBT(new NBTTagCompound()));require(copy.isWaterlogged(),"waterlogging persists");
                tile.setWaterlogged(false);require(!WaterloggedParts.wet(world,pos),"drained variant");
            }
        }finally{world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] waterlogged-parts PASS slab stair halves facings clipped-volume water-physics persistence");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
