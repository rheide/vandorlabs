package com.vandorlabs.client;

import com.vandorlabs.container.ContainerTokenSlot;
import com.vandorlabs.drives.DriveSettings;
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
    private boolean observerScanEnabled;
    private int texture,speed,allowed,behavior;private boolean animated,redstone,security;
    private final boolean observer;private final boolean drive,maxwell;private DriveSettings engine;
    private int redstoneTab(){return observer?1:choices.length+(drive?1:0);}
    private boolean redstonePane(){return surface==redstoneTab();}
    private boolean enginePane(){return drive&&surface==choices.length;}
    private int activation(){return RedstoneActivationButton.mode(redstone,maxwell&&engine.inverted);}
    public GuiNavigationTable(ContainerTokenSlot container){super(container);tile=(TileEntitySecuredDisplay)container.tile;observer=tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).observer();observerScanEnabled=observer&&((TileEntityConstructor)tile).observerScanEnabled();drive=tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).isDrive();maxwell=drive&&((TileEntityConstructor)tile).maxwell();engine=drive?((TileEntityConstructor)tile).driveSettings():DriveSettings.DEFAULT;choices=new int[tile.surfaceCount()];for(int i=0;i<choices.length;i++)choices[i]=tile.surfaceChoice(i);NavigationScreenSettings s=tile.screenSettings();texture=choices.length==0?-1:choices[0];speed=s.speed;animated=s.animated;redstone=s.redstone;security=s.security;allowed=tile.effectiveAllowed();behavior=tile.behavior;}
    @Override protected HousingTextureList materialPicker(){return textures;}
    @Override public void initGui(){
        String text=channels==null?ChannelFields.text(tile.getRedstoneChannels()):channels.getText();panel=new ProgrammableDialogLayout(width,height);
        xSize=panel.width;ySize=panel.height;super.initGui();buttonList.clear();org.lwjgl.input.Keyboard.enableRepeatEvents(true);
        textures=observer||choices.length==0||enginePane()||redstonePane()?null:HousingTextureList.forDisplay(panel.listX,panel.top+58,panel.listWidth,texture,(panel.height-72)/12,tile.nativeArtwork()).keepView(textures).custom(value->{choose(value);save();});
        if(textures!=null&&tile instanceof TileEntityNavigationTable)textures.navigation(()->{choose(com.vandorlabs.navigation.NavigationMap.SURFACE);save();});
        for(int i=0;i<=redstoneTab();i++){GuiButton tab=panel.fullTab(20+i,i,redstoneTab()+1,i==redstoneTab()?"Redstone":observer?"Observer":i==choices.length?"Engine":tile.surfaceLabel(i));tab.enabled=i!=surface;buttonList.add(tab);}
        int[] controls=observer&&!redstonePane()?new int[0]:redstonePane()?(maxwell?new int[]{1,9}:drive?new int[]{1,16,9}:new int[]{1}):enginePane()?(maxwell&&inventorySlots instanceof com.vandorlabs.container.ContainerMaxwellDrive?new int[]{93}:new int[0]):new int[]{3,4};
        for(int id:controls){GuiButton button=redstonePane()?new net.minecraftforge.fml.client.config.GuiButtonExt(id,guiLeft+12,guiTop+controlY(id),xSize-24,20,""):panel.control(id,controlY(id),"");if(id==1||id==16)button=RedstoneActivationButton.from(button,id==16?"Engine":"Activation",id==16?(engine.inverted?2:1):activation());if(panel.height<280)button.height=18;buttonList.add(button);}
        if(redstonePane()&&observer)buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(90,guiLeft+12,guiTop+126,xSize-24,20,"Transmitter..."));
        if(observer&&!redstonePane())buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(92,guiLeft+12,guiTop+62,xSize-24,20,""));
        channels=new GuiTextField(6,fontRenderer,guiLeft+12,panel.top+channelY(),xSize-24,18);ChannelFields.configure(channels);channels.setText(text);channels.setVisible(redstonePane());channels.setFocused(false);buttonList.add(panel.done(7));labels();
    }
    GuiButton control(int id){for(GuiButton b:buttonList)if(b.id==id)return b;return null;}
    int controlY(int id){int index=redstonePane()?(id==1?0:id==16?1:id==9?(maxwell?1:2):3):enginePane()?0:id==3?0:1;return (redstonePane()?96:62)+index*(panel.height<280?20:24);}
    private int channelLabelY(){return 54;}
    private int channelY(){return channelLabelY()+14;}
    private int filterLabelY(){return panel.height<280?172:196;}
    int filterY(){return panel.height<280?184:210;}
    private void labels(){for(GuiButton b:buttonList)switch(b.id){case 92:b.displayString=observerScanEnabled?"Mode: Scanning":"Mode: Idle";break;case 1:
            RedstoneActivationButton activation=(RedstoneActivationButton)b;
            if(maxwell)activation.setMode(activation(),"Disabled");
            else activation.setMode(activation());
            break;case 2:b.displayString=tile.supportsSecurity()?"Security: "+(security?"Enabled":"Disabled"):"Security: No mount";break;case 3:b.displayString=animated?"Animated":"Static";break;case 4:b.displayString="Speed: "+new String[]{"Slow","Normal","Fast"}[speed];break;case 9:b.displayString=maxwell?(engine.levels?"Signal: Analog":"Signal: Digital"):(engine.levels?"Brightness: Signal":"Brightness: On/Off");break;case 93:b.displayString="Purge Tank";break;case 16:((RedstoneActivationButton)b).setMode(engine.inverted?2:1);break;}}
    private void choose(int choice){texture=choice;if(choices.length>0)choices[surface]=choice;}
    private void save(){ChannelList list=ChannelList.parse(channels.getText());if(list==null){channels.setTextColor(0xff7777);return;}channels.setTextColor(0xffffff);NavigationScreenSettings settings=new NavigationScreenSettings(tile instanceof TileEntityConstructor?-1:texture,animated,speed,redstone,security);
        if(tile instanceof TileEntityConstructor)PacketHandler.INSTANCE.sendToServer(new MessageMachineDisplay(inventorySlots.windowId,settings,choices,127,0,list).withDrive(drive?engine:null).withObserverScan(tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).observer()?observerScanEnabled:null));
        else PacketHandler.INSTANCE.sendToServer(new MessageNavigationTable(inventorySlots.windowId,settings,allowed,behavior,list));}

    @Override protected void actionPerformed(GuiButton b){if(b.id>=20&&b.id<=20+redstoneTab()){surface=b.id-20;if(surface<choices.length)texture=choices[surface];initGui();return;}switch(b.id){case 93:mc.playerController.sendEnchantPacket(inventorySlots.windowId,com.vandorlabs.container.ContainerMaxwellDrive.PURGE_TANK);return;case 92:observerScanEnabled=!observerScanEnabled;break;case 1:cycleActivation();break;case 2:security=!security;break;case 3:animated=!animated;break;case 4:speed=GuiOptionCycle.next(speed,3);break;case 9:engine=new DriveSettings(!engine.levels,engine.inverted,engine.particles,engine.threshold);break;case 16:engine=new DriveSettings(engine.levels,!engine.inverted,engine.particles,engine.threshold);break;case 7:mc.player.closeScreen();return;}save();labels();}
    private void cycleActivation(){
        if(!maxwell){
            redstone=!redstone;
            return;
        }
        int next=GuiOptionCycle.next(activation(),3);
        redstone=next!=RedstoneActivationButton.MANUAL;
        engine=new DriveSettings(engine.levels,next==RedstoneActivationButton.OFF,engine.particles,engine.threshold);
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,3,4,9,16,92))return;
        if(textures!=null&&textures.click(x,y,button)){if(textures.picked()){int choice=textures.selected();choose(choice==HousingTextureList.NAVIGATION_BLACK?-1:choice==HousingTextureList.MODEL_ARTWORK?-2:choice);save();}return;}

        if(redstonePane())channels.mouseClicked(x,y,button);super.mouseClicked(x,y,button);
    }
    @Override protected void keyTyped(char ch,int key)throws IOException{if(redstonePane()&&channels.textboxKeyTyped(ch,key))save();else super.keyTyped(ch,key);}
    @Override public void updateScreen(){channels.updateCursorCounter();}
    @Override public void onGuiClosed(){org.lwjgl.input.Keyboard.enableRepeatEvents(false);super.onGuiClosed();}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xff19232c);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+26,0xff304858);DialogHeader.logo(guiLeft,guiTop,xSize,26);if(textures!=null)textures.draw(fontRenderer,x,y);else if(observer&&!redstonePane()){
            TileEntityConstructor observer=(TileEntityConstructor)tile;
            int statusY=guiTop+100;
            if(observer.observerPowered())fontRenderer.drawString("RF connected · "+(observer.activity()==2?"Scanning":"Idle"),guiLeft+12,statusY,0xabcfe8);
            else for(String line:new String[]{"Silent dish awaits","Bring power beneath its base","Maps wake with the dawn"}){fontRenderer.drawString(line,guiLeft+12,statusY,0xffbf83);statusY+=12;}
        }else if(!enginePane()&&!redstonePane())fontRenderer.drawString("No screen surfaces",panel.listX,panel.top+38,0xabcfe8);}
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){fontRenderer.drawString(tile.getBlockType().getLocalizedName(),12,10,0xffffff);if(redstonePane()){fontRenderer.drawString("Receive channels",12,channelLabelY(),0xffffff);}if(observer&&!redstonePane())fontRenderer.drawString("Scanning radius: "+com.vandorlabs.navigation.NavigationSources.radius((TileEntityConstructor)tile)+" blocks",12,150,0xabcfe8);if(enginePane()){if(maxwell){
            TileEntityConstructor engine=(TileEntityConstructor)tile;
            fontRenderer.drawString("Fuel  "+engine.maxwellFuel()+" / "+engine.maxwellCapacity()+" mB",12,93,0xabcfe8);
            fontRenderer.drawString(engine.maxwellActiveLevel()==15?"Afterburner":engine.maxwellActiveLevel()>0?"Burning":"Idle",12,110,0xabcfe8);
            fontRenderer.drawSplitString("With Redstone activation disabled, the drive supplies thrust during powered Flight. Analog signals scale fuel burn with signal strength.",12,131,panel.listWidth,0xabcfe8);
        }else fontRenderer.drawSplitString("Decorative drive. Redstone controls activation and light brightness. It supplies no vehicle thrust.",12,64,panel.listWidth,0xabcfe8);}}
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);GlStateManager.disableLighting();GlStateManager.disableDepth();channels.drawTextBox();renderHoveredToolTip(x,y);}
    @Override public boolean doesGuiPauseGame(){return false;}
}
