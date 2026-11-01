package com.vandorlabs.client;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.blocks.ModBlocks;
import com.vandorlabs.items.ModItems;
import com.vandorlabs.items.ProgrammableSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import org.lwjgl.opengl.GL11;

/** Real selections and packets, flat tab captures, upgrade/copy fidelity and actual tile rendering. */
final class DisplayMaterialRuntimeChecks {
    private static final BlockPos POS=new BlockPos(280,90,280);
    private static final String[] SOURCES={"cool27_full_specialist_science_static","console_inputs/cool27_half_analysis_station_static","company_pinnacle_full_reactor_static","company_paladyne_full_biology_static"};
    private static final int[] MODES={1,1,1,1},SPEEDS={1,1,1,1};
    private static int ticks,stage,glassIndex,worldWait;private static boolean done;
    private static int choice(int slot){int value=ScreenHousingTextures.clamp(ScreenHousingTextures.screenIndex(SOURCES[slot]));require(value>0,"display choice missing "+SOURCES[slot]);return value;}
    static boolean tick(Minecraft mc,File output)throws Exception{
        if(done)return true;if(++ticks%10!=0)return false;mc.gameSettings.pauseOnLostFocus=false;
        if(stage==0){
            mc.player.closeScreen();mc.playerController.setGameType(GameType.CREATIVE);
            server(mc,()->{EntityPlayerMP p=player(mc);p.interactionManager.setGameType(GameType.CREATIVE);p.inventory.currentItem=0;p.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));p.capabilities.isFlying=true;p.sendPlayerAbilities();for(BlockPos at:BlockPos.getAllInBox(POS.add(-2,-1,-2),POS.add(3,2,3)))p.world.setBlockToAir(at);p.world.setBlockState(POS,ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK.getDefaultState(),2);p.connection.setPlayerLocation(POS.getX()+.5,POS.getY()-1,POS.getZ()+3,180,5);p.inventoryContainer.detectAndSendChanges();});stage=1;return false;
        }
        if(stage==1){
            boolean ready=mc.player.isCreative()&&mc.player.inventory.currentItem==0&&mc.player.getHeldItemMainhand().getItem()==ModItems.CONFIGURIZER&&mc.player.getDistanceSq(POS)<16&&mc.world.getTileEntity(POS) instanceof TileEntityProgrammableTrigger;
            require(ready||ticks<240,"fixture synchronization mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" position="+mc.player.getPosition());if(!ready)return false;
            open(mc,GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR);stage=2;return false;
        }
        if(stage>=2&&stage<=7){
            require(mc.currentScreen instanceof GuiProgrammableTrigger,"trigger GUI missing stage="+stage);GuiProgrammableTrigger gui=(GuiProgrammableTrigger)mc.currentScreen;
            if(stage==2){pick(gui,triggerList(gui,0),choice(0));layout(mc,gui);capture(mc,output,"trigger-off");}
            if(stage==3){button(gui,93);pick(gui,triggerList(gui,3),choice(3));capture(mc,output,"trigger-on");}
            if(stage==4){button(gui,111);layout(mc,gui);capture(mc,output,"trigger-redstone-binary");button(gui,101);button(gui,102,1);require((Integer)field(gui,"exact")==15,"right-click exact level did not reverse");button(gui,102);require((Integer)field(gui,"exact")==-1,"exact level did not wrap");layout(mc,gui);capture(mc,output,"trigger-redstone-bands");button(gui,103);button(gui,112);layout(mc,gui);capture(mc,output,"trigger-sides");}
            if(stage==5){button(gui,91);pick(gui,triggerList(gui,1),choice(1));capture(mc,output,"trigger-low");}
            if(stage==6){button(gui,92);pick(gui,triggerList(gui,2),choice(2));capture(mc,output,"trigger-mid");}
            if(stage==7){button(gui,93);layout(mc,gui);capture(mc,output,"trigger-high");button(gui,100);}
            stage++;return false;
        }
        if(stage==8){
            server(mc,()->{TileEntityProgrammableTrigger tile=(TileEntityProgrammableTrigger)player(mc).world.getTileEntity(POS);assertTrigger(tile);TileEntityProgrammableTrigger loaded=new TileEntityProgrammableTrigger();loaded.readFromNBT(tile.writeToNBT(new NBTTagCompound()));assertTrigger(loaded);
                BlockPos target=POS.east(2);player(mc).world.setBlockState(target,ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK.getDefaultState(),2);require(ProgrammableSettings.apply(player(mc).world,target,ProgrammableSettings.capture(player(mc).world,POS)),"Duplifier refused trigger settings");assertTrigger((TileEntityProgrammableTrigger)player(mc).world.getTileEntity(target));ItemStack configured=ProgrammableSettings.applyToItem(new ItemStack(net.minecraft.item.Item.getItemFromBlock(ModBlocks.PROGRAMMABLE_TRIGGER_BLOCK)),ProgrammableSettings.capture(player(mc).world,POS));
                require(configured.hasTagCompound()&&configured.getSubCompound("BlockEntityTag")!=null,"configured item missing settings");TileEntityProgrammableTrigger itemTile=new TileEntityProgrammableTrigger();itemTile.readFromNBT(configured.getSubCompound("BlockEntityTag"));assertTrigger(itemTile);

            });stage=9;return false;
        }
        if(stage==9){
            TileEntityProgrammableTrigger tile=(TileEntityProgrammableTrigger)mc.world.getTileEntity(POS);assertTrigger(tile);
            BufferedImage moving0=render(mc,tile,0,0),moving1=render(mc,tile,0,5);require(difference(moving0,moving1)==0,"Trigger artwork must stay static");
            NBTTagCompound repeat=tile.writeToNBT(new NBTTagCompound());tile.readFromNBT(repeat);require(difference(moving0,render(mc,tile,0,0))==0,"repeated tile update restarted/changed frame");
            require(difference(render(mc,tile,5,0),render(mc,tile,5,50))==0&&!tile.hasFastRenderer(),"Static front low band is not stable");
            require(difference(render(mc,tile,10,0),render(mc,tile,10,50))==0&&!tile.hasFastRenderer(),"blank mid band changes/batches atlas artwork");
            require(difference(render(mc,tile,15,0),render(mc,tile,15,20))==0&&!tile.hasFastRenderer(),"Trigger On/high artwork animated");
            require(difference(render(mc,tile,0,0,false),render(mc,tile,15,20,false))==0,"static side changed with signal/animation");
            ImageIO.write(render(mc,tile,15,20,false),"png",new File(output,"display-material-trigger-static-side.png"));
            ImageIO.write(moving0,"png",new File(output,"display-material-trigger-frame0.png"));ImageIO.write(moving1,"png",new File(output,"display-material-trigger-frame1.png"));
            stage=10;return false;
        }
        if(stage==10){
            server(mc,()->{EntityPlayerMP p=player(mc);p.closeScreen();p.world.setBlockToAir(POS);p.world.setBlockState(POS.west(),net.minecraft.init.Blocks.STONE.getDefaultState(),3);p.world.setBlockState(POS,glassIndex==0?net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_glass").getDefaultState():ModBlocks.PROGRAMMABLE_PORTHOLE_WALL.getDefaultState(),2);});stage=11;return false;
        }
        if(stage==11){
            require(mc.world.getTileEntity(POS)!=null,"glass fixture not observed");open(mc,glassIndex==0?GuiHandler.GUI_PROGRAMMABLE_GLASS:GuiHandler.GUI_ANIMATED_SCREEN_SELECTOR);stage=12;return false;
        }
        if(stage==12){
            GuiContainer gui=(GuiContainer)mc.currentScreen;require(glassIndex==0?gui instanceof GuiProgrammableGlass:gui instanceof GuiProgrammableWall,"glass material dialog type");
            if(glassIndex==1)button(gui,213);
            HousingTextureList list=(HousingTextureList)field(gui,glassIndex==0?"textures":"textureList");pick(gui,list,choice(glassIndex));capture(mc,output,glassIndex==0?"glass-display":"porthole-display");stage=13;return false;
        }
        if(stage==13){
            server(mc,()->{net.minecraft.tileentity.TileEntity raw=player(mc).world.getTileEntity(POS);int material=glassIndex==0?((TileEntityProgrammableGlass)raw).getMaterial():((TileEntityAnimatedScreenSelector)raw).getGlassMaterial();require(material==choice(glassIndex),"glass material packet did not save");NBTTagCompound saved=raw.writeToNBT(new NBTTagCompound());if(glassIndex==0){TileEntityProgrammableGlass copy=new TileEntityProgrammableGlass();copy.readFromNBT(saved);require(copy.getMaterial()==material,"glass reload");}else{TileEntityAnimatedScreenSelector copy=new TileEntityAnimatedScreenSelector();copy.readFromNBT(saved);require(copy.getGlassMaterial()==material,"porthole reload");}});
            mc.player.closeScreen();worldWait=0;mc.renderGlobal.loadRenderers();mc.renderGlobal.markBlockRangeForRenderUpdate(POS.getX()-1,POS.getY()-1,POS.getZ()-1,POS.getX()+1,POS.getY()+1,POS.getZ()+1);stage=14;return false;
        }
        if(stage==14){
            require(mc.currentScreen==null,"glass world view still has dialog");if(++worldWait<8)return false;
            net.minecraft.tileentity.TileEntity tile=mc.world.getTileEntity(POS);
            BufferedImage selected=renderGlazing(mc,tile);int material=glassIndex==0?((TileEntityProgrammableGlass)tile).getMaterial():((TileEntityAnimatedScreenSelector)tile).getGlassMaterial();
            if(glassIndex==0)((TileEntityProgrammableGlass)tile).setMaterial(-1);else ((TileEntityAnimatedScreenSelector)tile).setGlassMaterial(-1);
            BufferedImage baseline=renderGlazing(mc,tile);
            if(glassIndex==0)((TileEntityProgrammableGlass)tile).setMaterial(material);else ((TileEntityAnimatedScreenSelector)tile).setGlassMaterial(material);
            require(difference(selected,baseline)>1000,"selected display did not change rendered glazing");ImageIO.write(selected,"png",new File(output,"display-material-"+(glassIndex==0?"glass":"porthole")+"-geometry.png"));
            mc.entityRenderer.updateCameraAndRender(0,System.nanoTime());mc.renderGlobal.updateChunks(System.nanoTime()+100000000L);mc.entityRenderer.updateCameraAndRender(0,System.nanoTime());
            System.out.println("[vandorlabs][reprolab] display-material-world fixture="+glassIndex+" tile="+tile.getClass().getSimpleName()+" position="+mc.player.getPosition()+" "+mc.renderGlobal.getDebugInfoRenders()+" "+mc.renderGlobal.getDebugInfoEntities());
            mc.getFramebuffer().bindFramebuffer(true);
            BufferedImage world=net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            int center=world.getRGB(world.getWidth()/2+20,world.getHeight()/2+20);require((center&255)<160&&((center>>16)&255)<160,"glazing world capture contains sky instead of selected artwork");
            ImageIO.write(world,"png",new File(output,"display-material-"+(glassIndex==0?"glass":"porthole")+"-world.png"));
            if(++glassIndex<2){stage=10;return false;}done=true;System.out.println("[vandorlabs][reprolab] display-material-live PASS trigger-flat-tabs Off-On four-bands front-only static sides real-click-packet-save-reload Duplifier repeated-update static display artwork Glass-Porthole-display-materials screenshots");return true;
        }
        return false;
    }
    private static void assertTrigger(TileEntityProgrammableTrigger tile){require(tile.isLevelStates()&&tile.getExactLevel()==-1&&tile.isFrontOnly()&&tile.getSidesTexture()==0,"analog/front-only settings");for(int i=0;i<4;i++)require(tile.getTexture(i)==choice(i)&&tile.getArtworkMode(i)==MODES[i]&&tile.getArtworkSpeed(i)==SPEEDS[i],"state settings slot="+i);}
    private static HousingTextureList triggerList(GuiProgrammableTrigger gui,int slot)throws Exception{return ((HousingTextureList[])field(gui,"lists"))[slot];}
    private static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    @SuppressWarnings("unchecked") private static List<GuiButton> buttons(GuiScreen gui){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");}
    private static void button(GuiContainer gui,int id)throws Exception{button(gui,id,0);}
    private static void button(GuiContainer gui,int id,int mouse)throws Exception{GuiButton b=buttons(gui).stream().filter(v->v.id==id&&v.visible).findFirst().orElseThrow(()->new IllegalStateException("missing button "+id));if(!b.enabled){require(id>=1&&id<=3||id>=10&&id<=12,"disabled non-playback button "+id);return;}click(gui,b.x+b.width/2,b.y+b.height/2,mouse);}
    private static void click(GuiContainer gui,int x,int y,int mouse)throws Exception{if(gui instanceof GuiProgrammableTrigger)((GuiProgrammableTrigger)gui).mouseClicked(x,y,mouse);else if(gui instanceof GuiProgrammableGlass)((GuiProgrammableGlass)gui).mouseClicked(x,y,mouse);else ((GuiProgrammableWall)gui).mouseClicked(x,y,mouse);}
    @SuppressWarnings("unchecked") private static void pick(GuiContainer gui,HousingTextureList list,int choice)throws Exception{
        Map<Integer,HousingTextureList.Option> options=(Map<Integer,HousingTextureList.Option>)field(list,"options");require(options.containsKey(choice),"display omitted from material list "+choice);
        int old=list.selected();list.setSelected(choice);Field selected=HousingTextureList.class.getDeclaredField("selected");selected.setAccessible(true);selected.setInt(list,old);
        List<Integer> rows=(List<Integer>)field(list,"rows");int row=rows.indexOf(choice);require(row>=0,"selected display folder collapsed");click(gui,(Integer)field(list,"x")+8,(Integer)field(list,"y")+(row-(Integer)field(list,"scroll"))*HousingTextureList.ROW_HEIGHT+8,0);
    }
    private static void layout(Minecraft mc,GuiProgrammableTrigger gui)throws Exception{
        int originalWidth=gui.width,originalHeight=gui.height;
        try{for(int[] size:new int[][]{{320,240},{460,340},{originalWidth,originalHeight}}){gui.setWorldAndResolution(mc,size[0],size[1]);List<GuiButton> buttons=buttons(gui);
            if((Integer)field(gui,"page")!=1){HousingTextureList list=(Integer)field(gui,"page")==2?(HousingTextureList)field(gui,"sidesList"):triggerList(gui,(Integer)field(gui,"textureTab"));int x=(Integer)field(list,"x"),y=(Integer)field(list,"y"),w=(Integer)field(list,"width"),count=(Integer)field(list,"count");require(y+count*HousingTextureList.ROW_HEIGHT+1<=gui.getGuiTop()+gui.getYSize(),"picker outside dialog");for(GuiButton b:buttons)if(b.visible)require(!(x-1<b.x+b.width&&b.x<x+w+9&&y-1<b.y+b.height&&b.y<y+count*HousingTextureList.ROW_HEIGHT+1),"picker overlaps button "+b.id);}
            for(GuiButton a:buttons)if(a.visible){require(a.x>=0&&a.y>=0&&a.x+a.width<=gui.width&&a.y+a.height<=gui.height,"button outside viewport "+a.id);for(GuiButton b:buttons)if(a!=b&&b.visible)require(!(a.x<b.x+b.width&&b.x<a.x+a.width&&a.y<b.y+b.height&&b.y<a.y+a.height),"overlapping buttons "+a.id+"/"+b.id);if(a.id>=90&&a.id<=93||a.id==111)require(a.y==gui.getGuiTop()+30&&!a.displayString.equals("General"),"nested/General trigger tab");}
        }}finally{gui.setWorldAndResolution(mc,originalWidth,originalHeight);}
    }
    private static void capture(Minecraft mc,File output,String name)throws Exception{mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();mc.entityRenderer.disableLightmap();GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);mc.currentScreen.drawScreen(-1,-1,0);ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"display-material-"+name+".png"));}
    private static BufferedImage render(Minecraft mc,TileEntityProgrammableTrigger tile,int signal,long tick){return render(mc,tile,signal,tick,true);}
    private static BufferedImage render(Minecraft mc,TileEntityProgrammableTrigger tile,int signal,long tick,boolean front){
        Framebuffer frame=new Framebuffer(256,256,true);long previous=tile.getWorld().getTotalWorldTime();tile.setChannelLevel(signal);tile.getWorld().setTotalWorldTime(tick);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.15,1.15,-.15,1.15,-4,4);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(.12F,.18F,.24F,1);GlStateManager.clear(16640);GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.enableDepth();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();
            if(front){GlStateManager.translate(1,0,1);GlStateManager.rotate(180,0,1,0);}
            CockpitVisualMotion.authoredFrame(()->{if(tile.hasFastRenderer()){mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);ProgrammableSolidRenderer.emit(tile,0,0,0,buffer);Tessellator.getInstance().draw();}else new TEAnimatedScreenSelector().render(tile,0,0,0,0,-1,1);});GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,frame);
        }finally{tile.getWorld().setTotalWorldTime(previous);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static BufferedImage renderGlazing(Minecraft mc,net.minecraft.tileentity.TileEntity tile){
        Framebuffer frame=new Framebuffer(256,256,true);int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-.15,1.15,-.15,1.15,-4,4);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(.12F,.18F,.24F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableCull();GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();
            net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer renderer=net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.getRenderer(tile);require(renderer!=null,"glazing renderer missing");
            for(int pass=0;pass<2;pass++){net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);renderer.render(tile,0,0,0,0,-1,1);}
            GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(256,256,frame);
        }finally{net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static int difference(BufferedImage a,BufferedImage b){int count=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))count++;return count;}
    private static EntityPlayerMP player(Minecraft mc){return mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());}
    private static void server(Minecraft mc,Runnable action)throws Exception{mc.getIntegratedServer().addScheduledTask(action).get();}
    private static void open(Minecraft mc,int id)throws Exception{server(mc,()->{EntityPlayerMP p=player(mc);p.openGui(com.vandorlabs.VandorLabs.instance,id,p.world,POS.getX(),POS.getY(),POS.getZ());});}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("display materials: "+message);}
}
