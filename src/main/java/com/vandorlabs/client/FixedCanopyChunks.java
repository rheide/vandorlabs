package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockFixedCanopyGlass;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** Local dirty queue: no world scans, chunk loads, or geometry mutation on chunk workers. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class FixedCanopyChunks {
    private static final Set<TileEntityCanopy> pending=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<BlockPos> positions=new HashSet<>();
    private static final Map<BlockPos,TileEntityCanopy> tiles=new HashMap<>();
    private static World world;
    private static WorldRenderListener listener;
    private static net.minecraft.client.renderer.vertex.VertexFormat format;
    private static final com.google.common.cache.Cache<String,FixedCanopyModel.Mesh> meshes=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(8*1024*1024)
        .weigher((String k,FixedCanopyModel.Mesh v)->Math.max(1,v.bytes())).build();
    public static void changed(TileEntityCanopy tile){
        if(tile.getWorld()==null||!tile.getWorld().isRemote||tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld)return;
        synchronized(pending){pending.add(tile);}
    }
    static void reload(){meshes.invalidateAll();for(TileEntityCanopy tile:tiles.values()){tile.chunkMesh=null;synchronized(pending){pending.add(tile);}}}
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.world.WorldEvent.Unload event){if(event.getWorld()==world)reset(null);}
    private static void reset(World next){
        if(world!=null&&listener!=null)world.removeEventListener(listener);
        for(TileEntityCanopy tile:tiles.values())tile.chunkMesh=null;tiles.clear();positions.clear();meshes.invalidateAll();world=next;
        if(next!=null){listener=new WorldRenderListener(){@Override public void notifyBlockUpdate(World w,BlockPos p,IBlockState a,IBlockState b,int flags){positions.add(p.toImmutable());}};next.addEventListener(listener);}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END)flush();}
    static void flush(){
        World next=Minecraft.getMinecraft().world;if(next!=world)reset(next);if(world==null){synchronized(pending){pending.clear();}return;}
        if(format==null||!format.equals(net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK)){format=new net.minecraft.client.renderer.vertex.VertexFormat(net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK);reload();}
        Set<TileEntityCanopy> changed=Collections.newSetFromMap(new IdentityHashMap<>());
        synchronized(pending){changed.addAll(pending);pending.clear();}
        for(TileEntityCanopy tile:changed)if(tile.getWorld()==world){
            BlockPos pos=tile.getPos().toImmutable();positions.add(pos);
            if(!tile.isInvalid()&&world.isBlockLoaded(pos)&&world.getTileEntity(pos)==tile&&world.getBlockState(pos).getBlock() instanceof BlockFixedCanopyGlass)tiles.put(pos,tile);
            else if(tiles.get(pos)==tile)tiles.remove(pos);
        }
        Set<TileEntityCanopy> dirty=Collections.newSetFromMap(new IdentityHashMap<>());
        for(BlockPos pos:positions)for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){TileEntityCanopy tile=tiles.get(pos.add(x,0,z));if(tile!=null)dirty.add(tile);}
        positions.clear();
        for(TileEntityCanopy tile:dirty){
            if(tile.isInvalid()||!world.isBlockLoaded(tile.getPos())||world.getTileEntity(tile.getPos())!=tile){tiles.remove(tile.getPos(),tile);continue;}
            if(tile.chunkMesh instanceof FixedCanopyModel.Mesh&&((FixedCanopyModel.Mesh)tile.chunkMesh).matches(tile))continue;
            // The connection masks are logical, including released canopy proxies and the retained roof rail.
            String key=tile.mask()+":"+tile.roofMask()+":"+tile.facing()+":"+tile.large.key()+":"+tile.centeredPlacement+":"+tile.draws();
            FixedCanopyModel.Mesh mesh=meshes.getIfPresent(key);if(mesh==null){mesh=new FixedCanopyModel.Mesh(tile);meshes.put(key,mesh);}
            tile.chunkMesh=mesh;world.markBlockRangeForRenderUpdate(tile.getPos(),tile.getPos());
        }
    }
    private FixedCanopyChunks(){}
}
