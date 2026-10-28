package com.vandorlabs.client;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

/** Focused mechanism matrices stay separate from unrelated 2.6 utility fixtures. */
public final class Mechanism27RuntimeChecks {
    private Mechanism27RuntimeChecks(){}
    public static void server(WorldServer world,EntityPlayerMP actor){
        com.vandorlabs.gear.Gear26RuntimeChecks.run(world,actor);
        RampAnalogRuntimeChecks.run(world,actor);
        UtilityStorageRuntimeChecks.run(world);
    }
    public static void client(Minecraft mc,File output)throws Exception{
        GearDialogChecks.run(mc,output);TrackClientChecks.run(mc,output);HexTankClientChecks.run(mc,output);
    }
}
