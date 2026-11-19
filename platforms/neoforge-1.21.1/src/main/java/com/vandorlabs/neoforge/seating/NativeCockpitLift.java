package com.vandorlabs.neoforge.seating;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** The server owns the original reversible, thirty-tick, 12-pixel lift timeline. */
public record NativeCockpitLift(boolean retracted, double from, long started, long revision) {
    public static final String KEY="CockpitScreenLift";
    public static final int DURATION=30;
    public static final double TRAVEL=.75;
    static final NativeCockpitLift DEPLOYED=new NativeCockpitLift(false,0,0,0);

    public double at(double now){
        if(now<started)return started-now>DURATION*2?(retracted?1:0):from;
        return interpolate(from,retracted,(now-started)/DURATION);
    }

    public static double interpolate(double from,boolean goal,double fraction){
        double t=Math.max(0,Math.min(1,fraction));
        return from+((goal?1:0)-from)*t*t*(3-2*t);
    }

    NativeCockpitLift toggle(long now){
        return new NativeCockpitLift(!retracted,at(now),now,revision==Long.MAX_VALUE?revision:revision+1);
    }

    static NativeCockpitLift read(CompoundTag root){
        if(!root.contains(KEY))return DEPLOYED;
        if(!root.contains(KEY,Tag.TAG_COMPOUND))throw new IllegalArgumentException("Malformed Cockpit lift");
        var tag=root.getCompound(KEY);
        if(!tag.contains("Target",Tag.TAG_BYTE)||!tag.contains("From",Tag.TAG_DOUBLE)||!tag.contains("Started",Tag.TAG_LONG)
            ||tag.contains("Revision")&&!tag.contains("Revision",Tag.TAG_LONG))throw new IllegalArgumentException("Malformed Cockpit lift fields");
        double from=tag.getDouble("From");
        long started=tag.getLong("Started"),revision=tag.getLong("Revision");
        if(!Double.isFinite(from)||from<0||from>1||started<0||revision<0)throw new IllegalArgumentException("Invalid Cockpit lift timeline");
        return new NativeCockpitLift(tag.getBoolean("Target"),from,started,revision);
    }

    void write(CompoundTag root){
        var tag=root.getCompound(KEY).copy();
        tag.putBoolean("Target",retracted);
        tag.putDouble("From",from);
        tag.putLong("Started",started);
        tag.putLong("Revision",revision);
        root.put(KEY,tag);
    }
}
