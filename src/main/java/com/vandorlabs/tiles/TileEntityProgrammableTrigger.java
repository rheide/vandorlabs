package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;

/** The inherited housing finish is the off finish. */
public final class TileEntityProgrammableTrigger extends TileEntityAnimatedScreenSelector {
    private int onTexture;

    public int getOnTexture() { return onTexture; }
    public int getVisibleTexture() {
        return isTriggerPowered() ? onTexture : getHousingTexture();
    }

    public void configure(int off, int on, int channel) {
        if(channel<0)return;
        configure(off,on,com.vandorlabs.redstone.ChannelList.of(channel));
    }
    public void configure(int off,int on,com.vandorlabs.redstone.ChannelList channels) {
        if (off < 0 || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(off)
                || on < 0 || !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(on)
                || channels == null) return;
        setHousingTexture(off);
        onTexture = on;
        setRedstoneChannels(channels);
        markDirty();
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("TriggerOnTexture", onTexture);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        onTexture = ScreenHousingTextures.clamp(tag.getInteger("TriggerOnTexture"));
    }
}
