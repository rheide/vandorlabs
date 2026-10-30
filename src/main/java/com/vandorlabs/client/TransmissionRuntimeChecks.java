package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.network.MessageTransmitterSlot;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.io.File;

/** Finite built-in transmission, independent physical output, migration and real dialog packets. */
final class TransmissionRuntimeChecks {
    private static int ticks;
    private static boolean ready;
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Transmission settings: "+message);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(++ticks==1){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;}
        if(ready)return InlineTransmitterLiveChecks.tick(mc,output);
        if(ticks<80)return false;
        mc.getIntegratedServer().callFromMainThread(()->{WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);run(world);SharedScreenTransmitterChecks.run(world,actor);WirelessRedstoneRuntimeChecks.run(world);com.vandorlabs.redstone.TransmitterMigrationChecks.run();return null;}).get();
        TransmissionDialogChecks.run(mc,output);AutopilotDialogChecks.run(mc,output);ready=true;System.out.println("[vandorlabs][reprolab] transmission-settings PASS seven-ranges sphere boundaries physical-toggle saved-reload legacy-items carried-rows packet-roundtrip");return false;
    }
    private static void run(WorldServer world){
        BlockPos pos=new BlockPos(2110,170,2110);world.getChunkFromBlockCoords(pos);
        Block block=Block.getBlockFromName("vandorlabs:wall_slider");
        try{
            world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(pos,block.getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.UP),2);
            TileEntityRedstoneChannel tile=(TileEntityRedstoneChannel)world.getTileEntity(pos);DeferredTileLoad.flush(world);
            require(TransmitterSlots.get(tile).isEmpty()&&tile.transmissionRange()==16,"fresh control needs no hardware and defaults to 16 blocks");
            for(int range:TransmitterSlots.RANGES){
                TransmitterSlots.configure(tile,ChannelList.of(62321),range,true);tile.setLocalOn(true);
                require(RedstoneChannels.level(world,pos.east(range),62321)>0,"inclusive sphere boundary "+range);
                require(RedstoneChannels.level(world,pos.add(range,1,0),62321)==0,"outside sphere "+range);
                require(block.getWeakPower(world.getBlockState(pos),world,pos,EnumFacing.UP)>0,"physical output On "+range);
                TransmitterSlots.configure(tile,ChannelList.of(62321),range,false);
                require(block.getWeakPower(world.getBlockState(pos),world,pos,EnumFacing.UP)==0&&block.getStrongPower(world.getBlockState(pos),world,pos,EnumFacing.UP)==0,"physical output Off "+range);
                require(RedstoneChannels.level(world,pos.east(range),62321)>0,"channels retained while physical output Off "+range);
                NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());TileEntity restored=TileEntity.create(world,saved);require(restored instanceof TileEntityRedstoneChannel&&((RedstoneChannelMember)restored).transmissionRange()==range&&!TransmitterSlots.localRedstone(restored),"saved settings "+range);
                io.netty.buffer.ByteBuf encoded=io.netty.buffer.Unpooled.buffer();MessageTransmitterSlot packet=MessageTransmitterSlot.settings(14,0,ChannelList.of(62321),range,false);packet.toBytes(encoded);int bytes=encoded.readableBytes();MessageTransmitterSlot decoded=new MessageTransmitterSlot();decoded.fromBytes(encoded);io.netty.buffer.ByteBuf roundtrip=io.netty.buffer.Unpooled.buffer();decoded.toBytes(roundtrip);io.netty.buffer.ByteBuf original=io.netty.buffer.Unpooled.buffer();packet.toBytes(original);require(original.equals(roundtrip)&&bytes<64,"bounded settings packet roundtrip "+range);encoded.release();roundtrip.release();original.release();
            }
            NBTTagCompound copied=com.vandorlabs.items.ProgrammableSettings.capture(world,pos);TransmitterSlots.configure(tile,ChannelList.of(62322),4,true);
            require(com.vandorlabs.items.ProgrammableSettings.apply(world,pos,copied)&&tile.transmissionRange()==256&&!TransmitterSlots.localRedstone(tile)&&tile.transmittedChannels().equals(ChannelList.of(62321)),"Copifier restores built-in settings without hardware");
            NBTTagCompound before=tile.writeToNBT(new NBTTagCompound());try{TransmitterSlots.configure(tile,ChannelList.of(1),3,true);throw new IllegalStateException("accepted invalid range");}catch(IllegalArgumentException expected){}require(before.equals(tile.writeToNBT(new NBTTagCompound())),"invalid settings cannot mutate tile");
            // Legacy hardware remains owned by the block, while its settings can change freely.
            net.minecraft.item.ItemStack legacy=new net.minecraft.item.ItemStack(com.vandorlabs.items.ModItems.REDSTONE_TRANSMITTER,1,3);com.vandorlabs.items.ItemRedstoneTransmitter.configure(legacy,ChannelList.of(62322));TileEntityRedstoneChannel old=new TileEntityRedstoneChannel();TransmitterSlots.set(old,legacy);require(old.transmissionRange()==256,"old infinite block hardware caps at 256");TransmitterSlots.configure(old,ChannelList.of(62323),8,false);require(net.minecraft.item.ItemStack.areItemStacksEqual(legacy,TransmitterSlots.get(old)),"editing settings retains exact legacy hardware for recovery");
            io.netty.buffer.ByteBuf malformed=io.netty.buffer.Unpooled.buffer();MessageTransmitterSlot.settings(14,0,ChannelList.of(1),3,true).toBytes(malformed);try{new MessageTransmitterSlot().fromBytes(malformed);throw new IllegalStateException("decoded invalid range");}catch(IllegalArgumentException expected){}finally{malformed.release();}
        }finally{world.setBlockToAir(pos);world.setBlockToAir(pos.down());}
    }
}
