package com.vandorlabs.persistence;

import net.minecraft.nbt.NBTTagCompound;

/** Forge adapter preserving exact typed-key semantics of the legacy trigger codec. */
public final class NbtTriggerData implements TriggerState.Tag {
    private final NBTTagCompound tag;
    public NbtTriggerData(NBTTagCompound tag){this.tag=tag;}
    @Override public boolean containsInt(String key){return tag.hasKey(key,3);}
    @Override public int getInt(String key){return tag.getInteger(key);}
    @Override public boolean getBoolean(String key){return tag.getBoolean(key);}
    @Override public int[] getIntArray(String key){return tag.getIntArray(key);}
    @Override public void putInt(String key,int value){tag.setInteger(key,value);}
    @Override public void putBoolean(String key,boolean value){tag.setBoolean(key,value);}
    @Override public void putIntArray(String key,int[] value){tag.setIntArray(key,value);}
    @Override public void remove(String key){tag.removeTag(key);}
}
