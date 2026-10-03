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
        if(scene.startsWith("patch_")){buildPatch(world,scene,x,y);return;}
        if(scene.startsWith("followup_")){buildFollowup(world,scene,x,y);return;}
        boolean flat=scene.startsWith("flat"),stagger=scene.startsWith("stagger"),v=scene.startsWith("v_");
        boolean sliding=scene.contains("sliding"),open=scene.endsWith("open");
        BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)(flat?ModBlocks.PROGRAMMABLE_TRAPDOOR:ModBlocks.PROGRAMMABLE_DIAGONAL_TRAPDOOR);
        int width=flat?2:stagger||v?2:5,height=flat?4:2;
        int mode=flat?1:scene.startsWith("stagger_halfwidth")?0:scene.startsWith("stagger_shallow")?2:stagger?1:0;
        BlockPos origin=new BlockPos(x-width/2,y+2,-18);
        TileEntityProgrammableTrapdoor first=null;
        for(int row=0;row<height;row++)for(int col=0;col<width;col++) {
            BlockPos at=flat?origin.add(col,0,row):origin.add(col,row,stagger?row:0);
            TileEntityProgrammableTrapdoor prototype=flat?new TileEntityProgrammableTrapdoor():new TileEntityProgrammableDiagonalTrapdoor();
            prototype.configure(4,mode,sliding,0,0);
            ItemStack stack=new ItemStack(block);stack.setTagInfo("BlockEntityTag",prototype.itemSettings());
            IBlockState state=block.getDefaultState().withProperty(BlockTrapDoor.FACING,EnumFacing.NORTH)
                    .withProperty(BlockTrapDoor.HALF,v && row>0?BlockTrapDoor.DoorHalf.TOP:BlockTrapDoor.DoorHalf.BOTTOM);
            if(!((ItemBlock)stack.getItem()).placeBlockAt(stack,null,world,at,EnumFacing.UP,.5F,.5F,.5F,state))throw new IllegalStateException("trapdoor gallery placement failed");
            TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)world.getTileEntity(at);
            if(first==null)first=leaf;
        }
        if(world.isRemote)return;
        if(first.group().size()!=width*height)throw new IllegalStateException("trapdoor gallery group failed: "+scene+" size="+first.group().size());
        if(v)checkOutside(world,first,true);
        first.requestOpen(open);
        for(TileEntityProgrammableTrapdoor leaf:first.group())if(world.getBlockState(leaf.getPos()).getValue(BlockTrapDoor.OPEN)!=open)throw new IllegalStateException("trapdoor gallery opening failed");
        System.out.println("[vandorlabs][reprolab] trapdoor-assembly PASS "+scene);
    }
    private static void buildPatch(World world,String scene,int x,int y) {
        BlockPos base=new BlockPos(x-1,y+2,-18);int mode=scene.contains("shallow")?2:0;
        boolean stagger=scene.contains("stagger"),sliding=scene.contains("sliding"),intoWall=scene.contains("inset"),open=scene.endsWith("open");
        // Exact horizontal three-cell example: Z, Z+1, then X+1/Z+1.
        BlockPos[] cells=stagger?new BlockPos[]{base,base.south(),base.south().east().up()}:new BlockPos[]{base,base.south(),base.south().east()};
        for(BlockPos at:cells)place(world,at,true,4,mode,sliding,EnumFacing.EAST);
        TileEntityProgrammableTrapdoor root=(TileEntityProgrammableTrapdoor)world.getTileEntity(base);
        if(world.isRemote)return;
        if(root.group().size()!=3)throw new IllegalStateException("three-cell diagonal patch not joined: "+scene);
        root.configureGroup(4,mode,sliding,0,0,false,false,true,EnumFacing.EAST,intoWall);
        net.minecraft.entity.player.EntityPlayer owner=world.playerEntities.isEmpty()?null:world.playerEntities.get(0);
        if(owner==null)throw new IllegalStateException("patch interaction player missing");
        for(TileEntityProgrammableTrapdoor leaf:root.group()) {
            root.requestOpen(false);
            BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)world.getBlockState(leaf.getPos()).getBlock();
            block.onBlockActivated(world,leaf.getPos(),world.getBlockState(leaf.getPos()),owner,net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
            for(TileEntityProgrammableTrapdoor member:root.group())if(!world.getBlockState(member.getPos()).getValue(BlockTrapDoor.OPEN))throw new IllegalStateException("manual use missed a three-cell patch member");
        }
        root.requestOpen(open);checkPatch(world,scene,x,y);
    }
    static void checkPatch(World world,String scene,int x,int y) {
        int mode=scene.contains("shallow")?2:0;boolean open=scene.endsWith("open"),sliding=scene.contains("sliding"),intoWall=scene.contains("inset");
        TileEntityProgrammableTrapdoor root=(TileEntityProgrammableTrapdoor)world.getTileEntity(new BlockPos(x-1,y+2,-18));
        if(root==null || root.group().size()!=3)throw new IllegalStateException("three-cell patch client/server membership failed: "+scene);
        for(TileEntityProgrammableTrapdoor leaf:root.group())if(leaf.getPosition()!=mode || leaf.isSliding()!=sliding || leaf.isSlideIntoWall()!=intoWall || world.getBlockState(leaf.getPos()).getValue(BlockTrapDoor.OPEN)!=open)throw new IllegalStateException("three-cell patch mode/open state did not synchronize");
        System.out.println("[vandorlabs][reprolab] diagonal-partial-patch-runtime PASS "+(world.isRemote?"client ":"server ")+scene);
    }
    private static TileEntityProgrammableTrapdoor placeCover(World world,BlockPos pos,EnumFacing facing) {
        TileEntityProgrammableTrapdoor configured=new TileEntityProgrammableTrapdoor();configured.configure(4,0,false,0,0);configured.setCover(true);
        net.minecraft.nbt.NBTTagCompound tag=configured.itemSettings();tag.setInteger("TrapdoorCoverFacing",facing.getHorizontalIndex());
        ItemStack stack=new ItemStack(ModBlocks.PROGRAMMABLE_TRAPDOOR);stack.setTagInfo("BlockEntityTag",tag);
        BlockProgrammableTrapdoor block=(BlockProgrammableTrapdoor)ModBlocks.PROGRAMMABLE_TRAPDOOR;
        if(!((ItemBlock)stack.getItem()).placeBlockAt(stack,null,world,pos,EnumFacing.UP,.5F,.5F,.5F,block.getDefaultState()))throw new IllegalStateException("offset gallery placement failed");
        TileEntityProgrammableTrapdoor leaf=(TileEntityProgrammableTrapdoor)world.getTileEntity(pos);
        if(!world.getBlockState(pos).getValue(BlockTrapDoor.OPEN))throw new IllegalStateException("Next block item was placed closed");
        return leaf;
    }
    static void checkOpposing(World world,int x,int y) {
        BlockPos base=new BlockPos(x-1,y+2,-18);
        TileEntityProgrammableTrapdoor a=(TileEntityProgrammableTrapdoor)world.getTileEntity(base),b=(TileEntityProgrammableTrapdoor)world.getTileEntity(base.north(3));
        if(a==null || b==null || a.coverOverhang()!=0 || b.coverOverhang()!=0)throw new IllegalStateException("opposing covers did not suppress overhang");
        net.minecraft.util.math.AxisAlignedBB first=com.vandorlabs.tiles.OffsetTrapdoorInteractions.bounds(a),second=com.vandorlabs.tiles.OffsetTrapdoorInteractions.bounds(b);
        if(first.intersects(second))throw new IllegalStateException("opposing covers intersect");
        if(!world.getBlockState(base).getValue(BlockTrapDoor.OPEN) && Math.abs(first.minZ-second.maxZ)>2*com.vandorlabs.render.TrapdoorGeometry.EDGE_CLEARANCE+1e-8)throw new IllegalStateException("opposing covers leave a visible gap");
        System.out.println("[vandorlabs][reprolab] opposing-next-block-runtime PASS "+(world.isRemote?"client":"server"));
    }
    static void checkOutside(World world,TileEntityProgrammableTrapdoor root,boolean convex) {
        IBlockState rootState=world.getBlockState(root.getPos());
        double[][] surface=BlockProgrammableDiagonalTrapdoor.corners(rootState,(TileEntityProgrammableDiagonalTrapdoor)root,0);
        EnumFacing rise=EnumFacing.getFacingFromVector((float)(surface[2][0]-surface[0][0]),0,(float)(surface[2][2]-surface[0][2]));
        EnumFacing outward=convex?rise:rise.getOpposite();
        for(TileEntityProgrammableTrapdoor raw:root.group()) {
            TileEntityProgrammableDiagonalTrapdoor leaf=(TileEntityProgrammableDiagonalTrapdoor)raw;
            IBlockState state=world.getBlockState(leaf.getPos());
            double[][] closed=BlockProgrammableDiagonalTrapdoor.corners(state,leaf,0);
            for(double pose:new double[]{.25,.5,.75,1}) {
                double[][] moving=BlockProgrammableDiagonalTrapdoor.corners(state,leaf,pose);double distance=0;
                for(int i=0;i<8;i++)distance+=(moving[i][0]-closed[i][0])*outward.getFrontOffsetX()+(moving[i][2]-closed[i][2])*outward.getFrontOffsetZ();
                if(distance<=0)throw new IllegalStateException("diagonal group row opens inside at pose "+pose);
            }
        }
        System.out.println("[vandorlabs][reprolab] diagonal-outward-runtime PASS "+(world.isRemote?"client":"server"));
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
        if(scene.contains("opposing_next_")) {
            if(world.isRemote)return;
            TileEntityProgrammableTrapdoor a=placeCover(world,base,EnumFacing.NORTH),b=placeCover(world,base.north(3),EnumFacing.SOUTH);
            boolean open=scene.endsWith("open");a.requestOpen(open);b.requestOpen(open);
            checkOpposing(world,x,y);
            System.out.println("[vandorlabs][reprolab] next-block-open-placement-runtime PASS");
        } else if(scene.contains("flush_")) {
            boolean ceiling=scene.endsWith("ceiling");
            place(world,base,false,texture,ceiling?2:0,false,EnumFacing.NORTH);
            world.setBlockState(ceiling?base.up():base.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);
        } else if(scene.contains("slide_wall")) {
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
                TileEntityProgrammableTrapdoor leaf=place(world,pos,diagonal,texture,0,false,scene.contains("reversed_plane") && row==1?EnumFacing.SOUTH:EnumFacing.NORTH,diagonal && row==1);if(first==null)first=leaf;
            }
            if(world.isRemote)return;
            if(first.group().size()!=4)throw new IllegalStateException("followup trapdoor square not joined");
            first.configureGroup(texture,0,scene.contains("_sliding_"),0,0,false,false,!fit,EnumFacing.NORTH);
            if(scene.endsWith("_open")){checkOutside(world,first,scene.contains("opposite_slopes"));first.requestOpen(true);}
        }
        System.out.println("[vandorlabs][reprolab] trapdoor-followup PASS "+scene);
    }
}
