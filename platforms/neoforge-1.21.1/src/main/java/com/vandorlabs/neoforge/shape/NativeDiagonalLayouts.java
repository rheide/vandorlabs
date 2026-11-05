package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.render.DiagonalPanelContact;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Loaded diagonal artwork groups. Only immutable local coordinates survive discovery. */
final class NativeDiagonalLayouts {
    static NativePanelLayout layout(NativeWallEntity root){
        Level level=root.getLevel();if(level==null||!level.isClientSide)return NativePanelLayout.UNIT;
        var old=root.textureGroup;
        if(old!=null&&old.valid&&!membershipValid(root,old))old.valid=false;
        if(old!=null&&!old.valid&&!old.queued){old.queued=true;for(long packed:old.layouts.keySet()){BlockPos pos=BlockPos.of(packed);if(NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeWallEntity member)NativeFluidWorld.queue(member);}}
        if(root.settings().layout==0){root.textureGroup=null;return NativePanelLayout.UNIT;}
        if(old!=null&&old.valid)return old.layouts.getOrDefault(root.getBlockPos().asLong(),NativePanelLayout.UNIT);
        Map<BlockPos,NativeWallEntity> members=new LinkedHashMap<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();members.put(root.getBlockPos(),root);queue.add(root.getBlockPos());
        while(!queue.isEmpty()&&members.size()<NativePanelGroups.LIMIT){
            BlockPos current=queue.removeFirst();NativeWallEntity host=members.get(current);
            for(BlockPos next:candidates(host)){
                if(members.containsKey(next))continue;NativeWallEntity other=compatible(host,next);if(other==null)continue;members.put(next,other);queue.add(next);if(members.size()>=NativePanelGroups.LIMIT)break;
            }
        }
        BlockPos first=Collections.min(members.keySet());Direction basis=members.get(first).getBlockState().getValue(NativePanelBlock.FACING),right=basis.getClockWise();boolean half=NativeDiagonalWallTopology.mode(level,root.getBlockPos())==2;Direction up=half?basis.getOpposite():Direction.UP;
        Map<Long,NativePanelLayout> layouts=NativePanelGroups.partition(members.keySet(),right,up,members.size()>=NativePanelGroups.LIMIT,
                pos->members.get(pos).getBlockState().getValue(NativePanelBlock.FACING).getClockWise()!=right,
                pos->half&&members.get(pos).getBlockState().getValue(NativePanelBlock.FACING).getOpposite()!=up);
        var group=new NativeWallLayouts.Group(NativePanelGroups.Mode.MATERIAL,layouts,members.size()>=NativePanelGroups.LIMIT);
        for(var entry:members.entrySet()){
            NativeWallEntity member=entry.getValue();var previous=member.textureGroup;
            if(previous!=null&&previous!=group){previous.valid=false;for(long packed:previous.layouts.keySet()){BlockPos pos=BlockPos.of(packed);if(!members.containsKey(pos)&&NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeWallEntity excluded)NativeFluidWorld.queue(excluded);}}
            member.textureGroup=group;if(member!=root)NativeFluidWorld.queue(member);
        }
        return layouts.getOrDefault(root.getBlockPos().asLong(),NativePanelLayout.UNIT);
    }
    private static boolean membershipValid(NativeWallEntity root,NativeWallLayouts.Group group){
        for(BlockPos next:candidates(root)){boolean contains=group.layouts.containsKey(next.asLong()),matches=compatible(root,next)!=null;if(contains&&!matches||!contains&&matches&&!group.capped)return false;}return true;
    }
    private static List<BlockPos> candidates(NativeWallEntity host){
        BlockPos pos=host.getBlockPos();Direction facing=host.getBlockState().getValue(NativePanelBlock.FACING),right=facing.getClockWise();int mode=NativeDiagonalWallTopology.mode(host.getLevel(),pos);
        List<BlockPos> result=new ArrayList<>(8);result.add(pos.relative(right));result.add(pos.relative(right.getOpposite()));Direction row=mode==2?facing.getOpposite():Direction.UP,normal=mode==2?Direction.UP:facing.getOpposite();
        for(boolean positive:new boolean[]{false,true})for(int offset=-1;offset<=1;offset++)result.add(pos.relative(row,positive?1:-1).relative(normal,offset));return result;
    }
    private static NativeWallEntity compatible(NativeWallEntity root,BlockPos next){
        Level level=root.getLevel();if(!NativeFluidWorld.loaded(level,next))return null;BlockState a=root.getBlockState(),b=level.getBlockState(next);
        if(a.getBlock()!=b.getBlock()||!(level.getBlockEntity(next) instanceof NativeWallEntity other)||other.isRemoved())return null;
        if(root.settings().mainTexture!=other.settings().mainTexture||root.settings().layout!=other.settings().layout||root.wallSettings().fill!=other.wallSettings().fill)return null;
        Direction af=a.getValue(NativePanelBlock.FACING),bf=b.getValue(NativePanelBlock.FACING);if(af.getAxis()!=bf.getAxis())return null;
        int mode=NativeDiagonalWallTopology.mode(level,root.getBlockPos());if(mode!=NativeDiagonalWallTopology.mode(level,next))return null;
        BlockPos delta=next.subtract(root.getBlockPos());boolean ai=a.getValue(NativeDiagonalWallBlock.INVERTED),bi=b.getValue(NativeDiagonalWallBlock.INVERTED);double normal=NativeDiagonalWallTopology.axis(delta,af.getOpposite());
        if(DiagonalPanelContact.samePlane(mode,ai,bi,bf!=af,delta.getY(),normal))return other;
        int row=NativeDiagonalWallTopology.axis(delta,mode==2?af.getOpposite():Direction.UP);
        return Math.abs(row)==1&&NativeDiagonalWallTopology.axis(delta,af.getClockWise())==0&&DiagonalPanelContact.endsMeet(mode,ai,bi,bf!=af,delta.getY(),normal,row>0)?other:null;
    }
    private NativeDiagonalLayouts(){}
}
