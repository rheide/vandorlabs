package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockTelescopicLandingGear;
import com.vandorlabs.tiles.TileEntityLandingGear;
import net.minecraft.util.math.BlockPos;
import net.minecraft.init.Blocks;

/** Actual reservations and configuration rollback for the corner-aligned wheel. */
final class LandingGearFootprintChecks {
    static void run() {
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityLandingGear.class,new net.minecraft.util.ResourceLocation("minecraft:vandorlabs_gear_footprint"));
        BlockTelescopicLandingGear block=new BlockTelescopicLandingGear("landing_gear");
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        BlockPos pos=new BlockPos(10,100,10);world.setBlockState(pos,block.getDefaultState());
        TileEntityLandingGear tile=(TileEntityLandingGear)world.getTileEntity(pos);
        world.setBlockState(pos.west(),Blocks.STONE.getDefaultState());
        require(tile.configure(0,0,32,4),"2x2 rejected a block outside its footprint");
        for(int x=0;x<=1;x++)for(int z=0;z<=1;z++)for(int down=0;down<=1;down++) {
            if(x==0&&z==0&&down==0)continue;
            require(block.root(world,pos.add(x,-down,z))==tile,"missing corner reservation");
        }
        require(world.getBlockState(pos.west()).getBlock()==Blocks.STONE,"touched neighboring structure");
        require(!tile.configure(0,0,32,3) && tile.getSize()==4,"failed centered reservation changed configuration");
        BlockPos coverPos=pos.down().west();
        world.setBlockState(coverPos,com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState().withProperty(com.vandorlabs.blocks.BlockProgrammableTrapdoor.FACING,net.minecraft.util.EnumFacing.EAST));
        com.vandorlabs.tiles.TileEntityProgrammableTrapdoor cover=(com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)world.getTileEntity(coverPos);cover.setCover(true);
        java.util.List<BlockPos> covers=new java.util.ArrayList<>();covers.add(coverPos);
        for(int side=0;side<2;side++)for(int row=0;row<2;row++) {
            BlockPos mount=pos.add(side==0?-1:2,-1,row);if(mount.equals(coverPos))continue;
            world.setBlockState(mount,com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState().withProperty(com.vandorlabs.blocks.BlockProgrammableTrapdoor.FACING,side==0?net.minecraft.util.EnumFacing.EAST:net.minecraft.util.EnumFacing.WEST));
            com.vandorlabs.tiles.TileEntityProgrammableTrapdoor panel=(com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)world.getTileEntity(mount);panel.setCover(true);panel.configure(0,0,false,1,0);covers.add(mount);
        }
        cover.configure(0,0,false,com.vandorlabs.persistence.SpaceDoorData.TRIGGER_REDSTONE_ON,0);
        require(cover.corners(world.getBlockState(coverPos),0)[0][0]>=1,"closed cover does not span the adjacent cell");
        require(block.setExtended(world,pos,true),"corner wheel could not extend");
        for(BlockPos mount:covers)require(world.getBlockState(mount).getValue(com.vandorlabs.blocks.BlockProgrammableTrapdoor.OPEN),"cover did not automatically open for gear");
        for(int i=0;i<45;i++)tile.update();
        require(tile.progress==2,"extension progress");
        require(block.setExtended(world,pos,false),"corner wheel could not retract");
        require(world.getBlockState(coverPos).getValue(com.vandorlabs.blocks.BlockProgrammableTrapdoor.OPEN),"cover closed before wheel retracted");
        for(int i=0;i<45;i++)tile.update();
        for(BlockPos mount:covers)require(!world.getBlockState(mount).getValue(com.vandorlabs.blocks.BlockProgrammableTrapdoor.OPEN) && world.getTileEntity(mount) instanceof com.vandorlabs.tiles.TileEntityProgrammableTrapdoor,"cover failed to survive and close after retraction");
        require(world.getTileEntity(coverPos)==cover,"reservation cleanup removed the cover owner");
        require(tile.progress==0 && world.isAirBlock(pos.down(2)),"retraction reservation cleanup");
        require(cover.itemSettings().getBoolean("TrapdoorCover"),"cover item settings");
        BlockPos copied=coverPos.north(3);world.setBlockState(copied,com.vandorlabs.blocks.ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState());
        com.vandorlabs.items.ProgrammableSettings.apply(world,copied,com.vandorlabs.items.DuplifierApplyOptions.selected(com.vandorlabs.items.ProgrammableSettings.capture(world,coverPos),com.vandorlabs.items.DuplifierApplyOptions.ALL));
        require(((com.vandorlabs.tiles.TileEntityProgrammableTrapdoor)world.getTileEntity(copied)).isCover(),"Duplifier cover movement");
        net.minecraft.nbt.NBTTagCompound saved=tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
        TileEntityLandingGear restored=new TileEntityLandingGear();restored.readFromNBT(saved);
        require(restored.getSize()==4,"saved corner alignment");
        System.out.println("PASS: extra-large 2x2 footprint, extension/retraction, obstruction rollback and saved alignment");
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
