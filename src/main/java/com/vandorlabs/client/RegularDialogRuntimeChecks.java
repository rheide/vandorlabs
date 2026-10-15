package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

/** Actual block-use packets and GUI saves through creative/survival entry paths. */
public final class RegularDialogRuntimeChecks {
    private static final BlockPos POS=new BlockPos(144,100,144);
    private static int scenario,stage,frame,total;
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("regular-dialog: "+text+" scenario="+scenario+" stage="+stage);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static Block block(){switch(scenario%4){case 0:return ModBlocks.VEHICLE_CONTROLLER;case 1:return ModBlocks.PROGRAMMABLE_RAMP;case 2:return ModBlocks.WAYPOINT;default:return ModBlocks.AUTOPILOT;}}
    private static boolean survival(){return (scenario/4&1)!=0;}
    private static boolean tool(){return scenario>=8;}
    public static void begin(Minecraft mc)throws Exception{scenario=stage=frame=total=0;prepare(mc);}
    private static void prepare(Minecraft mc)throws Exception{
        mc.player.closeScreen();mc.displayGuiScreen(null);
        mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=player(mc);p.dismountRidingEntity();p.setSneaking(false);p.setGameType(survival()?GameType.SURVIVAL:GameType.CREATIVE);
            p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.setHeldItem(EnumHand.MAIN_HAND,tool()?new ItemStack(ModItems.CONFIGURIZER):ItemStack.EMPTY);p.setHeldItem(EnumHand.OFF_HAND,ItemStack.EMPTY);p.inventoryContainer.detectAndSendChanges();
            WorldServer w=world(mc);for(BlockPos at:BlockPos.getAllInBox(POS.add(-4,-2,-4),POS.add(4,4,4)))w.setBlockToAir(at);
            for(BlockPos at:BlockPos.getAllInBox(POS.add(-4,-1,-4),POS.add(4,-1,4)))w.setBlockState(at,Blocks.STONE.getDefaultState(),3);
            w.setBlockState(POS,block().getDefaultState(),3);
            if(scenario%4==0){BlockPos seat=POS.east();w.setBlockState(seat,ModBlocks.PILOT_SEAT.getDefaultState(),3);ModBlocks.PILOT_SEAT.onBlockPlacedBy(w,seat,w.getBlockState(seat),p,new ItemStack(ModBlocks.PILOT_SEAT));}
            if(scenario%4==1)((TileEntityRampController)w.getTileEntity(POS)).setOwner(p);
            double dy=.5-p.getEyeHeight();float pitch=(float)-Math.toDegrees(Math.atan2(dy,2.5));p.connection.setPlayerLocation(POS.getX()+.5,POS.getY(),POS.getZ()-2,0,pitch);
            require(ConfigurationAccess.canEditDialog(p,w,POS),"ordinary permitted block remains editable without creative requirement");
            double x=p.posX,y=p.posY,z=p.posZ;p.setPosition(x+20,y,z);require(!ConfigurationAccess.canEditDialog(p,w,POS),"out-of-reach dialog rejected");p.setPosition(x,y,z);
            if(scenario==0){p.setGameType(GameType.SPECTATOR);require(!ConfigurationAccess.canEditDialog(p,w,POS),"spectator dialog rejected");p.setGameType(GameType.ADVENTURE);require(!ConfigurationAccess.canEditDialog(p,w,POS),"uneditable adventure block rejected");p.setGameType(GameType.CREATIVE);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();}
        }).get();
        stage=0;frame=0;
    }
    private static boolean dialog(Minecraft mc){return scenario%4==0?mc.currentScreen!=null&&mc.currentScreen.getClass().getName().endsWith("VehicleClient$Preview"):scenario%4==1?mc.currentScreen instanceof GuiRampController:mc.currentScreen instanceof GuiAutopilot;}
    private static void mouse(Minecraft mc,int id)throws Exception{
        GuiScreen gui=mc.currentScreen;List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");
        GuiButton selected=null;for(GuiButton b:buttons)if(b.id==id)selected=b;require(selected!=null&&selected.enabled,"editable GUI button "+id);
        Method click;try{click=GuiScreen.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);}catch(NoSuchMethodException e){click=GuiScreen.class.getDeclaredMethod("func_73864_a",int.class,int.class,int.class);}click.setAccessible(true);click.invoke(gui,selected.x+selected.width/2,selected.y+selected.height/2,0);
    }
    public static boolean tick(Minecraft mc,File output)throws Exception{
        require(++total<1000,"bounded actual block-use timeout; current="+(mc.currentScreen==null?"none":mc.currentScreen.getClass().getSimpleName()));frame++;
        if(stage==0){
            if(frame<12||mc.world.getBlockState(POS).getBlock()!=block()||mc.player.isSpectator()||mc.player.capabilities.isCreativeMode==survival()||(mc.player.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER)!=tool())return false;
            require(!mc.player.isSneaking(),"regular right-click has no Shift");
            Vec3d hit=new Vec3d(POS).addVector(.5,.5,0);mc.objectMouseOver=new RayTraceResult(hit,EnumFacing.NORTH,POS);
            Method click;try{click=Minecraft.class.getDeclaredMethod("rightClickMouse");}catch(NoSuchMethodException e){click=Minecraft.class.getDeclaredMethod("func_147121_ag");}click.setAccessible(true);click.invoke(mc);stage=1;frame=0;return false;
        }
        if(stage==1){require(frame<100,"correct dialog did not open; current="+(mc.currentScreen==null?"none":mc.currentScreen.getClass().getSimpleName()));if(!dialog(mc)||frame<6)return false;
            VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_regular_dialog_"+scenario+".png");
            if(scenario==1){RampModeRuntimeChecks.begin();stage=3;frame=0;return false;}
            if(scenario%4==1)mouse(mc,18);else if(scenario%4==2){mouse(mc,3);mouse(mc,1);}else if(scenario%4==3){mouse(mc,0);mouse(mc,1);}else mc.player.closeScreen();
            stage=2;frame=0;return false;
        }
        if(stage==3){if(!RampModeRuntimeChecks.tick(mc,POS,output))return false;mouse(mc,18);stage=2;frame=0;return false;}
        if(stage==2&&frame>=10){
            mc.getIntegratedServer().addScheduledTask(()->{
                net.minecraft.tileentity.TileEntity tile=world(mc).getTileEntity(POS);
                if(scenario%4==1)require(tile instanceof TileEntityRampController&&!((TileEntityRampController)tile).matchTextures,"real ramp button packet saves without tool");
                else if(scenario%4==2)require(tile instanceof TileEntityWaypoint&&!((TileEntityWaypoint)tile).autoDepart,"real waypoint Done packet saves without tool");
                else if(scenario%4==3)require(tile instanceof TileEntityAutopilot&&((TileEntityAutopilot)tile).trigger==2,"real autopilot Done packet saves without tool");
            }).get();
            if(++scenario==16){mc.player.closeScreen();mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);p.setGameType(GameType.CREATIVE);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();}).get();System.out.println("[vandorlabs][reprolab] regular-dialog-client PASS four-blocks actual-right-click no-shift creative survival empty-hand Configurizer full-dialog-parity GUI-mouse-packet-saves reach-edit-spectator-protection");return true;}
            prepare(mc);
        }
        return false;
    }
}
