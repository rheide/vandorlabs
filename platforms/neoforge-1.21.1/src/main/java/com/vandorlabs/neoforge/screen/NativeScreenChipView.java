package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.credentials.NativeTokenReader;
import com.vandorlabs.programmer.JournalText;
import java.util.Set;
import net.minecraft.nbt.*;

/** Bounded display-only projection. No inventory stack, capability envelope or return-player identity. */
public final class NativeScreenChipView {
    private static final Set<String> KEYS=Set.of("Company","Logo","Icon","Occupied","Output","Sequence","Age","RejectedFor","RejectedUntil","Page","Journal","Master","DisplayName");
    public final NativeTokenReader.Visual reader;
    public final NativeTokenReader.Motion motion;
    public final JournalText journal;
    public final int page;
    public final boolean master;
    public final String displayName;
    public static final int NAME_LIMIT=256;
    private final CompoundTag text;
    private NativeScreenChipView(NativeTokenReader.Visual reader,NativeTokenReader.Motion motion,CompoundTag text,int page,boolean master,String displayName){this.master=master;this.displayName=displayName;this.reader=reader;this.motion=motion;this.text=text==null?null:text.copy();journal=NativeChipJournal.viewText(this.text);this.page=journal==null?0:Math.max(0,Math.min(journal.pages.size()-1,page));}
    public static NativeScreenChipView of(NativeEmbeddedChipReader owner,long now,int page){return new NativeScreenChipView(owner.visual(now),owner.motion(now),owner.journalData(),page,owner.master(),owner.displayName());}
    public CompoundTag write(){
        var tag=new CompoundTag();tag.putInt("Company",reader.company());tag.putInt("Logo",reader.logo());tag.putBoolean("Icon",reader.icon());tag.putBoolean("Occupied",reader.occupied());tag.putInt("Output",reader.output());tag.putLong("Sequence",motion.sequence());tag.putInt("Age",motion.age());tag.putInt("RejectedFor",motion.rejectedFor());tag.putLong("RejectedUntil",motion.rejectionDeadline());tag.putInt("Page",page);tag.putBoolean("Master",master);if(displayName!=null)tag.putString("DisplayName",displayName);if(text!=null)tag.put("Journal",text.copy());return tag;
    }
    private static boolean flag(CompoundTag tag,String key){if(!tag.contains(key,Tag.TAG_BYTE)||tag.getByte(key)<0||tag.getByte(key)>1)throw new IllegalArgumentException("Chip view flag");return tag.getBoolean(key);}
    public static NativeScreenChipView read(CompoundTag tag){
        for(String key:tag.getAllKeys())if(!KEYS.contains(key))throw new IllegalArgumentException("Unknown chip view field");
        for(String key:new String[]{"Company","Logo","Output","Age","RejectedFor","Page"})if(!tag.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("Chip view integer");for(String key:new String[]{"Sequence","RejectedUntil"})if(!tag.contains(key,Tag.TAG_LONG))throw new IllegalArgumentException("Chip view timeline");
        boolean icon=flag(tag,"Icon"),occupied=flag(tag,"Occupied"),master=flag(tag,"Master");if(tag.contains("DisplayName")&&(!tag.contains("DisplayName",Tag.TAG_STRING)||tag.getString("DisplayName").length()>NAME_LIMIT||!occupied)||master&&(!occupied||tag.getInt("Company")==0))throw new IllegalArgumentException("Chip display identity");if(icon&&!occupied)throw new IllegalArgumentException("Unoccupied chip icon");var reader=new NativeTokenReader.Visual(true,tag.getInt("Company"),tag.getInt("Logo"),icon,tag.getInt("Output"),occupied);var motion=new NativeTokenReader.Motion(tag.getLong("Sequence"),tag.getInt("Age"),false,tag.getInt("RejectedFor"),tag.getLong("RejectedUntil"));
        CompoundTag text=null;if(tag.contains("Journal")){
            if(!occupied||!tag.contains("Journal",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Chip journal envelope");text=tag.getCompound("Journal");if(!text.getAllKeys().equals(Set.of("ChipType","JournalTitle","JournalPages"))||!text.contains("ChipType",Tag.TAG_STRING)||!text.getString("ChipType").equals("journal")||!text.contains("JournalTitle",Tag.TAG_STRING)||text.getString("JournalTitle").length()>JournalText.TITLE_LIMIT||!text.contains("JournalPages",Tag.TAG_LIST))throw new IllegalArgumentException("Chip journal fields");
            ListTag pages=(ListTag)text.get("JournalPages");if(pages.size()>JournalText.MAX_ENTRIES||!pages.isEmpty()&&pages.getElementType()!=Tag.TAG_STRING)throw new IllegalArgumentException("Chip journal entry count/type");for(int i=0;i<pages.size();i++)if(pages.getString(i).length()>JournalText.PAGE_LIMIT)throw new IllegalArgumentException("Chip journal entry length");
        }
        if(master&&text!=null)throw new IllegalArgumentException("Journal cannot be a master chip");
        int page=tag.getInt("Page");if(page<0)throw new IllegalArgumentException("Chip journal page");var result=new NativeScreenChipView(reader,motion,text,page,master,tag.contains("DisplayName",Tag.TAG_STRING)?tag.getString("DisplayName"):null);if(result.page!=page)throw new IllegalArgumentException("Chip journal page bounds");return result;
    }
}
