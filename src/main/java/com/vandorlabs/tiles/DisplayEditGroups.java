package com.vandorlabs.tiles;

import com.vandorlabs.surface.JoinedRectangles;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;
import java.util.function.Predicate;

/** Stable authored rectangles: editing a signal band cannot disconnect its own editor. */
public final class DisplayEditGroups {
    private static boolean matching(TileEntityAnimatedScreenSelector a,TileEntityAnimatedScreenSelector b){
        return DisplayJoinGeometry.sameShape(a,b)&&a.sharedArtworkTexture()==b.sharedArtworkTexture()
            &&a.frameAppearance.displayFit==b.frameAppearance.displayFit
            &&a.frameAppearance.split==b.frameAppearance.split
            &&(!a.frameAppearance.split||a.frameAppearance.splitTop==b.frameAppearance.splitTop&&a.frameAppearance.splitBottom==b.frameAppearance.splitBottom);
    }
    public static List<TileEntityAnimatedScreenSelector> members(TileEntityAnimatedScreenSelector first){
        List<TileEntityAnimatedScreenSelector> single=Collections.singletonList(first);World world=first.getWorld();
        if(world==null||!world.isBlockLoaded(first.getPos(),false)||!TileEntityAnimatedScreenSelector.supportsActivation(first.getBlockType())||!DisplayJoinGeometry.supportsJoining(first)||first.sharedArtworkTexture()<0)return single;
        IBlockState state=world.getBlockState(first.getPos());DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(first,state);
        Map<BlockPos,TileEntityAnimatedScreenSelector> found=new LinkedHashMap<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();found.put(first.getPos(),first);queue.add(first.getPos());
        while(!queue.isEmpty()){
            BlockPos at=queue.removeFirst();
            for(BlockPos step:basis.steps){BlockPos next=at.add(step);if(found.containsKey(next)||!world.isBlockLoaded(next,false)||world.getBlockState(next)!=state)continue;TileEntity raw=world.getTileEntity(next);if(!(raw instanceof TileEntityAnimatedScreenSelector)||!matching(first,(TileEntityAnimatedScreenSelector)raw))continue;if(found.size()>=1023)return single;found.put(next,(TileEntityAnimatedScreenSelector)raw);queue.addLast(next);}
        }
        Map<Long,TileEntityAnimatedScreenSelector> cells=new HashMap<>();for(TileEntityAnimatedScreenSelector tile:found.values())cells.put(basis.cell(tile.getPos()),tile);
        long origin=basis.cell(first.getPos());int x=(int)(origin>>32),y=(int)origin;
        for(JoinedRectangles.Rect rect:JoinedRectangles.partition(cells.keySet()))if(x>=rect.x&&x<rect.x+rect.width&&y>=rect.y&&y<rect.y+rect.height){List<TileEntityAnimatedScreenSelector> result=new ArrayList<>();for(int row=0;row<rect.height;row++)for(int col=0;col<rect.width;col++)result.add(cells.get(JoinedRectangles.cell(rect.x+col,rect.y+row)));return result;}
        return single;
    }

    public static boolean wide(TileEntityAnimatedScreenSelector tile){
        List<TileEntityAnimatedScreenSelector> members=members(tile);if(members.size()<2)return false;
        DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(tile,tile.getWorld().getBlockState(tile.getPos()));int minX=Integer.MAX_VALUE,minY=minX,maxX=Integer.MIN_VALUE,maxY=maxX;
        for(TileEntityAnimatedScreenSelector member:members){long cell=basis.cell(member.getPos());int x=(int)(cell>>32),y=(int)cell;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}return maxX-minX>maxY-minY;
    }
    public static boolean apply(TileEntityAnimatedScreenSelector origin,int mode,int state,int texture,int playback,int speed,boolean inverted,Predicate<TileEntityAnimatedScreenSelector> allowed){
        if(!DisplayActivation.valid(mode,state,texture,playback,speed))return false;
        List<TileEntityAnimatedScreenSelector> targets=members(origin);for(TileEntityAnimatedScreenSelector target:targets)if(!allowed.test(target))return false;
        for(TileEntityAnimatedScreenSelector target:targets){target.setRedstoneInverted(inverted);target.configureActivation(mode,state,texture,playback,speed);}return true;
    }
    private DisplayEditGroups(){}
}
