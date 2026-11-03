package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.client.config.GuiSlider;
import java.io.File;

/** Small actual dialog/packet focus for chair settings and carried Track visuals. */
final class FurnitureLiveChecks {
    private static int stage,ticks;
    private static final BlockPos POS=new BlockPos(28,90,28);
    private static EntityPlayerMP actor(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayers().get(0);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("furniture stage="+stage+": "+why);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        mc.gameSettings.pauseOnLostFocus=false;if(++ticks<30)return false;
        require(ticks<300,"dialog timeout mode="+mc.playerController.getCurrentGameType()+" held="+mc.player.getHeldItemMainhand()+" screen="+mc.currentScreen);
        if(stage==0){mc.displayGuiScreen(null);mc.playerController.setGameType(GameType.CREATIVE);mc.getIntegratedServer().addScheduledTask(()->{
            EntityPlayerMP p=actor(mc);p.setGameType(GameType.CREATIVE);p.setSneaking(false);p.dismountRidingEntity();ChairRuntimeChecks.run(p.world,p);
            for(BlockPos at:BlockPos.getAllInBox(POS.add(-1,-1,-1),POS.add(1,-1,3)))p.world.setBlockState(at,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
            p.world.setBlockState(POS,ModBlocks.PROGRAMMABLE_CHAIR.getDefaultState(),2);ModBlocks.PROGRAMMABLE_CHAIR.onBlockPlacedBy(p.world,POS,p.world.getBlockState(POS),p,new ItemStack(ModBlocks.PROGRAMMABLE_CHAIR));
            p.connection.setPlayerLocation(POS.getX()+.5,POS.getY(),POS.getZ()+2,180,0);p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER));p.inventoryContainer.detectAndSendChanges();
        }).get();stage++;ticks=0;return false;}
        if(stage==1){if(!(mc.world.getTileEntity(POS) instanceof TileEntityProgrammableChair)||mc.player.getDistanceSq(POS.getX()+.5,POS.getY(),POS.getZ()+2)>.1)return false;mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=actor(mc);p.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_PROGRAMMABLE_CHAIR,p.world,POS.getX(),POS.getY(),POS.getZ());}).get();stage++;ticks=0;return false;}
        if(stage==2){if(!(mc.currentScreen instanceof GuiProgrammableChair))return false;GuiProgrammableChair gui=(GuiProgrammableChair)mc.currentScreen;
            java.util.List<net.minecraft.client.gui.GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");GuiSlider slider=(GuiSlider)buttons.stream().filter(b->b.id==12).findFirst().get();require(slider.mousePressed(mc,slider.x+slider.width-5,slider.y+10),"real slider click refused");slider.mouseReleased(slider.x+slider.width-5,slider.y+10);stage++;ticks=0;return false;}
        if(stage==3){boolean ready=mc.getIntegratedServer().callFromMainThread(()->((TileEntityProgrammableChair)actor(mc).world.getTileEntity(POS)).getAngle()==7).get();if(!ready)return false;
            GuiProgrammableChair gui=(GuiProgrammableChair)mc.currentScreen;for(int[] size:new int[][]{{320,240},{480,320}}){gui.setWorldAndResolution(mc,size[0],size[1]);javax.imageio.ImageIO.write(DialogRenderCapture.image(mc,gui,size[0],size[1]),"png",new File(output,"chair-angle-dialog-"+size[0]+".png"));}
            mc.getIntegratedServer().addScheduledTask(()->{EntityPlayerMP p=actor(mc);TileEntityProgrammableChair tile=(TileEntityProgrammableChair)p.world.getTileEntity(POS);require(tile.getStyle()==0&&tile.getHeight()==1&&tile.getAngle()==7,"angle packet changed unrelated settings");net.minecraft.nbt.NBTTagCompound captured=com.vandorlabs.items.ProgrammableSettings.capture(p.world,POS);require(captured.getInteger(com.vandorlabs.items.ProgrammableSettings.CHAIR_ANGLE)==7,"Duplifier capture omitted angle");tile.setAngle(1);require(com.vandorlabs.items.ProgrammableSettings.apply(p.world,POS,captured)&&tile.getAngle()==7,"Duplifier apply omitted angle");TileEntityProgrammableChair old=new TileEntityProgrammableChair();require(old.getAngle()==0&&!old.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).hasKey("ChairAngle"),"legacy zero-angle serialization changed");}).get();
            for(boolean legacy:new boolean[]{false,true}){io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();try{MessageProgrammableChair packet=legacy?new MessageProgrammableChair(POS,2,1):new MessageProgrammableChair(POS,2,1,7);packet.toBytes(bytes);require(bytes.readableBytes()==(legacy?16:17),"bounded packet size");new MessageProgrammableChair().fromBytes(bytes);require(!bytes.isReadable(),"packet round trip left bytes");}finally{bytes.release();}}
            mc.player.closeScreen();DisplaysLabelChecks.run(mc,output);FurnitureModelChecks.run(mc,output);com.vandorlabs.gear.client.TrackSteeringChecks.run(mc,output);com.vandorlabs.gear.client.TrackTreadChecks.run(mc,output);
            System.out.println("[vandorlabs][reprolab] furniture-live PASS actual-angle-slider packets17/legacy16 exact-style/height all8-angles saved-placement seat-yaw item-padding model-cache Track-body/support and legacy-tread-regressions");return true;
        }
        return false;
    }
    private FurnitureLiveChecks(){}
}
