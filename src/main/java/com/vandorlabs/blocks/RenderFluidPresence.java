package com.vandorlabs.blocks;

import com.vandorlabs.world.NeighbourhoodCache;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import java.util.*;

/** Render hints only. Stored fluids stay live; only native-water adjacency is cached. */
@Mod.EventBusSubscriber(modid="vandorlabs")
public final class RenderFluidPresence {
    private static final EnumFacing[] FACES=EnumFacing.values();
    private static final Map<World,Cache> WORLDS=new WeakHashMap<>();
    private RenderFluidPresence(){}
    private static final class Cache extends NeighbourhoodCache<Integer> {
        final Map<BlockPos,Integer> masks=entries;
        Cache(){super(2048);}
        @Override public void notifyBlockUpdate(World world,BlockPos pos,IBlockState before,IBlockState after,int flags){
            // Tile configuration packets do not change native-water adjacency.
            if((before.getMaterial()==net.minecraft.block.material.Material.WATER)!=(after.getMaterial()==net.minecraft.block.material.Material.WATER))invalidate(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());
        }
    }
    public static boolean wet(TileEntity tile){
        World world=tile.getWorld();if(world==null||tile.isInvalid())return false;
        ContainedFluids stored=WaterloggedParts.data(tile);if(stored==null)return false;
        IBlockState state=tile.getBlockType().getDefaultState();
        // Walls and glazing only need their live stored-fluid bit. They never
        // participate in the aperture adjacency cache.
        if(!OpeningFluids.supports(state))return stored.any()&&WaterloggedParts.supports(state);
        if(stored.any())return true;
        // Simulation views replace their snapshots without normal world callbacks.
        if(!world.isRemote||world instanceof com.vandorlabs.vehicle.VehicleWorld)return WaterloggedParts.wet(world,tile.getPos());
        Cache cache=WORLDS.get(world);if(cache==null){cache=new Cache();WORLDS.put(world,cache);world.addEventListener(cache);}
        BlockPos pos=tile.getPos();Integer mask=cache.masks.get(pos);
        if(mask==null){int bits=0;for(EnumFacing face:FACES){BlockPos next=pos.offset(face);if(world.isBlockLoaded(next)&&world.getBlockState(next).getMaterial()==net.minecraft.block.material.Material.WATER)bits|=1<<face.getIndex();}mask=bits;cache.masks.put(pos.toImmutable(),mask);}
        if(mask==0)return false;
        // With no stored fluid, either ambient side makes the aperture wet.
        // Open/closed mirroring cannot change that OR. Evaluate its current plane
        // so facing, hinge, root and trapdoor edits never need a geometry cache.
        double[] plane=OpeningFluids.plane(world,pos);
        for(EnumFacing face:FACES)if((mask&(1<<face.getIndex()))!=0&&Math.abs(plane[0]*face.getFrontOffsetX()+plane[1]*face.getFrontOffsetY()+plane[2]*face.getFrontOffsetZ())>.1)return true;
        return false;
    }
    @SubscribeEvent public static void unloaded(WorldEvent.Unload event){if(!event.getWorld().isRemote)return;Cache cache=WORLDS.remove(event.getWorld());if(cache!=null)event.getWorld().removeEventListener(cache);}
    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event){chunkChanged(event);}
    @SubscribeEvent public static void chunkUnloaded(ChunkEvent.Unload event){chunkChanged(event);}
    private static void chunkChanged(ChunkEvent event){if(!event.getWorld().isRemote)return;Cache cache=WORLDS.get(event.getWorld());if(cache!=null){int x=event.getChunk().x<<4,z=event.getChunk().z<<4;cache.invalidate(x,0,z,x+15,255,z+15);}}
}
