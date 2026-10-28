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
        void clear(){layouts.clear();frames.clear();}
        @Override public void notifyBlockUpdate(World world,BlockPos p,IBlockState a,IBlockState b,int flags){clear();}
        @Override public void markBlockRangeForRenderUpdate(int a,int b,int c,int d,int e,int f){clear();}
    }
    private static final Map<World,Cache> CACHES=new WeakHashMap<>();
    static synchronized GlassSurfaceGroups.Layout layout(TileEntityAnimatedScreenSelector tile){return layout(tile,false);}
    static synchronized GlassSurfaceGroups.Layout frameLayout(TileEntityAnimatedScreenSelector tile){return layout(tile,true);}
    private static GlassSurfaceGroups.Layout layout(TileEntityAnimatedScreenSelector tile,boolean frame){
        World world=tile.getWorld();Cache cache=CACHES.get(world);if(cache==null){cache=new Cache();CACHES.put(world,cache);world.addEventListener(cache);}
        int vehicle=world instanceof com.vandorlabs.vehicle.VehicleWorld?((com.vandorlabs.vehicle.VehicleWorld)world).tileRevision:-1;
        if(cache.revision!=GlassMaterials.revision()||cache.vehicleRevision!=vehicle){cache.clear();cache.revision=GlassMaterials.revision();cache.vehicleRevision=vehicle;}
        Map<BlockPos,GlassSurfaceGroups.Layout> layouts=frame?cache.frames:cache.layouts;
        GlassSurfaceGroups.Layout existing=layouts.get(tile.getPos());if(existing!=null)return existing;
        IBlockState state=world.getBlockState(tile.getPos());EnumFacing face=state.getBlock() instanceof BlockProgrammableInput?state.getValue(BlockProgrammableInput.FACING):state.getBlock() instanceof BlockProgrammableDiagonalScreen?state.getValue(BlockProgrammableDiagonalScreen.FACING):state.getValue(BlockAnimatedScreenSelector.FACING);
        if(state.getBlock() instanceof BlockProgrammableHalfConsole||state.getBlock() instanceof BlockDiagonalHalfConsole)return new GlassSurfaceGroups.Layout(0,0,1,1);
        EnumFacing right=face.getAxis().isHorizontal()?face.rotateY():EnumFacing.EAST;
        EnumFacing up=face.getAxis().isHorizontal()?EnumFacing.UP:face==EnumFacing.UP?EnumFacing.NORTH:EnumFacing.SOUTH;
        boolean diagonal=state.getBlock() instanceof BlockProgrammableDiagonalScreen;
        if(state.getBlock() instanceof BlockProgrammableInput&&(state.getValue(BlockProgrammableInput.KEYBOARD)||tile.isCeilingMounted()))up=face.getOpposite();
        BlockPos rowStep=new BlockPos(up.getFrontOffsetX(),up.getFrontOffsetY(),up.getFrontOffsetZ());
        if(diagonal)rowStep=rowStep.offset(state.getValue(BlockProgrammableDiagonalScreen.INVERTED)?face:face.getOpposite());
        final EnumFacing rowAxis=up;
        BlockPos rightStep=new BlockPos(right.getFrontOffsetX(),right.getFrontOffsetY(),right.getFrontOffsetZ());
        BlockPos[] steps={rightStep,new BlockPos(-rightStep.getX(),-rightStep.getY(),-rightStep.getZ()),rowStep,new BlockPos(-rowStep.getX(),-rowStep.getY(),-rowStep.getZ())};Set<BlockPos> members=new LinkedHashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(tile.getPos());members.add(tile.getPos());
        while(!queue.isEmpty()&&members.size()<1024){BlockPos p=queue.removeFirst();for(BlockPos step:steps){BlockPos next=p.add(step);if(members.contains(next)||!world.isBlockLoaded(next)||world.getBlockState(next)!=state)continue;net.minecraft.tileentity.TileEntity raw=world.getTileEntity(next);if(!(raw instanceof TileEntityAnimatedScreenSelector))continue;TileEntityAnimatedScreenSelector other=(TileEntityAnimatedScreenSelector)raw;if(!matching(tile,other,frame))continue;members.add(next);queue.add(next);if(members.size()>=1024)break;}}
        Map<Long,BlockPos> cells=new HashMap<>();for(BlockPos p:members)cells.put(PortholeRectangles.cell(p.getX()*right.getFrontOffsetX()+p.getZ()*right.getFrontOffsetZ(),p.getX()*rowAxis.getFrontOffsetX()+p.getY()*rowAxis.getFrontOffsetY()+p.getZ()*rowAxis.getFrontOffsetZ()),p);
        if(layouts.size()+members.size()>8192)layouts.clear();
        if(members.size()>=1024)for(BlockPos p:members)layouts.put(p,new GlassSurfaceGroups.Layout(0,0,1,1));
        else for(PortholeRectangles.Rect r:PortholeRectangles.partition(cells.keySet()))for(int row=0;row<r.height;row++)for(int col=0;col<r.width;col++)layouts.put(cells.get(PortholeRectangles.cell(r.x+col,r.y+row)),new GlassSurfaceGroups.Layout(col,row,r.width,r.height));
        return layouts.get(tile.getPos());
    }
    private static boolean matching(TileEntityAnimatedScreenSelector a,TileEntityAnimatedScreenSelector b,boolean frame){
        if(a.isCeilingMounted()!=b.isCeilingMounted()||a.isSmallInput()!=b.isSmallInput()||a.getCeilingPosition(-1)!=b.getCeilingPosition(-1)||a.getWallPosition(-1)!=b.getWallPosition(-1))return false;
        if(frame)return a.frameAppearance.front==b.frameAppearance.front&&a.frameAppearance.tile==b.frameAppearance.tile;
        return a.frameAppearance.displayFit==b.frameAppearance.displayFit&&a.frameAppearance.split==b.frameAppearance.split
            &&a.frameAppearance.splitTop==b.frameAppearance.splitTop&&a.frameAppearance.splitBottom==b.frameAppearance.splitBottom
            &&a.getSelectedScreen().equals(b.getSelectedScreen())&&a.getSurfaceTexture(0)==b.getSurfaceTexture(0)
            &&a.getEffectiveMode()==b.getEffectiveMode()&&a.getAnimationSpeedIndex()==b.getAnimationSpeedIndex();
    }
    static void unload(World world){Cache cache=CACHES.remove(world);if(cache!=null)world.removeEventListener(cache);}
    private ScreenSurfaceGroups(){}
}
