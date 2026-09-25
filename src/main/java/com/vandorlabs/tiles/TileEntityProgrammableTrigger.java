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
        if (off < 0 || off >= ScreenHousingTextures.IDS.length
                || on < 0 || on >= ScreenHousingTextures.IDS.length
                || channel < 0) return;
        setHousingTexture(off);
        onTexture = on;
        setRedstoneChannel(channel);
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
