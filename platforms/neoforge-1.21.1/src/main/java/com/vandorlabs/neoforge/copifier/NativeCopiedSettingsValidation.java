package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.items.CopiedSettingsValidation;
import net.minecraft.nbt.CompoundTag;

/** Native exact tag-type access for the shared pre-mutation contract. */
public final class NativeCopiedSettingsValidation {
    private static final CopiedSettingsValidation.Access<CompoundTag> TAGS=new CopiedSettingsValidation.Access<>(){
        public boolean contains(CompoundTag tag,String key){return tag.contains(key);}
        public boolean type(CompoundTag tag,String key,int type){return tag.contains(key,type);}
        public int integer(CompoundTag tag,String key){return tag.getInt(key);}
        public byte numberByte(CompoundTag tag,String key){return tag.getByte(key);}
        public int[] integers(CompoundTag tag,String key){return tag.getIntArray(key);}
        public CompoundTag compound(CompoundTag tag,String key){return tag.getCompound(key);}
    };
    public static boolean valid(CompoundTag values){return CopiedSettingsValidation.valid(values,TAGS);}
    private NativeCopiedSettingsValidation(){}
}
