package com.vandorlabs.persistence;

import com.vandorlabs.propulsion.RedstoneLightPolicy;
import java.util.function.IntUnaryOperator;

/** Normalized saved fields for placed redstone lamps/engines; no programmable-Light trigger or offset policy. */
public final class RedstoneLightData {
    public final int channel,channelLevel,particleLevel,particleThreshold,sideTexture;
    public final boolean channelSignal,manualOn,initialized,inverted,analog,join;
    public RedstoneLightData(int channel,boolean channelSignal,int channelLevel,boolean manualOn,int particleLevel,boolean initialized,boolean inverted,boolean analog,int particleThreshold,boolean join,int sideTexture){
        if(channel<0||channelLevel<0||channelLevel>15||particleLevel<0||particleLevel>3||particleThreshold<0||particleThreshold>15||sideTexture<0)throw new IllegalArgumentException("Invalid redstone-light fields");
        this.channel=channel;this.channelSignal=channelSignal;this.channelLevel=channelLevel;this.manualOn=manualOn;this.particleLevel=particleLevel;this.initialized=initialized;this.inverted=inverted;this.analog=analog;this.particleThreshold=particleThreshold;this.join=join;this.sideTexture=sideTexture;
    }
    public static RedstoneLightData read(HousingTags tag,int primary,IntUnaryOperator material){
        RedstoneData.Light base=RedstoneData.Light.read(tag);
        return new RedstoneLightData(primary,base.signal,tag.containsInt("ChannelLevel")?clamp(tag.getInt("ChannelLevel"),0,15):base.signal?15:0,base.manualOn,tag.containsInt("ParticleLevel")?clamp(tag.getInt("ParticleLevel"),0,3):base.particleStream?1:0,base.initialized,tag.getBoolean("RedstoneInverted"),tag.getBoolean("SignalBrightness"),tag.containsInt("ParticleThreshold")?clamp(tag.getInt("ParticleThreshold"),0,15):8,!tag.contains("PropulsionJoin")||tag.getBoolean("PropulsionJoin"),tag.containsInt("PropulsionSideTexture")?material.applyAsInt(tag.getInt("PropulsionSideTexture")):9);
    }
    /** Local block state is authoritative only for old snapshots lacking a completed initial capture. */
    public RedstoneLightData capture(boolean blockPowered){return initialized?this:new RedstoneLightData(channel,channelSignal,channelLevel,blockPowered,particleLevel,true,inverted,analog,particleThreshold,join,sideTexture);}
    public int activation(int physical){return RedstoneLightPolicy.activation(channelLevel,physical,inverted);}
    public int brightness(int physical){return RedstoneLightPolicy.brightness(analog,activation(physical));}
    public boolean powered(int physical){return RedstoneLightPolicy.powered(analog,activation(physical),channel,physical>0,channelSignal,inverted,manualOn);}
    public boolean particles(int physical){return RedstoneLightPolicy.particles(particleLevel,analog,activation(physical),particleThreshold);}
    public int manualMode(){return RedstoneLightPolicy.manualMode(manualOn,particleLevel);}
    public void write(PrimitiveData tag){new RedstoneData.Light(channel,channelSignal,manualOn,particleLevel>0,initialized).write(tag);tag.putInt("ChannelLevel",channelLevel);tag.putInt("ParticleLevel",particleLevel);tag.putBoolean("SignalBrightness",analog);tag.putBoolean("RedstoneInverted",inverted);tag.putInt("ParticleThreshold",particleThreshold);tag.putBoolean("PropulsionJoin",join);tag.putInt("PropulsionSideTexture",sideTexture);}
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    @Override public boolean equals(Object value){if(!(value instanceof RedstoneLightData))return false;RedstoneLightData d=(RedstoneLightData)value;return channel==d.channel&&channelSignal==d.channelSignal&&channelLevel==d.channelLevel&&manualOn==d.manualOn&&particleLevel==d.particleLevel&&initialized==d.initialized&&inverted==d.inverted&&analog==d.analog&&particleThreshold==d.particleThreshold&&join==d.join&&sideTexture==d.sideTexture;}
    @Override public int hashCode(){int result=channel;result=31*result+channelLevel;result=31*result+particleLevel;result=31*result+particleThreshold;result=31*result+sideTexture;return 31*result+(channelSignal?1:0)+(manualOn?2:0)+(initialized?4:0)+(inverted?8:0)+(analog?16:0)+(join?32:0);}
}
