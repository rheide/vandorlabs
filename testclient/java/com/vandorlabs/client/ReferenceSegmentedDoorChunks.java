package com.vandorlabs.client;

import com.vandorlabs.client.segmented.SegmentedDoorVisuals;

import com.vandorlabs.segmented.SegmentedDoorTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.*;
import java.util.*;

/** Frozen master publisher (02a0c652) for idle retirement and allocation regression comparisons. */
public final class ReferenceSegmentedDoorChunks {
    private static final Set<SegmentedDoorTile> QUEUED=Collections.newSetFromMap(new IdentityHashMap<SegmentedDoorTile,Boolean>());
    private static final Map<SegmentedDoorTile,Pending> ACTIVE=new IdentityHashMap<>();
    private static final Field FRUSTUM=ReflectionHelper.findField(RenderGlobal.class,"viewFrustum","field_175008_n"),DISPATCHER=ReflectionHelper.findField(RenderGlobal.class,"renderDispatcher","field_174995_M"),TASK=ReflectionHelper.findField(RenderChunk.class,"compileTask","field_178599_i");
    private static final Method LOOKUP=lookup();
    private static Method lookup(){for(Method m:ViewFrustum.class.getDeclaredMethods())if(m.getReturnType()==RenderChunk.class&&Arrays.equals(m.getParameterTypes(),new Class[]{BlockPos.class})){m.setAccessible(true);return m;}throw new IllegalStateException("Missing render chunk lookup");}
    private static final class Section {
        final BlockPos pos;RenderChunk chunk;CompiledChunk before;
        Section(BlockPos pos){this.pos=pos;}
        boolean uploaded(Minecraft mc,Set<RenderChunk> rebuild)throws ReflectiveOperationException{
            ViewFrustum view=(ViewFrustum)FRUSTUM.get(mc.renderGlobal);if(view==null)return false;RenderChunk current=(RenderChunk)LOOKUP.invoke(view,pos);
            if(current==null||!current.getPosition().equals(pos))return false;
            if(chunk!=current){chunk=current;before=current.getCompiledChunk();current.setNeedsUpdate(false);}
            if(current.needsUpdate()){rebuild.add(current);return false;}
            current.getLockCompileTask().lock();try{
                ChunkCompileTaskGenerator task=(ChunkCompileTaskGenerator)TASK.get(current);
                return !current.needsUpdate()&&task!=null&&task.getStatus()==ChunkCompileTaskGenerator.Status.DONE&&current.getCompiledChunk()!=before&&task.getCompiledChunk()==current.getCompiledChunk();
            }finally{current.getLockCompileTask().unlock();}
        }
    }
    private static final class Pending {
        final boolean baked;final List<SegmentedDoorTile> members;final List<Section> sections=new ArrayList<>();
        Pending(SegmentedDoorTile root,boolean baked,List<SegmentedDoorTile> members){
            this.baked=baked;this.members=members;Set<BlockPos> positions=new LinkedHashSet<>();for(SegmentedDoorTile tile:members){tile.clientBaked=baked;positions.add(new BlockPos(tile.getPos().getX()&~15,tile.getPos().getY()&~15,tile.getPos().getZ()&~15));}
            for(BlockPos pos:positions)sections.add(new Section(pos));root.clientAwaitingChunk=true;root.clientSuppressed=false;
            root.getWorld().markBlockRangeForRenderUpdate(root.getPos().down(root.height-1),root.getPos());
        }
        boolean uploaded(Minecraft mc,Set<RenderChunk> rebuild)throws ReflectiveOperationException{boolean done=true;for(Section section:sections)done&=section.uploaded(mc,rebuild);return done;}
    }
    public static void changed(SegmentedDoorTile tile){if(tile.getWorld()!=null&&tile.getWorld().isRemote&&!(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld))synchronized(QUEUED){QUEUED.add(tile);}}
    public static void reload(){for(Pending pending:ACTIVE.values())if(pending!=null)for(Section section:pending.sections){section.chunk=null;section.before=null;}}
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld().isRemote){synchronized(QUEUED){QUEUED.removeIf(t->t.getWorld()==event.getWorld());}ACTIVE.keySet().removeIf(t->t.getWorld()==event.getWorld());}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();if(mc.world==null)return;
        synchronized(QUEUED){for(SegmentedDoorTile tile:QUEUED)if(tile.getWorld()==mc.world&&!tile.isInvalid()){SegmentedDoorTile root=tile.root();if(root.ready()&&root.isRoot())ACTIVE.putIfAbsent(root,null);}QUEUED.clear();}
        Set<RenderChunk> rebuild=new LinkedHashSet<>();
        try{Iterator<Map.Entry<SegmentedDoorTile,Pending>> it=ACTIVE.entrySet().iterator();while(it.hasNext()){
            Map.Entry<SegmentedDoorTile,Pending> entry=it.next();SegmentedDoorTile root=entry.getKey();if(root.getWorld()!=mc.world||root.isInvalid()||SegmentedDoorTile.at(mc.world,root.getPos())!=root||!root.isRoot()){it.remove();continue;}
            List<SegmentedDoorTile> members=new ArrayList<>();boolean complete=true;for(int row=0;row<root.height;row++){SegmentedDoorTile member=SegmentedDoorTile.at(mc.world,root.getPos().down(row));if(member==null||!member.ready()||member.topOffset!=row||member.height!=root.height){complete=false;break;}members.add(member);}if(!complete)continue;
            Pending pending=entry.getValue();boolean wantsBaked=root.canBake()&&!root.targetOpen&&(root.clientAwaitingChunk||SegmentedDoorVisuals.pose(root,0)==0);
            boolean changed=false;for(SegmentedDoorTile member:members)changed|=member.clientBaked!=wantsBaked;
            if(changed||pending!=null&&(pending.baked!=wantsBaked||!pending.members.equals(members))){pending=new Pending(root,wantsBaked,members);entry.setValue(pending);}
            if(pending!=null){if(!pending.uploaded(mc,rebuild))continue;root.clientAwaitingChunk=false;root.clientSuppressed=pending.baked;if(!pending.baked)SegmentedDoorVisuals.resume(root);entry.setValue(null);}
            if(root.clientSuppressed||root.targetOpen){it.remove();}
        }
        // Publish every column flag first. Queue each dirty section once, including
        // sections outside the current frustum. Native workers/upload queues retain
        // normal scheduling; cap submissions so a bank of doors cannot spike a tick.
        ChunkRenderDispatcher dispatcher=(ChunkRenderDispatcher)DISPATCHER.get(mc.renderGlobal);int submitted=0;
        if(dispatcher!=null)for(RenderChunk chunk:rebuild){if(submitted++>=8||!dispatcher.updateChunkLater(chunk))break;chunk.clearNeedsUpdate();}
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Segmented terrain publication failed",failure);}
    }
    private ReferenceSegmentedDoorChunks(){}
}
