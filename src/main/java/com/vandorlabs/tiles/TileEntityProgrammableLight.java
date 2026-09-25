package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.EnumSkyBlock;

/** Per-block artwork, switch state and emitted light level. */
public class TileEntityProgrammableLight extends TileEntityAnimatedScreenSelector {
    private int texture;
    private int lightLevel = 15;
    private boolean on = true;
    private boolean join;
    private static long joinRevision;

    public static long getJoinRevision() { return joinRevision; }

    public int getTexture() { return texture; }
    public int getLightLevel() { return lightLevel; }
    public boolean isOn() {
        return getRedstoneChannel() > 0 ? getEffectiveMode() != MODE_OFF : on;
    }
    public boolean isJoin() { return join; }

    public void configure(int selectedTexture, int selectedLevel) {
        configure(selectedTexture, selectedLevel, join, getRedstoneChannel());
    }

    public void configure(int selectedTexture, int selectedLevel, boolean selectedJoin,
            int selectedChannel) {
        int nextTexture = ProgrammableLightTextures.clamp(selectedTexture);
        int nextLevel = Math.max(0, Math.min(15, selectedLevel));
        if (texture == nextTexture && lightLevel == nextLevel && join == selectedJoin
                && getRedstoneChannel() == selectedChannel) return;
        texture = nextTexture;
        lightLevel = nextLevel;
        join = selectedJoin;
        if (getRedstoneChannel() != selectedChannel) setRedstoneChannel(selectedChannel);
        else changed();
    }

    public void setOn(boolean value) {
        if (getRedstoneChannel() > 0 || on == value) return;
        on = value;
        changed();
    }

    @Override public void setRedstoneChannel(int value) {
        if (getRedstoneChannel() == Math.max(0, value)) return;
        super.setRedstoneChannel(value);
        setRedstoneEnabled(value > 0);
        changed();
    }

    @Override public void setChannelSignal(boolean powered) {
        boolean wasOn = isOn();
        super.setChannelSignal(powered);
        if (wasOn != isOn()) joinRevision++;
    }

    private void changed() {
        joinRevision++;
        markDirty();
        if (world != null && pos != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            world.checkLightFor(EnumSkyBlock.BLOCK, pos);
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("LightTexture", texture);
        tag.setInteger("LightLevel", lightLevel);
        tag.setBoolean("LightOn", on);
        tag.setBoolean("LightJoin", join);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        texture = ProgrammableLightTextures.clamp(tag.getInteger("LightTexture"));
        lightLevel = tag.hasKey("LightLevel", 3)
                ? Math.max(0, Math.min(15, tag.getInteger("LightLevel"))) : 15;
        on = !tag.hasKey("LightOn") || tag.getBoolean("LightOn");
        join = tag.getBoolean("LightJoin");
        setRedstoneEnabled(getRedstoneChannel() > 0);
        joinRevision++;
        if (world != null && pos != null) world.checkLightFor(EnumSkyBlock.BLOCK, pos);
    }
}
