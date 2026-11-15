package com.vandorlabs.client;

import com.vandorlabs.ports.*;
import java.io.File;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** One bounded actual-client port path, isolated from the other utility fixtures. */
final class ResourcePortLiveChecks {
    private static final Field TELEPORT=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(NetHandlerPlayServer.class,"targetPos","field_184362_y");
    private static int stage,ticks,fixture,initialScale;
    private static boolean clicked,finished,batteryRendered;
    private static volatile String status="initial setup";
    private static BlockPos at(){return new BlockPos(920+fixture*4,110,0);}
    private static boolean battery(){return fixture==3;}
    private static net.minecraft.block.Block block(){return battery()?net.minecraft.block.Block.getBlockFromName("vandorlabs:battery_block"):ResourcePorts.BLOCKS[fixture];}
    private static ResourcePortBlock.Kind kind(){return battery()?ResourcePortBlock.Kind.POWER:ResourcePorts.BLOCKS[fixture].kind;}
    private static String texture(){return battery()?"battery":kind().texture;}
    private static void need(boolean ok,String why){if(!ok)throw new IllegalStateException("Resource port fixture="+fixture+" stage="+stage+" ticks="+ticks+" "+why+" "+status);}
    private static boolean sync(Minecraft mc)throws Exception{
        double x=mc.player.posX,y=mc.player.posY,z=mc.player.posZ;int slot=mc.player.inventory.currentItem,count=mc.player.getHeldItemMainhand().getCount();net.minecraft.item.Item item=mc.player.getHeldItemMainhand().getItem();GameType mode=mc.playerController.getCurrentGameType();
        return mc.getIntegratedServer().callFromMainThread(()->{EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);boolean pending=TELEPORT.get(actor.connection)!=null;status="teleportPending="+pending+" mode="+actor.interactionManager.getGameType()+" clientMode="+mode+" slot="+actor.inventory.currentItem+" held="+actor.getHeldItemMainhand()+" position="+actor.getPositionVector();return !pending&&actor.interactionManager.getGameType()==mode&&actor.inventory.currentItem==slot&&actor.getHeldItemMainhand().getItem()==item&&actor.getHeldItemMainhand().getCount()==count&&actor.getDistanceSq(x,y,z)<.0001;}).get();
    }
    private static void prepare(Minecraft mc,boolean configurizer)throws Exception{
        mc.getIntegratedServer().callFromMainThread(()->{
            WorldServer world=mc.getIntegratedServer().getWorld(0);EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            if(!configurizer){for(BlockPos p:BlockPos.getAllInBox(at().add(-2,-1,-5),at().add(2,2,2))){world.getChunkFromBlockCoords(p);world.removeTileEntity(p);world.setBlockToAir(p);}for(BlockPos p:BlockPos.getAllInBox(at().add(-2,-1,-5),at().add(2,-1,2)))world.setBlockState(p,net.minecraft.init.Blocks.STONE.getDefaultState(),2);
                actor.setPosition(at().getX()+.5,at().getY(),at().getZ()-4);actor.rotationYaw=0;net.minecraft.item.ItemBlock item=(net.minecraft.item.ItemBlock)net.minecraft.item.Item.getItemFromBlock(block());net.minecraft.block.state.IBlockState state=block().getStateForPlacement(world,at(),EnumFacing.UP,.5F,.5F,.5F,0,actor);need(item.placeBlockAt(new ItemStack(item),actor,world,at(),EnumFacing.UP,.5F,.5F,.5F,state),"scene native ItemBlock placement");
            }
            actor.setGameType(GameType.SURVIVAL);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,configurizer?new ItemStack(com.vandorlabs.items.ModItems.CONFIGURIZER):ItemStack.EMPTY);actor.inventoryContainer.detectAndSendChanges();actor.connection.setPlayerLocation(at().getX()+.5+(configurizer?.25:0),at().getY(),at().getZ()-4,0,15);return null;
        }).get();clicked=false;ticks=0;
    }
    private static void click(GuiResourcePort gui,int id,int button)throws Exception{GuiButton target=null;for(GuiButton candidate:gui.controls())if(candidate.id==id)target=candidate;need(target!=null&&target.visible&&target.enabled,"available dialog control "+id);gui.mouseClicked(target.x+target.width/2,target.y+target.height/2,button);}
    private static void screenshot(Minecraft mc,GuiResourcePort gui,File output,String name)throws Exception{mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,name));}
    private static void renderModels(Minecraft mc){
        if(battery()){need(mc.world.getTileEntity(at()) instanceof com.vandorlabs.tiles.TileEntityBattery,"same installed Battery owner backs dialog");if(!batteryRendered){BatteryCacheClientChecks.run(mc);batteryRendered=true;}return;}
        ResourcePortTile tile=(ResourcePortTile)mc.world.getTileEntity(at());net.minecraft.block.state.IBlockState clean=mc.world.getBlockState(at()),extended=block().getExtendedState(clean,mc.world,at());IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(clean);
        for(EnumFacing side:EnumFacing.values()){
            java.util.List<BakedQuad> quads=model.getQuads(extended,side,0);need(quads.size()==1,"native opaque cube emits exactly one selected quad per face");String sprite=quads.get(0).getSprite().getIconName();ResourcePortSettings.Face face=tile.settings().face(side.getIndex());String texture=face==ResourcePortSettings.Face.DISABLED?"port_hull":kind().texture+(face==ResourcePortSettings.Face.INPUT?"_in_":"_out_")+(((tile.appearanceMask()>>side.getIndex())&1)!=0?"on":"off");String expected=face==ResourcePortSettings.Face.DISABLED&&tile.settings().housingChoice>=0?com.vandorlabs.tiles.ScreenHousingTextures.texture(tile.settings().housingChoice):"vandorlabs:blocks/resource_ports/"+texture;need(sprite.equals(expected),"emitted selected authored face "+side+" got="+sprite);
            need((face==ResourcePortSettings.Face.DISABLED&&tile.settings().housingChoice>=0)||quads.get(0).getSprite().getIconWidth()==64&&quads.get(0).getSprite().getIconHeight()==64,"authored 64px port artwork retained");need(quads==model.getQuads(extended,side,0),"repeated face requests reuse immutable records");
        }
    }
    @SuppressWarnings("unchecked") private static void copiedSettingsRow(Minecraft mc,File output)throws Exception{
        itemPadding(mc,output);
        ItemStack held=mc.player.getHeldItemMainhand();int scale=mc.gameSettings.guiScale;GuiDuplifier gui=null;
        try{
            mc.player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(com.vandorlabs.items.ModItems.DUPLIFIER));gui=new GuiDuplifier(mc.player.inventory);gui.setWorldAndResolution(mc,320,240);
            Field field=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.gui.GuiScreen.class,"buttonList","field_146292_n");java.util.List<GuiButton> controls=(java.util.List<GuiButton>)field.get(gui);int index=-1;com.vandorlabs.items.DuplifierOptionCatalog.Option[] options=com.vandorlabs.items.DuplifierOptionCatalog.options();for(int i=0;i<options.length;i++)if(options[i].key.equals("resource_port_settings"))index=i;need(index==options.length-1,"Resource Port option appends without moving earlier indices");
            GuiButton selected=null;int height=new net.minecraft.client.gui.ScaledResolution(mc).getScaledHeight();for(GuiButton button:controls){need(button.y>=0&&button.y+button.height<=height,"compact Copifier includes last row and footer");if(button.id==100+index)selected=button;}need(selected!=null&&selected.displayString.equals("Resource Port Settings: On"),"new tool defaults to copying all port settings");
            mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();gui.drawScreen(-1,-1,0);javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"port-copifier-compact-row.png"));
        }finally{if(gui!=null)gui.onGuiClosed();mc.player.setHeldItem(EnumHand.MAIN_HAND,held);need(mc.gameSettings.guiScale==scale,"compact Copifier restores player GUI scale");}
    }
    private static void itemPadding(Minecraft mc,File output)throws Exception{
        net.minecraft.client.gui.GuiScreen previous=mc.currentScreen;PortIcons icons=new PortIcons();try{mc.displayGuiScreen(icons);mc.getFramebuffer().bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.depthMask(true);net.minecraft.client.renderer.GlStateManager.clear(16640);mc.entityRenderer.setupOverlayRendering();icons.drawScreen(-1,-1,0);java.awt.image.BufferedImage shot=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int scale=new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor(),size=16*scale;
            for(int i=0;i<3;i++){need(new ItemStack(ResourcePorts.BLOCKS[i]).getDisplayName().equals(ResourcePorts.BLOCKS[i].kind.title),"canonical item translation "+ResourcePorts.BLOCKS[i].kind);int x=icons.x(i)*scale,y=icons.y()*scale,ink=0;for(int a=0;a<size;a++)for(int b=0;b<size;b++){boolean marked=(shot.getRGB(x+a,y+b)&0xffffff)!=PortIcons.BACK;if(marked){ink++;need(a>0&&b>0&&a<size-1&&b<size-1,"every actual rotated inventory-icon edge has padding "+ResourcePorts.BLOCKS[i].kind);}}need(ink>10&&ink<size*size*.85,"recognizable padded port icon "+ResourcePorts.BLOCKS[i].kind);}javax.imageio.ImageIO.write(shot,"png",new File(output,"port-item-padding.png"));
        }finally{net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);mc.displayGuiScreen(previous);}System.out.println("[vandorlabs][reprolab] resource-port-item-padding PASS all3actualrotateditemmodels everyedgepadding");
    }
    private static final class PortIcons extends net.minecraft.client.gui.GuiScreen{
        static final int BACK=0x203040;int x(int i){return width/2+(i-1)*40;}int y(){return height/2;}@Override public boolean doesGuiPauseGame(){return false;}
        @Override public void drawScreen(int x,int y,float partial){net.minecraft.client.renderer.GlStateManager.disableLighting();drawRect(0,0,width,height,0xff101820);for(int i=0;i<3;i++){drawRect(x(i)-1,y()-1,x(i)+17,y()+17,0xff708090);drawRect(x(i),y(),x(i)+16,y()+16,0xff000000|BACK);}net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();for(int i=0;i<3;i++)itemRender.renderItemAndEffectIntoGUI(new ItemStack(ResourcePorts.BLOCKS[i]),x(i),y());net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();}
    }
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(finished)return true;
        if("true".equals(System.getenv("VANDOR_RESOURCE_PORT_OWNERSHIP_ONLY"))){if(++ticks<80)return false;mc.getIntegratedServer().callFromMainThread(()->{ResourcePortOwnershipChecks.run(mc.getIntegratedServer().getWorld(0));return null;}).get();finished=true;System.out.println("[vandorlabs][reprolab] utility26-focused PASS resource-port ownership integration");return true;}
        if("true".equals(System.getenv("VANDOR_RESOURCE_PORT_COPIFIER_ONLY"))){if(++ticks<80)return false;copiedSettingsRow(mc,output);finished=true;System.out.println("[vandorlabs][reprolab] utility26-focused PASS resource-port compact Copifier row");return true;}
        if(fixture>=4){if(!mc.getIntegratedServer().callFromMainThread(()->ResourcePortRuntimeChecks.idleTick(mc.getIntegratedServer().getWorld(0))).get())return false;copiedSettingsRow(mc,output);finished=true;System.out.println("[vandorlabs][reprolab] utility26-focused PASS resource-ports actual Survival ordinary/Configurizer dedicated dialogs real C2S all settings/rendered authored faces compact Copifier row");return true;}
        need(++ticks<500,"bounded actual interaction");
        if(stage==0){if(ticks<80)return false;mc.displayGuiScreen(null);initialScale=mc.gameSettings.guiScale;mc.gameSettings.pauseOnLostFocus=false;mc.getIntegratedServer().callFromMainThread(()->{ResourcePortRuntimeChecks.run(mc.getIntegratedServer().getWorld(0),mc.getIntegratedServer().getPlayerList().getPlayers().get(0));return null;}).get();prepare(mc,false);stage=1;return false;}
        if(stage==1||stage==4){
            if(mc.playerController.getCurrentGameType()!=GameType.SURVIVAL||mc.player.inventory.currentItem!=0||!(mc.world.getTileEntity(at()) instanceof ResourcePortTile)||mc.player.getDistanceSq(at().getX()+.5+(stage==4?.25:0),at().getY(),at().getZ()-4)>.0001||!sync(mc))return false;
            if(stage==1&&!mc.player.getHeldItemMainhand().isEmpty()||stage==4&&mc.player.getHeldItemMainhand().getItem()!=com.vandorlabs.items.ModItems.CONFIGURIZER)return false;
            if(!clicked){mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(at(),EnumFacing.NORTH,EnumHand.MAIN_HAND,.5F,.5F,0));clicked=true;}stage++;ticks=0;return false;
        }
        if(stage==2){
            if(!(mc.currentScreen instanceof GuiResourcePort))return false;GuiResourcePort gui=(GuiResourcePort)mc.currentScreen;need(gui.inventorySlots instanceof ResourcePortContainer,"ordinary right-click opens dedicated settings container");need(gui.controlsFit(),"all controls, header and Done fit actual viewport");renderModels(mc);screenshot(mc,gui,output,"port-"+texture()+"-general.png");
            click(gui,14,1);gui.mouseClicked(gui.left()+20,gui.top()+166,0);gui.keyTyped('7',org.lwjgl.input.Keyboard.KEY_7);click(gui,20,0);click(gui,21,1);click(gui,101,0);gui.mouseClicked(gui.left()+20,gui.top()+92,0);for(char c:"64123".toCharArray())gui.keyTyped(c,0);click(gui,30,0);stage=3;ticks=0;return false;
        }
        if(stage==3){
            boolean accepted=mc.getIntegratedServer().callFromMainThread(()->{ResourcePortTile tile=(ResourcePortTile)mc.getIntegratedServer().getWorld(0).getTileEntity(at());return tile.settings().throughput==7&&tile.settings().pullInputs&&tile.settings().onTexture==ResourcePortSettings.OnTexture.CONNECTED&&tile.settings().face(EnumFacing.WEST.getIndex())==ResourcePortSettings.Face.OUTPUT&&tile.redstoneMode()==1&&tile.getRedstoneChannels().equals(com.vandorlabs.redstone.ChannelList.of(64123));}).get();if(!accepted)return false;
            need(mc.currentScreen instanceof GuiResourcePort,"authorized settings edits retain dedicated dialog");need(((GuiResourcePort)mc.currentScreen).controlsFit(),"Redstone page fits actual viewport");screenshot(mc,(GuiResourcePort)mc.currentScreen,output,"port-"+texture()+"-redstone.png");GuiResourcePort gui=(GuiResourcePort)mc.currentScreen;click(gui,102,0);gui.mouseClicked(gui.left()+20,gui.top()+70,0);for(char c:"Light Wall Panel".toCharArray())gui.keyTyped(c,0);gui.mouseClicked(gui.left()+20,gui.top()+94,0);stage=30;ticks=0;return false;
        }
        if(stage==30){boolean accepted=mc.getIntegratedServer().callFromMainThread(()->((ResourcePortTile)mc.getIntegratedServer().getWorld(0).getTileEntity(at())).settings().housingChoice==1).get();if(!accepted||((ResourcePortTile)mc.world.getTileEntity(at())).settings().housingChoice!=1)return false;need(mc.currentScreen instanceof GuiResourcePort,"Housing tab stays in authorized session");renderModels(mc);screenshot(mc,(GuiResourcePort)mc.currentScreen,output,"port-"+texture()+"-housing.png");if(battery()){click((GuiResourcePort)mc.currentScreen,40,0);stage=31;ticks=0;return false;}mc.player.closeScreen();need(mc.gameSettings.guiScale==initialScale,"Housing dialog restores original scale");prepare(mc,true);stage=4;return false;}
        if(stage==31){boolean accepted=mc.getIntegratedServer().callFromMainThread(()->!((com.vandorlabs.tiles.TileEntityBattery)mc.getIntegratedServer().getWorld(0).getTileEntity(at())).isBlockShape()).get();if(!accepted||((com.vandorlabs.tiles.TileEntityBattery)mc.world.getTileEntity(at())).isBlockShape())return false;need(((GuiResourcePort)mc.currentScreen).controlsFit(),"Battery status, picker and shape fit");screenshot(mc,(GuiResourcePort)mc.currentScreen,output,"port-battery-hex-housing.png");mc.player.closeScreen();prepare(mc,true);stage=4;return false;}
        if(stage==5){if(!(mc.currentScreen instanceof GuiResourcePort))return false;need(mc.player.openContainer instanceof ResourcePortContainer,"Configurizer uses identical dedicated container");ResourcePortTile client=(ResourcePortTile)mc.world.getTileEntity(at());need(client.settings().throughput==7&&client.settings().pullInputs&&client.redstoneMode()==1,"client receives independent public settings round-trip");renderModels(mc);mc.player.closeScreen();System.out.println("[vandorlabs][reprolab] resource-port-player PASS "+kind()+" synchronized Survival right-click + Configurizer, reverse cycles, typed fields, actual C2S and native selected-face mesh");fixture++;stage=1;if(fixture<4)prepare(mc,false);return false;}
        return false;
    }
    private ResourcePortLiveChecks(){}
}
