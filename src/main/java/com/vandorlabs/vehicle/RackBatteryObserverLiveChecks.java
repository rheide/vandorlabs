package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.*;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.machines.CoreLayout;
import com.vandorlabs.navigation.ObserverPower;
import com.vandorlabs.rack.ModuleBatteryData;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.concurrent.Future;

/**
 * A captured Module Rack Battery Module powers a carried Survey Dish only in Flight, while a
 * Sentinel Array keeps its wired Battery Block supply. The charge survives save, parking and
 * recapture, and keeps powering the dish in high cruise.
 */
@SideOnly(Side.CLIENT)
public final class RackBatteryObserverLiveChecks {
    private static final BlockPos ROOT=new BlockPos(149,150,149);
    private static final BlockPos RACK=ROOT.add(2,0,0),DISH=ROOT.add(1,0,3),ARRAY=ROOT.add(6,0,3),DRIVE=ROOT.add(5,0,6),PARK=ROOT.add(20,-2,0);
    private static final int WIRED_CHARGE=60000;
    private static Future<Boolean> pending;
    private static int stage,ticks,total,attempts;
    private static volatile EntityGroundVehicle craft;
    private static BlockPos origin,rackLocal,dishLocal,arrayLocal,batteryLocal;
    private static float assembledYaw;
    private static long sampleTime;
    private static int sampleRack,sampleWired,flightDrawn;
    private static String lastRefusal="";
    private static volatile Exception failure;

    private RackBatteryObserverLiveChecks(){}

    public static boolean tick(Minecraft client)throws Exception{
        if(client.currentScreen!=null)client.displayGuiScreen(null);
        client.gameSettings.pauseOnLostFocus=false;
        if(pending!=null){
            if(!pending.isDone())return false;
            if(pending.get())return true;
            pending=null;
        }
        clientChecks(client);
        pending=client.getIntegratedServer().callFromMainThread(()->advance(client.getIntegratedServer().getWorld(0),client.getIntegratedServer().getPlayerList().getPlayers().get(0)));
        return false;
    }

    private static boolean advance(WorldServer world,EntityPlayerMP actor)throws Exception{
        require(++total<1800,"fixture completes");
        ticks++;
        if(failure!=null)throw failure;
        if(stage==0){build(world,actor);next();return false;}
        if(stage==1){
            if(craft==null||VehicleTransfer.busy(world))return false;
            require(craft.board(actor)&&craft.getControllingPassenger()==actor,"pilot boards the assembled craft");
            assembledYaw=craft.rotationYaw;
            require(rack(craft).batteryEnergy()==ModuleBatteryData.CAPACITY,"captured rack keeps its full Battery Module: "+rack(craft).batteryEnergy());
            require(!craft.flying()&&!craft.hover,"assembled craft starts in Taxi");
            IEnergyStorage input=wired().getCapability(CapabilityEnergy.ENERGY,EnumFacing.DOWN);
            require(input!=null,"carried Battery Block exposes its input face");
            int charged=0;for(int i=0;i<200&&charged<WIRED_CHARGE;i++)charged+=input.receiveEnergy(WIRED_CHARGE-charged,false);
            require(charged==WIRED_CHARGE,"charged the carried wired Battery Block: "+charged);
            next();return false;
        }
        if(stage==2){ // Taxi: wired supply only, rack battery untouched.
            if(ticks==10)sample(world);
            if(ticks<50)return false;
            requireIdle(world,"Taxi");
            craft.hover=true;craft.hoverTarget=craft.posY+12;craft.setPosition(craft.posX,craft.hoverTarget,craft.posZ);
            next();return false;
        }
        if(stage==3){ // Hover: still no rack draw.
            if(ticks==10)sample(world);
            if(ticks<50)return false;
            require(craft.vehicleMode()==2,"craft reports Hover");
            requireIdle(world,"Hover");
            next();return false;
        }
        if(stage==4){
            if(!engageFlight(actor))return false;
            next();return false;
        }
        if(stage==5){ // Flight: the rack battery tops up the dish, wiring still feeds the array.
            require(craft.flying(),"craft remains in powered Flight: "+state());
            if(ticks==10){sample(world);return false;}
            if(ticks<70)return false;
            requireFlightDraw(world,"powered Flight");
            NBTTagCompound saved=craft.writeToNBT(new NBTTagCompound());
            EntityGroundVehicle reloaded=new EntityGroundVehicle(world);reloaded.readFromNBT(saved);
            try{
                require(rack(reloaded).batteryEnergy()==rack(craft).batteryEnergy(),"save/load keeps the spent rack charge "+rack(reloaded).batteryEnergy()+" vs "+rack(craft).batteryEnergy());
                require(reloaded.flying(),"save/load keeps Flight");
            }finally{reloaded.signals.close();reloaded.setDead();}
            craft.leaveFlight(1);
            next();return false;
        }
        if(stage==6){ // Leaving Flight for Taxi.
            if(craft.flying()){require(ticks<900,"descent back to Taxi completes: "+state());return false;}
            require(!craft.hover,"craft returns to Taxi: "+state());
            next();return false;
        }
        if(stage==7){
            if(ticks==10)sample(world);
            if(ticks<50)return false;
            requireIdle(world,"Taxi after Flight");
            int charge=rack(craft).batteryEnergy();
            require(charge<ModuleBatteryData.CAPACITY,"Flight spent rack charge");
            require(clientSeen>=charge&&clientSeen<ModuleBatteryData.CAPACITY,"client vehicle view receives the spent rack charge: "+clientSeen);
            craft.stopForParking();craft.rotationYaw=assembledYaw;craft.setPosition(PARK.getX(),PARK.getY(),PARK.getZ());
            sampleRack=charge;
            EntityGroundVehicle parking=craft;
            VehicleTransfer.submit(world,()->VehicleTransfer.park(world,parking,PARK,actor,false),error->failure=error);
            next();return false;
        }
        if(stage==8){
            if(VehicleTransfer.busy(world)||ticks<20)return false;
            require(craft.isDead,"parked craft entity is retired");
            TileEntity parked=world.getTileEntity(PARK.add(rackLocal));
            require(parked instanceof TileEntityModuleRack,"native Module Rack restored at "+PARK.add(rackLocal));
            require(((TileEntityModuleRack)parked).batteryEnergy()==sampleRack,"parked rack keeps the in-flight charge "+((TileEntityModuleRack)parked).batteryEnergy()+" vs "+sampleRack);
            TileEntityModuleRack ground=(TileEntityModuleRack)parked;
            require(ground.receiveBatteryEnergy(ModuleBatteryData.CAPACITY,false)==ModuleBatteryData.CAPACITY-sampleRack&&ground.batteryEnergy()==ModuleBatteryData.CAPACITY,"parked rack recharges its Battery Module");
            sampleRack=ModuleBatteryData.CAPACITY;
            craft=null;
            VehicleScan scan=new VehicleScan(world,PARK.add(ROOT.subtract(origin)));scan.holdSource();
            for(int i=0;i<300&&!scan.step(128);i++){}
            require(scan.result!=null,"recapture scan "+scan.error);
            require(scan.origin.equals(PARK),"recaptured origin matches parking "+scan.origin);
            VehicleTransfer.submit(world,()->{craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);scan.releaseSource();},error->{scan.releaseSource();failure=error;});
            next();return false;
        }
        if(stage==9){
            if(craft==null||VehicleTransfer.busy(world))return false;
            require(rack(craft).batteryEnergy()==sampleRack,"recaptured rack keeps the parked charge "+rack(craft).batteryEnergy()+" vs "+sampleRack);
            require(craft.board(actor),"pilot boards the recaptured craft");
            craft.hover=true;craft.hoverTarget=craft.posY+12;craft.setPosition(craft.posX,craft.hoverTarget,craft.posZ);
            next();return false;
        }
        if(stage==10){
            if(!engageFlight(actor))return false;
            craft.setPosition(craft.posX,craft.cruise.handoffAltitude()+2,craft.posZ);
            require(craft.cruise.move(0,1,0),"enter high cruise");
            VehicleNetwork.state(craft);
            next();return false;
        }
        if(stage==11){ // High cruise: still Flight, still drawing.
            require(craft.flying()&&craft.cruise.active(),"craft remains in high cruise: "+state());
            if(ticks==10){sample(world);return false;}
            if(ticks<60)return false;
            requireFlightDraw(world,"high cruise");
            System.out.println("[vandorlabs] rack-battery-observers PASS Taxi/Hover hold charge, Flight and cruise draw "+ObserverPower.RF_PER_TICK
                    +" RF/t for the dish only, wired array unchanged, save/park/recapture keep charge; flight spent "+flightDrawn+" RF");
            return true;
        }
        return false;
    }

    private static void build(WorldServer world,EntityPlayerMP actor){
        actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
        actor.connection.setPlayerLocation(ROOT.getX()-3.5,ROOT.getY()+4,ROOT.getZ()-3.5,-45,20);
        for(int x=-32;x<=48;x+=16)for(int z=-32;z<=32;z+=16)world.getChunkFromBlockCoords(ROOT.add(x,0,z));
        for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-4,-3,-4),ROOT.add(36,14,15)))world.setBlockToAir(at);
        for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-3,-3,-3),ROOT.add(34,-3,14)))world.setBlockState(at,Blocks.STONE.getDefaultState(),2);
        for(int x=0;x<=9;x++)for(int z=0;z<=10;z++)world.setBlockState(ROOT.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
        Block wheel=null;for(Block block:Block.REGISTRY)if(block instanceof BlockWheel){wheel=block;break;}
        require(wheel!=null,"wheel available");
        for(BlockPos at:new BlockPos[]{ROOT.add(0,-2,0),ROOT.add(9,-2,0),ROOT.add(9,-2,10)})world.setBlockState(at,wheel.getDefaultState(),2);
        world.setBlockState(ROOT,ModBlocks.PILOT_SEAT.getDefaultState(),2);ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,ROOT,world.getBlockState(ROOT),actor,new ItemStack(ModBlocks.PILOT_SEAT));
        world.setBlockState(ROOT.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
        world.setBlockState(RACK,Block.getBlockFromName("vandorlabs:module_rack").getDefaultState(),2);
        ItemStack battery=new ItemStack(ModItems.BATTERY_MODULE);ModuleBatteryData.receive(battery,ModuleBatteryData.CAPACITY,false);
        require(((TileEntityModuleRack)world.getTileEntity(RACK)).insertModule(0,battery,true),"charged Battery Module installs in the rack");
        TileEntityConstructor drive=MaxwellRuntimeChecks.assemble(world,DRIVE,4,EnumFacing.NORTH,0,0);
        require(MaxwellRuntimeChecks.inlet(drive,drive.facing()).fill(new FluidStack(MaxwellRuntimeChecks.testFuel(),1000),true)==1000,"fuel the drive");
        TileEntityConstructor dish=formed(world,DISH,"standalone_dish",3,3,3),array=formed(world,ARRAY,"standalone_antenna",2,4,2);
        BlockPos wired=array.observerPowerPort().down();
        require(world.getBlockState(dish.observerPowerPort().down()).getBlock()==Blocks.STONE,"dish inlet has no wired supply");
        Block batteryBlock=Block.getBlockFromName("vandorlabs:battery_block");
        world.setBlockState(wired,batteryBlock.getDefaultState().withProperty(BlockBattery.FACING,EnumFacing.NORTH),2);
        ((TileEntityBattery)world.getTileEntity(wired)).place(EnumFacing.DOWN);
        DeferredTileLoad.flush(world);
        require(dish.screenOn()&&array.screenOn()&&dish.observerScanEnabled()&&array.observerScanEnabled(),"observers are on and scanning-enabled before capture");
        VehicleScan scan=new VehicleScan(world,ROOT);scan.holdSource();
        for(int i=0;i<300&&!scan.step(128);i++){}
        require(scan.result!=null,"source scan "+scan.error);
        origin=scan.origin;
        rackLocal=RACK.subtract(origin);dishLocal=DISH.subtract(origin);arrayLocal=ARRAY.subtract(origin);batteryLocal=wired.subtract(origin);
        require(scan.result.at(batteryLocal)!=null&&scan.result.at(dishLocal)!=null&&scan.result.at(arrayLocal)!=null&&scan.result.at(rackLocal)!=null,"scan captures rack, observers and wired Battery Block");
        VehicleTransfer.submit(world,()->{craft=VehicleTransfer.assemble(world,scan.result,origin,actor);scan.releaseSource();},error->{scan.releaseSource();failure=error;});
    }

    private static TileEntityConstructor formed(WorldServer world,BlockPos at,String id,int width,int height,int depth){
        BlockConstructorCore core=(BlockConstructorCore)Block.getBlockFromName("vandorlabs:"+id+"_core");
        world.setBlockState(at,core.getDefaultState(),2);
        CoreLayout layout=new CoreLayout(width,height,depth,EnumFacing.NORTH.getIndex(),0,0);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)for(int z=0;z<depth;z++)
            if(x!=0||y!=0||z!=0)world.setBlockState(at.add(x,y,z),layout.frame(x,y,z)?ModBlocks.PROGRAMMABLE_BLOCK.getDefaultState():Blocks.AIR.getDefaultState(),2);
        TileEntityConstructor tile=(TileEntityConstructor)world.getTileEntity(at);
        require(tile.assemble(),"formed "+id+" "+tile.assemblyProblem());
        return tile;
    }

    private static boolean engageFlight(EntityPlayerMP actor){
        try{craft.requestFlight(actor);}
        catch(IllegalArgumentException refused){
            lastRefusal=refused.getMessage();
            require(++attempts<200,"Flight engages: "+lastRefusal+" "+state());
            return false;
        }
        attempts=0;
        require(craft.flying(),"Flight request enters powered Flight from Hover: "+state());
        return true;
    }

    private static void sample(WorldServer world){
        sampleTime=world.getTotalWorldTime();sampleRack=rack(craft).batteryEnergy();sampleWired=wired().stored();
    }
    private static void requireIdle(WorldServer world,String mode){
        long elapsed=world.getTotalWorldTime()-sampleTime;
        require(elapsed>=30,mode+" observed long enough: "+elapsed);
        require(rack(craft).batteryEnergy()==sampleRack,mode+" leaves the rack battery untouched: "+sampleRack+" -> "+rack(craft).batteryEnergy());
        require(!observer(dishLocal).observerPowered(),mode+" leaves the rack-only Survey Dish unpowered");
        require(observer(arrayLocal).observerPowered()&&observer(arrayLocal).observerScanning(),mode+" keeps the wired Sentinel Array powered");
        require(wired().stored()<sampleWired,mode+" draws the array's wired Battery Block");
    }
    private static void requireFlightDraw(WorldServer world,String mode){
        long elapsed=world.getTotalWorldTime()-sampleTime;
        int drawn=sampleRack-rack(craft).batteryEnergy(),wiredDrawn=sampleWired-wired().stored();
        require(elapsed>=40,mode+" observed long enough: "+elapsed);
        require(observer(dishLocal).observerPowered()&&observer(dishLocal).observerScanning(),mode+" powers the Survey Dish from the rack battery: rack="+rack(craft).batteryEnergy());
        require(observer(arrayLocal).observerPowered()&&observer(arrayLocal).observerScanning(),mode+" keeps the wired Sentinel Array powered");
        require(drawn==ObserverPower.RF_PER_TICK*elapsed,mode+" rack battery pays only the dish's demand: drew "+drawn+" RF over "+elapsed+" ticks");
        require(wiredDrawn==ObserverPower.RF_PER_TICK*elapsed,mode+" wired Battery Block keeps feeding the array: drew "+wiredDrawn+" RF over "+elapsed+" ticks");
        flightDrawn+=drawn;
    }

    private static volatile int clientSeen=-1;
    private static void clientChecks(Minecraft client){
        EntityGroundVehicle craft=RackBatteryObserverLiveChecks.craft;
        if(stage<5||stage>7||craft==null||rackLocal==null)return;
        Entity entity=client.world==null?null:client.world.getEntityByID(craft.getEntityId());
        if(!(entity instanceof EntityGroundVehicle)||((EntityGroundVehicle)entity).view==null)return;
        TileEntity tile=((EntityGroundVehicle)entity).view.getTileEntity(rackLocal);
        if(tile instanceof TileEntityModuleRack&&((TileEntityModuleRack)tile).batteryEnergy()<ModuleBatteryData.CAPACITY)clientSeen=((TileEntityModuleRack)tile).batteryEnergy();
    }

    private static TileEntityModuleRack rack(EntityGroundVehicle vehicle){
        TileEntity tile=vehicle.view==null?null:vehicle.view.getTileEntity(rackLocal);
        require(tile instanceof TileEntityModuleRack,"carried Module Rack at "+rackLocal);
        return (TileEntityModuleRack)tile;
    }
    private static TileEntityConstructor observer(BlockPos local){
        TileEntity tile=craft.view.getTileEntity(local);
        require(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).observer(),"carried observer at "+local);
        return (TileEntityConstructor)tile;
    }
    private static TileEntityBattery wired(){
        TileEntity tile=craft.view.getTileEntity(batteryLocal);
        require(tile instanceof TileEntityBattery,"carried wired Battery Block at "+batteryLocal);
        return (TileEntityBattery)tile;
    }
    private static String state(){
        return craft==null?"no craft":"mode="+craft.vehicleMode()+" phase="+craft.flightPhase()+" hover="+craft.hover+" cruise="+craft.cruise.active()
                +" y="+String.format("%.2f",craft.posY)+" readout="+craft.flightReadout().drives+"/"+craft.flightReadout().canFly()+" refusal="+lastRefusal
                +" pilot="+(craft.getControllingPassenger()!=null);
    }
    private static void next(){stage++;ticks=0;}
    private static void require(boolean value,String reason){
        if(!value)throw new IllegalStateException("Rack battery observers stage "+stage+" tick "+ticks+": "+reason
                +(craft==null||craft.view==null||rackLocal==null||!(craft.view.getTileEntity(rackLocal) instanceof TileEntityModuleRack)?"":" rack="+((TileEntityModuleRack)craft.view.getTileEntity(rackLocal)).batteryEnergy()));
    }
}
