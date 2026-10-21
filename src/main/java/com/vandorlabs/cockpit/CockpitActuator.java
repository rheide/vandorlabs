package com.vandorlabs.cockpit;

import net.minecraft.nbt.NBTTagCompound;

/** Persisted reversible motion sampled from world time; clients need only target changes. */
public final class CockpitActuator {
    public static final int DURATION=30;
    private boolean target;
    private double from;
    private long started;
    public boolean target(){return target;}
    public double sample(double now){if(now<started)return target?1:0;double t=Math.max(0,Math.min(1,(now-started)/DURATION));t=t*t*(3-2*t);return from+((target?1:0)-from)*t;}
    public boolean moving(long now){return now>=started&&now<started+DURATION&&Math.abs(from-(target?1:0))>1e-8;}
    public void toggle(long now){from=sample(now);target=!target;started=now;}
    public void write(NBTTagCompound tag,String key){NBTTagCompound value=new NBTTagCompound();value.setBoolean("Target",target);value.setDouble("From",from);value.setLong("Started",started);tag.setTag(key,value);}
    public void read(NBTTagCompound tag,String key){NBTTagCompound value=tag.getCompoundTag(key);target=value.getBoolean("Target");double saved=value.getDouble("From");from=Double.isFinite(saved)?Math.max(0,Math.min(1,saved)):0;started=value.getLong("Started");}
}
