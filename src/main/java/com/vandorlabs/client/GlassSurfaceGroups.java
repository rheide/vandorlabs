package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.lang.ref.WeakReference;
import java.util.*;

/** Loaded, coplanar material groups. Holes partition into complete rectangles. */
final class GlassSurfaceGroups {
    static final class Layout {
        final int column,row,columns,rows;
        final boolean flipU,flipV;
        private Layout mirroredU,mirroredV;
        Layout mirrorU(){if(mirroredU==null)mirroredU=new Layout(columns-1-column,row,columns,rows,flipU,flipV);return mirroredU;}
        Layout mirrorV(){if(mirroredV==null)mirroredV=new Layout(column,rows-1-row,columns,rows,flipU,flipV);return mirroredV;}
        Layout(int column,int row,int columns,int rows){this(column,row,columns,rows,false,false);}
        Layout(int column,int row,int columns,int rows,boolean flipU,boolean flipV){this.column=column;this.row=row;this.columns=columns;this.rows=rows;this.flipU=flipU;this.flipV=flipV;}
        String key(){return column+":"+row+":"+columns+":"+rows+":"+flipU+":"+flipV;}
    }
    static final int MAX_LAYOUTS=8192;
    // A cheap same-flush guard for captured state/topology. All writes already
    // hold this class's cache lock; readers need no world lookup or allocation.
    private static volatile long mutation;
    static long mutation(){return mutation;}
    private static final class Corner {
        final WeakReference<TileEntityProgrammableGlass> owner;
        final IBlockState state;
        final int metadata;
        final boolean joined;
        final EnumFacing facing;
        final BlockProgrammableWall.FlatCorner value;
        Corner(TileEntityProgrammableGlass tile,IBlockState state,EnumFacing facing){
            owner=new WeakReference<>(tile);this.state=state;metadata=state.getBlock().getMetaFromState(state);joined=tile.isJoin();this.facing=facing;
            value=PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos());
        }
    }
    private static final class Cache extends WorldRenderListener {
        long revision;
        final Map<BlockPos,Layout> frames=new HashMap<>();
        final Map<BlockPos,Integer> frameMasks=new HashMap<>();
        int tileRevision=-1;
        WeakReference<Object> structure;
        final Map<BlockPos,Layout> layouts=new LinkedHashMap<BlockPos,Layout>(128,.75F,true){protected boolean removeEldestEntry(Map.Entry<BlockPos,Layout> entry){return size()>MAX_LAYOUTS;}};
        final Map<BlockPos,Layout> mainLayouts=new LinkedHashMap<BlockPos,Layout>(128,.75F,true){protected boolean removeEldestEntry(Map.Entry<BlockPos,Layout> entry){return size()>MAX_LAYOUTS;}};
        final Map<BlockPos,Corner> corners=new HashMap<>();
        final Dependencies layoutDependencies=new Dependencies();
        final BlockPos.MutableBlockPos lookup=new BlockPos.MutableBlockPos();
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        void clearLayouts(){layouts.clear();mainLayouts.clear();frames.clear();frameMasks.clear();layoutDependencies.clear();}
        void clear(){clearLayouts();corners.clear();minX=minY=minZ=Integer.MAX_VALUE;maxX=maxY=maxZ=Integer.MIN_VALUE;mutation++;}
        void admit(BlockPos pos,Corner corner){
            corners.put(pos.toImmutable(),corner);
            minX=Math.min(minX,pos.getX());minY=Math.min(minY,pos.getY());minZ=Math.min(minZ,pos.getZ());
            maxX=Math.max(maxX,pos.getX());maxY=Math.max(maxY,pos.getY());maxZ=Math.max(maxZ,pos.getZ());
        }
        /** Flat corners read only the owner and its four same-height neighbours.
         * Connected artwork tracks its wider discovery dependencies separately.
         * Bounds may be conservative after removal; they never omit an entry. */
        void changed(int x0,int y0,int z0,int x1,int y1,int z1){
            mutation++;
            // Include rejected and unloaded candidate cells as well as members:
            // placing a bridge there can change an entire artwork rectangle.
            // A distant edit cannot affect any of those previously read cells.
            if(layoutDependencies.intersects(x0,y0,z0,x1,y1,z1))clearLayouts();
            long loX=(long)Math.min(x0,x1)-1,hiX=(long)Math.max(x0,x1)+1;
            long loZ=(long)Math.min(z0,z1)-1,hiZ=(long)Math.max(z0,z1)+1;
            int loY=Math.min(y0,y1),hiY=Math.max(y0,y1);
            if(corners.isEmpty()||hiX<minX||loX>maxX||hiY<minY||loY>maxY||hiZ<minZ||loZ>maxZ)return;
            if(x0==x1&&y0==y1&&z0==z1){
                corners.remove(lookup.setPos(x0,y0,z0));
                if(x0>Integer.MIN_VALUE)corners.remove(lookup.setPos(x0-1,y0,z0));
                if(x0<Integer.MAX_VALUE)corners.remove(lookup.setPos(x0+1,y0,z0));
                if(z0>Integer.MIN_VALUE)corners.remove(lookup.setPos(x0,y0,z0-1));
                if(z0<Integer.MAX_VALUE)corners.remove(lookup.setPos(x0,y0,z0+1));
                return;
            }
            int left=(int)Math.max(loX,minX),right=(int)Math.min(hiX,maxX);
            int bottom=Math.max(loY,minY),top=Math.min(hiY,maxY);
            int near=(int)Math.max(loZ,minZ),far=(int)Math.min(hiZ,maxZ);
            long width=(long)right-left+1,height=(long)top-bottom+1,depth=(long)far-near+1;
            // Small edits need no iterator. Large ranges scan at most the fixed
            // cache budget, never every coordinate of a chunk/world-sized range.
            if(width<=256&&height<=256&&depth<=256&&width*height*depth<=256){
                for(long y=bottom;y<=top;y++)for(long z=near;z<=far;z++)for(long x=left;x<=right;x++)
                    corners.remove(lookup.setPos((int)x,(int)y,(int)z));
            }else for(Iterator<BlockPos> it=corners.keySet().iterator();it.hasNext();){
                BlockPos pos=it.next();
                if(pos.getX()>=left&&pos.getX()<=right&&pos.getY()>=bottom&&pos.getY()<=top&&pos.getZ()>=near&&pos.getZ()<=far)it.remove();
            }
        }
        void topology(World world){
            int next=-1;Object snapshot=null;
            if(world instanceof com.vandorlabs.vehicle.VehicleWorld){
                com.vandorlabs.vehicle.VehicleWorld vehicle=(com.vandorlabs.vehicle.VehicleWorld)world;
                next=vehicle.tileRevision;snapshot=vehicle.structure;
            }
            if(tileRevision!=next||(structure==null?null:structure.get())!=snapshot){
                tileRevision=next;structure=snapshot==null?null:new WeakReference<>(snapshot);clear();
            }
        }
        void materials(World world){
            topology(world);long next=GlassMaterials.revision();
            if(revision!=next){revision=next;clearLayouts();}
        }
        @Override public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags){
            synchronized(GlassSurfaceGroups.class){changed(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());}
        }
        @Override public void markBlockRangeForRenderUpdate(int x1,int y1,int z1,int x2,int y2,int z2){
            synchronized(GlassSurfaceGroups.class){changed(x1,y1,z1,x2,y2,z2);}
        }
    }
    /** Conservative union of every cell consulted by the retained layouts and
     * frame masks. Eviction may leave extra bounds; it can never omit a dependency. */
    private static final class Dependencies {
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        void clear(){minX=minY=minZ=Integer.MAX_VALUE;maxX=maxY=maxZ=Integer.MIN_VALUE;}
        void include(BlockPos pos){
            minX=Math.min(minX,pos.getX());minY=Math.min(minY,pos.getY());minZ=Math.min(minZ,pos.getZ());
            maxX=Math.max(maxX,pos.getX());maxY=Math.max(maxY,pos.getY());maxZ=Math.max(maxZ,pos.getZ());
        }
        boolean intersects(int x0,int y0,int z0,int x1,int y1,int z1){
            return minX<=maxX && Math.min(x0,x1)<=maxX && Math.max(x0,x1)>=minX
                    && Math.min(y0,y1)<=maxY && Math.max(y0,y1)>=minY
                    && Math.min(z0,z1)<=maxZ && Math.max(z0,z1)>=minZ;
        }
    }
    private static final Map<World,Cache> CACHES=new WeakHashMap<>();
    private static Cache cache(World world){
        Cache cache=CACHES.get(world);
        if(cache==null){cache=new Cache();CACHES.put(world,cache);world.addEventListener(cache);mutation++;}
        return cache;
    }
    static synchronized void invalidate(World world){Cache cache=CACHES.get(world);if(cache!=null)cache.clear();else mutation++;}
    static synchronized void invalidate(World world,int x0,int y0,int z0,int x1,int y1,int z1){
        Cache cache=CACHES.get(world);if(cache!=null)cache.changed(x0,y0,z0,x1,y1,z1);else mutation++;
    }
    /** Local-only tile lifecycle/settings notification, also used by captured worlds. */
    static void changed(TileEntityProgrammableGlass tile){
        World world=tile.getWorld();if(world==null)return;BlockPos pos=tile.getPos();
        invalidate(world,pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());
    }
    static synchronized void unload(World world){Cache cache=CACHES.remove(world);mutation++;if(cache!=null)world.removeEventListener(cache);}
    static Layout layout(TileEntity tile){return layout(tile,false,false);}
    static Layout frameLayout(TileEntity tile){return layout(tile,false,true);}
    /** Fast-render eligibility must not perform discovery: Forge asks twice per
     * dispatch, including panes outside the admission budget. A missed proof
     * keeps the ordinary renderer, which discovers the corner only once. */
    static synchronized boolean flatCached(TileEntityProgrammableGlass tile,IBlockState state){
        World world=tile.getWorld();if(world==null)return false;Cache cache=CACHES.get(world);if(cache==null)return false;
        cache.topology(world);Corner corner=cache.corners.get(tile.getPos());
        return corner!=null&&corner.value==null&&corner.owner.get()==tile&&corner.state==state
                &&corner.joined==tile.isJoin()&&corner.facing==tile.getPanelFacing(PanelJoins.facing(state));
    }
    /** Native metadata preserves the corner's ROTATED/DEPTH inputs; size and
     * calculated connection properties do not participate. Precompute the saved
     * value on admission instead of rebuilding an IBlockState twice per dispatch. */
    static synchronized boolean flatCached(TileEntityProgrammableGlass tile,net.minecraft.block.Block block,int metadata){
        World world=tile.getWorld();if(world==null)return false;Cache cache=CACHES.get(world);if(cache==null)return false;
        cache.topology(world);Corner corner=cache.corners.get(tile.getPos());
        return corner!=null&&corner.value==null&&corner.owner.get()==tile&&corner.state.getBlock()==block&&corner.metadata==metadata
                &&corner.joined==tile.isJoin()&&corner.facing==tile.getPanelFacing((metadata&1)==0?EnumFacing.NORTH:EnumFacing.EAST);
    }
    /** Reuse regular-panel topology across collection, sorting and pane submission.
     * Null corners are cached too; geometry contains no atlas or vertex-format data. */
    static synchronized BlockProgrammableWall.FlatCorner flatCorner(TileEntityProgrammableGlass tile,IBlockState state){
        World world=tile.getWorld();Cache cache=cache(world);cache.topology(world);
        EnumFacing facing=tile.getPanelFacing(PanelJoins.facing(state));
        Corner old=cache.corners.get(tile.getPos());
        if(old!=null&&old.owner.get()==tile&&old.state==state&&old.joined==tile.isJoin()&&old.facing==facing)return old.value;
        // A view can contain more panes than the cache budget. Cycling through
        // those panes must not evict every useful entry and allocate a new weak
        // owner/map node on every pass. Keep the admitted working set and run
        // excess positions uncached until ordinary invalidation reopens space.
        // An admitted position can still replace a stale owner/state in place.
        if(old==null&&cache.corners.size()>=MAX_LAYOUTS)
            return PanelJoins.flatCorner(state,world,tile.getPos());
        Corner next=new Corner(tile,state,facing);cache.admit(tile.getPos(),next);return next.value;
    }
    /** Local contour edges are independent of rectangular artwork partitions. */
    static synchronized int frameMask(TileEntityProgrammableGlass tile){
        if(tile.frameAppearance.tile||!tile.isJoin())return 15;
        World world=tile.getWorld();Cache cache=cache(world);cache.materials(world);
        Integer old=cache.frameMasks.get(tile.getPos());if(old!=null)return old;
        EnumFacing right=PanelJoins.facing(world.getBlockState(tile.getPos())).rotateY();
        BlockPos p=tile.getPos();BlockPos[] next={p.offset(right.getOpposite()),p.offset(right),p.down(),p.up()};boolean[] joined=new boolean[4];int mask=0;
        cache.layoutDependencies.include(p);
        for(int i=0;i<4;i++){cache.layoutDependencies.include(next[i]);joined[i]=com.vandorlabs.blocks.TextureJoins.allows(tile,p,next[i])&&world.isBlockLoaded(next[i])&&compatible(tile,next[i],false,true);if(!joined[i])mask|=1<<i;}
        for(int side=0;side<2;side++)for(int vertical=0;vertical<2;vertical++)if(joined[side]&&joined[2+vertical]){
            BlockPos diagonal=next[side].add(0,vertical==0?-1:1,0);
            cache.layoutDependencies.include(diagonal);
            if(!world.isBlockLoaded(diagonal)||!compatible(tile,diagonal,false,true))mask|=1<<(4+side+vertical*2);
        }
        if(cache.frameMasks.size()>=MAX_LAYOUTS)cache.frameMasks.clear();cache.frameMasks.put(p.toImmutable(),mask);return mask;
    }

    static Layout mainLayout(TileEntity tile){return layout(tile,true,false);}
    private static synchronized Layout layout(TileEntity tile,boolean main,boolean frame){
        World world=tile.getWorld();Cache cache=cache(world);cache.materials(world);
        Map<BlockPos,Layout> layouts=frame?cache.frames:main?cache.mainLayouts:cache.layouts;
        if(layouts.size()>MAX_LAYOUTS)layouts.clear();
        Layout found=layouts.get(tile.getPos());if(found!=null)return found;
        cache.layoutDependencies.include(tile.getPos());
        IBlockState state=world.getBlockState(tile.getPos());boolean diagonal=state.getBlock() instanceof BlockProgrammableWall && ((BlockProgrammableWall)state.getBlock()).isDiagonalShape();
        EnumFacing facing=PanelJoins.facing(state),right=facing.rotateY(),up=diagonal && ((TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight()?facing.getOpposite():EnumFacing.UP;
        Set<BlockPos> members=new LinkedHashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();members.add(tile.getPos());queue.add(tile.getPos());
        if(main || join(tile))while(!queue.isEmpty() && members.size()<LoadedPlaneConnections.LIMIT){
            BlockPos current=queue.removeFirst();List<BlockPos> candidates=new ArrayList<>();candidates.add(current.offset(right));candidates.add(current.offset(right.getOpposite()));
            if(diagonal)for(boolean positive:new boolean[]{false,true})candidates.addAll(DiagonalPanelGeometry.rowCandidates(current,world.getBlockState(current),BlockProgrammableWall.geometry(world,current),positive));
            else {candidates.add(current.up());candidates.add(current.down());}
            for(BlockPos next:candidates){cache.layoutDependencies.include(next);if(members.contains(next) || !com.vandorlabs.blocks.TextureJoins.allows(tile,current,next) || !world.isBlockLoaded(next) || !compatible(world.getTileEntity(current),next,main,frame))continue;members.add(next);queue.add(next);if(members.size()>=LoadedPlaneConnections.LIMIT)break;}
        }
        BlockPos first=Collections.min(members);EnumFacing basis=PanelJoins.facing(world.getBlockState(first));right=basis.rotateY();up=diagonal && ((TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight()?basis.getOpposite():EnumFacing.UP;
        Map<Long,BlockPos> cells=new HashMap<>();for(BlockPos p:members)cells.put(PortholeRectangles.cell(PanelPlane.axis(p,right),PanelPlane.axis(p,up)),p);
        if(members.size()>=LoadedPlaneConnections.LIMIT){for(BlockPos p:members)if(p.equals(tile.getPos())||com.vandorlabs.blocks.TextureJoins.defaultPolicy(tile)&&com.vandorlabs.blocks.TextureJoins.defaultPolicy(world.getTileEntity(p)))layouts.put(p,new Layout(0,0,1,1));}
        else for(PortholeRectangles.Rect rect:PortholeRectangles.partition(cells.keySet()))for(int y=0;y<rect.height;y++)for(int x=0;x<rect.width;x++){
            BlockPos p=cells.get(PortholeRectangles.cell(rect.x+x,rect.y+y));EnumFacing local=PanelJoins.facing(world.getBlockState(p));
            if(p.equals(tile.getPos())||com.vandorlabs.blocks.TextureJoins.defaultPolicy(tile)&&com.vandorlabs.blocks.TextureJoins.defaultPolicy(world.getTileEntity(p)))layouts.put(p,new Layout(x,y,rect.width,rect.height,local.rotateY()!=right,diagonal && ((TileEntityAnimatedScreenSelector)tile).isDiagonalHalfHeight() && local.getOpposite()!=up));
        }
        return layouts.get(tile.getPos());
    }
    private static boolean join(TileEntity tile){return tile instanceof TileEntityProgrammableGlass?((TileEntityProgrammableGlass)tile).isJoin():((TileEntityAnimatedScreenSelector)tile).isGlassTextureJoin();}
    private static boolean compatible(TileEntity root,BlockPos next,boolean main,boolean frame){
        if(root==null)return false;World world=root.getWorld();IBlockState a=world.getBlockState(root.getPos()),b=world.getBlockState(next);TileEntity other=world.getTileEntity(next);
        if(a.getBlock()!=b.getBlock() || other==null || !main && !join(other))return false;
        if(!frame&&!main){int material=GlazingDisplay.material(root);if(material>=0&&"Screens".equals(com.vandorlabs.tiles.ScreenHousingTextures.category(material))&&!com.vandorlabs.tiles.GlassPlayback.of(root).same(com.vandorlabs.tiles.GlassPlayback.of(other)))return false;}
        if(root instanceof TileEntityProgrammableGlass){TileEntityProgrammableGlass x=(TileEntityProgrammableGlass)root,y=(TileEntityProgrammableGlass)other;
            if(frame)return x.isFramed()==y.isFramed()&&x.frameAppearance.front==y.frameAppearance.front&&x.frameAppearance.interior==y.frameAppearance.interior&&x.frameAppearance.exteriorChoice()==y.frameAppearance.exteriorChoice()&&x.frameAppearance.tile==y.frameAppearance.tile&&a.getValue(BlockGlassWall.ROTATED).equals(b.getValue(BlockGlassWall.ROTATED))&&a.getValue(BlockGlassWall.DEPTH).equals(b.getValue(BlockGlassWall.DEPTH));
            return sameMaterial(x.getMaterial(),y.getMaterial()) && x.getTint()==y.getTint() && x.getTextureLayout()==y.getTextureLayout() && a.getValue(BlockGlassWall.ROTATED).equals(b.getValue(BlockGlassWall.ROTATED)) && a.getValue(BlockGlassWall.DEPTH).equals(b.getValue(BlockGlassWall.DEPTH));}
        TileEntityAnimatedScreenSelector x=(TileEntityAnimatedScreenSelector)root,y=(TileEntityAnimatedScreenSelector)other;
        if(main){if(x.getHousingTexture()!=y.getHousingTexture() || x.getMainTextureLayout()!=y.getMainTextureLayout())return false;}
        else if(!sameMaterial(x.getGlassMaterial(),y.getGlassMaterial()) || x.getGlassTint()!=y.getGlassTint() || x.getGlassTextureLayout()!=y.getGlassTextureLayout())return false;
        if(a.getValue(BlockProgrammableWall.FACING).getAxis()!=b.getValue(BlockProgrammableWall.FACING).getAxis())return false;
        if(!((BlockProgrammableWall)a.getBlock()).isDiagonalShape())return a.getValue(BlockProgrammableWall.FACING)==b.getValue(BlockProgrammableWall.FACING) && a.getValue(BlockProgrammableWall.DEPTH).equals(b.getValue(BlockProgrammableWall.DEPTH));
        int mode=BlockProgrammableWall.geometry(world,root.getPos());
        return mode==BlockProgrammableWall.geometry(world,next) && x.getDiagonalFill()==y.getDiagonalFill()
                && (DiagonalPanelGeometry.samePlane(root.getPos(),a,next,b,mode) || DiagonalPanelGeometry.rowsMeet(root.getPos(),a,next,b,mode));
    }
    private static boolean sameMaterial(int a,int b){return a==b||a>=0&&b>=0&&com.vandorlabs.tiles.ScreenHousingTextures.clamp(a)==com.vandorlabs.tiles.ScreenHousingTextures.clamp(b);}
    private GlassSurfaceGroups(){}
}
