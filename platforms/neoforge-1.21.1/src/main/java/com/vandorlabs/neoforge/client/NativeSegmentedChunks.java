package com.vandorlabs.neoforge.client;

import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.mixin.*;
import com.vandorlabs.neoforge.segmented.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Publish all cell flags before scheduling; retain closed BER panels until every section ACKs. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeSegmentedChunks {
    private NativeSegmentedChunks(){}
    static {NativeSegmentedDoorEntity.clientObserver(NativeSegmentedChunks::changed);}
    private static final Set<NativeSegmentedDoorEntity> QUEUED=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<NativeSegmentedDoorEntity> KNOWN=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<NativeSegmentedDoorEntity,Column> ACTIVE=new IdentityHashMap<>();
    private static final Map<BlockPos,Section> SECTIONS=new LinkedHashMap<>();
    private static ClientLevel world;private static long revision,bakeGeneration,requests,publications,submissions,completions,samples;
    public record Stats(long requests,long publications,long submissions,long completions,long memberSamples,int pendingColumns,int knownColumns,int sections){}
    public static Stats stats(){return new Stats(requests,publications,submissions,completions,samples,ACTIVE.size(),KNOWN.size(),SECTIONS.size());}
    public static void changed(NativeSegmentedDoorEntity tile){NativeSegmentedGateContexts.changed(tile);synchronized(QUEUED){QUEUED.add(tile);requests++;}}
    private static boolean queued(){synchronized(QUEUED){return !QUEUED.isEmpty();}}
    private static NativeSegmentedDoorEntity loaded(BlockPos pos){if(world==null)return null;var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);var raw=chunk==null?null:chunk.getBlockEntities().get(pos);return raw instanceof NativeSegmentedDoorEntity tile&&!tile.isRemoved()&&tile.getLevel()==world?tile:null;}
    private static final class Column {
        final NativeSegmentedDoorEntity root;List<NativeSegmentedDoorEntity> members;boolean dirty=true,force;Pending pending;
        Column(NativeSegmentedDoorEntity root){this.root=root;}
    }
    private static final class Pending {
        final boolean body,gate;final List<Section> sections;
        Pending(boolean body,boolean gate,List<Section> sections){this.body=body;this.gate=gate;this.sections=sections;}
    }
    private static final class Section {
        final BlockPos origin;int refs;long revision;SectionRenderDispatcher.RenderSection section;
        NativeSegmentedSectionUploads.Publication publication;Object before;
        Section(BlockPos origin){this.origin=origin;}
        void bind(){
            var renderer=(SegmentedLevelRendererAccessor)Minecraft.getInstance().levelRenderer;var view=renderer.vandorlabs$segmentedViewArea();if(view==null)return;
            var current=((SegmentedViewAreaInvoker)view).vandorlabs$segmentedSection(origin);if(current==null||!current.getOrigin().equals(origin)){releasePublication();section=null;before=null;return;}
            if(current!=section||publication==null||publication.generation()!=revision){
                releasePublication();section=current;before=current.getCompiled();publication=NativeSegmentedSectionUploads.publish((NativeSegmentedSectionUploads.Section)current,revision);current.setDirty(false);
            }
        }
        boolean acknowledged(){return section!=null&&publication!=null&&NativeSegmentedSectionUploads.acknowledged((NativeSegmentedSectionUploads.Section)section,publication,before);}
        void releasePublication(){if(section!=null&&publication!=null)NativeSegmentedSectionUploads.remove((NativeSegmentedSectionUploads.Section)section,publication);publication=null;}
    }
    private static void release(Pending pending){if(pending==null)return;for(var section:pending.sections)if(--section.refs==0){section.releasePublication();SECTIONS.remove(section.origin);}}
    private static boolean same(NativeSegmentedTerrainSnapshot a,NativeSegmentedTerrainSnapshot b){return a.facing()==b.facing()&&a.texture()==b.texture()&&a.header()==b.header()&&a.body()==b.body()&&a.top()==b.top()&&a.bottom()==b.bottom()&&a.column()==b.column()&&a.row()==b.row();}
    private static boolean members(Column column,NativeSegmentedVisual visual){
        if(!column.dirty&&column.members!=null)return true;var next=new ArrayList<NativeSegmentedDoorEntity>(visual.height());
        for(int row=0;row<visual.height();row++){samples++;var tile=loaded(column.root.getBlockPos().below(row));if(tile==null||tile.topOffset()!=row||tile.height()!=visual.height()||tile.facing()!=visual.facing()||!tile.inert()&&(tile.publication()==null||!tile.publication().visual().ready()))return false;next.add(tile);}
        column.members=List.copyOf(next);column.dirty=false;return true;
    }
    private static void begin(Column column,NativeSegmentedVisual visual,boolean body,boolean changedBody){
        boolean keepSuppressed=!column.force&&!changedBody&&body&&column.root.clientTerrain().suppressed();
        boolean gate=column.root.clientTerrain().awaitingChunk()||column.force&&body||changedBody||column.pending!=null&&column.pending.gate;
        release(column.pending);long next=++revision;var affected=new LinkedHashSet<BlockPos>();
        boolean oldHeader=column.root.clientTerrain().headerTerrain()||column.root.clientTerrain().terrain().header();
        for(int row=0;row<column.members.size();row++){
            var tile=column.members.get(row);var snapshot=NativeSegmentedTerrainSnapshot.cell(visual,row,tile.effectiveTexture(),body,next);
            tile.clientTerrain(new NativeSegmentedTerrainSnapshot.ClientProjection(snapshot,row==0&&gate,row==0&&keepSuppressed,row==0&&oldHeader,next));publications++;
            affected.add(new BlockPos(tile.getBlockPos().getX()&~15,tile.getBlockPos().getY()&~15,tile.getBlockPos().getZ()&~15));
        }
        // ModelDataManager snapshots on createCompileTask drain all these refreshes together.
        for(var tile:column.members)tile.requestModelDataUpdate();var sections=new ArrayList<Section>(affected.size());
        for(var origin:affected){var section=SECTIONS.computeIfAbsent(origin,Section::new);section.refs++;section.revision=next;sections.add(section);}
        column.pending=new Pending(body,gate,List.copyOf(sections));column.force=false;
    }
    private static boolean process(Column column){
        var root=column.root;var visual=NativeSegmentedBlockRenderer.visual(root);if(visual==null||!visual.ready()||!members(column,visual))return false;
        boolean eligible=!root.inert()&&NativeSegmentedTerrainSnapshot.eligible(visual);
        boolean body=eligible&&!root.targetOpen()&&(root.clientTerrain().awaitingChunk()||NativeSegmentedBlockRenderer.selectionPoseWithoutPolling(root)==0);
        boolean changedBody=false,changed=false;for(int row=0;row<column.members.size();row++){var tile=column.members.get(row);var previous=tile.clientTerrain().terrain();var next=NativeSegmentedTerrainSnapshot.cell(visual,row,tile.effectiveTexture(),body,0);changedBody|=previous.body()!=body;changed|=!same(previous,next);}
        if(changed||column.force||column.pending!=null&&column.pending.body!=body)begin(column,visual,body,changedBody);
        return column.pending==null&&(root.targetOpen()||root.clientTerrain().suppressed()||!eligible);
    }
    private static boolean complete(Column column){
        var pending=column.pending;if(pending==null||column.dirty||queued())return false;
        for(var section:pending.sections)if(!section.acknowledged())return false;
        var root=column.root;var old=root.clientTerrain();root.clientTerrain(new NativeSegmentedTerrainSnapshot.ClientProjection(old.terrain(),false,pending.body,old.terrain().header(),old.revision()));
        column.pending=null;release(pending);completions++;
        if(!pending.body&&pending.gate){var visual=NativeSegmentedBlockRenderer.visual(root);var motion=NativeSegmentedBlockRenderer.motion(root);if(visual!=null&&motion!=null)NativeSegmentedRenderer.resume(world,root.getBlockPos(),visual,motion);}
        return true;
    }
    /** Uploads may finish during the render's upload phase, after the last client tick. */
    public static void pollReady(NativeSegmentedDoorEntity root){if(root.getLevel()!=world||bakeGeneration!=NativeSegmentedTerrainModel.activeGeneration())return;var column=ACTIVE.get(root);if(column!=null&&complete(column)&&(root.targetOpen()||root.clientTerrain().suppressed()||!NativeSegmentedTerrainSnapshot.eligible(NativeSegmentedBlockRenderer.visual(root))))ACTIVE.remove(root);}
    private static void clear(){synchronized(QUEUED){QUEUED.clear();}ACTIVE.clear();KNOWN.clear();SECTIONS.clear();NativeSegmentedSectionUploads.clear();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var current=Minecraft.getInstance().level;if(current!=world){clear();world=current;bakeGeneration=NativeSegmentedTerrainModel.activeGeneration();}
        if(world==null)return;boolean enabled=NativeSegmentedTerrainModel.activeGeneration()!=0;
        if(enabled)reloadIfChanged();
        synchronized(QUEUED){for(var tile:QUEUED){if(tile.getLevel()!=world)continue;if(tile.isRemoved()){var old=ACTIVE.remove(tile);if(old!=null)release(old.pending);KNOWN.remove(tile);}var root=tile.topOffset()==0?tile:loaded(tile.getBlockPos().above(tile.topOffset()));if(root==null||root.isRemoved()||root.getLevel()!=world||root.topOffset()!=0)continue;KNOWN.add(root);if(enabled)ACTIVE.computeIfAbsent(root,Column::new).dirty=true;}QUEUED.clear();}
        if(!enabled)return;
        var iterator=ACTIVE.values().iterator();while(iterator.hasNext()){var column=iterator.next();var root=column.root;if(root.getLevel()!=world||root.isRemoved()||root.topOffset()!=0||loaded(root.getBlockPos())!=root){release(column.pending);iterator.remove();continue;}if(process(column))iterator.remove();}
        for(var section:SECTIONS.values())section.bind();iterator=ACTIVE.values().iterator();while(iterator.hasNext()){var column=iterator.next();if(complete(column)&&(column.root.targetOpen()||column.root.clientTerrain().suppressed()||!NativeSegmentedTerrainSnapshot.eligible(NativeSegmentedBlockRenderer.visual(column.root))))iterator.remove();}
        var dispatcher=((SegmentedLevelRendererAccessor)Minecraft.getInstance().levelRenderer).vandorlabs$segmentedDispatcher();if(dispatcher==null)return;RenderRegionCache cache=null;int submitted=0;
        for(var section:SECTIONS.values()){if(section.section==null||!section.section.getOrigin().equals(section.origin)||!section.section.isDirty())continue;if(submitted>=8)break;if(cache==null)cache=new RenderRegionCache();section.section.rebuildSectionAsync(dispatcher,cache);section.section.setNotDirty();submitted++;submissions++;}
    }
    private static void reloadIfChanged(){
        if(world==null||NativeSegmentedTerrainModel.activeGeneration()==0||bakeGeneration==NativeSegmentedTerrainModel.activeGeneration())return;
        bakeGeneration=NativeSegmentedTerrainModel.activeGeneration();NativeSegmentedSectionUploads.clear();SECTIONS.clear();
        for(var root:KNOWN){if(root==null||root.isRemoved()||root.getLevel()!=world||root.topOffset()!=0)continue;var column=ACTIVE.computeIfAbsent(root,Column::new);column.pending=null;column.force=true;column.dirty=true;var old=root.clientTerrain();if(old.suppressed()||old.awaitingChunk())root.clientTerrain(new NativeSegmentedTerrainSnapshot.ClientProjection(old.terrain(),true,false,old.headerTerrain(),old.revision()));}
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Reload {
        @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void completed(net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted event){Minecraft.getInstance().execute(NativeSegmentedChunks::reloadIfChanged);}
    }
    @SubscribeEvent public static void load(net.neoforged.neoforge.event.level.LevelEvent.Load event){if(event.getLevel() instanceof ClientLevel level){clear();world=level;bakeGeneration=NativeSegmentedTerrainModel.activeGeneration();}}
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel()==world){clear();world=null;}}
}
