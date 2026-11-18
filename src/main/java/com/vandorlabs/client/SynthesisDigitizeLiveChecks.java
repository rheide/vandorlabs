package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.machines.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.rack.ModuleBatteryData;
import com.vandorlabs.container.ContainerSynthesisTower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameType;
import java.io.File;
import java.awt.image.BufferedImage;

/** Real window mode actions and held-block enclosure placement in Survival. */
final class SynthesisDigitizeLiveChecks {
    private static final BlockPos AT=new BlockPos(180,220,180);private static int stage,ticks,checks;private static Vec3d eye,target;private static NBTTagCompound partial;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new IllegalStateException("digitize-client stage="+stage+": "+why);}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static TileEntityConstructor tile(net.minecraft.world.World world){return (TileEntityConstructor)world.getTileEntity(AT);}
    private static void server(Minecraft mc,Runnable action)throws Exception{mc.getIntegratedServer().addScheduledTask(action).get();}
    private static GuiButton button(GuiSynthesisTower gui,int id){java.util.List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");for(GuiButton b:buttons)if(b.id==id)return b;throw new IllegalStateException("Missing button "+id);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(++ticks>700)throw new IllegalStateException("digitize client timeout stage="+stage+" mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand());
        if(stage==0){mc.player.closeScreen();mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;mc.getConnection().sendPacket(new net.minecraft.network.play.client.CPacketHeldItemChange(0));server(mc,()->{EntityPlayerMP p=player(mc);p.closeScreen();p.setGameType(GameType.SURVIVAL);p.capabilities.allowFlying=true;p.capabilities.isFlying=true;p.fallDistance=0;p.setHealth(p.getMaxHealth());p.sendPlayerAbilities();p.inventory.currentItem=0;p.inventory.clear();TileEntityConstructor t=tile(p.world);NBTTagCompound clean=t.writeToNBT(new NBTTagCompound());clean.removeTag("Synthesis");clean.removeTag("InsertedToken");t.readFromNBT(clean);t.refreshInputs();t.configureSynthesisRedstone(1,com.vandorlabs.redstone.ChannelList.EMPTY);p.setHeldItem(EnumHand.MAIN_HAND,ItemStack.EMPTY);eye=new Vec3d(180.5,220.5,177.3);p.connection.setPlayerLocation(eye.x,eye.y-p.getEyeHeight(),eye.z,0,0);p.inventoryContainer.detectAndSendChanges();p.world.notifyBlockUpdate(AT,p.world.getBlockState(AT),p.world.getBlockState(AT),2);});stage=1;ticks=0;return false;}
        if(stage==1){if(ticks<20||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||!mc.player.getHeldItemMainhand().isEmpty()||mc.player.getPositionEyes(1).squareDistanceTo(eye)>.025||!mc.player.capabilities.isFlying)return false;mc.playerController.processRightClickBlock(mc.player,mc.world,AT,EnumFacing.NORTH,new Vec3d(AT).addVector(.5,.5,0),EnumHand.MAIN_HAND);stage=2;ticks=0;return false;}
        if(stage==2){if(ticks<15||!(mc.currentScreen instanceof GuiSynthesisTower))return false;GuiSynthesisTower gui=(GuiSynthesisTower)mc.currentScreen;need(gui.controlsFit(),"standard production layout fits");GuiButton mode=button(gui,102);gui.mouseClicked(mode.x+4,mode.y+4,0);stage=3;ticks=0;return false;}
        if(stage==3){if(ticks<15||((ContainerSynthesisTower)mc.player.openContainer).values[6]!=1)return false;server(mc,()->need(tile(player(mc).world).digitizing(),"real mode packet authorizes Digitize"));GuiSynthesisTower gui=(GuiSynthesisTower)mc.currentScreen;need(gui.inventorySlots.inventorySlots.get(0).xPos==18&&gui.inventorySlots.inventorySlots.get(3).xPos==108&&gui.inventorySlots.inventorySlots.get(1).xPos<0,"Input arrow Chip and hidden output");GuiButton redstone=button(gui,101);gui.mouseClicked(redstone.x+4,redstone.y+4,0);need(gui.inventorySlots.inventorySlots.stream().allMatch(s->s.xPos<0),"Redstone tab hides every machine and player inventory slot");SynthesisTowerLiveChecks.captureGui(mc,gui,output,"synthesis-digitize-redstone.png");GuiButton production=button(gui,100);gui.mouseClicked(production.x+4,production.y+4,0);SynthesisTowerLiveChecks.captureGui(mc,gui,output,"synthesis-digitize-dialog.png");mc.player.closeScreen();server(mc,()->{EntityPlayerMP p=player(mc);TileEntityConstructor t=tile(p.world);t.setInventorySlotContents(0,new ItemStack(ModItems.DATA_CHIP,1,2));p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Blocks.GLASS,2));BuilderOutputBounds bounds=BuilderOutputBounds.forMachine("synthesis_tower");target=new Vec3d(AT).addVector(bounds.centerX(),bounds.centerY(),0);eye=target.addVector(0,0,-2.7);p.connection.setPlayerLocation(eye.x,eye.y-p.getEyeHeight(),eye.z,0,0);p.inventoryContainer.detectAndSendChanges();});stage=4;ticks=0;return false;}
        if(stage==4){if(ticks<20||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getCount()!=2||mc.player.getPositionEyes(1).squareDistanceTo(eye)>.025)return false;BlockPos hit=new BlockPos(target);need(mc.playerController.processRightClickBlock(mc.player,mc.world,hit,EnumFacing.NORTH,target,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"enclosure accepts real held-block packet");stage=5;ticks=0;return false;}
        if(stage==5){if(ticks<15)return false;server(mc,()->{EntityPlayerMP p=player(mc);TileEntityConstructor t=tile(p.world);need(t.synthesisInventory().getStackInSlot(0).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.GLASS)&&t.synthesisInventory().getStackInSlot(0).getCount()==1&&p.getHeldItemMainhand().getCount()==1,"held block transfers exactly once into enclosure");ItemStack battery=new ItemStack(ModItems.BATTERY_MODULE);ModuleBatteryData.receive(battery,10000,false);t.synthesisInventory().setStackInSlot(2,battery);t.configureSynthesisRedstone(0,com.vandorlabs.redstone.ChannelList.EMPTY);});stage=6;ticks=0;return false;}
        if(stage==6){if(ticks<40||!tile(mc.world).processing())return false;TileEntityConstructor client=tile(mc.world);partial=client.getUpdateTag();renderReverse(mc,client,output);stage=7;ticks=0;return false;}
        if(stage==7){boolean done=mc.getIntegratedServer().callFromMainThread(()->{TileEntityConstructor t=tile(player(mc).world);return !t.processing()&&t.synthesisInventory().getStackInSlot(0).isEmpty()&&!BlockRecipeData.read(t.getStackInSlot(0)).isEmpty();}).get();if(!done)return false;server(mc,()->{TileEntityConstructor t=tile(player(mc).world);need(BlockRecipeData.read(t.getStackInSlot(0)).getItem()==net.minecraft.item.Item.getItemFromBlock(Blocks.GLASS),"actual powered scan creates glass Catalog");need(t.getStackInSlot(0).getMetadata()==2&&t.batteryEnergy()==2000,"company retained and scan spends exactly 8000RF");});renderFinishedBlock(mc,tile(mc.world),output);System.out.println("[vandorlabs] synthesis-digitize-client PASS checks="+checks+" realMode/window/tabVisibility/enclosureSurvivalTransfer reversePixels 8000RF exactCatalog");return true;}
        return false;
    }
    private static void renderFinishedBlock(Minecraft mc,TileEntityConstructor tile,File output)throws Exception{
        NBTTagCompound original=tile.getUpdateTag();
        try{
            NBTTagCompound finished=original.copy();
            NBTTagCompound work=finished.getCompoundTag("Synthesis");
            work.removeTag("Mode");work.removeTag("WorkCost");work.setBoolean("Active",false);work.setInteger("Work",0);
            work.setString("BuildingRecipe",BlockRecipeData.ID);
            tile.handleUpdateTag(finished);
            BufferedImage empty=SynthesisTowerLiveChecks.tower(mc,tile,20,155);
            tile.synthesisInventory().setStackInSlot(1,new ItemStack(Blocks.STONE));
            BufferedImage first=SynthesisTowerLiveChecks.tower(mc,tile,20,155);
            BufferedImage later=SynthesisTowerLiveChecks.tower(mc,tile,53,155);
            int visible=0;
            for(int y=119;y<205;y++)for(int x=173;x<232;x++){
                need(first.getRGB(x,y)==later.getRGB(x,y),"finished block remains still across render times");
                if(first.getRGB(x,y)!=empty.getRGB(x,y))visible++;
            }
            need(visible>300,"finished placed block appears in chamber: "+visible);
            javax.imageio.ImageIO.write(first,"png",new File(output,"synthesis-finished-vanilla.png"));
        }finally{tile.handleUpdateTag(original);}
    }

    private static void renderReverse(Minecraft mc,TileEntityConstructor tile,File output)throws Exception{
        NBTTagCompound original=tile.getUpdateTag();try{NBTTagCompound early=original.copy();early.getCompoundTag("Synthesis").setInteger("Work",2000);early.setBoolean("SynthesisWorking",false);tile.handleUpdateTag(early);BufferedImage a=SynthesisTowerLiveChecks.tower(mc,tile,20,155);NBTTagCompound late=early.copy();late.getCompoundTag("Synthesis").setInteger("Work",6000);tile.handleUpdateTag(late);BufferedImage b=SynthesisTowerLiveChecks.tower(mc,tile,20,155);int changed=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;need(changed>25,"actual emitted reverse scan visibly removes material: "+changed);javax.imageio.ImageIO.write(a,"png",new File(output,"synthesis-digitize-early.png"));javax.imageio.ImageIO.write(b,"png",new File(output,"synthesis-digitize-late.png"));}finally{tile.handleUpdateTag(original);}
    }
}
