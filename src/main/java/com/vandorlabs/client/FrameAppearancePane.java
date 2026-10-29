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
    private boolean displayPage,standalone;private HousingTextureList textures;private int page,contentTop;private List<GuiButton> buttons;
    public FrameAppearancePane(GuiContainer host,TileEntity owner){this(host,owner,null,null);}
    public FrameAppearancePane(GuiContainer host,TileEntity owner,Runnable bareToggle,BooleanSupplier framed){this.host=host;this.owner=owner;this.bareToggle=bareToggle;this.framed=framed;appearance=new FrameAppearance();net.minecraft.nbt.NBTTagCompound draft=new net.minecraft.nbt.NBTTagCompound();FrameAppearance.of(owner).write(draft);appearance.read(draft);appearance.exterior=appearance.exteriorChoice();if(FrameAppearance.usesMainFront(owner.getBlockType()))appearance.front=-1;if(appearance.splitTop<0)appearance.splitTop=defaultScreen();if(appearance.splitBottom<0)appearance.splitBottom=defaultScreen();if(BlockAnimatedScreenSelector.usesDeviceHousing(owner.getBlockType()))appearance.artworkVersion=1;if(!front())page=1;}
    private static int defaultScreen(){
        for(int i=0;i<ScreenHousingTextures.IDS.length;i++)if("Screens".equals(ScreenHousingTextures.category(i))&&FrameAppearance.bareChoice(i)==i)return ScreenHousingTextures.choiceAt(i);
        return 0;
    }
    private boolean front(){return !FrameAppearance.usesMainFront(owner.getBlockType())&&(!(owner.getBlockType() instanceof BlockProgrammableWall)||owner.getBlockType() instanceof BlockProgrammableDiagonalGlass);}
    private boolean interior(){return owner instanceof TileEntityProgrammableGlass||owner instanceof TileEntitySpaceDoor||owner.getBlockType() instanceof BlockProgrammableWall;}
    private boolean display(){return BlockAnimatedScreenSelector.usesDeviceHousing(owner.getBlockType());}
    public void init(int relativeContentTop,List<GuiButton> hostButtons){displayPage=false;if(page>1&&!standalone)page=0;contentTop=relativeContentTop;buttons=hostButtons;buttons.removeIf(b->b.id>=3000&&b.id<=3008);int left=host.getGuiLeft(),top=host.getGuiTop(),width=host.getXSize(),height=host.getYSize(),controls=left+width-166;
        int count=(front()?1:0)+(interior()?1:0),index=0,span=(width-24)/count;
        if(standalone)count=1;
        if(count>1){if(front())tab(3000,index++,span,"Front");if(interior())tab(3001,index++,span,"Interior");}
        for(GuiButton b:buttons)if(b.id>=3000&&b.id<=3006)b.enabled=b.id!=(3000+(page<2?page:page+3));
        boolean tiledFrame=display()||owner instanceof TileEntityProgrammableGlass,bare=display()||bareToggle!=null&&framed!=null;
        boolean anyControls=tiledFrame||display()||bare;int listWidth=anyControls?controls-(left+12)-19:width-24,body=top+contentTop+(count>1?24:0);
        textures=(page==0?HousingTextureList.forFrames(left+12,body,listWidth,appearance.front,8,display(),display()&&appearance.split)
                :page==1||page==4?HousingTextureList.forFrameSurfaces(left+12,body,listWidth,page==1?appearance.interior:appearance.exterior,8)
                :HousingTextureList.forCategory("Screens",left+12,body,listWidth,page==2?appearance.splitTop:appearance.splitBottom,8))
                .visibleRows(Math.max(2,(height-contentTop-(anyControls?34:56))/HousingTextureList.ROW_HEIGHT)).keepView(textures);
        int controlY=body;
        if(tiledFrame){buttons.add(new GuiButton(3003,controls,controlY,154,20,"Texture: "+(appearance.tile?"Tile":"Fit")));controlY+=24;}

        if(bare)buttons.add(new GuiButton(3008,controls,controlY,154,20,(display()?appearance.hasFront():framed.getAsBoolean())?"Framed":"Bare"));

    }
    public void initGlassFace(int face,int relativeContentTop,List<GuiButton> hostButtons){standalone=true;page=face==2?4:face;init(relativeContentTop,hostButtons);}
    public boolean splitDisplay(){return appearance.split;}
    public void initDisplay(List<GuiButton> hostButtons){
        displayPage=true;buttons=hostButtons;contentTop=54;if(page<2)page=2;
        buttons.removeIf(b->b.id>=3000&&b.id<=3008);
        int left=host.getGuiLeft(),top=host.getGuiTop(),controls=left+host.getXSize()-166;
        buttons.add(new GuiButton(3004,controls,top+124,154,20,"Texture: "+(appearance.displayFit?"Fit":"Tile")));
        buttons.add(new GuiButton(3007,controls,top+148,154,20,"Split display: "+(appearance.split?"On":"Off")));
        textures=null;
        if(appearance.split){
            int span=(controls-left-31)/2;
            buttons.add(new DialogTabButton(3005,left+12,top+54,span-2,18,"Top"));buttons.add(new DialogTabButton(3006,left+12+span,top+54,span-2,18,"Bottom"));
            for(GuiButton b:buttons)if(b.id==3005||b.id==3006)b.enabled=b.id!=(page==2?3005:3006);
            textures=HousingTextureList.forCategory("Screens",left+12,top+78,controls-left-31,page==2?appearance.splitTop:appearance.splitBottom,8).visibleRows(Math.max(2,(host.getYSize()-110)/HousingTextureList.ROW_HEIGHT));
        }
    }
    private void refresh(){
        // GuiScreen iterates this list by index while dispatching a click. Moving
        // the replacement button past Done can dispatch that same click twice.
        int first=buttons.size();for(int i=0;i<buttons.size();i++)if(buttons.get(i).id>=3000&&buttons.get(i).id<=3008){first=i;break;}
        if(displayPage)initDisplay(buttons);else init(contentTop,buttons);
        java.util.List<GuiButton> pane=new java.util.ArrayList<>();for(GuiButton b:buttons)if(b.id>=3000&&b.id<=3008)pane.add(b);
        buttons.removeAll(pane);buttons.addAll(Math.min(first,buttons.size()),pane);
    }
    private void tab(int id,int index,int span,String label){buttons.add(new DialogTabButton(id,host.getGuiLeft()+12+index*span,host.getGuiTop()+contentTop,span-2,18,label));}
    public HousingTextureList picker(){return textures;}
    public void draw(int mouseX,int mouseY){if(textures!=null)textures.draw(net.minecraft.client.Minecraft.getMinecraft().fontRenderer,mouseX,mouseY);}
    public boolean action(GuiButton button){int id=button.id-3000;if(id<0||id>8||id==2)return false;if(id<2||id==5||id==6){page=id<2?id:id-3;refresh();return true;}if(id==3)appearance.tile=!appearance.tile;else if(id==4)appearance.displayFit=!appearance.displayFit;else if(id==7)appearance.split=!appearance.split;else if(id==8){if(display()){if(appearance.front<0){appearance.front=ScreenHousingTextures.screenIndex("frames/frames-screen-full/frame_bolted_1254");appearance.bare=false;}else appearance.bare=!appearance.bare;}else if(bareToggle!=null)bareToggle.run();else return false;}else return false;if(id!=8||display())send();refresh();return true;}
    public boolean click(int x,int y,int button)throws java.io.IOException{if(GuiOptionCycle.rightClick(net.minecraft.client.Minecraft.getMinecraft(),buttons,x,y,button,this::action,3003,3004,3007,3008))return true;if(textures!=null&&textures.click(x,y,button)){if(textures.picked()){if(page==0)appearance.front=textures.selected();else if(page==1)appearance.interior=textures.selected();else if(page==4)appearance.exterior=textures.selected();else if(page==2)appearance.splitTop=textures.selected();else appearance.splitBottom=textures.selected();send();}return true;}return false;}
    public boolean wheel(int x,int y,int delta){return textures!=null&&textures.wheel(x,y,delta);}
    public boolean drag(int y){return textures!=null&&textures.drag(y);}
    public void release(){if(textures!=null)textures.release();}
    private void send(){net.minecraft.nbt.NBTTagCompound draft=new net.minecraft.nbt.NBTTagCompound();appearance.write(draft);FrameAppearance.of(owner).read(draft);owner.markDirty();GlassMaterials.changed();if(owner.getWorld()!=null)owner.getWorld().markBlockRangeForRenderUpdate(owner.getPos().add(-1,-1,-1),owner.getPos().add(1,1,1));PacketHandler.INSTANCE.sendToServer(new MessageFrameAppearance(owner,appearance));}
}
