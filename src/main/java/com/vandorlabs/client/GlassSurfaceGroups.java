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
        final boolean flipU,flipV;
        Layout(int column,int row,int columns,int rows){this(column,row,columns,rows,false,false);}
        Layout(int column,int row,int columns,int rows,boolean flipU,boolean flipV){this.column=column;this.row=row;this.columns=columns;this.rows=rows;this.flipU=flipU;this.flipV=flipV;}
        String key(){return column+":"+row+":"+columns+":"+rows+":"+flipU+":"+flipV;}
    }
    static final int MAX_LAYOUTS=8192;
    private static final class Cache extends WorldRenderListener {
        long revision;
        final Map<BlockPos,Layout> frames=new HashMap<>();
        final Map<BlockPos,Integer> frameMasks=new HashMap<>();
        int tileRevision=-1;
        final Map<BlockPos,Layout> layouts=new LinkedHashMap<BlockPos,Layout>(128,.75F,true){protected boolean removeEldestEntry(Map.Entry<BlockPos,Layout> entry){return size()>MAX_LAYOUTS;}};
        final Map<BlockPos,Layout> mainLayouts=new LinkedHashMap<BlockPos,Layout>(128,.75F,true){protected boolean removeEldestEntry(Map.Entry<BlockPos,Layout> entry){return size()>MAX_LAYOUTS;}};
        void clear(){synchronized(GlassSurfaceGroups.class){layouts.clear();mainLayouts.clear();frames.clear();frameMasks.clear();}}
        @Override public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags){clear();}
        @Override public void markBlockRangeForRenderUpdate(int x1,int y1,int z1,int x2,int y2,int z2){clear();}
    }
    private static final Map<World,Cache> CACHES=new WeakHashMap<>();
    static synchronized void invalidate(World world){Cache cache=CACHES.get(world);if(cache!=null)cache.clear();}
    static synchronized void unload(World world){Cache cache=CACHES.remove(world);if(cache!=null)world.removeEventListener(cache);}
    static Layout layout(TileEntity tile){return layout(tile,false,false);}
    static Layout frameLayout(TileEntity tile){return layout(tile,false,true);}
    /** Local contour edges are independent of rectangular artwork partitions. */
    static synchronized int frameMask(TileEntityProgrammableGlass tile){
        if(tile.frameAppearance.tile||!tile.isJoin())return 15;
        World world=tile.getWorld();Cache cache=CACHES.get(world);
        if(cache==null){cache=new Cache();CACHES.put(world,cache);world.addEventListener(cache);}
        int revision=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).tileRevision:-1;
        if(cache.tileRevision!=revision||cache.revision!=GlassMaterials.revision()){cache.tileRevision=revision;cache.revision=GlassMaterials.revision();cache.clear();}
        Integer old=cache.frameMasks.get(tile.getPos());if(old!=null)return old;
        EnumFacing right=PanelJoins.facing(world.getBlockState(tile.getPos())).rotateY();
        BlockPos p=tile.getPos();BlockPos[] next={p.offset(right.getOpposite()),p.offset(right),p.down(),p.up()};boolean[] joined=new boolean[4];int mask=0;
        for(int i=0;i<4;i++){joined[i]=world.isBlockLoaded(next[i])&&compatible(tile,next[i],false,true);if(!joined[i])mask|=1<<i;}
        for(int side=0;side<2;side++)for(int vertical=0;vertical<2;vertical++)if(joined[side]&&joined[2+vertical]){
            BlockPos diagonal=next[side].add(0,vertical==0?-1:1,0);
            if(!world.isBlockLoaded(diagonal)||!compatible(tile,diagonal,false,true))mask|=1<<(4+side+vertical*2);
        }
        if(cache.frameMasks.size()>=MAX_LAYOUTS)cache.frameMasks.clear();cache.frameMasks.put(p.toImmutable(),mask);return mask;
    }

    static Layout mainLayout(TileEntity tile){return layout(tile,true,false);}
    private static synchronized Layout layout(TileEntity tile,boolean main,boolean frame){
        World world=tile.getWorld();Cache cache=CACHES.get(world);
        if(cache==null){cache=new Cache();CACHES.put(world,cache);world.addEventListener(cache);}
        // Captured vehicle worlds can replace their local structure without ordinary block events.
        int tileRevision=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).tileRevision:-1;
        if(cache.tileRevision!=tileRevision || cache.revision!=GlassMaterials.revision()){cache.tileRevision=tileRevision;cache.revision=GlassMaterials.revision();cache.clear();}
        Map<BlockPos,Layout> layouts=frame?cache.frames:main?cache.mainLayouts:cache.layouts;
        if(layouts.size()>MAX_LAYOUTS)layouts.clear();
        Layout found=layouts.get(tile.getPos());if(found!=null)return found;
        IBlockState state=world.getBlockState(tile.getPos());boolean diagonal=state.getBlock() instanceof BlockProgrammableWall && ((BlockProgrammableWall)state.getBlock()).isDiagonalShape();
        EnumFacing facing=PanelJoins.facing(state),right=facing.rotateY(),up=diagonal && ((TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight()?facing.getOpposite():EnumFacing.UP;
        Set<BlockPos> members=new LinkedHashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();members.add(tile.getPos());queue.add(tile.getPos());
        if(main || join(tile))while(!queue.isEmpty() && members.size()<LoadedPlaneConnections.LIMIT){
            BlockPos current=queue.removeFirst();List<BlockPos> candidates=new ArrayList<>();candidates.add(current.offset(right));candidates.add(current.offset(right.getOpposite()));
            if(diagonal)for(boolean positive:new boolean[]{false,true})candidates.addAll(DiagonalPanelGeometry.rowCandidates(current,world.getBlockState(current),BlockProgrammableWall.geometry(world,current),positive));
            else {candidates.add(current.up());candidates.add(current.down());}
            for(BlockPos next:candidates){if(members.contains(next) || !world.isBlockLoaded(next) || !compatible(world.getTileEntity(current),next,main,frame))continue;members.add(next);queue.add(next);if(members.size()>=LoadedPlaneConnections.LIMIT)break;}
        }
        BlockPos first=Collections.min(members);EnumFacing basis=PanelJoins.facing(world.getBlockState(first));right=basis.rotateY();up=diagonal && ((TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight()?basis.getOpposite():EnumFacing.UP;
        Map<Long,BlockPos> cells=new HashMap<>();for(BlockPos p:members)cells.put(PortholeRectangles.cell(PanelPlane.axis(p,right),PanelPlane.axis(p,up)),p);
        if(members.size()>=LoadedPlaneConnections.LIMIT){for(BlockPos p:members)layouts.put(p,new Layout(0,0,1,1));}
        else for(PortholeRectangles.Rect rect:PortholeRectangles.partition(cells.keySet()))for(int y=0;y<rect.height;y++)for(int x=0;x<rect.width;x++){
            BlockPos p=cells.get(PortholeRectangles.cell(rect.x+x,rect.y+y));EnumFacing local=PanelJoins.facing(world.getBlockState(p));
            layouts.put(p,new Layout(x,y,rect.width,rect.height,local.rotateY()!=right,diagonal && ((TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight() && local.getOpposite()!=up));
        }
        return layouts.get(tile.getPos());
    }
    private static boolean join(TileEntity tile){return tile instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)tile).isJoin():((TileEntityAnimatedScreenSelector)tile).isGlassTextureJoin();}
    private static boolean compatible(TileEntity root,BlockPos next,boolean main,boolean frame){
        if(root==null)return false;World world=root.getWorld();IBlockState a=world.getBlockState(root.getPos()),b=world.getBlockState(next);TileEntity other=world.getTileEntity(next);
        if(a.getBlock()!=b.getBlock() || other==null || !main && !join(other))return false;
        if(root instanceof TileEntityProgrammableGlass){TileEntityProgrammableGlass x=(TileEntityProgrammableGlass)root,y=(TileEntityProgrammableGlass)other;
            if(frame)return x.isFramed()==y.isFramed()&&x.frameAppearance.front==y.frameAppearance.front&&x.frameAppearance.interior==y.frameAppearance.interior&&x.frameAppearance.exteriorChoice()==y.frameAppearance.exteriorChoice()&&x.frameAppearance.tile==y.frameAppearance.tile&&a.getValue(BlockGlassWall.ROTATED).equals(b.getValue(BlockGlassWall.ROTATED))&&a.getValue(BlockGlassWall.DEPTH).equals(b.getValue(BlockGlassWall.DEPTH));
            return x.getMaterial()==y.getMaterial() && x.getTint()==y.getTint() && x.getTextureLayout()==y.getTextureLayout() && a.getValue(BlockGlassWall.ROTATED).equals(b.getValue(BlockGlassWall.ROTATED)) && a.getValue(BlockGlassWall.DEPTH).equals(b.getValue(BlockGlassWall.DEPTH));}
        TileEntityAnimatedScreenSelector x=(TileEntityAnimatedScreenSelector)root,y=(TileEntityAnimatedScreenSelector)other;
        if(main){if(x.getHousingTexture()!=y.getHousingTexture() || x.getMainTextureLayout()!=y.getMainTextureLayout())return false;}
        else if(x.getGlassMaterial()!=y.getGlassMaterial() || x.getGlassTint()!=y.getGlassTint() || x.getGlassTextureLayout()!=y.getGlassTextureLayout())return false;
        if(a.getValue(BlockProgrammableWall.FACING).getAxis()!=b.getValue(BlockProgrammableWall.FACING).getAxis())return false;
        if(!((BlockProgrammableWall)a.getBlock()).isDiagonalShape())return a.getValue(BlockProgrammableWall.FACING)==b.getValue(BlockProgrammableWall.FACING) && a.getValue(BlockProgrammableWall.DEPTH).equals(b.getValue(BlockProgrammableWall.DEPTH));
        int mode=BlockProgrammableWall.geometry(world,root.getPos());
        return mode==BlockProgrammableWall.geometry(world,next) && x.getDiagonalFill()==y.getDiagonalFill()
                && (DiagonalPanelGeometry.samePlane(root.getPos(),a,next,b,mode) || DiagonalPanelGeometry.rowsMeet(root.getPos(),a,next,b,mode));
    }
    private GlassSurfaceGroups(){}
}
