package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Focused live fixtures: glass single, native paired, and replacement-material large. */
final class DoorMotionGallery {
    static void build(World world,String shot,int x,int y) {
        String[] bits=shot.split("_");boolean large=bits[3].equals("large"),paired=large || bits[3].equals("paired");
        int direction=bits[4].equals("left")?4:bits[4].equals("right")?5:bits[4].equals("horizontal")?6:7;
        boolean open=bits[5].equals("open");
        Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",large?"large_programmable_door":"programmable_door"));
        BlockPos anchor=new BlockPos(x-(paired?1:0),y,-18);
        int columns=large?3:paired?2:1,rows=large?3:2;
        for(int col=0;col<columns;col++)for(int row=0;row<rows;row++) {
            BlockPos at=anchor.add(col,row,0);
            IBlockState state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.SOUTH)
                    .withProperty(BlockVandorDoor.HALF,row==0?BlockDoor.EnumDoorHalf.LOWER:BlockDoor.EnumDoorHalf.UPPER)
                    .withProperty(BlockVandorDoor.HINGE,col==0?BlockDoor.EnumHingePosition.RIGHT:BlockDoor.EnumHingePosition.LEFT)
                    .withProperty(BlockVandorDoor.OPEN,open);
            world.setBlockState(at,state,2);
            TileEntitySpaceDoor tile=(TileEntitySpaceDoor)world.getTileEntity(at);
            if(large)((TileEntityLargeProgrammableDoor)tile).assign(anchor);
            if(row==0 && (!large || col==0)) {
                tile.configure(paired?2:0,1,true,direction,true,true,true,0,false);
                tile.setPlacementDepth(0);
                if(large){tile.setFaceTexture(java.util.Arrays.asList(ScreenHousingTextures.IDS).indexOf("light_alloy_hull"));tile.setTileTexture(true);}
            }
        }
        System.out.println("[vandorlabs][reprolab] door-motion-scene PASS "+shot);
    }
}
