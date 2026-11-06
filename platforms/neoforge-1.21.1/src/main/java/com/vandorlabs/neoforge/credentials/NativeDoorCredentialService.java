package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.credentials.CredentialCatalog;
import com.vandorlabs.neoforge.door.NativeDoorSettings;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Two physical readers on the owning lower Door, sharing its authoritative security toggle. */
public final class NativeDoorCredentialService {
    public interface Host {
        BlockEntity owner();
        boolean usable(ServerPlayer player);
        void changed();
        void schedule(int ticks);
        boolean securityToggled();
        void flipSecurityToggle();
    }
    public record Side(NativeTokenReader.Visual visual,NativeTokenReader.Motion motion){}
    public record Projection(Side front,Side rear){public Side side(int side){return side==1?rear:front;}}
    private static final Side EMPTY_SIDE=new Side(new NativeTokenReader.Visual(false,0,0,false,0,false),new NativeTokenReader.Motion(0,20,false,0));
    public static final Projection EMPTY=new Projection(EMPTY_SIDE,EMPTY_SIDE);
    private static final String[] KEYS={"DoorKeyReader","DoorRearKeyReader"};
    private final Host host;
    private final NativeTokenReader[] readers=new NativeTokenReader[2];
    private boolean acting;
    private final Tag[] opaqueReaders=new Tag[2];
    public NativeDoorCredentialService(Host host){
        this.host=Objects.requireNonNull(host);
        for(int side=0;side<2;side++)readers[side]=new NativeTokenReader(new NativeTokenReader.Host(){
            public BlockEntity owner(){return host.owner();}
            public boolean usable(ServerPlayer player){return host.usable(player);}
            public void changed(){host.changed();}
            public void schedule(int ticks){host.schedule(ticks);}
            public int returnPulseTicks(){return 100;}
            public boolean toggleState(boolean ignored){return host.securityToggled();}
            public boolean flipToggle(boolean local){host.flipSecurityToggle();return local;}
        },false);
    }
    private static int index(int side){if(side<0||side>1)throw new IllegalArgumentException("Door reader side");return side;}
    /** Pure local decode. Legacy readers may have independent saved company policies. */
    public void read(CompoundTag tag,HolderLookup.Provider registries,int securityMode,int fallbackAllowed){
        for(int side=0;side<2;side++){
            var key=KEYS[side];opaqueReaders[side]=tag.contains(key)&&!tag.contains(key,Tag.TAG_COMPOUND)?tag.get(key).copy():null;
            readers[side].read(tag.getCompound(key),registries);
            if(readers[side].opaqueOccupied())opaqueReaders[side]=tag.getCompound(key).copy();
            int mask=tag.contains(key,Tag.TAG_COMPOUND)?readers[side].effectiveAllowed():CredentialCatalog.singleCompany(fallbackAllowed);
            readers[side].configureLocal(false,mask,NativeDoorSettings.securityBehavior(securityMode));
        }
    }
    /** Opaque reader and stack ownership belongs only to saved/recovery data. */
    public void write(CompoundTag tag,HolderLookup.Provider registries){for(int side=0;side<2;side++)tag.put(KEYS[side],opaqueReaders[side]!=null?opaqueReaders[side].copy():readers[side].write(registries));}
    public CompoundTag rawRecovery(HolderLookup.Provider registries){var tag=new CompoundTag();write(tag,registries);return tag;}
    public void configureLocal(int securityMode,int allowed){for(var reader:readers)reader.configureLocal(false,allowed,NativeDoorSettings.securityBehavior(securityMode));}
    public int effectiveAllowed(int side){return readers[index(side)].effectiveAllowed();}
    /** Caller resolves the exact installed lower owner before invoking any action. */
    public boolean interact(int side,ServerPlayer player,InteractionHand hand){int i=index(side);if(acting)return false;acting=true;try{return opaqueReaders[i]!=null?usable(player):readers[i].interact(player,hand,true);}finally{acting=false;}}
    private boolean usable(ServerPlayer player){var owner=host.owner();if(!(owner.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return false;if(!level.getServer().isSameThread())throw new IllegalStateException("Door credential action requires owning server thread");return com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(level)&&!owner.isRemoved()&&level.hasChunkAt(owner.getBlockPos())&&level.getBlockEntity(owner.getBlockPos())==owner&&player.serverLevel()==level&&host.usable(player);}
    public boolean holdingCredential(ServerPlayer player,InteractionHand hand){return readers[0].holdingCredential(player,hand,true);}
    public boolean readerOpen(int panelMode,long now){return panelMode>0&&opaqueReaders[0]==null&&readers[0].outputLevel(now)>0||panelMode==2&&opaqueReaders[1]==null&&readers[1].outputLevel(now)>0;}
    public boolean opaqueOccupied(int side){int i=index(side);return opaqueReaders[i]!=null||readers[i].opaqueOccupied();}
    public ItemStack takeForDrop(int side){int i=index(side);boolean previous=acting;acting=true;try{return opaqueReaders[i]!=null?ItemStack.EMPTY:readers[i].takeForDrop();}finally{acting=previous;}}
    public void scheduledTick(){if(acting)return;acting=true;try{for(int i=0;i<2;i++)if(opaqueReaders[i]==null)readers[i].scheduledTick();}finally{acting=false;}}
    /** Door reload does not apply the standalone reader's twenty-tick pulse clamp. */
    public void resume(){if(acting)return;acting=true;try{for(int i=0;i<2;i++)if(opaqueReaders[i]==null)readers[i].resume(false);}finally{acting=false;}}
    private Side side(int i,long now){
        var visual=readers[i].visual(now);if(opaqueReaders[i]!=null)visual=new NativeTokenReader.Visual(visual.chip(),0,visual.logo(),false,0,true);
        return new Side(visual,readers[i].motion(now));
    }
    public Projection projection(long now){return new Projection(side(0,now),side(1,now));}
    public void writeProjection(CompoundTag out,long now){writeProjection(out,projection(now));}
    public static void writeProjection(CompoundTag out,Projection projection){
        for(int i=0;i<2;i++){var side=projection.side(i);var v=side.visual();var m=side.motion();var tag=new CompoundTag();tag.putBoolean("Chip",v.chip());tag.putInt("Company",v.company());tag.putInt("Logo",v.logo());tag.putBoolean("Icon",v.icon());tag.putInt("Output",v.output());tag.putBoolean("Occupied",v.occupied());tag.putLong("Sequence",m.sequence());tag.putInt("Age",m.age());tag.putBoolean("Returning",m.returning());tag.putInt("Rejected",m.rejectedFor());tag.putLong("RejectionDeadline",m.rejectionDeadline());out.put("DoorReaderVisual"+i,tag);}
    }
    public static Projection readProjection(CompoundTag tag){if(!tag.contains("DoorReaderVisual0")&&!tag.contains("DoorReaderVisual1"))return EMPTY;return new Projection(readSide(tag.getCompound("DoorReaderVisual0")),readSide(tag.getCompound("DoorReaderVisual1")));}
    private static Side readSide(CompoundTag tag){return new Side(new NativeTokenReader.Visual(tag.getBoolean("Chip"),Math.max(0,Math.min(6,tag.getInt("Company"))),Math.max(-1,Math.min(6,tag.getInt("Logo"))),tag.getBoolean("Icon"),Math.max(0,Math.min(15,tag.getInt("Output"))),tag.getBoolean("Occupied")),new NativeTokenReader.Motion(tag.getLong("Sequence"),Math.max(0,Math.min(20,tag.getInt("Age"))),tag.getBoolean("Returning"),Math.max(0,Math.min(12,tag.getInt("Rejected"))),tag.getLong("RejectionDeadline")));}
}
