package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.compat.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

final class FollowupRuntimeChecks {
    static void run(World world,EntityPlayerMP player) {
        BlockPos p=new BlockPos(65,245,40);
        ItemStack held=player.getHeldItemMainhand();
        try {
            for(boolean upper:new boolean[]{false,true}) {
                world.setBlockState(p,ModBlocks.PROGRAMMABLE_SLAB.getDefaultState().withProperty(BlockProgrammableSlab.HALF,upper?BlockSlab.EnumBlockHalf.TOP:BlockSlab.EnumBlockHalf.BOTTOM),3);
                TileEntityAnimatedScreenSelector slab=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                slab.setHousingTexture(4);slab.setFaceTextures(new FaceTextures(true,new int[]{1,2,3,4,5,6}));
                ItemStack stack=new ItemStack(ModBlocks.PROGRAMMABLE_SLAB,2);player.setHeldItem(EnumHand.MAIN_HAND,stack);
                boolean creative=player.capabilities.isCreativeMode;player.capabilities.isCreativeMode=false;
                try {
                    require(stack.getItem().onItemUse(player,world,p,EnumHand.MAIN_HAND,upper?EnumFacing.DOWN:EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"slab merge failed");
                    require(world.getBlockState(p).getBlock()==ModBlocks.PROGRAMMABLE_BLOCK && stack.getCount()==1,"slab merge consumption");
                    TileEntityAnimatedScreenSelector full=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                    require(full.getHousingTexture()==4&&full.getFaceTextures().choice(1)==2,"slab merge lost settings");
                } finally {player.capabilities.isCreativeMode=creative;world.setBlockToAir(p);}
            }
            BlockProgrammableStairs stairs=(BlockProgrammableStairs)ModBlocks.PROGRAMMABLE_STAIRS;
            for(EnumFacing facing:EnumFacing.HORIZONTALS) for(BlockStairs.EnumHalf half:BlockStairs.EnumHalf.values()) {
                IBlockState state=stairs.getDefaultState().withProperty(BlockStairs.FACING,facing).withProperty(BlockStairs.HALF,half);
                world.setBlockState(p,state,3);
                require(stairs.getStateFromMeta(stairs.getMetaFromState(state)).equals(state),"stairs metadata");
                world.setBlockState(p.offset(facing),state.withProperty(BlockStairs.FACING,facing.rotateY()),3);
                require(stairs.getActualState(state,world,p).getValue(BlockStairs.SHAPE)!=BlockStairs.EnumShape.STRAIGHT,"stairs corner");
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);
                tile.setHousingTexture(3);tile.setFaceTextures(new FaceTextures(true,new int[]{1,2,3,4,5,6}));
                ItemStack pick=stairs.createConfiguredDrop(tile);
                require(pick.getItem()==Item.getItemFromBlock(stairs)&&pick.getSubCompound("BlockEntityTag").getBoolean("FaceTexturesEnabled"),"stairs configured drop");
                world.setBlockToAir(p.offset(facing));world.setBlockToAir(p);
            }
            world.setBlockState(p,ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),3);
            world.setBlockState(p.east(),ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState(),3);
            TileEntityAnimatedScreenSelector a=(TileEntityAnimatedScreenSelector)world.getTileEntity(p),b=(TileEntityAnimatedScreenSelector)world.getTileEntity(p.east());
            b.setHousingTexture(3);require(!RampMaterials.matches(world,p,p.east()),"ramp joined different finishes");
            a.setHousingTexture(3);require(RampMaterials.matches(world,p,p.east()),"ramp rejected equal finishes");
            b.setFaceTextures(new FaceTextures(true,new int[]{1,-1,-1,-1,-1,-1}));
            require(!RampMaterials.matches(world,p,p.east()),"ramp ignored face override");
            b.setFaceTextures(new FaceTextures(false,new int[]{1,-1,-1,-1,-1,-1}));
            require(RampMaterials.matches(world,p,p.east()),"disabled override affected ramp selection");
            world.setBlockToAir(p.east());world.setBlockToAir(p);
            int ie=0;
            for(Block block:Block.REGISTRY) {
                if(block.getRegistryName()==null||!block.getRegistryName().getResourceDomain().equals("immersiveengineering")||!block.getClass().getName().contains("Slab"))continue;
                for(int type=0;type<3;type++) {
                    world.setBlockState(p,block.getDefaultState(),3);world.setBlockState(p.east(),block.getDefaultState(),3);
                    if(!RampMaterials.isIESlab(world,p))continue;
                    net.minecraft.tileentity.TileEntity tile=world.getTileEntity(p);
                    NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());saved.setInteger("slabType",type);
                    TileEntityControlledRamp.restoreSourceTile(world,p,saved);
                    require(RampMaterials.isIESlab(world,p),"IE whitelist");
                    require(RampMaterials.matches(world,p,p.east())==(type==0),"IE slab half matching");
                    AxisAlignedBB bounds=block.getDefaultState().getBoundingBox(world,p);
                    require(bounds.maxY-bounds.minY==(type==2?1:.5),"IE slab bounds");
                    world.setBlockToAir(p);world.setBlockState(p,block.getDefaultState(),3);
                    TileEntityControlledRamp.restoreSourceTile(world,p,saved);
                    require(world.getTileEntity(p).writeToNBT(new NBTTagCompound()).getInteger("slabType")==type,"IE slab restore");
                    world.setBlockToAir(p.east());
                    Block controller=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_ramp"));
                    world.setBlockState(p.north(),controller.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);
                    TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(p.north());
                    require(ramp.request(true),"IE slab deploy: "+block.getRegistryName()+" type "+type);
                    require(ramp.recover(true),"IE ramp restore");
                    require(world.getTileEntity(p).writeToNBT(new NBTTagCompound()).getInteger("slabType")==type,"IE slab deployment changed half");
                    world.setBlockToAir(p.north());
                    ie++;
                }
                world.setBlockToAir(p);world.setBlockToAir(p.east());
            }
            require(ie>0,"actual IE slab integration not loaded");
            BlockProgrammableWall port=(BlockProgrammableWall)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_diagonal_porthole"));
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int shape=0;shape<4;shape++) {
                IBlockState lower=port.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing);
                world.setBlockState(p,lower,3);
                world.setBlockState(p.up(),lower.withProperty(BlockProgrammableWall.FACING,facing.getOpposite()).withProperty(BlockProgrammableWall.INVERTED,true),3);
                a=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);b=(TileEntityAnimatedScreenSelector)world.getTileEntity(p.up());
                a.setPortholeShape(shape);b.setPortholeShape(shape);
                require(TEAnimatedScreenSelector.portholeGroup(a,lower).rows==2,"stacked diagonal porthole did not join");
                b.setDiagonalFullWidth(true);
                require(TEAnimatedScreenSelector.portholeGroup(a,lower).rows==1,"incompatible stacked plane joined");
                a.setDiagonalGeometry(2,0);
                java.util.List<AxisAlignedBB> boxes=new java.util.ArrayList<>();
                port.addCollisionBoxToList(lower,world,p,new AxisAlignedBB(p),boxes,null,false);
                for(AxisAlignedBB box:boxes)require(box.maxY<=p.getY()+.625+1e-7,"half-height porthole collision");
                world.setBlockToAir(p);world.setBlockToAir(p.up());
            }
            BlockProgrammableWall wall=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
            for(boolean inverted:new boolean[]{false,true})for(boolean front:new boolean[]{false,true})for(int fill=1;fill<4;fill++) {
                IBlockState state=wall.getDefaultState().withProperty(BlockProgrammableWall.INVERTED,inverted);
                world.setBlockState(p,state,3);BlockPos neighbor=front?p.north():p.south();
                world.setBlockState(neighbor,state.withProperty(BlockProgrammableWall.FACING,EnumFacing.EAST),3);
                a=(TileEntityAnimatedScreenSelector)world.getTileEntity(p);a.setDiagonalGeometry(1,fill);
                b=(TileEntityAnimatedScreenSelector)world.getTileEntity(neighbor);b.setDiagonalGeometry(1,0);
                require(wall.corner(state,world,p)!=null,"filled diagonal lost unfilled neighbor connection");
                java.util.List<AxisAlignedBB> boxes=new java.util.ArrayList<>();
                wall.addCollisionBoxToList(state,world,p,new AxisAlignedBB(p),boxes,null,false);
                for(AxisAlignedBB box:boxes) require(box.minX>=p.getX()-.125&&box.maxX<=p.getX()+1.125&&box.minZ>=p.getZ()-.125&&box.maxZ<=p.getZ()+1.125,"corner fill exceeds two-pixel overhang");
                world.setBlockToAir(neighbor);world.setBlockToAir(p);
            }
            checkDiagonalPlacement(world,player,p);
            DiagonalSurfaceChecks.run();
            checkRampTextureOption(world,player,p);
            RedstoneLoadChecks.run(world);
            checkUndo(world,player,p);
        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
        finally {
            player.setHeldItem(EnumHand.MAIN_HAND,held);
            for(BlockPos pos:BlockPos.getAllInBox(p.add(-2,-2,-2),p.add(2,2,2)))world.setBlockToAir(pos);
        }
        System.out.println("[vandorlabs][reprolab] followup-1.2-runtime PASS");
    }
    private static void checkUndo(World world,EntityPlayerMP player,BlockPos p)throws ReflectiveOperationException {
        Item wandItem=Item.REGISTRY.getObject(new ResourceLocation("betterbuilderswands:wanddiamond"));
        if(wandItem==null)for(Item item:Item.REGISTRY)if(item.getRegistryName()!=null&&item.getRegistryName().getResourceDomain().equals("betterbuilderswands")){wandItem=item;break;}
        ItemStack wand=new ItemStack(wandItem);player.setHeldItem(EnumHand.MAIN_HAND,wand);
        Block block=ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
        IBlockState source=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,EnumFacing.EAST).withProperty(BlockProgrammableWall.INVERTED,true);
        world.setBlockState(p,source,3);
        BetterBuildersWandsCompat.INSTANCE.onRightClickBlock(new PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,p,EnumFacing.SOUTH,new Vec3d(p)));
        world.setBlockState(p.south(),block.getDefaultState(),3);
        NBTTagCompound root=new NBTTagCompound(),bbw=new NBTTagCompound();
        bbw.setIntArray("lastPlaced",new int[]{p.getX(),p.getY(),p.getZ()+1});
        bbw.setString("lastBlock",block.getDefaultState().toString());root.setTag("bbw",bbw);wand.setTagCompound(root);
        require(BetterBuildersWandsCompat.finishPending(player),"wand copy boundary");
        net.minecraft.command.ICommand cmd=(net.minecraft.command.ICommand)Class.forName("portablejim.bbw.core.OopsCommand").newInstance();
        net.minecraftforge.event.CommandEvent event=new net.minecraftforge.event.CommandEvent(cmd,player,new String[0]);
        BetterBuildersWandsCompat.INSTANCE.onCommand(event);
        require(event.isCanceled()&&world.isAirBlock(p.south())&&world.getBlockState(p).equals(source),"actual wandOops undo");
    }
    private static void checkDiagonalPlacement(World world, EntityPlayerMP player, BlockPos p) {
        float yaw = player.rotationYaw;
        ItemStack offhand = player.getHeldItemOffhand();
        try {
            BlockProgrammableWall port = (BlockProgrammableWall) Block.REGISTRY.getObject(
                    new ResourceLocation("vandorlabs:programmable_diagonal_porthole"));
            for (BlockProgrammableWall block : new BlockProgrammableWall[]{port,
                    (BlockProgrammableWall) ModBlocks.PROGRAMMABLE_DIAGONAL_WALL}) {
                for (EnumFacing facing : EnumFacing.HORIZONTALS) for (int mode : new int[]{0, 2}) {
                    player.rotationYaw = facing.getOpposite().getHorizontalAngle();
                    // Fresh placement chooses the clicked edge, also with a configured offhand item.
                    ItemStack configured = new ItemStack(block);
                    configured.getOrCreateSubCompound("BlockEntityTag").setBoolean("DiagonalHalfHeight", mode == 2);
                    player.setHeldItem(EnumHand.OFF_HAND, configured);
                    for (boolean far : new boolean[]{false, true}) {
                        float coordinate = far ? .9F : .1F;
                        IBlockState edge = block.getStateForPlacement(world, p, EnumFacing.NORTH,
                                coordinate, coordinate, coordinate, 0, player, EnumHand.OFF_HAND);
                        if (mode == 2) require(edge.getValue(BlockProgrammableWall.INVERTED) == far,
                                "shallow placement ignored click height");
                        else require(edge.getValue(BlockProgrammableWall.FACING) ==
                                (facing.getAxis() == EnumFacing.Axis.X ? (far ? EnumFacing.EAST : EnumFacing.WEST)
                                        : (far ? EnumFacing.SOUTH : EnumFacing.NORTH)), "tall placement ignored edge");
                        require(block.getStateFromMeta(block.getMetaFromState(edge)).equals(edge), "diagonal placement metadata");
                    }
                    for (int shape = 0; shape < (block == port ? 4 : 1); shape++) {
                        EnumFacing along = mode == 2 ? facing.getOpposite() : EnumFacing.UP;
                        BlockPos q = p.offset(along);
                        IBlockState lower = block.getDefaultState().withProperty(BlockProgrammableWall.FACING, facing);
                        IBlockState upper = lower.withProperty(BlockProgrammableWall.FACING, facing.getOpposite())
                                .withProperty(BlockProgrammableWall.INVERTED, true);
                        for (boolean reverse : new boolean[]{false, true}) {
                            BlockPos start = reverse ? q : p, end = reverse ? p : q;
                            EnumFacing side = reverse ? along.getOpposite() : along;
                            world.setBlockState(start, reverse ? upper : lower, 3);
                            TileEntityAnimatedScreenSelector a = (TileEntityAnimatedScreenSelector) world.getTileEntity(start);
                            a.setDiagonalGeometry(mode, 0); a.setPortholeShape(shape);
                            // Exercise normal item use, including geometry inheritance and tile initialization.
                            ItemStack plain = new ItemStack(block, 2);
                            player.setHeldItem(EnumHand.MAIN_HAND, plain);
                            require(plain.getItem().onItemUse(player, world, start, EnumHand.MAIN_HAND,
                                    side, .5F, .5F, .5F) == EnumActionResult.SUCCESS, "diagonal item placement failed");
                            require(world.getBlockState(end).equals(reverse ? lower : upper), "diagonal continuation chose wrong plane");
                            TileEntityAnimatedScreenSelector b = (TileEntityAnimatedScreenSelector) world.getTileEntity(end);
                            require(BlockProgrammableWall.geometry(world, end) == mode, "plain item did not inherit proportions");
                            b.setPortholeShape(shape);
                            require(DiagonalPanelGeometry.samePlane(start, world.getBlockState(start), end,
                                    world.getBlockState(end), mode), "continued surfaces differ");
                            if (block == port) {
                                TEAnimatedScreenSelector.PortholeGroup group = TEAnimatedScreenSelector.portholeGroup(a, world.getBlockState(start));
                                require(group.rows == 2 && group.columns == 1, "continued portholes did not join");
                                require(TEAnimatedScreenSelector.portholeGroup(b, world.getBlockState(end)) == group, "join depends on render order");
                                b.setJoinPortholes(false);
                                require(TEAnimatedScreenSelector.portholeGroup(a, world.getBlockState(start)).rows == 1, "Join Off ignored");
                                b.setJoinPortholes(true); b.setDiagonalGeometry(mode == 2 ? 0 : 2, 0);
                                require(TEAnimatedScreenSelector.portholeGroup(a, world.getBlockState(start)).rows == 1, "different proportions joined");
                            }
                            world.setBlockToAir(p); world.setBlockToAir(q);
                        }
                    }
                }
            }
            System.out.println("[vandorlabs][reprolab] diagonal-placement-joins PASS");
        } finally { player.rotationYaw = yaw; player.setHeldItem(EnumHand.OFF_HAND, offhand); }
    }

    private static void checkRampTextureOption(World world,EntityPlayerMP player,BlockPos p) {
        for(Block material:new Block[]{ModBlocks.PROGRAMMABLE_BLOCK,ModBlocks.PROGRAMMABLE_SLAB}) {
            world.setBlockState(p,material.getDefaultState(),3);
            world.setBlockState(p.east(),material.getDefaultState(),3);
            ((TileEntityAnimatedScreenSelector)world.getTileEntity(p.east())).setHousingTexture(4);
            Block controller=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:programmable_ramp"));
            world.setBlockState(p.north(),controller.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH),3);
            TileEntityRampController ramp=(TileEntityRampController)world.getTileEntity(p.north());
            require(ramp.matchTextures,"ramp texture matching default");
            require(ramp.request(true)&&ramp.area()==1,"matching ramp included different finish");
            require(ramp.recover(true),"matching ramp recovery");
            ramp.matchTextures=false;
            require(ramp.request(true)&&ramp.area()==2,"optional matching did not include both finishes");
            require(ramp.recover(true),"mixed finish ramp recovery");
            require(((TileEntityAnimatedScreenSelector)world.getTileEntity(p.east())).getHousingTexture()==4,
                    "mixed ramp lost a source finish");
            require(!ramp.writeToNBT(new NBTTagCompound()).getBoolean("MatchTextures"),"ramp matching persistence");
            require(!controller.getPickBlock(world.getBlockState(p.north()),null,world,p.north(),player)
                    .getSubCompound("RampSettings").getBoolean("MatchTextures"),"ramp matching pick-block");
            require(!com.vandorlabs.items.ProgrammableSettings.capture(world,p.north())
                    .getBoolean(com.vandorlabs.items.ProgrammableSettings.RAMP_MATCH_TEXTURES),"ramp matching copy capture");
            world.setBlockToAir(p.north());world.setBlockToAir(p);world.setBlockToAir(p.east());
        }
        System.out.println("[vandorlabs][reprolab] ramp-texture-option PASS");
    }

    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
