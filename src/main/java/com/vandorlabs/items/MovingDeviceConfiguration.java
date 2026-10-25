package com.vandorlabs.items;

import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;

/** Precise Configurizer targeting without making inert displays block ordinary picking. */
public final class MovingDeviceConfiguration {
    private MovingDeviceConfiguration(){}
    public static BlockPos ownerAt(net.minecraft.world.World world,BlockPos at){
        TileEntity tile=world.getTileEntity(at);
        if(tile instanceof TileEntityConstructorPart)return ((TileEntityConstructorPart)tile).owner();
        if(tile instanceof TileEntityNavigationTable){TileEntityNavigationTable reader=((TileEntityNavigationTable)tile).reader();if(reader!=null)return reader.getPos();}
        if(tile instanceof TileEntityControlledRamp)return ((TileEntityControlledRamp)tile).controller;
        return at;
    }
    private static Vec3d local(EntityTurntable table,Vec3d point){
        double[] p=TurntableGeometry.rotate(point.x-table.posX,point.y-table.posY,point.z-table.posZ,table.snapshot.layout.basis,-table.angle());
        return new Vec3d(p[0]+table.posX-table.snapshot.origin.getX(),p[1]+table.posY-table.snapshot.origin.getY(),p[2]+table.posZ-table.snapshot.origin.getZ());
    }
    public static TileEntityTurntableController pointedTurntable(EntityPlayer player){
        Vec3d start=player.getPositionEyes(1),end=start.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(6));
        RayTraceResult terrain=player.world.rayTraceBlocks(start,end,false,true,false);double nearest=terrain==null?36:start.squareDistanceTo(terrain.hitVec);
        VehicleInteraction.Target vehicle=VehicleInteraction.pointed(player);if(vehicle!=null)nearest=Math.min(nearest,vehicle.hit.distanceSq);
        TileEntityTurntableController selected=null;
        for(EntityTurntable table:player.world.getEntitiesWithinAABB(EntityTurntable.class,new AxisAlignedBB(start.x,start.y,start.z,end.x,end.y,end.z).grow(32))){
            if(table.isDead||table.snapshot==null||table.owner()==null)continue;
            VehicleSelection.Hit hit=VehicleSelection.local(table.snapshot.blocks,table.view,local(table,start),local(table,end),nearest);
            if(hit!=null&&hit.distanceSq<=nearest){nearest=hit.distanceSq;selected=table.owner();}
        }
        return selected;
    }
    public static boolean nearOwnedPart(EntityPlayer player,TileEntity controller){
        if(controller instanceof TileEntityConstructor){
            TileEntityConstructor core=(TileEntityConstructor)controller;
            if(!core.formed()||core.getWorld()!=player.world)return false;
            com.vandorlabs.blocks.BlockConstructorCore block=core.core();
            for(int y=0;y<block.height;y++)for(int x=0;x<block.width;x++)for(int z=0;z<block.depth;z++){
                BlockPos at=core.cell(x,y,z);if(player.getDistanceSq(at)>64||!player.world.isBlockLoaded(at))continue;
                TileEntity tile=player.world.getTileEntity(at);if(tile==core||tile instanceof TileEntityConstructorPart&&((TileEntityConstructorPart)tile).owner().equals(core.getPos()))return true;
            }
        }
        if(controller instanceof TileEntityNavigationTable){
            TileEntityNavigationTable root=(TileEntityNavigationTable)controller;
            if(root.nearDisplayMember(player))return true;
        }
        if(player.getHeldItemMainhand().getItem()!=ModItems.CONFIGURIZER)return false;
        if(controller instanceof TileEntityRampController)for(BlockPos part:((TileEntityRampController)controller).vehicleCells()){
            if(player.getDistanceSq(part)>64||!player.world.isBlockLoaded(part))continue;
            TileEntity tile=player.world.getTileEntity(part);if(tile instanceof TileEntityControlledRamp&&((TileEntityControlledRamp)tile).belongsTo(controller.getPos()))return true;
        }
        if(controller instanceof TileEntityTurntableController){
            EntityTurntable table=((TileEntityTurntableController)controller).display();
            if(table!=null&&table.getEntityBoundingBox().grow(8).contains(player.getPositionEyes(1)))return true;
            TurntableSurfaceLayout layout=((TileEntityTurntableController)controller).layout;
            if(layout!=null)for(BlockPos part:layout.cells)if(player.getDistanceSq(part)<=64)return true;
        }
        return false;
    }
    public static boolean openTurntable(EntityPlayer player,TileEntityTurntableController owner){
        if(owner==null||!ConfigurationAccess.canEditDialog(player,player.world,owner.getPos()))return false;
        if(!player.world.isRemote)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_TURNTABLE,player.world,owner.getPos().getX(),owner.getPos().getY(),owner.getPos().getZ());
        return true;
    }
}
