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
                for(AxisAlignedBB box:boxes)require(box.maxY<=p.getY()+.5+1e-7,"half-height porthole collision");
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
                for(AxisAlignedBB box:boxes) require(box.minX>=p.getX()&&box.maxX<=p.getX()+1&&box.minZ>=p.getZ()&&box.maxZ<=p.getZ()+1,"corner fill escaped cell");
                world.setBlockToAir(neighbor);world.setBlockToAir(p);
            }
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
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
