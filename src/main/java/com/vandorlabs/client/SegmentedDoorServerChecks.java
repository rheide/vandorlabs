package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.DeferredTileLoad;
import com.vandorlabs.world.WorldListener;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import java.util.*;

/** Large native server matrix, separate from the renderer-only comparison. */
final class SegmentedDoorServerChecks {
    private static void require(boolean b,String s){if(!b)throw new IllegalStateException("segmented-server-density: "+s);}
    static void run(net.minecraft.client.Minecraft mc)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());WorldServer world=player.getServerWorld();Block block=Block.getBlockFromName("vandorlabs:segmented_door");BlockPos origin=new BlockPos(812,94,800);List<BlockPos> roots=new ArrayList<>();
            for(int group=0;group<64;group++)for(int column=0;column<9;column++){
                BlockPos bottom=origin.add(group%8*12+column,0,group/8*8);for(int row=0;row<5;row++)world.setBlockState(bottom.up(row),block.getDefaultState(),3);roots.add(bottom.up(4));
            }
            for(int i=0;i<6;i++)DeferredTileLoad.flush(world);
            for(int group=0;group<64;group++){
                SegmentedDoorTile top=SegmentedDoorTile.at(world,roots.get(group*9));require(top.columns().size()==9,"separate linked group "+group);top.configure(player,ChannelList.of(64000+group),10,false,true);
                for(int column=0;column<9;column++){SegmentedDoorTile root=SegmentedDoorTile.at(world,roots.get(group*9+column));require(root.isRoot()&&root.height==5&&!root.targetOpen&&root.getRedstoneChannels().contains(64000+group),"configuration leaked between groups");}
            }
            final int[] publications={0},bytes={0};WorldListener observer=new WorldListener(){@Override public void notifyBlockUpdate(World w,BlockPos pos,IBlockState before,IBlockState after,int flags){if(after.getBlock()!=block||pos.getX()<812)return;SegmentedDoorTile tile=SegmentedDoorTile.at(world,pos);if(tile==null)return;require(tile.isRoot(),"motion published a child update");publications[0]++;net.minecraft.network.PacketBuffer packet=new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());try{tile.getUpdatePacket().writePacketData(packet);bytes[0]+=packet.readableBytes();}catch(java.io.IOException e){throw new IllegalStateException(e);}finally{packet.release();}}};
            world.addEventListener(observer);
            try{
                SegmentedDoorTile.at(world,roots.get(0)).setChannelSignal(true);DeferredTileLoad.flush(world);require(publications[0]==9,"one group should publish nine owners");require(!SegmentedDoorTile.at(world,roots.get(9)).targetOpen,"channel signal crossed independent group");
                for(int group=1;group<64;group++)SegmentedDoorTile.at(world,roots.get(group*9)).setChannelSignal(true);DeferredTileLoad.flush(world);require(publications[0]==576,"motion publication count "+publications[0]);
                for(BlockPos pos:roots)require(SegmentedDoorTile.at(world,pos).targetOpen,"linked owner missed command");
                for(int repeat=0;repeat<100;repeat++)for(int group=0;group<64;group++)SegmentedDoorTile.at(world,roots.get(group*9)).setChannelSignal(true);DeferredTileLoad.flush(world);require(publications[0]==576,"unchanged signal emitted updates");
            }finally{world.removeEventListener(observer);}
            BlockPos position=roots.get(0);SegmentedDoorTile before=SegmentedDoorTile.at(world,position);NBTTagCompound tag=before.writeToNBT(new NBTTagCompound());SegmentedDoorTile replacement=new SegmentedDoorTile();replacement.readFromNBT(tag);world.setTileEntity(position,replacement);DeferredTileLoad.flush(world);require(replacement.identity.equals(before.identity)&&replacement.targetOpen&&replacement.height==5&&replacement.getRedstoneChannels().equals(before.getRedstoneChannels()),"reloaded owner lost saved fields");
            System.out.println("[vandorlabs][reprolab] segmented-server-density PASS groups=64 sections=2880 roots=576 motion-publications="+publications[0]+" packet-payload-bytes="+bytes[0]+" per-section-publications-avoided=2304 repeated-signals=6400 independent-channels owner-reload");
            for(int group=0;group<64;group++)for(int column=0;column<9;column++){BlockPos top=roots.get(group*9+column);for(int row=0;row<5;row++)world.setBlockToAir(top.down(row));}
        }).get();
    }
    private SegmentedDoorServerChecks(){}
}
