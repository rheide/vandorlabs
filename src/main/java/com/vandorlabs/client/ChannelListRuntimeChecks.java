package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.persistence.SpaceDoorData;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Real switch/button state and light receivers, including partial latch banks. */
final class ChannelListRuntimeChecks {
    static void run(World world,EntityPlayer player) {
        BlockVandorSwitch rocker=(BlockVandorSwitch)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:rocker_switch"));
        BlockVandorSwitch button=(BlockVandorSwitch)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs:push_button"));
        BlockPos[] cells=new BlockPos[7];
        for(int i=0;i<cells.length;i++){cells[i]=new BlockPos(4+i*4,25,32);world.setBlockState(cells[i].down(),Blocks.STONE.getDefaultState(),3);}
        try {
            for(int i=0;i<3;i++)world.setBlockState(cells[i],(i==2?button:rocker).getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP),3);
            TileEntityRedstoneChannel a=(TileEntityRedstoneChannel)world.getTileEntity(cells[0]);
            TileEntityRedstoneChannel b=(TileEntityRedstoneChannel)world.getTileEntity(cells[1]);
            TileEntityRedstoneChannel momentary=(TileEntityRedstoneChannel)world.getTileEntity(cells[2]);
            a.setRedstoneChannels(ChannelList.of(14841,14842));b.setRedstoneChannels(ChannelList.of(14842,14843));momentary.setRedstoneChannels(ChannelList.of(14841,14843));
            TileEntityProgrammableLight[] lights=new TileEntityProgrammableLight[4];
            for(int i=0;i<4;i++) {
                world.setBlockState(cells[i+3],ModBlocks.PROGRAMMABLE_LIGHT.getDefaultState(),3);
                lights[i]=(TileEntityProgrammableLight)world.getTileEntity(cells[i+3]);
                lights[i].configure(0,15,false,0,0,SpaceDoorData.TRIGGER_REDSTONE_ON);
                lights[i].setRedstoneChannels(i==3?ChannelList.of(14841,14843):ChannelList.of(14841+i));
            }
            click(rocker,world,cells[0],player);check(lights,true,true,false,true);
            require(a.isLocalOn() && !b.isLocalOn(),"partial bank incorrectly marked on");
            click(rocker,world,cells[1],player);check(lights,true,true,true,true);
            click(rocker,world,cells[0],player);check(lights,false,false,true,true);
            require(!a.isLocalOn() && !b.isLocalOn(),"partial switch did not retain separate channel state");
            TileEntityRedstoneChannel restored=new TileEntityRedstoneChannel();restored.readFromNBT(b.writeToNBT(new NBTTagCompound()));
            require(restored.latchedChannels().equals(ChannelList.of(14843)),"partial latch persistence lost");
            click(button,world,cells[2],player);check(lights,true,false,true,true);
            button.updateTick(world,cells[2],world.getBlockState(cells[2]),world.rand);check(lights,false,false,true,true);
            require(!b.isLocalOn(),"momentary button latched another switch");
            world.setBlockToAir(cells[1]);check(lights,false,false,false,false);
            System.out.println("[vandorlabs][reprolab] channel-list-runtime PASS multiple outputs, consumer OR, independent partial latches, button release, persistence and source removal");
        } finally {for(BlockPos pos:cells){world.setBlockToAir(pos);world.setBlockToAir(pos.down());}}
    }
    private static void click(BlockVandorSwitch block,World world,BlockPos pos,EntityPlayer player){block.onBlockActivated(world,pos,world.getBlockState(pos),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);}
    private static void check(TileEntityProgrammableLight[] lights,boolean... expected){for(int i=0;i<lights.length;i++)require(lights[i].isOn()==expected[i],"receiver "+i+" has wrong OR state");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("channel-list runtime: "+message);}
    private ChannelListRuntimeChecks(){}
}
