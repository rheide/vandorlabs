package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Real item placement, grouping and open-state contracts in the screenshot world. */
final class TrapdoorGallery {
    static void build(World world,String scene,int x,int y) {
        boolean flat=scene.startsWith("flat"),stagger=scene.startsWith("stagger"),v=scene.startsWith("v_");
        boolean sliding=scene.contains("sliding"),open=scene.endsWith("open");
        BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)(flat?ModBlocks.PROGRAMMABLE_TRAPDOOR:ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR);
        int width=flat?2:stagger||v?2:5,height=flat?4:2;
        BlockPos origin=new BlockPos(x-width/2,y+2,-18);
        TileEntityProgrammableTrapdoor first=null;
        for(int row=0;row<height;row++)for(int col=0;col<width;col++) {
            BlockPos at=flat?origin.add(col,0,row):origin.add(col,row,stagger?row:0);
            TileEntityProgrammableTrapdoor prototype=flat?new TileEntityProgrammableTrapdoor():new TileEntityProgrammableDiagonalTrapdoor();
            prototype.configure(4,flat?1:stagger?1:0,sliding,0,0);
            ItemStack stack=new ItemStack(block);stack.setTagInfo("BlockEntityTag",prototype.itemSettings());
            IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,EnumFacing.NORTH)
                    .withProperty(BlockTrapDoor.HALF,v && row>0?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM);
            if(!((ItemBlock)stack.getItem()).placeBlockAt(stack,null,world,at,EnumFacing.UP,.5F,.5F,.5F,state))throw new IllegalStateException("trapdoor gallery placement failed");
            TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)world.getTileEntity(at);
            if(first==null)first=leaf;
        }
        if(world.isRemote)return;
        if(first.group().size()!=width*height)throw new IllegalStateException("trapdoor gallery group failed: "+scene+" size="+first.group().size());
        first.requestOpen(open);
        for(TileEntityProgrammableTrapdoor leaf:first.group())if(world.getBlockState(leaf.getPos()).getValue(BlockTrapDoor.OPEN)!=open)throw new IllegalStateException("trapdoor gallery opening failed");
        System.out.println("[vandorlabs][reprolab] trapdoor-assembly PASS "+scene);
    }
}
