package com.vandorlabs.neoforge.gear.track;

import com.vandorlabs.gear.track.core.*;
import com.vandorlabs.neoforge.gear.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Loaded-only discovery; a bounded complete component or no accepted assembly. */
public final class NativeTrackRuntime {
    private static final Direction[] SIDES={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static NativeTrackEntity at(Level world,BlockPos pos){return NativeGearFormation.loaded(world,pos)&&world.getBlockEntity(pos) instanceof NativeTrackEntity track?track:null;}
    private static BlockPos step(BlockPos pos,Direction mount,Direction side){return pos.offset(NativeGearOrientation.vector(mount,new BlockPos(side.getNormal())));}
    public static void tick(Level world,BlockPos pos,BlockState state,NativeGearEntity base){NativeGearEntity.tick(world,pos,state,base);if(!(world instanceof ServerLevel)||!(base instanceof NativeTrackEntity track)||track.inert()||!track.formationReady())return;long now=world.getGameTime();if(now<track.nextCheck())return;if(track.trackPending()||track.trackLeader()==null||track.trackLeader().equals(pos)||at(world,track.trackLeader())==null)rebuild(track);}
    public static void around(Level world,BlockPos changed){if(world.isClientSide)return;var track=at(world,changed);if(track!=null)track.requestRebuild();for(var side:Direction.values()){track=at(world,changed.relative(side));if(track!=null)track.requestRebuild();}}
    public static void rebuild(NativeTrackEntity start){var world=start.getLevel();if(!(world instanceof ServerLevel server)||!server.getServer().isSameThread()||at(world,start.getBlockPos())!=start)return;var origin=start.getBlockPos();var mount=start.mount();var facing=start.getBlockState().getValue(NativeGearBlock.FACING);var visited=new LinkedHashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();var nodes=new ArrayList<TrackTopology.Node>();queue.add(origin);visited.add(origin);String error="";
        while(!queue.isEmpty()&&error.isEmpty()){var pos=queue.removeFirst();var local=NativeGearOrientation.inverse(mount,pos.subtract(origin));nodes.add(new TrackTopology.Node(new TrackTopology.Cell(local.getX(),local.getY(),local.getZ()),start.style(),TrackTopology.Facing.valueOf(facing.name())));for(var side:SIDES){var next=step(pos,mount,side);if(!NativeGearFormation.loaded(world,next)){error="Load the complete Track boundary";break;}var neighbor=at(world,next);if(neighbor==null||neighbor.mount()!=mount||neighbor.style()!=start.style()||neighbor.getBlockState().getValue(NativeGearBlock.FACING)!=facing)continue;if(visited.add(next)){if(visited.size()>TrackTopology.MAX_TRACKS){error="More than 64 connected Tracks";break;}queue.add(next);}}}
        var affected=new LinkedHashSet<BlockPos>(visited);for(var pos:visited){var track=at(world,pos);if(track!=null)affected.addAll(track.trackMembers());}
        TrackTopology.Result result=error.isEmpty()?TrackTopology.analyze(nodes):null;if(result==null||!result.isValid()){for(var pos:affected){var track=at(world,pos);if(track!=null)track.installTrack(16,null,List.of(),false,result==null?error:result.error());}return;}
        var assembly=result.assembly();var leader=origin.offset(NativeGearOrientation.vector(mount,new BlockPos(assembly.leader().x,assembly.leader().y,assembly.leader().z)));boolean supported=false;for(var pos:visited)supported|=overhead(world,pos,mount)||sides(world,pos,mount,false)!=0;var members=List.copyOf(visited);
        for(var pos:affected)if(!visited.contains(pos)){var old=at(world,pos);if(old!=null)old.installTrack(16,null,List.of(),false,"Topology changed");}
        for(var part:assembly.parts()){var cell=part.cell();var pos=origin.offset(NativeGearOrientation.vector(mount,new BlockPos(cell.x,cell.y,cell.z)));var tile=at(world,pos);if(tile==null)continue;var above=pos.relative(mount.getOpposite());TrackVisualState.Mount support=TrackVisualState.Mount.NONE;if(overhead(world,pos,mount)){var arm=NativeGearFormation.at(world,above);support=arm!=null&&arm.definition().arm()?TrackVisualState.Mount.SOCKET:com.vandorlabs.neoforge.landing.NativeSlabSupport.top(world.getBlockState(above))?TrackVisualState.Mount.TOP_HIGH:TrackVisualState.Mount.TOP;}else if(pos.equals(leader)&&!supported)support=TrackVisualState.Mount.SOCKET;var visual=new TrackVisualState(part.beltMask(),support,sides(world,pos,mount,false),(part.chassisMask()&~part.beltMask())&10,sides(world,pos,mount,true));tile.installTrack(visual.key(),leader,pos.equals(leader)?members:List.of(),true,"");}
    }
    private static boolean overhead(Level world,BlockPos pos,Direction mount){var above=pos.relative(mount.getOpposite());return NativeGearFormation.loaded(world,above)&&TrackMounting.acceptsOverhead(world.getBlockState(above).isAir());}
    private static int sides(Level world,BlockPos pos,Direction mount,boolean lower){int mask=0;for(int n=0;n<4;n++){var side=step(pos,mount,SIDES[n]);if(!NativeGearFormation.loaded(world,side))continue;var state=world.getBlockState(side);if(com.vandorlabs.neoforge.landing.NativeSlabSupport.slab(state)){if(!lower||com.vandorlabs.neoforge.landing.NativeSlabSupport.bottom(state))mask|=1<<n;}}return mask;}
    private NativeTrackRuntime(){}
}
