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
public final class CabinWalkChecks {
    private static final String MODE=System.getenv().getOrDefault("VANDOR_LABS_REENTRY_MODE","physical");
    private static int stage,ticks;
    private static EntityGroundVehicle server;
    private static Vec3d start;
    private static double altitude,route,exitZ;
    private static Vec3d lastFeet;
    private static volatile boolean arrowSpawned;
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
        if(stage==10 && mc.player.getRidingEntity()==null && Math.abs(mc.player.posZ-exitZ)<96){
            key(mc.gameSettings.keyBindJump,false);
            need(mc.player.posY<altitude+5,"jump exits at geographic altitude rather than sky offset: "+mc.player.posY);
            System.out.println("[vandorlabs] cruise-physical PASS cabin walk "+MODE+" real motion, continuous camera, saved local feet, pilot seat click and geographic deck exit");
            return true;
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
        }else if(stage==4){
            Vec3d now=local.cabin.position(mc.player,1), before=local.cabin.position(mc.player,0);
            if(lastFeet!=null){
                need(now.z>=lastFeet.z-1e-6 && before.z>=lastFeet.z-1e-6,"walking camera replays a previous step "+lastFeet+" "+before+" "+now);
                need(now.z-before.z<=.321,"one tick motion bound");
            }
            lastFeet=now;
            if(ticks<=35)return false;
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
            Vec3d delta=new Vec3d(local.structure.seat).addVector(.5,.55,.5)
                    .subtract(local.cabin.position(mc.player,1).addVector(0,mc.player.getEyeHeight(),0));
            look(mc,(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
            advance(6);
        }else if(stage==6 && ticks>3){
            VehicleInteraction.Target pointed=VehicleInteraction.pointed(mc.player);
            need(pointed!=null&&pointed.hit.action==VehicleSelection.Action.BOARD,"standing pilot can aim at their actual seat; target="+(pointed==null?null:pointed.hit.cell.pos));
            task=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP pilot=(EntityPlayerMP)server.getControllingPassenger();
                pilot.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new net.minecraft.item.ItemStack(net.minecraft.init.Items.BOW));
                pilot.inventory.addItemStackToInventory(new net.minecraft.item.ItemStack(net.minecraft.init.Items.ARROW,2));
                pilot.inventoryContainer.detectAndSendChanges();
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(CabinWalkChecks.class);
            });
            advance(11);
        }else if(stage==11 && mc.player.getHeldItemMainhand().getItem()==net.minecraft.init.Items.BOW){
            need(!VehicleInteraction.supported(mc.player,VehicleInteraction.pointed(mc.player)),"native held item gets use before pilot seat");
            key(mc.gameSettings.keyBindUseItem,true);
            click(mc);
            advance(12);
        }else if(stage==12 && ticks>18){
            task=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP pilot=(EntityPlayerMP)server.getControllingPassenger();
                need(pilot.isHandActive()&&pilot.getActiveItemStack().getItem()==net.minecraft.init.Items.BOW,"native bow draw reached server");
            });
            advance(13);
        }else if(stage==13){
            key(mc.gameSettings.keyBindUseItem,false);
            mc.playerController.onStoppedUsingItem(mc.player);
            advance(14);
        }else if(stage==14 && ticks>6){
            task=mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP pilot=(EntityPlayerMP)server.getControllingPassenger();
                need(!pilot.isHandActive(),"release reached server");
                if(!server.cruise.projected())need(arrowSpawned,"arrow spawned in anchored physical world");
                System.out.println("[vandorlabs] cabin bow "+MODE+" vanilla draw/release, server arrow spawned="+arrowSpawned);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(CabinWalkChecks.class);
                pilot.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
                pilot.inventoryContainer.detectAndSendChanges();
            });
            advance(15);
        }else if(stage==15 && mc.player.getHeldItemMainhand().isEmpty()){
            click(mc);
            advance(7);
        }else if(stage==7 && !local.cabin.walking(mc.player)){
            need(local.flying()&&local.getControllingPassenger()==mc.player,"seat click returns to controls");
            chord(mc,true);advance(8);
        }else if(stage==8 && local.cabin.walking(mc.player)){
            chord(mc,false);look(mc,90,0);
            key(mc.gameSettings.keyBindForward,true);advance(9);
        }else if(stage==9 && ticks>20){
            key(mc.gameSettings.keyBindForward,false);
            exitZ=local.cruise.z();
            key(mc.gameSettings.keyBindJump,true);advance(10);
        }
        return false;
    }
    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void arrow(net.minecraftforge.event.entity.EntityJoinWorldEvent event){
        if(!event.getWorld().isRemote && event.getEntity() instanceof net.minecraft.entity.projectile.EntityArrow
                && ((net.minecraft.entity.projectile.EntityArrow)event.getEntity()).shootingEntity==server.getControllingPassenger())arrowSpawned=true;
    }
    private static void click(Minecraft mc)throws Exception{
        mc.objectMouseOver=new net.minecraft.util.math.RayTraceResult(net.minecraft.util.math.RayTraceResult.Type.MISS,VehicleInteraction.eyes(mc.player),EnumFacing.UP,BlockPos.ORIGIN);
        net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);
    }
    private static void look(Minecraft mc,float yaw,float pitch){
        mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;
        mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;
        mc.player.connection.sendPacket(new net.minecraft.network.play.client.CPacketPlayer.Rotation(yaw,pitch,false));
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
