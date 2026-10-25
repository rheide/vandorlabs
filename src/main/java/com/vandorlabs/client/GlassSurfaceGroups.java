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
    static final class Layout extends com.vandorlabs.render.PanelTextureMapping.Layout {
        Layout(int column,int row,int columns,int rows){super(column,row,columns,rows);}
        Layout(int column,int row,int columns,int rows,boolean flipU,boolean flipV){super(column,row,columns,rows,flipU,flipV);}
    }
    static final int MAX_LAYOUTS=8192;
    private static final class Cache extends WorldRenderListener {
        long revision;
        int tileRevision=-1;
        final Map<BlockPos,Layout> layouts=new LinkedHashMap<BlockPos,Layout>(128,.75F,true){protected boolean removeEldestEntry(Map.Entry<BlockPos,Layout> entry){return size()>MAX_LAYOUTS;}};
        final Map<BlockPos,Layout> mainLayouts=new LinkedHashMap<BlockPos,Layout>(128,.75F,true){protected boolean removeEldestEntry(Map.Entry<BlockPos,Layout> entry){return size()>MAX_LAYOUTS;}};
        void clear(){synchronized(GlassSurfaceGroups.class){layouts.clear();mainLayouts.clear();}}
        @Override public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags){clear();}
        @Override public void markBlockRangeForRenderUpdate(int x1,int y1,int z1,int x2,int y2,int z2){clear();}
    }
    private static final Map<World,Cache> CACHES=new WeakHashMap<>();
    static synchronized void invalidate(World world){Cache cache=CACHES.get(world);if(cache!=null)cache.clear();}
    static synchronized void unload(World world){Cache cache=CACHES.remove(world);if(cache!=null)world.removeEventListener(cache);}
    static Layout layout(TileEntity tile){return layout(tile,false);}
    static Layout mainLayout(TileEntity tile){return layout(tile,true);}
    private static synchronized Layout layout(TileEntity tile,boolean main){
        World world=tile.getWorld();Cache cache=CACHES.get(world);
        if(cache==null){cache=new Cache();CACHES.put(world,cache);world.addEventListener(cache);}
        // Captured vehicle worlds can replace their local structure without ordinary block events.
        int tileRevision=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).tileRevision:-1;
        if(cache.tileRevision!=tileRevision || cache.revision!=GlassMaterials.revision()){cache.tileRevision=tileRevision;cache.revision=GlassMaterials.revision();cache.clear();}
        Map<BlockPos,Layout> layouts=main?cache.mainLayouts:cache.layouts;
        Layout found=layouts.get(tile.getPos());if(found!=null)return found;
        IBlockState state=world.getBlockState(tile.getPos());boolean diagonal=state.getBlock() instanceof BlockProgrammableWall && ((BlockProgrammableWall)state.getBlock()).isDiagonalShape();
        EnumFacing facing=PanelJoins.facing(state),right=facing.rotateY(),up=diagonal && ((TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight()?facing.getOpposite():EnumFacing.UP;
        Set<BlockPos> members=new LinkedHashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();members.add(tile.getPos());queue.add(tile.getPos());
        if(main || join(tile))while(!queue.isEmpty() && members.size()<LoadedPlaneConnections.LIMIT){
            BlockPos current=queue.removeFirst();List<BlockPos> candidates=new ArrayList<>();candidates.add(current.offset(right));candidates.add(current.offset(right.getOpposite()));
            if(diagonal)for(boolean positive:new boolean[]{false,true})candidates.addAll(DiagonalPanelGeometry.rowCandidates(current,world.getBlockState(current),BlockProgrammableWall.geometry(world,current),positive));
            else {candidates.add(current.up());candidates.add(current.down());}
            for(BlockPos next:candidates){if(members.contains(next) || !world.isBlockLoaded(next) || !compatible(world.getTileEntity(current),next,main))continue;members.add(next);queue.add(next);if(members.size()>=LoadedPlaneConnections.LIMIT)break;}
        }
        BlockPos first=Collections.min(members);EnumFacing basis=PanelJoins.facing(world.getBlockState(first));right=basis.rotateY();up=diagonal && ((TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight()?basis.getOpposite():EnumFacing.UP;
        final EnumFacing horizontal=right,vertical=up;
        Map<BlockPos,com.vandorlabs.render.WallGroupPolicy.Layout> partition=com.vandorlabs.render.WallGroupPolicy.partition(members,p->PanelPlane.axis(p,horizontal),p->PanelPlane.axis(p,vertical),LoadedPlaneConnections.LIMIT);
        for(Map.Entry<BlockPos,com.vandorlabs.render.WallGroupPolicy.Layout> entry:partition.entrySet()){
            BlockPos p=entry.getKey();com.vandorlabs.render.WallGroupPolicy.Layout part=entry.getValue();EnumFacing local=PanelJoins.facing(world.getBlockState(p));
            boolean capped=members.size()>=LoadedPlaneConnections.LIMIT;
            layouts.put(p,new Layout(part.column,part.row,part.columns,part.rows,!capped&&local.rotateY()!=right,!capped&&diagonal&&((TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight()&&local.getOpposite()!=up));
        }
        return layouts.get(tile.getPos());
    }
    private static boolean join(TileEntity tile){return tile instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)tile).isJoin():((TileEntityAnimatedScreenSelector)tile).isGlassTextureJoin();}
    private static boolean compatible(TileEntity root,BlockPos next,boolean main){
        if(root==null)return false;World world=root.getWorld();IBlockState a=world.getBlockState(root.getPos()),b=world.getBlockState(next);TileEntity other=world.getTileEntity(next);
        if(a.getBlock()!=b.getBlock() || other==null || !main && !join(other))return false;
        if(root instanceof TileEntityProgrammableGlass){TileEntityProgrammableGlass x=(TileEntityProgrammableGlass)root,y=(TileEntityProgrammableGlass)other;
            return x.getMaterial()==y.getMaterial() && x.getTint()==y.getTint() && x.getTextureLayout()==y.getTextureLayout() && a.getValue(BlockGlassWall.ROTATED).equals(b.getValue(BlockGlassWall.ROTATED)) && a.getValue(BlockGlassWall.DEPTH).equals(b.getValue(BlockGlassWall.DEPTH));}
        TileEntityAnimatedScreenSelector x=(TileEntityAnimatedScreenSelector)root,y=(TileEntityAnimatedScreenSelector)other;
        if(main){
            if(!((BlockProgrammableWall)a.getBlock()).isDiagonalShape())return com.vandorlabs.render.WallGroupPolicy.main(flatSurface(a,x),flatSurface(b,y));
            if(x.getHousingTexture()!=y.getHousingTexture() || x.getMainTextureLayout()!=y.getMainTextureLayout())return false;
        }
        else if(x.getGlassMaterial()!=y.getGlassMaterial() || x.getGlassTint()!=y.getGlassTint() || x.getGlassTextureLayout()!=y.getGlassTextureLayout())return false;
        if(a.getValue(BlockProgrammableWall.FACING).getAxis()!=b.getValue(BlockProgrammableWall.FACING).getAxis())return false;
        if(!((BlockProgrammableWall)a.getBlock()).isDiagonalShape())return a.getValue(BlockProgrammableWall.FACING)==b.getValue(BlockProgrammableWall.FACING) && a.getValue(BlockProgrammableWall.DEPTH).equals(b.getValue(BlockProgrammableWall.DEPTH));
        int mode=BlockProgrammableWall.geometry(world,root.getPos());
        return mode==BlockProgrammableWall.geometry(world,next) && x.getDiagonalFill()==y.getDiagonalFill()
                && (DiagonalPanelGeometry.samePlane(root.getPos(),a,next,b,mode) || DiagonalPanelGeometry.rowsMeet(root.getPos(),a,next,b,mode));
    }
    static com.vandorlabs.render.WallGroupPolicy.Surface flatSurface(IBlockState state,TileEntityAnimatedScreenSelector tile){
        BlockProgrammableWall block=(BlockProgrammableWall)state.getBlock();
        return new com.vandorlabs.render.WallGroupPolicy.Surface(block,block.isPortholeShape(),tile.isJoinPortholes(),state.getValue(BlockProgrammableWall.FACING).getIndex(),state.getValue(BlockProgrammableWall.DEPTH),tile.getPortholeShape(),tile.getHousingTexture(),tile.getMainTextureLayout());
    }
    private GlassSurfaceGroups(){}
}
