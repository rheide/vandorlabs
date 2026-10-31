package com.vandorlabs.client;

import com.vandorlabs.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.network.*;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;

/** Actual dialog clicks, server round trips, saved playback and matching input geometry. */
final class GlazingFeatureChecks {
    private static final String[] BLOCKS={"programmable_input","programmable_half_input","programmable_glass","programmable_porthole_block","programmable_diagonal_porthole","programmable_diagonal_glass"};
    private static final BlockPos POS=new BlockPos(9,121,9);
    private static int ticks,phase,fixture;
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static List<GuiButton> buttons(GuiScreen gui){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");}
    private static GuiButton button(GuiScreen gui,int id){for(GuiButton b:buttons(gui))if(b.id==id&&b.visible)return b;throw new IllegalStateException("Missing dialog button "+id+" fixture="+fixture);}
    private static void click(GuiScreen gui,int id,int mouse)throws Exception{GuiButton b=button(gui,id);if(gui instanceof GuiAnimatedScreenSelector)((GuiAnimatedScreenSelector)gui).mouseClicked(b.x+4,b.y+4,mouse);else if(gui instanceof GuiProgrammableInput)((GuiProgrammableInput)gui).mouseClicked(b.x+4,b.y+4,mouse);else if(gui instanceof GuiProgrammableGlass)((GuiProgrammableGlass)gui).mouseClicked(b.x+4,b.y+4,mouse);else ((GuiProgrammableWall)gui).mouseClicked(b.x+4,b.y+4,mouse);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;require(ticks<1200,"fixture timeout="+fixture+" stage="+phase+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPosition());if(++ticks%12!=0)return false;
        if(phase==0){
            mc.player.closeScreen();mc.playerController.setGameType(GameType.CREATIVE);
            mc.getIntegratedServer().addScheduledTask(()->{
                EntityPlayerMP p=player(mc);p.interactionManager.setGameType(GameType.CREATIVE);p.inventory.currentItem=0;p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));p.capabilities.isFlying=true;p.sendPlayerAbilities();
                Block block=Block.getBlockFromName("vandorlabs:"+BLOCKS[fixture]);require(block!=null,"registered block");p.world.setBlockState(POS,block.getDefaultState(),3);
                TileEntity tile=p.world.getTileEntity(POS);if(fixture>=2){int material=ScreenHousingTextures.screenIndex("cool27_full_bridge_medical_static");if(tile instanceof TileEntityProgrammableGlass)((TileEntityProgrammableGlass)tile).setMaterial(material);else ((TileEntityAnimatedScreenSelector)tile).setGlassMaterial(material);p.world.notifyBlockUpdate(POS,block.getDefaultState(),block.getDefaultState(),3);}
                p.connection.setPlayerLocation(POS.getX()+.5,POS.getY()+1,POS.getZ()+3,180,0);p.inventoryContainer.detectAndSendChanges();
            }).get();phase++;return false;
        }
        if(phase==1){
            if(!mc.player.isCreative()||mc.player.inventory.currentItem!=0||mc.player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER||mc.world.getBlockState(POS).getBlock()!=Block.getBlockFromName("vandorlabs:"+BLOCKS[fixture]))return false;
            mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=player(mc);p.openGui(VandorLabs.instance,fixture==2?GuiHandler.GUI_PROGRAMMABLE_GLASS:GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,p.world,POS.getX(),POS.getY(),POS.getZ());}).get();phase++;return false;
        }
        GuiScreen gui=mc.currentScreen;require(gui!=null,"open dialog stage="+phase);
        if(phase==2){if(fixture>=2)click(gui,fixture==2?200:214,0);phase++;return false;}
        if(phase==3){
            capture(mc,gui,output,"before");click(gui,fixture<2?30:702,0);if(fixture>=2)click(gui,710,0);phase++;return false;
        }
        if(phase==4){
            verify(mc,true);capture(mc,gui,output,"after");
            if(fixture<2)click(gui,30,1);else{click(gui,701,0);click(gui,711,0);}phase++;return false;
        }
        verify(mc,false);mc.player.closeScreen();if(++fixture==BLOCKS.length){geometry();packets();System.out.println("[vandorlabs][reprolab] glazing-features PASS six-dialogs real-clicks server-roundtrip save-reload Small-Full left-right playback-mode-speed geometry packets");return true;}phase=0;return false;
    }
    private static void verify(Minecraft mc,boolean changed)throws Exception{
        mc.getIntegratedServer().addScheduledTask(()->{
            TileEntity tile=player(mc).world.getTileEntity(POS);NBTTagCompound tag=tile.writeToNBT(new NBTTagCompound());
            if(fixture<2){TileEntityAnimatedScreenSelector loaded=new TileEntityAnimatedScreenSelector();loaded.readFromNBT(tag);require(((TileEntityAnimatedScreenSelector)tile).isSmallInput()==changed&&loaded.isSmallInput()==changed,"saved size fixture="+fixture);}
            else{GlassPlayback playback=GlassPlayback.of(tile);require(playback.mode()==(changed?2:1)&&playback.speed()==(changed?0:1),"server playback fixture="+fixture);GlassPlayback loaded=new GlassPlayback(new TileEntityProgrammableGlass());loaded.read(tag);require(loaded.same(playback),"saved playback");}
        }).get();
    }
    private static void capture(Minecraft mc,GuiScreen gui,File output,String stage)throws Exception{
        List<GuiButton> buttons=buttons(gui);for(int i=0;i<buttons.size();i++){GuiButton a=buttons.get(i);if(!a.visible)continue;for(int j=i+1;j<buttons.size();j++){GuiButton b=buttons.get(j);if(b.visible)require(a.x+a.width<=b.x||b.x+b.width<=a.x||a.y+a.height<=b.y||b.y+b.height<=a.y,"overlapping buttons "+a.id+"/"+b.id+" "+BLOCKS[fixture]);}}
        mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);net.minecraft.client.renderer.GlStateManager.disableDepth();mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"glass-display-order_dialog_"+BLOCKS[fixture]+"_"+stage+".png"));
    }
    private static void geometry(){
        for(boolean keyboard:new boolean[]{false,true})for(boolean upper:new boolean[]{false,true}){com.vandorlabs.render.InputSurfaceLayout.Mounted m=com.vandorlabs.render.InputSurfaceLayout.fullInput(keyboard,upper,true);require(m.housing.x1-m.housing.x0==8&&(keyboard?m.housing.z1-m.housing.z0:m.housing.y1-m.housing.y0)==8,"small Flatscreen half-block square");}
        for(int position=0;position<3;position++){com.vandorlabs.render.InputSurfaceLayout.Box b=com.vandorlabs.render.InputSurfaceLayout.ceilingInput(true,true,position).housing;require(b.x1-b.x0==8&&b.z1-b.z0==8,"ceiling small size");}
    }
    private static void packets(){
        for(boolean wall:new boolean[]{false,true}){
            net.minecraftforge.fml.common.network.simpleimpl.IMessage packet=wall?new MessageProgrammableWallShade(POS,0,true,0).withAppearance(1,true).withPlayback(2,0):new MessageProgrammableGlass(POS,0,0,true).withPlayback(2,0);io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();packet.toBytes(bytes);int size=bytes.readableBytes();require(size==(wall?29:28),"bounded packet byte count "+size);net.minecraftforge.fml.common.network.simpleimpl.IMessage restored=wall?new MessageProgrammableWallShade():new MessageProgrammableGlass();restored.fromBytes(bytes);io.netty.buffer.ByteBuf round=io.netty.buffer.Unpooled.buffer();restored.toBytes(round);require(round.readableBytes()==size,"packet roundtrip");bytes.release();round.release();
        }
        System.out.println("[vandorlabs][reprolab] glass-playback-network PASS regular=25->28 porthole=26->29 bytes");
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("Glazing feature: "+message);}
}
