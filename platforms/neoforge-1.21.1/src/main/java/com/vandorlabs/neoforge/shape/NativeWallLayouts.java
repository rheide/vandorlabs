package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import com.vandorlabs.surface.JoinedRectangles;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

/** Cosmetic client groups. Descriptors live only on loaded tiles and never retain tiles or levels. */
public final class NativeWallLayouts {
    private static final int LIMIT=4096;
    static final class Group {
        final Map<Long,NativePanelLayout> layouts;
        final boolean capped;
        boolean valid=true,queued;
        Group(Map<Long,NativePanelLayout> layouts,boolean capped){this.layouts=Map.copyOf(layouts);this.capped=capped;}
    }
    /** Local invalidation is safe during NBT loading and chunk unload; all world work stays deferred. */
    public static void invalidate(NativeWallEntity host){Group old=host.textureGroup;if(old!=null)old.valid=false;}
    public static void invalidateAt(Level level,BlockPos pos){if(NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeWallEntity host)invalidate(host);}
    static NativePanelLayout layout(NativeWallEntity host){
        Level level=host.getLevel();if(level==null||!level.isClientSide)return NativePanelLayout.UNIT;
        Group old=host.textureGroup;
        if(old!=null&&old.valid&&!membershipValid(host,old))old.valid=false;
        if(old!=null&&!old.valid&&!old.queued){old.queued=true;for(long packed:old.layouts.keySet()){BlockPos pos=BlockPos.of(packed);if(NativeFluidWorld.loaded(level,pos)&&level.getBlockEntity(pos) instanceof NativeWallEntity member)NativeFluidWorld.queue(member);}}
        if(host.settings().layout==0){host.textureGroup=null;return NativePanelLayout.UNIT;}
        if(old!=null&&old.valid)return old.layouts.getOrDefault(host.getBlockPos().asLong(),NativePanelLayout.UNIT);
        return capture(host);
    }
    private static boolean membershipValid(NativeWallEntity root,Group group){
        Direction right=root.getBlockState().getValue(NativeFlatWallBlock.FACING).getClockWise();BlockPos pos=root.getBlockPos();
        for(Direction direction:new Direction[]{right,right.getOpposite(),Direction.UP,Direction.DOWN}){
            BlockPos next=pos.relative(direction);boolean contains=group.layouts.containsKey(next.asLong()),compatible=compatible(root,next)!=null;
            if(contains&&!compatible||!contains&&compatible&&!group.capped)return false;
        }
        return true;
    }
    private static NativeWallEntity compatible(NativeWallEntity root,BlockPos pos){
        Level level=root.getLevel();if(!NativeFluidWorld.loaded(level,pos))return null;
        var state=level.getBlockState(pos);var base=root.getBlockState();
        if(state.getBlock()!=base.getBlock()||!(level.getBlockEntity(pos) instanceof NativeWallEntity other)||other.isRemoved())return null;
        if(state.getValue(NativeFlatWallBlock.FACING)!=base.getValue(NativeFlatWallBlock.FACING)||!state.getValue(NativeFlatWallBlock.DEPTH).equals(base.getValue(NativeFlatWallBlock.DEPTH)))return null;
        return other.settings().mainTexture==root.settings().mainTexture&&other.settings().layout==root.settings().layout?other:null;
    }
    private static NativePanelLayout capture(NativeWallEntity root){
        Level level=root.getLevel();Direction right=root.getBlockState().getValue(NativeFlatWallBlock.FACING).getClockWise();
        Map<BlockPos,NativeWallEntity> members=new LinkedHashMap<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();members.put(root.getBlockPos(),root);queue.add(root.getBlockPos());
        while(!queue.isEmpty()&&members.size()<LIMIT){BlockPos pos=queue.remove();for(Direction direction:new Direction[]{right,right.getOpposite(),Direction.UP,Direction.DOWN}){
            BlockPos next=pos.relative(direction);if(members.containsKey(next))continue;NativeWallEntity other=compatible(root,next);if(other==null)continue;
            members.put(next,other);queue.add(next);if(members.size()>=LIMIT)break;
        }}
        boolean capped=members.size()>=LIMIT;Map<Long,NativePanelLayout> layouts=new HashMap<>();
        if(capped)for(BlockPos pos:members.keySet())layouts.put(pos.asLong(),NativePanelLayout.UNIT);
        else{
            Map<Long,BlockPos> cells=new HashMap<>();for(BlockPos pos:members.keySet())cells.put(JoinedRectangles.cell(pos.getX()*right.getStepX()+pos.getZ()*right.getStepZ(),pos.getY()),pos);
            for(var rect:JoinedRectangles.partition(cells.keySet()))for(int y=0;y<rect.height;y++)for(int x=0;x<rect.width;x++){
                BlockPos pos=cells.get(JoinedRectangles.cell(rect.x+x,rect.y+y));layouts.put(pos.asLong(),new NativePanelLayout(x,y,rect.width,rect.height,false,false));
            }
        }
        Group group=new Group(layouts,capped);
        for(var entry:members.entrySet()){NativeWallEntity member=entry.getValue();member.textureGroup=group;if(member!=root)NativeFluidWorld.queue(member);}
        return group.layouts.get(root.getBlockPos().asLong());
    }
    private NativeWallLayouts(){}
}
