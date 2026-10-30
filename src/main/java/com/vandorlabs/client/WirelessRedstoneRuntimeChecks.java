package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.redstone.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Exact signal levels, one-sided wiring, both modes, multiple channels and save/load. */
final class WirelessRedstoneRuntimeChecks {
    static void require(boolean value,String message){if(!value)throw new IllegalStateException("wireless redstone: "+message);}
    static void run(WorldServer world){
        BlockPos txPos=new BlockPos(450,185,450),rxPos=txPos.east(12);
        BlockWirelessRedstone transmitter=(BlockWirelessRedstone)Block.getBlockFromName("vandorlabs:redstone_transmitter_block"),receiver=(BlockWirelessRedstone)Block.getBlockFromName("vandorlabs:redstone_receiver");
        Block slider=Block.getBlockFromName("vandorlabs:wall_slider");
        for(EnumFacing port:EnumFacing.values()){
            world.getChunkFromBlockCoords(txPos);world.getChunkFromBlockCoords(rxPos);
            world.setBlockState(txPos,transmitter.getDefaultState().withProperty(BlockWirelessRedstone.FACING,port),2);world.setBlockState(rxPos,receiver.getDefaultState().withProperty(BlockWirelessRedstone.FACING,port),2);
            TileEntityWirelessRedstone tx=(TileEntityWirelessRedstone)world.getTileEntity(txPos),rx=(TileEntityWirelessRedstone)world.getTileEntity(rxPos);
            net.minecraft.item.ItemStack module=new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.REDSTONE_TRANSMITTER,1,0);com.vandorlabs.items.ItemRedstoneTransmitter.configure(module,ChannelList.of(64801,64802));TransmitterSlots.set(tx,module);rx.setRedstoneChannels(ChannelList.of(64802,64803));RedstoneChannels.register(tx);RedstoneChannels.register(rx);
            BlockPos source=txPos.offset(port);
            try{
                world.setBlockState(source,slider.getDefaultState().withProperty(BlockVandorSwitch.FACING,port),2);TileEntitySignalControl control=(TileEntitySignalControl)world.getTileEntity(source);
                for(int level=0;level<=15;level++){
                    if(level>0)control.configureLimits(level<14?level:1,level<14?15:level);control.setStep(level==0?0:level<14?1:3);
                    tx.configure(true,15,0);rx.configure(true,8,3);tx.sampleInput();
                    require(RedstoneChannels.level(world,64801)==level&&RedstoneChannels.level(world,64802)==level,"transmitter analog level "+port+level+" actual="+RedstoneChannels.level(world,64801)+","+RedstoneChannels.level(world,64802)+" input="+tx.localSignalLevel(64801)+" source="+control.getOutputLevel()+" block="+world.getBlockState(source));
                    for(EnumFacing side:EnumFacing.values()){
                        int expected=side==port.getOpposite()?(level>0?level:3):0;
                        require(receiver.getWeakPower(world.getBlockState(rxPos),world,rxPos,side)==expected&&receiver.getStrongPower(world.getBlockState(rxPos),world,rxPos,side)==expected,"receiver directional output "+port+side+level);
                    }
                    rx.configure(false,8,3);require(rx.getOutputLevel()==(level>0?8:3),"digital On/Off levels");
                    tx.configure(false,15,0);rx.configure(true,15,0);require(rx.getOutputLevel()==(level>0?15:0),"digital transmitter threshold");
                }
                world.setBlockToAir(source);
                EnumFacing wrong=port==EnumFacing.UP?EnumFacing.NORTH:EnumFacing.UP;BlockPos wrongPos=txPos.offset(wrong);world.setBlockState(wrongPos,Blocks.REDSTONE_BLOCK.getDefaultState(),2);tx.sampleInput();require(RedstoneChannels.level(world,64802)==0,"unselected input face ignored");world.setBlockToAir(wrongPos);
                rx.configure(false,7,2);TileEntityWirelessRedstone restored=(TileEntityWirelessRedstone)net.minecraft.tileentity.TileEntity.create(world,rx.writeToNBT(new NBTTagCompound()));restored.setWorld(world);require(!restored.analog()&&restored.onLevel()==7&&restored.offLevel()==2&&restored.getRedstoneChannels().size()==2,"settings save/load");
                for(EnumFacing side:EnumFacing.values())require(transmitter.getWeakPower(world.getBlockState(txPos),world,txPos,side)==(TransmitterSlots.localRedstone(tx)&&side==world.getBlockState(txPos).getValue(com.vandorlabs.blocks.BlockWirelessRedstone.FACING)?((com.vandorlabs.tiles.TileEntityWirelessRedstone)world.getTileEntity(txPos)).localSignalLevel(0):0),"transmitter output uses the opposite wired port");
            }finally{world.setBlockToAir(source);world.setBlockToAir(txPos);world.setBlockToAir(rxPos);}
        }
        System.out.println("[vandorlabs][reprolab] wireless-redstone PASS six port directions all 16 levels analog/digital On/Off sliders multiple channels wrong-face rejection persistence");
    }
}
