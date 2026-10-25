package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.navigation.NavigationScreenSettings;
import com.vandorlabs.network.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import java.io.IOException;

/** Shared material picker, surface tabs and input gates for navigation modules and Core machines. */
public class GuiNavigationTable extends GuiMaterialPicker {
    private final TileEntitySecuredDisplay tile;
    private int surface;private final int[] choices;
    private HousingTextureList textures;private ProgrammableDialogLayout panel;private GuiTextField channels;
    private int texture,speed,allowed,behavior;private boolean animated,redstone,security;
    private final boolean drive;private com.vandorlabs.drives.DriveSettings engine;
    private boolean enginePane(){return drive&&surface==choices.length;}
    public GuiNavigationTable(ContainerTokenSlot container){super(container);tile=(TileEntitySecuredDisplay)container.tile;drive=tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).isDrive();engine=drive?((TileEntityConstructor)tile).driveSettings():com.vandorlabs.drives.DriveSettings.DEFAULT;choices=new int[tile.surfaceCount()];for(int i=0;i<choices.length;i++)choices[i]=tile.surfaceChoice(i);NavigationScreenSettings s=tile.screenSettings();texture=choices.length==0?-1:choices[0];speed=s.speed;animated=s.animated;redstone=s.redstone;security=s.security;allowed=tile.effectiveAllowed();behavior=tile.behavior;}
    @Override protected HousingTextureList materialPicker(){return textures;}
    @Override public void initGui(){
        String text=channels==null?tile.getRedstoneChannels().toString():channels.getText();panel=new ProgrammableDialogLayout(width,height);
        xSize=panel.width;ySize=panel.height;super.initGui();buttonList.clear();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        textures=choices.length==0||enginePane()?null:HousingTextureList.forDisplay(panel.listX,panel.top+(choices.length>1?52:30),panel.listWidth,texture,(panel.height-(choices.length>1?64:42))/12,tile.nativeArtwork()).keepView(textures).custom(value->{choose(value);save();});
        if(choices.length>1||drive)for(int i=0;i<choices.length+(drive?1:0);i++){GuiButton tab=panel.tab(20+i,i,choices.length+(drive?1:0),i==choices.length?"Engine":tile.surfaceLabel(i));tab.enabled=i!=surface;buttonList.add(tab); }
        for(int id:enginePane()?new int[]{1,16,9,10,11}:new int[]{1,3,4}){GuiButton button=panel.control(id,controlY(id),"");if(panel.height<280)button.height=18;buttonList.add(button);}
        channels=new GuiTextField(6,fontRenderer,panel.controlsX,panel.top+channelY(),ProgrammableDialogLayout.CONTROLS_WIDTH,18);ChannelFields.configure(channels);channels.setText(text);buttonList.add(panel.done(7));labels();
    }
    int controlY(int id){int index=enginePane()?(id==1?0:id==16?1:id==9?2:id==10?3:4):(id==1?0:id==3?1:2);return panel.height<280?30+index*20:32+index*24;}
    private int channelLabelY(){return panel.height<280?134:152;}
    private int channelY(){return panel.height<280?146:166;}
    private int filterLabelY(){return panel.height<280?172:196;}
    int filterY(){return panel.height<280?184:210;}
    private void labels(){for(GuiButton b:buttonList)switch(b.id){case 1:b.displayString="Redstone: "+(redstone?"Enabled":"Disabled");break;case 2:b.displayString=tile.supportsSecurity()?"Security: "+(security?"Enabled":"Disabled"):"Security: No mount";break;case 3:b.displayString=animated?"Animated":"Static";break;case 4:b.displayString="Speed: "+new String[]{"Slow","Normal","Fast"}[speed];break;case 9:b.displayString=engine.levels?"Brightness: Signal":"Brightness: On/Off";break;case 10:b.displayString="Particles: "+TileEntityRedstoneLight.PARTICLE_LEVELS[engine.particles];break;case 11:b.displayString="Particles at level: "+engine.threshold;break;case 16:b.displayString=engine.inverted?"Activate on: OFF":"Activate on: ON";break;}}
    private void choose(int choice){texture=choice;if(choices.length>0)choices[surface]=choice;}
    private void save(){ChannelList list=ChannelList.parse(channels.getText());if(list==null){channels.setTextColor(0xff7777);return;}channels.setTextColor(0xffffff);NavigationScreenSettings settings=new NavigationScreenSettings(tile instanceof TileEntityConstructor?-1:texture,animated,speed,redstone,security);
        if(tile instanceof TileEntityConstructor)PacketHandler.INSTANCE.sendToServer(new MessageMachineDisplay(inventorySlots.windowId,settings,choices,127,0,list).withDrive(drive?engine:null));
        else PacketHandler.INSTANCE.sendToServer(new MessageNavigationTable(inventorySlots.windowId,settings,allowed,behavior,list));}

    @Override protected void actionPerformed(GuiButton b){if(b.id>=20&&b.id<20+choices.length+(drive?1:0)){surface=b.id-20;if(surface<choices.length)texture=choices[surface];initGui();return;}switch(b.id){case 1:redstone=!redstone;break;case 2:security=!security;break;case 3:animated=!animated;break;case 4:speed=GuiOptionCycle.next(speed,3);break;case 9:engine=new com.vandorlabs.drives.DriveSettings(!engine.levels,engine.inverted,engine.particles,engine.threshold);break;case 10:engine=new com.vandorlabs.drives.DriveSettings(engine.levels,engine.inverted,GuiOptionCycle.next(engine.particles,4),engine.threshold);break;case 11:engine=new com.vandorlabs.drives.DriveSettings(engine.levels,engine.inverted,engine.particles,GuiOptionCycle.next(engine.threshold,16));break;case 16:engine=new com.vandorlabs.drives.DriveSettings(engine.levels,!engine.inverted,engine.particles,engine.threshold);break;case 7:mc.player.closeScreen();return;}save();labels();}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,3,4,9,10,11,16))return;
        if(textures!=null&&textures.click(x,y,button)){if(textures.picked()){int choice=textures.selected();choose(choice==HousingTextureList.NAVIGATION_BLACK?-1:choice==HousingTextureList.MODEL_ARTWORK?-2:choice);save();}return;}

        channels.mouseClicked(x,y,button);super.mouseClicked(x,y,button);
    }
    @Override protected void keyTyped(char ch,int key)throws IOException{if(channels.textboxKeyTyped(ch,key))save();else super.keyTyped(ch,key);}
    @Override public void updateScreen(){channels.updateCursorCounter();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+26,0xff304858);if(textures!=null)textures.draw(fontRenderer,x,y);else if(!enginePane())fontRenderer.drawString("No screen surfaces",panel.listX,panel.top+38,0xabcfe8);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(enginePane()?"Drive Engine":tile instanceof TileEntityConstructor?"Machine Display":"Navigation Display",12,10,0xffffff);fontRenderer.drawString("Input channels (0 = none)",panel.controlsX-guiLeft,channelLabelY(),0xffffff);if(enginePane())fontRenderer.drawSplitString("Decorative drive. Redstone controls activation, brightness and exhaust. It supplies no vehicle thrust.",12,60,panel.listWidth,0xabcfe8);if(panel.height>=280)fontRenderer.drawSplitString("Data Chips store data. No security gate or redstone output.",panel.controlsX-guiLeft,208,154,0xabcfe8);}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);GlStateManager.disableLighting();GlStateManager.disableDepth();channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
