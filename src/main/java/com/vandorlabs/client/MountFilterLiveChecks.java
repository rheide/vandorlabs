package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.tiles.TileEntityTokenSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
final class MountFilterLiveChecks {
    private static final BlockPos POS=new BlockPos(300,40,146);private static int ticks;
    static boolean tick(Minecraft mc,java.io.File output)throws Exception{
        int kind=ticks/120,t=++ticks-kind*120;if(kind>=2)return true;boolean chip=kind==1;
        if(t==1)mc.getIntegratedServer().addScheduledTask(()->{
            net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);world.setBlockState(POS.south(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);
            world.setBlockState(POS,net.minecraft.block.Block.getBlockFromName("vandorlabs:"+(chip?"data_chip_slot":"access_card_slot")).getDefaultState().withProperty(BlockVandorSwitch.FACING,EnumFacing.NORTH),3);
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);ItemStack sample=new ItemStack(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD,5,3);sample.setStackDisplayName("Live credential sample");player.inventory.setInventorySlotContents(0,sample);player.connection.sendPacket(new net.minecraft.network.play.server.SPacketSetSlot(-2,0,sample));
        }).get();
        if(t==20)mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_TOKEN_SLOT,player.world,POS.getX(),POS.getY(),POS.getZ());}).get();
        if(t==50){
            if(!(mc.currentScreen instanceof GuiTokenSlot))throw new IllegalStateException("Mount server dialog did not open");GuiTokenSlot gui=(GuiTokenSlot)mc.currentScreen;
            gui.actionPerformed(new net.minecraft.client.gui.GuiButton(101,0,0,"Security"));for(int i=0;i<3;i++)gui.mouseClicked(left(gui)+140,top(gui)+86,0);if(mc.currentScreen!=gui)throw new IllegalStateException("Company selection opened a child dialog");
        }
        if(t==70){
            check(mc,chip,8,0,false);GuiTokenSlot gui=(GuiTokenSlot)mc.currentScreen;
            mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new java.io.File(output,(chip?"chip":"access-card")+"-company-dialog.png"));
            gui.mouseClicked(left(gui)+30,top(gui)+86,0);gui.actionPerformed(new net.minecraft.client.gui.GuiButton(100,0,0,"Redstone"));gui.mouseClicked(left(gui)+30,top(gui)+132,1);
            java.lang.reflect.Field field=GuiTokenSlot.class.getDeclaredField("channels");field.setAccessible(true);GuiTextField channels=(GuiTextField)field.get(gui);channels.setText("64215,64216");channels.setFocused(true);gui.keyTyped(' ',org.lwjgl.input.Keyboard.KEY_SPACE);
        }
        if(t==95){check(mc,chip,127,3,true);mc.player.closeScreen();}
        if(t==120&&chip){System.out.println("[vandorlabs][reprolab] mount-filter-live PASS real server containers both families sample picker packets inventory preservation clear backwards behavior cycle channel lists client synchronization");return true;}return false;
    }
    private static int left(GuiTokenSlot gui){return (gui.width-Math.min(308,gui.width-12))/2+12;}
    private static int top(GuiTokenSlot gui){return (gui.height-160)/2;}
    private static void check(Minecraft mc,boolean chip,int mask,int mode,boolean channels)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);TileEntityTokenSlot tile=(TileEntityTokenSlot)player.world.getTileEntity(POS);
            if(tile.effectiveAllowed()!=mask||tile.behavior!=mode)throw new IllegalStateException("Mount filter/behavior packet not applied");ItemStack held=player.inventory.getStackInSlot(0);if(held.getCount()!=5||held.getItem()!=(chip?ModItems.DATA_CHIP:ModItems.ACCESS_CARD)||!held.getDisplayName().equals("Live credential sample"))throw new IllegalStateException("Mount filter modified inventory");
            if(channels&&!java.util.Arrays.equals(tile.getRedstoneChannels().toArray(),new int[]{64215,64216}))throw new IllegalStateException("Mount channel packet not applied");
            TileEntityTokenSlot saved=new TileEntityTokenSlot(chip);saved.readFromNBT(tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));if(saved.effectiveAllowed()!=mask||saved.behavior!=mode)throw new IllegalStateException("Mount filter persistence");
        }).get();TileEntityTokenSlot tile=(TileEntityTokenSlot)mc.world.getTileEntity(POS);if(tile.effectiveAllowed()!=mask||tile.behavior!=mode)throw new IllegalStateException("Mount filter synchronization");
    }
}
