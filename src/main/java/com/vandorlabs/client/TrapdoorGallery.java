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
        if(scene.startsWith("followup_")){buildFollowup(world,scene,x,y);return;}
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
    static TileEntityProgrammableTrapdoor place(World world,BlockPos pos,boolean diagonal,int texture,int mode,boolean sliding,EnumFacing facing) {
        return place(world,pos,diagonal,texture,mode,sliding,facing,false);
    }
    static TileEntityProgrammableTrapdoor place(World world,BlockPos pos,boolean diagonal,int texture,int mode,boolean sliding,EnumFacing facing,boolean inverted) {
        BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)(diagonal?ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR:ModBlocks.PROGRAMMABLE_TRAPDOOR);
        TileEntityProgrammableTrapdoor prototype=diagonal?new TileEntityProgrammableDiagonalTrapdoor():new TileEntityProgrammableTrapdoor();
        prototype.configure(texture,mode,sliding,0,0);
        ItemStack stack=new ItemStack(block);stack.setTagInfo("BlockEntityTag",prototype.itemSettings());
        if(!((ItemBlock)stack.getItem()).placeBlockAt(stack,null,world,pos,EnumFacing.UP,.5F,.5F,.5F,block.getDefaultState().withProperty(BlockTrapDoor.FACING,facing).withProperty(BlockTrapDoor.HALF,inverted?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM)))throw new IllegalStateException("followup trapdoor placement failed");
        return (TileEntityProgrammableTrapdoor)world.getTileEntity(pos);
    }
    static void buildFollowup(World world,String scene,int x,int y) {
        BlockPos base=new BlockPos(x-1,y+2,-18);
        boolean diagonal=scene.contains("diagonal"),custom=scene.contains("custom"),fit=scene.endsWith("fit");
        int texture=custom?CustomBlockMaterials.choice(new ItemStack(net.minecraft.init.Items.OAK_DOOR)):ScreenHousingTextures.doorIndex(1,1);
        if(scene.contains("slide_wall")) {
            TileEntityProgrammableTrapdoor leaf=place(world,base,true,texture,0,true,EnumFacing.NORTH);
            BlockPos support=base.west();world.setBlockState(support,ModBlocks.PROGRAMMABLE_DIAGONAL_WALL.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.NORTH),3);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(support)).setDiagonalGeometry(0,0);
            leaf.requestOpen(true);
        } else if(scene.contains("rotate_neighbors")) {
            TileEntityProgrammableTrapdoor leaf=place(world,base,false,texture,0,false,EnumFacing.NORTH);
            for(BlockPos support:new BlockPos[]{base.north(),base.west(),base.east(),base.down()})world.setBlockState(support,net.minecraft.init.Blocks.STONE.getDefaultState(),3);
            leaf.requestOpen(true);
        } else if(scene.contains("next_")) {
            TileEntityProgrammableTrapdoor leaf=place(world,base,false,texture,0,false,EnumFacing.NORTH);
            world.setBlockState(base.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
            leaf.configureGroup(texture,0,false,0,0,false,true,true,EnumFacing.NORTH);leaf.requestOpen(scene.endsWith("open"));
        } else {
            TileEntityProgrammableTrapdoor first=null;
            for(int row=0;row<2;row++)for(int col=0;col<2;col++) {
                BlockPos pos=base.add(col,diagonal?row:0,diagonal?0:row);
                TileEntityProgrammableTrapdoor leaf=place(world,pos,diagonal,texture,0,false,EnumFacing.NORTH,diagonal && row==1);if(first==null)first=leaf;
            }
            if(world.isRemote)return;
            if(first.group().size()!=4)throw new IllegalStateException("followup trapdoor square not joined");
            first.configureGroup(texture,0,false,0,0,false,false,!fit,EnumFacing.NORTH);
        }
        System.out.println("[vandorlabs][reprolab] trapdoor-followup PASS "+scene);
    }
}
