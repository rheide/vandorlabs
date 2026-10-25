package com.vandorlabs.drives;

import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.MachineMountSurface;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.vehicle.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Pure cosmetic output; no flight detector or movement input consumes these settings. */
public final class DriveParticles {
    private DriveParticles(){}
    public static Vec3d center(TileEntityConstructor tile){double z=tile.core().design.equals("longburn_drive")?tile.core().depth-.05:.05;Vec3d local=new Vec3d(tile.core().width*.5,tile.core().height*.55,z);return MachineMountSurface.rotate(local,tile.facing(),tile.core().width,tile.core().depth).add(new Vec3d(tile.getPos().subtract(tile.pivot())));}
    public static EnumFacing direction(TileEntityConstructor tile){return tile.core().design.equals("longburn_drive")?tile.facing().getOpposite():tile.facing();}
    public static int color(TileEntityConstructor tile,int index){return tile.core().design.equals("longburn_drive")?(index%3==0?0xFFFFFF:0xFFE29A):tile.core().design.equals("meridian_core")?0x66EEFF:0xA3CFFF;}
    public static void emit(TileEntityConstructor tile){
        World world=tile.getWorld();if(world==null||!world.isRemote||!tile.renderReady()||!tile.driveHasParticles()||!tile.screenOn())return;DriveSettings settings=tile.driveSettings();if(!settings.plume(true,tile.driveLevel()))return;Vec3d point=center(tile);EnumFacing face=direction(tile);EntityGroundVehicle craft=world instanceof VehicleWorld?((VehicleWorld)world).craft:null;if(world instanceof VehicleWorld&&(craft==null||craft.ownershipBlocked||craft.transferInProgress))return;
        int count=settings.count(tile.driveLevel());float speed=.25F,wide=settings.particles==2?1.75F:2.75F;
        if(craft!=null){point=craft.toWorld(point);world=craft.world;}
        for(int i=0;i<count;i++){float spread=i<8?.95F:.95F*wide;if(craft==null)VandorLabs.proxy.spawnThrusterParticle(world,point.x,point.y,point.z,face,color(tile,i),0,speed,spread);else VandorLabs.proxy.spawnRotatedThrusterParticle(world,point.x,point.y,point.z,face,color(tile,i),0,speed,spread,craft.rotationYaw);}
    }
}
