package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.blocks.RedstoneScreenInteractions;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.screen.VehicleStatusLayout;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.TileEntityConstructor;
import com.vandorlabs.tiles.TileEntityPilotSeat;
import com.vandorlabs.tiles.TileEntityVehicleController;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A Maxwell-only aircraft uses its real Status hit target and the driver's normal key packets. */
final class VehicleFlightClientChecks {
    private static final BlockPos ORIGIN=new BlockPos(184,124,96);
    private static final BlockPos SEAT=new BlockPos(2,1,3);
    private static final BlockPos SCREEN=new BlockPos(2,2,1);
    private static final BlockPos DRIVE=new BlockPos(5,1,1);
    private static UUID id;
    private static int stage,frame,total,settled;
    private static double runwayY,climbY,turnYaw,turnX,turnZ,priorPitch;
    private static boolean sawDeparture, sawDescent, sawLongClearance;

    static void begin(Minecraft mc)throws Exception {
        mc.getIntegratedServer().addScheduledTask(()->spawn(mc)).get();
    }

    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle server(Minecraft mc){return (EntityGroundVehicle)world(mc).getEntityFromUuid(id);}
    private static EntityGroundVehicle client(Minecraft mc){
        for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))if(craft.getUniqueID().equals(id))return craft;
        return null;
    }

    private static void spawn(Minecraft mc){
        WorldServer world=world(mc);
        EntityPlayerMP player=player(mc);
        player.dismountRidingEntity();
        player.setSneaking(false);
        player.setHeldItem(EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
        for(int x=-16;x<=16;x+=16)for(int z=-112;z<=16;z+=16)world.getChunkFromBlockCoords(ORIGIN.add(x,0,z));
        for(BlockPos pos:BlockPos.getAllInBox(ORIGIN.add(-3,-1,-88),ORIGIN.add(23,-1,8)))world.setBlockState(pos,Blocks.STONE.getDefaultState(),2);
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(int x=1;x<=3;x++)for(int z=0;z<=4;z++)cells.add(new VehicleStructure.Cell(new BlockPos(x,1,z),Blocks.STONE.getDefaultState(),null));
        cells.removeIf(cell->cell.pos.equals(SEAT));
        TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(SEAT);seat.setTaxiMode(false);
        cells.add(new VehicleStructure.Cell(SEAT,ModBlocks.PILOT_SEAT.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.NORTH),seat.writeToNBT(new NBTTagCompound())));
        BlockPos controller=new BlockPos(3,2,3);
        TileEntityVehicleController controllerTile=new TileEntityVehicleController();controllerTile.setPos(controller);controllerTile.owner=player.getUniqueID();
        cells.add(new VehicleStructure.Cell(controller,ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),controllerTile.writeToNBT(new NBTTagCompound())));
        TileEntityAnimatedScreenSelector screen=new TileEntityAnimatedScreenSelector();screen.setPos(SCREEN);screen.setSurfaceTexture(0,TileEntityAnimatedScreenSelector.VEHICLE_SURFACE);
        cells.add(new VehicleStructure.Cell(SCREEN,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.SOUTH),screen.writeToNBT(new NBTTagCompound())));
        Block wheel=Block.getBlockFromName("vandorlabs:wheel_medium");
        for(BlockPos pos:new BlockPos[]{new BlockPos(1,0,0),new BlockPos(3,0,0),new BlockPos(1,0,4),new BlockPos(3,0,4)})
            cells.add(new VehicleStructure.Cell(pos,wheel.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.WEST),null));
        BlockPos staged=ORIGIN.add(24,8,0);
        world.getChunkFromBlockCoords(staged);
        TileEntityConstructor engine=MaxwellRuntimeChecks.assemble(world,staged,3,EnumFacing.NORTH,0,0);
        need(MaxwellRuntimeChecks.inlet(engine,engine.facing()).fill(new FluidStack(MaxwellRuntimeChecks.testFuel(),1000),true)==1000,"fill the assembled engine's inlet");
        cells.add(new VehicleStructure.Cell(new BlockPos(4,1,1),Blocks.STONE.getDefaultState(),null));
        BlockPos offset=DRIVE.subtract(staged);
        for(BlockPos at:BlockPos.getAllInBox(staged,staged.add(2,2,3))){
            net.minecraft.tileentity.TileEntity member=world.getTileEntity(at);
            NBTTagCompound data=VehicleTileData.translated(member.writeToNBT(new NBTTagCompound()),offset);
            cells.add(new VehicleStructure.Cell(at.add(offset),world.getBlockState(at),data));
        }
        EntityGroundVehicle craft=new EntityGroundVehicle(world);
        craft.install(new VehicleStructure(cells,SEAT,EnumFacing.NORTH,4));
        craft.setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());
        TileEntityConstructor carried=(TileEntityConstructor)craft.view.getTileEntity(DRIVE);
        need(carried.formed()&&carried.maxwellFuel()==1000&&carried.maxwellThrustLevel(true)==15,"captured formed drive keeps available thrust and fuel");
        need(world.spawnEntity(craft),"spawn craft");
        id=craft.getUniqueID();
        player.connection.setPlayerLocation(ORIGIN.getX()+2.5,ORIGIN.getY()+2,ORIGIN.getZ()+5,180,0);
        need(craft.board(player),"board Pilot Seat");
    }

    private static void aim(Minecraft mc,EntityGroundVehicle craft,int mode)throws Exception {
        IBlockState state=craft.view.getBlockState(SCREEN);
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)craft.view.getTileEntity(SCREEN);
        ScreenSurface.Quad quad=RedstoneScreenInteractions.surface(state,tile,0);
        int height=RedstoneScreenInteractions.displayHeight(state.getBlock(),0);
        VehicleStatusLayout layout=VehicleStatusLayout.forHeight(height);
        double column=layout.centerX(mode)/128D;
        double row=layout.centerY(mode)/(double)height;
        double x=(quad.topRight.x+(quad.topLeft.x-quad.topRight.x)*column)/16;
        double y=(quad.topRight.y+(quad.bottomRight.y-quad.topRight.y)*row)/16;
        double z=(quad.topRight.z+(quad.bottomRight.z-quad.topRight.z)*row)/16;
        Vec3d screenLocal=new Vec3d(SCREEN).addVector(1-x,y,1-z);
        Vec3d target=craft.toWorld(screenLocal),eye=mc.player.getPositionEyes(1);
        Vec3d delta=target.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
        mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;
        mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(yaw,pitch,false));
        Vec3d a=craft.toLocal(eye).subtract(new Vec3d(SCREEN)),b=craft.toLocal(eye.add(delta.scale(1.2))).subtract(new Vec3d(SCREEN));
        need(RedstoneScreenInteractions.statusAction(state,tile,0,a,b)==VehicleStatus.FLIGHT_ACTION,"seated ray hits Flight button at "+a+" toward "+b);
    }

    static boolean tick(Minecraft mc,File output)throws Exception{
        if(++total>1500)throw new IllegalStateException("Vehicle Flight timeout stage="+stage+" frame="+frame);
        EntityGroundVehicle craft=client(mc);
        if(craft==null||craft.structure==null||mc.player.getRidingEntity()!=craft)return false;
        frame++;
        if(stage==0&&frame>=15){
            VehicleFlightReadout readout=craft.flightReadout();
            if(readout.drives==0&&frame<60)return false;
            if(!readout.canFly()||readout.drives!=1){
                VehicleFlightReadout actual=mc.getIntegratedServer().callFromMainThread(()->server(mc).flightReadout()).get();
                need(false,"networked drive, fuel and weight permit takeoff, client "+measurements(readout)+" server "+measurements(actual));
            }
            boolean ready=mc.getIntegratedServer().callFromMainThread(()->{
                EntityGroundVehicle actual=server(mc);
                EntityPlayerMP pilot=player(mc);
                return actual.onGround&&actual.getControllingPassenger()==pilot&&actual.acknowledgedInput()>=0
                        &&actual.flightReadout().canFly()&&pilot.getHeldItemMainhand().isEmpty();
            }).get();
            if(!ready){need(frame<120,"server contact, seated input and inventory are synchronized before Flight click");return false;}
            VehicleStatusRenderChecks.fuel(mc,output,craft,SCREEN);
            runwayY=craft.posY;
            aim(mc,craft,3);next();return false;
        }
        if(stage==1&&frame>=3){
            aim(mc,craft,3);
            VehicleInteraction.Target hit=VehicleInteraction.pointed(mc.player);
            need(hit!=null&&hit.hit.cell.pos.equals(SCREEN)&&VehicleInteraction.supported(mc.player,hit),"real seated access to Flight button");
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP pilot=player(mc);
                VehicleInteraction.Target selected=VehicleInteraction.pointed(pilot);
                need(selected!=null&&selected.hit.cell.pos.equals(SCREEN)&&VehicleInteraction.supported(pilot,selected),
                        "server aim and held stack synced for Status click; yaw="+pilot.rotationYaw+" pitch="+pilot.rotationPitch+" held="+pilot.getHeldItemMainhand());
            }).get();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickEmpty(mc.player,EnumHand.MAIN_HAND));
            next();return false;
        }
        if(stage==2&&frame>=22){
            String motion="roll="+craft.takeoffRoll()+" flight="+craft.flying()+" Y="+craft.posY+" runway="+runwayY+" speed="+craft.driveSpeed;
            if(!craft.flying()&&(!craft.takeoffRoll()||craft.driveSpeed<FlightEnvelope.TAKEOFF_SPEED-1e-4)&&frame<100)return false;
            if(!craft.takeoffRoll())motion+=mc.getIntegratedServer().callFromMainThread(()->{
                EntityGroundVehicle actual=server(mc);EntityPlayerMP pilot=player(mc);
                return " server roll="+actual.takeoffRoll()+" ground="+actual.onGround+" acknowledged="+actual.acknowledgedInput()
                        +" Y="+actual.posY+" seated="+(actual.getControllingPassenger()==pilot)+" held="+pilot.getHeldItemMainhand();
            }).get();
            need(craft.takeoffRoll()&&!craft.flying()&&Math.abs(craft.posY-runwayY)<.08&&craft.driveSpeed>=FlightEnvelope.TAKEOFF_SPEED-1e-4,
                    "ground roll builds speed without liftoff before pitch input; "+motion);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),true);
            next();return false;
        }
        if(stage==3&&frame>=13){
            if((!craft.flying()||craft.flightPitch()<=5||craft.posY<=runwayY+.7)&&frame<60)return false;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),false);
            need(craft.flying()&&craft.flightPitch()>5&&craft.posY>runwayY+.7,"S pitches up and takes off from Taxi");
            climbY=craft.posY;
            next();return false;
        }
        if(stage==4&&frame>=23){
            if(craft.posY<=climbY+1&&frame<60)return false;
            need(craft.posY>climbY+1&&craft.driveSpeed>FlightEnvelope.TAKEOFF_SPEED,"released keys preserve forward flight and climb");
            turnYaw=craft.rotationYaw;turnX=craft.posX;turnZ=craft.posZ;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),true);
            next();return false;
        }
        if(stage==5&&frame>=9){
            if(craft.rotationYaw<=turnYaw+4&&frame<50)return false;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),false);
            need(craft.rotationYaw>turnYaw+4&&Math.hypot(craft.posX-turnX,craft.posZ-turnZ)>2,"D rotates while forward travel continues");
            priorPitch=craft.flightPitch();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);
            next();return false;
        }
        if(stage==6&&frame>=7){
            if(craft.flightPitch()>=priorPitch-4&&frame<45)return false;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
            need(craft.flightPitch()<priorPitch-4&&craft.flying(),"W lowers the nose");
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                    "png",new File(output,"shot_vehicle_status_maxwell_flight.png"));
            // The pilot has already taken off through the normal controls. Save the flight time to the build-height boundary.
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle actual=server(mc);
                need(actual.flying()&&actual.flightReadout().fuel>0,"server applied powered flight");
                actual.rotationYaw=0;
                actual.setPosition(ORIGIN.getX(),255.7,ORIGIN.getZ());
            }).get();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),true);
            next();return false;
        }
        if(stage==7){
            if(!craft.cruise.active()||craft.flightAltitude()<=256){need(frame<120,"powered S climb crosses Y=256");return false;}
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),false);
            need(Double.isInfinite(craft.groundClearance()),"cruise does not query ground clearance");
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle actual=server(mc);
                need(actual.cruise.active()&&actual.flightAltitude()>256&&actual.poweredFlight(),"authoritative crossing happened under powered flight ticks");
                need(((TileEntityConstructor)actual.view.getTileEntity(DRIVE)).purgeMaxwell(),"empty Maxwell tank during cruise");
            }).get();
            sawDeparture=true;
            next();return false;
        }
        if(stage==8){
            sawDescent|=craft.descendingFromFlight();
            if(!craft.cruise.active()&&craft.flightAltitude()<217&&craft.flightAltitude()>205){
                double clearance=craft.groundClearance();
                sawLongClearance|=Double.isFinite(clearance)&&clearance>70;
                need(craft.flying(),"Maxwell-only recovery continues below cruise toward terrain");
            }
            if(craft.flying()){settled=0;return false;}
            if(++settled<5)return false;
            need(!craft.hover&&craft.vehicleMode()==1&&Math.abs(craft.flightPitch())<.01&&Math.abs(craft.posY-runwayY)<.15&&craft.flightReadout().fuel==0&&craft.flightReadout().ratedThrust==1&&craft.flightReadout().thrust==0,
                    "power loss returns a level craft to Taxi on the runway; Y="+craft.posY+" runway="+runwayY+" pitch="+craft.flightPitch()+" fuel="+craft.flightReadout().fuel);
            need(sawDeparture&&sawDescent&&sawLongClearance,"observed powered entry, recovery and measured clearance beyond 32 blocks");
            System.out.println("[vandorlabs][reprolab] maxwell-flight-basic PASS real Status click taxi-roll pilot-takeoff keys pitch yaw coast powered-threshold fuel-loss reentry exact-clearance touchdown");
            return true;
        }
        return false;
    }

    private static String measurements(VehicleFlightReadout readout){
        return "drives="+readout.drives+" fuel="+readout.fuel+" mass="+readout.mass+" thrust="+readout.thrust;
    }
    private static void next(){stage++;frame=0;}
    private static void need(boolean yes,String message){if(!yes)throw new IllegalStateException("Vehicle Flight "+message+" stage="+stage+" frame="+frame);}
    private VehicleFlightClientChecks(){}
}
