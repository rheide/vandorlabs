package com.vandorlabs.segmented;

import java.util.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Derived window groups never become saved ownership or mutable runtime settings. */
public final class SegmentedDoorWindows {
    private static final Map<World,LinkedHashMap<BlockPos,Layout>> WORLDS=Collections.synchronizedMap(new WeakHashMap<>());
    public static final class Layout {
        public final int column,row,width,height;
        Layout(int column,int row,int width,int height){this.column=column;this.row=row;this.width=width;this.height=height;}
        @Override public int hashCode(){return ((column*31+row)*31+width)*31+height;}
        @Override public boolean equals(Object o){if(!(o instanceof Layout))return false;Layout b=(Layout)o;return column==b.column&&row==b.row&&width==b.width&&height==b.height;}
    }
    private static final Layout SINGLE=new Layout(0,0,1,1);
    public static void invalidate(World world){if(world!=null)WORLDS.remove(world);}
    public static Layout layout(SegmentedDoorTile tile){
        if(!tile.settings.window||!tile.settings.join||tile.getWorld()==null)return SINGLE;
        World world=tile.getWorld();LinkedHashMap<BlockPos,Layout> cache=WORLDS.computeIfAbsent(world,w->new LinkedHashMap<>(32,.75F,true));Layout ready=cache.get(tile.getPos());if(ready!=null)return ready;
        SegmentedDoorTile root=tile.root();EnumFacing right=tile.facing().rotateY();BlockPos origin=root.getPos().offset(right,-root.column);Set<BlockPos> seen=new HashSet<>();ArrayDeque<SegmentedDoorTile> queue=new ArrayDeque<>();List<SegmentedDoorTile> group=new ArrayList<>();seen.add(tile.getPos());queue.add(tile);
        int minColumn=tile.column,maxColumn=tile.column,minY=tile.getPos().getY(),maxY=minY;
        while(!queue.isEmpty()){
            SegmentedDoorTile current=queue.removeFirst();group.add(current);minColumn=Math.min(minColumn,current.column);maxColumn=Math.max(maxColumn,current.column);minY=Math.min(minY,current.getPos().getY());maxY=Math.max(maxY,current.getPos().getY());
            for(EnumFacing side:new EnumFacing[]{EnumFacing.UP,EnumFacing.DOWN,right,right.getOpposite()}){
                BlockPos p=current.getPos().offset(side);int column=(p.getX()-origin.getX())*right.getFrontOffsetX()+(p.getZ()-origin.getZ())*right.getFrontOffsetZ();
                if(column<0||column>=root.columns||p.getY()>origin.getY()||p.getY()<=origin.getY()-root.height||!seen.add(p))continue;
                SegmentedDoorTile next=SegmentedDoorTile.at(world,p);if(next!=null&&next.facing()==tile.facing()&&next.settings.window&&next.settings.join&&next.settings.shape==tile.settings.shape)queue.add(next);
            }
        }
        for(SegmentedDoorTile member:group)cache.put(member.getPos().toImmutable(),new Layout(member.column-minColumn,member.getPos().getY()-minY,maxColumn-minColumn+1,maxY-minY+1));
        while(cache.size()>4096)cache.remove(cache.keySet().iterator().next());return cache.get(tile.getPos());
    }
    private SegmentedDoorWindows(){}
}
