package com.vandorlabs.neoforge.copifier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Inactive tool item-data adapter; all operations preserve unrelated item fields. */
public final class NativeCopifierClipboard {
    public static final String SETTINGS="DuplifierSettings",SOURCE="DuplifierSource";
    public static boolean hasCopy(CompoundTag root){return root!=null&&root.contains(SETTINGS,Tag.TAG_COMPOUND)&&!root.getCompound(SETTINGS).isEmpty();}
    public static CompoundTag settings(CompoundTag root){return root!=null&&root.contains(SETTINGS,Tag.TAG_COMPOUND)?root.getCompound(SETTINGS).copy():null;}
    public static String suffix(CompoundTag root){if(root==null||!root.contains(SOURCE,Tag.TAG_STRING)||root.getString(SOURCE).isEmpty())return "";return " - "+root.getString(SOURCE);}
    public static CompoundTag clear(CompoundTag original){if(original==null)return null;var root=original.copy();root.remove(SETTINGS);root.remove(SOURCE);return root.isEmpty()?null:root;}
    /** A failed/empty capture leaves the prior clipboard and every option untouched. */
    public static CompoundTag copied(CompoundTag original,CompoundTag captured,String source){
        if(captured==null||captured.isEmpty())return original==null?null:original.copy();
        var root=original==null?new CompoundTag():original.copy();root.put(SETTINGS,captured.copy());root.putString(SOURCE,java.util.Objects.requireNonNull(source));return root;
    }
    private NativeCopifierClipboard(){}
}
