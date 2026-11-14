package com.vandorlabs.items;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Per-item choices for which copied settings may be applied. */
public final class DuplifierApplyOptions {
    public static final String TAG = "DuplifierApplyMask";
    private static final String COUNT_TAG = "DuplifierApplyOptionCount";
    public static final String CONNECTED_TAG = "DuplifierConnected";
    public static final String RESPECT_JOINS_TAG = "DuplifierRespectJoins";
    public static final String IGNORE_DIRECTION_TAG = "DuplifierIgnoreDirectionality";
    public static final String[] PAGES = DuplifierOptionCatalog.pages();

    public static final class Option {
        public final String key;
        public final String label;
        public final int page;

        private Option(String key, String label, int page) {
            this.key = key;
            this.label = label;
            this.page = page;
        }
    }

    private static Option[] options(){
        DuplifierOptionCatalog.Option[] catalog=DuplifierOptionCatalog.options();
        Option[] options=new Option[catalog.length];
        for(int i=0;i<catalog.length;i++)options[i]=new Option(catalog[i].key,catalog[i].label,catalog[i].page);
        return options;
    }
    public static final Option[] OPTIONS=options();

    /** Legacy API covers the original first word; option indices are append-only. */
    private static final DuplifierOptionMask MASK = new DuplifierOptionMask(OPTIONS.length);
    public static final long ALL = MASK.lowMask();
    public static final String WORDS_TAG=TAG+"Words";

    private DuplifierApplyOptions() { }

    public static boolean connected(ItemStack tool) {
        NBTTagCompound root = tool.getTagCompound();
        return root != null && root.getBoolean(CONNECTED_TAG);
    }

    public static void setConnected(ItemStack tool, boolean connected) {
        tool.setTagInfo(CONNECTED_TAG, new net.minecraft.nbt.NBTTagByte((byte)(connected ? 1 : 0)));
    }

    public static boolean respectJoins(ItemStack tool){NBTTagCompound root=tool.getTagCompound();return root!=null&&root.getBoolean(RESPECT_JOINS_TAG);}
    public static void setRespectJoins(ItemStack tool,boolean respect){tool.setTagInfo(RESPECT_JOINS_TAG,new net.minecraft.nbt.NBTTagByte((byte)(respect?1:0)));}

    public static boolean ignoreDirectionality(ItemStack tool) {
        NBTTagCompound root=tool.getTagCompound();
        return root!=null && root.getBoolean(IGNORE_DIRECTION_TAG);
    }

    public static void setIgnoreDirectionality(ItemStack tool,boolean ignore) {
        tool.setTagInfo(IGNORE_DIRECTION_TAG,new net.minecraft.nbt.NBTTagByte((byte)(ignore?1:0)));
    }

    public static long[] legacyWords(long low){return MASK.legacyWords(low);}
    public static boolean valid(long[] words){return MASK.valid(words);}
    /** Original long and option count remain readable; appended options begin enabled. */
    public static long[] maskWords(ItemStack tool){
        NBTTagCompound root=tool.getTagCompound();
        if(root==null)return MASK.legacyWords(ALL);
        return MASK.read(root.hasKey(TAG,4),root.getLong(TAG),
                root.hasKey(COUNT_TAG,3)?root.getInteger(COUNT_TAG):56,
                root.hasKey(WORDS_TAG,11)?root.getIntArray(WORDS_TAG):null);
    }
    public static long mask(ItemStack tool){return maskWords(tool)[0];}
    /** Updating an old first-word caller preserves all newer choices. */
    public static void setMask(ItemStack tool,long value){long[] words=maskWords(tool);words[0]=value & ALL;setMask(tool,words);}
    public static void setMask(ItemStack tool,long[] value){
        int[] stored=MASK.encode(value);
        NBTTagCompound root=tool.getTagCompound();if(root==null)root=new NBTTagCompound();
        root.setLong(TAG,value[0]);root.setIntArray(WORDS_TAG,stored);root.setInteger(COUNT_TAG,OPTIONS.length);tool.setTagCompound(root);
    }
    public static boolean enabled(long mask,int index){return MASK.enabled(mask,index);}
    public static boolean enabled(long[] words,int index){return MASK.enabled(words,index);}
    public static NBTTagCompound selected(NBTTagCompound captured,long mask){return selected(captured,legacyWords(mask));}

    public static NBTTagCompound selected(NBTTagCompound captured, long[] mask) {
        if (captured == null) return null;
        NBTTagCompound selected = captured.copy();
        DuplifierSettingSelection.prune(mask,selected::hasKey,selected::removeTag);
        return selected;
    }
}
