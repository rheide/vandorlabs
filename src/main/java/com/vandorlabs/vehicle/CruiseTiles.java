package com.vandorlabs.vehicle;

import com.vandorlabs.navigation.CruiseAtlas;
import com.vandorlabs.navigation.CruiseAtlas.Tile;
import com.vandorlabs.network.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.*;

/** Track delivery per rider, prioritizing coverage directly under the aircraft at each scale. */
final class CruiseTiles {
    private final Map<EntityPlayerMP,Set<Tile>> sent=new WeakHashMap<>();
    private List<Tile> footprint=Collections.emptyList();
    private int lastX,lastZ,lastStep;
    void clear(){sent.clear();footprint=Collections.emptyList();lastStep=0;}
    void update(EntityGroundVehicle craft,double x,double z,double altitude){
        List<Entity> riders=craft.getPassengers();
        sent.keySet().retainAll(riders);
        if(riders.isEmpty())return;
        int step=CruiseAtlas.nearStep(altitude);
        int size=CruiseAtlas.SIZE*step;
        int tx=(int)Math.floor(x/size),tz=(int)Math.floor(z/size);
        if(tx!=lastX||tz!=lastZ||step!=lastStep){
            lastX=tx;lastZ=tz;lastStep=step;
            footprint=new ArrayList<>(27);
            for(int radius=0;radius<=1;radius++)
                for(int scale:new int[]{step,step*16,step*4}){
                    int span=CruiseAtlas.SIZE*scale;
                    int baseX=(int)Math.floor(x/span),baseZ=(int)Math.floor(z/span);
                    for(int i=-radius;i<=radius;i++)for(int j=-radius;j<=radius;j++){
                        if(Math.max(Math.abs(i),Math.abs(j))==radius)
                            footprint.add(new Tile((baseX+i)*span,(baseZ+j)*span,scale));
                    }
                }

        }
        CruiseAtlas atlas=CruiseAtlas.forWorld(craft.world);
        for(Entity rider:riders)if(rider instanceof EntityPlayerMP){
            EntityPlayerMP player=(EntityPlayerMP)rider;
            Set<Tile> known=sent.computeIfAbsent(player,k->new LinkedHashSet<>());

            int budget=3;
            for(Tile tile:footprint){
                if(known.contains(tile))continue;
                int[] pixels=atlas.request(craft.world,tile);
                if(pixels==null)continue;
                PacketHandler.INSTANCE.sendTo(new MessageCruiseTerrain(craft.getEntityId(),tile.x,tile.z,tile.step,pixels),player);
                known.add(tile);
                if(known.size()>96)known.remove(known.iterator().next());
                if(--budget==0)break;
            }
        }
    }
}
