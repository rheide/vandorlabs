package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Actual key bindings, client ticks and authenticated FLIGHT packets. */
final class TaxiHoverGestureChecks {
    private static UUID id; private static int stage,frame,total;
    private static int firstTapTick;
    private static VehicleStructure original;
    private static UUID nextId;
    private static final BlockPos BASE=new BlockPos(184,101,48),SEAT=new BlockPos(1,1,0);
    private static void require(boolean yes,String text){if(!yes)throw new IllegalStateException("Taxi/Hover gesture stage="+stage+" frame="+frame+": "+text);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle server(Minecraft mc){return (EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id);}
    private static EntityGroundVehicle client(Minecraft mc){for(EntityGroundVehicle e:VehicleLookup.loaded(mc.world))if(e.getUniqueID().equals(id))return e;return null;}
    private static EntityGroundVehicle find(Minecraft mc,UUID wanted){for(EntityGroundVehicle e:VehicleLookup.loaded(mc.world))if(e.getUniqueID().equals(wanted))return e;return null;}
    private static void jump(Minecraft mc,boolean down){KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),down);}
    private static void next(){stage++;frame=0;}
    private static void expect(Minecraft mc,boolean hover,String label)throws Exception{mc.getIntegratedServer().addScheduledTask(()->require(server(mc).hover==hover,label+" server")).get();require(client(mc).hover==hover,label+" client");System.out.println("[vandorlabs][reprolab] taxi-hover-gesture PASS "+label);}
    static boolean tick(Minecraft mc)throws Exception{
        require(++total<900,"timeout riding="+mc.player.getRidingEntity()+" screen="+mc.currentScreen);
        if(stage==0){
            jump(mc,false);mc.getIntegratedServer().addScheduledTask(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP p=player(mc);p.dismountRidingEntity();p.setSneaking(false);
                for(BlockPos at:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(8,8,5)))world.setBlockState(at,at.getY()==100?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
                List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null));com.vandorlabs.tiles.TileEntityPilotSeat pilot=new com.vandorlabs.tiles.TileEntityPilotSeat();pilot.setPos(SEAT);pilot.setTaxiMode(false);pilot.owner=p.getUniqueID();cells.add(new VehicleStructure.Cell(SEAT,ModBlocks.PILOT_SEAT.getDefaultState(),pilot.writeToNBT(new net.minecraft.nbt.NBTTagCompound())));cells.add(new VehicleStructure.Cell(new BlockPos(4,0,0),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));
                String[] lifts={"antigravity_plate","repulsor_array","vertical_hover_thruster"};for(int i=0;i<3;i++)cells.add(new VehicleStructure.Cell(new BlockPos(i,0,1),Block.getBlockFromName("vandorlabs:"+lifts[i]).getDefaultState(),null));cells.add(new VehicleStructure.Cell(new BlockPos(3,0,0),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),null));
                EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,SEAT,EnumFacing.NORTH,1));craft.setPosition(BASE.getX(),BASE.getY(),BASE.getZ());require(world.spawnEntity(craft),"spawn");id=craft.getUniqueID();p.setPositionAndUpdate(BASE.getX(),BASE.getY()+2,BASE.getZ());require(craft.board(p),"real player boards");
            }).get();next();return false;
        }
        EntityGroundVehicle craft=client(mc);
        if(stage==1){if(craft!=null&&mc.player.getRidingEntity()==craft&&frame>=30){require(craft.getControllingPassenger()==mc.player,"client driver synchronized");next();return false;}}
        else if(stage==2){if(frame==0)jump(mc,true);if(frame==2)jump(mc,false);if(frame==18){expect(mc,false,"single press stays Taxi");next();return false;}}
        else if(stage==3){if(frame==0)jump(mc,true);if(frame==25)jump(mc,false);if(frame==35){expect(mc,false,"held jump stays Taxi");next();return false;}}
        else if(stage==4||stage==5){if(frame==0||frame==4)jump(mc,true);if(frame==2||frame==6)jump(mc,false);if(frame==20){expect(mc,stage==4,stage==4?"double press enters Hover":"double press returns Taxi");next();return false;}}
        else if(stage==6){if(frame==0)jump(mc,true);if(frame==1)jump(mc,false);if(frame==2)mc.displayGuiScreen(new GuiScreen(){@Override public boolean doesGuiPauseGame(){return false;}});if(frame==3)mc.displayGuiScreen(null);if(frame==4)jump(mc,true);if(frame==5)jump(mc,false);if(frame==18){expect(mc,false,"GUI interruption clears tap");next();return false;}}
        else if(stage==7){
            if(frame==0){firstTapTick=mc.player.ticksExisted;jump(mc,true);}
            if(frame==1){jump(mc,false);mc.getIntegratedServer().addScheduledTask(()->player(mc).dismountRidingEntity()).get();mc.player.dismountRidingEntity();}
            if(frame==2){mc.getIntegratedServer().addScheduledTask(()->require(server(mc).board(player(mc)),"remount")).get();require(mc.player.startRiding(craft,true),"client remount");}
            if(frame==3){require(mc.player.ticksExisted-firstTapTick<=7,"remount exercises seven-tick boundary");jump(mc,true);}
            if(frame==4)jump(mc,false);
            if(frame==18){expect(mc,false,"dismount/remount clears tap");next();return false;}
        }
        else if(stage==8){
            if(frame==0){jump(mc,false);mc.getIntegratedServer().addScheduledTask(()->player(mc).dismountRidingEntity()).get();mc.player.dismountRidingEntity();}
            if(frame==3){VehicleNetwork.Packet forged=new VehicleNetwork.Packet(VehicleNetwork.FLIGHT);forged.entity=craft.getEntityId();forged.token=id;forged.flag=true;com.vandorlabs.network.PacketHandler.INSTANCE.sendToServer(forged);}
            if(frame==16){expect(mc,false,"non-driver FLIGHT toggle refused");mc.getIntegratedServer().addScheduledTask(()->require(server(mc).board(player(mc)),"board after refusal")).get();next();return false;}
        }
        else if(stage==9){if(frame>=15&&mc.player.getRidingEntity()==craft){next();return false;}}
        else if(stage==10){
            if(frame==0){firstTapTick=mc.player.ticksExisted;jump(mc,true);}
            if(frame==1)jump(mc,false);
            if(frame==2){ // Exercise the real START handler at the world-unavailable lifecycle boundary.
                net.minecraft.client.multiplayer.WorldClient saved=mc.world;mc.world=null;
                try{new VehicleClient().tick(new net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent(net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START));}finally{mc.world=saved;}
            }
            if(frame==3){require(mc.player.ticksExisted-firstTapTick<=7,"world boundary exercises seven-tick interval");jump(mc,true);}
            if(frame==4)jump(mc,false);
            if(frame==18){expect(mc,false,"world-unavailable START clears tap");next();return false;}
        }
        else if(stage==11){
            if(frame==0)jump(mc,true);
            if(frame==1)jump(mc,false);
            if(frame==2){ // Player age corrections cannot complete a pending gesture with negative elapsed ticks.
                int age=mc.player.ticksExisted;mc.player.ticksExisted=0;jump(mc,true);
                try{new VehicleClient().tick(new net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent(net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START));}finally{mc.player.ticksExisted=age;jump(mc,false);}
            }
            if(frame==18){expect(mc,false,"negative player-age interval refused");next();return false;}
        }
        else if(stage>=12&&stage<=14){
            if(frame==0)mc.getIntegratedServer().addScheduledTask(()->{
                EntityGroundVehicle e=server(mc);original=e.structure;List<VehicleStructure.Cell> cells=new ArrayList<>();
                for(VehicleStructure.Cell c:original.cells){String name=c.state.getBlock().getRegistryName().getResourcePath();
                    if(stage==12&&name.equals("repulsor_array")||stage==13&&name.equals("impulse_engine")||stage==14&&c.state.getBlock()==ModBlocks.VEHICLE_CONTROLLER)continue;cells.add(c);}
                e.install(new VehicleStructure(cells,original.seat,original.facing,original.gearCount));
            }).get();
            if(frame==2||frame==6)jump(mc,true);if(frame==4||frame==8)jump(mc,false);
            if(frame==24){expect(mc,false,stage==12?"insufficient lift refuses Hover":stage==13?"missing thruster refuses Hover":"missing controller refuses Hover");mc.getIntegratedServer().addScheduledTask(()->server(mc).install(original)).get();next();return false;}
        }
        else if(stage==15){
            if(frame==0)mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle other=new EntityGroundVehicle(player(mc).getServerWorld());other.install(server(mc).structure);other.setPosition(BASE.getX()+20,BASE.getY(),BASE.getZ());require(other.world.spawnEntity(other),"second vehicle spawn");nextId=other.getUniqueID();}).get();
            if(frame==30){require(find(mc,nextId)!=null&&find(mc,nextId).structure!=null,"second vehicle synchronized");next();return false;}
        }
        else if(stage==16){
            if(frame==0){firstTapTick=mc.player.ticksExisted;jump(mc,true);}
            if(frame==1){jump(mc,false);mc.getIntegratedServer().addScheduledTask(()->{player(mc).dismountRidingEntity();EntityGroundVehicle other=(EntityGroundVehicle)player(mc).getServerWorld().getEntityFromUuid(nextId);require(other.board(player(mc)),"board second vehicle");}).get();mc.player.dismountRidingEntity();require(mc.player.startRiding(find(mc,nextId),true),"client second vehicle");id=nextId;}
            if(frame==3){require(mc.player.ticksExisted-firstTapTick<=7,"craft change within seven ticks");jump(mc,true);}
            if(frame==4)jump(mc,false);
            if(frame==20){expect(mc,false,"changing vehicle clears tap");next();return false;}
        }
        else if(stage==17){
            if(frame==0)jump(mc,true);if(frame==1)jump(mc,false);
            if(frame==2)mc.getIntegratedServer().addScheduledTask(()->{original=server(mc).structure;player(mc).dismountRidingEntity();player(mc).changeDimension(-1,(world,entity,yaw)->entity.setPosition(BASE.getX(),BASE.getY()+2,BASE.getZ()));}).get();
            if(frame>=10&&mc.world.provider.getDimension()==-1&&mc.player.dimension==-1){
                mc.getIntegratedServer().addScheduledTask(()->{WorldServer world=player(mc).getServerWorld();require(world.provider.getDimension()==-1,"server dimension synchronized");for(BlockPos at:BlockPos.getAllInBox(BASE.add(-3,-1,-3),BASE.add(7,5,4)))world.setBlockState(at,at.getY()==100?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);EntityGroundVehicle other=new EntityGroundVehicle(world);other.install(original);other.setPosition(BASE.getX(),BASE.getY(),BASE.getZ());require(world.spawnEntity(other),"new world vehicle spawn");id=other.getUniqueID();player(mc).setPositionAndUpdate(BASE.getX(),BASE.getY()+2,BASE.getZ());require(other.board(player(mc)),"new world real driver");}).get();next();return false;
            }
        }
        else if(stage==18){if(frame>=25&&craft!=null&&craft.structure!=null&&mc.player.getRidingEntity()==craft&&craft.getControllingPassenger()==mc.player){next();return false;}}
        else if(stage==19){
            if(frame==0)jump(mc,true);if(frame==2)jump(mc,false);
            if(frame==18){mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle e=(EntityGroundVehicle)player(mc).getServerWorld().getEntityFromUuid(id);require(e!=null&&!e.hover,"first press in new world stays Taxi server");}).get();require(!craft.hover,"first press in new world stays Taxi client");System.out.println("[vandorlabs][reprolab] taxi-hover-gesture PASS actual dimension/player replacement clears tap");next();return false;}
        }
        else{System.out.println("[vandorlabs][reprolab] utility26-focused PASS taxi-hover-gesture-only");return true;}
        frame++;return false;
    }
}
