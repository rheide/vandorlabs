package com.vandorlabs.client;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
public final class StairDialogLiveChecks {
    private static final BlockPos POS=new BlockPos(300,40,146);private static int ticks;private static boolean captured;
    @SuppressWarnings("unchecked") private static java.util.List<GuiButton> buttons(GuiProgrammableWall gui){try{java.lang.reflect.Field field=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.gui.GuiScreen.class,"buttonList","field_146292_n");return (java.util.List<GuiButton>)field.get(gui);}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}}
    private static GuiButton button(GuiProgrammableWall gui,int id){return buttons(gui).stream().filter(b->b.id==id).findFirst().orElseThrow(()->new IllegalStateException("Missing stair control "+id));}
    static boolean tick(Minecraft mc,java.io.File output)throws Exception{
        if(++ticks==1)mc.getIntegratedServer().addScheduledTask(()->{
            net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);world.setBlockState(POS,ModBlocks.PROGRAMMABLE_STAIRS.getDefaultState(),2);
            EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);player.interactionManager.setGameType(net.minecraft.world.GameType.CREATIVE);player.capabilities.isFlying=true;player.sendPlayerAbilities();player.setPositionAndUpdate(POS.getX()+.5,POS.getY()+1,POS.getZ()+2);

        }).get();
        if(ticks==20)mc.getIntegratedServer().addScheduledTask(()->{
            net.minecraft.world.WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            player.connection.sendPacket(new net.minecraft.network.play.server.SPacketBlockChange(world,POS));
            player.connection.sendPacket(world.getTileEntity(POS).getUpdatePacket());
            player.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,world,POS.getX(),POS.getY(),POS.getZ());
        }).get();
        if(ticks==50){
            if(!(mc.currentScreen instanceof GuiProgrammableWall))throw new IllegalStateException("Stair dialog did not open through server GUI handler: "+mc.currentScreen+" block="+mc.world.getBlockState(POS));
            GuiProgrammableWall gui=(GuiProgrammableWall)mc.currentScreen;GuiButton slider=button(gui,130);gui.mouseClicked(slider.x+4,slider.y+10,0);gui.mouseReleased(slider.x+4,slider.y+10,0);gui.actionPerformed(button(gui,131));
        }
        if(ticks==70){
            check(mc,1,1);GuiProgrammableWall gui=(GuiProgrammableWall)mc.currentScreen;
            if("true".equals(System.getenv("VANDOR_LABS_TEXTURE_SEARCH_CHECKS")))TextureSearchLiveChecks.type(gui);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Capture(output));
        }
        if(ticks==72){
            if(!captured)throw new IllegalStateException("Stair dialog normal rendered frame was not captured");
            GuiProgrammableWall gui=(GuiProgrammableWall)mc.currentScreen;if("true".equals(System.getenv("VANDOR_LABS_TEXTURE_SEARCH_CHECKS")))TextureSearchLiveChecks.clear(gui);GuiButton orientation=button(gui,131);gui.mouseClicked(orientation.x+4,orientation.y+8,1);
        }
        if(ticks==90){check(mc,1,0);GuiProgrammableWall gui=(GuiProgrammableWall)mc.currentScreen;GuiButton slider=button(gui,130);gui.mouseClicked(slider.x+slider.width-4,slider.y+10,0);gui.mouseReleased(slider.x+slider.width-4,slider.y+10,0);}
        if(ticks==110){check(mc,8,0);mc.player.closeScreen();System.out.println("[vandorlabs][reprolab] stair-dialog-live PASS real server container slider packet left/right orientation updates synchronized geometry");return true;}return ticks>110;
    }
    public static final class Capture {
        private final java.io.File output;
        Capture(java.io.File output){this.output=output;}
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void rendered(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event){
            if(event.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END)return;
            Minecraft mc=Minecraft.getMinecraft();if(!(mc.currentScreen instanceof GuiProgrammableWall))return;
            try{java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());javax.imageio.ImageIO.write(image,"png",new java.io.File(output,"stair-size-sideways-dialog.png"));if("true".equals(System.getenv("VANDOR_LABS_TEXTURE_SEARCH_CHECKS")))javax.imageio.ImageIO.write(image,"png",new java.io.File(output,"texture-search-vand.png"));captured=true;}
            catch(java.io.IOException failure){throw new IllegalStateException("Stair dialog rendered capture",failure);}
            finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(this);}
        }
    }
    private static void check(Minecraft mc,int tread,int roll)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.getIntegratedServer().getWorld(0).getTileEntity(POS);
            if(tile.stairTread()!=tread||tile.stairRoll()!=roll)throw new IllegalStateException("Stair GUI packet did not apply on server");
        }).get();
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS);if(tile.stairTread()!=tread||tile.stairRoll()!=roll)throw new IllegalStateException("Stair settings not synchronized to client");
    }
}
