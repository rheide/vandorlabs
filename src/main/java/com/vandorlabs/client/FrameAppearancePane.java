package com.vandorlabs.client;

import com.vandorlabs.tiles.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.tileentity.TileEntity;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Inline frame settings; the owning dialog retains its tabs, container and Done action. */
public final class FrameAppearancePane {
    private final GuiContainer host;private final TileEntity owner;private final FrameAppearance appearance;private final Runnable bareToggle;private final BooleanSupplier framed;
    private HousingTextureList textures;private int page,contentTop;private List<GuiButton> buttons;
    public FrameAppearancePane(GuiContainer host,TileEntity owner){this(host,owner,null,null);}
    public FrameAppearancePane(GuiContainer host,TileEntity owner,Runnable bareToggle,BooleanSupplier framed){this.host=host;this.owner=owner;this.bareToggle=bareToggle;this.framed=framed;appearance=MessageFrameAppearance.appearance(owner);if(BlockAnimatedScreenSelector.usesDeviceHousing(owner.getBlockType()))appearance.artworkVersion=1;if(!front())page=1;}
    private boolean front(){return !(owner.getBlockType() instanceof BlockProgrammableWall)||owner.getBlockType() instanceof BlockProgrammableDiagonalGlass;}
    private boolean interior(){return owner instanceof TileEntityProgrammableGlass||owner instanceof TileEntitySpaceDoor||owner.getBlockType() instanceof BlockProgrammableWall;}
    private boolean display(){return FrameAppearance.displayBlock(owner.getBlockType());}
    public void init(int relativeContentTop,List<GuiButton> hostButtons){contentTop=relativeContentTop;buttons=hostButtons;buttons.removeIf(b->b.id>=3000&&b.id<=3008);int left=host.getGuiLeft(),top=host.getGuiTop(),width=host.getXSize(),height=host.getYSize(),controls=left+width-166;
        int count=(front()?1:0)+(interior()?1:0)+(display()?2:0),index=0,span=(width-24)/count;
        if(front())tab(3000,index++,span,"Front");if(interior())tab(3001,index++,span,"Interior");if(display()){tab(3005,index++,span,"Top");tab(3006,index,span,"Bottom");}
        for(GuiButton b:buttons)if(b.id>=3000&&b.id<=3006)b.enabled=b.id!=(3000+(page<2?page:page+3));
        int listWidth=controls-(left+12)-19,body=top+contentTop+24;
        textures=(page<2?HousingTextureList.forFrames(left+12,body,listWidth,page==0?appearance.front:appearance.interior,8):HousingTextureList.forCategory("Screens",left+12,body,listWidth,page==2?appearance.splitTop:appearance.splitBottom,8)).visibleRows(Math.max(2,(height-contentTop-34)/HousingTextureList.ROW_HEIGHT)).keepView(textures);
        if(display()||owner instanceof TileEntityProgrammableGlass)buttons.add(new GuiButton(3003,controls,body,154,20,"Frame: "+(appearance.tile?"Tile":"Fit")));
        if(display()){buttons.add(new GuiButton(3004,controls,body+24,154,20,"Display: "+(appearance.displayFit?"Fit":"Tile")));buttons.add(new GuiButton(3007,controls,body+48,154,20,"Split display: "+(appearance.split?"On":"Off")));}
        if(bareToggle!=null&&framed!=null)buttons.add(new GuiButton(3008,controls,body+72,154,20,framed.getAsBoolean()?"Framed":"Bare"));
    }
    private void tab(int id,int index,int span,String label){buttons.add(new DialogTabButton(id,host.getGuiLeft()+12+index*span,host.getGuiTop()+contentTop,span-2,18,label));}
    public HousingTextureList picker(){return textures;}
    public void draw(int mouseX,int mouseY){if(textures!=null)textures.draw(net.minecraft.client.Minecraft.getMinecraft().fontRenderer,mouseX,mouseY);}
    public boolean action(GuiButton button){int id=button.id-3000;if(id<0||id>8||id==2)return false;if(id<2||id==5||id==6){page=id<2?id:id-3;init(contentTop,buttons);return true;}if(id==3)appearance.tile=!appearance.tile;else if(id==4)appearance.displayFit=!appearance.displayFit;else if(id==7)appearance.split=!appearance.split;else if(id==8&&bareToggle!=null)bareToggle.run();else return false;if(id!=8)send();init(contentTop,buttons);return true;}
    public boolean click(int x,int y,int button)throws java.io.IOException{if(GuiOptionCycle.rightClick(net.minecraft.client.Minecraft.getMinecraft(),buttons,x,y,button,this::action,3003,3004,3007,3008))return true;if(textures!=null&&textures.click(x,y,button)){if(textures.picked()){if(page==0)appearance.front=textures.selected();else if(page==1)appearance.interior=textures.selected();else if(page==2)appearance.splitTop=textures.selected();else appearance.splitBottom=textures.selected();send();}return true;}return false;}
    public boolean wheel(int x,int y,int delta){return textures!=null&&textures.wheel(x,y,delta);}
    public boolean drag(int y){return textures!=null&&textures.drag(y);}
    public void release(){if(textures!=null)textures.release();}
    private void send(){owner.markDirty();GlassMaterials.changed();if(owner.getWorld()!=null)owner.getWorld().markBlockRangeForRenderUpdate(owner.getPos().add(-1,-1,-1),owner.getPos().add(1,1,1));PacketHandler.INSTANCE.sendToServer(new MessageFrameAppearance(owner,appearance));}
}
