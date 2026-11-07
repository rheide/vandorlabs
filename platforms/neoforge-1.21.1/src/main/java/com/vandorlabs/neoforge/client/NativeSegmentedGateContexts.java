package com.vandorlabs.neoforge.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.neoforge.segmented.*;
import java.util.*;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Main-thread bounded neighbour discovery. Cached values contain scalars/owned arrays only. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeSegmentedGateContexts {
    static {NativeSegmentedGateRays.bindClient((root,pose,out)->{var prepared=prepare(root);java.util.Arrays.fill(out,0);if(prepared.context!=null)NativeSegmentedGateRays.masks(prepared.context,pose,prepared.leftPose(),prepared.rightPose(),switch(root.facing()){case NORTH->0;case EAST->3;case SOUTH->2;case WEST->1;default->throw new IllegalArgumentException("Horizontal gate facing");},out);});}
    private static final int BUDGET=4*1024*1024,LIMIT=1024;
    public static final class Prepared {
        public final SegmentedGateContext context;
        private final WeakReference<NativeSegmentedDoorEntity> left,right;
        private final net.minecraft.world.level.block.state.BlockState state,leftState,rightState;
        private final WeakReference<NativeSegmentedDoorEntity.Publication> leftRevision,rightRevision;
        private Prepared(SegmentedGateContext context,NativeSegmentedDoorEntity owner,NativeSegmentedDoorEntity left,NativeSegmentedDoorEntity right){this.context=context;this.left=new WeakReference<>(left);this.right=new WeakReference<>(right);state=owner==null?null:owner.getBlockState();leftState=left==null?null:left.getBlockState();rightState=right==null?null:right.getBlockState();leftRevision=new WeakReference<>(left==null?null:left.publication());rightRevision=new WeakReference<>(right==null?null:right.publication());}
        private boolean valid(NativeSegmentedDoorEntity owner){return context==null||state==owner.getBlockState()&&valid(left,leftState,leftRevision)&&valid(right,rightState,rightRevision);}
        private static boolean valid(WeakReference<NativeSegmentedDoorEntity> reference,net.minecraft.world.level.block.state.BlockState state,WeakReference<NativeSegmentedDoorEntity.Publication> revision){var peer=reference.get();return state==null?peer==null:peer!=null&&!peer.isRemoved()&&peer.getBlockState()==state&&peer.publication()==revision.get();}
        public double leftPose(){return pose(left);}public double rightPose(){return pose(right);}
        private static double pose(WeakReference<NativeSegmentedDoorEntity> reference){var peer=reference.get();return peer==null||peer.isRemoved()||!Minecraft.getInstance().isSameThread()?Double.NaN:NativeSegmentedBlockRenderer.selectionPoseWithoutPolling(peer);}
        int bytes(){return 384+(context==null?0:context.retainedBytes());}
    }
    private static final Prepared NONE=new Prepared(null,null,null,null);
    private static final WeakHashMap<ClientLevel,SegmentedGatePreparedCache<NativeSegmentedDoorEntity,Prepared>> WORLDS=new WeakHashMap<>();
    private static long builds,rootReads;
    public record Stats(long builds,long loadedRootReads,int worlds,int columns,int retainedBytes){}
    public static Stats stats(){synchronized(WORLDS){int columns=0,bytes=512;for(var cache:WORLDS.values()){columns+=cache.size();bytes+=cache.retainedBytes();}return new Stats(builds,rootReads,WORLDS.size(),columns,bytes);}}
    public static void clear(){synchronized(WORLDS){WORLDS.clear();}}
    public static void changed(NativeSegmentedDoorEntity tile){if(!(tile.getLevel() instanceof ClientLevel world))return;BlockPos pos=tile.getBlockPos();synchronized(WORLDS){var cache=WORLDS.get(world);if(cache!=null)cache.changed(pos.getX(),pos.getY(),pos.getZ());}}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){synchronized(WORLDS){WORLDS.remove(event.getLevel());}}
    private static NativeSegmentedDoorEntity loaded(ClientLevel world,BlockPos pos){rootReads++;var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);var raw=chunk==null?null:chunk.getBlockEntities().get(pos);return raw instanceof NativeSegmentedDoorEntity tile&&!tile.isRemoved()&&tile.getLevel()==world&&tile.topOffset()==0&&!tile.inert()&&tile.publication()!=null&&tile.publication().visual().ready()?tile:null;}
    private static int[] styles(NativeSegmentedVisual visual){int[] styles=new int[visual.height()];for(int row=0;row<styles.length;row++)styles[row]=NativeSegmentedArtwork.gate(visual.texture(row));return styles;}
    private static boolean driver(NativeSegmentedDoorEntity a,NativeSegmentedDoorEntity b,int offset){
        if(b==null||a.getBlockState().getBlock()!=b.getBlockState().getBlock()||a.height()!=b.height()||a.columnsCount()!=b.columnsCount()||a.column()+offset!=b.column()||a.facing()!=b.facing())return false;var x=a.settings();var y=b.settings();return x.direction==y.direction&&x.straight==y.straight&&x.position==y.position&&a.ticksPerSection()==b.ticksPerSection();
    }
    private static boolean movingDriver(NativeSegmentedDoorEntity a,NativeSegmentedDoorEntity b){var motion=a.publication().motion().motionIdentity();return motion!=null&&motion.equals(b.publication().motion().motionIdentity())&&a.started()==b.started()&&a.duration()==b.duration()&&a.targetOpen()==b.targetOpen()&&Double.doubleToLongBits(a.initialPose())==Double.doubleToLongBits(b.initialPose());}
    public static Prepared prepare(NativeSegmentedDoorEntity owner){
        // A worker may draw an already supplied pure context, but never performs discovery here.
        if(!Minecraft.getInstance().isSameThread()||owner.isRemoved()||owner.inert()||owner.topOffset()!=0||!(owner.getLevel() instanceof ClientLevel world))return NONE;
        var source=owner.publication();if(source==null||!source.visual().ready())return NONE;BlockPos pos=owner.getBlockPos();synchronized(WORLDS){
            var cache=WORLDS.get(world);if(cache==null){cache=new SegmentedGatePreparedCache<>(BUDGET,LIMIT);WORLDS.put(world,cache);if(WORLDS.size()>2){var iterator=WORLDS.keySet().iterator();while(iterator.hasNext())if(iterator.next()!=world){iterator.remove();break;}}}
            Prepared found=cache.get(owner,pos.getX(),pos.getY(),pos.getZ(),source);if(found!=null&&found.valid(owner))return found;builds++;
            var visual=source.visual();int[] own=styles(visual);boolean any=false,hasGate=false;for(int row=0;row<own.length;row++){hasGate|=own[row]>=0;any|=NativeSegmentedArtwork.complex(visual.texture(row));}
            if(!any){cache.put(owner,pos.getX(),pos.getY(),pos.getZ(),source,NONE,NONE.bytes());return NONE;}
            if(loaded(world,pos)!=owner)return NONE;var right=owner.facing().getClockWise();var left=hasGate&&owner.column()>0?loaded(world,pos.relative(right,-1)):null;var next=hasGate&&owner.column()+1<owner.columnsCount()?loaded(world,pos.relative(right)):null;boolean joinLeft=driver(owner,left,-1),joinRight=driver(owner,next,1);
            SegmentedGateContext context=new SegmentedGateContext(visual.height(),visual.width(),visual.column(),visual.settings().direction,visual.settings().straight,visual.settings().depthOffset(),source.motion().motionIdentity()!=null,(joinLeft?1:0)|(joinRight?2:0),((joinLeft&&movingDriver(owner,left)?1:0)|(joinRight&&movingDriver(owner,next)?2:0)),own,joinLeft?styles(left.publication().visual()):null,joinRight?styles(next.publication().visual()):null);
            found=new Prepared(context,owner,joinLeft?left:null,joinRight?next:null);cache.put(owner,pos.getX(),pos.getY(),pos.getZ(),source,found,found.bytes());return found;
        }
    }
    private NativeSegmentedGateContexts(){}
}
