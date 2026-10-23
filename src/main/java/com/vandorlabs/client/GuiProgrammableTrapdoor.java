package com.vandorlabs.client;

import com.vandorlabs.redstone.ChannelList;

import com.vandorlabs.container.ContainerProgrammableTrapdoor;
import com.vandorlabs.network.*;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.tiles.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import org.lwjgl.input.*;
import java.io.IOException;

/** Same housing finish picker as other programmable blocks, with live settings. */
public final class GuiProgrammableTrapdoor extends GuiMaterialPicker {
    private int textureLayout;
    private final TileEntityProgrammableTrapdoor tile;
    private HousingTextureList textures;
    private int position,trigger,channel,tallWidth,doorDetail;
    private ChannelList channelList;
    private net.minecraft.util.EnumFacing facing;
    private boolean rotationFlipped;
    private int hingeSide;
    private boolean sliding,slideIntoWall,slideOverSurface,inverted,cover,tileTexture;
    private boolean diagonal(){return tile instanceof TileEntityProgrammableDiagonalTrapdoor;}
    private GuiTextField channelField;
    private GuiButton done;
    public GuiProgrammableTrapdoor(TileEntityProgrammableTrapdoor tile) {
        super(new ContainerProgrammableTrapdoor(tile));this.tile=tile;
        cover=tile.isCover();position=tile.getPosition();sliding=tile.isSliding();slideIntoWall=tile.isSlideIntoWall();slideOverSurface=tile.isSlideOverSurface();trigger=tile.getTrigger();channelList=tile.getRedstoneChannels();channel=channelList.first();
        doorDetail=0;
        tileTexture=tile.isTileTexture();textureLayout=tile.getTextureLayout();tallWidth=position==0?0:1;facing=tile.getWorld().getBlockState(tile.getPos()).getValue(com.vandorlabs.blocks.BlockProgrammableTrapdoor.FACING);
        hingeSide=diagonal()?((TileEntityProgrammableDiagonalTrapdoor)tile).getHingeSide():0;
        rotationFlipped=diagonal()&&((TileEntityProgrammableDiagonalTrapdoor)tile).isRotationFlipped();
        inverted=diagonal() && ((TileEntityProgrammableDiagonalTrapdoor)tile).isInverted();
        xSize=360;ySize=240;
    }
    private int controlsX, channelLabelY;
    @Override protected HousingTextureList materialPicker(){return textures;}
    @Override public void initGui() {
        String channelText=channelField==null?channelList.toString():channelField.getText();
        ProgrammableDialogLayout layout=new ProgrammableDialogLayout(width,height);
        xSize=layout.width;ySize=layout.height;
        super.initGui();
        Keyboard.enableRepeatEvents(true);buttonList.clear();
        int controlsWidth=ProgrammableDialogLayout.CONTROLS_WIDTH;
        controlsX=guiLeft+xSize-controlsWidth-12;
        textures=HousingTextureList.forDoors(doorDetail,guiLeft+12,guiTop+38,controlsX-guiLeft-31,HousingTextureList.doorSizeChoice(tile.getHousingTexture(),0),false,false)
                .visibleRows(Math.max(2,(ySize-64)/HousingTextureList.ROW_HEIGHT)).custom(value->send());
        int y=guiTop+38;
        buttonList.add(new GuiButton(1,controlsX,y,controlsWidth,20,motionLabel()));y+=22;
        GuiButton positionButton=new GuiButton(2,controlsX,y,diagonal()?(controlsWidth-2)/2:controlsWidth,20,positionLabel());
        positionButton.enabled=!diagonal() || position!=2;buttonList.add(positionButton);
        if(diagonal())buttonList.add(new GuiButton(7,controlsX+(controlsWidth+2)/2,y,(controlsWidth-2)/2,20,heightLabel()));
        y+=22;
        buttonList.add(new GuiButton(6,controlsX,y,controlsWidth,20,layoutLabel()));y+=22;
        GuiButton direction=new GuiButton(diagonal()?5:8,controlsX,y,diagonal()?(controlsWidth-2)/2:controlsWidth,20,diagonal()?"Flip slope":facingLabel());
        direction.enabled=diagonal() || tile.canOffsetClosedLeaf();buttonList.add(direction);
        if(diagonal()){GuiButton hinge=new GuiButton(10,controlsX+(controlsWidth+2)/2,y,(controlsWidth-2)/2,20,hingeLabel());hinge.enabled=!sliding;buttonList.add(hinge);}y+=22;
        if(diagonal()){GuiButton rotation=new GuiButton(9,controlsX,y,controlsWidth,20,rotationLabel());rotation.enabled=!sliding;buttonList.add(rotation);y+=22;}
        buttonList.add(new GuiButton(3,controlsX,y,controlsWidth,20,triggerLabel()));y+=22;
        channelLabelY=y;
        channelField=new GuiTextField(0,fontRenderer,controlsX,y+11,controlsWidth,18);
        ChannelFields.configure(channelField);
        channelField.setText(channelText);
        done=new GuiButton(4,controlsX,guiTop+ySize-26,controlsWidth,20,"Done");done.enabled=parsedChannel()>=0;buttonList.add(done);
    }
    private String hingeLabel(){return "Hinge: "+new String[]{"Auto","Left","Right"}[hingeSide];}
    private String rotationLabel(){return "Rotation: "+(rotationFlipped?"Reverse":"Normal");}
    private String motionLabel(){return diagonal()?(sliding?slideIntoWall?"Slide into wall":"Slide over wall":"Rotating"):cover?(sliding?"Slide into next block":"Rotate into next block"):(sliding?slideOverSurface?"Slide over surface":"Sliding":"Rotating");}
    private String positionLabel(){return diagonal()?"Width: "+(position==0?"Half":"Full"):"Position: "+new String[]{"Bottom","Middle","Top"}[position];}
    private String layoutLabel(){return "Texture: "+com.vandorlabs.render.TextureLayoutMode.label(textureLayout);}
    private String heightLabel(){return "Height: "+(position==2?"Half":"Full");}
    private String facingLabel(){return "Hinge: "+facing.getName();}
    private String triggerLabel(){return trigger==SpaceDoorData.TRIGGER_REDSTONE_ON?"Redstone: On":trigger==SpaceDoorData.TRIGGER_REDSTONE_OFF?"Redstone: Off":"Redstone: Disabled";}
    private int parsedChannel(){return ChannelFields.first(channelField);}
    private void send() {
        if(parsedChannel()>=0){channelList=ChannelFields.parse(channelField);channel=channelList.first();}
        int selected=textures.selected();
        tile.configureGroup(selected,position,sliding,trigger,channelList,inverted,cover,tileTexture,facing,slideIntoWall,slideOverSurface);
        for(TileEntityProgrammableTrapdoor leaf:tile.group())leaf.setTextureLayout(textureLayout);
        if(diagonal()){((TileEntityProgrammableDiagonalTrapdoor)tile).setHingeSide(hingeSide);((TileEntityProgrammableDiagonalTrapdoor)tile).setRotationFlipped(rotationFlipped);}
        PacketHandler.INSTANCE.sendToServer(new MessageProgrammableTrapdoor(tile.getPos(),selected,position,sliding,trigger,channel,inverted,cover,tileTexture,facing,slideIntoWall,slideOverSurface).withChannels(channelList).withTextureLayout(textureLayout).withRotationFlipped(rotationFlipped).withHingeSide(hingeSide));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==4){if(parsedChannel()>=0){send();mc.player.closeScreen();}return;}
        if(button.id==1){if(diagonal()){int mode=GuiOptionCycle.next(sliding?slideIntoWall?2:1:0,3);sliding=mode!=0;slideIntoWall=mode==2;}else{int mode=cover?(sliding?4:3):sliding?(slideOverSurface?2:1):0;mode=GuiOptionCycle.next(mode,tile.canOffsetClosedLeaf()?5:3);cover=mode>=3;sliding=mode==1 || mode==2 || mode==4;slideOverSurface=mode==2;}button.displayString=motionLabel();}
        else if(button.id==2){position=diagonal()?(position==0?1:0):GuiOptionCycle.next(position,3);if(diagonal())tallWidth=position;button.displayString=positionLabel();}
        else if(button.id==3){trigger=GuiOptionCycle.next(trigger,3);button.displayString=triggerLabel();}
        else if(button.id==5){inverted=!inverted;}
        else if(button.id==6){textureLayout=GuiOptionCycle.next(textureLayout,3);tileTexture=textureLayout==1;button.displayString=layoutLabel();}
        else if(button.id==7){
            if(diagonal()){if(position==2)position=tallWidth;else{tallWidth=position;position=2;}button.displayString=heightLabel();for(GuiButton other:buttonList)if(other.id==2){other.enabled=position!=2;other.displayString=positionLabel();}}

        }
        else if(button.id==8){if(!tile.canOffsetClosedLeaf())return;facing=GuiOptionCycle.next(facing);button.displayString=facingLabel();}
        else if(button.id==9){rotationFlipped=!rotationFlipped;button.displayString=rotationLabel();}
        else if(button.id==10){hingeSide=GuiOptionCycle.next(hingeSide,3);button.displayString=hingeLabel();}
        for(GuiButton control:buttonList)if(control.id==9 || control.id==10)control.enabled=!sliding;
        send();
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException {
        if(GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,1,2,3,5,6,7,8,9,10))return;
        channelField.mouseClicked(x,y,button);int before=textures.selected();
        if(textures.click(x,y,button)){if(before!=textures.selected())send();return;}super.mouseClicked(x,y,button);
    }

    @Override protected void keyTyped(char c,int key)throws IOException {
        if(key==Keyboard.KEY_RETURN || key==Keyboard.KEY_NUMPADENTER){if(parsedChannel()>=0){send();mc.player.closeScreen();}return;}
        if(channelField.textboxKeyTyped(c,key)){done.enabled=parsedChannel()>=0;channelField.setTextColor(done.enabled?0xE0E0E0:0xFF7777);if(done.enabled)send();return;}
        super.keyTyped(c,key);
    }
    @Override public void updateScreen(){super.updateScreen();channelField.updateCursorCounter();}
    @Override public void onGuiClosed(){super.onGuiClosed();Keyboard.enableRepeatEvents(false);}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y) {
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xFF19232C);
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+24,0xFF304858);textures.draw(fontRenderer,x,y);

    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y) {
        fontRenderer.drawString(diagonal()?"Programmable Diagonal Trapdoor":"Programmable Trapdoor",12,8,0xFFFFFF);
        fontRenderer.drawString("Block texture",12,27,0xDAE8F0);
        fontRenderer.drawString("Channels (0 = none)",controlsX-guiLeft,channelLabelY-guiTop,0xDAE8F0);
    }
    @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);channelField.drawTextBox();}
}
