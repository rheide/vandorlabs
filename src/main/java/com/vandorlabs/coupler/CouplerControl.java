package com.vandorlabs.coupler;

import com.vandorlabs.redstone.ChannelList;
import net.minecraft.entity.player.EntityPlayer;

/** Dedicated dialog contract shared by installed tiles and captured scalar controls. */
public interface CouplerControl {
    ChannelList getRedstoneChannels();
    boolean inverted();
    boolean articulation();
    boolean requested();
    String status();
    void status(String value);
    void authorize(EntityPlayer actor);
    void configure(ChannelList channels,boolean inverted,boolean articulation);
}
