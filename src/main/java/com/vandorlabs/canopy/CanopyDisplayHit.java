package com.vandorlabs.canopy;

import com.vandorlabs.screen.VehicleInstrument;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.vehicle.EntityGroundVehicle;
import com.vandorlabs.vehicle.VehicleStatus;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Raycast the real posed glazing and address the same display coordinates used for drawing. */
public final class CanopyDisplayHit {
    private CanopyDisplayHit() {}

    public static int height(CanopyMesh.Face face) { return face.sideGlass ? 64 : 128; }

    public static boolean mirrored(CanopyMesh.Face face,int b,int c) {
        double[] first=face.uv[0],second=face.uv[b],third=face.uv[c];
        return (second[0]-first[0])*(third[1]-first[1])-(second[1]-first[1])*(third[0]-first[0])<0;
    }

    public static boolean use(TileEntityCanopy tile,EntityPlayer player,boolean emergencyOnly) {
        if(tile==null||player==null||player.isSneaking())return false;
        TileEntityCanopy root=tile.owner();
        if(!root.large.hasDisplay())return false;
        double reach=Math.min(8,player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue());
        Vec3d start=com.vandorlabs.vehicle.VehicleInteraction.eyes(player);
        Vec3d end=start.add(com.vandorlabs.vehicle.VehicleInteraction.look(player).scale(reach));
        if(root.getWorld() instanceof VehicleWorld){
            EntityGroundVehicle craft=((VehicleWorld)root.getWorld()).craft;
            if(craft==null)return false;
            start=craft.toLocal(start,root.getPos());end=craft.toLocal(end,root.getPos());
        }
        RayTraceResult impact=root.trace(start,end);
        if(impact==null)return false;
        EnumFacing facing=root.facing();
        EnumFacing inverse=facing==EnumFacing.EAST?EnumFacing.WEST:facing==EnumFacing.WEST?EnumFacing.EAST:facing;
        Vec3d origin=new Vec3d(root.getPos());
        Vec3d local=CanopyMesh.rotate(impact.hitVec.subtract(origin),inverse);
        Vec3d eye=CanopyMesh.rotate(start.subtract(origin),inverse);
        for(CanopyMesh.Face face:root.faces()){
            if(face.display==null)continue;
            for(int i=1;i<face.vertices.length-1;i++){
                double[] pixel=pixel(face,i,i+1,local,eye);
                if(pixel==null)continue;
                if(face.display!=VehicleInstrument.STATUS)return !emergencyOnly;
                int action=VehicleStatus.panelAction(height(face),pixel[0],pixel[1]);
                if(emergencyOnly!=(action==VehicleStatus.EMERGENCY_PARK_ACTION))return false;
                if(action>=0)VehicleStatus.activate(root.getWorld(),root.getPos(),player,action);
                return true;
            }
        }
        return false;
    }

    private static double[] pixel(CanopyMesh.Face face,int b,int c,Vec3d point,Vec3d eye){
        Vec3d origin=face.vertices[0];
        Vec3d u=face.vertices[b].subtract(origin),v=face.vertices[c].subtract(origin);
        Vec3d normal=u.crossProduct(v),offset=point.subtract(origin);
        double norm=normal.lengthSquared();
        if(norm<1e-16||Math.abs(normal.dotProduct(offset))>1e-5*Math.sqrt(norm))return null;
        double s=offset.crossProduct(v).dotProduct(normal)/norm;
        double t=u.crossProduct(offset).dotProduct(normal)/norm;
        if(s< -1e-6||t< -1e-6||s+t>1+1e-6)return null;
        double[] first=face.uv[0],second=face.uv[b],third=face.uv[c];
        double x=first[0]+s*(second[0]-first[0])+t*(third[0]-first[0]);
        double y=first[1]+s*(second[1]-first[1])+t*(third[1]-first[1]);
        boolean reverse=normal.dotProduct(eye.subtract(origin))<0;
        if(mirrored(face,b,c)!=reverse)x=1-x;
        return new double[]{128*x,height(face)*(1-y)};
    }
}
