package com.vandorlabs.programmer;

import com.vandorlabs.items.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/** Bounded, plain-text journal payload. Company metadata stays compatible with existing readers. */
public final class ChipJournal {
    public static final int TITLE_LIMIT=40, PAGE_LIMIT=512, MAX_PAGES=8;
    private ChipJournal() { }
    public static boolean isChip(ItemStack stack){return !stack.isEmpty()&&stack.getItem()==ModItems.DATA_CHIP;}
    public static boolean journal(ItemStack stack){return isChip(stack)&&stack.hasTagCompound()&&"journal".equals(stack.getTagCompound().getString("ChipType"));}
    public static boolean master(ItemStack stack){return isChip(stack)&&!journal(stack)&&stack.getMetadata()>0&&stack.getMetadata()<7;}
    public static String title(ItemStack stack){return journal(stack)?bounded(stack.getTagCompound().getString("JournalTitle"),TITLE_LIMIT):"";}
    public static String page(ItemStack stack,int index){if(!journal(stack)||index<0||index>=MAX_PAGES)return "";NBTTagList pages=stack.getTagCompound().getTagList("JournalPages",8);return index<pages.tagCount()?bounded(pages.getStringTagAt(index),PAGE_LIMIT):"";}
    public static String bounded(String value,int max){return value==null?"":value.substring(0,Math.min(max,value.length())).replace("\u0000","");}
    public static void write(ItemStack stack,boolean journal,String title,String[] pages){
        NBTTagCompound tag=stack.hasTagCompound()?stack.getTagCompound():new NBTTagCompound();
        tag.setString("ChipType",journal?"journal":"master");tag.removeTag("JournalTitle");tag.removeTag("JournalPages");
        if(journal){tag.setString("JournalTitle",bounded(title,TITLE_LIMIT));NBTTagList list=new NBTTagList();for(int i=0;i<MAX_PAGES;i++)list.appendTag(new NBTTagString(bounded(i<pages.length?pages[i]:"",PAGE_LIMIT)));tag.setTag("JournalPages",list);}
        stack.setTagCompound(tag);
    }
}
