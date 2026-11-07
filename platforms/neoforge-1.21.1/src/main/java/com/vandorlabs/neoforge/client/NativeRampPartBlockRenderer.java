package com.vandorlabs.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vandorlabs.neoforge.NativeRampPartRenderer;
import com.vandorlabs.neoforge.NativeRampPartVisualClock;
import com.vandorlabs.neoforge.ramp.*;
import java.util.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Inactive generated-part adapter: source capture on binding change, immutable geometry thereafter. */
public final class NativeRampPartBlockRenderer implements BlockEntityRenderer<NativeRampPartEntity> {
    private static final long BUDGET=8*1024*1024;
    private static final Map<Object,WorldCache> WORLDS=new WeakHashMap<>();
    private static NativeRampPartRenderer.Bake generation;
    private static final class WorldCache {
        final LinkedHashMap<BlockPos,Presentation> entries=new LinkedHashMap<>(32,.75F,true);
        long bytes=256;
    }
    private static final class Presentation {
        final BlockPos pos;
        final AABB bounds;
        NativeRampGeometry.Spec spec;
        Map<BlockPos,NativeRampPartMaterial> choices;
        NativeRampPartRenderer.Materials materials;
        NativeRampPartRenderer.SourceFaces source;
        NativeRampGeometry.Snapshot geometry;
        NativeRampBeltGeometry.Snapshot belt;
        NativeRampPartRenderer.Draw draw;
        double pose=Double.NaN,distance=Double.NaN;
        long bindings,weight=512;
        Presentation(BlockPos pos){this.pos=pos.immutable();bounds=new AABB(this.pos);}
        long bytes(){return 512+bindings+(belt==null?geometry==null?0:geometry.retainedBytes():belt.retainedBytes())+(draw==null?0:draw.retainedBytes());}
    }
    public NativeRampPartBlockRenderer(BlockEntityRendererProvider.Context context){}
    private static WorldCache world(Object level){return WORLDS.computeIfAbsent(level,ignored->new WorldCache());}
    private static Presentation presentation(WorldCache cache,BlockPos pos){
        var found=cache.entries.get(pos);
        if(found==null){found=new Presentation(pos);cache.entries.put(found.pos,found);cache.bytes+=found.weight;trim(cache);}
        return found;
    }
    private static void trim(WorldCache cache){while(cache.bytes>BUDGET&&!cache.entries.isEmpty()){var iterator=cache.entries.entrySet().iterator();var old=iterator.next();cache.bytes-=old.getValue().weight;iterator.remove();}}
    private static void account(WorldCache cache,Presentation entry){cache.bytes+=entry.bytes()-entry.weight;entry.weight=entry.bytes();trim(cache);}
    private static void bind(Presentation entry,NativeRampPartData data,Level level){
        entry.spec=data.spec();entry.choices=data.materials();
        entry.materials=new NativeRampPartRenderer.Materials(data.original(),data.materials());
        entry.source=NativeRampPartRenderer.source(data.original(),level,entry.pos);
        entry.bindings=1024+entry.materials.retainedBytes()+256;
        for(var origin:data.spec().origins())entry.bindings+=128L+80L*origin.shapes().size();
        entry.geometry=null;entry.belt=null;entry.draw=null;entry.pose=entry.distance=Double.NaN;
    }
    @Override public void render(NativeRampPartEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        var level=tile.getLevel();var publication=tile.publication();var operation=tile.operationId();
        if(level==null||publication==null||operation==null||!tile.ownershipKnown())return;
        var bake=NativeRampPartRenderer.currentBake();if(bake==null)return;
        if(generation!=bake){WORLDS.clear();generation=bake;}
        var cache=world(level);var entry=presentation(cache,tile.getBlockPos());var data=publication.data();
        if(entry.spec!=data.spec()||entry.choices!=data.materials())bind(entry,data,level);
        var motion=NativeRampPartVisualClock.sample(level,tile.getBlockPos(),operation,publication.revision(),data);
        boolean belt=publication.beltGeometry()!=null;
        if(entry.draw==null||(belt?entry.distance!=motion.distance():entry.pose!=motion.pose())||belt!=(entry.belt!=null)){
            entry.pose=motion.pose();entry.distance=motion.distance();
            if(belt){entry.belt=NativeRampBeltGeometry.snapshot(data.spec(),entry.distance);entry.geometry=entry.belt.geometry();entry.draw=bake.drawBelt(entry.belt,entry.materials,entry.source);}
            else{entry.belt=null;entry.geometry=NativeRampGeometry.snapshot(data.spec(),entry.pose);entry.draw=bake.draw(data.spec(),entry.geometry,entry.pose,entry.materials,entry.source);}
            account(cache,entry);
        }
        entry.draw.emit(poses.last(),buffers,light,overlay);
    }
    @Override public int getViewDistance(){return 256;}
    @Override public AABB getRenderBoundingBox(NativeRampPartEntity tile){var level=tile.getLevel();return level==null?new AABB(tile.getBlockPos()):presentation(world(level),tile.getBlockPos()).bounds;}
    public static long retainedBytes(Object level){var cache=WORLDS.get(level);return cache==null?0:cache.bytes;}
    public static void clear(){WORLDS.clear();generation=null;}
    @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
    public static final class Worlds {@SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel().isClientSide())WORLDS.remove(event.getLevel());}}
    @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Resources {@SubscribeEvent public static void completed(net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted event){clear();}}
}
