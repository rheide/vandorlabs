package com.vandorlabs.vehicle;

import com.vandorlabs.tiles.TileEntityAutopilot;
import com.vandorlabs.tiles.TileEntityWaypoint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import java.util.*;

/** Bounded status discovery and authoritative screen controls. */
public final class VehicleStatus {
    public static final int FLIGHT_ACTION=5, EMERGENCY_PARK_ACTION=6;
    private static final Map<World,Map<BlockPos,Entry>> cache=new WeakHashMap<>();
    private static final class Entry {final long at;final BlockPos controller,vehicle;Entry(long at,BlockPos controller,BlockPos vehicle){this.at=at;this.controller=controller;this.vehicle=vehicle;}}
    private VehicleStatus(){}
    public static void activate(World world,BlockPos screen,net.minecraft.entity.player.EntityPlayer actor,int action){
        if(!(actor instanceof net.minecraft.entity.player.EntityPlayerMP))return;
        net.minecraft.entity.player.EntityPlayerMP player=(net.minecraft.entity.player.EntityPlayerMP)actor;
        EntityGroundVehicle craft=world instanceof VehicleWorld?((VehicleWorld)world).craft:null;
        try{
            if(!hasController(world,screen))return;
            if(player.isDead||player.isSpectator()||craft!=null&&(craft.isDead||craft.ownershipBlocked||craft.transferInProgress))return;
            BlockPos at=craft==null?screen:new BlockPos(craft.toWorld(new net.minecraft.util.math.Vec3d(screen).addVector(.5,.5,.5)));
            if(player.world!=(craft==null?world:craft.world)||player.getDistanceSqToCenter(at)>64)return;
            if(craft==null)VehicleService.permission(player,at);else VehicleService.carriedPermission(player,craft,at);
            if(action>=1&&action<=3){VehicleService.screenMode(player,screen,craft,action-1);return;}
            if(action==FLIGHT_ACTION){VehicleService.screenMode(player,screen,craft,3);return;}
            if(action==EMERGENCY_PARK_ACTION){VehicleService.screenEmergencyPark(player,craft);return;}
            TileEntityAutopilot controller=find(world,screen);if(controller==null)return;
            if(craft!=null&&(craft.flying()||craft.takeoffRoll()))return;
            VehicleService.permission(player,craft==null?controller.getPos():new BlockPos(craft.toWorld(new net.minecraft.util.math.Vec3d(controller.getPos()).addVector(.5,.5,.5))));
            if(action==0){if(craft==null)controller.setEnabled(!controller.enabled());else craft.autopilot.toggle();}
            else if(action==4){if(craft==null)controller.pulseDeparture();else craft.autopilot.pulseDeparture();}
        }catch(IllegalArgumentException denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    public static boolean offersFlight(World world){
        if(!(world instanceof VehicleWorld))return false;
        EntityGroundVehicle craft=((VehicleWorld)world).craft;
        return craft!=null&&(craft.flying()||craft.flightReadout().drives>0);
    }
    public static boolean offersEmergencyPark(World world,net.minecraft.entity.player.EntityPlayer player){
        EntityGroundVehicle craft=world instanceof VehicleWorld?((VehicleWorld)world).craft:null;
        return craft!=null&&!craft.transferInProgress&&!craft.cruise.active()&&player!=null&&player.capabilities.isCreativeMode
                &&player.getHeldItemMainhand().getItem() instanceof com.vandorlabs.items.ItemConfigurizer;
    }
    public static int modeLeft(int mode,boolean flight){return 6+mode*(flight?29:39);}
    public static int modeRight(int mode,boolean flight){return modeLeft(mode,flight)+(flight?27:37);}
    public static com.vandorlabs.tiles.TileEntityVehicleController controller(World world,BlockPos screen){
        BlockPos at=world instanceof VehicleWorld?VehicleControlSettings.position(((VehicleWorld)world).structure):entry(world,screen).vehicle;
        TileEntity raw=at==null||!world.isBlockLoaded(at)?null:world.getTileEntity(at);return raw instanceof com.vandorlabs.tiles.TileEntityVehicleController?(com.vandorlabs.tiles.TileEntityVehicleController)raw:null;
    }
    public static boolean hasController(World world,BlockPos screen){
        if(world instanceof VehicleWorld){VehicleStructure structure=((VehicleWorld)world).structure;return structure!=null&&structure.hasDedicatedController();}
        Entry entry=entry(world,screen);return entry.vehicle!=null&&world.isBlockLoaded(entry.vehicle)&&world.getBlockState(entry.vehicle).getBlock() instanceof com.vandorlabs.blocks.BlockVehicleController;
    }
    public static TileEntityAutopilot find(World world,BlockPos screen){
        if(world instanceof VehicleWorld){EntityGroundVehicle craft=((VehicleWorld)world).craft;return craft==null?null:craft.autopilot.controller();}
        Entry entry=entry(world,screen);TileEntity tile=entry.controller==null||!world.isBlockLoaded(entry.controller)?null:world.getTileEntity(entry.controller);return tile instanceof TileEntityAutopilot?(TileEntityAutopilot)tile:null;
    }
    private static Entry entry(World world,BlockPos screen){
        Map<BlockPos,Entry> entries=cache.computeIfAbsent(world,w->new HashMap<>());Entry old=entries.get(screen);
        if(old!=null&&world.getTotalWorldTime()-old.at>=0&&world.getTotalWorldTime()-old.at<100)return old;
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();Set<BlockPos> seen=new HashSet<>();queue.add(screen);seen.add(screen);
        BlockPos autopilot=null,controller=null;int autopilots=0,budget=4096;
        while(!queue.isEmpty()&&budget-->0){BlockPos p=queue.removeFirst();if(!world.isBlockLoaded(p)||world.isAirBlock(p))continue;TileEntity tile=world.getTileEntity(p);
            int navigation=com.vandorlabs.rack.ModuleAutopilots.count(tile);if(navigation>0){autopilots+=navigation;autopilot=p;}
            net.minecraft.block.state.IBlockState state=world.getBlockState(p);if(state.getBlock() instanceof com.vandorlabs.blocks.BlockVehicleController)controller=p;

            if(state.getBlock() instanceof com.vandorlabs.blocks.BlockLandingGear)continue;
            if(state.getBlock() instanceof com.vandorlabs.blocks.BlockPropulsionLight&&state.getValue(com.vandorlabs.blocks.BlockPropulsionLight.FACING)==net.minecraft.util.EnumFacing.DOWN)continue;
            try{for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.values())if(VehicleScan.touches(world,p,state,face)){BlockPos next=p.offset(face);if(world.isBlockLoaded(next)&&!VehicleScan.ownedCoupler(state,world.getBlockState(next),face)&&seen.add(next))queue.add(next);}}catch(RuntimeException|LinkageError unavailable){VehicleCompatibility.warn(state.getBlock(),"status connectivity",unavailable);}
        }
        if(entries.size()>256)entries.clear();Entry result=new Entry(world.getTotalWorldTime(),autopilots==1?autopilot:null,controller);entries.put(screen.toImmutable(),result);return result;
    }
}
