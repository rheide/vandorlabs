package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.BlockPropulsionLight;
import com.vandorlabs.blocks.BlockWheel;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.physics.FlightTuning;
import com.vandorlabs.tiles.DeferredTileLoad;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.concurrent.Future;

/**
 * A light craft with Impulse Engines and no Maxwell Drive offers Flight, takes off from a runway, and
 * climbs no higher than the seed-view handoff at no more than the thruster-only speed.
 */
@SideOnly(Side.CLIENT)
public final class ThrusterFlightLiveChecks {
    private static final BlockPos ROOT=new BlockPos(149,180,149),CONTROL=ROOT.east();
    private static Future<Boolean> pending;
    private static int stage,ticks,total,sequence=1<<29; // above the real client pilot's input sequence
    private static volatile EntityGroundVehicle craft;
    private static double highest,fastest;
    private static volatile Exception failure;

    private ThrusterFlightLiveChecks(){}

    public static boolean tick(Minecraft client)throws Exception{
        if(client.currentScreen!=null)client.displayGuiScreen(null);
        client.gameSettings.pauseOnLostFocus=false;
        if(pending!=null){
            if(!pending.isDone())return false;
            if(pending.get())return true;
            pending=null;
        }
        pending=client.getIntegratedServer().callFromMainThread(()->advance(client.getIntegratedServer().getWorld(0),client.getIntegratedServer().getPlayerList().getPlayers().get(0)));
        return false;
    }

    private static boolean advance(WorldServer world,EntityPlayerMP actor)throws Exception{
        require(++total<3000,"fixture completes");
        ticks++;
        if(failure!=null)throw failure;
        if(stage==0){build(world,actor);next();return false;}
        if(stage==1){
            if(craft==null||VehicleTransfer.busy(world))return false;
            require(craft.board(actor)&&craft.getControllingPassenger()==actor,"pilot boards the thruster craft");
            next();return false;
        }
        if(stage==2){
            if(ticks<20)return false;
            VehicleFlightReadout readout=craft.flightReadout();
            require(readout.drives==0&&readout.thrusters==6&&readout.thrusterOnly(),"six Impulse Engines and no Maxwell Drive: "+readout.drives+"/"+readout.thrusters);
            require(Math.abs(readout.thrust-6*FlightTuning.THRUSTER_FLIGHT_THRUST)<1e-9&&readout.canFly(),"thrusters alone give Flight thrust: thrust="+readout.thrust+" effective="+readout.effectiveMass+" limit="+readout.takeoffMass());
            require(VehicleStatus.flightBlocker(craft,actor)==null,"Status offers Flight: "+VehicleStatus.flightBlocker(craft,actor));
            craft.requestFlight(actor);
            require(craft.takeoffRoll(),"Flight request starts a takeoff roll");
            next();return false;
        }
        if(stage==3){ // Hold S once the roll reaches takeoff speed, then keep climbing.
            craft.input(actor,++sequence,(byte)(craft.driveSpeed>=FlightTuning.RUNWAY_TAKEOFF_SPEED-1e-4||craft.flying()?-1:0),(byte)0,false);
            if(craft.flying()){highest=Math.max(highest,craft.posY);fastest=Math.max(fastest,craft.driveSpeed);}
            if(ticks%40==0)System.out.println("[vandorlabs] thruster-flight progress t="+ticks+" roll="+craft.takeoffRoll()+" flying="+craft.flying()+" y="+craft.posY+" speed="+craft.driveSpeed+" pitch="+craft.flightPitch()+" ground="+craft.onGround+" z="+craft.posZ);
            if(ticks<700)return false;
            int handoff=craft.cruise.handoffAltitude();
            require(craft.flying()&&!craft.cruise.active(),"thruster craft keeps flying below the seed view: "+craft.posY);
            require(highest<handoff-1,"climb stops below the seed-view handoff "+handoff+": highest Y="+highest);
            require(highest>ROOT.getY()+60,"thruster craft climbs well clear of the runway: highest Y="+highest);
            require(fastest<=FlightTuning.THRUSTER_ONLY_MAX_SPEED+1e-6,"thruster-only speed limit: fastest="+fastest);
            System.out.println("[vandorlabs] thruster-flight PASS thrusters-only Flight offered, takeoff roll, climb capped at Y="+String.format(java.util.Locale.ROOT,"%.1f",highest)
                    +" below handoff "+handoff+", speed "+String.format(java.util.Locale.ROOT,"%.2f",fastest)+" b/t");
            return true;
        }
        return false;
    }

    private static void build(WorldServer world,EntityPlayerMP actor){
        actor.setGameType(GameType.CREATIVE);actor.capabilities.isFlying=true;actor.sendPlayerAbilities();
        actor.connection.setPlayerLocation(ROOT.getX()-3.5,ROOT.getY()+4,ROOT.getZ()-3.5,-45,20);
        for(int x=-16;x<=16;x+=16)for(int z=-400;z<=32;z+=16)world.getChunkFromBlockCoords(ROOT.add(x,0,z));
        for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-4,-3,-6),ROOT.add(8,6,8)))world.setBlockToAir(at);
        // A long runway ahead (north); the craft faces north.
        for(BlockPos at:BlockPos.getAllInBox(ROOT.add(-3,-3,-380),ROOT.add(7,-3,6)))world.setBlockState(at,Blocks.CONCRETE.getDefaultState(),2);
        for(int x=0;x<=4;x++)for(int z=0;z<=4;z++)world.setBlockState(ROOT.add(x,-1,z),Blocks.STONE.getDefaultState(),2);
        Block wheel=null;for(Block block:Block.REGISTRY)if(block instanceof BlockWheel){wheel=block;break;}
        require(wheel!=null,"wheel available");
        for(BlockPos at:new BlockPos[]{ROOT.add(0,-2,0),ROOT.add(4,-2,0),ROOT.add(4,-2,4)})world.setBlockState(at,wheel.getDefaultState(),2);
        world.setBlockState(ROOT.add(2,0,1),ModBlocks.PILOT_SEAT.getDefaultState(),2);
        ModBlocks.PILOT_SEAT.onBlockPlacedBy(world,ROOT.add(2,0,1),world.getBlockState(ROOT.add(2,0,1)),actor,new ItemStack(ModBlocks.PILOT_SEAT));
        world.setBlockState(ROOT.add(3,0,1),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),2);
        Block engine=Block.getBlockFromName("vandorlabs:impulse_engine");
        require(engine instanceof BlockPropulsionLight,"Impulse Engine registered");
        // Exhaust faces south, so the engines push the craft north.
        for(int x=0;x<=4;x+=2)for(int y=0;y<=1;y++)world.setBlockState(ROOT.add(x,y,4),engine.getDefaultState().withProperty(BlockPropulsionLight.FACING,EnumFacing.SOUTH),2);
        DeferredTileLoad.flush(world);
        VehicleScan scan=new VehicleScan(world,ROOT.add(2,0,1));scan.holdSource();
        for(int i=0;i<300&&!scan.step(128);i++){}
        require(scan.result!=null,"source scan "+scan.error);
        VehicleTransfer.submit(world,()->{craft=VehicleTransfer.assemble(world,scan.result,scan.origin,actor);scan.releaseSource();},error->{scan.releaseSource();failure=error;});
    }
    private static void next(){stage++;ticks=0;}
    private static void require(boolean ok,String message){
        if(!ok)throw new IllegalStateException("thruster-flight stage="+stage+" ticks="+ticks+": "+message);
    }
}
