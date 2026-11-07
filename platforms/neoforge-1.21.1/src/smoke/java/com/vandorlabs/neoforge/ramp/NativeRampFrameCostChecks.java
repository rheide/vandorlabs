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
        long nanos,maximum,allocated,constructed,removed,automaticTags,sampledTags,packets,bytes,cells,newCells;
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
                public void removed(NativeRampPartEntity part){totals[pass].removed++;}
                public void updateTag(NativeRampPartEntity part,CompoundTag tag){if(sampling)totals[pass].sampledTags++;else totals[pass].automaticTags++;}
            });
        }
        double pose=step<=8?step/8D:(16-step)/8D;
        long nextRevision=pass==0?revision:revision+1;
        var candidate=new NativeRampFramePublisher.Frame(assembly.cells(pose,pose),new NativeRampPartData.Motion(0,tick,pose,pose,8,false,pose),NativeRampPartData.Belt.NONE,nextRevision);
        int added=0;for(var position:candidate.cells())if(!before.cells().contains(position))added++;
        long thread=Thread.currentThread().threadId(),allocation=METER.getThreadAllocatedBytes(thread),start=System.nanoTime();
        var result=NativeRampFrameCommand.advance(level,fixture.operation(),fixture.actor(),before,candidate,nextRevision+1,(player,payload)->{throw new AssertionError("Unexpected player rider in cost baseline");});
        long elapsed=System.nanoTime()-start;count.nanos+=elapsed;count.maximum=Math.max(count.maximum,elapsed);count.allocated+=METER.getThreadAllocatedBytes(thread)-allocation;
        need(result.outcome()==NativeRampFrameCommand.Outcome.INSTALLED,"actual frame admitted: "+result.outcome()+" "+result.error());need(result.notices().pending().isEmpty(),"notifications completed");
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
        for(int i=0;i<totals.length;i++){var value=totals[i];need(value.steps==17&&value.constructed>0&&value.removed>0&&value.packets==value.sampledTags,"complete actual lifecycle/codec baseline");System.out.println("[vandorlabs][ramp-frame-cost] mode="+(i==0?"fixed-revision":"changed-revision")+" steps="+value.steps+" partCells="+value.cells+" newlyOccupied="+value.newCells+" constructed="+value.constructed+" removed="+value.removed+" automaticUpdateTags="+value.automaticTags+" explicitCodecPackets="+value.packets+" projectedWireBytes="+value.bytes+" commandAllocatedBytes="+value.allocated+" commandNanos="+value.nanos+" peakCommandNanos="+value.maximum);}
        System.out.println("[vandorlabs][ramp-frame-cost] PASS private two-source baseline; no connected-client delivery or dense-ramp performance acceptance");return true;
    }
    private NativeRampFrameCostChecks(){}
}
