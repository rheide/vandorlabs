package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.HousingTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Native NBT adapter, retaining the legacy tag types and spellings. */
public final class ModernHousingTags implements com.vandorlabs.screen.ScreenArtworkMigration.Tag,HousingTags,com.vandorlabs.persistence.FrameSettings.Tag,com.vandorlabs.persistence.TriggerState.Tag,com.vandorlabs.persistence.ControlLatchData.Tag {
    private final CompoundTag tag;
    public ModernHousingTags(CompoundTag tag){this.tag=java.util.Objects.requireNonNull(tag);}
    public void remove(String key){tag.remove(key);}
    public boolean contains(String key){return tag.contains(key);}
    public boolean containsString(String key){return tag.contains(key,Tag.TAG_STRING);}
    public boolean containsInt(String key){return tag.contains(key,Tag.TAG_INT);}
    public boolean containsIntArray(String key){return tag.contains(key,Tag.TAG_INT_ARRAY);}
    public boolean containsBoolean(String key){return tag.contains(key,Tag.TAG_BYTE);}
    public String getString(String key){return tag.getString(key);}
    public int getInt(String key){return tag.getInt(key);}
    public int[] getIntArray(String key){return tag.getIntArray(key);}
    public long getLong(String key){return tag.getLong(key);}
    public double getDouble(String key){return tag.getDouble(key);}
    public boolean getBoolean(String key){return tag.getBoolean(key);}
    public void putString(String key,String value){tag.putString(key,value);}
    public void putInt(String key,int value){tag.putInt(key,value);}
    public void putIntArray(String key,int[] value){tag.putIntArray(key,value);}
    public void putLong(String key,long value){tag.putLong(key,value);}
    public void putDouble(String key,double value){tag.putDouble(key,value);}
    public void putBoolean(String key,boolean value){tag.putBoolean(key,value);}
}
