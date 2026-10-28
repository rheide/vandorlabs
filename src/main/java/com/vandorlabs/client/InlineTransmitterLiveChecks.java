package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockWirelessRedstone;
import com.vandorlabs.container.ContainerWirelessRedstone;
import com.vandorlabs.items.*;
import com.vandorlabs.redstone.*;
import com.vandorlabs.tiles.TileEntityWirelessRedstone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.io.File;
import java.util.List;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Real survival mouse/window packets prove slot ownership and a modal editor on the original window. */
final class InlineTransmitterLiveChecks {
    private static final BlockPos POS=new BlockPos(2020,160,2090),RX=POS.east(8);
    private static final int CHANNEL=65291;
    private static int stage,ticks,total,window,selected;
    private static Container serverWindow;private static GuiWirelessRedstone parent;
    private static NBTTagList inventory;private static ItemStack cursor;private static GameType mode;
    private static Vec3d position;private static float yaw,pitch;private static boolean noGravity,allowFlying,flying;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("inline transmitter survival stage="+stage+": "+message);}
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static WorldServer world(Minecraft mc){return mc.getIntegratedServer().getWorld(0);}
    private static TileEntityWirelessRedstone tx(Minecraft mc){return (TileEntityWirelessRedstone)world(mc).getTileEntity(POS);}
    private static boolean server(Minecraft mc,java.util.concurrent.Callable<Boolean> action)throws Exception{return mc.getIntegratedServer().callFromMainThread(action).get();}
    private static void next(){stage++;ticks=0;}
    private static void owners(Minecraft mc,int expected){EntityPlayerMP actor=actor(mc);int count=0;for(int i=0;i<actor.inventory.getSizeInventory();i++){ItemStack stack=actor.inventory.getStackInSlot(i);if(ItemRedstoneTransmitter.valid(stack))count+=stack.getCount();}ItemStack held=actor.inventory.getItemStack(),installed=TransmitterSlots.get(tx(mc));if(ItemRedstoneTransmitter.valid(held))count+=held.getCount();if(ItemRedstoneTransmitter.valid(installed))count+=installed.getCount();require(count==expected,"module ownership count="+count+" expected="+expected);require(actor.openContainer==serverWindow&&actor.openContainer.windowId==window,"original server container/window retained");require(actor.interactionManager.getGameType()==GameType.SURVIVAL&&actor.inventory.currentItem==0,"authoritative survival mode/selected hotbar retained");}
    private static void click(GuiScreen gui,int x,int y)throws Exception{ReflectionHelper.findMethod(GuiScreen.class,"mouseClicked","func_73864_a",int.class,int.class,int.class).invoke(gui,x,y,0);ReflectionHelper.findMethod(GuiScreen.class,"mouseReleased","func_146286_b",int.class,int.class,int.class).invoke(gui,x,y,0);}
    private static void slot(GuiContainer gui,Slot slot)throws Exception{int left=ReflectionHelper.getPrivateValue(GuiContainer.class,gui,"guiLeft","field_147003_i"),top=ReflectionHelper.getPrivateValue(GuiContainer.class,gui,"guiTop","field_147009_r");click(gui,left+slot.xPos+8,top+slot.yPos+8);}
    private static GuiButton button(GuiScreen gui,String label){List<GuiButton> buttons=ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");for(GuiButton b:buttons)if(b.visible&&b.displayString.equals(label))return b;throw new IllegalStateException("Missing inline button "+label);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        ticks++;require(++total<1200,"timeout mode="+mc.playerController.getCurrentGameType()+" selected="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" hotbar1="+mc.player.inventory.getStackInSlot(1)+" cursor="+mc.player.inventory.getItemStack()+" screen="+(mc.currentScreen==null?"none":mc.currentScreen.getClass().getSimpleName()));
        if(stage==0){
            mc.player.closeScreen();server(mc,()->{EntityPlayerMP p=actor(mc);p.closeScreen();inventory=p.inventory.writeToNBT(new NBTTagList());cursor=p.inventory.getItemStack().copy();mode=p.interactionManager.getGameType();selected=p.inventory.currentItem;position=p.getPositionVector();yaw=p.rotationYaw;pitch=p.rotationPitch;noGravity=p.hasNoGravity();allowFlying=p.capabilities.allowFlying;flying=p.capabilities.isFlying;
                WorldServer world=world(mc);for(BlockPos at:new BlockPos[]{POS,POS.west(),RX}){world.getChunkFromBlockCoords(at);require(world.isAirBlock(at),"isolated fixture cell is empty "+at);}
                BlockWirelessRedstone block=(BlockWirelessRedstone)net.minecraft.block.Block.getBlockFromName("vandorlabs:redstone_transmitter_block"),receiver=(BlockWirelessRedstone)net.minecraft.block.Block.getBlockFromName("vandorlabs:redstone_receiver");world.setBlockState(POS,block.getDefaultState().withProperty(BlockWirelessRedstone.FACING,EnumFacing.WEST),3);world.setBlockState(POS.west(),Blocks.REDSTONE_BLOCK.getDefaultState(),3);world.setBlockState(RX,receiver.getDefaultState().withProperty(BlockWirelessRedstone.FACING,EnumFacing.UP),3);((TileEntityWirelessRedstone)world.getTileEntity(RX)).setRedstoneChannels(ChannelList.of(CHANNEL));
                p.setGameType(GameType.SURVIVAL);p.setSneaking(false);p.setNoGravity(true);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.sendPlayerAbilities();p.inventory.clear();p.inventory.setItemStack(ItemStack.EMPTY);p.inventory.currentItem=0;p.inventory.setInventorySlotContents(0,new ItemStack(ModItems.CONFIGURIZER));ItemStack module=new ItemStack(ModItems.REDSTONE_TRANSMITTER,1,0);ItemRedstoneTransmitter.configure(module,ChannelList.of(CHANNEL));p.inventory.setInventorySlotContents(1,module);p.inventoryContainer.detectAndSendChanges();p.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(0));float look=(float)-Math.toDegrees(Math.atan2(.5-p.getEyeHeight(),2.5));p.connection.setPlayerLocation(POS.getX()+.5,POS.getY(),POS.getZ()-2,0,look);return true;});mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;next();return false;
        }
        if(stage==1){if(ticks<10||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.capabilities.isCreativeMode||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=ModItems.CONFIGURIZER||!ItemRedstoneTransmitter.valid(mc.player.inventory.getStackInSlot(1))||mc.world.getBlockState(POS).getBlock().getRegistryName()==null||!mc.world.getBlockState(POS).getBlock().getRegistryName().getResourcePath().equals("redstone_transmitter_block")||mc.player.getDistanceSq(POS)>16)return false;
            require(server(mc,()->{EntityPlayerMP p=actor(mc);return p.interactionManager.getGameType()==GameType.SURVIVAL&&p.inventory.currentItem==0&&p.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER&&ItemRedstoneTransmitter.valid(p.inventory.getStackInSlot(1));}),"client/server mode, selected slot and held stack synchronize before interaction");mc.objectMouseOver=new RayTraceResult(new Vec3d(POS).addVector(.5,.5,0),EnumFacing.NORTH,POS);ReflectionHelper.findMethod(Minecraft.class,"rightClickMouse","func_147121_ag").invoke(mc);next();return false;
        }
        if(stage==2){if(!(mc.currentScreen instanceof GuiWirelessRedstone)||ticks<4)return false;parent=(GuiWirelessRedstone)mc.currentScreen;InlineTransmitters.Binding binding=InlineTransmitters.binding(parent.inventorySlots);require(binding!=null&&binding.modules.get(0).visible&&binding.hotbar.get(1).visible,"actual module and hotbar slots visible on Redstone tab");require(server(mc,()->{EntityPlayerMP p=actor(mc);require(p.openContainer instanceof ContainerWirelessRedstone,"native wireless configuration container");serverWindow=p.openContainer;window=p.openContainer.windowId;owners(mc,1);return true;}),"original window captured");slot(parent,binding.hotbar.get(1));next();return false;}
        if(stage==3){if(ticks<3||!server(mc,()->ItemRedstoneTransmitter.valid(actor(mc).inventory.getItemStack())&&actor(mc).inventory.getStackInSlot(1).isEmpty()))return false;require(server(mc,()->{owners(mc,1);require(TransmitterSlots.get(tx(mc)).isEmpty(),"pickup has not installed module yet");return true;}),"real hotbar pickup roundtrip");slot(parent,InlineTransmitters.binding(parent.inventorySlots).modules.get(0));next();return false;}
        if(stage==4){if(ticks<4||!server(mc,()->ItemRedstoneTransmitter.valid(TransmitterSlots.get(tx(mc)))&&actor(mc).inventory.getItemStack().isEmpty()))return false;server(mc,()->{owners(mc,1);require(((TileEntityWirelessRedstone)world(mc).getTileEntity(RX)).getOutputLevel()==15,"survival insertion enables actual in-range native receiver");return true;});VehicleTaxiRuntimeChecks.screenshot(mc,output,"shot_inline_transmitter_survival.png");GuiButton edit=button(parent,"Channels...");require(edit.enabled,"inserted module has channel editor");click(parent,edit.x+edit.width/2,edit.y+edit.height/2);require(mc.currentScreen instanceof GuiHostedTransmitterEditor,"inline channel editor opens as modal");next();return false;}
        if(stage==5){if(ticks<8)return false;server(mc,()->{owners(mc,1);return true;});GuiHostedTransmitterEditor editor=(GuiHostedTransmitterEditor)mc.currentScreen;GuiTextField field=ReflectionHelper.getPrivateValue(GuiHostedTransmitterEditor.class,editor,"channels");field.setText(Integer.toString(CHANNEL+1));GuiButton done=button(editor,"Done");click(editor,done.x+done.width/2,done.y+done.height/2);require(mc.currentScreen==parent&&mc.player.openContainer==parent.inventorySlots,"modal returns to exact original client dialog/window");next();return false;}
        if(stage==6){if(ticks<5||!server(mc,()->ItemRedstoneTransmitter.channels(TransmitterSlots.get(tx(mc))).contains(CHANNEL+1)))return false;server(mc,()->{owners(mc,1);require(((TileEntityWirelessRedstone)world(mc).getTileEntity(RX)).getOutputLevel()==0,"editing item channels withdraws old channel");return true;});slot(parent,InlineTransmitters.binding(parent.inventorySlots).modules.get(0));next();return false;}
        if(stage==7){if(ticks<4||!server(mc,()->TransmitterSlots.get(tx(mc)).isEmpty()&&ItemRedstoneTransmitter.channels(actor(mc).inventory.getItemStack()).contains(CHANNEL+1)))return false;server(mc,()->{owners(mc,1);return true;});slot(parent,InlineTransmitters.binding(parent.inventorySlots).hotbar.get(1));next();return false;}
        if(stage==8){if(ticks<5||!server(mc,()->actor(mc).inventory.getItemStack().isEmpty()&&ItemRedstoneTransmitter.channels(actor(mc).inventory.getStackInSlot(1)).contains(CHANNEL+1)))return false;server(mc,()->{owners(mc,1);require(TransmitterSlots.get(tx(mc)).isEmpty(),"native source empty after removal");return true;});mc.player.closeScreen();server(mc,()->{EntityPlayerMP p=actor(mc);p.closeScreen();for(BlockPos at:new BlockPos[]{POS,POS.west(),RX})world(mc).setBlockToAir(at);p.inventory.readFromNBT(inventory);p.inventory.setItemStack(cursor);p.inventory.currentItem=selected;p.setGameType(mode);p.setNoGravity(noGravity);p.capabilities.allowFlying=allowFlying;p.capabilities.isFlying=flying;p.sendPlayerAbilities();p.inventoryContainer.detectAndSendChanges();p.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(selected));p.connection.setPlayerLocation(position.x,position.y,position.z,yaw,pitch);return true;});mc.playerController.setGameType(mode);System.out.println("[vandorlabs][reprolab] inline-transmitter-survival PASS native-right-click synchronized-survival-hotbar real-mouse-window-packets insert modal-edit original-window-unchanged receiver-update remove exact-one-owner");return true;}
        return false;
    }
}
