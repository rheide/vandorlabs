package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.*;
final class DoorWaterGushChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("door gush: "+message);}
    static void run(WorldServer world){
        net.minecraft.entity.player.EntityPlayerMP player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);BlockPos pos=new BlockPos(304,40,146);boolean creative=player.capabilities.isCreativeMode;ItemStack held=player.getHeldItemMainhand();int cases=0;
        try{player.capabilities.isCreativeMode=false;player.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);
            BlockConfigurableSpaceDoor block=(BlockConfigurableSpaceDoor)Block.getBlockFromName("vandorlabs:programmable_door");
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int wet=0;wet<2;wet++)for(int half=0;half<2;half++)for(int fill=0;fill<3;fill++){
                clear(world,pos);world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);IBlockState state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,facing);
                world.setBlockState(pos,state,2);world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);TileEntitySpaceDoor lower=(TileEntitySpaceDoor)world.getTileEntity(pos);lower.setPanelMode(0);
                ContainedFluids fluids=WaterloggedParts.data(world.getTileEntity(pos.up(half)));fluids.set(wet,new FluidStack(fill==2?FluidRegistry.LAVA:FluidRegistry.WATER,1000));if(fill==1)fluids.set(1-wet,new FluidStack(FluidRegistry.WATER,1000));
                double[] plane=OpeningFluids.plane(world,pos);EnumFacing dry=null;for(EnumFacing side:EnumFacing.HORIZONTALS)if((wet==0?1:-1)*(plane[0]*side.getFrontOffsetX()+plane[2]*side.getFrontOffsetZ())>.5)dry=side;
                require(dry!=null,"dry exit direction");BlockPos out=pos.up(half).offset(dry);
                block.onBlockActivated(world,pos,state,player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.2F,.5F);
                require(world.getBlockState(pos).getValue(BlockVandorDoor.OPEN),"real manual door opens");
                IBlockState released=world.getBlockState(out);require(fill==0?released.getBlock()==Blocks.FLOWING_WATER&&released.getValue(BlockLiquid.LEVEL)>0:released.getMaterial()!=net.minecraft.block.material.Material.WATER,"one-sided water only");
                require(fluids.occupied(wet)&&fluids.occupied(1-wet)==(fill==1),"saved sides remain independent");
                if(fill==0){Blocks.FLOWING_WATER.updateTick(world,out,released,world.rand);boolean spread=world.getBlockState(out.down()).getMaterial()==net.minecraft.block.material.Material.WATER;for(EnumFacing side:EnumFacing.HORIZONTALS)spread|=world.getBlockState(out.offset(side)).getMaterial()==net.minecraft.block.material.Material.WATER;require(spread,"released water spreads through vanilla fluid update");}
                cases++;
            }
        }finally{clear(world,pos);player.capabilities.isCreativeMode=creative;player.setHeldItem(EnumHand.MAIN_HAND,held);}
        System.out.println("[vandorlabs][reprolab] door-water-gush PASS cases="+cases+" real opening four facings both source sides independent halves vanilla spreading no source blocks both-wet and lava preserved");
    }
    private static void clear(WorldServer world,BlockPos pos){for(BlockPos at:BlockPos.getAllInBox(pos.add(-3,-2,-3),pos.add(3,3,3)))world.setBlockState(at,Blocks.AIR.getDefaultState(),2);}
}
