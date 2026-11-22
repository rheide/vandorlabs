package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.client.CruiseSky;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;

@SideOnly(Side.CLIENT)
public final class CruiseRuntimeChecks {
    private static final String RESTART = System.getenv("VANDOR_LABS_CRUISE_PHASE");
    private static final String SAVE_KEY = "VandorCruiseFixture";
    private static int stage,ticks;
    private static Future<?> work;
    private static EntityGroundVehicle craft;
    private static double startX,destination;
    private static void require(boolean ok,String detail){if(!ok)throw new IllegalStateException("Cruise: "+detail);}

    private static void place(Minecraft mc){
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        WorldServer world=pilot.getServerWorld();
        BlockPos seat=BlockPos.ORIGIN;
        craft=new EntityGroundVehicle(world);
        craft.install(new VehicleStructure(Arrays.asList(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null),
                new VehicleStructure.Cell(seat.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null)),seat,EnumFacing.NORTH,1));
        startX=pilot.posX;
        craft.setPosition(startX,255.9,pilot.posZ);
        world.spawnEntity(craft);
        pilot.setPositionAndUpdate(startX,256,pilot.posZ);
        craft.assignSeat(pilot,seat);
        require(pilot.startRiding(craft,true),"pilot boards");
        craft.hover=true;craft.hoverTarget=255.9;
    }

    private static void enter(){
        // Set a recovery flight so the production physics levels the craft and descends on its own.
        NBTTagCompound state=new NBTTagCompound();state.setByte("MaxwellFlightPhase",(byte)3);
        craft.readFlightMode(state);
        craft.setPosition(startX,255.9,craft.posZ);
        require(craft.cruise.move(0,1,0),"cross build ceiling");
        for(int i=0;i<64;i++)require(craft.cruise.move(64,16,0),"route step "+i);
        destination=craft.cruise.x();
        require(Math.abs(craft.posX-startX)<.001&&destination-startX==4096,"horizontal route keeps physical anchor");
        require(craft.cruise.altitude()>1200&&Double.isInfinite(craft.groundClearance()),"logical altitude without a clearance query");
        for(int i=512;i<=4096;i+=256)require(!craft.world.isBlockLoaded(new BlockPos(startX+i,64,craft.posZ)),"cruise loaded route at "+i);
        require(Math.abs(com.vandorlabs.navigation.NavigationVehicle.center(craft).x-destination)<3,"navigation follows geographic route");
        NBTTagCompound saved=craft.writeToNBT(new NBTTagCompound());
        EntityGroundVehicle loaded=new EntityGroundVehicle(craft.world);
        loaded.readFromNBT(saved);
        require(loaded.cruise.active()&&loaded.cruise.x()==destination&&loaded.cruise.altitude()==craft.cruise.altitude(),"persisted route and altitude");
    }

    private static void saveFlight(Minecraft mc) {
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        NBTTagCompound marker=new NBTTagCompound();
        marker.setUniqueId("Aircraft",craft.getUniqueID());
        marker.setDouble("OriginX",startX);
        marker.setDouble("DestinationX",destination);
        pilot.getEntityData().setTag(SAVE_KEY,marker);
        mc.getIntegratedServer().getPlayerList().saveAllPlayerData();
        mc.getIntegratedServer().saveAllWorlds(false);
    }

    private static void resumeFlight(Minecraft mc) {
        EntityPlayerMP pilot=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        NBTTagCompound marker=pilot.getEntityData().getCompoundTag(SAVE_KEY);
        require(marker.hasUniqueId("Aircraft"),"cold restart has the saved player");
        require(pilot.getRidingEntity() instanceof EntityGroundVehicle,"cold restart restores the mounted pilot");
        craft=(EntityGroundVehicle)pilot.getRidingEntity();
        require(craft.getUniqueID().equals(marker.getUniqueId("Aircraft")),"same saved aircraft");
        startX=marker.getDouble("OriginX");
        destination=marker.getDouble("DestinationX");
        require(craft.cruise.active() && craft.cruise.x()==destination && craft.posX==startX,"restored cruise route and anchor");
        require(craft.cruise.altitude()>256 && craft.descendingFromFlight(),"restored airborne recovery");
    }

    public static boolean tick(Minecraft mc,File out) throws Exception {
        if(++ticks>800)throw new IllegalStateException("Cruise fixture timeout at stage "+stage+" rider="+mc.player.getRidingEntity());
        if(work!=null){if(!work.isDone())return false;work.get();work=null;ticks=0;}
        if(stage==0 && "read".equals(RESTART)){
            work=mc.getIntegratedServer().addScheduledTask(()->resumeFlight(mc));stage=2;
        }else if(stage==0){
            work=mc.getIntegratedServer().addScheduledTask(()->place(mc));stage=1;
        }else if(stage==1&&ticks>10&&mc.player.getRidingEntity() instanceof EntityGroundVehicle){
            work=mc.getIntegratedServer().addScheduledTask(CruiseRuntimeChecks::enter);stage=2;
        }else if(stage==2&&mc.player.getRidingEntity() instanceof EntityGroundVehicle&&CruiseSky.ready()){
            mc.gameSettings.thirdPersonView=1;
            mc.player.rotationPitch=mc.player.prevRotationPitch=35;
            mc.gameSettings.hideGUI=true;stage=3;ticks=0;
        }else if(stage==3&&ticks>15){
            BufferedImage shot=ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            ImageIO.write(shot,"png",new File(out,"maxwell-cruise.png"));
            if("write".equals(RESTART)){
                work=mc.getIntegratedServer().addScheduledTask(()->saveFlight(mc));stage=5;return false;
            }
            work=mc.getIntegratedServer().addScheduledTask(()->{
                require(craft.cruise.active(),"craft still cruising");
                require(craft.cruise.move(0,255-craft.cruise.altitude(),0),"reenter");
                require(!craft.cruise.active()&&Math.abs(craft.posX-destination)<.01,"reentered at geographic destination");
                require(craft.world.isBlockLoaded(new BlockPos(destination,64,craft.posZ)),"destination prepared");
                require(!craft.world.isBlockLoaded(new BlockPos(startX+2048,64,craft.posZ)),"middle of route stayed unloaded");
            });stage=4;
        }else if(stage==4&&mc.player.getRidingEntity() instanceof EntityGroundVehicle&&Math.abs(mc.player.posX-destination)<4&&mc.player.posY<300){
            System.out.println("[vandorlabs] maxwell-cruise PASS route-unloaded rider-seed-view saved-route destination-remount");
            return true;
        }
        if(stage==5 && work==null){
            System.out.println("[vandorlabs] maxwell-cruise PASS saved mounted pilot in flight for a cold restart");
            return true;
        }
        return false;
    }
    private CruiseRuntimeChecks(){}
}
