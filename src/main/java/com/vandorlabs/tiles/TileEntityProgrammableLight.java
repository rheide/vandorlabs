package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.blocks.ProgrammableLightConnections;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.EnumSkyBlock;

/** Per-block artwork, switch state and emitted light level. */
public class TileEntityProgrammableLight extends TileEntityAnimatedScreenSelector {
    private int texture;
    private int faceTexture=-1;
    public int getFaceTexture(){return faceTexture<0?ScreenHousingTextures.lightIndex(texture):faceTexture;}
    public void setFaceTexture(int choice){int next=ScreenHousingTextures.clamp(choice);if(faceTexture==next)return;faceTexture=next;changed();}
    private int lightLevel = 15;
    private boolean signalBrightness;
    private int lightOffset,joinedLevel;
    public boolean isSignalBrightness(){return signalBrightness;}
    public int getConfiguredLightLevel(){return lightLevel;}
    public int getLightOffset(){return lightOffset;}
    public void configureSignalBrightness(boolean enabled,int offset){
        signalBrightness=enabled;lightOffset=Math.max(-15,Math.min(15,offset));changed();
    }
    public void setJoinedSignalLevel(int level){
        if(joinedLevel==level && joinedTriggerPower==(level>0))return;
        joinedLevel=level;joinedTriggerPower=level>0;notifyChanged();
    }
    @Override public void setChannelLevel(int level){super.setChannelLevel(level);ProgrammableLightConnections.refreshAround(world,pos);}

    private boolean on = true;
    private boolean join;
    private boolean joinedTriggerPower;
    private boolean unloading;

    public boolean isAvailableForJoining() { return !isInvalid() && !unloading; }

    public boolean hasDirectTriggerPower() { return isTriggerPowered(); }

    public void setJoinedTriggerPower(boolean powered) {
        if (joinedTriggerPower == powered) return;
        joinedTriggerPower = powered;
        notifyChanged();
    }
    private int trigger = SpaceDoorData.TRIGGER_DISABLED;

    public int getTrigger() { return trigger; }
    public boolean isManual() { return trigger == SpaceDoorData.TRIGGER_DISABLED; }
    private static long joinRevision;

    public static long getJoinRevision() { return joinRevision; }

    public int getTexture() { return texture; }
    public int getLightLevel() { return signalBrightness?Math.max(0,Math.min(15,(join?joinedLevel:getSignalLevel())+lightOffset)):lightLevel; }
    public boolean isOn() {
        if(signalBrightness)return getLightLevel()>0;
        return isManual() ? on : SpaceDoorData.openForSignal(trigger, join ? joinedTriggerPower : isTriggerPowered());
    }
    public boolean isManualOn() { return on; }
    public boolean isJoin() { return join; }

    public void configure(int selectedTexture, int selectedLevel) {
        configure(selectedTexture, selectedLevel, join, getRedstoneChannels(),getHousingTexture(),trigger);
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
        configure(selectedTexture,selectedLevel,selectedJoin,com.vandorlabs.redstone.ChannelList.of(Math.max(0,selectedChannel)),selectedHousing,selectedTrigger);
    }
    public void configure(int selectedTexture,int selectedLevel,boolean selectedJoin,
            com.vandorlabs.redstone.ChannelList selectedChannel,int selectedHousing,int selectedTrigger) {
        if (!SpaceDoorData.validTrigger(selectedTrigger)) return;
        int nextTexture = ProgrammableLightTextures.clamp(selectedTexture);
        int nextLevel = Math.max(0, Math.min(15, selectedLevel));
        int nextHousing = ScreenHousingTextures.clamp(selectedHousing);
        if (texture == nextTexture && lightLevel == nextLevel && join == selectedJoin
                && getRedstoneChannels().equals(selectedChannel)
                && getHousingTexture() == nextHousing && trigger == selectedTrigger) return;
        texture = nextTexture;
        lightLevel = nextLevel;
        join = selectedJoin;
        trigger = selectedTrigger;
        setHousingTexture(nextHousing);
        if (!getRedstoneChannels().equals(selectedChannel)) setRedstoneChannels(selectedChannel);
        else changed();
    }

    @Override public boolean isSmallInput(){return getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableLightFrame && super.isSmallInput();}
    @Override public void setSmallInput(boolean small) {
        small=small && getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableLightFrame;
        if(isSmallInput()==small)return;
        super.setSmallInput(small);changed();
    }
    public void setOn(boolean value) {
        if (!isManual() || on == value) return;
        on = value;
        changed();
    }

    @Override public void setRedstoneChannel(int value) {
        setRedstoneChannels(com.vandorlabs.redstone.ChannelList.of(Math.max(0,value)));
    }
    @Override public void setRedstoneChannels(com.vandorlabs.redstone.ChannelList next) {
        if (getRedstoneChannels().equals(next)) return;
        super.setRedstoneChannels(next);
        changed();
    }

    @Override public void setChannelSignal(boolean powered) {
        boolean wasOn = isOn();
        super.setChannelSignal(powered);
        if (wasOn != isOn()) joinRevision++;
        ProgrammableLightConnections.refreshAround(world, pos);
    }

    @Override public void localInputChanged() {
        super.localInputChanged();
        changed();
    }

    private void changed() {
        ProgrammableLightConnections.refreshAround(world, pos);
        notifyChanged();
    }

    @Override public void onLoad() {
        unloading = false;
        super.onLoad();
    }

    @Override protected void finishLoading() {
        super.finishLoading();
        ProgrammableLightConnections.refreshAround(world, pos);
    }

    @Override public void onChunkUnload() {
        unloading = true;
        super.onChunkUnload();
        ProgrammableLightConnections.refreshAround(world, pos);
    }

    @Override public void invalidate() {
        super.invalidate();
        ProgrammableLightConnections.refreshAround(world, pos);
    }

    private void notifyChanged() {
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
        tag.setInteger("LightFaceTexture",faceTexture);
        tag.setInteger("LightLevel", lightLevel);
        tag.setBoolean("SignalBrightness",signalBrightness);tag.setInteger("LightOffset",lightOffset);tag.setInteger("JoinedSignalLevel",joinedLevel);
        tag.setBoolean("LightOn", on);
        tag.setBoolean("LightJoin", join);
        tag.setInteger("LightTrigger", trigger);
        tag.setBoolean("LightGroupPowered", joinedTriggerPower);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        signalBrightness=tag.getBoolean("SignalBrightness");lightOffset=Math.max(-15,Math.min(15,tag.getInteger("LightOffset")));joinedLevel=Math.max(0,Math.min(15,tag.getInteger("JoinedSignalLevel")));
        faceTexture=tag.hasKey("LightFaceTexture",3) && tag.getInteger("LightFaceTexture")>=0?ScreenHousingTextures.clamp(tag.getInteger("LightFaceTexture")):-1;
        texture = ProgrammableLightTextures.clamp(tag.getInteger("LightTexture"));
        lightLevel = tag.hasKey("LightLevel", 3)
                ? Math.max(0, Math.min(15, tag.getInteger("LightLevel"))) : 15;
        on = !tag.hasKey("LightOn") || tag.getBoolean("LightOn");
        join = tag.getBoolean("LightJoin");
        joinedTriggerPower = tag.getBoolean("LightGroupPowered");
        trigger = tag.hasKey("LightTrigger", 3) ? tag.getInteger("LightTrigger")
                : getRedstoneChannel() > 0 ? SpaceDoorData.TRIGGER_REDSTONE_ON
                : SpaceDoorData.TRIGGER_DISABLED;
        if (!SpaceDoorData.validTrigger(trigger)) trigger = SpaceDoorData.TRIGGER_DISABLED;
        joinRevision++;
        if (world != null && pos != null) {
            if (world.isRemote) world.checkLightFor(EnumSkyBlock.BLOCK, pos);
            else DeferredTileLoad.schedule(this, () -> {
                ProgrammableLightConnections.refreshAround(world, pos);
                world.checkLightFor(EnumSkyBlock.BLOCK, pos);
            });
        }
    }
}
