package com.vandorlabs.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

/** Finds actual collision-surface heights, including slabs, without loading chunks. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=com.vandorlabs.VandorLabs.MODID)
public final class SafeDismount {
    private static final Map<UUID,Exit> pending=new HashMap<>();
    private static final class Exit {final Vec3d position;final AxisAlignedBB area;final World world;Exit(Vec3d p,AxisAlignedBB b,World w){position=p;area=b.grow(2);world=w;}}
    private SafeDismount(){}
    public static Vec3d find(World world,Entity rider,AxisAlignedBB mount) {
        double half=rider.width/2D+.02;
        List<double[]> candidates=new ArrayList<>();
        double nearX=MathHelper.clamp(rider.posX,mount.minX,mount.maxX),nearZ=MathHelper.clamp(rider.posZ,mount.minZ,mount.maxZ);
        candidates.add(new double[]{mount.minX-half-.05,nearZ});candidates.add(new double[]{mount.maxX+half+.05,nearZ});
        candidates.add(new double[]{nearX,mount.minZ-half-.05});candidates.add(new double[]{nearX,mount.maxZ+half+.05});
        // A small search around the rider also handles narrow aisles and a blocked chair side.
        for(int radius=1;radius<=3;radius++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)
            if(Math.abs(x)==radius || Math.abs(z)==radius)candidates.add(new double[]{Math.floor(rider.posX)+.5+x,Math.floor(rider.posZ)+.5+z});
        candidates.add(new double[]{(mount.minX+mount.maxX)/2,(mount.minZ+mount.maxZ)/2});
        Vec3d best=null;double score=Double.MAX_VALUE;
        for(double[] point:candidates) {
            AxisAlignedBB column=new AxisAlignedBB(point[0]-half,mount.minY-2,point[1]-half,point[0]+half,mount.maxY+2,point[1]+half);
            if(!world.isAreaLoaded(new BlockPos(column.minX-1,column.minY-1,column.minZ-1),new BlockPos(column.maxX+1,column.maxY+rider.height+1,column.maxZ+1)))continue;
            for(AxisAlignedBB support:world.getCollisionBoxes(null,column)) {
                double y=support.maxY+.001;
                if(y<mount.minY-1 || y>mount.maxY+1)continue;
                AxisAlignedBB body=new AxisAlignedBB(point[0]-half,y,point[1]-half,point[0]+half,y+rider.height,point[1]+half);
                if(body.intersects(mount))continue;
                if(!world.getCollisionBoxes(rider,body).isEmpty())continue;
                // Require support below the centre, not a tiny corner brushing a wall.
                AxisAlignedBB foot=new AxisAlignedBB(point[0]-.1,y-.02,point[1]-.1,point[0]+.1,y,point[1]+.1);
                if(world.getCollisionBoxes(null,foot).isEmpty())continue;
                double distance=rider.getDistanceSq(point[0],y,point[1]);
                if(distance<score){score=distance;best=new Vec3d(point[0],y,point[1]);}
            }
        }
        return best;
    }
    public static void move(Entity rider,Vec3d position,AxisAlignedBB mount) {
        if(position==null)return;
        if(rider instanceof net.minecraft.entity.player.EntityPlayer)pending.put(rider.getUniqueID(),new Exit(position,mount,rider.world));
        rider.motionX=rider.motionY=rider.motionZ=0;rider.fallDistance=0;
        rider.setPositionAndUpdate(position.x,position.y,position.z);
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void finish(net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent event) {
        if(event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || event.player.world.isRemote)return;
        Exit exit=pending.remove(event.player.getUniqueID());
        // EntityLivingBase performs a second vanilla placement after removePassenger.
        // Correct it at the end of that same player tick, before another physics step.
        if(exit!=null && exit.world==event.player.world && exit.area.contains(event.player.getPositionVector()) && !event.player.isRiding()) {
            Vec3d position=exit.position;
            event.player.motionX=event.player.motionY=event.player.motionZ=0;event.player.fallDistance=0;
            event.player.setPositionAndUpdate(position.x,position.y,position.z);
        }
    }
}
