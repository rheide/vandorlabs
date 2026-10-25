package com.vandorlabs.client;

import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** A carried read-only view has no vanilla tile ticks; emit decorative drive particles once per client tick. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class DriveClientEvents {
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){Minecraft mc=Minecraft.getMinecraft();if(event.phase!=TickEvent.Phase.END||mc.world==null||mc.isGamePaused())return;for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))if(!craft.isDead&&craft.view!=null)for(TileEntity tile:craft.view.tiles.values())if(tile instanceof TileEntityConstructor)((TileEntityConstructor)tile).update();}
    private DriveClientEvents(){}
}
