package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.world.WorldEvent;
import java.util.Map;

/** Unloading the client world must release group cache owners and their geometry. */
final class GroupCacheLifetimeChecks {
    static void run() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        NonRenderingChecks.MemoryWorld other=new NonRenderingChecks.MemoryWorld(true);
        BlockPos lightPos=new BlockPos(8,64,8),wallPos=new BlockPos(10,64,8);
        IBlockState lightState=new BlockProgrammableLight().getDefaultState();
        IBlockState wallState=new BlockProgrammableWall("cache_lifetime_porthole",BlockProgrammableWall.Shape.PORTHOLE).getDefaultState();
        world.setBlockState(lightPos,lightState,2);world.setBlockState(wallPos,wallState,2);
        TileEntityProgrammableLight light=(TileEntityProgrammableLight)world.getTileEntity(lightPos);
        TileEntityAnimatedScreenSelector wall=(TileEntityAnimatedScreenSelector)world.getTileEntity(wallPos);
        wall.setJoinPortholes(false);
        Object lightGroup=TEAnimatedScreenSelector.lightGroup(light,lightState);
        Object portholeGroup=TEAnimatedScreenSelector.portholeGroup(wall,wallState);
        require(field("lightCacheWorld")==world && field("portholeCacheWorld")==world,"group caches were not primed");
        DiagonalWallMeshCache.Events events=new DiagonalWallMeshCache.Events();
        events.worldUnloaded(new WorldEvent.Unload(other));
        require(TEAnimatedScreenSelector.lightGroup(light,lightState)==lightGroup
                && TEAnimatedScreenSelector.portholeGroup(wall,wallState)==portholeGroup,"unrelated unload discarded active groups");
        events.worldUnloaded(new WorldEvent.Unload(world));
        require(field("lightCacheWorld")==null && field("portholeCacheWorld")==null,"unloaded world retained by group renderer");
        require(((Map<?,?>)field("LIGHT_GROUPS")).isEmpty() && ((Map<?,?>)field("PORTHOLE_GROUPS")).isEmpty(),"unloaded group geometry retained");
        require(TEAnimatedScreenSelector.lightGroup(light,lightState)!=lightGroup
                && TEAnimatedScreenSelector.portholeGroup(wall,wallState)!=portholeGroup,"next world render reused unloaded groups");
        events.worldUnloaded(new WorldEvent.Unload(world));
        System.out.println("PASS: world unload releases light/porthole owners and groups, preserves unrelated worlds and rebuilds on next draw");
    }
    private static Object field(String name) {
        try{java.lang.reflect.Field field=TEAnimatedScreenSelector.class.getDeclaredField(name);field.setAccessible(true);return field.get(null);}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
