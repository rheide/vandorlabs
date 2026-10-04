package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.persistence.SpaceDoorData;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Compare persisted state and opening decisions with the released group power loop. */
final class TrapdoorPowerChecks {
    static void run() {
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(Probe.class,new ResourceLocation("minecraft:vandorlabs_power_check"));
        NonRenderingChecks.MemoryWorld before=new NonRenderingChecks.MemoryWorld(false),after=new NonRenderingChecks.MemoryWorld(false);
        Probe[] reference=group(before,true),current=group(after,false);
        reference[0].evaluatePower(false);current[0].evaluatePower(false);
        before.dirty=after.dirty=0;
        for(int i=0;i<256;i++){reference[0].evaluatePower(false);current[0].evaluatePower(false);}
        System.out.println("Unchanged trapdoor group dirty calls, reference/current: "+before.dirty+"/"+after.dirty);
        if(after.dirty!=0)throw new AssertionError("unchanged trapdoor power still dirties chunks");
        reference[0].setChannelSignal(true);current[0].setChannelSignal(true);
        for(Probe leaf:reference)leaf.reads=0;
        for(Probe leaf:current)leaf.reads=0;
        for(int i=0;i<256;i++){reference[0].evaluatePower(false);current[0].evaluatePower(false);}
        int oldReads=0,newReads=0;
        for(int i=0;i<64;i++){oldReads+=reference[i].reads;newReads+=current[i].reads;}
        if(newReads!=0)throw new AssertionError("powered root channel still scanned physical inputs");
        System.out.println("Powered trapdoor group local queries, reference/current: "+oldReads+"/"+newReads);
        Random random=new Random(78231);
        for(int event=0;event<600;event++) {
            if(event%200==0)for(int i=0;i<64;i++){reference[i].triggerMode(event/200);current[i].triggerMode(event/200);}
            if(event%40==0)for(int i=0;i<64;i++) {
                reference[i].source=current[i].source=false;
                reference[i].setChannelSignal(false);current[i].setChannelSignal(false);
            }
            for(int changed=0;changed<1+event%4;changed++) {
                int member=random.nextInt(64);boolean powered=random.nextBoolean();
                reference[member].source=current[member].source=powered;
            }
            int notified=random.nextInt(64);
            if(event%3==0) {
                boolean channel=random.nextBoolean();
                reference[notified].setChannelSignal(channel);current[notified].setChannelSignal(channel);
            }
            boolean force=event%11==0;
            reference[notified].evaluatePower(force);current[notified].evaluatePower(force);
            for(int i=0;i<64;i++) {
                if(!before.getBlockState(reference[i].getPos()).equals(after.getBlockState(current[i].getPos())))throw new AssertionError("trapdoor power changed open state");
                if(!reference[i].writeToNBT(new NBTTagCompound()).equals(current[i].writeToNBT(new NBTTagCompound())))throw new AssertionError("trapdoor power changed persistence");
            }
        }
        // Power loss must still settle all leaves and remain persisted.
        for(int i=0;i<64;i++){reference[i].source=current[i].source=false;reference[i].setChannelSignal(false);current[i].setChannelSignal(false);}
        reference[0].evaluatePower(false);current[0].evaluatePower(false);
        for(int i=0;i<64;i++)if(!reference[i].writeToNBT(new NBTTagCompound()).equals(current[i].writeToNBT(new NBTTagCompound())))throw new AssertionError("power loss not saved");
        System.out.println("PASS: 600 trapdoor group events match released opening/persistence rules after coalesced physical/channel changes");
    }
    private static Probe[] group(NonRenderingChecks.MemoryWorld world,boolean reference) {
        Probe[] group=new Probe[64];BlockPos origin=new BlockPos(8,100,8);NBTTagList assembly=new NBTTagList();
        for(int i=0;i<64;i++)assembly.appendTag(new NBTTagLong(origin.add(i%8,0,i/8).toLong()));
        for(int i=0;i<64;i++) {
            Probe leaf=new Probe();leaf.reference=reference;BlockPos pos=origin.add(i%8,0,i/8);
            NBTTagCompound tag=leaf.writeToNBT(new NBTTagCompound());tag.setInteger("x",pos.getX());tag.setInteger("y",pos.getY());tag.setInteger("z",pos.getZ());
            tag.setTag("TrapdoorAssembly",assembly.copy());tag.setInteger("TrapdoorTrigger",SpaceDoorData.TRIGGER_REDSTONE_ON);
            leaf.readFromNBT(tag);leaf.setWorld(world);world.states.put(pos,ModBlocks.PROGRAMMABLE_TRAPDOOR.getDefaultState());world.tiles.put(pos,leaf);group[i]=leaf;
        }
        return group;
    }
    public static final class Probe extends TileEntityProgrammableTrapdoor {
        boolean reference,source;int reads;
        @Override public boolean hasLocalRedstoneSignal(){reads++;return source;}
        void triggerMode(int mode){trigger=mode;}
        @Override public void evaluatePower(boolean force) {
            if(!reference){super.evaluatePower(force);return;}
            // Frozen released algorithm; fixtures are ordinary joined leaves, not gear covers.
            if(configuring || world==null || world.isRemote)return;
            List<TileEntityProgrammableTrapdoor> members=group();boolean powered=false;
            for(TileEntityProgrammableTrapdoor leaf:members)powered|=((Probe)leaf).signal() || leaf.hasLocalRedstoneSignal();
            boolean changed=!powerKnown || lastPower!=powered;
            powerKnown=true;lastPower=powered;
            for(TileEntityProgrammableTrapdoor leaf:members){((Probe)leaf).setKnown(powered);leaf.markDirty();}
            markDirty();
            if(trigger!=SpaceDoorData.TRIGGER_DISABLED && (force || changed))requestOpen(trigger==SpaceDoorData.TRIGGER_REDSTONE_ON?powered:!powered);
        }
        boolean signal(){return channelSignal;}
        void setKnown(boolean powered){powerKnown=true;lastPower=powered;}
    }
}
