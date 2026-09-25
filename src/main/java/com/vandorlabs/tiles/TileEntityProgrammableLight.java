package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import com.vandorlabs.persistence.SpaceDoorData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.EnumSkyBlock;

/** Per-block artwork, switch state and emitted light level. */
public class TileEntityProgrammableLight extends TileEntityAnimatedScreenSelector {
    private int texture;
    private int lightLevel = 15;
    private boolean on = true;
    private boolean join;
    private int trigger = SpaceDoorData.TRIGGER_DISABLED;

    public int getTrigger() { return trigger; }
    public boolean isManual() { return trigger == SpaceDoorData.TRIGGER_DISABLED; }
    private static long joinRevision;

    public static long getJoinRevision() { return joinRevision; }

    public int getTexture() { return texture; }
    public int getLightLevel() { return lightLevel; }
    public boolean isOn() {
        return isManual() ? on : SpaceDoorData.openForSignal(trigger, isTriggerPowered());
    }
    public boolean isJoin() { return join; }

    public void configure(int selectedTexture, int selectedLevel) {
        configure(selectedTexture, selectedLevel, join, getRedstoneChannel());
    }

    public void configure(int selectedTexture, int selectedLevel, boolean selectedJoin,
            int selectedChannel) {
        configure(selectedTexture, selectedLevel, selectedJoin, selectedChannel,
                getHousingTexture());
    }

    public void configure(int selectedTexture, int selectedLevel, boolean selectedJoin,
            int selectedChannel, int selectedHousing) {
        configure(selectedTexture, selectedLevel, selectedJoin, selectedChannel,
                selectedHousing, trigger);
    }

    public void configure(int selectedTexture, int selectedLevel, boolean selectedJoin,
            int selectedChannel, int selectedHousing, int selectedTrigger) {
        if (!SpaceDoorData.validTrigger(selectedTrigger)) return;
        int nextTexture = ProgrammableLightTextures.clamp(selectedTexture);
        int nextLevel = Math.max(0, Math.min(15, selectedLevel));
        int nextHousing = ScreenHousingTextures.clamp(selectedHousing);
        if (texture == nextTexture && lightLevel == nextLevel && join == selectedJoin
                && getRedstoneChannel() == selectedChannel
                && getHousingTexture() == nextHousing && trigger == selectedTrigger) return;
        texture = nextTexture;
        lightLevel = nextLevel;
        join = selectedJoin;
        trigger = selectedTrigger;
        setHousingTexture(nextHousing);
        if (getRedstoneChannel() != selectedChannel) setRedstoneChannel(selectedChannel);
        else changed();
    }

    public void setOn(boolean value) {
        if (!isManual() || on == value) return;
        on = value;
        changed();
    }

    @Override public void setRedstoneChannel(int value) {
        if (getRedstoneChannel() == Math.max(0, value)) return;
        super.setRedstoneChannel(value);
        changed();
    }

    @Override public void setChannelSignal(boolean powered) {
        boolean wasOn = isOn();
        super.setChannelSignal(powered);
        if (wasOn != isOn()) joinRevision++;
    }

    @Override public void localInputChanged() {
        super.localInputChanged();
        if (!isManual()) changed();
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
        tag.setInteger("LightTrigger", trigger);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        texture = ProgrammableLightTextures.clamp(tag.getInteger("LightTexture"));
        lightLevel = tag.hasKey("LightLevel", 3)
                ? Math.max(0, Math.min(15, tag.getInteger("LightLevel"))) : 15;
        on = !tag.hasKey("LightOn") || tag.getBoolean("LightOn");
        join = tag.getBoolean("LightJoin");
        trigger = tag.hasKey("LightTrigger", 3) ? tag.getInteger("LightTrigger")
                : getRedstoneChannel() > 0 ? SpaceDoorData.TRIGGER_REDSTONE_ON
                : SpaceDoorData.TRIGGER_DISABLED;
        if (!SpaceDoorData.validTrigger(trigger)) trigger = SpaceDoorData.TRIGGER_DISABLED;
        joinRevision++;
        if (world != null && pos != null) world.checkLightFor(EnumSkyBlock.BLOCK, pos);
    }
}
