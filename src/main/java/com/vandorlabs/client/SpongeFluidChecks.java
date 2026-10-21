package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

final class SpongeFluidChecks {
    static void run(World world){
        BlockPos pos=new BlockPos(8,230,8);int cases=0;
        try{
            for(String id:new String[]{"programmable_slab","programmable_stairs","programmable_wall","programmable_porthole_wall","programmable_glass","programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass","programmable_door","programmable_trapdoor","programmable_diagonal_trapdoor"})
            for(int fill=0;fill<3;fill++)for(boolean bridge:new boolean[]{false,true}){
                clear(world,pos);Block block=Block.getBlockFromName("vandorlabs:"+id);world.setBlockState(pos,block.getDefaultState(),2);
                if(block instanceof BlockConfigurableSpaceDoor){world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(pos.up(),block.getDefaultState().withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);}
                ContainedFluids data=WaterloggedParts.data(world.getTileEntity(pos));data.set(0,new FluidStack(FluidRegistry.WATER,1000));if(fill>0)data.set(1,new FluidStack(fill==1?FluidRegistry.WATER:FluidRegistry.LAVA,1000));
                BlockPos sponge=pos.east(bridge?2:1);if(bridge)world.setBlockState(pos.east(),Blocks.WATER.getDefaultState(),2);
                place(world,sponge);
                require(world.getBlockState(pos).getBlock()==block,"sponge preserves "+id);
                require(!data.occupied(0)&&(fill==2?data.get(1)!=null&&data.get(1).getFluid()==FluidRegistry.LAVA:!data.occupied(1)),"sponge clears only water "+id+" fill="+fill+" nativeBridge="+bridge);
                require(world.getBlockState(sponge).getValue(BlockSponge.WET),"sponge becomes wet");cases++;
            }
            clear(world,pos);world.setBlockState(pos,ModBlocks.PROGRAMMABLE_WALL.getDefaultState(),2);world.setBlockState(pos.east(),Blocks.SPONGE.getDefaultState(),3);
            ContainedFluids data=WaterloggedParts.data(world.getTileEntity(pos));data.set(0,new FluidStack(FluidRegistry.WATER,1000));require(!data.occupied(0),"existing dry sponge absorbs bucket update");
            data.set(0,new FluidStack(FluidRegistry.WATER,1000));require(data.occupied(0),"wet sponge does not absorb twice");world.notifyBlockUpdate(pos.east(),Blocks.AIR.getDefaultState(),Blocks.SPONGE.getDefaultState(),3);require(data.occupied(0),"stale placement notification never reactivates wet sponge");
            clear(world,pos);world.setBlockState(pos,ModBlocks.PROGRAMMABLE_WALL.getDefaultState(),2);data=WaterloggedParts.data(world.getTileEntity(pos));data.set(0,new FluidStack(FluidRegistry.WATER,1000));world.setBlockState(pos.east(),Blocks.STONE.getDefaultState(),2);world.setBlockState(pos.east(2),Blocks.SPONGE.getDefaultState(),3);require(data.occupied(0),"solid barrier stops absorption");
            clear(world,pos);for(int i=0;i<9;i++){BlockPos at=pos.east(i);world.setBlockState(at,ModBlocks.PROGRAMMABLE_WALL.getDefaultState(),2);WaterloggedParts.data(world.getTileEntity(at)).set(0,new FluidStack(FluidRegistry.WATER,1000));}world.setBlockState(pos.west(),Blocks.SPONGE.getDefaultState(),3);
            require(!WaterloggedParts.data(world.getTileEntity(pos.east(6))).any()&&WaterloggedParts.data(world.getTileEntity(pos.east(7))).any(),"seven-block connected reach, no unbounded drain");
        }finally{clear(world,pos);}
        System.out.println("[vandorlabs][reprolab] contained-water-sponge PASS cases="+cases+" eleven families one/two wet sides mixed lava native bridges repeat-fill barriers bounded reach");
    }
    private static void place(World world,BlockPos pos){
        net.minecraft.entity.player.EntityPlayerMP player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft((net.minecraft.world.WorldServer)world);
        net.minecraft.item.ItemStack held=player.getHeldItemMainhand();boolean creative=player.capabilities.isCreativeMode;
        try{world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);player.capabilities.isCreativeMode=false;net.minecraft.item.ItemStack item=new net.minecraft.item.ItemStack(Blocks.SPONGE);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,item);
            require(item.onItemUse(player,world,pos.down(),net.minecraft.util.EnumHand.MAIN_HAND,EnumFacing.UP,.5F,1,.5F)==net.minecraft.util.EnumActionResult.SUCCESS,"actual sponge item placement");
        }finally{player.capabilities.isCreativeMode=creative;player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,held);}
    }
    private static void clear(World world,BlockPos pos){world.setBlockToAir(pos.up());world.setBlockToAir(pos.down());for(int x=-1;x<=9;x++){world.setBlockToAir(pos.east(x));world.setBlockToAir(pos.east(x).down());}}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
