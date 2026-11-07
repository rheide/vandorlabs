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
    private final NativeTokenReader.Host host;
    private NativeChipDisplay display;
    private static final NativeTokenReader.Host STORAGE=new NativeTokenReader.Host(){
        public net.minecraft.world.level.block.entity.BlockEntity owner(){throw new IllegalStateException("Detached chip storage has no gameplay owner");}
        public boolean usable(net.minecraft.server.level.ServerPlayer player){return false;}
        public void changed(){throw new IllegalStateException("Detached chip storage cannot publish gameplay");}
        public void schedule(int ticks){throw new IllegalStateException("Detached chip storage cannot schedule gameplay");}
    };
    public NativeEmbeddedChipReader(NativeTokenReader.Host host){
        this.host=java.util.Objects.requireNonNull(host);
        reader=new NativeTokenReader(new NativeTokenReader.Host(){
            public net.minecraft.world.level.block.entity.BlockEntity owner(){return host.owner();}
            public boolean usable(net.minecraft.server.level.ServerPlayer player){return host.usable(player);}
            public void changed(){prepareJournal(reader.tokenCopy());host.changed();}
            public void schedule(int ticks){host.schedule(ticks);}
            public boolean chip(boolean saved){return true;}
        },true);
    }
    private void prepareJournal(net.minecraft.world.item.ItemStack token){display=NativeChipDisplay.prepare(token);}
    /** Caller selected an authorized tray hit. Screen trays never search inventory or accept a second chip. */
    public boolean interact(net.minecraft.server.level.ServerPlayer player,net.minecraft.world.InteractionHand hand){
        var owner=host.owner();if(!(owner.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return false;
        if(!level.getServer().isSameThread())throw new IllegalStateException("Embedded chip action requires owning server thread");
        if(!com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(level)||owner.isRemoved()||!level.hasChunkAt(owner.getBlockPos())||level.getBlockEntity(owner.getBlockPos())!=owner||player.serverLevel()!=level||!host.usable(player))return false;
        var held=player.getItemInHand(hand);
        if(held.isEmpty()&&!reader.occupied())return true;
        if(!held.isEmpty()){
            if(reader.occupied()||!reader.accepts(held))return false;
            NativeCredentialItems.writeStack(NativeCredentialItems.ownedCopy(held).copyWithCount(1),level.registryAccess());
        }
        return reader.interact(player,hand,false);
    }
    public static NativeEmbeddedChipReader storage(CompoundTag raw,HolderLookup.Provider registries){var result=new NativeEmbeddedChipReader(STORAGE);result.read(raw,registries);return result;}
    /** Validate before touching live state; the owning Screen retains its complete snapshot on refusal. */
    public void read(CompoundTag raw,HolderLookup.Provider registries){
        for(String key:raw.getAllKeys())if(!KEYS.contains(key))throw new IllegalArgumentException("Unsupported embedded chip reader field: "+key);
        var token=raw.contains("InsertedToken",Tag.TAG_COMPOUND)?NativeCredentialItems.readStack(raw.getCompound("InsertedToken"),registries):net.minecraft.world.item.ItemStack.EMPTY;
        if(raw.contains("InsertedToken")&&token.isEmpty())throw new IllegalArgumentException("Unresolved embedded chip ownership");
        if(!token.isEmpty())NativeCredentialItems.writeStack(token.copyWithCount(1),registries);
        var nextDisplay=NativeChipDisplay.prepare(token);
        var tag=raw.copy();tag.putBoolean("ChipSlot",true);tag.putBoolean("AnyToken",true);tag.putInt("AllowedTokens",127);tag.putInt("TokenBehavior",0);tag.remove("TokenReturnToMost");tag.remove("TokenReturnToLeast");tag.remove("TokenReturnTo");reader.read(tag,registries);display=nextDisplay;
    }
    public CompoundTag write(HolderLookup.Provider registries){return reader.write(registries);}
    public boolean occupied(){return reader.occupied();}
    public net.minecraft.world.item.ItemStack tokenCopy(){return reader.tokenCopy();}
    public NativeTokenReader.Visual visual(long now){return reader.visual(now);}
    public NativeTokenReader.Motion motion(long now){return reader.motion(now);}
    public boolean master(){return display!=null&&display.master;}
    /** Null means the consumer must localize the stock chip name. */
    public String displayName(){return display==null?null:display.customName;}
    public NativeChipDisplay display(){return display;}
    public com.vandorlabs.programmer.JournalText journal(){return display==null?null:display.journal;}
    public CompoundTag journalData(){return display==null?null:display.text();}
}
