package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Loaded receiver identity. Received signals never become independent transmitted sources. */
public interface NativeChannelMember {
    BlockEntity channelBlockEntity();
    ChannelList getRedstoneChannels();
    void setChannelLevel(int level);
    default BlockPos channelPosition(){return channelBlockEntity().getBlockPos();}
    default boolean isChannelMemberActive(){
        BlockEntity tile=channelBlockEntity();
        return tile!=null&&!tile.isRemoved()&&tile.getLevel()!=null&&tile.getLevel().hasChunkAt(tile.getBlockPos())
            &&tile.getLevel().getBlockEntity(tile.getBlockPos())==tile;
    }
}
