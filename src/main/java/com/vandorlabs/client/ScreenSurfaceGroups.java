package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Bounded loaded-plane discovery shared by independent display and frame layouts. */
final class ScreenSurfaceGroups {
    private static final class Cache extends WorldRenderListener {
        long revision=-1;int vehicleRevision=-1;
        final Map<BlockPos,GlassSurfaceGroups.Layout> layouts=new HashMap<>(),frames=new HashMap<>();
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        void clear(){layouts.clear();frames.clear();minX=minY=minZ=Integer.MAX_VALUE;maxX=maxY=maxZ=Integer.MIN_VALUE;}
        void put(Map<BlockPos,GlassSurfaceGroups.Layout> target,BlockPos p,GlassSurfaceGroups.Layout layout){
            target.put(p,layout);
            minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());
            maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());
        }
        private void invalidateNear(int x0,int y0,int z0,int x1,int y1,int z1){
            // Bounds may remain oversized after one map reaches its cap; that only
            // costs a scan. Every insertion expands them before the next update.
            if((layouts.isEmpty()&&frames.isEmpty())|| (long)maxX<(long)x0-1 || (long)minX>(long)x1+1
                || (long)maxY<(long)y0-1 || (long)minY>(long)y1+1
                || (long)maxZ<(long)z0-1 || (long)minZ>(long)z1+1)return;
            for(BlockPos p:layouts.keySet())if(near(p,x0,y0,z0,x1,y1,z1)){clear();return;}
            for(BlockPos p:frames.keySet())if(near(p,x0,y0,z0,x1,y1,z1)){clear();return;}
        }
        private boolean near(BlockPos p,int x0,int y0,int z0,int x1,int y1,int z1){
            return (long)p.getX()>= (long)x0-1 && (long)p.getX()<=(long)x1+1
                && (long)p.getY()>=(long)y0-1 && (long)p.getY()<=(long)y1+1
                && (long)p.getZ()>=(long)z0-1 && (long)p.getZ()<=(long)z1+1;
        }
        @Override public void notifyBlockUpdate(World world,BlockPos p,IBlockState a,IBlockState b,int flags){invalidateNear(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ());}
        @Override public void markBlockRangeForRenderUpdate(int a,int b,int c,int d,int e,int f){invalidateNear(a,b,c,d,e,f);}
    }
    private static final Map<World,Cache> CACHES=new WeakHashMap<>();
    static boolean wideDisplay(TileEntityAnimatedScreenSelector tile){
        if(tile.getWorld()==null)return false;
        net.minecraft.block.Block block=tile.getBlockType();
        if(block!=ModBlocks.ANIMATED_SCREEN_SELECTOR&&block!=ModBlocks.PROGRAMMABLE_FULL_INPUT)return false;
        return DisplayEditGroups.wide(tile);
    }
    static synchronized GlassSurfaceGroups.Layout layout(TileEntityAnimatedScreenSelector tile){return layout(tile,false);}
    static synchronized GlassSurfaceGroups.Layout frameLayout(TileEntityAnimatedScreenSelector tile){GlassSurfaceGroups.Layout layout=layout(tile,true);if(tile.getBlockType().getClass()==BlockAnimatedScreenSelector.class&&tile.hasChipSurface(0)){EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(BlockAnimatedScreenSelector.FACING);if(facing==EnumFacing.UP)return layout.mirrorU();if(facing==EnumFacing.DOWN)return layout.mirrorV();}return layout;}
    private static GlassSurfaceGroups.Layout layout(TileEntityAnimatedScreenSelector tile,boolean frame){
        if(tile.isSmallInput()&&tile.getBlockType() instanceof com.vandorlabs.blocks.BlockProgrammableFullInput)return new GlassSurfaceGroups.Layout(0,0,1,1);
        World world=tile.getWorld();Cache cache=CACHES.get(world);if(cache==null){cache=new Cache();CACHES.put(world,cache);world.addEventListener(cache);}
        int vehicle=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).tileRevision:-1;
        if(cache.revision!=GlassMaterials.revision()||cache.vehicleRevision!=vehicle){cache.clear();cache.revision=GlassMaterials.revision();cache.vehicleRevision=vehicle;}
        Map<BlockPos,GlassSurfaceGroups.Layout> layouts=frame?cache.frames:cache.layouts;
        GlassSurfaceGroups.Layout existing=layouts.get(tile.getPos());if(existing!=null)return existing;
        IBlockState state=world.getBlockState(tile.getPos());
        if(!DisplayJoinGeometry.supportsJoining(tile))return new GlassSurfaceGroups.Layout(0,0,1,1);
        DisplayJoinGeometry.Basis basis=DisplayJoinGeometry.basis(tile,state);
        BlockPos[] steps=basis.steps;Set<BlockPos> members=new LinkedHashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(tile.getPos());members.add(tile.getPos());
        while(!queue.isEmpty()&&members.size()<=4096){BlockPos p=queue.removeFirst();for(BlockPos step:steps){BlockPos next=p.add(step);if(members.contains(next)||!world.isBlockLoaded(next)||world.getBlockState(next)!=state)continue;net.minecraft.tileentity.TileEntity raw=world.getTileEntity(next);if(!(raw instanceof TileEntityAnimatedScreenSelector))continue;TileEntityAnimatedScreenSelector other=(TileEntityAnimatedScreenSelector)raw;if(!matching(tile,other,frame))continue;members.add(next);queue.add(next);if(members.size()>4096)break;}}
        Map<Long,BlockPos> cells=new HashMap<>();for(BlockPos p:members)cells.put(basis.cell(p),p);
        if(layouts.size()+members.size()>8192)layouts.clear();
        if(members.size()>4096)for(BlockPos p:members)cache.put(layouts,p,new GlassSurfaceGroups.Layout(0,0,1,1));
        else for(PortholeRectangles.Rect r:PortholeRectangles.partition(cells.keySet()))for(int row=0;row<r.height;row++)for(int col=0;col<r.width;col++)cache.put(layouts,cells.get(PortholeRectangles.cell(r.x+col,r.y+row)),new GlassSurfaceGroups.Layout(col,row,r.width,r.height));
        return layouts.get(tile.getPos());
    }
    private static boolean matching(TileEntityAnimatedScreenSelector a,TileEntityAnimatedScreenSelector b,boolean frame){
        if(!DisplayJoinGeometry.sameShape(a,b))return false;
        if(frame)return a.frameAppearance.bare==b.frameAppearance.bare&&a.frameAppearance.front==b.frameAppearance.front&&a.frameAppearance.tile==b.frameAppearance.tile;
        return a.frameAppearance.displayFit==b.frameAppearance.displayFit&&a.splitDisplay()==b.splitDisplay()
            &&(!a.splitDisplay()||a.frameAppearance.splitTop==b.frameAppearance.splitTop&&a.frameAppearance.splitBottom==b.frameAppearance.splitBottom)
            &&a.primaryArtworkTexture()==b.primaryArtworkTexture()
            &&a.getEffectiveMode()==b.getEffectiveMode()&&a.getAnimationSpeedIndex()==b.getAnimationSpeedIndex();
    }
    static void unload(World world){Cache cache=CACHES.remove(world);if(cache!=null)world.removeEventListener(cache);}
    static void checkInvalidation(){
        Cache cache=new Cache();
        for(BlockPos p:new BlockPos[]{new BlockPos(-17,-1,-16),new BlockPos(16,1,17),new BlockPos(Integer.MIN_VALUE,0,0),new BlockPos(Integer.MAX_VALUE,0,0)}){
            cache.clear();GlassSurfaceGroups.Layout layout=new GlassSurfaceGroups.Layout(0,0,2,1);
            cache.put(cache.layouts,p,layout);cache.put(cache.frames,p,layout);
            cache.notifyBlockUpdate(null,p.add(0,0,2),null,null,0);
            if(cache.layouts.get(p)!=layout||cache.frames.get(p)!=layout)throw new IllegalStateException("distant screen update discarded cached layout");
            cache.markBlockRangeForRenderUpdate(p.getX(),p.getY(),p.getZ()-1,p.getX(),p.getY(),p.getZ()-1);
            if(!cache.layouts.isEmpty()||!cache.frames.isEmpty())throw new IllegalStateException("adjacent screen update retained cached group");
            cache.put(cache.frames,p,layout);
            cache.notifyBlockUpdate(null,p,null,null,0);
            if(!cache.frames.isEmpty())throw new IllegalStateException("screen frame insertion did not expand cleared bounds");
        }
    }
    private ScreenSurfaceGroups(){}
}
