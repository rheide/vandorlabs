package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityTurntableSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;

/** Defer local topology reads until chunk tile publication is complete. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="vandorlabs",value=net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class SurfaceArmClients {
    private static final Set<TileEntityTurntableSurface> pending=Collections.newSetFromMap(new IdentityHashMap<>());
    public static void changed(TileEntityTurntableSurface tile){SurfaceArmHandoff.changed(tile);if(tile.getWorld()!=null&&tile.getWorld().isRemote&&!(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld))synchronized(pending){pending.add(tile);}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;net.minecraft.world.World world=Minecraft.getMinecraft().world;Set<BlockPos> positions=new HashSet<>();synchronized(pending){for(TileEntityTurntableSurface tile:pending)if(world!=null&&tile.getWorld()==world&&!tile.isInvalid()&&world.isBlockLoaded(tile.getPos())&&world.getTileEntity(tile.getPos())==tile)positions.add(tile.getPos().toImmutable());pending.clear();}if(!positions.isEmpty())com.vandorlabs.gear.ArmSurfaceActivity.surfaceChanged(world,positions);}
    private SurfaceArmClients(){}
}
