package com.vandorlabs.neoforge.canopy;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Installed-thread local preparation; pairing and reservation transactions are separate stages. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCanopyLifecycle {
    private NativeCanopyLifecycle(){}
    private static java.util.function.BiConsumer<Level,BlockPos> CLIENT_RETIRE=(world,pos)->{};
    /** Client binding only; the common lifecycle never loads a renderer or a client clock class. */
    public static void clientRetirement(java.util.function.BiConsumer<Level,BlockPos> retire){CLIENT_RETIRE=java.util.Objects.requireNonNull(retire);}
    static void retireClientRoot(NativeCanopyEntity host){var world=host.getLevel();if(world==null||!world.isClientSide)return;var links=host.links();if(links.anchor()!=null&&!links.anchor().equals(host.getBlockPos()))return;CLIENT_RETIRE.accept(world,links.pair()==null?host.getBlockPos():links.pair());CLIENT_RETIRE.accept(world,host.getBlockPos());}
    private static final Map<Level,NativeCanopyGeometry.Cache> PHYSICAL=Collections.synchronizedMap(new WeakHashMap<>());
    public static NativeCanopyEntity at(Level level,BlockPos pos){
        if(level==null)return null;if(level instanceof ServerLevel server&&!server.getServer().isSameThread())throw new IllegalStateException("Canopy preparation requires owning thread");
        var chunk=level instanceof ServerLevel server?server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4):level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        if(!(chunk instanceof LevelChunk full))return null;var entity=full.getBlockEntities().get(pos);return entity instanceof NativeCanopyEntity host&&!host.isRemoved()?host:null;
    }
    public static boolean installed(NativeCanopyEntity host){return at(host.getLevel(),host.getBlockPos())==host;}
    /** Called after chunk installation, never from onLoad or a render/shape query. */
    public static void prepare(NativeCanopyEntity host){
        if(!host.pending()||!installed(host))return;var previous=host.publication();
        if(!host.available()||!previous.draws()){host.prepared(new NativeCanopyEntity.Publication(previous.visual(),null,previous.progress(),previous.targetOpen(),previous.waiting(),previous.draws(),true,host.inert()));return;}
        var pos=host.getBlockPos();var cache=PHYSICAL.computeIfAbsent(host.getLevel(),unused->new NativeCanopyGeometry.Cache(16*1024*1024));
        var geometry=cache.prepare(previous.visual(),previous.progress(),pos.getX(),pos.getY(),pos.getZ());
        host.prepared(new NativeCanopyEntity.Publication(previous.visual(),geometry,previous.progress(),previous.targetOpen(),previous.waiting(),true,true,false));
    }
    public static NativeCanopyEntity constituent(NativeCanopyEntity host){
        var anchor=host.links().anchor();if(anchor==null||anchor.equals(host.getBlockPos()))return host;var candidate=at(host.getLevel(),anchor);return sameBlock(host,candidate)?candidate:host;
    }
    public static NativeCanopyEntity owner(NativeCanopyEntity host){
        var member=constituent(host);var pair=member.links().pair();if(pair==null||pair.equals(member.getBlockPos()))return member;var candidate=at(member.getLevel(),pair);return sameBlock(member,candidate)?candidate:member;
    }
    private static boolean sameBlock(NativeCanopyEntity a,NativeCanopyEntity b){return b!=null&&a.getBlockState().is(b.getBlockState().getBlock());}
    static List<NativeCanopyEntity> members(NativeCanopyEntity host){
        var root=owner(host);List<NativeCanopyEntity> result=new ArrayList<>();int width=root.links().pair()==null?1:root.links().joinedWidth();
        for(int i=0;i<width;i++){var member=at(root.getLevel(),root.getBlockPos().relative(root.facing().getClockWise(),i));if(sameBlock(root,member)&&constituent(member)==member&&(member==root||root.getBlockPos().equals(member.links().pair())))result.add(member);}return result;
    }
    /** Incremental left-before-right Source joining, never a history-independent repartition. */
    public static void tryPair(NativeCanopyEntity host){
        if(!host.available()||!installed(host)||!(host.kind().equals("canopy")||host.kind().equals("long_canopy"))||constituent(host)!=host)return;
        var root=owner(host);if(root.links().open()||root.links().progress()!=0)return;
        var data=host.settings().data();var facing=host.facing();
        for(var direction:new net.minecraft.core.Direction[]{facing.getCounterClockWise(),facing.getClockWise()}){
            var edge=root.getBlockPos().relative(direction,direction==facing.getCounterClockWise()?1:root.width());var adjacent=at(host.getLevel(),edge);
            if(!sameBlock(host,adjacent)||!adjacent.available()||constituent(adjacent)!=adjacent||adjacent.facing()!=facing)continue;
            var otherData=adjacent.settings().data();if(otherData.mount!=data.mount||otherData.motion!=data.motion||data.motion==0&&otherData.slideForward!=data.slideForward||otherData.angle!=data.angle)continue;
            var other=owner(adjacent);if(other==root||!other.available()||other.links().open()||other.links().progress()!=0||root.width()+other.width()>9)continue;
            var group=members(root);group.addAll(members(other));var left=direction==facing.getCounterClockWise()?other:root;
            for(var member:group)member.stageLinks(member.links().withPair(left.getBlockPos(),group.size()));
            // All ownership facts are installed before the first external observer is notified.
            for(var member:group)member.notifyChanged();root=left;
        }
    }
    /** Ownership phase only: callers retain the constituent's own item data before erasing its cells. */
    public static NativeCanopyEntity unpairForRemoval(NativeCanopyEntity hit){
        var base=constituent(hit);if(!base.available()||!installed(base))return null;var root=owner(base);base.markRemoving();
        if(root.links().pair()!=null){var group=members(root);for(var member:group)member.stageLinks(member.links().unpairedClosed());for(var member:group)member.notifyChanged();for(var member:group)if(member!=base)tryPair(member);}
        return base;
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event){var cache=PHYSICAL.remove(event.getLevel());if(cache!=null)cache.clear();}
}
