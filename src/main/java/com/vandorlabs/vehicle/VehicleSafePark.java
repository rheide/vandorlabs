package com.vandorlabs.vehicle;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Creative recovery uses the ordinary transfer journal and never changes the live joint pose. */
final class VehicleSafePark {
    private VehicleSafePark(){}
    static void submit(EntityPlayerMP player,EntityGroundVehicle craft,BlockPos selected){
        WorldServer world=player.getServerWorld();
        VehicleTransfer.submit(world,new VehicleTransfer.Action(){
            private VehicleStructure source;private VehicleRotation.Plan plan;private VehicleWorld placedView;private VehicleRotation.Validation validation;private boolean validated;private VehicleSafeParkSearch search,verification;private int turns,slices;
            public void run()throws Exception {
                if(++slices>1200)throw new IllegalArgumentException("Safe Park timed out; craft preserved. Move the craft closer to a clear, flat area and try again.");
                if(player.world!=world||craft.world!=world)throw new IllegalArgumentException("Craft changed dimension during Safe Park.");
                requireAuthorized(player,craft,selected);
                if(source==null){
                    if(craft.transferInProgress)throw new IllegalArgumentException("Wait for the current vehicle transfer.");
                    if(!Float.isFinite(craft.rotationYaw))throw new IllegalArgumentException("Invalid craft heading");
                    craft.signals.persist();
                    turns=Math.floorMod(Math.round(MathHelper.wrapDegrees(craft.rotationYaw)/90F),4);
                    plan=VehicleRotation.safePlan(craft,turns);source=craft.structure;
                    VehicleTransfer.holdForPreflight(craft);
                }
                if(craft.structure!=source)throw new IllegalArgumentException("Craft changed during Safe Park; inspect it again.");
                if(validation==null){placedView=new VehicleWorld(world,plan.structure);validation=new VehicleRotation.Validation(plan.structure,placedView);}
                if(!validated){validated=validation.step(256,System.nanoTime()+2_000_000L);if(!validated){VehicleTransfer.deferPreflight();return;}}
                VehicleCollision geometry=VehicleTransfer.geometry(world,plan.structure);
                if(search==null)search=new VehicleSafeParkSearch(VehicleService.parkingTargets(placedView),geometry.boxes,plan.origin,16,world.getHeight(),VehicleSafeParkSearch.access(world,craft,player));
                if(!search.step(256,System.nanoTime()+2_000_000L)){VehicleTransfer.deferPreflight();return;}
                BlockPos origin=search.result();
                if(origin==null)throw new IllegalArgumentException("No loaded, flat area fits this craft within 16 horizontal blocks. Craft preserved.");
                if(verification==null)verification=search.recheck();
                if(!verification.step(256,System.nanoTime()+2_000_000L)){VehicleTransfer.deferPreflight();return;}
                if(verification.result()==null)throw new IllegalArgumentException("The Safe Park area changed; craft preserved. Try again.");
                VehicleService.validatePreparedParking(player,craft,plan.structure,origin);
                try{VehicleTransfer.parkPrepared(world,craft,origin,player,true,plan.structure,turns);}
                finally{verification=null;} // Disk preparation can yield: recheck the complete destination before committing.
                player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Safe Park: craft aligned and restored as blocks."),true);
            }
        },failure->VehicleNetwork.error(player,failure.getMessage()));
    }
    static void requireAuthorized(EntityPlayerMP player,EntityGroundVehicle craft,BlockPos selected){
        if(player.isDead||player.isSpectator()||!player.capabilities.isCreativeMode||!(player.getHeldItemMainhand().getItem() instanceof com.vandorlabs.items.ItemConfigurizer)
                ||craft==null||craft.isDead||craft.ownershipBlocked||craft.world!=player.world||craft.structure==null)
            throw new IllegalArgumentException("Safe Park requires Creative Mode and the Configurizer.");
        Vec3d point=craft.toWorld(new Vec3d(selected).addVector(.5,.5,.5));
        if(player.getDistanceSq(point.x,point.y,point.z)>64)throw new IllegalArgumentException("Move closer to the Vehicle Controller.");
        VehicleService.permission(player,new BlockPos(point));
    }
}
