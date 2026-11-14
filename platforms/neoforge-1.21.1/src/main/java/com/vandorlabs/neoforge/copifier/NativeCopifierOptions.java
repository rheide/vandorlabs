package com.vandorlabs.neoforge.copifier;

import com.vandorlabs.items.DuplifierOptionCatalog;
import com.vandorlabs.items.DuplifierOptionMask;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Inactive native item-data adapter; the Copifier item, menu and gameplay route remain separate. */
public final class NativeCopifierOptions {
    public static final String MASK="DuplifierApplyMask",WORDS=MASK+"Words",COUNT="DuplifierApplyOptionCount";
    public static final String CONNECTED="DuplifierConnected",IGNORE_DIRECTION="DuplifierIgnoreDirectionality",RESPECT_JOINS="DuplifierRespectJoins";
    private static final DuplifierOptionMask POLICY=new DuplifierOptionMask(DuplifierOptionCatalog.size());
    public static long[] maskWords(CompoundTag root){
        if(root==null)return POLICY.legacyWords(POLICY.lowMask());
        return POLICY.read(root.contains(MASK,Tag.TAG_LONG),root.getLong(MASK),
                root.contains(COUNT,Tag.TAG_INT)?root.getInt(COUNT):56,
                root.contains(WORDS,Tag.TAG_INT_ARRAY)?root.getIntArray(WORDS):null);
    }
    public static CompoundTag withMask(CompoundTag original,long[] words){
        int[] stored=POLICY.encode(words);CompoundTag root=copy(original);
        root.putLong(MASK,words[0]);root.putIntArray(WORDS,stored);root.putInt(COUNT,DuplifierOptionCatalog.size());return root;
    }
    public static CompoundTag withMask(CompoundTag original,long low){long[] words=maskWords(original);words[0]=low&POLICY.lowMask();return withMask(original,words);}
    public static boolean connected(CompoundTag root){return root!=null&&root.getBoolean(CONNECTED);}
    public static boolean respectJoins(CompoundTag root){return root!=null&&root.getBoolean(RESPECT_JOINS);}
    public static CompoundTag withRespectJoins(CompoundTag original,boolean value){CompoundTag root=copy(original);root.putBoolean(RESPECT_JOINS,value);return root;}
    public static boolean ignoreDirectionality(CompoundTag root){return root!=null&&root.getBoolean(IGNORE_DIRECTION);}
    public static CompoundTag withConnected(CompoundTag original,boolean value){CompoundTag root=copy(original);root.putBoolean(CONNECTED,value);return root;}
    public static CompoundTag withIgnoreDirectionality(CompoundTag original,boolean value){CompoundTag root=copy(original);root.putBoolean(IGNORE_DIRECTION,value);return root;}
    public static CompoundTag selected(CompoundTag captured,long[] words){
        if(captured==null)return null;
        CompoundTag selected=captured.copy();
        com.vandorlabs.items.DuplifierSettingSelection.prune(words,selected::contains,selected::remove);
        return selected;
    }
    private static CompoundTag copy(CompoundTag original){return original==null?new CompoundTag():original.copy();}
    private NativeCopifierOptions(){}
}
