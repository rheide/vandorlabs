package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityPilotSeat;
import com.vandorlabs.vehicle.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import java.io.File;
import java.util.*;

/** Exercise actual key bindings and network dispatch across client/server ticks. */
final class HoverClientRuntimeChecks {
    private static UUID id;
    private static int stage,frame,total;
    private static double altitude;
    private static final BlockPos ORIGIN=new BlockPos(184,101,48),SEAT=new BlockPos(1,1,0);
    private static void require(boolean value,String what){if(!value)throw new IllegalStateException("hover client: "+what);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static EntityGroundVehicle server(Minecraft mc){return (EntityGroundVehicle)mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id);}
    private static EntityGroundVehicle client(Minecraft mc){for(EntityGroundVehicle craft:VehicleLookup.loaded(mc.world))if(craft.getUniqueID().equals(id))return craft;return null;}
    private static void key(net.minecraft.client.settings.KeyBinding binding,boolean on){KeyBinding.setKeyBindState(binding.getKeyCode(),on);}
    static void begin(Minecraft mc)throws Exception{
        key(mc.gameSettings.keyBindJump,false);key(mc.gameSettings.keyBindSneak,false);key(mc.gameSettings.keyBindSprint,false);
        mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=player(mc);player.setSneaking(false);player.dismountRidingEntity();
            List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,Blocks.STONE.getDefaultState(),null));
            TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(SEAT);seat.setTaxiMode(false);seat.owner=player.getUniqueID();cells.add(new VehicleStructure.Cell(SEAT,ModBlocks.PILOT_SEAT.getDefaultState(),seat.writeToNBT(new NBTTagCompound())));
            String[] lift={"antigravity_plate","repulsor_array","vertical_hover_thruster"};for(int i=0;i<3;i++)cells.add(new VehicleStructure.Cell(new BlockPos(i,0,1),Block.getBlockFromName("vandorlabs:"+lift[i]).getDefaultState(),null));
            cells.add(new VehicleStructure.Cell(new BlockPos(3,0,0),Block.getBlockFromName("vandorlabs:impulse_engine").getDefaultState(),null));
            EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,SEAT,EnumFacing.NORTH,1));craft.setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());require(world.spawnEntity(craft),"fixture spawn");id=craft.getUniqueID();player.setPositionAndUpdate(ORIGIN.getX()-2,ORIGIN.getY()+2,ORIGIN.getZ());
        }).get();
    }
    private static void next(){stage++;frame=0;}
    private static void tap(Minecraft mc){if(frame==0 || frame==4)key(mc.gameSettings.keyBindJump,true);if(frame==2 || frame==6)key(mc.gameSettings.keyBindJump,false);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        require(++total<650,"timeout stage="+stage+" frame="+frame);EntityGroundVehicle craft=client(mc);
        if(stage==0){if(craft!=null && craft.structure!=null && frame>=25){mc.getIntegratedServer().addScheduledTask(()->require(server(mc).board(player(mc)),"server boards fixture")).get();next();return false;}}
        else if(stage==1){if(frame>=25 && mc.player.getRidingEntity()==craft){next();return false;}}
        else if(stage==2){tap(mc);if(frame>=30){require(craft.hover && mc.player.getRidingEntity()==craft,"double-space enables Hover on client");mc.getIntegratedServer().addScheduledTask(()->require(server(mc).hover,"double-space enables Hover on server")).get();altitude=craft.posY;key(mc.gameSettings.keyBindJump,true);next();return false;}}
        else if(stage==3){if(frame>=20){key(mc.gameSettings.keyBindJump,false);require(craft.posY>altitude+1,"held Space ascends through network");next();return false;}}
        else if(stage==4){if(frame>=15){altitude=craft.posY;key(mc.gameSettings.keyBindSneak,true);next();return false;}}
        else if(stage==5){if(frame>=20){key(mc.gameSettings.keyBindSneak,false);require(mc.player.getRidingEntity()==craft && craft.hover && craft.posY<altitude-1,"Sneak descends without dismounting");mc.getIntegratedServer().addScheduledTask(()->require(player(mc).getRidingEntity()==server(mc),"server preserves Hover rider while descending")).get();next();return false;}}
        else if(stage==6){if(frame>=20){altitude=craft.posY;next();return false;}}
        else if(stage==7){if(frame>=15){require(Math.abs(craft.posY-altitude)<.1,"released vertical controls hold height on client");javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_hover_controls.png"));next();return false;}}
        else if(stage==8){tap(mc);if(frame>=30){require(!craft.hover,"double-space selects Taxi near ground");mc.getIntegratedServer().addScheduledTask(()->require(!server(mc).hover,"server selects Taxi from double-space")).get();next();return false;}}
        else if(stage==9){tap(mc);if(frame>=30){require(craft.hover,"second double-space reenters Hover");key(mc.gameSettings.keyBindSprint,true);key(mc.gameSettings.keyBindSneak,true);next();return false;}}
        else if(stage==10){if(frame>=20){key(mc.gameSettings.keyBindSprint,false);key(mc.gameSettings.keyBindSneak,false);require(mc.player.getRidingEntity()!=craft,"Sprint+Sneak exits Hover on client");mc.getIntegratedServer().addScheduledTask(()->{EntityGroundVehicle entity=server(mc);require(player(mc).getRidingEntity()!=entity && entity.hover && !entity.isDead && !entity.taxiReturnPending,"explicit Hover exit preserves hovering vehicle");entity.setDead();}).get();System.out.println("[vandorlabs][reprolab] hover-client-controls PASS real-double-space Taxi-Hover-Taxi network-ascend descend hold-height crouch-retains-seat sprint-crouch-exit");return true;}}
        frame++;return false;
    }
    private HoverClientRuntimeChecks(){}
}
