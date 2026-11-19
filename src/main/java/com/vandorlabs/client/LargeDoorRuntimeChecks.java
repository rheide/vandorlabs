package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Opt-in live placement, shared configuration, save/load, geometry and atomic removal. */
final class LargeDoorRuntimeChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("large-door: "+message);}
    static void horizontal(World world,EntityPlayer player){
        BlockLargeProgrammableDoor door=(BlockLargeProgrammableDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","large_programmable_door"));
        BlockPos center=new BlockPos(8,80,8);
        ItemStack previous=player.getHeldItemMainhand();
        boolean sneaking=player.isSneaking();
        try {
            player.setSneaking(false);
            for(EnumFacing front:EnumFacing.Plane.HORIZONTAL){
                for(BlockPos p:BlockPos.getAllInBox(center.add(-5,-2,-5),center.add(5,3,5)))world.setBlockToAir(p);
                EnumFacing width=front.rotateYCCW();
                BlockPos clicked=center.offset(front.getOpposite());
                for(int x=-1;x<=1;x++)world.setBlockState(clicked.offset(width,x),Blocks.STONE.getDefaultState(),2);
                ItemStack stack=new ItemStack(door,2);
                player.setHeldItem(EnumHand.MAIN_HAND,stack);
                require(stack.getItem().onItemUse(player,world,clicked,EnumHand.MAIN_HAND,front,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"mount on support side "+front);
                BlockPos anchor=center.offset(width,-1);
                BlockPos passage=center.offset(front);
                TileEntityLargeProgrammableDoor root=door.root(world,passage);
                require(root!=null&&root.isHorizontal()&&root.getPos().equals(anchor)&&door.complete(world,root),"horizontal nine-cell ownership "+front);
                root.configure(23,1,true,com.vandorlabs.persistence.SpaceDoorData.VERTICAL_SPLIT,true,true,false,0,false);
                root.setPlacementDepth(0);
                AxisAlignedBB sweep=new AxisAlignedBB(passage).grow(0,2,0).shrink(.02);
                Vec3d above=new Vec3d(passage).addVector(.5,2,.5),below=above.addVector(0,-4,0);
                require(!world.getCollisionBoxes(null,sweep).isEmpty()&&MovedDoorInteractions.trace(world,above,below)!=null,"closed hatch blocks a one-block vertical sweep "+front);
                require(root.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).getBoolean("LargeDoorHorizontal"),"horizontal orientation saved");
                for(int column=0;column<3;column++)for(int row=0;row<3;row++){
                    BlockPos cell=root.cell(anchor,front,column,row);
                    TileEntity restored=TileEntity.create(world,world.getTileEntity(cell).writeToNBT(new net.minecraft.nbt.NBTTagCompound()));
                    world.setTileEntity(cell,restored);
                    require(((TileEntityLargeProgrammableDoor)restored).isHorizontal()&&((TileEntityLargeProgrammableDoor)restored).anchorPos().equals(anchor),"role round-trips "+front+" "+cell);
                }
                require(door.root(world,passage).getSlideDirection()==com.vandorlabs.persistence.SpaceDoorData.VERTICAL_SPLIT,"split mode survives placed tile reload");
                door.onBlockActivated(world,passage,world.getBlockState(passage),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
                require(world.getBlockState(passage).getValue(BlockVandorDoor.OPEN),"open from a follower cell "+front);
                require(world.getCollisionBoxes(null,sweep).isEmpty()&&MovedDoorInteractions.trace(world,above,below)==null,"open hatch clears the vertical swept column "+front);
                verifyWater(world,door,door.root(world,passage),player,front,passage);
                require(stack.getItem().onItemUse(player,world,clicked,EnumHand.MAIN_HAND,front,.5F,.5F,.5F)==EnumActionResult.SUCCESS
                        &&door.root(world,passage)!=null,"replace assembly for support removal "+front);
                world.destroyBlock(clicked.offset(width),false);
                require(world.getBlockState(passage).getBlock()!=door,"loss of side support removes assembly "+front);
            }
        } finally {
            player.setHeldItem(EnumHand.MAIN_HAND,previous);
            player.setSneaking(sneaking);
        }
        System.out.println("[vandorlabs] horizontal-door PASS four side placements, saved part roles, vertical sweep, owned water sources and support removal");
    }

    private static void verifyWater(World world,BlockLargeProgrammableDoor door,TileEntityLargeProgrammableDoor root,
            EntityPlayer player,EnumFacing front,BlockPos passage){
        TileEntityLargeProgrammableDoor upper=(TileEntityLargeProgrammableDoor)world.getTileEntity(root.cell(root.getPos(),front,0,2));
        TileEntityLargeProgrammableDoor lower=(TileEntityLargeProgrammableDoor)world.getTileEntity(root.cell(root.getPos(),front,2,2));
        BlockPos outDown=upper.getPos().down(),outUp=lower.getPos().up();
        net.minecraftforge.fluids.FluidStack water=new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,1000);
        upper.fluids.set(0,water);lower.fluids.set(1,water);
        DoorWaterGush.opened(world,upper.getPos());DoorWaterGush.opened(world,lower.getPos());
        require(upper.waterOutlets()==1&&lower.waterOutlets()==2&&root.waterOutlets()==0
                &&world.getBlockState(outDown).getValue(net.minecraft.block.BlockLiquid.LEVEL)==0
                &&world.getBlockState(outUp).getValue(net.minecraft.block.BlockLiquid.LEVEL)==0,"each follower owns its vertical source "+front);
        door.onBlockActivated(world,passage,world.getBlockState(passage),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
        DoorWaterGush.opened(world,upper.getPos());DoorWaterGush.opened(world,lower.getPos());
        require(upper.waterOutlets()==0&&lower.waterOutlets()==0
                &&world.getBlockState(outDown).getValue(net.minecraft.block.BlockLiquid.LEVEL)==1
                &&world.getBlockState(outUp).getValue(net.minecraft.block.BlockLiquid.LEVEL)==1,"closing retires both vertical sources "+front);
        door.onBlockActivated(world,passage,world.getBlockState(passage),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
        DoorWaterGush.opened(world,upper.getPos());DoorWaterGush.opened(world,lower.getPos());
        world.destroyBlock(lower.getPos(),false);
        require(door.root(world,passage)==null
                &&world.getBlockState(outDown).getValue(net.minecraft.block.BlockLiquid.LEVEL)==1
                &&world.getBlockState(outUp).getValue(net.minecraft.block.BlockLiquid.LEVEL)==1,"breaking a follower retires both owned sources "+front);
    }

    static void run(World world,EntityPlayer player){
        BlockLargeProgrammableDoor door=(BlockLargeProgrammableDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs","large_programmable_door"));
        ItemStack previous=player.getHeldItemMainhand();float yaw=player.rotationYaw;boolean creative=player.capabilities.isCreativeMode;player.capabilities.isCreativeMode=true;
        BlockPos center=new BlockPos(8,80,8);int assertions=0;
        try{for(EnumFacing front:EnumFacing.Plane.HORIZONTAL){
            EnumFacing width=front.rotateYCCW();BlockPos anchor=center.offset(width,-1);
            for(BlockPos p:BlockPos.getAllInBox(center.add(-4,-1,-4),center.add(4,6,4))){require(world.isBlockLoaded(p),"fixture chunk loaded");world.setBlockToAir(p);}
            for(int x=0;x<3;x++)world.setBlockState(anchor.offset(width,x).down(),Blocks.STONE.getDefaultState(),2);
            ItemStack stack=new ItemStack(door,2);player.setHeldItem(EnumHand.MAIN_HAND,stack);player.rotationYaw=front.getOpposite().getHorizontalAngle();player.setPosition(center.getX(),center.getY()+1,center.getZ()+3);
            require(stack.getItem().onItemUse(player,world,center.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"place complete assembly");
            require(stack.getCount()==1,"consume one item");
            TileEntityLargeProgrammableDoor root=door.root(world,center.up(2));require(root!=null && root.getPos().equals(anchor),"top cell resolves anchor");
            int rendered=0;
            for(int x=0;x<3;x++)for(int y=0;y<3;y++){
                BlockPos p=anchor.offset(width,x).up(y);IBlockState state=world.getBlockState(p);
                require(state.getBlock()==door && door.getActualState(state,world,p).getValue(BlockConnectingDetailedDoor.PAIRED),"always paired cell");
                require(ProgrammableTarget.settingsPos(world,p).equals(anchor),"tools resolve anchor");
                TileEntityLargeProgrammableDoor tile=(TileEntityLargeProgrammableDoor)world.getTileEntity(p);
                TileEntity saved=TileEntity.create(world,tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));world.setTileEntity(p,saved);
                require(door.root(world,p)!=null,"role survives NBT");
                if(((TileEntityLargeProgrammableDoor)saved).shouldRenderInPass(0))rendered++;
                assertions+=3;
            }
            require(rendered==1,"one opaque renderer per assembly");root=door.root(world,anchor);
            root.configure(15,2,false,0,true,true,false,0,false);root.setPlacementDepth(2);
            ItemStack picked=door.getPickBlock(world.getBlockState(center),null,world,center,player);
            require(picked.getItem()==stack.getItem() && picked.getSubCompound("SpaceDoorSettings").getInteger("SpaceDesign")==15,"pick retains large type and design");
            door.onBlockActivated(world,center.up(2),world.getBlockState(center.up(2)),player,EnumHand.MAIN_HAND,front,.5F,.5F,.5F);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)require(world.getBlockState(anchor.offset(width,x).up(y)).getValue(BlockVandorDoor.OPEN),"both leaves open from any cell");
            root.configure(2,1,true,0,false,false,true,0,true);
            require(!door.geometry(world,center).isEmpty(),"open hinged leaf geometry");
            world.setBlockToAir(center.up());
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)require(world.getBlockState(anchor.offset(width,x).up(y)).getBlock()!=door,"breaking any cell removes assembly");
            // An obstructed upper cell rejects the whole operation without consuming or overwriting.
            world.setBlockState(anchor.up(2),Blocks.GOLD_BLOCK.getDefaultState(),2);stack=new ItemStack(door,2);player.setHeldItem(EnumHand.MAIN_HAND,stack);
            require(stack.getItem().onItemUse(player,world,center.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.FAIL && stack.getCount()==2,"obstruction is atomic");
            require(world.getBlockState(center).getBlock()!=door && world.getBlockState(anchor.up(2)).getBlock()==Blocks.GOLD_BLOCK,"obstructed placement unchanged");
        }}finally{player.setHeldItem(EnumHand.MAIN_HAND,previous);player.rotationYaw=yaw;player.capabilities.isCreativeMode=creative;}
        System.out.println("[vandorlabs][reprolab] large-door-runtime PASS orientations=4 cellContracts="+assertions);
    }
}
