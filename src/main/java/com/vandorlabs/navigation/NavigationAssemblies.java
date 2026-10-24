package com.vandorlabs.navigation;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/** Event-driven discovery using the existing bounded loaded-plane traversal. */
public final class NavigationAssemblies {
    private static final Map<World,Set<BlockPos>> PENDING=new WeakHashMap<>();
    private NavigationAssemblies(){}
    public static void around(World world,BlockPos pos){
        if(world==null||world.isRemote)return;
        schedule(world,pos);for(EnumFacing face:EnumFacing.HORIZONTALS)schedule(world,pos.offset(face));
    }
    private static void schedule(World world,BlockPos pos){
        if(!world.isBlockLoaded(pos))return;TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof TileEntityNavigationTable){
            PENDING.computeIfAbsent(world,w->new LinkedHashSet<>()).add(pos.toImmutable());
            DeferredTileLoad.schedule(tile,()->flush(world));
        }
    }
    private static void flush(World world){
        Set<BlockPos> pending=PENDING.remove(world);if(pending==null)return;
        Set<BlockPos> visited=new HashSet<>();
        for(BlockPos pos:pending)if(!visited.contains(pos))visited.addAll(rebuildComponent(world,pos));
    }
    public static void rebuild(World world,BlockPos pos){
        rebuildComponent(world,pos);
    }
    private static Set<BlockPos> rebuildComponent(World world,BlockPos pos){
        if(world.isRemote||!world.isBlockLoaded(pos))return Collections.emptySet();
        IBlockState state=world.getBlockState(pos);if(!(state.getBlock() instanceof BlockNavigationTable))return Collections.emptySet();
        BlockNavigationTable block=(BlockNavigationTable)state.getBlock();EnumFacing facing=state.getValue(BlockVandorDirectional.FACING);
        LoadedPlaneConnections.Scan scan=LoadedPlaneConnections.scan(pos,PanelPlane.of(EnumFacing.UP),world::isBlockLoaded,next->{
            IBlockState other=world.getBlockState(next);
            return other.getBlock()==block&&other.getValue(BlockVandorDirectional.FACING)==facing
                    &&(block.kind==NavigationLayout.Kind.FLAT||PanelPlane.axis(next,facing)==PanelPlane.axis(pos,facing));
        },NavigationLayout.DEFAULT_MAX_CELLS+1);
        List<NavigationLayout.Cell> cells=new ArrayList<>();List<TileEntityNavigationTable> tiles=new ArrayList<>();
        for(BlockPos at:scan.members){cells.add(new NavigationLayout.Cell(at.getX(),at.getY(),at.getZ()));TileEntity tile=world.getTileEntity(at);if(tile instanceof TileEntityNavigationTable)tiles.add((TileEntityNavigationTable)tile);}
        NavigationLayout.Layout layout=null;
        try{layout=NavigationLayout.build(block.kind,NavigationLayout.Facing.valueOf(facing.name()),cells,scan.complete&&tiles.size()==cells.size());}catch(IllegalArgumentException invalid){/* Remain unformed until the complete rectangle is available. */}
        if(layout!=null){
            BlockPos owner=new BlockPos(layout.chipOwner.x,layout.chipOwner.y,layout.chipOwner.z);
            TileEntityNavigationTable destination=(TileEntityNavigationTable)world.getTileEntity(owner);
            // Retain the configured reader when its selected physical module changes.
            TileEntityNavigationTable source=destination;
            for(TileEntityNavigationTable tile:tiles)if(!tile.isEmpty()&&(source.isEmpty()||!source.hasMount()))source=tile;
            if(source==destination&&destination.isEmpty())for(TileEntityNavigationTable tile:tiles)if(tile.hasMount()&&!tile.getPos().equals(owner)){source=tile;break;}
            if(source!=destination)source.moveReaderTo(destination);
            for(TileEntityNavigationTable tile:tiles){
                NavigationLayout.Tile assignment=layout.tiles.get(new NavigationLayout.Cell(tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ()));
                tile.layout(assignment.connections,owner,true);
            }
            // Merging independently occupied tables preserves borrowed returns;
            // extra retained chips are released once, as when removing a mount.
            for(TileEntityNavigationTable tile:tiles)if(tile!=destination&&!tile.isEmpty()&&!tile.returning())TokenSlotInteractions.drop(world,tile.getPos());
        }else for(TileEntityNavigationTable tile:tiles)tile.layout(0,tile.getPos(),false);
        return scan.members;
    }
}
