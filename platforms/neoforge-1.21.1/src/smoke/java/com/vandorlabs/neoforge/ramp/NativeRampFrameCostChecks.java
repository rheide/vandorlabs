package com.vandorlabs.neoforge.ramp;

import java.lang.management.ManagementFactory;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Private installed-frame baseline. Explicit codec samples are not connected-client traffic. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeRampFrameCostChecks {
    private static final class Totals {
        long nanos,maximum,allocated,constructed,removed,removedOwned,automaticTags,sampledTags,packets,bytes,cells,newCells;
        int steps;
    }
    private static final Totals[] totals={new Totals(),new Totals()};
    private static NativeRampFramePublisher.Frame before;
    private static NativeRampAssemblyJournalChecks.PartProbe prior;
    private static int pass,step;private static long revision;private static boolean sampling;
    private static final com.sun.management.ThreadMXBean METER=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static void need(boolean ok,String why){if(!ok)throw new AssertionError("Ramp frame cost: "+why);}
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!"true".equals(System.getenv("VANDOR_RAMP_FRAME_COST")))return;
        NativeRampAssemblyJournalChecks.bindFrameCost(NativeRampFrameCostChecks::tick);
    }
    private static boolean tick(NativeRampAssemblyJournalChecks.FrameFixture fixture){
        var level=fixture.level();var assembly=fixture.operation().assembly();var count=totals[pass];long tick=level.getGameTime();
        if(before==null){
            METER.setThreadAllocatedMemoryEnabled(true);
            before=new NativeRampFramePublisher.Frame(assembly.cells(0,0),new NativeRampPartData.Motion(0,tick,0,0,8,false,0),NativeRampPartData.Belt.NONE,0);
            prior=NativeRampAssemblyJournalChecks.bindPartProbe(new NativeRampAssemblyJournalChecks.PartProbe(){
                public void constructed(NativeRampPartEntity part){totals[pass].constructed++;}
                public void removed(NativeRampPartEntity part){totals[pass].removed++;if(part.ownershipKnown())totals[pass].removedOwned++;}
                public void updateTag(NativeRampPartEntity part,CompoundTag tag){if(sampling)totals[pass].sampledTags++;else totals[pass].automaticTags++;}
            });
        }
        double pose=step<=8?step/8D:(16-step)/8D;
        long nextRevision=pass==0?revision:revision+1;
        var candidate=new NativeRampFramePublisher.Frame(assembly.cells(pose,pose),new NativeRampPartData.Motion(0,tick,pose,pose,8,false,pose),NativeRampPartData.Belt.NONE,nextRevision);
        var retainedOwners=new LinkedHashMap<net.minecraft.core.BlockPos,NativeRampPartEntity>();for(var position:before.cells())if(candidate.cells().contains(position))retainedOwners.put(position,(NativeRampPartEntity)level.getBlockEntity(position));
        int added=0;for(var position:candidate.cells())if(!before.cells().contains(position))added++;
        long thread=Thread.currentThread().threadId(),allocation=METER.getThreadAllocatedBytes(thread),start=System.nanoTime();
        var result=NativeRampFrameCommand.advance(level,fixture.operation(),fixture.actor(),before,candidate,nextRevision+1,(player,payload)->{throw new AssertionError("Unexpected player rider in cost baseline");});
        long elapsed=System.nanoTime()-start;count.nanos+=elapsed;count.maximum=Math.max(count.maximum,elapsed);count.allocated+=METER.getThreadAllocatedBytes(thread)-allocation;
        need(result.outcome()==NativeRampFrameCommand.Outcome.INSTALLED,"actual frame admitted: "+result.outcome()+" "+result.error());need(result.notices().pending().isEmpty(),"notifications completed");
        for(var entry:retainedOwners.entrySet())need(level.getBlockEntity(entry.getKey())==entry.getValue()&&!entry.getValue().isRemoved(),"overlapping owner identity retained");
        before=result.frame();revision=nextRevision;count.cells+=candidate.cells().size();count.newCells+=added;count.steps++;
        // There are no connected trackers in this dedicated fixture. Sample the real packet codec separately.
        sampling=true;try{for(var position:before.cells()){
            var entity=level.getBlockEntity(position);need(entity instanceof NativeRampPartEntity,"installed typed owner");var packet=((NativeRampPartEntity)entity).getUpdatePacket();need(packet!=null,"real update packet");
            var raw=Unpooled.buffer();try{var buffer=new RegistryFriendlyByteBuf(raw,level.registryAccess());ClientboundBlockEntityDataPacket.STREAM_CODEC.encode(buffer,packet);int bytes=buffer.readableBytes();var decoded=ClientboundBlockEntityDataPacket.STREAM_CODEC.decode(buffer);need(!buffer.isReadable()&&decoded.getPos().equals(packet.getPos())&&decoded.getType()==packet.getType()&&decoded.getTag().equals(packet.getTag()),"exact packet roundtrip");count.packets++;count.bytes+=bytes;}finally{raw.release();}
        }}finally{sampling=false;}
        if(++step<=16)return false;
        need(before.motion().observed()==0,"return to original-space support before generic recovery");
        if(pass==0){pass=1;step=0;return false;}
        NativeRampAssemblyJournalChecks.bindPartProbe(prior);
        retainedRollback(fixture);
        for(int i=0;i<totals.length;i++){var value=totals[i];need(value.steps==17&&value.constructed>0&&value.constructed<=value.newCells*3&&value.removed<=2*value.newCells&&value.removedOwned==value.newCells&&value.packets==value.sampledTags,"complete actual lifecycle/codec baseline constructed="+value.constructed+" removed="+value.removed+" owned="+value.removedOwned+" newlyOccupied="+value.newCells);System.out.println("[vandorlabs][ramp-frame-cost] mode="+(i==0?"fixed-revision":"changed-revision")+" steps="+value.steps+" partCells="+value.cells+" newlyOccupied="+value.newCells+" constructed="+value.constructed+" removed="+value.removed+" removedOwned="+value.removedOwned+" automaticUpdateTags="+value.automaticTags+" explicitCodecPackets="+value.packets+" projectedWireBytes="+value.bytes+" commandAllocatedBytes="+value.allocated+" commandNanos="+value.nanos+" peakCommandNanos="+value.maximum);}
        System.out.println("[vandorlabs][ramp-frame-cost] PASS private two-source baseline; no connected-client delivery or dense-ramp performance acceptance");return true;
    }
    private static void retainedRollback(NativeRampAssemblyJournalChecks.FrameFixture fixture){
        var level=fixture.level();var assembly=fixture.operation().assembly();
        for(String stage:List.of("after-candidate","after-retirement")){
            var candidate=new NativeRampFramePublisher.Frame(assembly.cells(.5,.5),new NativeRampPartData.Motion(0,level.getGameTime(),.5,.5,8,false,.5),NativeRampPartData.Belt.NONE,revision+1);
            var owners=new LinkedHashMap<net.minecraft.core.BlockPos,NativeRampPartEntity>();for(var position:before.cells())if(candidate.cells().contains(position))owners.put(position,(NativeRampPartEntity)level.getBlockEntity(position));need(!owners.isEmpty(),"fault fixture includes retained owners");
            NativeRampFramePublisher.testFault=at->{if(at.equals(stage))throw new IllegalStateException("fixture retained owner rollback "+stage);};
            NativeRampFrameCommand.Receipt result;try{result=NativeRampFrameCommand.advance(level,fixture.operation(),fixture.actor(),before,candidate,revision+2,(player,payload)->{throw new AssertionError("Unexpected player rider");});}finally{NativeRampFramePublisher.testFault=null;}
            need(result.outcome()==NativeRampFrameCommand.Outcome.RESTORED,"retained-owner fault restores frozen publication: "+stage+" "+result.error());
            before=result.frame();revision=before.revision();for(var entry:owners.entrySet())need(level.getBlockEntity(entry.getKey())==entry.getValue()&&entry.getValue().publication().pose()==0&&entry.getValue().publication().revision()==revision,"rollback preserves owner and restores exact frozen body");
            for(var position:assembly.potential())if(!before.cells().contains(position))need(!(level.getBlockEntity(position) instanceof NativeRampPartEntity),"rollback retires newly occupied owners");
        }
    }
    private NativeRampFrameCostChecks(){}
}
