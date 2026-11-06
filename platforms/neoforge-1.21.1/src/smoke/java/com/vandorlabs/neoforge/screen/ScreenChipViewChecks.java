package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.component.CustomData;

/** Display text and stock icon data remain bounded independently of private inventory components. */
final class ScreenChipViewChecks {
    static void run(HolderLookup.Provider registries)throws Exception{
        var chip=NativeCredentialItems.dataChip(2);var privateData=new CompoundTag();privateData.putString("PrivateRoot","private-marker-"+"x".repeat(40000));chip.set(DataComponents.CUSTOM_DATA,CustomData.of(privateData));var entries=new String[8];java.util.Arrays.fill(entries,"\u0800".repeat(512));NativeChipJournal.write(chip,true,"\u0800".repeat(40),entries);var raw=new CompoundTag();raw.put("InsertedToken",NativeCredentialItems.writeStack(chip,registries));var owner=NativeEmbeddedChipReader.storage(raw,registries);var view=NativeScreenChipView.of(owner,0,999);var wire=view.write();var output=new java.io.ByteArrayOutputStream();NbtIo.write(wire,new java.io.DataOutputStream(output));if(output.size()>16384||wire.toString().contains("private-marker")||wire.contains("InsertedToken"))throw new AssertionError("Unbounded/private chip view");
        var restored=NativeScreenChipView.read(wire);if(!restored.reader.equals(view.reader)||!restored.motion.equals(view.motion)||!restored.journal.key.equals(view.journal.key)||restored.page!=view.page)throw new AssertionError("Chip view roundtrip");wire.getCompound("Journal").putString("JournalTitle","tampered");if(view.write().getCompound("Journal").getString("JournalTitle").equals("tampered"))throw new AssertionError("Mutable chip projection");
        int malformed=0;for(int failure=0;failure<14;failure++){
            var bad=view.write();switch(failure){case 0->bad.putString("FutureOwner","opaque");case 1->bad.putInt("Company",7);case 2->bad.putInt("Logo",7);case 3->bad.putByte("Icon",(byte)2);case 4->bad.putInt("Age",21);case 5->bad.putInt("RejectedFor",13);case 6->bad.putInt("Page",-1);case 7->bad.putInt("Sequence",0);case 8->bad.getCompound("Journal").putString("PrivateRoot","opaque");case 9->{var pages=new ListTag();for(int i=0;i<9;i++)pages.add(StringTag.valueOf("extra"));bad.getCompound("Journal").put("JournalPages",pages);}case 10->{var pages=new ListTag();pages.add(StringTag.valueOf("x".repeat(513)));bad.getCompound("Journal").put("JournalPages",pages);}case 11->bad.putInt("Journal",42);case 12->bad.putBoolean("Occupied",false);case 13->bad.putInt("Page",Integer.MAX_VALUE);}
            try{NativeScreenChipView.read(bad);throw new AssertionError("Malformed chip view accepted "+failure);}catch(IllegalArgumentException expected){malformed++;}
        }
        java.util.Arrays.fill(entries,"\n".repeat(512));NativeChipJournal.write(chip,true,"Pages",entries);raw.put("InsertedToken",NativeCredentialItems.writeStack(chip,registries));owner=NativeEmbeddedChipReader.storage(raw,registries);var many=NativeScreenChipView.of(owner,0,999);if(many.journal.pages.size()!=416||many.page!=415||NativeScreenChipView.read(many.write()).page!=415)throw new AssertionError("Maximum source pagination");
        System.out.println("[vandorlabs][screen-chip-view] PASS worstUtf16Bytes="+output.size()+" malformed="+malformed+" maxPages416 private40000charsExcluded/defensive DTO only; no entity binding");
    }
}
