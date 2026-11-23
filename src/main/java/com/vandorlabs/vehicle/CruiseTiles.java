package com.vandorlabs.vehicle;

import com.vandorlabs.navigation.CruiseAtlas;
import com.vandorlabs.navigation.CruiseAtlas.Tile;
import com.vandorlabs.network.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.*;

/** Track delivery per rider, keeping horizon coverage under the pilot and moving the two finest layers ahead at cruise speed. */
final class CruiseTiles {
    private final Map<EntityPlayerMP,Set<Tile>> sent=new WeakHashMap<>();
    private List<Tile> footprint=Collections.emptyList();
    private int lastX,lastZ,lastStep,lastAheadX,lastAheadZ;
    void clear(){sent.clear();footprint=Collections.emptyList();lastStep=0;}
    void update(EntityGroundVehicle craft,double x,double z,double altitude){
        List<Entity> riders=craft.getPassengers();
        sent.keySet().retainAll(riders);
        if(riders.isEmpty())return;
        int step=CruiseAtlas.nearStep(altitude);
        int size=CruiseAtlas.SIZE*step;
        int tx=(int)Math.floor(x/size),tz=(int)Math.floor(z/size);
        double yaw=Math.toRadians(craft.rotationYaw);
        double forecast=craft.cruise.active()?CruiseAtlas.forecastDistance(craft.cruiseGroundSpeed(),step):0;
        int fx=craft.structure.facing.getFrontOffsetX(),fz=craft.structure.facing.getFrontOffsetZ();
        double aheadX=x+forecast*(fx*Math.cos(yaw)-fz*Math.sin(yaw));
        double aheadZ=z+forecast*(fx*Math.sin(yaw)+fz*Math.cos(yaw));
        int ax=(int)Math.floor(aheadX/size),az=(int)Math.floor(aheadZ/size);
        if(tx!=lastX||tz!=lastZ||step!=lastStep||ax!=lastAheadX||az!=lastAheadZ){
            lastAheadX=ax;lastAheadZ=az;
            lastX=tx;lastZ=tz;lastStep=step;
            footprint=footprint(x,z,step,aheadX,aheadZ);
        }
        CruiseAtlas atlas=CruiseAtlas.forWorld(craft.world);
        for(Entity rider:riders)if(rider instanceof EntityPlayerMP){
            EntityPlayerMP player=(EntityPlayerMP)rider;
            Set<Tile> known=sent.computeIfAbsent(player,k->new LinkedHashSet<>());

            int budget=3;
            for(Tile tile:footprint){
                if(known.contains(tile))continue;
                MessageCruiseTerrain image=atlas.request(craft.world,tile);
                if(image==null)continue;
                PacketHandler.INSTANCE.sendTo(new MessageCruiseTerrain(craft.getEntityId(),tile.x,tile.z,tile.step,image.pixels,image.heights),player);
                known.add(tile);
                if(known.size()>CruiseAtlas.RESIDENT_TILES)known.remove(known.iterator().next());
                if(--budget==0)break;
            }
        }
    }
    static List<Tile> footprint(double x,double z,int step,double aheadX,double aheadZ){
        List<Tile> result=new ArrayList<>(68);
        int[] levels=CruiseAtlas.scales(step);
        for(int ring=0;ring<=2;ring++)for(int scale:levels){
            if(ring>CruiseAtlas.radius(scale,step))continue;
            int span=CruiseAtlas.SIZE*scale;
            boolean detail=scale<=step*4;
            int cx=(int)Math.floor((detail?aheadX:x)/span),cz=(int)Math.floor((detail?aheadZ:z)/span);
            for(int i=-ring;i<=ring;i++)for(int j=-ring;j<=ring;j++){
                if(Math.max(Math.abs(i),Math.abs(j))==ring)
                    result.add(new Tile((cx+i)*span,(cz+j)*span,scale));
            }
        }
        return result;
    }

}
