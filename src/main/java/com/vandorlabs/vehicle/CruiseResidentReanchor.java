package com.vandorlabs.vehicle;

import com.vandorlabs.config.BlockAvailability;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** A read-only check of the real, already resident destination before a periodic high-flight hop. */
final class CruiseResidentReanchor {
    private static final int INTERVAL = 40;
    private static final double DISTANCE = 64;

    private CruiseResidentReanchor() {}

    static boolean ready(EntityGroundVehicle craft,double destinationX,double destinationY,double destinationZ,float yaw,float pitch,float bank) {
        return craft.ticksExisted%INTERVAL==0&&footprintReady(craft,destinationX,destinationY,destinationZ,yaw,pitch,bank);
    }

    static boolean footprintReady(EntityGroundVehicle craft,double destinationX,double destinationY,double destinationZ,float yaw,float pitch,float bank) {
        if(!BlockAvailability.reanchorResidentCruise()||!(craft.world instanceof WorldServer)
            ||!(craft.getControllingPassenger() instanceof EntityPlayerMP))return false;
        double dx=destinationX-craft.posX, dz=destinationZ-craft.posZ;
        if(dx*dx+dz*dz<DISTANCE*DISTANCE)return false;
        WorldServer world=(WorldServer)craft.world;
        if (Access.CHUNKS==null || Access.VIEW==null) return false;
        try {
            @SuppressWarnings("unchecked")
            Long2ObjectMap<Chunk> loaded=(Long2ObjectMap<Chunk>)Access.CHUNKS.get(world.getChunkProvider());
            int x=MathHelper.floor(destinationX)>>4, z=MathHelper.floor(destinationZ)>>4;
            if (!resident(loaded,x,z)) return false;
            int radius=Access.VIEW.getInt(world.getPlayerChunkMap());
            if (radius<3 || radius>32) return false;
            // The teleport applies the current seats before the new flight attitude takes effect.
            AxisAlignedBB hull=craft.getEntityBoundingBox().offset(dx,0,dz).union(craft.flightBounds(
                new VehiclePose(destinationX,destinationY,destinationZ,yaw,pitch,bank,craft.structure.facing)));
            if (!world.getWorldBorder().contains(new BlockPos(hull.minX,0,hull.minZ))
                || !world.getWorldBorder().contains(new BlockPos(hull.maxX,0,hull.maxZ))) return false;
            // Cover all seats and the rotated hull. The extra column allows vanilla's
            // truncate-to-int watch coordinate and section-boundary rounding.
            int minX=(MathHelper.floor(hull.minX)>>4)-radius-1, maxX=(MathHelper.floor(hull.maxX)>>4)+radius+1;
            int minZ=(MathHelper.floor(hull.minZ)>>4)-radius-1, maxZ=(MathHelper.floor(hull.maxZ)>>4)+radius+1;
            for (int cx=minX;cx<=maxX;cx++) for (int cz=minZ;cz<=maxZ;cz++)
                if (!resident(loaded,cx,cz)) return false;
            for (Entity rider:craft.getPassengers())
                if (rider instanceof EntityPlayerMP && (rider.isDead || rider.world!=world)) return false;
            return true;
        } catch (ReflectiveOperationException | RuntimeException incompatible) {
            return false;
        }
    }

    static Chunk loaded(WorldServer world,int x,int z) {
        if(Access.CHUNKS==null)return null;
        try {
            @SuppressWarnings("unchecked")
            Long2ObjectMap<Chunk> chunks=(Long2ObjectMap<Chunk>)Access.CHUNKS.get(world.getChunkProvider());
            return resident(chunks,x,z)?chunks.get(ChunkPos.asLong(x,z)):null;
        } catch(ReflectiveOperationException|RuntimeException incompatible) {return null;}
    }

    private static boolean resident(Long2ObjectMap<Chunk> loaded,int x,int z) {
        Chunk chunk=loaded.get(ChunkPos.asLong(x,z));
        return chunk!=null && chunk.isLoaded() && chunk.isTerrainPopulated() && !chunk.unloadQueued;
    }

    private static final class Access {
        // getLoadedChunk clears unloadQueued in Forge. Raw peeking leaves the provider untouched.
        static final Field CHUNKS=field(ChunkProviderServer.class,"loadedChunks","field_73244_f");
        static final Field VIEW=field(PlayerChunkMap.class,"playerViewRadius","field_72698_e");

        private static Field field(Class<?> owner,String name,String srg) {
            try {return ReflectionHelper.findField(owner,name,srg);}
            catch (RuntimeException incompatible) {return null;}
        }
    }
}
