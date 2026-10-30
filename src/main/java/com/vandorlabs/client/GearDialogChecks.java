package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModularGear;
import com.vandorlabs.gear.*;
import com.vandorlabs.gear.track.TrackRegistry;
import com.vandorlabs.vehicle.*;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Simultaneous controls are checked at compact resolution through actual mouse and FBO drawing. */
public final class GearDialogChecks {
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Arm dialog: "+why);}
    public static void run(Minecraft mc,File output)throws Exception{
        GearBlock[] blocks={GearRegistry.GEAR_EXTENDER,GearRegistry.GEAR_SWING_ARM,GearRegistry.GEAR_MOUNT,GearRegistry.LARGE_GEAR_EXTENDER,GearRegistry.LARGE_GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_MOUNT,TrackRegistry.COMPACT};
        Field[] fields=new Field[1];String[] names={"channels"};for(int i=0;i<1;i++){fields[i]=GuiModularGear.class.getDeclaredField(names[i]);fields[i].setAccessible(true);}
        for(Field field:GuiModularGear.class.getDeclaredFields())require(!field.getName().equals("extension"),"removed reach editor has no hit target");
        GuiScreen previous=mc.currentScreen;net.minecraft.inventory.Container container=mc.player.openContainer;int cases=0;
        try{for(GearBlock block:blocks)for(int[] size:new int[][]{{320,240},{460,340},{1280,720}}){
            GearTileEntity tile=fixture(mc,block);boolean track=tile instanceof com.vandorlabs.gear.track.TrackTile;net.minecraft.client.gui.inventory.GuiContainer gui=track?new GuiTrack(new ContainerModularGear(tile)):new RecordingGearGui(new ContainerModularGear(tile));gui.inventorySlots.windowId=-94;mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
            List<GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiScreen.class,gui,"buttonList","field_146292_n");List<Rectangle> occupied=new ArrayList<>();boolean rotation=false,done=false;
            for(GuiButton button:buttons){require(button.id!=6&&button.id!=8&&!button.displayString.equals("Back"),"no page-navigation controls");if(!button.visible)continue;require(mc.fontRenderer.getStringWidth(button.displayString)<=button.width-4,"button text fits "+button.displayString);add(occupied,new Rectangle(button.x,button.y,button.width,button.height),size);if(button.id==7)rotation=true;if(button.id==4)done="Done".equals(button.displayString);}
            require(done&&(track?!rotation:rotation),"separate Track appearance and arm motion controls");if(track){require(buttons.size()==2,"Track shows only style and Done");require(((com.vandorlabs.gear.track.TrackTile)tile).error().isEmpty(),"standalone mounted Track has no generic arm assembly error");}else for(Field field:fields){GuiTextField text=(GuiTextField)field.get(gui);if(text.getVisible())add(occupied,new Rectangle(text.x,text.y,text.width,text.height),size);}
            if(!track){
                GuiModularGear armGui=(GuiModularGear)gui;
                require(gui.getYSize()==218,"Mechanism uses compact panel height");
                for(int id:new int[]{1,2,7}){GuiButton row=buttons.stream().filter(b->b.id==id).findFirst().get();require(row.y-gui.getGuiTop()==56+26*(id==1?0:id==2?1:2),"Mechanism rows fill the space immediately below tabs");}
                GuiButton join=buttons.stream().filter(b->b.id==9).findFirst().get(),load=buttons.stream().filter(b->b.id==11).findFirst().get();
                require(join.visible&&load.visible&&!((GuiTextField)fields[0].get(gui)).getVisible(),"mechanism page separates receiver channels from arm geometry");
                if(join.enabled){
                    armGui.mouseClicked(join.x+5,join.y+5,0);require(join.displayString.endsWith("On"),"left click enables joining");
                    armGui.mouseClicked(join.x+5,join.y+5,1);require(join.displayString.endsWith("Off"),"right click disables joining");
                    armGui.mouseClicked(join.x+5,join.y+5,0);
                    sliderEndpoints(mc,armGui,buttons,10);
                    if(load.enabled){armGui.mouseClicked(load.x+5,load.y+5,1);require(load.displayString.endsWith("On"),"right click enables load");sliderEndpoints(mc,armGui,buttons,12);}
                }
                GuiButton angle=buttons.stream().filter(b->b.id==13).findFirst().get();
                GuiButton start=buttons.stream().filter(b->b.id==16).findFirst().get();
                if(angle.visible&&angle.enabled){
                    require(start.x<angle.x,"Start slider is in the left column and End in the right");
                    for(GuiButton endpoint:new GuiButton[]{start,angle}){
                        net.minecraftforge.fml.client.config.GuiSlider slider=(net.minecraftforge.fml.client.config.GuiSlider)endpoint;
                        for(double fraction:new double[]{.13,.37,.53,.71,.89}){
                            int mouse=slider.x+(int)(slider.width*fraction);armGui.mouseClicked(mouse,slider.y+10,0);armGui.mouseReleased(mouse,slider.y+10,0);
                            require(Math.abs(slider.getValue()/15-Math.rint(slider.getValue()/15))<1e-8,"both angle inputs snap to fifteen degrees");
                            require(slider.displayString.equals(slider.dispString+(int)Math.round(slider.getValue())+slider.suffix),"displayed degrees match the actual snapped angle");
                        }
                    }
                    angleDrag(mc,(RecordingGearGui)armGui,tile,(net.minecraftforge.fml.client.config.GuiSlider)start);
                    angleDrag(mc,(RecordingGearGui)armGui,tile,(net.minecraftforge.fml.client.config.GuiSlider)angle);
                    changedRoot((RecordingGearGui)armGui,tile,(net.minecraftforge.fml.client.config.GuiSlider)start,(net.minecraftforge.fml.client.config.GuiSlider)angle);
                }
                occupied.clear();for(GuiButton b:buttons)if(b.visible){require(mc.fontRenderer.getStringWidth(b.displayString)<=b.width-4,"one-page label fits "+b.displayString);add(occupied,new Rectangle(b.x,b.y,b.width,b.height),size);}GuiTextField text=(GuiTextField)fields[0].get(gui);if(text.getVisible())add(occupied,new Rectangle(text.x,text.y,text.width,text.height),size);
                GuiButton redstone=buttons.stream().filter(b->b.id==21).findFirst().get();armGui.mouseClicked(redstone.x+5,redstone.y+5,0);
                require(text.getVisible()&&!join.visible&&!load.visible,"Redstone tab shows channels and hides geometry");
                GuiButton activation=buttons.stream().filter(b->b.id==0).findFirst().get();
                require(activation.x==gui.getGuiLeft()+12&&activation.width==gui.getXSize()-24
                        &&text.x==activation.x&&text.width==activation.width,"Redstone controls use the full content width for "+block.getRegistryName());
                require(activation.enabled!=tile.isFixedMount(),"only fixed arms disable activation");
                require(buttons.stream().filter(b->b.id==4).findFirst().get().y+20<=gui.getGuiTop()+gui.getYSize()-20,"Done clears the two-line status footer");
                require(buttons.stream().noneMatch(b->b.id==15),"input-only arm does not expose a transmitter inventory");
                occupied.clear();for(GuiButton b:buttons)if(b.visible)add(occupied,new Rectangle(b.x,b.y,b.width,b.height),size);add(occupied,new Rectangle(text.x,text.y,text.width,text.height),size);
            }
            GuiOptionCycleRuntimeChecks.check(mc);
            GuiButton doneButton=buttons.stream().filter(b->b.id==4).findFirst().get();require(doneButton.width==200,"native Done remains 200 pixels on the single page");
            Framebuffer frame=new Framebuffer(size[0],size[1],true);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,size[0],size[1],0,-1000,1000);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
            try(TurntableRenderState saved=new TurntableRenderState()){
                frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.disableDepth();GlStateManager.enableTexture2D();GlStateManager.disableLighting();gui.drawScreen(-1,-1,0);org.lwjgl.opengl.GL11.glFinish();java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(size[0],size[1],frame);javax.imageio.ImageIO.write(image,"png",new File(output,"shot_arm_dialog_"+block.getRegistryName().getResourcePath()+"_"+size[0]+".png"));int left=0,right=0;for(int y=gui.getGuiTop()+24;y<gui.getGuiTop()+178;y++)for(int x=gui.getGuiLeft()+12;x<gui.getGuiLeft()+gui.getXSize()-12;x++){int rgb=image.getRGB(x,y)&0xffffff;if(rgb==0xffffff||rgb==0xcdd7e4){if(x<size[0]/2)left++;else right++;}}require(left+right>80,"actual dialog controls contain rendered glyphs");
                buttonPixels(mc,gui,doneButton,frame,size,output,block==GearRegistry.GEAR_EXTENDER||track);
            }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}cases++;
        }}finally{mc.displayGuiScreen(previous);mc.player.openContainer=container;org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
        System.out.println("[vandorlabs][reprolab] arm-platform-dialog PASS cases="+cases+" compact320/460/1280 separated Mechanism/Redstone tabs receiver-channels input-only angle-drag-preview single-release-submit authoritative-refusal native Done normal/hover pixels reverse/forward mouse paths");
    }
    private static final class RecordingGearGui extends GuiModularGear {
        final List<NBTTagCompound> sent=new ArrayList<>();
        RecordingGearGui(ContainerModularGear container){super(container);}
        @Override protected void sendSettings(NBTTagCompound tag){sent.add(tag.copy());}
    }
    /** Native slider click/draw-drag/release, with captured outbound settings and tile acknowledgments. */
    private static void angleDrag(Minecraft mc,RecordingGearGui gui,GearTileEntity tile,net.minecraftforge.fml.client.config.GuiSlider slider)throws Exception{
        NBTTagCompound authority=tile.writeToNBT(new NBTTagCompound());
        int revision=tile.motionRevision(),updates=tile.clientUpdates;tile.handleUpdateTag(authority.copy());require(tile.clientUpdates==updates+1,"chunk update increments transient acknowledgment counter");gui.updateScreen();
        double old=slider.getValue();int count=gui.sent.size();
        gui.mouseClicked(slider.x+slider.width/4,slider.y+10,0);
        for(double fraction:new double[]{.2,.4,.6,.89})slider.drawButton(mc,slider.x+(int)(slider.width*fraction),slider.y+10,0);
        double preview=slider.getValue();require(preview!=old,"drag changes angle preview");
        require(gui.sent.size()==count,"angle dragging sends no intermediate configuration");
        packetAck(tile,authority);gui.updateScreen();
        require(slider.getValue()==preview,"incoming tile acknowledgment preserves an actively dragged preview");
        gui.mouseReleased(slider.x+slider.width-1,slider.y+10,0);
        require(gui.sent.size()==count+1,"angle release sends exactly one final configuration");
        require(gui.sent.get(count).getDouble(slider.id==16?"StartAngle":"ActiveAngle")==preview,"released packet contains final snapped angle");
        NBTTagCompound unchanged=tile.settings();packetAck(tile,authority);gui.updateScreen();
        require(tile.motionRevision()==revision&&tile.settings().equals(unchanged),"same-revision refusal acknowledgment preserves gameplay revision and endpoint fields");
        require(slider.getValue()==old&&slider.displayString.equals(slider.dispString+(int)Math.round(old)+slider.suffix),"refused angle acknowledgment restores authoritative thumb and label");
        authority.setDouble(slider.id==16?"ArmStartAngle":"ArmActiveAngle",preview);authority.setInteger("GearRevision",++revision);packetAck(tile,authority);gui.updateScreen();
        require(slider.getValue()==preview,"accepted angle acknowledgment updates displayed endpoint");
        authority.setDouble(slider.id==16?"ArmStartAngle":"ArmActiveAngle",old);authority.setInteger("GearRevision",++revision);packetAck(tile,authority);gui.updateScreen();
    }
    private static void packetAck(GearTileEntity tile,NBTTagCompound authority){
        int updates=tile.clientUpdates;tile.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(tile.getPos(),0,authority.copy()));
        require(tile.clientUpdates==updates+1,"tile packet increments transient acknowledgment counter");
        GearTileEntity reloaded=new GearTileEntity();reloaded.readFromNBT(tile.writeToNBT(new NBTTagCompound()));
        require(reloaded.clientUpdates==0,"acknowledgment counter is not serialized across saved tile reload");
    }
    /** The same access tile now belongs to a different joined root with the same revision. */
    private static void changedRoot(RecordingGearGui gui,GearTileEntity access,net.minecraftforge.fml.client.config.GuiSlider start,net.minecraftforge.fml.client.config.GuiSlider end){
        VehicleWorld world=(VehicleWorld)access.getWorld();NBTTagCompound original=access.writeToNBT(new NBTTagCompound());BlockPos at=access.getPos().east(2);
        GearTileEntity replacement=new GearTileEntity();replacement.setPos(at);replacement.setWorld(world);
        NBTTagCompound root=original.copy();root.setInteger("x",at.getX());root.setInteger("y",at.getY());root.setInteger("z",at.getZ());root.setDouble("ArmStartAngle",30);root.setDouble("ArmActiveAngle",60);
        net.minecraft.nbt.NBTTagList columns=new net.minecraft.nbt.NBTTagList();columns.appendTag(new net.minecraft.nbt.NBTTagIntArray(new int[]{-2,0,0}));root.setTag("GearColumns",columns);replacement.readFromNBT(root);
        NBTTagCompound member=original.copy();member.setBoolean("GearController",false);member.setLong("GearOwnerOffset",at.subtract(access.getPos()).toLong());
        try{world.tiles.put(at,replacement);access.readFromNBT(member);gui.updateScreen();require(start.getValue()==30&&end.getValue()==60,"joined root identity change reconciles angles even with equal revision");}
        finally{access.readFromNBT(original);world.tiles.remove(at);GearGeometry.remove(replacement);gui.updateScreen();}
    }
    private static void sliderEndpoints(Minecraft mc,GuiModularGear gui,List<GuiButton> buttons,int id)throws Exception{
        net.minecraftforge.fml.client.config.GuiSlider slider=(net.minecraftforge.fml.client.config.GuiSlider)buttons.stream().filter(b->b.id==id).findFirst().get();require(slider.visible&&slider.enabled,"enabled slider shares the page");
        for(int value:new int[]{32,1}){int x=value==32?slider.x+slider.width-1:slider.x+1;gui.mouseClicked(x,slider.y+10,0);slider.mouseReleased(x,slider.y+10);require(slider.getValueInt()==value,"slider selects endpoint "+value);}
    }
    private static void buttonPixels(Minecraft mc,net.minecraft.client.gui.inventory.GuiContainer gui,GuiButton done,Framebuffer frame,int[] size,File output,boolean capture)throws Exception{
        java.awt.image.BufferedImage previous=null;
        for(boolean hover:new boolean[]{false,true}){
            int mouseX=hover?done.x+done.width/2:-1,mouseY=hover?done.y+4:-1;
            frame.bindFramebuffer(true);GlStateManager.clear(16640);GlStateManager.disableDepth();GlStateManager.disableLighting();gui.drawScreen(mouseX,mouseY,0);org.lwjgl.opengl.GL11.glFinish();java.awt.image.BufferedImage actual=net.minecraft.util.ScreenShotHelper.createScreenshot(size[0],size[1],frame);
            frame.bindFramebuffer(true);GlStateManager.clear(16640);GlStateManager.disableDepth();GlStateManager.disableLighting();new GuiButton(4,done.x,done.y,done.width,20,"Done").drawButton(mc,mouseX,mouseY,0);org.lwjgl.opengl.GL11.glFinish();java.awt.image.BufferedImage expected=net.minecraft.util.ScreenShotHelper.createScreenshot(size[0],size[1],frame);
            int changed=0;
            for(int y=done.y+2;y<done.y+18;y++)for(int x=done.x+2;x<done.x+done.width-2;x++){
                require(actual.getRGB(x,y)==expected.getRGB(x,y),"actual Done matches native button pixels hover="+hover+" viewport="+size[0]+" x="+x+" y="+y);
                if(previous!=null&&actual.getRGB(x,y)!=previous.getRGB(x,y))changed++;
            }
            if(hover)require(changed>200,"hover visibly changes native Done texture");previous=actual;
            if(capture)javax.imageio.ImageIO.write(actual,"png",new File(output,"shot_"+(gui instanceof GuiTrack?"track":"arm")+"_native_done_"+size[0]+(hover?"_hover":"_normal")+".png"));
        }
    }
    private static GearTileEntity fixture(Minecraft mc,GearBlock block){
        net.minecraft.block.state.IBlockState state=block.getDefaultState();GearTileEntity fresh=(GearTileEntity)block.createTileEntity(mc.world,state);fresh.setPos(BlockPos.ORIGIN);NBTTagCompound data=fresh.writeToNBT(new NBTTagCompound());boolean track=block instanceof com.vandorlabs.gear.track.TrackBlock;if(track){data.setBoolean("Complete",true);data.setTag("Appearance",new com.vandorlabs.gear.track.TrackAppearance(0,com.vandorlabs.gear.track.TrackAppearance.Mount.NONE,1,0).write());}
        if(!track){data.setBoolean("GearController",true);data.setInteger("GearHeight",1);data.setString("GearKind","NONE");data.setString("GearMode",block.kind()==GearBlock.Kind.MOUNT?"STATIC":block.kind()==GearBlock.Kind.SWING_ARM?"ROTATE":"EXTEND");data.setBoolean("GearTargetDeployed",true);data.setDouble("GearProgress",1);data.setBoolean("GearOperational",true);data.setDouble("ArmActiveExtension",1);net.minecraft.nbt.NBTTagList feet=new net.minecraft.nbt.NBTTagList();feet.appendTag(new net.minecraft.nbt.NBTTagIntArray(new int[]{0,-1,0}));data.setTag("GearFeet",feet);}
        VehicleStructure snapshot=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,state,data)));GearTileEntity result=(GearTileEntity)new VehicleWorld(mc.world,snapshot).getTileEntity(BlockPos.ORIGIN);if(!track)require(result.isOperational(),"GUI fixture has operational arm assembly");return result;
    }
    private static void add(List<Rectangle> occupied,Rectangle next,int[] size){require(next.x>=0&&next.y>=0&&next.x+next.width<=size[0]&&next.y+next.height<=size[1],"control fits viewport "+next);for(Rectangle prior:occupied)require(!prior.intersects(next),"controls do not overlap");occupied.add(next);}
    private GearDialogChecks(){}
}
