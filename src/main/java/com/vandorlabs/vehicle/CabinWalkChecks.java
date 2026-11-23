package com.vandorlabs.vehicle;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.config.BlockAvailability;
import com.vandorlabs.drives.MaxwellRuntimeChecks;
import com.vandorlabs.tiles.TileEntityConstructor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class CabinWalkChecks {
    private static final String MODE=System.getenv().getOrDefault("VANDOR_LABS_REENTRY_MODE","physical");
    private static int stage,ticks;
    private static EntityGroundVehicle server;
    private static Vec3d start;
    private static double altitude,route;
    private static Future<?> task;

    private static void need(boolean test,String why){
        if(!test)throw new IllegalStateException("Cabin walk "+MODE+" stage "+stage+" "+why);
    }
    private static void advance(int next){stage=next;ticks=0;}
    private static void key(KeyBinding key,boolean held){KeyBinding.setKeyBindState(key.getKeyCode(),held);}
    private static void chord(Minecraft mc,boolean held){
        key(mc.gameSettings.keyBindSprint,held);key(mc.gameSettings.keyBindSneak,held);
    }
    static boolean tick(Minecraft mc)throws Exception{
        need(++ticks<240,"timeout, rider="+mc.player.getRidingEntity());
        if(task!=null){if(!task.isDone())return false;task.get();task=null;}
        if(stage==0){
            Configuration config=new Configuration(new File(mc.mcDataDir,"config/walking-cruise.cfg"));config.load();
            config.get("vehicles","cruiseCoordinates","anchored").set(MODE);config.save();BlockAvailability.load(config.getConfigFile());
            mc.displayGuiScreen(null);
            task=mc.getIntegratedServer().addScheduledTask(()->place(mc));advance(1);return false;
        }
        if(!(mc.player.getRidingEntity() instanceof EntityGroundVehicle))return false;
        EntityGroundVehicle local=(EntityGroundVehicle)mc.player.getRidingEntity();
        if(local.view==null)return false;
        if(stage==1 && ticks>=15){
            task=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP pilot=(EntityPlayerMP)server.getControllingPassenger();
                server.requestFlight(pilot);
                server.setPosition(server.posX,server.cruise.handoffAltitude()+2,server.posZ);
                need(server.cruise.move(0,1,0),"enter cruise");
                need(server.cruise.projected()=="physical".equals(MODE),"chosen tracking mode "+server.cruise.projected());
                need(server.cruise.move(0,64,8192),"remote route");
                server.applyFlightPose(new VehiclePose(server.posX,server.posY,server.posZ,0,8,12,EnumFacing.NORTH));
                VehicleNetwork.state(server);
            });
            advance(2);
        }else if(stage==2 && local.cruise.active() && local.flying()){
            chord(mc,true);advance(3);
        }else if(stage==3 && local.cabin.walking(mc.player)){
            if(ticks<6)return false; // Holding the gesture across frames must not toggle the seat again.
            chord(mc,false);
            start=local.cabin.position(mc.player,1);
            mc.player.rotationYaw=mc.player.prevRotationYaw=0;
            mc.player.rotationPitch=mc.player.prevRotationPitch=0;
            mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(0,0,false));
            key(mc.gameSettings.keyBindForward,true);
            route=local.cruise.z();advance(4);
        }else if(stage==4 && ticks>35){
            key(mc.gameSettings.keyBindForward,false);
            Vec3d here=local.cabin.position(mc.player,1);
            need(here!=null&&here.z-start.z>1.3,"real movement packets walk along floor, start="+start+" end="+here);
            need(here.z<5-.29,"body stops before end wall "+here);
            need(Math.abs(local.flightPitch())<.1&&Math.abs(local.flightBank())<.1,"aircraft levels while pilot walks");
            need(Math.abs(local.cruise.z()-route)>4,"aircraft continued flying");
            altitude=local.flightAltitude();
            task=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP pilot=(EntityPlayerMP)server.getControllingPassenger();
                Vec3d p=server.cabin.position(pilot,1);
                need(p!=null&&p.distanceTo(here)<.25,"authoritative walker agrees with actual client");
                EntityGroundVehicle restored=new EntityGroundVehicle(server.world);
                restored.readFromNBT(server.writeToNBT(new NBTTagCompound()));
                need(restored.cabin.walking(pilot)&&restored.cabin.position(pilot,1).distanceTo(p)<.00001,"saved local feet survive entity reload");
                restored.setDead();
                WorldServer world=pilot.getServerWorld();
                need(!world.getChunkProvider().chunkExists(((int)server.cruise.x())>>4,((int)server.cruise.z())>>4),"walking requests no distant terrain");
            });
            advance(5);
        }else if(stage==5 && ticks>8){
            need(Math.abs(local.flightAltitude()-altitude)<.3,"level hold maintains altitude");
            chord(mc,true);advance(6);
        }else if(stage==6 && !local.cabin.walking(mc.player)){
            chord(mc,false);
            need(local.flying()&&local.getControllingPassenger()==mc.player,"returns to original pilot seat in flight");
            System.out.println("[vandorlabs] cruise-physical PASS cabin walk "+MODE+" actual Ctrl+Shift, walk, wall, pose hold, saved local feet, seat return");
            return true;
        }
        return false;
    }
    private static void place(Minecraft mc){
        EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
        WorldServer world=player.getServerWorld();
        List<VehicleStructure.Cell> cells=new ArrayList<>();
        BlockPos seat=new BlockPos(1,1,0);
        for(int z=0;z<=5;z++)for(int x=0;x<=2;x++){
            cells.add(new VehicleStructure.Cell(new BlockPos(x,0,z),Blocks.STONE.getDefaultState(),null));
            if(z==5)cells.add(new VehicleStructure.Cell(new BlockPos(x,1,z),Blocks.STONE.getDefaultState(),null));
        }
        cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        cells.add(new VehicleStructure.Cell(seat.east(),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));
        BlockPos assembly=new BlockPos(player.posX+12,130,player.posZ);
        TileEntityConstructor drive=MaxwellRuntimeChecks.assemble(world,assembly,3,EnumFacing.NORTH,0,0);
        MaxwellRuntimeChecks.inlet(drive,drive.facing()).fill(new FluidStack(MaxwellRuntimeChecks.testFuel(),1000),true);
        BlockPos offset=new BlockPos(3,0,1).subtract(assembly);
        for(BlockPos pos:BlockPos.getAllInBox(assembly,assembly.add(2,2,3))){
            NBTTagCompound tag=VehicleTileData.translated(world.getTileEntity(pos).writeToNBT(new NBTTagCompound()),offset);
            cells.add(new VehicleStructure.Cell(pos.add(offset),world.getBlockState(pos),tag));
        }
        server=new EntityGroundVehicle(world);
        server.install(new VehicleStructure(cells,seat,EnumFacing.NORTH,1));
        server.setPosition(player.posX,200,player.posZ);
        server.hover=true;server.hoverTarget=server.posY;
        need(world.spawnEntity(server),"spawn");
        player.connection.setPlayerLocation(server.posX+1.5,201,server.posZ+.5,0,0);
        need(server.board(player),"board");
    }
}
