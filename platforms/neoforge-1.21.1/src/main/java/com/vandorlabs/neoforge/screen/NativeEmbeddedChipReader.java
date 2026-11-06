package com.vandorlabs.neoforge.screen;

import com.vandorlabs.neoforge.credentials.NativeCredentialItems;
import com.vandorlabs.neoforge.credentials.NativeTokenReader;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;

/** Embedded Screen readers reuse credential ownership while forcing the released passive chip policy. */
public final class NativeEmbeddedChipReader {
    private static final Set<String> KEYS=Set.of("AnyToken","AllowedTokens","ChipSlot","TokenLogo","TokenBehavior","TokenPulseUntil","TokenInsertStarted","TokenRejectedUntil","TokenInsertionSequence","TokenToggleApplied","TokenToggled","TokenReturnToMost","TokenReturnToLeast","TokenReturnTo","InsertedToken");
    private final NativeTokenReader reader;
    private com.vandorlabs.programmer.JournalText journal;
    private CompoundTag journalData;
    private static final NativeTokenReader.Host STORAGE=new NativeTokenReader.Host(){
        public net.minecraft.world.level.block.entity.BlockEntity owner(){throw new IllegalStateException("Detached chip storage has no gameplay owner");}
        public boolean usable(net.minecraft.server.level.ServerPlayer player){return false;}
        public void changed(){throw new IllegalStateException("Detached chip storage cannot publish gameplay");}
        public void schedule(int ticks){throw new IllegalStateException("Detached chip storage cannot schedule gameplay");}
    };
    public NativeEmbeddedChipReader(NativeTokenReader.Host host){reader=new NativeTokenReader(host,true);}
    public static NativeEmbeddedChipReader storage(CompoundTag raw,HolderLookup.Provider registries){var result=new NativeEmbeddedChipReader(STORAGE);result.read(raw,registries);return result;}
    /** Validate before touching live state; the owning Screen retains its complete snapshot on refusal. */
    public void read(CompoundTag raw,HolderLookup.Provider registries){
        for(String key:raw.getAllKeys())if(!KEYS.contains(key))throw new IllegalArgumentException("Unsupported embedded chip reader field: "+key);
        var token=raw.contains("InsertedToken",Tag.TAG_COMPOUND)?NativeCredentialItems.readStack(raw.getCompound("InsertedToken"),registries):net.minecraft.world.item.ItemStack.EMPTY;
        if(raw.contains("InsertedToken")&&token.isEmpty())throw new IllegalArgumentException("Unresolved embedded chip ownership");
        if(!token.isEmpty())NativeCredentialItems.writeStack(token.copyWithCount(1),registries);
        var nextText=NativeChipJournal.textData(token);var nextJournal=NativeChipJournal.viewText(nextText);
        var tag=raw.copy();tag.putBoolean("ChipSlot",true);tag.putBoolean("AnyToken",true);tag.putInt("AllowedTokens",127);tag.putInt("TokenBehavior",0);tag.remove("TokenReturnToMost");tag.remove("TokenReturnToLeast");tag.remove("TokenReturnTo");reader.read(tag,registries);journal=nextJournal;journalData=nextText;
    }
    public CompoundTag write(HolderLookup.Provider registries){return reader.write(registries);}
    public boolean occupied(){return reader.occupied();}
    public net.minecraft.world.item.ItemStack tokenCopy(){return reader.tokenCopy();}
    public NativeTokenReader.Visual visual(long now){return reader.visual(now);}
    public NativeTokenReader.Motion motion(long now){return reader.motion(now);}
    public com.vandorlabs.programmer.JournalText journal(){return journal;}
    public CompoundTag journalData(){return journalData==null?null:journalData.copy();}
}
