package com.vandorlabs.gear;

import com.vandorlabs.gear.core.GearTopology;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Pick moving parts at their posed positions while retaining their source identity. */
public final class GearInteractions {
    public static RayTraceResult trace(World world,Vec3d start,Vec3d end){
        RayTraceResult nearest=null;double distance=start.squareDistanceTo(end);
        for(GearTileEntity root:GearGeometry.roots(world)){
            if(root.isInvalid()||!root.isOperational()||!world.isBlockLoaded(root.getPos())||world.getTileEntity(root.getPos())!=root)continue;
            if(!root.getRenderBoundingBox().intersects(new AxisAlignedBB(start,end).grow(.001)))continue;
            java.util.List<AxisAlignedBB> boxes=root.collisionBoxes();int perFoot=root.assembly().kind()==GearTopology.FootKind.SKI?2:1,feetStart=boxes.size()-root.assembly().feet().size()*perFoot;
            for(int i=0;i<boxes.size();i++){
                if(boxes.get(i).getAverageEdgeLength()==0)continue;RayTraceResult hit=boxes.get(i).calculateIntercept(start,end);if(hit==null||start.squareDistanceTo(hit.hitVec)>distance)continue;
                BlockPos source=root.getPos();if(i>=feetStart){GearTopology.Cell foot=root.assembly().feet().get((i-feetStart)/perFoot);source=source.add(foot.x,foot.y,foot.z);}
                nearest=new RayTraceResult(hit.hitVec,hit.sideHit,source);distance=start.squareDistanceTo(hit.hitVec);
            }
        }
        RayTraceResult block=world.rayTraceBlocks(start,end,false,true,false);
        return nearest!=null&&(block==null||start.squareDistanceTo(block.hitVec)+.00001>=distance)?nearest:null;
    }
    public static void use(EntityPlayerMP player,BlockPos source){
        if(player.isSpectator()||player.isDead)return;Vec3d start=player.getPositionEyes(1),end=start.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(player.getEntityAttribute(net.minecraft.entity.player.EntityPlayer.REACH_DISTANCE).getAttributeValue()+.5));
        RayTraceResult hit=trace(player.world,start,end);if(hit==null||!hit.getBlockPos().equals(source))return;
        TileEntity raw=player.world.getTileEntity(source);GearTileEntity root=raw instanceof GearTileEntity?((GearTileEntity)raw).loadedController():null;if(root==null)return;
        for(BlockPos member:root.members())com.vandorlabs.vehicle.VehicleService.permission(player,member);
        if(player.getHeldItemMainhand().getItem()==com.vandorlabs.items.ModItems.CONFIGURIZER)player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_MODULAR_GEAR,player.world,root.getPos().getX(),root.getPos().getY(),root.getPos().getZ());
        else if(player.getHeldItemMainhand().isEmpty())root.target(!root.target());
    }
}
