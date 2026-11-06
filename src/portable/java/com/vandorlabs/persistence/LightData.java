package com.vandorlabs.persistence;

import com.vandorlabs.tiles.ProgrammableLightTextures;
import java.util.function.IntUnaryOperator;

/** Light-owned save fields only. Housing, channels, frames and transmission keep their shared codecs. */
public final class LightData {
    public final int texture,faceTexture,offFaceTexture,configuredLevel,offset,joinedLevel,trigger;
    public final boolean signalBrightness,manualOn,join,joinedPower;
    public LightData(int texture,int faceTexture,int offFaceTexture,int configuredLevel,boolean signalBrightness,int offset,int joinedLevel,boolean manualOn,boolean join,int trigger,boolean joinedPower){
        if(texture!=ProgrammableLightTextures.clamp(texture)||faceTexture< -1||offFaceTexture< -1||configuredLevel<0||configuredLevel>15||offset< -15||offset>15||joinedLevel<0||joinedLevel>15||!SpaceDoorData.validTrigger(trigger))throw new IllegalArgumentException("Invalid Light scalar");
        this.texture=texture;this.faceTexture=faceTexture;this.offFaceTexture=offFaceTexture;this.configuredLevel=configuredLevel;this.signalBrightness=signalBrightness;this.offset=offset;this.joinedLevel=joinedLevel;this.manualOn=manualOn;this.join=join;this.trigger=trigger;this.joinedPower=joinedPower;
    }
    public static LightData fresh(){return new LightData(0,-1,-1,15,false,0,0,true,false,0,false);}
    public static LightData read(HousingTags tag,int legacyChannel,IntUnaryOperator material){
        int face=tag.containsInt("LightFaceTexture")&&tag.getInt("LightFaceTexture")>=0?material.applyAsInt(tag.getInt("LightFaceTexture")):-1;
        int off=tag.containsInt("LightOffFaceTexture")&&tag.getInt("LightOffFaceTexture")>=0?material.applyAsInt(tag.getInt("LightOffFaceTexture")):-1;
        int trigger=tag.containsInt("LightTrigger")?tag.getInt("LightTrigger"):legacyChannel>0?SpaceDoorData.TRIGGER_REDSTONE_ON:SpaceDoorData.TRIGGER_DISABLED;
        if(!SpaceDoorData.validTrigger(trigger))trigger=SpaceDoorData.TRIGGER_DISABLED;
        return new LightData(ProgrammableLightTextures.clamp(tag.getInt("LightTexture")),face,off,tag.containsInt("LightLevel")?clamp(tag.getInt("LightLevel"),0,15):15,tag.getBoolean("SignalBrightness"),clamp(tag.getInt("LightOffset"),-15,15),clamp(tag.getInt("JoinedSignalLevel"),0,15),!tag.contains("LightOn")||tag.getBoolean("LightOn"),tag.getBoolean("LightJoin"),trigger,tag.getBoolean("LightGroupPowered"));
    }
    @Override public boolean equals(Object other){if(!(other instanceof LightData))return false;LightData value=(LightData)other;return texture==value.texture&&faceTexture==value.faceTexture&&offFaceTexture==value.offFaceTexture&&configuredLevel==value.configuredLevel&&offset==value.offset&&joinedLevel==value.joinedLevel&&trigger==value.trigger&&signalBrightness==value.signalBrightness&&manualOn==value.manualOn&&join==value.join&&joinedPower==value.joinedPower;}
    @Override public int hashCode(){int value=texture;value=31*value+faceTexture;value=31*value+offFaceTexture;value=31*value+configuredLevel;value=31*value+offset;value=31*value+joinedLevel;value=31*value+trigger;return 31*value+(signalBrightness?1:0)+(manualOn?2:0)+(join?4:0)+(joinedPower?8:0);}
    private static int clamp(int value,int low,int high){return Math.max(low,Math.min(high,value));}
    public void write(PrimitiveData tag){
        writeFields(tag,texture,faceTexture,offFaceTexture,configuredLevel,signalBrightness,offset,joinedLevel,manualOn,join,trigger,joinedPower);
    }
    /** Preserve legacy live values while saving; normalization belongs to the read/edit boundary. */
    public static void writeFields(PrimitiveData tag,int texture,int faceTexture,int offFaceTexture,int configuredLevel,boolean signalBrightness,int offset,int joinedLevel,boolean manualOn,boolean join,int trigger,boolean joinedPower){
        tag.putInt("LightTexture",texture);tag.putInt("LightFaceTexture",faceTexture);tag.putInt("LightOffFaceTexture",offFaceTexture);tag.putInt("LightLevel",configuredLevel);tag.putBoolean("SignalBrightness",signalBrightness);tag.putInt("LightOffset",offset);tag.putInt("JoinedSignalLevel",joinedLevel);tag.putBoolean("LightOn",manualOn);tag.putBoolean("LightJoin",join);tag.putInt("LightTrigger",trigger);tag.putBoolean("LightGroupPowered",joinedPower);
    }
}
