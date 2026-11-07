package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.ramp.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;

/** Actual BE codec/installed handler, with explicit local times independent of server epochs. */
final class NativeRampPartPacketChecks {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Ramp part packet "+why);}
    private static CompoundTag tag(CompoundTag base,long revision,long tick,NativeRampPartData data){var tag=base.copy();tag.putLong("NativeRampPublicationRevision",revision);tag.putLong("NativeRampObservationTick",tick);tag.put("NativeRampPartData",data.write());return tag;}
    private static void deliver(Minecraft mc,NativeRampPartEntity owner,CompoundTag tag){var bytes=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),mc.level.registryAccess());try{var packet=ClientboundBlockEntityDataPacket.create(owner,(tile,registries)->tag.copy());ClientboundBlockEntityDataPacket.STREAM_CODEC.encode(bytes,packet);var decoded=ClientboundBlockEntityDataPacket.STREAM_CODEC.decode(bytes);need(!bytes.isReadable()&&decoded.getTag().equals(tag),"codec round trip");mc.getConnection().handleBlockEntityData(decoded);}finally{bytes.release();}}
    private static NativeRampPartVisualClock.State sample(Minecraft mc,NativeRampPartEntity owner,double time){var p=owner.publication();var d=p.data();var m=d.motion();var b=d.belt();return NativeRampPartVisualClock.sample(mc.level,owner.controller(),owner.operationId(),p.revision(),m.moving(),m.observed(),m.target(),m.duration(),b.mode()!=0,b.observed(),b.running()?b.velocity(d.spec()):0,time);}
    private static NativeRampPartData moving(NativeRampPartData data,long sequence,double from,double target,double observed){return data.withMotion(new NativeRampPartData.Motion(sequence,0,from,target,16,true,observed));}
    static void run(Minecraft mc){
        var pos=RampPartWorldFixture.pos(0);var owner=(NativeRampPartEntity)mc.level.getBlockEntity(pos);var base=owner.getUpdateTag(mc.level.registryAccess());var original=owner.data();NativeRampPartVisualClock.clear(mc.level);
        var open=tag(base,10,100,moving(original,10,0,1,0));deliver(mc,owner,open);need(sample(mc,owner,0).pose()==0&&sample(mc,owner,4).pose()==.25,"new motion smooth local rate");
        var advanced=tag(base,10,104,moving(original,10,0,1,.5));deliver(mc,owner,advanced);need(owner.publication().pose()==.5,"same command newer physical observation admitted");need(sample(mc,owner,6).pose()==.375,"new observation does not restart cosmetic motion");
        deliver(mc,owner,open);need(owner.publication().observationTick()==104,"older phase rejected");
        var close=tag(base,11,105,moving(original,11,.5,0,.5));deliver(mc,owner,close);need(sample(mc,owner,6).pose()==.375&&sample(mc,owner,8).pose()==.25,"ordered reversal continuous");
        var components=owner.components();var persistent=owner.getPersistentData().copy();var stale=open.copy();var oldComponents=net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("stale-ramp-component")).build();stale.put("components",net.minecraft.core.component.DataComponentMap.CODEC.encodeStart(mc.level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),oldComponents).getOrThrow());var oldPrivate=new CompoundTag();oldPrivate.putString("StaleRampPacket","must-not-install");stale.put("NeoForgeData",oldPrivate);deliver(mc,owner,stale);
        need(owner.publication().revision()==11&&owner.components()==components&&owner.getPersistentData().equals(persistent),"stale guard precedes platform/component installation");
        deliver(mc,owner,close);need(sample(mc,owner,10).pose()==.125,"duplicate reversal continues");
        var complete=original.withMotion(new NativeRampPartData.Motion(12,0,1,1,16,false,1));deliver(mc,owner,tag(base,12,110,complete));need(sample(mc,owner,10).pose()==1,"authoritative final endpoint");deliver(mc,owner,close);need(sample(mc,owner,14).pose()==1,"old moving packet cannot restart endpoint");
        var replacement=new NativeRampPartEntity(owner.getType(),pos,owner.getBlockState());replacement.loadWithComponents(owner.getUpdateTag(mc.level.registryAccess()),mc.level.registryAccess());mc.level.setBlockEntity(replacement);need(sample(mc,replacement,14).pose()==1,"same-operation actual BE replacement retains clock");
        var fresh=tag(base,0,0,moving(original,0,.2,1,.2));fresh.putUUID("Operation",java.util.UUID.randomUUID());deliver(mc,replacement,fresh);need(sample(mc,replacement,14).pose()==.2,"new operation starts its own observation");
        // Restore the original complete body before rendered baseline/reload comparisons.
        var restore=tag(base,100,200,original);restore.putUUID("Operation",replacement.operationId());deliver(mc,replacement,restore);
        NativeRampPartVisualClock.clear(mc.level);var peer=(NativeRampPartEntity)mc.level.getBlockEntity(RampPartWorldFixture.pos(1));var peerBase=peer.getUpdateTag(mc.level.registryAccess());var peerOriginal=peer.data();
        deliver(mc,replacement,tag(restore,200,300,moving(original,200,0,1,0)));var shared=tag(peerBase,200,301,moving(peerOriginal,200,0,1,.8));shared.putUUID("Operation",replacement.operationId());shared.putInt("RootX",replacement.controller().getX());shared.putInt("RootY",replacement.controller().getY());shared.putInt("RootZ",replacement.controller().getZ());deliver(mc,peer,shared);
        need(sample(mc,replacement,0)==sample(mc,peer,0)&&sample(mc,peer,4).pose()==.25&&sample(mc,replacement,4).pose()==.25,"late member shares controller operation clock instead of starting at its own observation");
        deliver(mc,replacement,tag(restore,300,400,original));deliver(mc,peer,tag(peerBase,300,400,peerOriginal));
        var beltOwner=(NativeRampPartEntity)mc.level.getBlockEntity(RampPartWorldFixture.pos(3));var beltBase=beltOwner.getUpdateTag(mc.level.registryAccess());var beltData=beltOwner.data();var running=beltData.withBelt(new NativeRampPartData.Belt(1,0,.3,0,true,.3));
        deliver(mc,beltOwner,tag(beltBase,10,100,running));need(sample(mc,beltOwner,0).distance()==.3,"initial belt observed phase");double predicted=sample(mc,beltOwner,10).distance();need(Math.abs(predicted-.8)<1e-9,"belt advances through server observation gap");
        var observed=running.withBelt(new NativeRampPartData.Belt(1,0,.3,0,true,.45));deliver(mc,beltOwner,tag(beltBase,10,101,observed));need(beltOwner.publication().beltGeometry().distance()==.45&&Math.abs(sample(mc,beltOwner,10).distance()-.8)<1e-9,"same command newer physical belt phase does not reset cosmetics");
        var stopped=running.withBelt(new NativeRampPartData.Belt(1,0,.4,0,false,.4));var stop=tag(beltBase,11,102,stopped);deliver(mc,beltOwner,stop);need(sample(mc,beltOwner,10).distance()==.4,"late stop commits processed phase after cosmetic prediction");deliver(mc,beltOwner,tag(beltBase,10,999,running));need(sample(mc,beltOwner,20).distance()==.4,"old running packet rejected even with newer phase tick");deliver(mc,beltOwner,stop);need(sample(mc,beltOwner,30).distance()==.4,"repeated stop stays still");
        deliver(mc,beltOwner,tag(beltBase,100,200,beltData));NativeRampPartVisualClock.clear(mc.level);
        System.out.println("[vandorlabs][ramp-part-packets] PASS checks="+checks+" actual codec/installed handler/reorder/duplicates/phase/reversal/stop/replacement/new operation");
    }
}
