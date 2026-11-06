package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.credentials.CredentialCatalog;
import com.vandorlabs.neoforge.redstone.NativeRedstoneChannels;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** One physical credential and its current reader policy. World callbacks occur only on guarded server actions. */
public final class NativeTokenReader {
    public interface Host {
        BlockEntity owner();
        boolean usable(ServerPlayer player);
        void changed();
        void schedule(int ticks);
        default int returnPulseTicks(){return 20;}
        /** Scalar hooks must be local-only. A door reads its authoritative shared toggle here. */
        default boolean toggleState(boolean local){return local;}
        /** Embedded doors perform their side effect and return UNCHANGED dormant local TokenToggled. */
        default boolean flipToggle(boolean local){return !local;}
        default boolean chip(boolean saved){return saved;}
        default ServerPlayer returnPlayer(ServerLevel level,UUID id){return level.getServer().getPlayerList().getPlayer(id);}
    }
    /** Safe for models/update packets. The physical stack, its components and owner UUID never escape here. */
    public record Visual(boolean chip,int company,int logo,boolean icon,int output,boolean occupied){
        public Visual{if(!CredentialCatalog.validCompany(company)||logo< -1||logo>=7||output<0||output>15)throw new IllegalArgumentException("Reader visual");}
        public int kind(){return chip?1:0;}
    }
    /** Relative cosmetic sample; authority remains in the saved absolute reader timeline. */
    public record Motion(long sequence,int age,boolean returning,int rejectedFor){
        public Motion{if(age<0||age>20||rejectedFor<0||rejectedFor>12)throw new IllegalArgumentException("Reader motion");}
    }
    private final Host host;
    private CompoundTag retained=new CompoundTag();
    private ItemStack token=ItemStack.EMPTY;
    private Tag opaqueToken;
    private boolean chip,any=true,toggled,toggleApplied;
    private int allowed=127,behavior,logo;
    private long pulseUntil,insertStarted,insertionSequence,rejectedUntil,nextReturnAttempt;
    private UUID returnTo;
    private boolean acting;
    public NativeTokenReader(Host host,boolean chip){this.host=Objects.requireNonNull(host);this.chip=chip;}
    public boolean chip(){return host.chip(chip);}
    public boolean any(){return any;}
    public int allowed(){return allowed;}
    public int behavior(){return behavior;}
    public int effectiveAllowed(){return CredentialCatalog.effectiveAllowed(any,allowed);}
    public boolean occupied(){return !token.isEmpty()||opaqueToken!=null;}
    public boolean opaqueOccupied(){return opaqueToken!=null;}
    public boolean returning(){return returnTo!=null&&!token.isEmpty();}
    public ItemStack tokenCopy(){return NativeCredentialItems.ownedCopy(token);}
    public boolean accepts(ItemStack stack){return NativeCredentialItems.accepts(stack,chip());}
    public boolean authorized(ItemStack stack){return NativeCredentialItems.authorized(stack,chip(),effectiveAllowed());}
    public boolean matches(){return authorized(token);}
    public int logoDesign(){int mask=effectiveAllowed();if((mask&1)!=0)return 0;if((mask&(1<<logo))!=0)return logo;for(int i=0;i<7;i++)if((mask&(1<<i))!=0)return i;return 0;}
    public int outputLevel(long now){return CredentialCatalog.output(behavior,behavior<2&&matches(),behavior==2&&host.toggleState(toggled),now,pulseUntil);}
    public Visual visual(long now){int company=NativeCredentialItems.company(token);return new Visual(chip(),CredentialCatalog.validCompany(company)?company:0,effectiveAllowed()==0?-1:logoDesign(),accepts(token),outputLevel(now),occupied());}
    public Motion motion(long now){return new Motion(insertionSequence,(int)Math.max(0,Math.min(20,now-insertStarted)),returning(),(int)Math.max(0,Math.min(12,rejectedUntil-now)));}
    public static ItemStack icon(Visual visual){return !visual.icon?ItemStack.EMPTY:visual.chip?NativeCredentialItems.dataChip(visual.company):NativeCredentialItems.accessCard(visual.company);}
    /** Local-only load: no owner lookup, chunk read, callback, registration or timeline advance. */
    public void read(CompoundTag input,HolderLookup.Provider registries){
        CompoundTag next=input.copy();ItemStack decoded=next.contains("InsertedToken",Tag.TAG_COMPOUND)?NativeCredentialItems.readStack(next.getCompound("InsertedToken"),registries):ItemStack.EMPTY;
        Tag unresolved=decoded.isEmpty()&&next.contains("InsertedToken")?next.get("InsertedToken"):null;
        UUID owner=next.contains("TokenReturnToMost",Tag.TAG_ANY_NUMERIC)&&next.contains("TokenReturnToLeast",Tag.TAG_ANY_NUMERIC)?new UUID(next.getLong("TokenReturnToMost"),next.getLong("TokenReturnToLeast")):next.hasUUID("TokenReturnTo")?next.getUUID("TokenReturnTo"):null;
        boolean nextAny=!next.contains("AnyToken")||next.getBoolean("AnyToken");int nextAllowed=CredentialCatalog.singleCompany(nextAny?127:next.contains("AllowedTokens")?next.getInt("AllowedTokens"):127);
        retained=next;retained.remove("InsertedToken");token=decoded.isEmpty()?ItemStack.EMPTY:decoded.copyWithCount(1);opaqueToken=unresolved;
        returnTo=token.isEmpty()&&opaqueToken==null?null:owner;chip=next.getBoolean("ChipSlot");any=nextAllowed==127;allowed=nextAllowed;behavior=CredentialCatalog.behavior(next.getInt("TokenBehavior"));logo=Math.max(0,Math.min(6,next.getInt("TokenLogo")));
        rejectedUntil=next.getLong("TokenRejectedUntil");pulseUntil=next.getLong("TokenPulseUntil");toggled=next.getBoolean("TokenToggled");toggleApplied=next.getBoolean("TokenToggleApplied");insertStarted=next.getLong("TokenInsertStarted");insertionSequence=next.getLong("TokenInsertionSequence");
    }
    public CompoundTag write(HolderLookup.Provider registries){
        var out=retained.copy();out.putInt("TokenLogo",logo);out.putLong("TokenRejectedUntil",rejectedUntil);out.putBoolean("ChipSlot",chip());out.putBoolean("AnyToken",any);out.putInt("AllowedTokens",allowed);out.putInt("TokenBehavior",behavior);out.putLong("TokenPulseUntil",pulseUntil);out.putBoolean("TokenToggled",toggled);out.putBoolean("TokenToggleApplied",toggleApplied);out.putLong("TokenInsertStarted",insertStarted);out.putLong("TokenInsertionSequence",insertionSequence);
        if(out.hasUUID("TokenReturnTo"))out.remove("TokenReturnTo");
        if(returnTo!=null){out.putLong("TokenReturnToMost",returnTo.getMostSignificantBits());out.putLong("TokenReturnToLeast",returnTo.getLeastSignificantBits());}else{out.remove("TokenReturnToMost");out.remove("TokenReturnToLeast");}
        if(!token.isEmpty())out.put("InsertedToken",NativeCredentialItems.writeStack(token,registries));else if(opaqueToken!=null)out.put("InsertedToken",opaqueToken.copy());else out.remove("InsertedToken");return out;
    }
    /** Recovery is a complete owned current reader snapshot, not a model/update-packet projection. */
    public CompoundTag rawRecovery(HolderLookup.Provider registries){return write(registries);}
    public boolean configureLocal(boolean requestedAny,int requestedAllowed,int requestedBehavior){
        int nextAllowed=CredentialCatalog.singleCompany(requestedAny?127:requestedAllowed),nextBehavior=CredentialCatalog.behavior(requestedBehavior);boolean nextAny=nextAllowed==127,changed=any!=nextAny||allowed!=nextAllowed||behavior!=nextBehavior;
        if(changed){pulseUntil=0;toggled=false;}any=nextAny;allowed=nextAllowed;behavior=nextBehavior;return changed;
    }
    public void configure(boolean any,int allowed,int behavior){configureLocal(any,allowed,behavior);publish(false);}
    private ServerLevel installed(){
        BlockEntity owner=host.owner();var level=owner.getLevel();if(!(level instanceof ServerLevel server))return null;
        if(!server.getServer().isSameThread())throw new IllegalStateException("Credential action requires owning server thread");
        if(!NativeRedstoneChannels.open(server)||owner.isRemoved()||!server.hasChunkAt(owner.getBlockPos())||server.getBlockEntity(owner.getBlockPos())!=owner)return null;return server;
    }
    private void sound(SoundEvent sound,float pitch){var level=installed();if(level!=null)level.playSound(null,host.owner().getBlockPos(),sound,SoundSource.BLOCKS,.25F,pitch);}
    private void schedule(int ticks){if(installed()!=null)host.schedule(Math.max(1,ticks));}
    private void publish(boolean inserted){
        var level=installed();if(level==null)return;if(inserted){insertStarted=level.getGameTime();insertionSequence++;if(matches()&&behavior==1){pulseUntil=level.getGameTime()+host.returnPulseTicks();schedule(host.returnPulseTicks());}}
        if(token.isEmpty()&&behavior==1)pulseUntil=0;host.changed();
    }
    private static void sync(ServerPlayer player){player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();}
    private ItemStack inventoryCredential(ServerPlayer player){for(var stack:player.getInventory().items)if(authorized(stack))return stack;var off=player.getOffhandItem();return authorized(off)?off:null;}
    public boolean holdingCredential(ServerPlayer player,InteractionHand hand,boolean inventoryFallback){var level=installed();return level!=null&&player.serverLevel()==level&&(accepts(player.getItemInHand(hand))||inventoryFallback&&inventoryCredential(player)!=null);}
    /** Caller has selected the credential route; configuration/menu routing remains host-owned. */
    public boolean interact(ServerPlayer player,InteractionHand hand,boolean inventoryFallback){
        if(acting)return false;acting=true;try{return interactOwned(player,hand,inventoryFallback);}finally{acting=false;}
    }
    private boolean interactOwned(ServerPlayer player,InteractionHand hand,boolean inventoryFallback){
        var level=installed();if(level==null||player.serverLevel()!=level||!host.usable(player))return false;
        if(opaqueOccupied())return true;ItemStack held=player.getItemInHand(hand);if(!occupied()&&!authorized(held)&&inventoryFallback){var found=inventoryCredential(player);if(found!=null)held=found;}
        if(held.isEmpty()){
            if(!occupied()&&!inventoryFallback&&hand==InteractionHand.MAIN_HAND&&accepts(player.getOffhandItem()))return false;
            if(!occupied())cycleLogo();else{var removed=remove();if(!removed.isEmpty()&&!player.getInventory().add(removed)&&!removed.isEmpty())player.drop(removed,false);}sync(player);return true;
        }
        if(!accepts(held))return inventoryFallback;if(!authorized(held)){reject();return true;}if(occupied())return true;
        if(behavior>=2)insertToggle(player,held);else{token=NativeCredentialItems.ownedCopy(held).copyWithCount(1);if(!player.isCreative())held.shrink(1);publish(true);}sync(player);return true;
    }
    private boolean insertToggle(ServerPlayer player,ItemStack held){
        var level=installed();if(level==null||(behavior!=2&&behavior!=3)||occupied()||!host.usable(player)||!authorized(held))return false;
        token=NativeCredentialItems.ownedCopy(held).copyWithCount(1);held.shrink(1);returnTo=player.getUUID();insertStarted=level.getGameTime();insertionSequence++;toggleApplied=false;publish(false);sound(SoundEvents.IRON_TRAPDOOR_OPEN,1.4F);schedule(8);return true;
    }
    public void reject(){var level=installed();if(level!=null){rejectedUntil=level.getGameTime()+12;publish(false);}}
    public void cycleLogo(){int mask=effectiveAllowed();if((mask&1)!=0)return;for(int i=1;i<=7;i++){int candidate=(logoDesign()+i)%7;if((mask&(1<<candidate))!=0){logo=candidate;publish(false);return;}}}
    public ItemStack remove(){return returning()?ItemStack.EMPTY:takeForDrop();}
    /** Unknown raw contents are never cleared when no real droppable stack exists. */
    public ItemStack takeForDrop(){if(installed()==null||opaqueToken!=null)return ItemStack.EMPTY;var result=token;token=ItemStack.EMPTY;returnTo=null;publish(false);return result;}
    private static boolean hasRoom(ServerPlayer player,ItemStack token){
        for(var slot:player.getInventory().items)if(slot.isEmpty()||ItemStack.isSameItemSameComponents(slot,token)&&slot.getCount()<Math.min(slot.getMaxStackSize(),player.getInventory().getMaxStackSize()))return true;
        for(var slot:player.getInventory().offhand)if(!slot.isEmpty()&&ItemStack.isSameItemSameComponents(slot,token)&&slot.getCount()<Math.min(slot.getMaxStackSize(),player.getInventory().getMaxStackSize()))return true;return false;
    }
    public boolean expire(long now){
        if(acting)return false;acting=true;try{return expireOwned(now);}finally{acting=false;}
    }
    private boolean expireOwned(long now){
        var level=installed();if(level==null)return false;boolean dirty=false;if((behavior==1||behavior==3)&&pulseUntil>0&&now>=pulseUntil){pulseUntil=0;dirty=true;}
        if(returning()){
            if(!toggleApplied&&now>=insertStarted+8){toggleApplied=true;if((behavior==2||behavior==3)&&matches()){if(behavior==2)toggled=host.flipToggle(toggled);else pulseUntil=now+host.returnPulseTicks();sound(SoundEvents.UI_BUTTON_CLICK.value(),toggled?1.4F:.8F);}dirty=true;}
            if(installed()!=null&&now>=insertStarted+20&&now>=nextReturnAttempt&&returning()){
                nextReturnAttempt=now+20;var player=host.returnPlayer(level,returnTo);if(player!=null&&player.isAlive()&&hasRoom(player,token)){
                    var returned=NativeCredentialItems.ownedCopy(token);if(player.getInventory().add(returned)&&returned.isEmpty()){token=ItemStack.EMPTY;returnTo=null;sync(player);sound(SoundEvents.IRON_TRAPDOOR_CLOSE,1.6F);dirty=true;}
                }
            }
        }
        if(dirty)publish(false);return dirty;
    }
    public void scheduledTick(){var level=installed();if(level==null)return;long now=level.getGameTime();expire(now);if(installed()==null)return;
        if(returning())schedule(now>=insertStarted+20?20:(int)Math.max(1,(toggleApplied?insertStarted+20:insertStarted+8)-now));else if((behavior==1||behavior==3)&&pulseUntil>now)schedule((int)Math.min(20,pulseUntil-now));
    }
    /** Invoke from the owner's existing deferred lifecycle, never directly from BlockEntity.onLoad. */
    public void resume(boolean clampPulse){var level=installed();if(level==null)return;if(clampPulse)pulseUntil=Math.min(pulseUntil,level.getGameTime()+20);scheduledTick();}
}
