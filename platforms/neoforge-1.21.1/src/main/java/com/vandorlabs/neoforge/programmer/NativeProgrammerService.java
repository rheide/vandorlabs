package com.vandorlabs.neoforge.programmer;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.screen.NativeChipJournal;
import com.vandorlabs.programmer.ProgrammerStatus;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Sole three-slot owner. Callers authorize access; callbacks see each complete committed change. */
public final class NativeProgrammerService {
 public interface Host {boolean current();boolean removing();void changed(boolean full);}
 private final Host host;
 private final ItemStack[] items={ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
 private NativeProgrammerDraft draft=NativeProgrammerDraft.empty();
 private int writing,complete;
 private boolean active;
 public NativeProgrammerService(Host host){this.host=Objects.requireNonNull(host);}
 private boolean begin(){if(active||!host.current())return false;active=true;return true;}
 private void changed(boolean full){host.changed(full);}
 private static boolean slot(int slot){return slot>=0&&slot<3;}
 private static boolean supported(ItemStack stack){return stack.isEmpty()||!NativeChipJournal.vehicle(stack)&&(NativeCredentialItems.accepts(stack,false)||NativeCredentialItems.accepts(stack,true));}
 public ItemStack item(int slot){return slot(slot)&&host.current()?items[slot]:ItemStack.EMPTY;}
 public boolean valid(int slot,ItemStack stack){return slot(slot)&&NativeProgrammerRules.validSlot(slot,stack);}
 public NativeProgrammerDraft draft(){return draft;}
 public int writing(){return writing;}
 public int complete(){return complete;}
 public ProgrammerStatus status(){return writing>0?ProgrammerStatus.WRITING:complete>0?ProgrammerStatus.COMPLETE:ProgrammerStatus.READY;}
 public String problem(){return NativeProgrammerRules.problem(items[0],items[1],items[2],draft);}
 public String copyProblem(){return NativeProgrammerRules.copyProblem(items[1],items[2]);}
 private void readDraft(){var text=NativeChipJournal.textData(items[1]);var pages=new ArrayList<String>(8);if(text!=null){var source=text.getList("JournalPages",Tag.TAG_STRING);for(int entry=0;entry<8;entry++)pages.add(entry<source.size()?source.getString(entry):"");}draft=new NativeProgrammerDraft(false,text!=null,text==null?"":text.getString("JournalTitle"),pages);}
 public boolean set(int slot,ItemStack stack){if(!slot(slot)||!supported(stack)||!begin())return false;try{items[slot]=stack;if(!stack.isEmpty()&&stack.getCount()>1)stack.setCount(1);if(slot==1)readDraft();complete=0;changed(true);return true;}finally{active=false;}}
 public ItemStack remove(int slot,int amount){if(!slot(slot)||amount<=0||!begin())return ItemStack.EMPTY;try{var result=items[slot].split(amount);if(!result.isEmpty()){if(items[slot].isEmpty())items[slot]=ItemStack.EMPTY;if(slot==1)readDraft();changed(true);}return result;}finally{active=false;}}
 public ItemStack removeWhole(int slot){if(!slot(slot)||!begin())return ItemStack.EMPTY;try{var result=items[slot];items[slot]=ItemStack.EMPTY;if(slot==1)readDraft();changed(true);return result;}finally{active=false;}}
 public void clear(){if(!begin())return;try{Arrays.fill(items,ItemStack.EMPTY);readDraft();changed(true);}finally{active=false;}}
 /** Exchange the hand before publication, so a removal observer cannot find a transient unowned token. */
 public boolean socket(int slot,ItemStack held,Consumer<ItemStack> replaceHand){if(!slot(slot)||!begin())return false;try{if(held.isEmpty()){if(!items[slot].isEmpty()){var result=items[slot];items[slot]=ItemStack.EMPTY;if(slot==1)readDraft();replaceHand.accept(result);changed(true);}}else if(items[slot].isEmpty()&&valid(slot,held)){items[slot]=held.split(1);if(slot==1)readDraft();complete=0;changed(true);}return true;}finally{active=false;}}
 public boolean configure(NativeProgrammerDraft next){if(next.vehicle()||!begin())return false;try{if(!items[1].is(NativeCredentialItems.DATA_CHIP.get()))return false;if(!draft.equals(next)){draft=next;changed(true);}return true;}finally{active=false;}}
 public boolean program(){if(!begin())return false;try{if(!problem().isEmpty())return false;var result=NativeProgrammerRules.programmedCopy(items[0],items[1],draft);items[1]=ItemStack.EMPTY;items[2]=result;readDraft();writing=20;complete=0;changed(true);return true;}finally{active=false;}}
 public boolean copy(){if(!begin())return false;try{if(!copyProblem().isEmpty())return false;items[2]=NativeProgrammerRules.committedCopy(items[1],items[2]);writing=20;complete=0;changed(true);return true;}finally{active=false;}}
 public void tick(){if(!begin())return;try{if(writing>0&&--writing==0){complete=60;changed(false);}else if(complete>0&&--complete==0)changed(false);}finally{active=false;}}
 /** Called only after every saved field and actual platform installation succeeded. */
 void install(NativeProgrammerSnapshot snapshot){for(int slot=0;slot<3;slot++)items[slot]=snapshot.item(slot);draft=snapshot.draft();writing=snapshot.writing();complete=snapshot.complete();}
 void reset(){Arrays.fill(items,ItemStack.EMPTY);draft=NativeProgrammerDraft.empty();writing=complete=0;}
 void writeKnownInto(CompoundTag tag,HolderLookup.Provider registries){NativeProgrammerSnapshot.writeKnownInto(tag,registries,items,draft,writing,complete);}
 ItemStack localItem(int slot){return items[slot];}
 /** Removal is an owned terminal operation and remains legal during a publication callback. */
 public List<ItemStack> takeForDrop(){boolean terminal=host.removing();if(!terminal&&(active||!host.current()))return List.of();var result=new ArrayList<ItemStack>(3);for(int slot=0;slot<3;slot++)if(!items[slot].isEmpty()){result.add(items[slot]);items[slot]=ItemStack.EMPTY;}if(result.isEmpty())return List.of();readDraft();if(!terminal){boolean previous=active;active=true;try{changed(true);}finally{active=previous;}}return List.copyOf(result);}
}
