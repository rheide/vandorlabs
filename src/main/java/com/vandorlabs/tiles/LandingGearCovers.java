package com.vandorlabs.tiles;

import com.vandorlabs.blocks.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Loaded-only cover updates when a wheel changes target or completes its motion. */
public final class LandingGearCovers {
    private LandingGearCovers(){ }
    public static boolean needsOpen(TileEntityProgrammableTrapdoor cover) {
        World world=cover.getWorld();if(world==null||!cover.isCover())return false;
        BlockPos target=cover.getPos().offset(world.getBlockState(cover.getPos()).getValue(BlockProgrammableTrapdoor.FACING));
        if(!world.isBlockLoaded(target))return false;
        if(!(world.getBlockState(target).getBlock() instanceof BlockTelescopicLandingGear))return false;
        TileEntityLandingGear gear=((BlockTelescopicLandingGear)world.getBlockState(target).getBlock()).root(world,target);
        return gear!=null && (gear.progress>0 || gear.getExtensionPixels()>0 && world.getBlockState(gear.getPos()).getValue(BlockTelescopicLandingGear.EXTENDED));
    }
    public static void refresh(World world,BlockPos root) {
        if(world.isRemote)return;
        for(int y=-5;y<=0;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            BlockPos pos=root.add(x,y,z);if(!world.isBlockLoaded(pos))continue;
            TileEntity tile=world.getTileEntity(pos);
            if(tile instanceof TileEntityProgrammableTrapdoor && ((TileEntityProgrammableTrapdoor)tile).isCover())
                ((TileEntityProgrammableTrapdoor)tile).evaluatePower(true);
        }
    }
}
