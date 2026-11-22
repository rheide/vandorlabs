package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.blocks.BlockVandorDirectional;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.blocks.RedstoneScreenInteractions;
import com.vandorlabs.physics.FlightEnvelope;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.render.ScreenSurface;
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
    private static int stage,frame,total;
    private static double runwayY,climbY,turnYaw,turnX,turnZ,priorPitch;

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
        for(BlockPos pos:BlockPos.getAllInBox(ORIGIN.add(-25,-1,-100),ORIGIN.add(52,-1,8)))world.setBlockState(pos,Blocks.STONE.getDefaultState(),2);
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
        need(MaxwellRuntimeChecks.inlet(engine,engine.facing()).fill(new FluidStack(MaxwellRuntimeChecks.testFuel(),400),true)==400,"fill the assembled engine's inlet");
        cells.add(new VehicleStructure.Cell(new BlockPos(4,1,1),Blocks.STONE.getDefaultState(),null));
        BlockPos offset=DRIVE.subtract(staged);
        for(BlockPos at:BlockPos.getAllInBox(staged,staged.add(2,2,3))){
            net.minecraft.tileentity.TileEntity member=world.getTileEntity(at);
            NBTTagCompound data=VehicleTileData.translated(member.writeToNBT(new NBTTagCompound()),offset);
            cells.add(new VehicleStructure.Cell(at.add(offset),world.getBlockState(at),data));
        }
        EntityGroundVehicle craft=new EntityGroundVehicle(world);
        craft.install(new VehicleStructure(cells,SEAT,EnumFacing.NORTH,4));
        craft.setPosition(ORIGIN.getX(),ORIGIN.getY()+.6,ORIGIN.getZ());
        TileEntityConstructor carried=(TileEntityConstructor)craft.view.getTileEntity(DRIVE);
        need(carried.formed()&&carried.maxwellFuel()==400&&carried.maxwellThrustLevel(true)==15,"captured formed drive keeps available thrust and fuel");
        need(world.spawnEntity(craft),"spawn craft");
        id=craft.getUniqueID();
        player.connection.setPlayerLocation(ORIGIN.getX()+2.5,ORIGIN.getY()+2,ORIGIN.getZ()+5,180,0);
        need(craft.board(player),"board Pilot Seat");
    }

    private static void aim(Minecraft mc,EntityGroundVehicle craft,int mode)throws Exception {
        IBlockState state=craft.view.getBlockState(SCREEN);
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)craft.view.getTileEntity(SCREEN);
        ScreenSurface.Quad quad=RedstoneScreenInteractions.surface(state,tile,0);
        double column=(VehicleStatus.modeLeft(mode,true)+VehicleStatus.modeRight(mode,true))/256D;
        double row=(RedstoneScreenInteractions.rowTop(state.getBlock(),0)+17D)/RedstoneScreenInteractions.displayHeight(state.getBlock(),0);
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
        if(++total>450)throw new IllegalStateException("Vehicle Flight timeout stage="+stage+" frame="+frame);
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
            need(craft.takeoffRoll()&&!craft.flying()&&Math.abs(craft.posY-runwayY)<.08&&craft.driveSpeed>=FlightEnvelope.TAKEOFF_SPEED-1e-4,
                    "ground roll builds speed without liftoff before pitch input; "+motion);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),true);
            next();return false;
        }
        if(stage==3&&frame>=13){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),false);
            need(craft.flying()&&craft.flightPitch()>5&&craft.posY>runwayY+.7,"S pitches up and takes off from Taxi");
            climbY=craft.posY;
            next();return false;
        }
        if(stage==4&&frame>=23){
            need(craft.posY>climbY+1&&craft.driveSpeed>FlightEnvelope.TAKEOFF_SPEED,"released keys preserve forward flight and climb");
            turnYaw=craft.rotationYaw;turnX=craft.posX;turnZ=craft.posZ;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),true);
            next();return false;
        }
        if(stage==5&&frame>=9){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),false);
            need(craft.rotationYaw>turnYaw+4&&Math.hypot(craft.posX-turnX,craft.posZ-turnZ)>2,"D rotates while forward travel continues");
            priorPitch=craft.flightPitch();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);
            next();return false;
        }
        if(stage==6&&frame>=7){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
            need(craft.flightPitch()<priorPitch-4&&craft.flying(),"W lowers the nose");
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                    "png",new File(output,"shot_vehicle_status_maxwell_flight.png"));
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle actual=server(mc);
                need(actual.flying()&&actual.flightReadout().fuel>0,"server applied powered flight");
                need(((TileEntityConstructor)actual.view.getTileEntity(DRIVE)).purgeMaxwell(),"empty Maxwell tank during flight");
            }).get();
            next();return false;
        }
        if(stage==7){
            if(frame<=20)need(craft.flying()||frame<4,"Maxwell-only descent remains Flight while above runway");
            if(craft.flying()||frame<22)return false;
            need(!craft.hover&&craft.vehicleMode()==1&&Math.abs(craft.flightPitch())<.01&&Math.abs(craft.posY-runwayY)<.15&&craft.flightReadout().fuel==0,
                    "power loss returns a level craft to Taxi on the runway");
            System.out.println("[vandorlabs][reprolab] maxwell-flight-basic PASS real Status click taxi-roll pilot-takeoff keys pitch yaw coast fuel-loss landing");
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
