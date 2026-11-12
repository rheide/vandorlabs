package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.TileEntityProgrammableTrigger;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Narrow source lookup acceptance through actual installed tile identity and Trigger consumers. */
final class ChannelSourceIndexRuntimeChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("channel source index: "+message);}
    private static final class Source implements RedstoneChannelMember {
        final TileEntity tile;final int channel;int level;
        Source(TileEntity tile,int channel){this.tile=tile;this.channel=channel;}
        public TileEntity channelTile(){return tile;}
        public int getRedstoneChannel(){return 0;}public void setRedstoneChannel(int channel){}
        public ChannelList getRedstoneChannels(){return ChannelList.EMPTY;}
        public ChannelList transmittedChannels(){return ChannelList.of(channel);}
        public int transmissionRange(){return 16;}
        public boolean hasLocalRedstoneSignal(){return level>0;}public int localSignalLevel(int channel){return level;}
        public void setChannelSignal(boolean on){}
    }
    static void run(Minecraft mc)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(mc.player.dimension);
            RedstoneRangeRuntimeChecks.sourceIndexFocus(world);placed(world);
        }).get();
    }
    private static void placed(WorldServer world){
        checks=0;int channel=65301;BlockPos origin=new BlockPos(2100,160,2100),edge=origin.east(16),outside=origin.east(17);
        Source source=null,replacement=null;
        try{
            world.getChunkFromBlockCoords(origin);world.getChunkFromBlockCoords(edge);
            world.setBlockState(origin.down(),Blocks.STONE.getDefaultState(),3);world.setBlockState(origin,Blocks.STANDING_SIGN.getDefaultState(),3);
            world.setBlockState(edge,ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK.getDefaultState(),3);world.setBlockState(outside,ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK.getDefaultState(),3);
            TileEntityProgrammableTrigger inside=(TileEntityProgrammableTrigger)world.getTileEntity(edge),beyond=(TileEntityProgrammableTrigger)world.getTileEntity(outside);
            inside.configureLevels(true,-1,0,0);inside.setRedstoneChannels(ChannelList.of(channel));beyond.setRedstoneChannels(ChannelList.of(channel));RedstoneChannels.register(inside);RedstoneChannels.register(beyond);
            source=new Source(world.getTileEntity(origin),channel);source.level=8;RedstoneChannels.register(source);
            require(source.isChannelMemberActive(),"actual installed source tile identity");
            require((origin.getX()>>4)!=(edge.getX()>>4),"range edge crosses chunk boundary");
            require(inside.getSignalLevel()==8&&inside.getVisibleSlot()==2&&beyond.getSignalLevel()==0,"placed Trigger receives exact analog edge, outside stays off");
            long[] before=RedstoneChannels.diagnostics(world);RedstoneChannels.inputChanged(source);long[] after=RedstoneChannels.diagnostics(world);
            for(int counter=0;counter<before.length;counter++)require(after[counter]==before[counter],"identical source update skips index/recipient work counter="+counter);
            source.level=15;RedstoneChannels.inputChanged(source);require(inside.getSignalLevel()==15&&inside.getVisibleSlot()==3,"placed Trigger full signal");
            source.level=0;RedstoneChannels.inputChanged(source);require(inside.getSignalLevel()==0&&inside.getVisibleSlot()==0,"placed Trigger source withdrawal");
            source.level=8;RedstoneChannels.inputChanged(source);
            world.setBlockState(edge.up(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);require(inside.getSignalLevel()==15,"local physical power dominates analog channel");
            world.setBlockToAir(edge.up());require(inside.getSignalLevel()==8,"channel signal retained after local power removal");
            world.removeTileEntity(origin);world.setTileEntity(origin,new TileEntitySign());
            require(!source.isChannelMemberActive()&&RedstoneChannels.level(world,edge,channel)==0,"stale replaced source filtered by actual tile identity before unregister");
            RedstoneChannels.unregister(source);require(inside.getSignalLevel()==0,"replacement withdrawal delivered to Trigger");
            replacement=new Source(world.getTileEntity(origin),channel);replacement.level=3;RedstoneChannels.register(replacement);
            require(inside.getSignalLevel()==3&&inside.getVisibleSlot()==1,"replacement source activates low artwork state");
            world.setBlockToAir(origin);require(!replacement.isChannelMemberActive()&&RedstoneChannels.level(world,edge,channel)==0,"removed physical source cannot publish stale strength");
            RedstoneChannels.unregister(replacement);require(inside.getSignalLevel()==0,"removed source withdrawal delivered");
            System.out.println("[vandorlabs][reprolab] channel-source-index-live PASS checks="+checks+" placed-trigger analog-bands cross-chunk-edge local-max replacement removal exact-deliveries");
        }finally{
            if(source!=null)RedstoneChannels.unregister(source);if(replacement!=null)RedstoneChannels.unregister(replacement);
            world.setBlockToAir(edge.up());world.setBlockToAir(edge);world.setBlockToAir(outside);world.setBlockToAir(origin);world.setBlockToAir(origin.down());
        }
    }
}
