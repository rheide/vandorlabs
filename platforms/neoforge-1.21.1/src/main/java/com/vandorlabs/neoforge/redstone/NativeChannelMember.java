package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Loaded channel identity; only explicit local output becomes an independent source. */
public interface NativeChannelMember {
    BlockEntity channelBlockEntity();
    ChannelList getRedstoneChannels();
    void setChannelLevel(int level);
    default ChannelList transmittedChannels(){return ChannelList.EMPTY;}
    default int transmissionRange(){return -1;}
    default int localSignalLevel(int channel){return 0;}
    default BlockPos channelPosition(){return channelBlockEntity().getBlockPos();}
    default boolean isChannelMemberActive(){
        BlockEntity tile=channelBlockEntity();
        return tile!=null&&!tile.isRemoved()&&tile.getLevel()!=null&&tile.getLevel().hasChunkAt(tile.getBlockPos())
            &&tile.getLevel().getBlockEntity(tile.getBlockPos())==tile;
    }
}
