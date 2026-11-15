package com.vandorlabs.neoforge.surface.client;

import com.vandorlabs.neoforge.surface.*;
import com.vandorlabs.neoforge.client.NativeMaterialPicker;
import com.vandorlabs.surface.core.SurfaceArtwork;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Source Active/Inactive artwork tabs, layout cycle and the standard scroll/drag material picker. */
public final class NativeSurfaceScreen extends AbstractContainerScreen<NativeSurfaceMenu> {
    private String on,off;private int mode;private boolean active;private NativeMaterialPicker picker;private final Map<Integer,Button> buttons=new LinkedHashMap<>();
    public NativeSurfaceScreen(NativeSurfaceMenu menu,Inventory inventory,Component title){super(menu,inventory,title);on=menu.tile.activeArtwork();off=menu.tile.inactiveArtwork();mode=menu.tile.layoutMode();}
    public Button control(int id){return buttons.get(id);}
    public NativeMaterialPicker materialPicker(){return picker;}
    @Override protected void init(){imageWidth=Math.min(360,width-12);imageHeight=Math.min(264,height-12);super.init();rebuild();}
    private void button(int id,int x,int y,int w,String caption){var button=Button.builder(Component.literal(caption),ignored->action(id,1)).bounds(leftPos+x,topPos+y,w,20).build();buttons.put(id,button);addRenderableWidget(button);}
    private void rebuild(){clearWidgets();buttons.clear();int half=(imageWidth-28)/2;button(90,12,28,half,"Inactive");button(91,16+half,28,half,"Active");buttons.get(active?91:90).active=false;button(1,imageWidth-126,52,114,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(mode));button(2,imageWidth-126,imageHeight-28,114,"Done");if(picker==null){picker=new NativeMaterialPicker(NativeSurfaceArtwork.choice(active?on:off),id->true,choice->{if(active)on=NativeSurfaceArtwork.fromChoice(choice);else off=NativeSurfaceArtwork.fromChoice(choice);send();});for(int i=0;i<SurfaceArtwork.IDS.length;i++)if(NativeSurfaceArtwork.choice(SurfaceArtwork.IDS[i])<0)picker.specialMaterial(-i-1,SurfaceArtwork.LABELS[i],"Surface","vandorlabs:blocks/"+SurfaceArtwork.FILES[i]);}picker.select(NativeSurfaceArtwork.choice(active?on:off),false);picker.layout(leftPos+12,topPos+52,imageWidth-158,Math.max(2,(imageHeight-90)/22),false);addRenderableWidget(picker.searchField());}
    private void send(){PacketDistributor.sendToServer(new NativeSurfaceSettingsPayload(menu.containerId,menu.tile.getBlockPos(),on,off,mode));}
    public void action(int id,int direction){if(id==90||id==91){active=id==91;rebuild();}else if(id==1){mode=Math.floorMod(mode+direction,3);send();rebuild();}else if(id==2)onClose();}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1&&buttons.get(1).isMouseOver(x,y)){action(1,-1);return true;}return picker.click(x,y,button)||super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return picker.drag(x,y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){picker.release();return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return picker.wheel(x,y,vertical)||super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){return picker.keyPressed(key,scan,modifiers)||super.keyPressed(key,scan,modifiers);}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff19232c);graphics.drawString(font,title,leftPos+8,topPos+8,0xffffff,false);picker.render(graphics,x,y);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public boolean isPauseScreen(){return false;}
}
