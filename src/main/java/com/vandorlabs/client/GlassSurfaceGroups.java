package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Loaded, coplanar material groups. Holes partition into complete rectangles. */
final class GlassSurfaceGroups {
    static final class Layout {
        final int column,row,columns,rows;
        Layout(int column,int row,int columns,int rows){this.column=column;this.row=row;this.columns=columns;this.rows=rows;}
        String key(){return column+":"+row+":"+columns+":"+rows;}
    }
    private static final class Cache {long tick=Long.MIN_VALUE,revision;final Map<BlockPos,Layout> layouts=new HashMap<>();}
    private static final Map<World,Cache> CACHES=new WeakHashMap<>();
    static synchronized Layout layout(TileEntity tile){
        World world=tile.getWorld();Cache cache=CACHES.computeIfAbsent(world,w->new Cache());
        if(cache.tick!=world.getTotalWorldTime() || cache.revision!=GlassMaterials.revision()){cache.tick=world.getTotalWorldTime();cache.revision=GlassMaterials.revision();cache.layouts.clear();}
        Layout found=cache.layouts.get(tile.getPos());if(found!=null)return found;
        IBlockState state=world.getBlockState(tile.getPos());boolean diagonal=tile instanceof TileEntityAnimatedScreenSelector;
        EnumFacing facing=PanelJoins.facing(state),right=facing.rotateY(),up=diagonal && ((TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight()?facing.getOpposite():EnumFacing.UP;
        Set<BlockPos> members=new LinkedHashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();members.add(tile.getPos());queue.add(tile.getPos());
        if(join(tile))while(!queue.isEmpty() && members.size()<LoadedPlaneConnections.LIMIT){
            BlockPos current=queue.removeFirst();List<BlockPos> candidates=new ArrayList<>();candidates.add(current.offset(right));candidates.add(current.offset(right.getOpposite()));
            if(diagonal)for(boolean positive:new boolean[]{false,true})candidates.addAll(DiagonalPanelGeometry.rowCandidates(current,world.getBlockState(current),BlockProgrammableWall.geometry(world,current),positive));
            else {candidates.add(current.up());candidates.add(current.down());}
            for(BlockPos next:candidates){if(members.contains(next) || !world.isBlockLoaded(next) || !compatible(tile,next))continue;members.add(next);queue.add(next);if(members.size()>=LoadedPlaneConnections.LIMIT)break;}
        }
        Map<Long,BlockPos> cells=new HashMap<>();for(BlockPos p:members)cells.put(PortholeRectangles.cell(PanelPlane.axis(p,right),PanelPlane.axis(p,up)),p);
        if(members.size()>=LoadedPlaneConnections.LIMIT){for(BlockPos p:members)cache.layouts.put(p,new Layout(0,0,1,1));}
        else for(PortholeRectangles.Rect rect:PortholeRectangles.partition(cells.keySet()))for(int y=0;y<rect.height;y++)for(int x=0;x<rect.width;x++)cache.layouts.put(cells.get(PortholeRectangles.cell(rect.x+x,rect.y+y)),new Layout(x,y,rect.width,rect.height));
        return cache.layouts.get(tile.getPos());
    }
    private static boolean join(TileEntity tile){return tile instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)tile).isJoin():((TileEntityAnimatedScreenSelector)tile).isGlassTextureJoin();}
    private static boolean compatible(TileEntity root,BlockPos next){
        World world=root.getWorld();IBlockState a=world.getBlockState(root.getPos()),b=world.getBlockState(next);TileEntity other=world.getTileEntity(next);
        if(a.getBlock()!=b.getBlock() || other==null || !join(other))return false;
        if(root instanceof TileEntityProgrammableGlass){TileEntityProgrammableGlass x=(TileEntityProgrammableGlass)root,y=(TileEntityProgrammableGlass)other;
            return x.getMaterial()==y.getMaterial() && x.getTint()==y.getTint() && x.isStretch()==y.isStretch() && a.getValue(BlockGlassWall.ROTATED).equals(b.getValue(BlockGlassWall.ROTATED)) && a.getValue(BlockGlassWall.DEPTH).equals(b.getValue(BlockGlassWall.DEPTH));}
        TileEntityAnimatedScreenSelector x=(TileEntityAnimatedScreenSelector)root,y=(TileEntityAnimatedScreenSelector)other;
        int mode=BlockProgrammableWall.geometry(world,root.getPos());
        return x.getGlassMaterial()==y.getGlassMaterial() && x.getGlassTint()==y.getGlassTint() && x.isGlassStretch()==y.isGlassStretch() && mode==BlockProgrammableWall.geometry(world,next)
                && x.getDiagonalFill()==y.getDiagonalFill() && a.getValue(BlockProgrammableWall.FACING)==b.getValue(BlockProgrammableWall.FACING)
                && DiagonalPanelGeometry.samePlane(root.getPos(),a,next,b,mode);
    }
    private GlassSurfaceGroups(){}
}
