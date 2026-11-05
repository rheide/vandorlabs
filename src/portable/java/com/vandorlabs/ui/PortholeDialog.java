package com.vandorlabs.ui;

import com.vandorlabs.persistence.PanelGlassSettings;
import com.vandorlabs.persistence.WallSettings;
import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.tiles.GlassTextures;
import java.util.*;

/** Current flat porthole panes. Frame editor state and publication remain host-owned. */
public final class PortholeDialog {
    public static final int MAIN=195,GLASS=213,INTERIOR=211,EXTERIOR=212,TINT=113,TEXTURE=115;
    public static final int SHADE_BASE=-100;
    private PortholeDialog(){}
    public static int shadeChoice(int shade){if(!GlassTextures.valid(shade))throw new IllegalArgumentException("shade");return SHADE_BASE-shade;}
    public static int choiceShade(int choice){int shade=SHADE_BASE-choice;return GlassTextures.valid(shade)?shade:-1;}
    public static boolean page(int id){return id==MAIN||id==GLASS||id==INTERIOR||id==EXTERIOR;}
    public static boolean framePage(int id){return id==INTERIOR||id==EXTERIOR;}
    private static DialogControl control(int id,int x,int y,int width,int height,String label,boolean tab,boolean enabled){return new DialogControl(id,x,y,width,height,label,tab,enabled);}
    public static List<DialogControl> tabs(ProgrammableDialogLayout p,int page){
        List<DialogControl> rows=new ArrayList<DialogControl>();int width=(p.width-22)/4;int[] ids={MAIN,GLASS,INTERIOR,EXTERIOR};String[] names={"Main","Glass","Interior","Exterior"};for(int i=0;i<4;i++)rows.add(control(ids[i],p.left+12+i*width,p.top+30,width-2,18,names[i],true,ids[i]!=page));return Collections.unmodifiableList(rows);
    }
    public static List<DialogControl> body(ProgrammableDialogLayout p,int page,int mainLayout,WallSettings wall,PanelGlassSettings glass){
        return body(p,page,mainLayout,wall,glass,false,2,false);
    }
    public static List<DialogControl> body(ProgrammableDialogLayout p,int page,int mainLayout,WallSettings wall,PanelGlassSettings glass,boolean diagonal,int facing,boolean inverted){
        if(framePage(page))return Collections.emptyList();List<DialogControl> rows=new ArrayList<DialogControl>();int y=p.top+54;
        if(page==MAIN){rows.add(control(ProgrammableBlockDialog.TEXTURE_LAYOUT,p.controlsX,y,154,20,"Texture: "+TextureLayoutMode.label(mainLayout),false,true));y+=24;}
        rows.add(control(WallAppearanceDialog.JOIN_PORTHOLES,p.controlsX,y,154,20,WallAppearanceDialog.portholeJoinLabel(wall.joinPortholes),false,true));y+=24;
        rows.add(control(WallAppearanceDialog.SHAPE,p.controlsX,y,154,20,WallAppearanceDialog.shapeLabel(wall.portholeShape),false,true));y+=24;
        if(diagonal){rows.addAll(DiagonalWallDialog.geometryControls(p,new DiagonalWallDialog.Geometry(wall,facing,inverted),y-p.top,true));y+=48;}
        if(page==GLASS){rows.add(control(TINT,p.controlsX,y,154,20,"Tint: "+GlassTextures.tintLabel(glass.tint),false,true));y+=24;if(glass.material>=0)rows.add(control(TEXTURE,p.controlsX,y,154,20,"Texture: "+TextureLayoutMode.label(glass.textureLayout()),false,true));}
        return Collections.unmodifiableList(rows);
    }
    public static int playbackY(PanelGlassSettings glass){return playbackY(glass,false);}
    public static int playbackY(PanelGlassSettings glass,boolean diagonal){return (glass.material>=0?150:126)+(diagonal?48:0);}
    public static PanelGlassSettings action(PanelGlassSettings state,int id,int direction){
        if(id==TINT)return new PanelGlassSettings(state.shade,state.material,state.layout,state.stretch,DialogInput.cycle(state.tint,GlassTextures.TINT_COUNT,direction),state.framed,state.textureJoin);
        if(id==TEXTURE){int next=DialogInput.cycle(state.textureLayout(),3,direction);return new PanelGlassSettings(state.shade,state.material,next,next!=1,state.tint,state.framed,state.textureJoin);}
        return state;
    }
    public static PanelGlassSettings choose(PanelGlassSettings state,int choice){int shade=choiceShade(choice);return new PanelGlassSettings(shade>=0?shade:state.shade,shade>=0?-1:choice,state.layout,state.stretch,state.tint,state.framed,state.textureJoin);}
}
