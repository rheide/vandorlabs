package com.vandorlabs.client;

import com.vandorlabs.container.ContainerModuleRack;
import com.vandorlabs.network.*;
import com.vandorlabs.rack.AutopilotModuleSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityModuleRack;
import net.minecraft.client.gui.*;
import net.minecraft.item.*;
import net.minecraft.block.Block;
import java.io.IOException;

/** Standard housing/transmission controls and the existing navigation controls for each physical module. */
public final class GuiModuleRack extends GuiMaterialPicker {
    private final TileEntityModuleRack tile;
    private HousingTextureList textures;
    private int page, modulePage, housing, knownKinds;
    private boolean emit,powerOut;
    private GuiTextField channels;
    private InventorySamplePicker routePicker;
    private final AutopilotModuleSettings[] drafts=new AutopilotModuleSettings[4];
    private final long[] sequences=new long[4];
    public GuiModuleRack(TileEntityModuleRack tile) {
        super(new ContainerModuleRack(tile)); this.tile=tile;
        housing=tile.getHousingTexture();emit=tile.emitsRedstone();powerOut=tile.powerOut();xSize=348;ySize=250;
        reloadModules();
    }
    private void reloadModules() {
        knownKinds=tile.getModuleKinds();
        for(int slot=0;slot<4;slot++)if(tile.moduleKind(slot)==2){drafts[slot]=tile.moduleSettings(slot);sequences[slot]=tile.getInsertionSequence(slot);}else drafts[slot]=null;
        if(page>=2&&drafts[page-2]==null)page=0;
    }
    @Override protected HousingTextureList materialPicker(){return page==0?textures:null;}
    @Override public void initGui() {
        HousingTextureList previous=textures;
        xSize=Math.min(348,width-12);ySize=Math.min(250,height-12);
        super.initGui();buttonList.clear();routePicker=null;channels=null;
        int modules=tile.autopilotCount(),available=xSize-24,index=2;
        buttonList.add(new DialogTabButton(100,guiLeft+12,guiTop+32,com.vandorlabs.rack.ModuleRackTabs.width(available,modules,0),20,"Housing"));
        buttonList.add(new DialogTabButton(101,guiLeft+12+com.vandorlabs.rack.ModuleRackTabs.offset(available,modules,1),guiTop+32,com.vandorlabs.rack.ModuleRackTabs.width(available,modules,1),20,"Redstone"));
        for(int slot=0;slot<4;slot++)if(drafts[slot]!=null){
            buttonList.add(new DialogTabButton(102+slot,guiLeft+12+com.vandorlabs.rack.ModuleRackTabs.offset(available,modules,index),guiTop+32,com.vandorlabs.rack.ModuleRackTabs.width(available,modules,index),20,com.vandorlabs.rack.ModuleRackTabs.moduleLabel(slot)));index++;
        }
        for(GuiButton button:buttonList)button.enabled=button.id!=100+page;
        if(page==0)textures=HousingTextureList.forHousing(guiLeft+12,guiTop+64,xSize-24,housing,
                Math.max(2,(ySize-110)/HousingTextureList.ROW_HEIGHT),"vandorlabs:blocks/module_rack/housing")
                .custom(value->{housing=value;send();}).keepView(previous);
        else if(page==1){
            buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(0,guiLeft+12,guiTop+64,xSize-24,20,"Emit Redstone: "+(emit?"On":"Off")));
            buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(90,guiLeft+12,guiTop+92,xSize-24,20,"Transmitter..."));
            buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(8,guiLeft+12,guiTop+174,xSize-24,20,"Power Port: "+(powerOut?"Out":"In")));
        }else{
            int half=(xSize-28)/2;
            buttonList.add(new DialogTabButton(200,guiLeft+12,guiTop+58,half,20,"General"));
            buttonList.add(new DialogTabButton(201,guiLeft+16+half,guiTop+58,half,20,"Redstone"));
            for(GuiButton button:buttonList)if(button.id>=200)button.enabled=button.id!=200+modulePage;
            AutopilotModuleSettings config=drafts[page-2];
            if(modulePage==1){
                channels=new GuiTextField(10,fontRenderer,guiLeft+12,guiTop+100,xSize-24,18);
                ChannelFields.configure(channels);channels.setText(config.channels.toString());
                buttonList.add(new RedstoneActivationButton(2,guiLeft+12,guiTop+130,xSize-24,config.activation));
            }else routePicker=InventorySamplePicker.hotbar(guiLeft+(xSize-162)/2,guiTop+138,GuiModuleRack::routeBlock,stack->{
                Block block=((ItemBlock)stack.getItem()).getBlock();
                drafts[page-2]=new AutopilotModuleSettings(config.channels,config.activation,block.getRegistryName().toString(),stack.getMetadata());sendModule();
            });
        }
        buttonList.add(new net.minecraftforge.fml.client.config.GuiButtonExt(1,guiLeft+12,guiTop+ySize-30,xSize-24,20,"Done"));
        org.lwjgl.input.Keyboard.enableRepeatEvents(true);
    }
    GuiButton control(int id){for(GuiButton button:buttonList)if(button.id==id)return button;return null;}
    private void send(){PacketHandler.INSTANCE.sendToServer(new MessageModuleRack(inventorySlots.windowId,tile.getPos(),housing,emit).withPowerOut(powerOut));}
    private boolean saveChannelDraft(){
        if(page<2||channels==null)return true;
        ChannelList bank=ChannelFields.parse(channels);if(bank==null)return false;
        AutopilotModuleSettings old=drafts[page-2];drafts[page-2]=new AutopilotModuleSettings(bank,old.activation,old.marker,old.markerMetadata);return true;
    }
    private void sendModule(){int slot=page-2;PacketHandler.INSTANCE.sendToServer(new MessageModuleAutopilot(inventorySlots.windowId,tile.getPos(),slot,sequences[slot],drafts[slot]));}
    @Override protected void actionPerformed(GuiButton button){
        if(button.id>=100&&button.id<=105){if(!saveChannelDraft())return;if(page>=2)sendModule();page=button.id-100;initGui();return;}
        if(button.id==200||button.id==201){if(!saveChannelDraft())return;sendModule();modulePage=button.id-200;initGui();return;}
        if(button.id==8){powerOut=!powerOut;send();initGui();}
        if(button.id==0){emit=!emit;send();initGui();}
        else if(button.id==2){if(!saveChannelDraft())return;AutopilotModuleSettings old=drafts[page-2];
            drafts[page-2]=new AutopilotModuleSettings(old.channels,GuiOptionCycle.next(old.activation,3),old.marker,old.markerMetadata);sendModule();initGui();}
        else if(button.id==1){if(!saveChannelDraft())return;if(page>=2)sendModule();mc.player.closeScreen();}
    }
    private static boolean routeBlock(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof ItemBlock
            &&((ItemBlock)stack.getItem()).getBlock()!=net.minecraft.init.Blocks.AIR&&stack.getMetadata()>=0&&stack.getMetadata()<=15;}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(page==0&&textures!=null){int before=textures.selected();if(textures.click(x,y,button)){if(before!=textures.selected()){housing=textures.selected();send();}return;}}
        if(routePicker!=null&&routePicker.click(x,y,button))return;
        if(channels!=null)channels.mouseClicked(x,y,button);
        if(!GuiOptionCycle.rightClick(mc,buttonList,x,y,button,this::actionPerformed,0,2,8))super.mouseClicked(x,y,button);
    }
    @Override protected void keyTyped(char character,int key)throws IOException{
        if(channels!=null&&channels.textboxKeyTyped(character,key))return;
        if(page!=0||textures==null||!textures.searchKey(character,key))super.keyTyped(character,key);
    }
    @Override public void updateScreen(){super.updateScreen();boolean changed=knownKinds!=tile.getModuleKinds();for(int slot=0;slot<4;slot++)if(drafts[slot]!=null&&sequences[slot]!=tile.getInsertionSequence(slot))changed=true;if(changed){reloadModules();initGui();}}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){
        drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,0xEE171B23);DialogHeader.logo(guiLeft,guiTop,xSize);
        if(page==0&&textures!=null)textures.draw(fontRenderer,x,y);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x,int y){
        fontRenderer.drawString("Module Rack",12,10,0xFFFFFF);
        if(page==0)fontRenderer.drawString("Wall texture",12,54,0xDAE8F0);
        else if(page>=2){
            fontRenderer.drawString(modulePage==0?"Route marker block":"Receive channels",12,84,0xDAE8F0);
            if(tile.autopilotCount()>1)fontRenderer.drawString("One Autopilot per craft is required.",12,modulePage==0?180:170,0xEAAF66);
            else if(modulePage==0)fontRenderer.drawString("A pulse runs until the next waypoint.",12,180,0xAAAAAA);
        }
    }
    @Override public void drawScreen(int x,int y,float partial){
        drawDefaultBackground();super.drawScreen(x,y,partial);DialogOverlay.prepare();
        if(channels!=null)channels.drawTextBox();
        if(routePicker!=null){
            AutopilotModuleSettings config=drafts[page-2];Block block=Block.getBlockFromName(config.marker);
            ItemStack marker=block==null?ItemStack.EMPTY:new ItemStack(block,1,config.markerMetadata);
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();itemRender.renderItemAndEffectIntoGUI(marker,guiLeft+12,guiTop+102);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();DialogOverlay.prepare();
            fontRenderer.drawString(fontRenderer.trimStringToWidth(marker.isEmpty()?config.marker:marker.getDisplayName(),xSize-52),guiLeft+36,guiTop+102,0xFFFFFF);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(config.marker+" : "+config.markerMetadata,xSize-52),guiLeft+36,guiTop+113,0xAAAAAA);routePicker.draw(x,y);
        }
    }
    @Override public void onGuiClosed(){super.onGuiClosed();org.lwjgl.input.Keyboard.enableRepeatEvents(false);}
}
