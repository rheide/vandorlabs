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
    private static final BlockPos BASE=new BlockPos(184,101,48),SEAT=new BlockPos(1,1,0);
    private static void require(boolean yes,String text){if(!yes)throw new IllegalStateException("Taxi/Hover gesture stage="+stage+" frame="+frame+": "+text);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle server(Minecraft mc){return (EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id);}
    private static EntityGroundVehicle client(Minecraft mc){for(EntityGroundVehicle e:VehicleLookup.loaded(mc.world))if(e.getUniqueID().equals(id))return e;return null;}
    private static void jump(Minecraft mc,boolean down){KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),down);}
    private static void next(){stage++;frame=0;}
    private static void expect(Minecraft mc,boolean hover,String label)throws Exception{mc.getIntegratedServer().addScheduledTask(()->require(server(mc).hover==hover,label+" server")).get();require(client(mc).hover==hover,label+" client");System.out.println("[vandorlabs][reprolab] taxi-hover-gesture PASS "+label);}
    static boolean tick(Minecraft mc)throws Exception{
        require(++total<500,"timeout riding="+mc.player.getRidingEntity()+" screen="+mc.currentScreen);
        if(stage==0){
            jump(mc,false);mc.getIntegratedServer().addScheduledTask(()->{
                WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP p=player(mc);p.dismountRidingEntity();p.setSneaking(false);
                for(BlockPos at:BlockPos.getAllInBox(BASE.add(-4,-1,-4),BASE.add(8,8,5)))world.setBlockState(at,at.getY()==100?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
                List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null));cells.add(new VehicleStructure.Cell(SEAT,ModBlocks.PILOT_SEAT.getDefaultState(),null));cells.add(new VehicleStructure.Cell(new BlockPos(4,0,0),ModBlocks.VEHICLE_CONTROLLER.getDefaultState(),null));
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
        else{System.out.println("[vandorlabs][reprolab] utility26-focused PASS taxi-hover-gesture-only");return true;}
        frame++;return false;
    }
}
