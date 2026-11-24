package com.vandorlabs.vehicle;

import com.vandorlabs.screen.VehicleStatusLayout;
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
    private static final class Entry {final long at;final BlockPos controller,vehicle;final int modes;Entry(long at,BlockPos controller,BlockPos vehicle,int modes){this.at=at;this.controller=controller;this.vehicle=vehicle;this.modes=modes;}}
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
            if(action>=com.vandorlabs.screen.SpeedAutopilotPanel.TOGGLE&&action<=com.vandorlabs.screen.SpeedAutopilotPanel.INCREASE){
                if(craft!=null&&craft.getControllingPassenger()==player)craft.speedControl(action);
                return;
            }
            int mode=action==FLIGHT_ACTION?VehicleStatusLayout.FLIGHT:action>=1&&action<=3?action-1:-1;
            if(mode>=0){
                if (craft!=null && highModeLocked(craft,mode))
                    throw new IllegalArgumentException("Descend to Y=256 or below before selecting Taxi or Hover.");
                // A parked inspection may predate a cargo change. Always remeasure a Hover request.
                if((craft==null&&mode==VehicleStatusLayout.HOVER)||(availableModes(world,screen,player)&(1<<mode))!=0)
                    VehicleService.screenMode(player,screen,craft,mode);
                return;
            }
            if(action==EMERGENCY_PARK_ACTION){if(offersEmergencyPark(world,player))VehicleService.screenEmergencyPark(player,craft);return;}
            TileEntityAutopilot controller=find(world,screen);if(controller==null)return;
            if(craft!=null&&(craft.flying()||craft.takeoffRoll()))return;
            VehicleService.permission(player,craft==null?controller.getPos():new BlockPos(craft.toWorld(new net.minecraft.util.math.Vec3d(controller.getPos()).addVector(.5,.5,.5))));
            if(action==0){if(craft==null)controller.setEnabled(!controller.enabled());else craft.autopilot.toggle();}
            else if(action==4){if(craft==null)controller.pulseDeparture();else craft.autopilot.pulseDeparture();}
        }catch(IllegalArgumentException denied){VehicleNetwork.error(player,denied.getMessage());}
    }
    static boolean highModeLocked(EntityGroundVehicle craft,int mode) {
        return (mode==VehicleStatusLayout.TAXI || mode==VehicleStatusLayout.HOVER) && craft.flying()
                && craft.flightAltitude()>com.vandorlabs.physics.FlightEnvelope.WORLD_CEILING;
    }
    public static int availableModes(World world,BlockPos screen,net.minecraft.entity.player.EntityPlayer player){
        EntityGroundVehicle craft=world instanceof VehicleWorld?((VehicleWorld)world).craft:null;
        if(craft==null){
            com.vandorlabs.tiles.TileEntityVehicleController root=controller(world,screen);
            if(root==null||root.converting)return 0;
            return entry(world,screen).modes;
        }
        if(craft.structure==null||craft.transferInProgress||craft.ownershipBlocked||craft.isDead)return 0;
        int selected=craft.takeoffRoll()?VehicleStatusLayout.FLIGHT:craft.vehicleMode();
        int modes=1<<selected;
        boolean transitioning=craft.flying()||craft.takeoffRoll();
        boolean taxi=transitioning||VehicleSkiTraction.allowed(craft)&&(!craft.hover||craft.groundClearance()<=3&&VehicleLandingGear.extended(craft));
        if(taxi)modes|=1<<VehicleStatusLayout.TAXI;
        if(craft.hoverAvailable())modes|=1<<VehicleStatusLayout.HOVER;
        if(!transitioning&&!craft.autopilot.enabled()&&craft.flightReadout().canFly()&&player!=null&&craft.getControllingPassenger()==player
                &&(craft.hover||taxi&&(craft.onGround||craft.groundClearance()<=.001)&&VehicleLandingGear.extended(craft)))modes|=1<<VehicleStatusLayout.FLIGHT;
        if((!transitioning||craft.flightLanded())&&!craft.cruise.active()&&craft.stopped()
                &&(!craft.hover||craft.groundClearance()<=3&&VehicleLandingGear.extended(craft))
                &&Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(craft.rotationYaw-Math.round(craft.rotationYaw/90F)*90F))<=12)
            modes|=1<<VehicleStatusLayout.PARK;
        if (highModeLocked(craft,VehicleStatusLayout.TAXI))
            modes &= ~((1<<VehicleStatusLayout.TAXI)|(1<<VehicleStatusLayout.HOVER));
        return modes;
    }
    public static boolean offersEmergencyPark(World world,net.minecraft.entity.player.EntityPlayer player){
        EntityGroundVehicle craft=world instanceof VehicleWorld?((VehicleWorld)world).craft:null;
        return craft!=null&&!craft.transferInProgress&&!craft.cruise.active()&&player!=null&&player.capabilities.isCreativeMode
                &&player.getHeldItemMainhand().getItem() instanceof com.vandorlabs.items.ItemConfigurizer;
    }
    public static int panelAction(int choice,int height,double x,double y){
        return choice==com.vandorlabs.screen.VehicleInstrument.AUTOPILOT.choice?com.vandorlabs.screen.SpeedAutopilotPanel.action(height,x,y):panelAction(height,x,y);
    }
    public static int panelAction(int height,double x,double y){
        int target=VehicleStatusLayout.forHeight(height).target(x,y);
        if(target==VehicleStatusLayout.AUTOPILOT)return 0;
        if(target==VehicleStatusLayout.NEXT)return 4;
        if(target==VehicleStatusLayout.EMERGENCY)return EMERGENCY_PARK_ACTION;
        if(target==VehicleStatusLayout.FLIGHT)return FLIGHT_ACTION;
        return target<0?-1:target+1;
    }
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
        if(old!=null&&world.getTotalWorldTime()-old.at>=0&&world.getTotalWorldTime()-old.at<40)return old;
        ArrayDeque<BlockPos> queue=new ArrayDeque<>();Set<BlockPos> seen=new HashSet<>();queue.add(screen);seen.add(screen);
        BlockPos autopilot=null,controller=null;int autopilots=0,budget=4096,lift=0,capacity=0;
        boolean thruster=false,pilot=false;VehicleStatusSupport supports=new VehicleStatusSupport();
        while(!queue.isEmpty()&&budget-->0){BlockPos p=queue.removeFirst();if(!world.isBlockLoaded(p)||world.isAirBlock(p))continue;TileEntity tile=world.getTileEntity(p);
            int navigation=com.vandorlabs.rack.ModuleAutopilots.count(tile);if(navigation>0){autopilots+=navigation;autopilot=p;}
            net.minecraft.block.state.IBlockState state=world.getBlockState(p);if(state.getBlock() instanceof com.vandorlabs.blocks.BlockVehicleController)controller=p;
            if(VehicleFlight.lift(state.getBlock())){lift++;capacity+=VehicleFlight.hoverLift(state.getBlock());}else if(state.getBlock() instanceof com.vandorlabs.blocks.BlockPropulsionLight)thruster=true;
            if(state.getBlock() instanceof com.vandorlabs.blocks.BlockPilotSeat&&!state.getValue(com.vandorlabs.blocks.BlockPilotSeat.UPPER))pilot=true;
            try{supports.observe(world,p,state,tile);}
            catch(RuntimeException|LinkageError unavailable){supports.unavailable();VehicleCompatibility.warn(state.getBlock(),"status landing supports",unavailable);}
            if(state.getBlock() instanceof com.vandorlabs.blocks.BlockLandingGear)continue;
            if(state.getBlock() instanceof com.vandorlabs.blocks.BlockPropulsionLight&&state.getValue(com.vandorlabs.blocks.BlockPropulsionLight.FACING)==net.minecraft.util.EnumFacing.DOWN)continue;
            try{for(net.minecraft.util.EnumFacing face:net.minecraft.util.EnumFacing.values())if(VehicleScan.touches(world,p,state,face)){BlockPos next=p.offset(face);if(world.isBlockLoaded(next)&&!VehicleScan.ownedCoupler(state,world.getBlockState(next),face)&&seen.add(next))queue.add(next);}}catch(RuntimeException|LinkageError unavailable){VehicleCompatibility.warn(state.getBlock(),"status connectivity",unavailable);}
        }
        int modes=1<<VehicleStatusLayout.PARK;
        if(queue.isEmpty()&&pilot&&supports.ready()){
            if(supports.taxi())modes|=1<<VehicleStatusLayout.TAXI;
            TileEntity raw=controller==null?null:world.getTileEntity(controller);
            if(lift>=3&&thruster&&raw instanceof com.vandorlabs.tiles.TileEntityVehicleController
                    && ((com.vandorlabs.tiles.TileEntityVehicleController)raw).vehicleWeight<=capacity)modes|=1<<VehicleStatusLayout.HOVER;
        }
        if(entries.size()>256)entries.clear();Entry result=new Entry(world.getTotalWorldTime(),autopilots==1?autopilot:null,controller,modes);entries.put(screen.toImmutable(),result);return result;
    }
}
