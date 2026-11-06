package com.vandorlabs.tiles;

import net.minecraft.block.state.IBlockState;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.persistence.LightData;
import com.vandorlabs.persistence.NbtPrimitiveData;
import com.vandorlabs.light.ProgrammableLightPolicy;
import com.vandorlabs.blocks.ProgrammableLightConnections;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.EnumSkyBlock;

/** Per-block artwork, switch state and emitted light level. */
public class TileEntityProgrammableLight extends TileEntityAnimatedScreenSelector {
    private int texture;
    private int faceTexture=-1,offFaceTexture=-1;
    public int getOffFaceTexture(){return offFaceTexture;}
    public void setOffFaceTexture(int choice){int next=choice<0?-1:ScreenHousingTextures.clamp(choice);if(offFaceTexture!=next){offFaceTexture=next;changed();}}
    public String activeFaceTexture(){boolean lit=isOn() && getLightLevel()>0;return !lit && offFaceTexture>=0?ScreenHousingTextures.texture(offFaceTexture):ScreenHousingTextures.texture(getFaceTexture(),lit);}

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
    public boolean isManual() { return ProgrammableLightPolicy.manualToggleAllowed(trigger); }
    private static long joinRevision;

    public static long getJoinRevision() { return joinRevision; }

    public int getTexture() { return texture; }
    public int getLightLevel() {
        int direct=signalBrightness&&!join?getSignalLevel():0;
        return ProgrammableLightPolicy.level(signalBrightness,lightLevel,join,direct,joinedLevel,lightOffset);
    }
    public boolean isOn() {
        // Keep the original lazy input sampling: manual and joined lights need no direct-world query.
        int level=signalBrightness?getLightLevel():0;
        boolean direct=!signalBrightness&&!isManual()&&!join&&isTriggerPowered();
        return ProgrammableLightPolicy.on(signalBrightness,level,trigger,on,join,direct,joinedTriggerPower);
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
        LightData.writeFields(new NbtPrimitiveData(tag),texture,faceTexture,offFaceTexture,lightLevel,
                signalBrightness,lightOffset,joinedLevel,on,join,trigger,joinedTriggerPower);
        return tag;
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        LightData data=LightData.read(new NbtPrimitiveData(tag),getRedstoneChannel(),ScreenHousingTextures::clamp);
        signalBrightness=data.signalBrightness;lightOffset=data.offset;joinedLevel=data.joinedLevel;
        offFaceTexture=data.offFaceTexture;faceTexture=data.faceTexture;texture=data.texture;
        lightLevel=data.configuredLevel;on=data.manualOn;join=data.join;
        joinedTriggerPower=data.joinedPower;trigger=data.trigger;
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
