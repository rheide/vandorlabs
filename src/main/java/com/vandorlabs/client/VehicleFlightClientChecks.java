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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.util.vector.Matrix4f;

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
    private static int stage,frame,total,settled,impactWallZ,inversionShot;
    private static double runwayY,climbY,turnYaw,turnX,turnZ,priorPitch;
    private static float steepPitch,rollTravel,previousBank,heldBank;
    private static double limitedCeiling,limitedX,limitedZ,glideZ,landingX,landingZ;
    private static boolean sawInversion,sawLimitedDescent,sawGlideTravel,divePressed,viewTurned,sawHoverCoast,sawGroundRoll,retriedTakeoff;
    private static Vec3d cabinLook,cabinUp,hoverEntry;
    private static float cabinYaw;
    private static double hoverEntrySpeed;
    private static boolean sawImpact;
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
        Vec3d target=craft.toWorld(screenLocal),eye=VehicleInteraction.eyes(mc.player);
        Vec3d delta=target.subtract(eye);
        double[] raw=craft.pilotView(craft.visualPose(1)).inverseVector(delta.x,delta.y,delta.z);
        Vec3d input=new Vec3d(raw[0],raw[1],raw[2]);
        float yaw=(float)Math.toDegrees(Math.atan2(-input.x,input.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(input.y,Math.hypot(input.x,input.z)));
        mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;
        mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(yaw,pitch,false));
        Vec3d a=craft.toLocal(eye).subtract(new Vec3d(SCREEN)),b=craft.toLocal(eye.add(delta.scale(1.2))).subtract(new Vec3d(SCREEN));
        need(RedstoneScreenInteractions.statusAction(state,tile,0,a,b)==(mode==3?VehicleStatus.FLIGHT_ACTION:mode+1),"seated ray hits mode "+mode+" button at "+a+" toward "+b);
    }

    static boolean tick(Minecraft mc,File output)throws Exception{
        if(++total>1850)throw new IllegalStateException("Vehicle Flight timeout stage="+stage+" frame="+frame);
        EntityGroundVehicle craft=client(mc);
        if(craft==null||craft.structure==null||mc.player.getRidingEntity()!=craft)return false;
        frame++;
        if(stage==0&&frame>=28){
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
        if(stage==1&&frame>=10){
            aim(mc,craft,3);
            VehicleInteraction.Target hit=VehicleInteraction.pointed(mc.player);
            need(hit!=null&&hit.hit.cell.pos.equals(SCREEN)&&VehicleInteraction.supported(mc.player,hit),"real seated access to Flight button");
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP pilot=player(mc);
                VehicleInteraction.Target selected=VehicleInteraction.pointed(pilot);
                need(selected!=null&&selected.hit.cell.pos.equals(SCREEN)&&VehicleInteraction.supported(pilot,selected),
                        "server aim and held stack synced for Status click; yaw="+pilot.rotationYaw+" pitch="+pilot.rotationPitch+" held="+pilot.getHeldItemMainhand());
            }).get();
            useStatus(mc);
            next();return false;
        }
        if(stage==2){
            sawGroundRoll|=craft.takeoffRoll();
            // A shader warm-up can interrupt the first click before any ground motion.
            if(frame>=52&&!sawGroundRoll&&!retriedTakeoff&&!craft.flying()
                    &&Math.hypot(craft.posX-ORIGIN.getX(),craft.posZ-ORIGIN.getZ())<.05){
                aim(mc,craft,3);
                if(alignedServer(mc)){useStatus(mc);retriedTakeoff=true;}
            }
            if(frame<22)return false;
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
            if(!craft.flying()||craft.flightPitch()<=5||craft.posY<=runwayY+.7){
                String actual=mc.getIntegratedServer().callFromMainThread(()->flightState(server(mc))).get();
                need(false,"S pitches up and takes off from Taxi; client "+flightState(craft)+" server "+actual);
            }
            climbY=craft.posY;
            next();return false;
        }
        if(stage==4&&frame>=23){
            if(craft.posY<=climbY+1&&frame<60)return false;
            need(craft.posY>climbY+1&&craft.driveSpeed>FlightEnvelope.TAKEOFF_SPEED,"released keys preserve forward flight and climb");
            mc.getIntegratedServer().addScheduledTask(()->stagePose(server(mc),runwayY+25,12,0)).get();
            turnYaw=0;turnX=ORIGIN.getX();turnZ=ORIGIN.getZ()-5;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),true);
            next();return false;
        }
        if(stage==5&&frame>=9){
            if((craft.rotationYaw<=turnYaw+4||craft.flightBank()<=10)&&frame<50)return false;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),false);
            VehiclePose banked=craft.visualPose(1);
            double px=craft.structure.bounds.maxX/2,pz=craft.structure.bounds.maxZ/2;
            Vec3d right=banked.world(new Vec3d(px+1,1,pz),px,pz),left=banked.world(new Vec3d(px-1,1,pz),px,pz);
            need(craft.rotationYaw>turnYaw+4&&Math.hypot(craft.posX-turnX,craft.posZ-turnZ)>2&&banked.bank>10&&right.y<left.y-.2,
                    "D lowers the right wing while turning forward; bank="+banked.bank);
            mc.gameSettings.thirdPersonView=1;
            mc.player.prevRotationYaw=mc.player.rotationYaw=craft.rotationYaw+204;
            mc.player.prevRotationPitch=mc.player.rotationPitch=17;
            next();return false;
        }
        if(stage==6){
            if(!divePressed){
                if(frame==2)shot(mc,output,"shot_vehicle_flight_bank.png");
                if(frame==4){
                    VehicleStatusRenderChecks.airborneInstruments(mc,output,craft,SCREEN);
                    mc.getIntegratedServer().addScheduledTask(()->stagePose(server(mc),runwayY+24,14,0)).get();
                }
                if(frame<7||Math.abs(craft.flightBank())>.5||craft.flightPitch()<13){
                    need(frame<50,"plane settles into the upright pitch fixture; "+flightState(craft));return false;
                }
                priorPitch=craft.flightPitch();
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);
                divePressed=true;frame=0;
                return false;
            }
            if(craft.flightPitch()>=priorPitch-4&&frame<45)return false;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
            need(craft.flightPitch()<priorPitch-4&&craft.flying(),"W lowers the upright nose; "+flightState(craft));
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                    "png",new File(output,"shot_vehicle_status_maxwell_flight.png"));
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle actual=server(mc);
                stagePose(actual,runwayY+13,0,0);
            }).get();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),true);
            next();return false;
        }
        if(stage==7){
            if(craft.flightPitch()<49){need(frame<95&&craft.flying()&&!craft.descendingFromFlight(),"holding S passes the steep trim threshold; pitch="+craft.flightPitch()+" Y="+craft.posY);return false;}
            steepPitch=craft.flightPitch();
            mc.gameSettings.thirdPersonView=0;
            mc.player.prevRotationYaw=mc.player.rotationYaw=craft.rotationYaw+204;
            mc.player.prevRotationPitch=mc.player.rotationPitch=-16;
            Vec3d expected=craft.seatEye(craft.visualPose(1),mc.player,1);
            Vec3d vanilla=mc.player.getPositionEyes(1),camera=VehicleCameraRuntimeChecks.camera(mc);
            need(expected.distanceTo(vanilla)>.75&&camera.distanceTo(expected)<.16,
                    "steep first-person eye follows the tilted seat; camera="+camera+" expected="+expected+" upright="+vanilla);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),false);
            next();return false;
        }
        if(stage==8){
            if(frame==2)shot(mc,output,"shot_vehicle_flight_steep_cockpit.png");
            if(frame<15)return false;
            need(craft.flightPitch()<=steepPitch-1&&craft.flightPitch()>=44.9,
                    "released pitch trims slowly toward 45; peak="+steepPitch+" pitch="+craft.flightPitch());
            mc.getIntegratedServer().addScheduledTask(()->stagePose(server(mc),runwayY+37,3,0)).get();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),true);
            next();return false;
        }
        if(stage==9){
            if(frame==inversionShot)shot(mc,output,"shot_vehicle_flight_inverted.png");
            VehiclePose pose=craft.visualPose(1);
            float bank=pose.bank;
            if(frame>3)rollTravel+=MathHelper.wrapDegrees(bank-previousBank);
            previousBank=bank;
            need(frame<175&&craft.flying()&&!craft.descendingFromFlight(),"held D completes two uninterrupted rolls; total="+rollTravel);
            if(frame==9){
                mc.gameSettings.thirdPersonView=0;
                aim(mc,craft,3);
                Matrix4f inverse=VehicleCameraRuntimeChecks.inverseCamera(mc);
                cabinLook=localDirection(craft,pose,cameraLook(inverse));
                cabinUp=localDirection(craft,pose,cameraUp(inverse));
                cabinYaw=pose.yaw;
            }
            if(frame>9&&frame%3==0){
                VehiclePose half=craft.visualPose(.5F);
                Matrix4f inverse=VehicleCameraRuntimeChecks.inverseCamera(mc,.5F);
                double forward=localDirection(craft,half,cameraLook(inverse)).dotProduct(cabinLook);
                double up=localDirection(craft,half,cameraUp(inverse)).dotProduct(cabinUp);
                viewTurned|=Math.abs(MathHelper.wrapDegrees(pose.yaw-cabinYaw))>5;
                need(forward>.998&&up>.998,"unmoved mouse stays fixed in the changing cockpit frame; look="+forward+" up="+up+" bank="+bank);
            }
            if(!sawInversion&&Math.abs(bank)>160){
                double px=craft.structure.bounds.maxX/2,pz=craft.structure.bounds.maxZ/2;
                Vec3d hullTop=pose.world(new Vec3d(px,2,pz),px,pz);
                Vec3d hullBottom=pose.world(new Vec3d(px,0,pz),px,pz);
                need(hullTop.y<hullBottom.y-1.5,"the physical hull is upside down");
                Matrix4f inverse=VehicleCameraRuntimeChecks.inverseCamera(mc);
                need(inverse.m11<-.7&&cameraLook(inverse).dotProduct(VehicleInteraction.look(mc.player))>.999,
                        "the first-person camera is inverted and its crosshair matches the carried ray; up="+inverse.m11);
                VehicleInteraction.Target target=VehicleInteraction.pointed(mc.player);
                need(target!=null&&target.craft==craft&&target.hit.cell.pos.equals(SCREEN),"inverted pilot can point at the carried Status face without re-aiming");
                sawInversion=true;
                inversionShot=frame+1;
            }
            if(rollTravel<750)return false;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),false);
            need(sawInversion&&viewTurned,"two full rolls covered inversion and changing heading");
            next();return false;
        }
        if(stage==10){
            if(frame==9)heldBank=craft.flightBank();
            if(frame<18)return false;
            need(Math.abs(heldBank)>10&&Math.abs(MathHelper.wrapDegrees(craft.flightBank()-heldBank))<2,
                    "neutral roll control holds the selected bank; held="+heldBank+" current="+craft.flightBank());
            mc.getIntegratedServer().addScheduledTask(()->{
                // The two rolls can take the pilot well away from the original runway.
                // Keep the watched horizontal position while arranging the boundary climb.
                EntityGroundVehicle actual=server(mc);
                stageAttitude(actual,14,0);
                actual.setPosition(actual.posX,FlightEnvelope.CRUISE_ALTITUDE-.3,actual.posZ);
            }).get();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),true);
            next();return false;
        }
        if(stage==11){
            if(!craft.cruise.active()||craft.flightAltitude()<=FlightEnvelope.CRUISE_ALTITUDE){
                if(frame>=120){
                    String actual=mc.getIntegratedServer().callFromMainThread(()->flightState(server(mc))).get();
                    need(false,"powered S climb crosses the cruise boundary; client "+flightState(craft)+" server "+actual);
                }
                return false;
            }
            need(Double.isInfinite(craft.groundClearance()),"cruise does not query ground clearance");
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle actual=server(mc);
                VehicleFlightReadout readout=actual.flightReadout();
                need(actual.cruise.active()&&actual.flightAltitude()>FlightEnvelope.CRUISE_ALTITUDE&&actual.poweredFlight()&&readout.fuel>0,
                        "authoritative crossing happened under powered flight ticks");
                int reduced=(int)Math.ceil(readout.effectiveMass/FlightEnvelope.takeoffMass(readout.ratedThrust)*15)+2;
                need(reduced<15,"real fixture can support lower thrust");
                limitedCeiling=FlightEnvelope.maximumAltitude(readout.effectiveMass,readout.ratedThrust*reduced/15);
                need(limitedCeiling>256&&limitedCeiling+22<readout.maximumAltitude(),"positive lower thrust gives a lower cruise ceiling");
                moveCruise(actual,0,limitedCeiling+22-actual.flightAltitude(),0);
                configureAnalog((TileEntityConstructor)actual.view.getTileEntity(DRIVE),true,reduced);
                limitedX=actual.cruise.x();limitedZ=actual.cruise.z();
            }).get();
            sawDeparture=true;
            next();return false;
        }
        if(stage==12){
            need(frame<180&&craft.flying()&&craft.poweredFlight()&&!craft.hover&&!craft.descendingFromFlight(),
                    "reduced thrust preserves pilot Flight; altitude="+craft.flightAltitude()+" ceiling="+limitedCeiling);
            sawLimitedDescent|=craft.descendingToFlightLimit()&&craft.flightPitch()<0&&craft.driveSpeed>.85
                    &&Math.hypot(craft.cruise.x()-limitedX,craft.cruise.z()-limitedZ)>10;
            if(!sawLimitedDescent||craft.flightAltitude()>limitedCeiling+.25)return false;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),false);
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle actual=server(mc);
                for(BlockPos pos:BlockPos.getAllInBox(ORIGIN.add(-2,-1,-630),ORIGIN.add(15,-1,-89)))
                    world(mc).setBlockState(pos,Blocks.STONE.getDefaultState(),2);
                moveCruise(actual,ORIGIN.getX()-actual.cruise.x(),259-actual.flightAltitude(),ORIGIN.getZ()-actual.cruise.z());
                actual.driveSpeed=1.5;glideZ=actual.cruise.z();
                need(((TileEntityConstructor)actual.view.getTileEntity(DRIVE)).purgeMaxwell(),"empty Maxwell tank during cruise");
            }).get();
            next();return false;
        }
        if(stage==13){
            sawDescent|=craft.descendingFromFlight();
            double routeZ=craft.cruise.active()?craft.cruise.z():craft.posZ;
            sawGlideTravel|=craft.descendingFromFlight()&&craft.flightPitch()<0&&craft.driveSpeed>.75&&routeZ<glideZ-12;
            if(!craft.cruise.active()&&craft.flightAltitude()<217&&craft.flightAltitude()>205){
                double clearance=craft.groundClearance();
                sawLongClearance|=Double.isFinite(clearance)&&clearance>70;
                need(craft.flying(),"Maxwell-only recovery continues below cruise toward terrain");
            }
            if(craft.flying()){settled=0;return false;}
            if(++settled<5)return false;
            need(!craft.hover&&craft.vehicleMode()==1&&Math.abs(craft.flightPitch())<.01&&Math.abs(craft.posY-runwayY)<.15&&craft.flightReadout().fuel==0&&craft.flightReadout().ratedThrust==1&&craft.flightReadout().thrust==0,
                    "power loss returns a level craft to Taxi on the runway; Y="+craft.posY+" runway="+runwayY+" pitch="+craft.flightPitch()+" fuel="+craft.flightReadout().fuel);
            need(sawDeparture&&sawDescent&&sawLongClearance&&sawGlideTravel,
                    "observed powered entry, forward glide and measured clearance beyond 32 blocks");
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle actual=server(mc);
                TileEntityConstructor engine=(TileEntityConstructor)actual.view.getTileEntity(DRIVE);
                configureAnalog(engine,false,0);
                need(MaxwellRuntimeChecks.inlet(engine,engine.facing()).fill(new FluidStack(MaxwellRuntimeChecks.testFuel(),1000),true)==1000,
                        "refuel the real carried Maxwell for the impact sortie");
                landingX=actual.posX;
                landingZ=actual.posZ;
            }).get();
            next();return false;
        }
        if(stage==14&&frame>=12){
            if(craft.flightReadout().fuel==0){
                need(frame<100,"refueled aircraft and runway synchronized");return false;
            }
            aim(mc,craft,3);
            if(frame<15)return false;
            boolean ready=mc.getIntegratedServer().callFromMainThread(()->{
                EntityPlayerMP pilot=player(mc);
                VehicleInteraction.Target selected=VehicleInteraction.pointed(pilot);
                return server(mc).onGround&&server(mc).getControllingPassenger()==pilot
                        && selected!=null&&selected.hit.cell.pos.equals(SCREEN);
            }).get();
            if(!ready){need(frame<100,"second departure must synchronize seat, runway and Status aim");return false;}
            useStatus(mc);
            next();return false;
        }
        if(stage==15){
            if(!craft.takeoffRoll()||craft.driveSpeed<FlightEnvelope.TAKEOFF_SPEED-1e-4){
                need(frame<85,"second takeoff uses the real Status button");return false;
            }
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),true);
            next();return false;
        }
        if(stage==16){
            if(!craft.flying()||craft.flightPitch()<6){need(frame<60,"second sortie airborne");return false;}
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(),false);
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle actual=server(mc);
                actual.rotationYaw=0;
                actual.setPosition(landingX,runwayY+9,landingZ-10);
                impactWallZ=MathHelper.floor(landingZ)-28;
                for(BlockPos at:BlockPos.getAllInBox(new BlockPos(landingX-3,runwayY,impactWallZ),new BlockPos(landingX+20,runwayY+28,impactWallZ)))
                    world(mc).setBlockState(at,Blocks.STONE.getDefaultState(),2);
            }).get();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),true);
            next();return false;
        }
        if(stage==17){
            if(!sawImpact&&craft.descendingFromFlight()){
                sawImpact=true;
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(),false);
                need(Math.abs(craft.flightBank())>8&&craft.flightReadout().fuel>0,"terrain impact stopped a still-powered, banked aircraft");
                mc.gameSettings.thirdPersonView=1;
            }
            if(craft.flying()){settled=0;need(frame<200,"banked aircraft breaks contact and reaches the runway; Y="+craft.posY+" yaw="+craft.rotationYaw+" bank="+craft.flightBank());return false;}
            if(++settled<5)return false;
            need(sawImpact&&craft.vehicleMode()==1&&Math.abs(craft.posY-runwayY)<.15&&craft.flightBank()==0&&craft.flightPitch()==0,
                    "powered wall contact recovers into level Taxi at the runway before the wall");
            boolean separated=mc.getIntegratedServer().callFromMainThread(()->server(mc).getEntityBoundingBox().minZ>=impactWallZ+1-.001).get();
            need(separated,"aircraft was never advanced through the wall or left embedded");
            shot(mc,output,"shot_vehicle_flight_terrain_recovery.png");
            mc.gameSettings.thirdPersonView=0;
            mc.getIntegratedServer().addScheduledTask(()->spawnHover(mc)).get();
            next();return false;
        }
        if(stage==18&&frame>=14){
            need(frame<120,"hover-capable aircraft receives its seat and fueled snapshot");
            if(!craft.hover||!craft.flightReadout().canFly())return false;
            aim(mc,craft,3);
            if(!alignedServer(mc))return false;
            useStatus(mc);
            next();return false;
        }
        if(stage==19){
            need(frame<120,"real Flight click from Hover builds airspeed; "+flightState(craft));
            if(!craft.flying()||craft.driveSpeed<1.4)return false;
            aim(mc,craft,2);
            if(!alignedServer(mc))return false;
            hoverEntry=craft.getPositionVector();
            hoverEntrySpeed=craft.driveSpeed;
            useStatus(mc);
            next();return false;
        }
        if(stage==20){
            need(frame<80,"Flight approaches Hover at a controlled speed; "+flightState(craft));
            if(craft.flying()){
                double travel=craft.getPositionVector().subtract(hoverEntry).lengthVector();
                sawHoverCoast|=craft.descendingFromFlight()&&travel>2
                        &&craft.driveSpeed<hoverEntrySpeed-.12&&craft.driveSpeed>EntityGroundVehicle.HOVER_CRUISE_SPEED;
                return false;
            }
            boolean carryingSpeed=mc.getIntegratedServer().callFromMainThread(()->{
                EntityGroundVehicle actual=server(mc);
                return actual.hover&&!actual.flying()&&actual.driveSpeed>.35&&actual.driveSpeed<=EntityGroundVehicle.HOVER_CRUISE_SPEED
                        &&Math.hypot(actual.posX-hoverEntry.x,actual.posZ-hoverEntry.z)>5;
            }).get();
            need(sawHoverCoast&&craft.hover&&carryingSpeed,
                    "actual Hover click coasts through an intermediate speed and hands forward movement into Hover");
            aim(mc,craft,3);
            next();return false;
        }
        if(stage==21){
            need(frame<120,"seated pilot has Flight target before runway approach");
            aim(mc,craft,3);
            if(!alignedServer(mc))return false;
            useStatus(mc);
            next();return false;
        }
        if(stage==22){
            need(frame<100,"pilot returns to powered Flight for runway approach");
            if(!craft.flying()||craft.driveSpeed<1)return false;
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle actual=server(mc);
                // Start a small nose-down final over the same runway as the original departure.
                actual.rotationYaw=0;
                actual.setPosition(ORIGIN.getX(),ORIGIN.getY()+1.5,ORIGIN.getZ()-8);
            }).get();
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);
            next();return false;
        }
        if(stage==23){
            need(frame<75,"powered touchdown over runway: "+flightState(craft));
            if(!craft.flying()){
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
                need(craft.vehicleMode()==1&&Math.abs(craft.posY-ORIGIN.getY())<.15&&craft.driveSpeed>.45,
                    "wheel touchdown keeps forward airspeed in Taxi: "+flightState(craft));
                landingZ=craft.posZ;
                next();return false;
            }
            if(craft.flightPitch()<-6)KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
            return false;
        }
        if(stage==24){
            if(frame<9)return false;
            need(craft.posZ<landingZ-2&&Math.abs(craft.posY-ORIGIN.getY())<.15,
                    "forward rollout settles on runway with no embeds");
            System.out.println("[vandorlabs][reprolab] maxwell-flight-basic PASS real Status click taxi-roll pilot-takeoff keys physical-bank sustained-roll cabin-locked-camera carried-ray steep-trim powered-throttle-limit forward-glide reentry exact-clearance touchdown terrain-impact hover-coast powered-wheel-touchdown forward-rollout");
            return true;
        }
        return false;
    }

    private static Vec3d cameraLook(Matrix4f inverse){return new Vec3d(-inverse.m20,-inverse.m21,-inverse.m22);}
    private static Vec3d cameraUp(Matrix4f inverse){return new Vec3d(inverse.m10,inverse.m11,inverse.m12);}

    private static Vec3d localDirection(EntityGroundVehicle craft,VehiclePose pose,Vec3d direction){
        double px=craft.structure.bounds.maxX/2,pz=craft.structure.bounds.maxZ/2;
        Vec3d origin=new Vec3d(pose.x,pose.y,pose.z);
        return pose.local(origin.add(direction),px,pz).subtract(pose.local(origin,px,pz)).normalize();
    }

    private static boolean alignedServer(Minecraft mc)throws Exception{
        return mc.getIntegratedServer().callFromMainThread(()->{
            EntityPlayerMP pilot=player(mc);
            VehicleInteraction.Target target=VehicleInteraction.pointed(pilot);
            return pilot.getHeldItemMainhand().isEmpty()&&target!=null&&target.craft==server(mc)&&target.hit.cell.pos.equals(SCREEN);
        }).get();
    }

    private static void useStatus(Minecraft mc)throws Exception{
        mc.objectMouseOver=new net.minecraft.util.math.RayTraceResult(net.minecraft.util.math.RayTraceResult.Type.MISS,
                VehicleInteraction.eyes(mc.player),EnumFacing.UP,BlockPos.ORIGIN);
        ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);
    }

    private static void spawnHover(Minecraft mc){
        EntityGroundVehicle grounded=server(mc);
        EntityPlayerMP pilot=player(mc);
        List<VehicleStructure.Cell> cells=new ArrayList<>(grounded.structure.cells);
        String[] lift={"antigravity_plate","repulsor_array","vertical_hover_thruster"};
        for(int z=0;z<lift.length;z++){
            IBlockState state=Block.getBlockFromName("vandorlabs:"+lift[z]).getDefaultState()
                    .withProperty(com.vandorlabs.blocks.BlockPropulsionLight.FACING,EnumFacing.DOWN);
            cells.add(new VehicleStructure.Cell(new BlockPos(0,0,z),state,null));
        }
        IBlockState thruster=Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState()
                .withProperty(com.vandorlabs.blocks.BlockPropulsionLight.FACING,EnumFacing.SOUTH);
        cells.add(new VehicleStructure.Cell(new BlockPos(0,1,3),thruster,null));
        EntityGroundVehicle craft=new EntityGroundVehicle(world(mc));
        craft.install(new VehicleStructure(cells,SEAT,EnumFacing.NORTH,4));
        craft.setPosition(grounded.posX-16,runwayY+31,grounded.posZ+36);
        need(craft.setHover(true)&&world(mc).spawnEntity(craft),"spawn the truly equipped Hover aircraft");
        TileEntityConstructor drive=(TileEntityConstructor)craft.view.getTileEntity(DRIVE);
        if(drive.maxwellFuel()==0)need(MaxwellRuntimeChecks.inlet(drive,drive.facing())
                .fill(new FluidStack(MaxwellRuntimeChecks.testFuel(),1000),true)==1000,"fuel the Hover aircraft");
        grounded.setTransferInProgress(true);
        pilot.dismountRidingEntity();
        grounded.setDead();
        pilot.connection.setPlayerLocation(craft.posX+2.5,craft.posY+2,craft.posZ+5,180,0);
        need(craft.board(pilot),"board the second aircraft");
        id=craft.getUniqueID();
    }

    private static void stagePose(EntityGroundVehicle craft,double altitude,float pitch,float bank){
        stageAttitude(craft,pitch,bank);
        craft.setPosition(ORIGIN.getX(),altitude,ORIGIN.getZ()-5);
    }

    private static void stageAttitude(EntityGroundVehicle craft,float pitch,float bank){
        craft.rotationYaw=0;
        ReflectionHelper.setPrivateValue(EntityGroundVehicle.class,craft,pitch,"flightPitch");
        ReflectionHelper.setPrivateValue(EntityGroundVehicle.class,craft,bank,"flightBank");
        ReflectionHelper.setPrivateValue(EntityGroundVehicle.class,craft,0F,"flightRollRate");
    }

    private static void moveCruise(EntityGroundVehicle craft,double dx,double dy,double dz){
        try{
            Object moved=ReflectionHelper.findMethod(VehicleCruise.class,"move","move",double.class,double.class,double.class)
                    .invoke(craft.cruise,dx,dy,dz);
            need(Boolean.TRUE.equals(moved),"fixture travels in the high flight band");
        }catch(ReflectiveOperationException failure){
            throw new IllegalStateException("Vehicle Flight could not position the cruise fixture",failure);
        }
    }

    private static void configureAnalog(TileEntityConstructor engine,boolean enabled,int level){
        engine.configureDrive(new com.vandorlabs.drives.DriveSettings(true,false,1,8));
        engine.configureMachine(new com.vandorlabs.navigation.NavigationScreenSettings(-1,false,1,enabled,false),
                new int[0],false,127,0,com.vandorlabs.redstone.ChannelList.EMPTY);
        engine.setChannelLevel(level);
    }

    private static void shot(Minecraft mc,File output,String name)throws Exception{
        javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),
                "png",new File(output,name));
    }

    private static String flightState(EntityGroundVehicle craft){
        return "roll="+craft.takeoffRoll()+" flight="+craft.flying()+" recovery="+craft.descendingFromFlight()
                +" pitch="+craft.flightPitch()+" bank="+craft.flightBank()+" speed="+craft.driveSpeed+" pos="+craft.getPositionVector();
    }

    private static String measurements(VehicleFlightReadout readout){
        return "drives="+readout.drives+" fuel="+readout.fuel+" mass="+readout.mass+" thrust="+readout.thrust;
    }
    private static void next(){stage++;frame=0;}
    private static void need(boolean yes,String message){if(!yes)throw new IllegalStateException("Vehicle Flight "+message+" stage="+stage+" frame="+frame);}
    private VehicleFlightClientChecks(){}
}
