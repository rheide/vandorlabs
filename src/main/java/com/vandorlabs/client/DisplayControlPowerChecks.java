package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Ordinary activation callbacks and native lamp notifications, rather than direct output getters alone. */
final class DisplayControlPowerChecks {
    private static final BlockPos POS=new BlockPos(704,100,704);
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
    static void run(WorldServer world,EntityPlayerMP player){
        List<String> failures=new ArrayList<>();boolean sneaking=player.isSneaking();player.setSneaking(false);int cases=0;
        try{
            Block[] families={ModBlocks.ANIMATED_SCREEN_SELECTOR,ModBlocks.PROGRAMMABLE_FULL_INPUT,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,ModBlocks.PROGRAMMABLE_INPUT,ModBlocks.PROGRAMMABLE_CONSOLE,ModBlocks.PROGRAMMABLE_HALF_CONSOLE,Block.getBlockFromName("vandorlabs:programmable_diagonal_half_console")};
            for(Block block:families)for(Block support:new Block[]{Blocks.STONE,ModBlocks.PROGRAMMABLE_BLOCK})for(EnumFacing facing:EnumFacing.HORIZONTALS)for(EnumFacing side:EnumFacing.HORIZONTALS){
                String context=block+" support="+support+" facing="+facing+" lever="+side;
                try{conduction(world,player,block,support,facing,side);}catch(IllegalStateException failure){failures.add(context+": "+failure.getMessage());}cases++;
            }
            BlockRockerSwitch rocker=(BlockRockerSwitch)Block.getBlockFromName("vandorlabs:rocker_switch");
            for(EnumFacing mount:EnumFacing.values())for(int setting=0;setting<3;setting++){
                try{rocker(world,player,rocker,mount,setting);}catch(IllegalStateException failure){failures.add("rocker mount="+mount+" setting="+setting+": "+failure.getMessage());}cases++;
            }
        }finally{player.setSneaking(sneaking);clear(world);}
        for(int i=0;i<Math.min(12,failures.size());i++)System.out.println("[vandorlabs] local-control FAIL "+failures.get(i));
        require(failures.isEmpty(),"Local control power cases="+cases+" failed="+failures.size()+" first="+(failures.isEmpty()?"":failures.get(0)));
        System.out.println("[vandorlabs] local-control-power PASS cases="+cases+" seven display families all facings lever through Stone/Programmable Block below; rocker six mounts repeated On-Off-On local/channel/local-disabled native lamps");
    }
    private static void clear(WorldServer world){for(BlockPos p:BlockPos.getAllInBox(POS.add(-2,-2,-2),POS.add(2,2,2)))world.setBlockToAir(p);}
    private static void conduction(WorldServer world,EntityPlayerMP player,Block block,Block support,EnumFacing facing,EnumFacing side){
        clear(world);IBlockState state=block.getDefaultState();
        if(block instanceof BlockProgrammableInput)state=state.withProperty(BlockProgrammableInput.FACING,facing);else if(block instanceof BlockProgrammableDiagonalScreen)state=state.withProperty(BlockProgrammableDiagonalScreen.FACING,facing);else state=state.withProperty(BlockAnimatedScreenSelector.FACING,facing);
        world.setBlockState(POS.down(),support.getDefaultState(),3);world.setBlockState(POS,state,3);
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(POS);for(int band=0;band<4;band++)tile.configureActivation(2,band,band,1,1);
        BlockPos lever=POS.down().offset(side);world.setBlockState(lever,Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING,BlockLever.EnumOrientation.forFacings(side,EnumFacing.NORTH)),3);DeferredTileLoad.flush(world);
        for(boolean on:new boolean[]{true,false,true}){
            Blocks.LEVER.onBlockActivated(world,lever,world.getBlockState(lever),player,EnumHand.MAIN_HAND,side,.5F,.5F,.5F);
            int expected=on?15:0;require(tile.getSignalLevel()==expected,"conducted power="+tile.getSignalLevel()+" expected="+expected);require(tile.activationState()==(on?3:0),"wrong display band");int slot=block instanceof BlockProgrammableHalfConsole?1:0;require(tile.getSurfaceTexture(slot)==(on?3:0),"wrong activated surface");require(tile.getSurfaceTexture(1-slot)==tile.baseSurfaceTexture(1-slot),"shared controls changed");
        }
    }
    private static void rocker(WorldServer world,EntityPlayerMP player,BlockRockerSwitch block,EnumFacing mount,int setting){
        clear(world);BlockPos support=POS.offset(mount.getOpposite()),lamp=POS.offset(mount);world.setBlockState(support,Blocks.STONE.getDefaultState(),3);world.setBlockState(lamp,Blocks.REDSTONE_LAMP.getDefaultState(),3);world.setBlockState(POS,block.getDefaultState().withProperty(BlockVandorSwitch.FACING,mount),3);DeferredTileLoad.flush(world);
        TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(POS);TransmitterSlots.configure(tile,setting==0?ChannelList.EMPTY:ChannelList.of(63177),16,setting!=2);
        for(int click=0;click<6;click++){
            boolean on=(click&1)==0;block.onBlockActivated(world,POS,world.getBlockState(POS),player,EnumHand.MAIN_HAND,mount,.5F,.5F,.5F);
            require(tile.isLocalOn()==on,"toggle latch click="+click);require(world.getBlockState(POS).getValue(BlockVandorSwitch.ON)==on,"toggle block state click="+click);
            if(!on&&world.getBlockState(lamp).getBlock()==Blocks.LIT_REDSTONE_LAMP)Blocks.LIT_REDSTONE_LAMP.updateTick(world,lamp,world.getBlockState(lamp),world.rand);
            require((world.getBlockState(lamp).getBlock()==Blocks.LIT_REDSTONE_LAMP)==(on&&setting!=2),"lamp missed notification click="+click+" physical="+world.isBlockPowered(lamp));
            if(setting>0)require(RedstoneChannels.level(world,lamp,63177)==(on?15:0),"channel output click="+click);
        }
    }
    private DisplayControlPowerChecks(){}
}
