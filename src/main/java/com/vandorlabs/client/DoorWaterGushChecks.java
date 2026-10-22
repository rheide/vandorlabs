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
                IBlockState released=world.getBlockState(out);require(fill==0?released.getBlock()==Blocks.FLOWING_WATER&&released.getValue(BlockLiquid.LEVEL)==0:released.getMaterial()!=net.minecraft.block.material.Material.WATER,"one-sided water only");
                require(fluids.occupied(wet)&&fluids.occupied(1-wet)==(fill==1),"saved sides remain independent");
                if(fill==0){
                    // Bounded channel: the same source must still feed its far end after
                    // many vanilla fluid updates, and drain when the real door closes.
                    for(int distance=1;distance<=9;distance++){
                        BlockPos channel=pos.up(half).offset(dry,distance);
                        world.setBlockState(channel.down(),Blocks.STONE.getDefaultState(),2);
                        world.setBlockState(channel.offset(dry.rotateY()),Blocks.STONE.getDefaultState(),2);
                        world.setBlockState(channel.offset(dry.rotateYCCW()),Blocks.STONE.getDefaultState(),2);
                    }
                    for(int tick=0;tick<40;tick++){flow(world,pos.up(half),dry);block.updateTick(world,pos,world.getBlockState(pos),world.rand);}
                    require(world.getBlockState(out).getValue(BlockLiquid.LEVEL)==0,"outlet sustains source");
                    require(world.getBlockState(pos.up(half).offset(dry,6)).getMaterial()==net.minecraft.block.material.Material.WATER,"continuous flow reaches channel end");
                    net.minecraft.nbt.NBTTagCompound saved=lower.writeToNBT(new net.minecraft.nbt.NBTTagCompound());lower.readFromNBT(saved);lower.onLoad();
                    require(lower.waterOutlets()!=0,"source ownership survives save/load");
                    block.onBlockActivated(world,pos,world.getBlockState(pos),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.2F,.5F);
                    require(!world.getBlockState(pos).getValue(BlockVandorDoor.OPEN),"real door closes");
                    for(int tick=0;tick<80;tick++){flow(world,pos.up(half),dry);block.updateTick(world,pos,world.getBlockState(pos),world.rand);}
                    require(world.getBlockState(out).getMaterial()!=net.minecraft.block.material.Material.WATER,"closed door outlet drains");
                    require(world.getBlockState(pos.up(half).offset(dry,6)).getMaterial()!=net.minecraft.block.material.Material.WATER,"closed channel drains");
                    require(lower.waterOutlets()==0,"source ownership released");
                }
                cases++;
            }
            for(int scenario=0;scenario<4;scenario++){
                clear(world,pos);world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);
                IBlockState state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,EnumFacing.NORTH);
                world.setBlockState(pos,state,2);world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
                TileEntitySpaceDoor lower=(TileEntitySpaceDoor)world.getTileEntity(pos);lower.setPanelMode(0);lower.fluids.set(0,new FluidStack(FluidRegistry.WATER,1000));
                BlockPos out=pos.north();
                if(scenario==0)world.setBlockState(out,Blocks.WATER.getDefaultState(),2);
                if(scenario==3)world.setBlockState(out,Blocks.STONE.getDefaultState(),2);
                block.onBlockActivated(world,pos,state,player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.2F,.5F);
                require(!lower.itemSettings().hasKey("DoorWaterOutlets"),"copied settings exclude outlet ownership");
                if(scenario==0){
                    require(lower.waterOutlets()==0,"existing source never claimed");
                    block.onBlockActivated(world,pos,world.getBlockState(pos),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.2F,.5F);
                    require(world.getBlockState(out).getValue(BlockLiquid.LEVEL)==0,"existing source retained after close");
                }else if(scenario==1){
                    lower.fluids.set(0,null);block.updateTick(world,pos,world.getBlockState(pos),world.rand);
                    require(lower.waterOutlets()==0&&world.getBlockState(out).getValue(BlockLiquid.LEVEL)>0,"drained open door releases source");
                }else if(scenario==2){
                    world.setBlockToAir(pos);
                    require(world.getBlockState(out).getValue(BlockLiquid.LEVEL)>0,"removed door releases source");
                }else require(world.getBlockState(out).getBlock()==Blocks.STONE&&lower.waterOutlets()==0,"solid outlet obstruction preserved");
                cases++;
            }
        }finally{clear(world,pos);player.capabilities.isCreativeMode=creative;player.setHeldItem(EnumHand.MAIN_HAND,held);}
        System.out.println("[vandorlabs][reprolab] door-water-gush PASS cases="+cases+" real opening/closing four facings both source sides independent halves sustained vanilla channel flow save/load and drainage both-wet and lava preserved");
    }
    private static void flow(WorldServer world,BlockPos pos,EnumFacing dry){
        for(int i=1;i<=9;i++){BlockPos at=pos.offset(dry,i);IBlockState state=world.getBlockState(at);if(state.getMaterial()==net.minecraft.block.material.Material.WATER)Blocks.FLOWING_WATER.updateTick(world,at,state,world.rand);}
    }
    private static void clear(WorldServer world,BlockPos pos){for(BlockPos at:BlockPos.getAllInBox(pos.add(-11,-2,-11),pos.add(11,3,11)))world.setBlockState(at,Blocks.AIR.getDefaultState(),2);}
}
