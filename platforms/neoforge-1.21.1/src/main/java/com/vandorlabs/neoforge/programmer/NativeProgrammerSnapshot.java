package com.vandorlabs.neoforge.programmer;

import com.vandorlabs.neoforge.NativePlatformComponents;
import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.inventory.NativeInventoryItems;
import com.vandorlabs.neoforge.screen.NativeChipJournal;
import com.vandorlabs.programmer.ProgrammerStatus;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** All-or-nothing local saved-state staging. No world access, platform installation or slot publication. */
public final class NativeProgrammerSnapshot {
 public static final String VERSION="NativeProgrammerVersion";
 private final CompoundTag original;
 private final ItemStack[] items;
 private final NativeProgrammerDraft draft;
 private final int writing,complete;
 private NativeProgrammerSnapshot(CompoundTag original,ItemStack[] items,NativeProgrammerDraft draft,int writing,int complete){this.original=original;this.items=items;this.draft=draft;this.writing=writing;this.complete=complete;}
 public static NativeProgrammerSnapshot prepare(CompoundTag raw,HolderLookup.Provider registries){
  var original=raw.copy();NativePlatformComponents.requireSupported(original,registries);
  if(original.getBoolean("ProgrammerProjection")||original.contains("ProgrammerPlacementRecovery"))throw new IllegalArgumentException("Public/recovery programmer data cannot install physical inventory");
  if(original.contains("ForgeCaps")&&(!(original.get("ForgeCaps") instanceof CompoundTag caps)||!caps.isEmpty()))throw new IllegalArgumentException("Unsupported legacy programmer capability owner");
  boolean modern=original.contains(VERSION);if(modern&&(!original.contains(VERSION,Tag.TAG_INT)||original.getInt(VERSION)!=1))throw new IllegalArgumentException("Unsupported programmer inventory version");
  var staged=new ItemStack[]{ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};var seen=new boolean[3];var budget=new NativeInventoryItems.Budget();
  if(original.contains("Items")){if(!(original.get("Items") instanceof ListTag entries)||!entries.isEmpty()&&entries.getElementType()!=Tag.TAG_COMPOUND||entries.size()>3)throw new IllegalArgumentException("Unsupported programmer inventory envelope");
   for(Tag value:entries){var entry=(CompoundTag)value;if(!entry.contains("Slot",Tag.TAG_BYTE))throw new IllegalArgumentException("Unsupported programmer slot type");int slot=entry.getByte("Slot")&255;if(slot>=3||seen[slot])throw new IllegalArgumentException("Ambiguous programmer inventory slot");seen[slot]=true;ItemStack item;if(modern){var data=entry.copy();data.remove("Slot");item=NativeInventoryItems.modern(data,registries,budget);}else item=NativeInventoryItems.legacy(entry,registries,budget);
    if(!item.isEmpty()&&(item.getCount()!=1||!NativeCredentialItems.accepts(item,false)&&!NativeCredentialItems.accepts(item,true)||NativeChipJournal.vehicle(item)))throw new IllegalArgumentException("Unsupported programmer credential owner");staged[slot]=NativeCredentialItems.ownedCopy(item);
   }
  }
  var draft=NativeProgrammerDraft.read(original,staged[1].is(NativeCredentialItems.DATA_CHIP.get()));if(draft.vehicle()||original.getBoolean("Vehicle"))throw new IllegalArgumentException("Vehicle programmer state is unavailable");
  return new NativeProgrammerSnapshot(original,staged,draft,Math.max(0,Math.min(20,original.getInt("Writing"))),Math.max(0,Math.min(60,original.getInt("Complete"))));
 }
 public ItemStack item(int slot){if(slot<0||slot>=3)throw new IndexOutOfBoundsException();return NativeCredentialItems.ownedCopy(items[slot]);}
 public NativeProgrammerDraft draft(){return draft;}
 public int writing(){return writing;}
 public int complete(){return complete;}
 public ProgrammerStatus status(){return writing>0?ProgrammerStatus.WRITING:complete>0?ProgrammerStatus.COMPLETE:ProgrammerStatus.READY;}
 public CompoundTag original(){return original.copy();}
 /** Caller owns live platform serialization; this replaces only the known programmer fields. */
 public void writeKnownInto(CompoundTag destination,HolderLookup.Provider registries){var entries=new ListTag();for(int slot=0;slot<3;slot++)if(!items[slot].isEmpty()){var entry=NativeInventoryItems.write(items[slot],registries);entry.putByte("Slot",(byte)slot);entries.add(entry);}destination.putInt(VERSION,1);destination.put("Items",entries);draft.write(destination);destination.putInt("Writing",writing);destination.putInt("Complete",complete);destination.remove("JournalDisplayPage");}
}
