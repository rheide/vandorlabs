package com.vandorlabs.neoforge.client;

import com.vandorlabs.surface.JoinedRectangles;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.render.HousingFacePlane;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Workers enqueue positions; loaded-world traversal/publication stays on the client thread. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class JoinedCubeSnapshots {
    private record Request(long generation,BlockPos pos){}
    private static final Map<BlockPos,Request> PENDING=new LinkedHashMap<>();
    private static ClientLevel lastWorld;
    private static volatile long generation;
    private static volatile boolean active;
    private record Signature(int main,int layout,boolean enabled,int f0,int f1,int f2,int f3,int f4,int f5,Direction facing){}
    private static final Map<ProgrammableBlockEntity,Signature> SEEN=new WeakHashMap<>();
    private static long scans,publications,requests,samples,invalidations;
    public record Stats(long requests,long samples,long scans,long publications,long invalidations,int queued){}
    public static synchronized Stats stats(){return new Stats(requests,samples,scans,publications,invalidations,PENDING.size());}
    private static final HousingFacePlane UNIT=new HousingFacePlane(new int[3],new int[]{1,1,1});
    public static long scans(){return scans;}
    public static long publications(){return publications;}
    public static synchronized void request(BlockPos pos){
        if(active&&PENDING.size()<4096){requests++;BlockPos at=pos.immutable();PENDING.put(at,new Request(generation,at));}
    }
    private static synchronized Request take(){var iterator=PENDING.entrySet().iterator();if(!iterator.hasNext())return null;var result=iterator.next().getValue();iterator.remove();return result;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var world=Minecraft.getInstance().level;
        if(world!=lastWorld){clear();lastWorld=world;active=world!=null;}
        if(world==null)return;
        for(int budget=0;budget<4;budget++){var request=take();if(request==null)return;if(request.generation==generation)sample(world,request.pos);}
    }
    private static synchronized void clear(){active=false;PENDING.clear();SEEN.clear();generation++;}
    @SubscribeEvent public static void load(net.neoforged.neoforge.event.level.LevelEvent.Load event){if(event.getLevel() instanceof ClientLevel world){clear();lastWorld=world;active=true;}}
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof ClientLevel&&event.getLevel()==lastWorld){clear();lastWorld=null;}}
    public static synchronized int queued(){return PENDING.size();}
    private static int material(ProgrammableBlockEntity tile,Direction face){
        var settings=tile.settings();int local=ProgrammableModel.localFace(face,tile.getBlockState().getValue(ProgrammableBlock.FACING));
        return settings.facesEnabled&&settings.face(local)>=0?settings.face(local):settings.mainTexture;
    }
    private static boolean exposed(ClientLevel world,BlockPos pos,Direction face){
        BlockPos adjacent=pos.relative(face);
        return !world.hasChunkAt(adjacent)||!world.getBlockState(adjacent).isSolidRender(world,adjacent)||!world.getBlockState(adjacent).isCollisionShapeFullBlock(world,adjacent);
    }
    private static boolean matches(ClientLevel world,ProgrammableBlockEntity root,BlockPos pos,Direction face){
        if(!world.hasChunkAt(pos)||!world.getBlockState(pos).is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get())||!exposed(world,pos,face))return false;
        return world.getBlockEntity(pos) instanceof ProgrammableBlockEntity tile&&tile.getBlockState().getValue(ProgrammableBlock.FACING)==root.getBlockState().getValue(ProgrammableBlock.FACING)
            &&tile.settings().layout==root.settings().layout&&material(tile,face)==material(root,face);
    }
    public static void sample(ClientLevel world,BlockPos pos){
        if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Joined world sampling outside client thread");
        if(!world.hasChunkAt(pos)||!world.getBlockState(pos).is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get())||!(world.getBlockEntity(pos) instanceof ProgrammableBlockEntity root))return;
        samples++;var state=root.settings();Signature signature=new Signature(state.mainTexture,state.layout,state.facesEnabled,state.face(0),state.face(1),state.face(2),state.face(3),state.face(4),state.face(5),root.getBlockState().getValue(ProgrammableBlock.FACING));
        Signature previous=SEEN.put(root,signature);
        if(previous!=null&&!previous.equals(signature)){invalidations++;Minecraft.getInstance().levelRenderer.setBlocksDirty(pos.getX()-16,pos.getY()-16,pos.getZ()-16,pos.getX()+16,pos.getY()+16,pos.getZ()+16);}
        Map<BlockPos,HousingFacePlane[]> next=new HashMap<>();next.put(pos,new HousingFacePlane[6]);
        if(state.layout!=1)for(Direction face:Direction.values())if(exposed(world,pos,face))discover(world,root,face,next);
        for(var entry:next.entrySet())if(world.hasChunkAt(entry.getKey())&&world.getBlockEntity(entry.getKey()) instanceof ProgrammableBlockEntity tile){
            HousingFacePlane[] planes=entry.getValue();boolean joined=false;
            for(var plane:planes)if(plane!=null&&plane.size(0)*plane.size(1)*plane.size(2)>1)joined=true;
            if(joined)for(Direction face:Direction.values())if(planes[face.get3DDataValue()]==null&&exposed(world,entry.getKey(),face))planes[face.get3DDataValue()]=UNIT;
            if(tile.publishJoined(joined?new JoinedCubeFaces(planes):JoinedCubeFaces.NONE))publications++;
        }
    }
    private static void discover(ClientLevel world,ProgrammableBlockEntity root,Direction face,Map<BlockPos,HousingFacePlane[]> next){
        scans++;Direction u=face.getAxis()==Direction.Axis.X?Direction.SOUTH:Direction.EAST,v=face.getAxis()==Direction.Axis.Y?Direction.SOUTH:Direction.UP;
        Set<BlockPos> members=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(root.getBlockPos());
        while(!queue.isEmpty()&&members.size()<=256){BlockPos at=queue.removeFirst();if(members.contains(at)||!matches(world,root,at,face))continue;
            members.add(at);queue.add(at.relative(u));queue.add(at.relative(u.getOpposite()));queue.add(at.relative(v));queue.add(at.relative(v.getOpposite()));}
        if(members.size()>256){for(BlockPos at:members)planes(world,at,next)[face.get3DDataValue()]=UNIT;return;}
        Map<Long,BlockPos> cells=new HashMap<>();for(BlockPos at:members)cells.put(JoinedRectangles.cell(axis(at,u),axis(at,v)),at);
        for(var rectangle:JoinedRectangles.partition(cells.keySet()))for(int y=0;y<rectangle.height;y++)for(int x=0;x<rectangle.width;x++){
            BlockPos at=cells.get(JoinedRectangles.cell(rectangle.x+x,rectangle.y+y));int column=x%16,row=y%16;
            int[] min={-u.getStepX()*column-v.getStepX()*row,-u.getStepY()*column-v.getStepY()*row,-u.getStepZ()*column-v.getStepZ()*row};
            int[] size={1,1,1};size[axis(u)]=Math.min(16,rectangle.width-x/16*16);size[axis(v)]=Math.min(16,rectangle.height-y/16*16);
            planes(world,at,next)[face.get3DDataValue()]=new HousingFacePlane(min,size);
        }
    }
    private static HousingFacePlane[] planes(ClientLevel world,BlockPos pos,Map<BlockPos,HousingFacePlane[]> next){return next.computeIfAbsent(pos,at->((ProgrammableBlockEntity)world.getBlockEntity(at)).joinedFaces().copy());}
    private static int axis(Direction direction){return direction.getAxis()==Direction.Axis.X?0:direction.getAxis()==Direction.Axis.Y?1:2;}
    private static int axis(BlockPos pos,Direction direction){return pos.getX()*direction.getStepX()+pos.getY()*direction.getStepY()+pos.getZ()*direction.getStepZ();}
    private JoinedCubeSnapshots(){}
}
