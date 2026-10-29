package com.vandorlabs.client;

import net.minecraft.client.Minecraft;

/** Isolated integrated-world Taxi support regression, without unrelated gameplay matrices. */
final class TaxiLandingClientChecks {
    private static int ticks;
    private static void sharedCacheChecks(net.minecraft.world.WorldServer world)throws Exception{
        java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new java.util.ArrayList<>();
        net.minecraft.util.math.BlockPos seat=net.minecraft.util.math.BlockPos.ORIGIN,anchor=new net.minecraft.util.math.BlockPos(8,2,8),gearPos=new net.minecraft.util.math.BlockPos(2,1,2);
        com.vandorlabs.tiles.TileEntityPilotSeat pilot=new com.vandorlabs.tiles.TileEntityPilotSeat();pilot.setPos(seat);cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(seat,com.vandorlabs.blocks.ModBlocks.PILOT_SEAT.getDefaultState(),pilot.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        com.vandorlabs.blocks.BlockCanopy canopy=(com.vandorlabs.blocks.BlockCanopy)net.minecraft.block.Block.getBlockFromName("vandorlabs:long_canopy");
        for(net.minecraft.util.math.BlockPos at:canopy.cells(anchor,net.minecraft.util.EnumFacing.NORTH,canopy.length)){com.vandorlabs.tiles.TileEntityCanopy tile=new com.vandorlabs.tiles.TileEntityCanopy();tile.setPos(at);tile.anchor=anchor;cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(at,canopy.getDefaultState(),tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));}
        net.minecraft.block.Block gear=null;for(net.minecraft.block.Block block:net.minecraft.block.Block.REGISTRY)if(block.getClass()==com.vandorlabs.blocks.BlockTelescopicLandingGear.class){gear=block;break;}if(gear==null)throw new IllegalStateException("Cache fixture needs ordinary landing gear");
        com.vandorlabs.tiles.TileEntityLandingGear landing=new com.vandorlabs.tiles.TileEntityLandingGear();landing.setPos(gearPos);cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(gearPos,gear.getDefaultState(),landing.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));
        com.vandorlabs.vehicle.EntityGroundVehicle craft=new com.vandorlabs.vehicle.EntityGroundVehicle(world);
        try{craft.install(new com.vandorlabs.vehicle.VehicleStructure(cells,seat,net.minecraft.util.EnumFacing.NORTH,1));VehicleCollisionCacheRuntimeChecks.run(craft);VehicleCanopyColdCacheRuntimeChecks.run(craft);}finally{craft.setDead();}
    }
    static boolean tick(Minecraft mc)throws Exception{
        if(++ticks==1){mc.displayGuiScreen(null);mc.gameSettings.pauseOnLostFocus=false;}
        if(ticks!=80)return false;
        mc.getIntegratedServer().callFromMainThread(()->{try{net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);if(Boolean.getBoolean("vandorlabs.checkVehicleCacheOnly")){sharedCacheChecks(world);return null;}if(Boolean.getBoolean("vandorlabs.checkSavedStuckTaxiOnly")){com.vandorlabs.vehicle.SavedStuckTaxiChecks.run(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}VehicleCombinedLightChecks.run(world);com.vandorlabs.vehicle.SavedTaxiFollowupChecks.paintingRollback4(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));com.vandorlabs.vehicle.SavedTaxiFollowupChecks.painting4(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));com.vandorlabs.vehicle.SavedStuckTaxiChecks.run(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));com.vandorlabs.vehicle.SavedTaxiFollowupChecks.run(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));com.vandorlabs.vehicle.TaxiLandingRuntimeChecks.run(world);com.vandorlabs.vehicle.TaxiModularRuntimeChecks.run(world,mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}catch(Exception failure){throw new RuntimeException(failure);}}).get();
        if(Boolean.getBoolean("vandorlabs.checkSavedStuckTaxiOnly")||Boolean.getBoolean("vandorlabs.checkVehicleCacheOnly")){System.out.println("[vandorlabs][reprolab] taxi-landing27 PASS focused-cache-or-saved-support");return true;}
        VehicleCombinedLightChecks.runClient(mc);
        TaxiVisibilityClientChecks.run(mc);
        System.out.println("[vandorlabs][reprolab] taxi-landing27 PASS");return true;
    }
}
