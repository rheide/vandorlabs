package com.vandorlabs.client;

import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import com.vandorlabs.network.MessageDisplayActivation;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import io.netty.buffer.*;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import javax.imageio.ImageIO;

/** Actual dialog packets, four bands, upgrade/copy data and immediate joined-layout invalidation. */
final class DisplayActivationRuntimeChecks {
    private static final BlockPos POS=new BlockPos(300,100,300);
    private static int stage,fixture,ticks,slot;private static boolean done;
    private static Block[] blocks(){return new Block[]{ModBlocks.ANIMATED_SCREEN_SELECTOR,ModBlocks.PROGRAMMABLE_FULL_INPUT,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,ModBlocks.PROGRAMMABLE_INPUT,ModBlocks.PROGRAMMABLE_CONSOLE,ModBlocks.PROGRAMMABLE_HALF_CONSOLE,Block.getBlockFromName("vandorlabs:programmable_diagonal_half_console")};}
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException("display activation fixture="+fixture+" stage="+stage+" slot="+slot+": "+reason);}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(done)return true;if(++ticks%10!=0)return false;require(ticks<6000,"bounded fixture");
        if(stage==0){
            if(fixture==0){if(!JoinedDisplayPowerRuntimeChecks.tick(mc))return false;packets();joined(mc);}
            mc.gameSettings.pauseOnLostFocus=false;mc.player.closeScreen();mc.playerController.setGameType(GameType.CREATIVE);Block block=blocks()[fixture];require(block!=null,"registered family block");
            server(mc,()->{EntityPlayerMP p=player(mc);p.interactionManager.setGameType(GameType.CREATIVE);p.capabilities.isFlying=true;p.sendPlayerAbilities();p.inventory.currentItem=0;p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));for(BlockPos at:BlockPos.getAllInBox(POS.add(-2,-1,-2),POS.add(8,3,3)))p.world.setBlockToAir(at);for(int x=0;x<(fixture<2?4:1);x++)for(int y=0;y<(fixture<2?2:1);y++){BlockPos member=POS.add(x,y,0);p.world.setBlockState(member,block.getDefaultState(),3);TileEntityAnimatedScreenSelector seed=(TileEntityAnimatedScreenSelector)p.world.getTileEntity(member);seed.setDisplayMode(2);seed.setAnimationSpeedIndex(2);}p.inventoryContainer.detectAndSendChanges();p.connection.setPlayerLocation(POS.getX()+.5,POS.getY()+1,POS.getZ()+3,180,0);});stage=1;return false;
        }
        if(stage==1){if(mc.world.getBlockState(POS).getBlock()!=blocks()[fixture]||mc.player.getHeldItemMainhand().getItem()!=ModItems.CONFIGURIZER)return false;server(mc,()->{EntityPlayerMP p=player(mc);p.openGui(com.vandorlabs.VandorLabs.instance,com.vandorlabs.GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR,p.world,POS.getX(),POS.getY(),POS.getZ());});stage=2;return false;}
        GuiContainer gui=mc.currentScreen instanceof GuiContainer?(GuiContainer)mc.currentScreen:null;
        if(stage==2){if(gui==null)return false;GuiButton redstone=buttons(gui).stream().filter(b->b.displayString.equals("Redstone")).findFirst().get();click(gui,redstone,0);TileEntityAnimatedScreenSelector draft=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS);int original=draft.sharedArtworkTexture();button(gui,640,0);for(int band:new int[]{0,3})require(draft.activationTexture(band)==original&&draft.activationPlayback(band)==2&&draft.activationSpeed(band)==2,"Digital must preserve native artwork animation and speed");require(buttons(gui).stream().anyMatch(b->b.id==650)&&buttons(gui).stream().anyMatch(b->b.id==653),"digital tabs missing");button(gui,640,0);button(gui,640,1);button(gui,640,0);for(int band=0;band<4;band++)require(draft.activationTexture(band)==original&&draft.activationPlayback(band)==2&&draft.activationSpeed(band)==2,"Analog must preserve artwork animation and speed");layout(mc,gui,output,"redstone");slot=0;stage=3;return false;}
        if(stage==3){require(gui!=null,"GUI retained");button(gui,650+slot,0);DisplayActivationPane pane=(DisplayActivationPane)field(gui,"activationPane");HousingTextureList picker=(HousingTextureList)field(pane,"picker");require(((String)field(picker,"query")).isEmpty(),"new tab clears stale filter and reveals selection");selectedVisible(picker);if(fixture<2)require(((Map<?,?>)field(picker,"options")).containsKey(texture(2)),"Half options must survive every state edit");pick(gui,picker,texture(slot));button(gui,660+(slot==2?2:slot==0?0:1),0);button(gui,663+slot%3,0);layout(mc,gui,output,"band"+slot);search(gui,(HousingTextureList)field(pane,"picker"));if(slot==0)layout(mc,gui,output,"search");if(++slot==4){button(gui,20,0);stage=4;}return false;}
        if(stage==4){server(mc,()->{
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)player(mc).world.getTileEntity(POS);assertStates(tile);List<TileEntityAnimatedScreenSelector> members=DisplayEditGroups.members(tile);require(members.size()==(fixture<2?8:1),"stable edit rectangle");for(TileEntityAnimatedScreenSelector member:members)assertStates(member);TileEntityAnimatedScreenSelector loaded=new TileEntityAnimatedScreenSelector();loaded.setWorld(tile.getWorld());loaded.setPos(POS);loaded.readFromNBT(tile.writeToNBT(new NBTTagCompound()));assertStates(loaded);
            player(mc).world.setBlockState(POS.east(6),blocks()[fixture].getDefaultState(),3);require(ProgrammableSettings.apply(player(mc).world,POS.east(6),ProgrammableSettings.capture(player(mc).world,POS)),"Copifier activation copy");assertStates((TileEntityAnimatedScreenSelector)player(mc).world.getTileEntity(POS.east(6)));
        });
            TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(POS);assertStates(tile);for(int level=0;level<=15;level++){tile.setChannelLevel(level);int expected=level==0?0:level<=5?1:level<=10?2:3;require(tile.getSurfaceTexture(tile.activationSurfaceSlot())==texture(expected)&&tile.getEffectiveMode()==(expected==2?2:expected==0?0:1),"analog level "+level);}
            java.awt.image.BufferedImage dark=render(mc,tile,0,0),on=render(mc,tile,15,0);require(difference(dark,on)>20,"rendered Off/On artwork unchanged");ImageIO.write(on,"png",new File(output,"activation-"+fixture+"-world.png"));
            String source=fixture==3||fixture==5||fixture==6?"console_inputs/cool27_half_analysis_station_static":"cool27_full_specialist_science_static";int animated=ScreenHousingTextures.clamp(ScreenHousingTextures.screenIndex(source));tile.configureActivation(2,3,animated,2,1);java.awt.image.BufferedImage frame0=render(mc,tile,15,0),frame1=render(mc,tile,15,20);require(difference(frame0,frame1)>2,"state animation did not advance "+source);ImageIO.write(frame0,"png",new File(output,"activation-"+fixture+"-animated.png"));
            int activeSlot=tile.activationSurfaceSlot();tile.redstoneScreen(activeSlot).setEnabled(true);require(tile.baseSurfaceTexture(activeSlot)==TileEntityAnimatedScreenSelector.REDSTONE_SURFACE&&tile.getSurfaceTexture(activeSlot)==animated,"state artwork must override visible rows without deleting shared controls");tile.frameAppearance.split=true;require(!tile.splitDisplay(),"state artwork overrides a saved split layout");tile.configureActivation(2,3,-1,2,1);require(tile.getSurfaceTexture(activeSlot)==TileEntityAnimatedScreenSelector.REDSTONE_SURFACE&&tile.splitDisplay(),"inherited state retains shared row and split configuration");tile.redstoneScreen(activeSlot).setEnabled(false);
            require(tile.baseSurfaceTexture(1-activeSlot)==tile.getSurfaceTexture(1-activeSlot),"console secondary artwork changed");tile.setChannelLevel(0);fixture++;stage=0;if(fixture==blocks().length){done=true;System.out.println("[vandorlabs][reprolab] display-activation PASS seven families actual dialog clicks Digital Analog Disabled right-click 16 levels packet bounds NBT Copifier console controls shared compact/full screenshots immediate tile-packet joins wide picker");}return done;
        }
        return false;
    }
    @SuppressWarnings("unchecked") private static void selectedVisible(HousingTextureList picker)throws Exception{
        List<Integer> rows=(List<Integer>)field(picker,"rows");int index=rows.indexOf((Integer)field(picker,"selected")),scroll=(Integer)field(picker,"scroll"),count=(Integer)field(picker,"count");
        require(index>scroll&&index<scroll+count,"selected texture must be visible below the pinned heading");
    }
    private static void search(GuiContainer gui,HousingTextureList picker)throws Exception{
        TextureSearchInput.draw(new net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre(gui,0,0,0));
        DialogRenderCapture.image(net.minecraft.client.Minecraft.getMinecraft(),gui,gui.width,gui.height);
        picker=(HousingTextureList)field(field(gui,"activationPane"),"picker");
        Field visible=TextureSearchInput.class.getDeclaredField("visible");visible.setAccessible(true);List<?> shown=(List<?>)visible.get(null);
        require(shown.size()==1&&shown.get(0)==picker,"only the visible state picker may receive search input");
        click(gui,(Integer)field(picker,"x")+6,(Integer)field(picker,"y")-HousingTextureList.ROW_HEIGHT+8,0);
        for(char c:"gold".toCharArray()){
            if(gui instanceof GuiAnimatedScreenSelector)((GuiAnimatedScreenSelector)gui).keyTyped(c,0);
            else if(gui instanceof GuiProgrammableInput)((GuiProgrammableInput)gui).keyTyped(c,0);
            else ((GuiProgrammableHalfConsole)gui).keyTyped(c,0);
        }
        require("gold".equals(field(picker,"query")),"active state picker must consume typed search text");
    }
    private static int texture(int state){return fixture<2&&state==2?ScreenHousingTextures.clamp(ScreenHousingTextures.screenIndex("console_inputs/cool27_half_analysis_station_static")):state;}
    private static void assertStates(TileEntityAnimatedScreenSelector tile){require(tile.activationMode()==2,"analog mode saved");for(int i=0;i<4;i++)require(tile.activation.texture(i)==texture(i)&&tile.activationPlayback(i)==(i==2?2:i==0?0:1)&&tile.activationSpeed(i)==i%3,"state saved "+i);}
    private static void packets()throws Exception{
        Field valid=MessageDisplayActivation.class.getDeclaredField("valid");valid.setAccessible(true);
        for(int mode=0;mode<3;mode++)for(int state=0;state<4;state++){MessageDisplayActivation sent=new MessageDisplayActivation(POS,mode,state,state,state%3,state%3,false);ByteBuf b=Unpooled.buffer();sent.toBytes(b);require(b.readableBytes()==17,"bounded edit packet size");MessageDisplayActivation read=new MessageDisplayActivation();read.fromBytes(b.copy());require(valid.getBoolean(read),"valid packet rejected");ByteBuf after=Unpooled.buffer();read.toBytes(after);require(ByteBufUtil.equals(b,after),"round trip changed bytes");b.writeByte(0);read.fromBytes(b);require(!valid.getBoolean(read),"trailing bytes accepted");b.release();after.release();}
        TileEntityAnimatedScreenSelector old=new TileEntityAnimatedScreenSelector();require(!old.writeToNBT(new NBTTagCompound()).hasKey("DisplayActivation"),"legacy NBT shape changed before opt in");
    }
    private static void joined(Minecraft mc)throws Exception{
        for(Block block:new Block[]{ModBlocks.ANIMATED_SCREEN_SELECTOR,ModBlocks.PROGRAMMABLE_FULL_INPUT})for(int[] size:new int[][]{{1,1},{2,2},{4,2},{2,4},{3,1}}){
            List<VehicleStructure.Cell> cells=new ArrayList<>();for(int x=0;x<size[0];x++)for(int y=0;y<size[1];y++){TileEntityAnimatedScreenSelector t=new TileEntityAnimatedScreenSelector();t.setPos(new BlockPos(x,y,0));t.setSurfaceTexture(0,0);cells.add(new VehicleStructure.Cell(t.getPos(),block.getDefaultState(),t.writeToNBT(new NBTTagCompound())));}
            VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(cells));TileEntityAnimatedScreenSelector first=(TileEntityAnimatedScreenSelector)view.getTileEntity(BlockPos.ORIGIN);require(ScreenSurfaceGroups.wideDisplay(first)==(size[0]>size[1]),"joined aspect "+block+Arrays.toString(size));GuiAnimatedScreenSelector dialog=new GuiAnimatedScreenSelector(mc.player.inventory,first);dialog.setWorldAndResolution(mc,460,340);ScreenTextureList list=(ScreenTextureList)field(dialog,"screenList");HousingTextureList picker=(HousingTextureList)field(list,"list");Map<?,?> options=(Map<?,?>)field(picker,"options");int half=ScreenHousingTextures.clamp(ScreenHousingTextures.screenIndex("console_inputs/cool27_half_analysis_station_static"));require(options.containsKey(half)==(size[0]>size[1]),"half picker visibility "+Arrays.toString(size));
            if(size[0]==2&&size[1]==2){require(!DisplayEditGroups.apply(first,2,1,3,1,1,false,target->target==first),"partial authorization must refuse the whole edit");for(net.minecraft.tileentity.TileEntity member:view.tiles.values())require(((TileEntityAnimatedScreenSelector)member).activation.mode<0,"denied bulk edit changed a tile");TileEntityAnimatedScreenSelector edited=(TileEntityAnimatedScreenSelector)view.getTileEntity(new BlockPos(1,1,0));NBTTagCompound different=edited.writeToNBT(new NBTTagCompound());different.setInteger("PrimarySurfaceTexture",1);edited.readFromNBT(different);require(ScreenSurfaceGroups.layout(first).columns*ScreenSurfaceGroups.layout(first).rows<4,"negative control must split");NBTTagCompound matching=edited.writeToNBT(new NBTTagCompound());matching.setInteger("PrimarySurfaceTexture",0);edited.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(edited.getPos(),0,matching));for(net.minecraft.tileentity.TileEntity tile:view.tiles.values()){GlassSurfaceGroups.Layout rect=ScreenSurfaceGroups.layout((TileEntityAnimatedScreenSelector)tile);require(rect.columns==2&&rect.rows==2,"tile packet must immediately join cached 2x2");}edited.setSelectedScreen("cool27_full_specialist_science");require(ScreenSurfaceGroups.layout(first).columns==2&&ScreenSurfaceGroups.layout(first).rows==2,"hidden native screen must not split identical face overrides");}
            ScreenSurfaceGroups.unload(view);
        }
    }
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen gui){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void button(GuiContainer gui,int id,int mouse)throws Exception{GuiButton b=buttons(gui).stream().filter(v->v.id==id&&v.visible).findFirst().orElseThrow(()->new IllegalStateException("missing button "+id));if(!b.enabled)return;click(gui,b,mouse);}
    private static void click(GuiContainer gui,GuiButton b,int mouse)throws Exception{click(gui,b.x+b.width/2,b.y+b.height/2,mouse);}
    private static void click(GuiContainer gui,int x,int y,int mouse)throws Exception{if(gui instanceof GuiAnimatedScreenSelector)((GuiAnimatedScreenSelector)gui).mouseClicked(x,y,mouse);else if(gui instanceof GuiProgrammableInput)((GuiProgrammableInput)gui).mouseClicked(x,y,mouse);else ((GuiProgrammableHalfConsole)gui).mouseClicked(x,y,mouse);}
    @SuppressWarnings("unchecked") private static void pick(GuiContainer gui,HousingTextureList list,int choice)throws Exception{int old=list.selected();list.setSelected(choice);Field selected=HousingTextureList.class.getDeclaredField("selected");selected.setAccessible(true);selected.setInt(list,old);List<Integer> rows=(List<Integer>)field(list,"rows");int row=rows.indexOf(choice);require(row>=0,"texture in picker");click(gui,(Integer)field(list,"x")+8,(Integer)field(list,"y")+(row-(Integer)field(list,"scroll"))*HousingTextureList.ROW_HEIGHT+8,0);}
    private static void layout(Minecraft mc,GuiContainer gui,File output,String name)throws Exception{int w=gui.width,h=gui.height;try{for(int[] size:new int[][]{{320,240},{460,340}}){gui.setWorldAndResolution(mc,size[0],size[1]);for(GuiButton a:buttons(gui))if(a.visible){require(a.x>=0&&a.y>=0&&a.x+a.width<=gui.width&&a.y+a.height<=gui.height,"button bounds "+a.id);require(mc.fontRenderer.getStringWidth(a.displayString)<=a.width-4,"button text overflow "+a.id+" "+a.displayString);for(GuiButton b:buttons(gui))if(a!=b&&b.visible)require(!(a.x<b.x+b.width&&b.x<a.x+a.width&&a.y<b.y+b.height&&b.y<a.y+a.height),"button overlap "+a.id+"/"+b.id);}ImageIO.write(DialogRenderCapture.image(mc,gui,size[0],size[1]),"png",new File(output,"activation-"+fixture+"-"+name+"-"+size[0]+".png"));}}finally{gui.setWorldAndResolution(mc,w,h);}}
    private static int difference(java.awt.image.BufferedImage a,java.awt.image.BufferedImage b){int count=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))count++;return count;}
    private static java.awt.image.BufferedImage render(Minecraft mc,TileEntityAnimatedScreenSelector tile,int level,long time){
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(256,256,true);long before=tile.getWorld().getTotalWorldTime();tile.setChannelLevel(level);tile.getWorld().setTotalWorldTime(time);
        net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.ortho(-.3,1.3,-.3,1.3,-5,5);net.minecraft.client.renderer.GlStateManager.matrixMode(5888);net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.depthMask(true);net.minecraft.client.renderer.GlStateManager.clearColor(.12F,.18F,.24F,1);net.minecraft.client.renderer.GlStateManager.clear(16640);net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableFog();net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.disableCull();net.minecraft.client.renderer.GlStateManager.enableTexture2D();net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();net.minecraft.client.renderer.GlStateManager.translate(.5,.5,.5);net.minecraft.client.renderer.GlStateManager.rotate(25,1,0,0);net.minecraft.client.renderer.GlStateManager.rotate(180,0,1,0);net.minecraft.client.renderer.GlStateManager.translate(-.5,-.5,-.5);
            TEAnimatedScreenSelector renderer=new TEAnimatedScreenSelector();renderer.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);CockpitVisualMotion.authoredFrame(()->renderer.render(tile,0,0,0,0,-1,1));org.lwjgl.opengl.GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,frame);
        }finally{tile.getWorld().setTotalWorldTime(before);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5889);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static void server(Minecraft mc,Runnable action)throws Exception{mc.getIntegratedServer().addScheduledTask(action).get();}
    private DisplayActivationRuntimeChecks(){}
}
