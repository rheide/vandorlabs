package com.vandorlabs.navigation;

import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.redstone.*;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import com.vandorlabs.gear.*;
import java.util.*;

/** Loaded, formed observers; channel membership is an identity, never a redstone level. */
public final class NavigationSources {
    private NavigationSources(){}
    public static int radius(TileEntityConstructor tile){
        if(tile.core()==null||tile.layout()==null)return 0;String id=tile.core().design;
        int base=id.equals("standalone_antenna")?NavigationRange.SENTINEL_BASE:id.equals("standalone_dish")?NavigationRange.SURVEY_BASE:0;
        return NavigationRange.radius(base,position(tile).y,tile.observerScale());
    }
    /** The construction coordinates of a captured machine are not its physical height. */
    public static Vec3d position(TileEntityConstructor tile){
        GearTileEntity arm=tile.carriedArm;BlockPos p=tile.getPos();
        if(tile.getWorld() instanceof com.vandorlabs.vehicle.VehicleWorld&&((com.vandorlabs.vehicle.VehicleWorld)tile.getWorld()).navigationArm!=null)return NavigationVehicle.mapPoint(tile.getWorld(),new Vec3d(p.getX()+.5,p.getY(),p.getZ()+.5),p);
        return arm!=null&&arm.payload()!=null&&!arm.payload().materialized()?NavigationVehicle.mapPoint(arm.getWorld(),arm.payload().transform(arm,arm.progress(),p.getX()+.5,p.getY(),p.getZ()+.5).add(new Vec3d(arm.getPos())),arm.getPos()):NavigationVehicle.mapPoint(tile.getWorld(),new Vec3d(p.getX()+.5,p.getY(),p.getZ()+.5),p);
    }
    public static final class Area {
        public final int x,z,radius,radarRadius;
        final TileEntityConstructor stationary;
        final boolean sentinel;
        public Area(int x,int z,int radius){this(x,z,radius,0,null,false);}
        Area(int x,int z,int radius,int radarRadius,TileEntityConstructor stationary,boolean sentinel){
            this.x=x;this.z=z;this.radius=radius;this.radarRadius=radarRadius;this.stationary=stationary;this.sentinel=sentinel;
        }
        public boolean contains(int x,int z){return NavigationMap.covers(x,z,this.x,this.z,radius);}
    }
    private static final Map<World,List<TileEntityConstructor>> INDEX=new WeakHashMap<>();
    public static void register(TileEntityConstructor tile){if(tile.getWorld()!=null&&!tile.getWorld().isRemote&&radius(tile)>0){List<TileEntityConstructor> list=INDEX.computeIfAbsent(tile.getWorld(),w->new ArrayList<>());if(!list.contains(tile))list.add(tile);}}
    public static void remove(TileEntityConstructor tile){List<TileEntityConstructor> list=INDEX.get(tile.getWorld());if(list!=null)list.remove(tile);}
    public static void unload(World world){INDEX.remove(world);}
    public static List<Area> matching(World world,BlockPos display,ChannelList channels){
        return matching(world,display,channels,true);
    }
    public static boolean noPower(World world,BlockPos display,ChannelList channels){
        return !matching(world,display,channels,false).isEmpty()&&matching(world,display,channels,true).isEmpty();
    }
    private static List<Area> matching(World world,BlockPos display,ChannelList channels,boolean powered){
        List<Area> areas=new ArrayList<>();if(channels.isEmpty())return areas;Vec3d receiver=NavigationVehicle.mapPoint(world,new Vec3d(display.getX()+.5,display.getY(),display.getZ()+.5),display);com.vandorlabs.vehicle.EntityGroundVehicle own=NavigationVehicle.craft(world);world=NavigationVehicle.physicalWorld(world);List<TileEntityConstructor> list=INDEX.get(world);
        if(list!=null)for(TileEntityConstructor tile:list){
            if(tile.isInvalid()||!world.isBlockLoaded(tile.getPos(),false)||world.getTileEntity(tile.getPos())!=tile||!tile.formed())continue;
            append(areas,tile,receiver,channels,tile,powered);
        }
        for(GearTileEntity arm:GearGeometry.roots(world)){
            if(arm.isInvalid()||!arm.isOperational()||!world.isBlockLoaded(arm.getPos(),false)||world.getTileEntity(arm.getPos())!=arm||arm.payload()==null||arm.payload().materialized()||arm.hasFailedArmSnapshot())continue;
            int first=areas.size();
            try{for(TileEntityConstructor tile:arm.payload().navigationObservers(arm))if(tile.formed())append(areas,tile,receiver,channels,powered);}
            catch(RuntimeException|LinkageError unavailable){
                // Match collision discovery: a failed synthetic view retains its owned payload.
                areas.subList(first,areas.size()).clear();arm.quarantinePayload();
            }
        }if(own!=null)NavigationVehicle.observers(areas,own,receiver,channels,powered);for(com.vandorlabs.vehicle.EntityGroundVehicle craft:NavigationVehicle.vehicles(world))if(craft!=own)NavigationVehicle.observers(areas,craft,receiver,channels,powered);return areas;
    }
    static void append(List<Area> areas,TileEntityConstructor tile,Vec3d display,ChannelList channels,boolean powered){
        append(areas,tile,display,channels,null,powered);
    }
    private static void append(List<Area> areas,TileEntityConstructor tile,Vec3d display,ChannelList channels,TileEntityConstructor stationary,boolean powered){
        if(tile.observerPowered()!=powered)return;
        int radius=radius(tile),range=tile.transmissionRange();Vec3d p=position(tile);
        if(radius==0||range<0||p.squareDistanceTo(display)>range*(double)range)return;
        int radar=NavigationRange.radarRadius(tile.core().design);
        boolean sentinel=radar==NavigationRange.SENTINEL_RADAR_RADIUS;
        ChannelList source=tile.transmittedChannels();
        for(int c:channels.toArray())if(source.contains(c)){
            areas.add(new Area(net.minecraft.util.math.MathHelper.floor(p.x),net.minecraft.util.math.MathHelper.floor(p.z),
                    radius,tile.observerScanning()?radar:0,stationary,sentinel));
            break;
        }
    }
    public static boolean contains(List<Area> areas,int x,int z){for(Area area:areas)if(area.contains(x,z))return true;return false;}
}
