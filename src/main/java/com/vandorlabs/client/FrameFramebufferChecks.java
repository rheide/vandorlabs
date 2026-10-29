package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;

/** Actual framebuffer comparisons for independent frame and display layouts. */
final class FrameFramebufferChecks {
    private static final int SIZE=256,BACKGROUND=0x203040;
    private static final class Fixture {
        final VehicleWorld world;final int columns,rows;
        final List<TileEntityAnimatedScreenSelector> tiles=new ArrayList<>();
        Fixture(Minecraft mc,int columns,int rows,int material){
            this.columns=columns;this.rows=rows;List<VehicleStructure.Cell> cells=new ArrayList<>();
            for(int y=0;y<rows;y++)for(int x=0;x<columns;x++){
                TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setPos(new BlockPos(x,y,0));tile.setSurfaceTexture(0,material);tile.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);tile.setRedstoneEnabled(false);
                cells.add(new VehicleStructure.Cell(tile.getPos(),ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.SOUTH),tile.writeToNBT(new NBTTagCompound())));
            }
            world=new VehicleWorld(mc.world,VehicleStructure.display(cells));
            for(net.minecraft.tileentity.TileEntity tile:world.tiles.values())tiles.add((TileEntityAnimatedScreenSelector)tile);
        }
        void appearance(int front,boolean tile,boolean fit){for(TileEntityAnimatedScreenSelector t:tiles){t.frameAppearance.front=front;t.frameAppearance.tile=tile;t.frameAppearance.displayFit=fit;}GlassMaterials.changed();}
        void close(){TEAnimatedScreenSelector.unloadGroups(world);}
    }
    static void run(Minecraft mc,File output)throws Exception{
        int pattern=ScreenHousingTextures.screenIndex("ceiling/pale_grey/plain"),dark=ScreenHousingTextures.screenIndex("ceiling/dark_cyan_metallic/plain"),bolted=ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_bolted_1254");
        require(pattern>0&&dark>0&&bolted>0,"fixture materials");
        Fixture one=new Fixture(mc,1,1,pattern),square=new Fixture(mc,2,2,pattern),rectangle=new Fixture(mc,2,3,pattern),row=new Fixture(mc,3,1,pattern);
        try{
            BufferedImage single=capture(mc,one),tile=capture(mc,square);Fixture visibilityControl=new Fixture(mc,1,1,dark);try{require(difference(single,capture(mc,visibilityControl),8,8,248,248)>10000,"selected display textures are hidden by housing or the camera sees the rear");}finally{visibilityControl.close();}square.appearance(-1,false,true);GlassSurfaceGroups.Layout group=ScreenSurfaceGroups.layout(square.tiles.get(0));require(group.columns==2&&group.rows==2,"2x2 grouping failed: "+group.key()+" tiles="+square.tiles.size()+" positions="+square.world.tiles.keySet());BufferedImage fit=capture(mc,square);ImageIO.write(single,"png",new File(output,"shot_frames_reference.png"));ImageIO.write(tile,"png",new File(output,"shot_frames_display_tile_2x2.png"));ImageIO.write(fit,"png",new File(output,"shot_frames_display_fit_2x2.png"));
            require(difference(single,fit,5,5,251,251)<180,"2x2 display Fit does not match one continuous image");
            require(difference(tile,fit,5,5,251,251)>300,"2x2 display Fit did not change repeated artwork");
            for(Fixture rectangular:new Fixture[]{rectangle,row}){BufferedImage before=capture(mc,rectangular);rectangular.appearance(-1,false,true);BufferedImage after=capture(mc,rectangular);require(difference(before,after,4,4,252,252)==0,"non-square display Fit stretched a rectangular group");}
            square.appearance(-1,false,false);BufferedImage bare=capture(mc,square);square.appearance(bolted,true,false);BufferedImage repeated=capture(mc,square);square.appearance(bolted,false,false);BufferedImage perimeter=capture(mc,square);ImageIO.write(perimeter,"png",new File(output,"shot_frames_border_fit_2x2.png"));ImageIO.write(repeated,"png",new File(output,"shot_frames_border_tile_2x2.png"));
            int repeatedSeam=difference(bare,repeated,120,24,136,232),fitSeam=difference(bare,perimeter,120,24,136,232);
            require(repeatedSeam>100&&fitSeam<repeatedSeam/3,"frame Fit retained an interior vertical rail");
            require(difference(bare,perimeter,0,0,256,256)>200,"fitted outer border was not rendered");
            int background=ScreenHousingTextures.screenIndex("frames/frames-door-window/vandor_bg");require(background>0&&!FrameAppearance.border(background),"opaque frame material fixture");one.appearance(background,true,false);BufferedImage backgroundFrame=capture(mc,one);ImageIO.write(backgroundFrame,"png",new File(output,"shot_frames_background_material.png"));require(distance(single.getRGB(128,128),backgroundFrame.getRGB(128,128))<30,"opaque frame material covered display center");require(FrameSurfaceRenderer.content(one.tiles.get(0),com.vandorlabs.render.ScreenSurface.quad(com.vandorlabs.render.ScreenSurface.Kind.FLAT,false)).topLeft.x>0,"frame thickness does not reserve display space");require(difference(single,backgroundFrame,0,0,256,256)>200,"opaque frame material did not paint border");one.appearance(-1,false,false);
            TileEntityAnimatedScreenSelector split=one.tiles.get(0);split.frameAppearance.split=true;split.frameAppearance.splitTop=pattern;split.frameAppearance.splitBottom=dark;
            BufferedImage splitImage=capture(mc,one);ImageIO.write(splitImage,"png",new File(output,"shot_frames_split_display.png"));Fixture darkReference=new Fixture(mc,1,1,dark);
            try{BufferedImage darkImage=capture(mc,darkReference);require(distance(splitImage.getRGB(128,64),single.getRGB(128,128))<30,"split upper display differs from selected artwork");require(distance(splitImage.getRGB(128,192),darkImage.getRGB(128,128))<30,"split lower display differs from selected artwork");require(distance(splitImage.getRGB(128,64),splitImage.getRGB(128,192))>30,"independent split artwork is indistinguishable");}finally{darkReference.close();}
            ImageIO.write(fit,"png",new File(output,"shot_frames_display_fit_2x2.png"));ImageIO.write(perimeter,"png",new File(output,"shot_frames_border_fit_2x2.png"));ImageIO.write(repeated,"png",new File(output,"shot_frames_border_tile_2x2.png"));ImageIO.write(splitImage,"png",new File(output,"shot_frames_split_display.png"));
            coupledLayouts(mc,output,pattern,dark,bolted,splitImage);
            framePersistence(mc);companyDisplays(mc,output);opaqueInterior(mc,output);frameDialog(mc,output,one.tiles.get(0));screenRedstoneDialog(mc,output,one.tiles.get(0));pickerDialogs(mc,output);
            System.out.println("[vandorlabs][reprolab] frame-framebuffer PASS square-display-fit rectangular-rejection independent-frame-fit mixed-artwork-and-frame-negative-controls split-fit-2x2-and-3x3 opaque-interior");
        }finally{one.close();square.close();rectangle.close();row.close();}
    }
    private static void framePersistence(Minecraft mc)throws Exception{
        net.minecraft.client.gui.GuiScreen previous=mc.currentScreen;net.minecraft.inventory.Container old=mc.player.openContainer;
        Fixture f=new Fixture(mc,1,1,0);int selected=ScreenHousingTextures.screenIndex("frames/company/vektor_full");
        try{
            TileEntityAnimatedScreenSelector tile=f.tiles.get(0);tile.frameAppearance.front=selected;
            GuiAnimatedScreenSelector gui=new GuiAnimatedScreenSelector(mc.player.inventory,tile);gui.inventorySlots.windowId=-95;mc.displayGuiScreen(gui);gui.actionPerformed(screenButton(gui,197));clickButton(gui,3008,0);
            require(!tile.frameAppearance.hasFront()&&tile.frameAppearance.front==selected,"Bare discarded selected frame finish");
            TileEntityAnimatedScreenSelector loaded=new TileEntityAnimatedScreenSelector();loaded.readFromNBT(tile.writeToNBT(new NBTTagCompound()));loaded.setWorld(f.world);loaded.setPos(tile.getPos());
            require(loaded.frameAppearance.bare&&loaded.frameAppearance.front==selected,"Bare finish lost through save/reload");
            GuiAnimatedScreenSelector reopened=new GuiAnimatedScreenSelector(mc.player.inventory,loaded);reopened.inventorySlots.windowId=-94;mc.displayGuiScreen(reopened);reopened.actionPerformed(screenButton(reopened,197));clickButton(reopened,3008,0);
            require(loaded.frameAppearance.hasFront()&&loaded.frameAppearance.front==selected,"reopened Framed button failed to restore saved finish");
        }finally{mc.displayGuiScreen(previous);mc.player.openContainer=old;f.close();}
    }
    private static void companyDisplays(Minecraft mc,File output)throws Exception{
        int content=ScreenHousingTextures.screenIndex("company_vektor_full_communications_static"),border=ScreenHousingTextures.screenIndex("frames/company/vektor_full");
        int half=ScreenHousingTextures.screenIndex("console_inputs/company_vektor_half_communications_static");
        require(content>0&&border>0&&half>0,"exact supplied Vektor artwork fixtures");
        for(int n:new int[]{1,2}){
            Fixture f=new Fixture(mc,n,n,content);
            try{
                f.appearance(border,false,true);BufferedImage fitted=capture(mc,f);
                BufferedImage empty;TEAnimatedScreenSelector.suppressDisplayContent=true;try{empty=capture(mc,f);}finally{TEAnimatedScreenSelector.suppressDisplayContent=false;}
                double[] thickness=ScreenFrameBorders.get(border);
                for(int edge=0;edge<4;edge++){
                    int at=edge<2?(edge==0?(int)Math.ceil(thickness[0]*SIZE/n)+1:SIZE-(int)Math.ceil(thickness[1]*SIZE/n)-2):(edge==2?(int)Math.ceil(thickness[2]*SIZE/n)+1:SIZE-(int)Math.ceil(thickness[3]*SIZE/n)-2);
                    int covered=0;
                    for(int along=SIZE/2-16;along<SIZE/2+16;along++){
                        int x=edge<2?at:along,y=edge<2?along:at;
                        if(distance(fitted.getRGB(x,y),empty.getRGB(x,y))>6)covered++;
                    }
                    require(covered>=24,"transparent registration margin remains between content and "+edge+" frame edge on "+n+"x"+n+": covered="+covered);
                }
                ImageIO.write(empty,"png",new File(output,"shot_frames_vektor_empty_negative_"+n+"x"+n+".png"));ImageIO.write(fitted,"png",new File(output,"shot_frames_vektor_communications_"+n+"x"+n+".png"));
                for(TileEntityAnimatedScreenSelector t:f.tiles){t.frameAppearance.split=true;t.frameAppearance.splitTop=half;t.frameAppearance.splitBottom=half;t.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_ANIMATED);}
                BufferedImage a=captureAt(mc,f,0),b=captureAt(mc,f,4);
                require(difference(a,b,24,24,232,116)>20,"split TOP animation frozen on "+n+"x"+n);
                require(difference(a,b,24,140,232,232)>20,"split BOTTOM animation frozen on "+n+"x"+n);
                ImageIO.write(a,"png",new File(output,"shot_frames_split_animated_"+n+"x"+n+"_0.png"));ImageIO.write(b,"png",new File(output,"shot_frames_split_animated_"+n+"x"+n+"_1.png"));
                for(TileEntityAnimatedScreenSelector t:f.tiles)t.setDisplayMode(TileEntityAnimatedScreenSelector.MODE_STATIC);
                require(difference(captureAt(mc,f,0),captureAt(mc,f,4),24,24,232,232)==0,"static split animation negative control advanced");
            }finally{f.close();}
        }
        System.out.println("[vandorlabs][reprolab] company-display-framebuffer PASS exact-vektor-communications single-2x2 seamless-source-crop split-top-bottom-animation static-negative-control");
    }
    private static BufferedImage captureAt(Minecraft mc,Fixture f,long tick){
        long previous=mc.world.getTotalWorldTime();final BufferedImage[] result=new BufferedImage[1];
        try{mc.world.setTotalWorldTime(tick);CockpitVisualMotion.authoredFrame(()->result[0]=capture(mc,f));return result[0];}
        finally{mc.world.setTotalWorldTime(previous);}
    }
    private static void coupledLayouts(Minecraft mc,File output,int pattern,int dark,int bolted,BufferedImage splitReference)throws Exception{
        for(int n:new int[]{2,3}){
            Fixture f=new Fixture(mc,n,n,pattern);
            try{
                f.appearance(-1,false,true);for(TileEntityAnimatedScreenSelector t:f.tiles){t.frameAppearance.split=true;t.frameAppearance.splitTop=pattern;t.frameAppearance.splitBottom=dark;}GlassMaterials.changed();
                BufferedImage splitFit=capture(mc,f);ImageIO.write(splitFit,"png",new File(output,"shot_frames_split_fit_"+n+"x"+n+".png"));require(difference(splitReference,splitFit,8,8,248,248)<400,"split+Fit did not span the entire "+n+"x"+n+" display");
                if(n==2){
                    int background=ScreenHousingTextures.screenIndex("frames/frames-door-window/vandor_bg");for(int k=0;k<f.tiles.size();k++){f.tiles.get(k).frameAppearance.front=(k&1)==0?bolted:background;f.tiles.get(k).frameAppearance.tile=(k&1)==0;}GlassMaterials.changed();
                    GlassSurfaceGroups.Layout display=ScreenSurfaceGroups.layout(f.tiles.get(0));require(display.columns==2&&display.rows==2,"display Fit depended on frame finish");BufferedImage mixed=capture(mc,f);int changed=0;for(int row=0;row<2;row++)for(int col=0;col<2;col++)changed+=difference(splitReference,mixed,col*128+20,row*128+20,col*128+108,row*128+108);require(changed>0,"mixed frame finishes did not reserve their separate display insets");ImageIO.write(mixed,"png",new File(output,"shot_frames_mixed_frame_split_fit.png"));
                    for(TileEntityAnimatedScreenSelector t:f.tiles)t.frameAppearance.front=-1;f.tiles.get(0).frameAppearance.splitTop=dark;GlassMaterials.changed();BufferedImage unmatched=capture(mc,f);for(TileEntityAnimatedScreenSelector t:f.tiles)t.frameAppearance.displayFit=false;GlassMaterials.changed();BufferedImage tiled=capture(mc,f);require(difference(unmatched,tiled,4,4,252,252)==0,"unequal split artwork joined one fitted display");
                }
            }finally{f.close();}
        }
        Fixture frame=new Fixture(mc,2,2,pattern);
        try{
            frame.appearance(-1,false,false);frame.tiles.get(0).setSurfaceTexture(0,dark);frame.tiles.get(1).frameAppearance.split=true;frame.tiles.get(1).frameAppearance.splitTop=dark;frame.tiles.get(1).frameAppearance.splitBottom=pattern;frame.tiles.get(2).frameAppearance.displayFit=true;frame.tiles.get(3).setDisplayMode(TileEntityAnimatedScreenSelector.MODE_OFF);frame.tiles.get(3).setAnimationSpeedIndex(2);GlassMaterials.changed();BufferedImage bare=capture(mc,frame);
            for(TileEntityAnimatedScreenSelector t:frame.tiles)t.frameAppearance.front=bolted;GlassMaterials.changed();GlassSurfaceGroups.Layout layout=ScreenSurfaceGroups.frameLayout(frame.tiles.get(0));require(layout.columns==2&&layout.rows==2,"frame Fit depended on artwork, split, playback or Display Fit");BufferedImage fitted=capture(mc,frame);FrameSurfaceRenderer.suppressBorders=true;try{bare=capture(mc,frame);}finally{FrameSurfaceRenderer.suppressBorders=false;}require(difference(bare,fitted,120,24,136,232)<30,"mixed display settings left an interior fitted frame rail");require(difference(bare,fitted,0,0,256,256)>200,"mixed display settings lost fitted outer frame");ImageIO.write(fitted,"png",new File(output,"shot_frames_mixed_displays_frame_fit.png"));
        }finally{frame.close();}
    }
    private static void screenRedstoneDialog(Minecraft mc,File output,TileEntityAnimatedScreenSelector tile)throws Exception{
        net.minecraft.client.gui.GuiScreen previous=mc.currentScreen;net.minecraft.inventory.Container container=mc.player.openContainer;
        try{for(int[] size:new int[][]{{320,240},{460,340}}){
            GuiAnimatedScreenSelector gui=new GuiAnimatedScreenSelector(mc.player.inventory,tile);gui.inventorySlots.windowId=-97;mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
            require(!screenButton(gui,90).enabled,"new screen dialog did not open its first tab");for(int id=90;id<=92;id++){net.minecraft.client.gui.GuiButton tab=screenButton(gui,id);require(mc.fontRenderer.getStringWidth(tab.displayString)<=tab.width-6,"screen tab caption clipped");}require(screenButton(gui,90).displayString.equals("Display")&&screenButton(gui,92).displayString.equals("Redstone"),"screen tab labels were shortened");gui.actionPerformed(screenButton(gui,92));
            net.minecraft.client.gui.GuiTextField receive=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(GuiAnimatedScreenSelector.class,gui,"channelField");
            require(receive.getVisible()&&receive.width==Math.min(400,gui.getXSize()-24)&&receive.x==gui.getGuiLeft()+(gui.getXSize()-receive.width)/2,"Redstone receive field retained empty texture-list column");
            net.minecraft.client.gui.GuiButton trigger=screenButton(gui,0);require(trigger.x==receive.x&&trigger.width==receive.width&&trigger.y>=receive.y+receive.height+8,"screen receive controls overlap");
            for(net.minecraft.client.gui.GuiButton button:dialogButtons(gui))require(!button.visible||!button.displayString.contains("Transmitter"),"receiver-only screen exposes transmission controls");
            renderDialog(mc,gui,new File(output,"shot_shared_redstone_"+size[0]+".png"),-1,-1);
            require(InlineTransmitterWidget.channelsField(gui)==null,"receiver-only screen gained transmitter editor");
            net.minecraft.inventory.Container sameWindow=gui.inventorySlots;gui.actionPerformed(screenButton(gui,197));require(mc.currentScreen==gui&&gui.inventorySlots==sameWindow&&!screenButton(gui,197).enabled,"Frame opened a child dialog or changed the window");gui.actionPerformed(screenButton(gui,92));require(mc.currentScreen==gui&&!screenButton(gui,92).enabled,"Frame return did not retain the parent Redstone tab");

        }}finally{mc.displayGuiScreen(previous);mc.player.openContainer=container;}
        System.out.println("[vandorlabs][reprolab] shared-dialog-feedback PASS compact320 regular460 first-tab new-open inline-frame fullwidth receive-trigger receiver-only-no-transmitter");
    }
    private static void pickerDialogs(Minecraft mc,File output)throws Exception{
        net.minecraft.client.gui.GuiScreen previous=mc.currentScreen;net.minecraft.inventory.Container old=mc.player.openContainer;
        net.minecraft.block.Block glass=net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_glass");
        net.minecraft.block.Block[] blocks={ModBlocks.ANIMATED_SCREEN_SELECTOR,ModBlocks.PROGRAMMABLE_CONSOLE,ModBlocks.PROGRAMMABLE_FULL_INPUT,ModBlocks.PROGRAMMABLE_DIAGONAL_SCREEN,ModBlocks.PROGRAMMABLE_INPUT,ModBlocks.PROGRAMMABLE_HALF_CONSOLE,glass,net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_door"),ModBlocks.PROGRAMMABLE_WALL,ModBlocks.PROGRAMMABLE_DIAGONAL_WALL,ModBlocks.PROGRAMMABLE_PORTHOLE_WALL,ModBlocks.PROGRAMMABLE_LIGHT,net.minecraft.block.Block.getBlockFromName("vandorlabs:redstone_transmitter_block"),net.minecraft.block.Block.getBlockFromName("vandorlabs:redstone_receiver"),net.minecraft.block.Block.getBlockFromName("vandorlabs:access_card_slot"),net.minecraft.block.Block.getBlockFromName("vandorlabs:rocket_thruster_hexagonal")};
        String[] names={"screen","console","touch_panel","diagonal_screen","input","half_console","glass","door","wall","diagonal_wall","porthole","light","wireless_tx","wireless_rx","token_slot","thruster"};int captures=0;
        try{for(int type=0;type<blocks.length;type++){
            net.minecraft.tileentity.TileEntity seed=blocks[type].createTileEntity(mc.world,blocks[type].getDefaultState());VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,blocks[type].getDefaultState(),seed.writeToNBT(new NBTTagCompound())))));
            try{for(int[] size:new int[][]{{320,240},{460,340}}){
                net.minecraft.tileentity.TileEntity tile=view.getTileEntity(BlockPos.ORIGIN);net.minecraft.client.gui.inventory.GuiContainer gui=type==11?new GuiProgrammableLight(mc.player.inventory,(TileEntityProgrammableLight)tile):type==12||type==13?new GuiWirelessRedstone(new com.vandorlabs.container.ContainerWirelessRedstone((TileEntityWirelessRedstone)tile)):type==14?new GuiTokenSlot(new com.vandorlabs.container.ContainerTokenSlot(mc.player.inventory,(TileEntityTokenSlot)tile)):type==15?new GuiRedstoneChannel((com.vandorlabs.redstone.RedstoneChannelMember)tile):type==7?new GuiSpaceDoor((TileEntitySpaceDoor)tile):type>=8?new GuiProgrammableWall(mc.player.inventory,(TileEntityAnimatedScreenSelector)tile):type==6?new GuiProgrammableGlass((TileEntityProgrammableGlass)tile):type==4?new GuiProgrammableInput(mc.player.inventory,(TileEntityAnimatedScreenSelector)tile):type==5?new GuiProgrammableHalfConsole(mc.player.inventory,(TileEntityAnimatedScreenSelector)tile):new GuiAnimatedScreenSelector(mc.player.inventory,(TileEntityAnimatedScreenSelector)tile);
                gui.inventorySlots.windowId=-200-type;mc.displayGuiScreen(gui);gui.setWorldAndResolution(mc,size[0],size[1]);
                for(net.minecraft.client.gui.GuiButton tab:dialogButtons(gui))if(tab instanceof DialogTabButton&&tab.displayString.equals("Redstone"))for(net.minecraft.client.gui.GuiButton other:dialogButtons(gui))if(other instanceof DialogTabButton&&other.y==tab.y)require(other.x<=tab.x,"Redstone must be the last tab: "+names[type]);
                net.minecraft.inventory.Container window=gui.inventorySlots;java.util.List<Integer> pages=new ArrayList<>();for(net.minecraft.client.gui.GuiButton b:dialogButtons(gui))if(b instanceof DialogTabButton&&(b.id<120||b.id>=190&&b.id<=197))pages.add(b.id);
                for(int page:pages){dialogAction(gui,dialogButton(gui,page));require(mc.currentScreen==gui&&gui.inventorySlots==window,"picker tab replaced the parent screen/window");dialogBounds(mc,gui);renderDialog(mc,gui,new File(output,"shot_picker_"+names[type]+"_"+page+"_"+size[0]+".png"),-1,-1);captures++;
                    if(page==195){java.util.List<Integer> faces=new ArrayList<>();for(net.minecraft.client.gui.GuiButton b:dialogButtons(gui))if(b instanceof DialogTabButton&&b.id>=120&&b.id<=129)faces.add(b.id);for(int face:faces){dialogAction(gui,dialogButton(gui,face));dialogBounds(mc,gui);renderDialog(mc,gui,new File(output,"shot_picker_"+names[type]+"_surface_"+face+"_"+size[0]+".png"),-1,-1);captures++;}}
                    if((page==197||page==191)&&type==6){
                        TileEntityProgrammableGlass glassTile=(TileEntityProgrammableGlass)tile;boolean before=glassTile.isFramed();
                        net.minecraft.client.gui.GuiButton toggle=dialogButton(gui,3008);
                        java.lang.reflect.Method click=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.gui.GuiScreen.class,"mouseClicked","func_73864_a",int.class,int.class,int.class);
                        click.invoke(gui,toggle.x+toggle.width/2,toggle.y+toggle.height/2,0);require(glassTile.isFramed()!=before,"first real Glass Framed/Bare click ignored");
                        toggle=dialogButton(gui,3008);click.invoke(gui,toggle.x+toggle.width/2,toggle.y+toggle.height/2,0);require(glassTile.isFramed()==before,"second real Glass toggle did not restore frame");
                    }
                    if(page==197||page==191){java.util.List<Integer> inner=new ArrayList<>();for(net.minecraft.client.gui.GuiButton b:dialogButtons(gui))if(b instanceof DialogTabButton&&b.id>=3000)inner.add(b.id);for(int sub:inner){dialogAction(gui,dialogButton(gui,sub));require(mc.currentScreen==gui,"frame subtab opened child screen");dialogBounds(mc,gui);renderDialog(mc,gui,new File(output,"shot_picker_"+names[type]+"_frame_"+sub+"_"+size[0]+".png"),-1,-1);captures++;}}
                }
                if(type<6){dialogAction(gui,dialogButton(gui,90));java.lang.reflect.Method rows=gui.getClass().getDeclaredMethod("openRows",int.class);rows.setAccessible(true);rows.invoke(gui,0);require(mc.currentScreen==gui&&gui.inventorySlots==window,"Redstone artwork opened a child window");dialogAction(gui,dialogButton(gui,600));dialogBounds(mc,gui);renderDialog(mc,gui,new File(output,"shot_picker_"+names[type]+"_rows_"+size[0]+".png"),-1,-1);captures++;}
            }}finally{TEAnimatedScreenSelector.unloadGroups(view);}
        }}finally{mc.displayGuiScreen(previous);mc.player.openContainer=old;}
        System.out.println("[vandorlabs][reprolab] picker-inline-dialogs PASS paths=16 compact320 regular460 persistent-tabs no-child-Frame-or-Redstone actual-framebuffers="+captures);
    }
    private static java.util.List<net.minecraft.client.gui.GuiButton> dialogButtons(net.minecraft.client.gui.GuiScreen gui){return net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");}
    private static net.minecraft.client.gui.GuiButton dialogButton(net.minecraft.client.gui.GuiScreen gui,int id){for(net.minecraft.client.gui.GuiButton b:dialogButtons(gui))if(b.id==id)return b;throw new IllegalStateException("Missing dialog button "+id);}
    private static void clickButton(net.minecraft.client.gui.GuiScreen gui,int id,int mouse)throws Exception{
        net.minecraft.client.gui.GuiButton b=dialogButton(gui,id);
        net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.gui.GuiScreen.class,"mouseClicked","func_73864_a",int.class,int.class,int.class).invoke(gui,b.x+b.width/2,b.y+b.height/2,mouse);
        net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.gui.GuiScreen.class,"mouseReleased","func_146286_b",int.class,int.class,int.class).invoke(gui,b.x+b.width/2,b.y+b.height/2,mouse);
    }
    private static void dialogAction(net.minecraft.client.gui.GuiScreen gui,net.minecraft.client.gui.GuiButton button)throws Exception{net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.gui.GuiScreen.class,"actionPerformed","func_146284_a",net.minecraft.client.gui.GuiButton.class).invoke(gui,button);}
    private static void dialogBounds(Minecraft mc,net.minecraft.client.gui.GuiScreen gui){for(net.minecraft.client.gui.GuiButton a:dialogButtons(gui))if(a.visible){require(a.x>=0&&a.y>=0&&a.x+a.width<=gui.width&&a.y+a.height<=gui.height,"picker control outside viewport: "+a.id);if(a instanceof DialogTabButton)require(mc.fontRenderer.getStringWidth(a.displayString)<=a.width-6,"picker tab caption clipped: "+a.displayString);for(net.minecraft.client.gui.GuiButton b:dialogButtons(gui))if(a!=b&&b.visible)require(a.x>=b.x+b.width||b.x>=a.x+a.width||a.y>=b.y+b.height||b.y>=a.y+a.height,"picker buttons overlap: "+a.id+"/"+b.id);}}
    private static BufferedImage renderDialog(Minecraft mc,net.minecraft.client.gui.inventory.GuiContainer gui,File target,int mouseX,int mouseY)throws Exception{
        Framebuffer frame=new Framebuffer(gui.width,gui.height,true);guiProjection(gui.width,gui.height);
        try(TurntableRenderState saved=new TurntableRenderState()){prepare(mc,frame);GlStateManager.disableDepth();InlineTransmitterWidget.beforeDraw(new net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre(gui,mouseX,mouseY,0));gui.drawScreen(mouseX,mouseY,0);InlineTransmitterWidget.afterDraw(new net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post(gui,mouseX,mouseY,0));net.minecraft.client.gui.GuiTextField module=InlineTransmitterWidget.channelsField(gui);if(module!=null&&module.getVisible())require(module.x>=gui.getGuiLeft()&&module.x+module.width<=gui.getGuiLeft()+gui.getXSize()&&module.y+module.height<=gui.getGuiTop()+gui.getYSize()-52,"inline module field overlaps hotbar or leaves the dialog: "+gui.getClass().getSimpleName());GL11.glFinish();BufferedImage result=net.minecraft.util.ScreenShotHelper.createScreenshot(gui.width,gui.height,frame);ImageIO.write(result,"png",target);return result;}finally{restore(mc,frame);}
    }
    private static net.minecraft.client.gui.GuiButton screenButton(GuiAnimatedScreenSelector gui,int id){java.util.List<net.minecraft.client.gui.GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");for(net.minecraft.client.gui.GuiButton b:buttons)if(b.id==id)return b;throw new IllegalStateException("Missing screen button "+id);}
    private static void frameDialog(Minecraft mc,File output,TileEntityAnimatedScreenSelector tile)throws Exception{
        net.minecraft.client.gui.GuiScreen previous=mc.currentScreen;net.minecraft.inventory.Container container=mc.player.openContainer;
        GuiAnimatedScreenSelector gui=new GuiAnimatedScreenSelector(mc.player.inventory,tile);gui.inventorySlots.windowId=-98;
        try{mc.displayGuiScreen(gui);for(int[] size:new int[][]{{320,240},{460,340}}){gui.setWorldAndResolution(mc,size[0],size[1]);
            gui.actionPerformed(screenButton(gui,197));require(mc.currentScreen==gui,"Frame replaced the parent dialog");
            boolean wasFramed=tile.frameAppearance.hasFront();clickButton(gui,3008,0);require(tile.frameAppearance.hasFront()!=wasFramed,"first Framed/Bare click ignored");clickButton(gui,3008,1);require(tile.frameAppearance.hasFront()==wasFramed,"second Framed/Bare click did not restore selection");boolean beforeTile=tile.frameAppearance.tile;clickButton(gui,3003,0);require(tile.frameAppearance.tile!=beforeTile,"first Frame Texture click applies");tile.frameAppearance.tile=beforeTile;clickButton(gui,3003,1);require(tile.frameAppearance.tile==beforeTile,"stale tile acknowledgement must not consume the second Frame Texture click");for(int page:new int[]{197,90,3005,3006}){if(page==90){gui.actionPerformed(screenButton(gui,90));if(!tile.frameAppearance.split){clickButton(gui,3007,0);require(tile.frameAppearance.split,"first real Split display click ignored");}}else clickButton(gui,page,0);java.util.List<net.minecraft.client.gui.GuiButton> buttons=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.client.gui.GuiScreen.class,gui,"buttonList","field_146292_n");
                for(net.minecraft.client.gui.GuiButton a:buttons)if(a.visible){require(a.x>=0&&a.y>=0&&a.x+a.width<=size[0]&&a.y+a.height<=size[1],"Frame page control outside viewport");for(net.minecraft.client.gui.GuiButton b:buttons)if(a!=b&&b.visible)require(a.x>=b.x+b.width||b.x>=a.x+a.width||a.y>=b.y+b.height||b.y>=a.y+a.height,"Frame page controls overlap");}
                GuiOptionCycleRuntimeChecks.check(mc);
            }
            Framebuffer frame=new Framebuffer(size[0],size[1],true);guiProjection(size[0],size[1]);
            try(TurntableRenderState saved=new TurntableRenderState()){prepare(mc,frame);GlStateManager.disableDepth();gui.drawScreen(-1,-1,0);GL11.glFinish();BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(size[0],size[1],frame);int glyphs=0;for(int y=0;y<size[1];y++)for(int x=0;x<size[0];x++)if((image.getRGB(x,y)&0xffffff)==0xffffff)glyphs++;require(glyphs>50,"Frame page text did not reach framebuffer");int cyan=0;for(int y=gui.getGuiTop();y<gui.getGuiTop()+20;y++)for(int x=gui.getGuiLeft()+gui.getXSize()-34;x<gui.getGuiLeft()+gui.getXSize()-6;x++){int pixel=image.getRGB(x,y),r=pixel>>16&255,g=pixel>>8&255,b=pixel&255;if(g>100&&b>100&&g>r+30)cyan++;}require(cyan>5,"supplied Vandor header logo did not render its cyan artwork");ImageIO.write(image,"png",new File(output,"shot_frames_dialog_"+size[0]+".png"));}finally{restore(mc,frame);}
        }}finally{mc.displayGuiScreen(previous);mc.player.openContainer=container;}
    }
    private static BufferedImage capture(Minecraft mc,Fixture fixture){
        Framebuffer frame=new Framebuffer(SIZE,SIZE,true);int pass=MinecraftForgeClient.getRenderPass();projection(0,fixture.columns,0,fixture.rows);
        try(TurntableRenderState saved=new TurntableRenderState()){
            prepare(mc,frame);ForgeHooksClient.setRenderPass(0);
            TEAnimatedScreenSelector renderer=new TEAnimatedScreenSelector();renderer.setRendererDispatcher(TileEntityRendererDispatcher.instance);
            for(TileEntityAnimatedScreenSelector tile:fixture.tiles)renderer.render(tile,tile.getPos().getX(),tile.getPos().getY(),0,0,-1,1);
            GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(SIZE,SIZE,frame);
        }finally{ForgeHooksClient.setRenderPass(pass);restore(mc,frame);}
    }
    private static void opaqueInterior(Minecraft mc,File output)throws Exception{
        TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.frameAppearance.front=ScreenHousingTextures.screenIndex("frames/frames-door-window/gunmetal-1");tile.frameAppearance.interior=ScreenHousingTextures.screenIndex("frames/frames-door-window/lights-cyan-1");require(tile.frameAppearance.front>0&&tile.frameAppearance.interior>0,"opaque frame fixture artwork");
        net.minecraft.block.Block glassBlock=net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_glass");
        VehicleWorld lightWorld=new VehicleWorld(mc.world,VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(BlockPos.ORIGIN,glassBlock.getDefaultState(),tile.writeToNBT(new NBTTagCompound())))));
        TileEntityProgrammableGlass lightTile=(TileEntityProgrammableGlass)lightWorld.getTileEntity(BlockPos.ORIGIN);lightTile.frameAppearance.front=-1;lightTile.frameAppearance.interior=-1;lightTile.frameAppearance.tile=true;require(!glassBlock.getActualState(lightWorld.getBlockState(BlockPos.ORIGIN),lightWorld,BlockPos.ORIGIN).getValue(com.vandorlabs.blocks.BlockGlassWall.TOP),"Original Tile frame must suppress the joined baked frame");lightTile.frameAppearance.read(tile.writeToNBT(new NBTTagCompound()));require(glassBlock.getLightValue(lightWorld.getBlockState(BlockPos.ORIGIN),lightWorld,BlockPos.ORIGIN)==3,"lit inner frame must emit faint level three");lightTile.setAppearance(1,false);require(glassBlock.getLightValue(lightWorld.getBlockState(BlockPos.ORIGIN),lightWorld,BlockPos.ORIGIN)==0,"Bare frame retained invisible frame light");
        Framebuffer frame=new Framebuffer(SIZE,SIZE,true);projection(0,16,0,16);
        try(TurntableRenderState saved=new TurntableRenderState()){
            prepare(mc,frame);GlStateManager.rotate(90,0,1,0);mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());GlassFrameMesh.get(tile,new GlassSurfaceGroups.Layout(0,0,1,1)).draw(buffer,240,240);Tessellator.getInstance().draw();GL11.glFinish();BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(SIZE,SIZE,frame);
            int holes=0;for(int y=16;y<240;y++)for(int x=118;x<138;x++)if((image.getRGB(x,y)&0xffffff)==BACKGROUND)holes++;require(holes==0,"transparent center pixels leaked into physical frame sides: "+holes);ImageIO.write(image,"png",new File(output,"shot_frames_opaque_interior.png"));
        }finally{restore(mc,frame);}
    }
    private static void guiProjection(int width,int height){GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,width,height,0,-1000,1000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();}
    private static void projection(double left,double right,double bottom,double top){GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(left,right,bottom,top,-32,32);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();}
    private static void prepare(Minecraft mc,Framebuffer frame){frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.depthFunc(GL11.GL_LEQUAL);GlStateManager.disableCull();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.colorMask(true,true,true,true);GlStateManager.color(1,1,1,1);mc.entityRenderer.disableLightmap();Tessellator.getInstance().getBuffer().setTranslation(0,0,0);}
    private static void restore(Minecraft mc,Framebuffer frame){GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    private static int difference(BufferedImage a,BufferedImage b,int left,int top,int right,int bottom){int n=0;for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if(distance(a.getRGB(x,y),b.getRGB(x,y))>6)n++;return n;}
    private static int distance(int a,int b){return Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));}
    private static void require(boolean value,String why){if(!value)throw new IllegalStateException("Frame framebuffer: "+why);}
    private FrameFramebufferChecks(){}
}
