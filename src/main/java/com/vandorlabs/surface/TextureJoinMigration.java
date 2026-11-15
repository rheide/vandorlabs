package com.vandorlabs.surface;

import com.vandorlabs.persistence.NbtPrimitiveData;
import java.util.Objects;
import net.minecraft.nbt.NBTTagCompound;

/** Retired own policy values compare as All without rewriting immutable snapshots or provider data. */
public final class TextureJoinMigration {
    private static boolean legacy(NBTTagCompound tag){
        if(tag==null||!tag.hasKey(TextureJoinSettings.PARITY_TAG,3))return false;
        int value=tag.getInteger(TextureJoinSettings.PARITY_TAG);if(value!=1&&value!=2)return false;
        return TextureJoinSettings.needsLegacyNormalization(new NbtPrimitiveData(tag));
    }
    /** The caller owns this installation copy; original journal/snapshot tags are never passed here. */
    public static void normalizeOwnedCopy(NBTTagCompound copy){if(legacy(copy))copy.setInteger(TextureJoinSettings.PARITY_TAG,0);}
    public static boolean same(NBTTagCompound before,NBTTagCompound after){
        if(Objects.equals(before,after))return true;
        if(before==null||after==null||!legacy(before)&&!legacy(after))return false;
        if(!before.hasKey(TextureJoinSettings.PARITY_TAG,3)||!after.hasKey(TextureJoinSettings.PARITY_TAG,3))return false;
        int a=before.getInteger(TextureJoinSettings.PARITY_TAG),b=after.getInteger(TextureJoinSettings.PARITY_TAG);
        if(a<0||a>2||b<0||b>2||!before.getKeySet().equals(after.getKeySet()))return false;
        for(String key:before.getKeySet())if(!key.equals(TextureJoinSettings.PARITY_TAG)&&!Objects.equals(before.getTag(key),after.getTag(key)))return false;
        return true;
    }
    private TextureJoinMigration(){}
}
