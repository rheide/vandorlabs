package com.vandorlabs.ui;

import com.vandorlabs.persistence.*;
import com.vandorlabs.tiles.GlassTextures;
import java.util.*;

/** Current diagonal glass profile on the existing material/frame panes. */
public final class DiagonalGlassDialog {
 public static final int GLASS=195,FRONT=210,INTERIOR=211,EXTERIOR=212,FRAME=3008;
 private DiagonalGlassDialog(){}
 public static boolean page(int id){return id==GLASS||framePage(id);}
 public static boolean framePage(int id){return id>=FRONT&&id<=EXTERIOR;}
 public static boolean visible(int id){return id!=114;}
 public static List<DialogControl> tabs(ProgrammableDialogLayout p,int page){
  List<DialogControl> rows=new ArrayList<DialogControl>();int width=(p.width-22)/4;int[] ids={GLASS,FRONT,INTERIOR,EXTERIOR};String[] labels={"Glass","Front","Interior","Exterior"};
  for(int i=0;i<4;i++)rows.add(new DialogControl(ids[i],p.left+12+i*width,p.top+30,width-2,18,labels[i],true,ids[i]!=page));return Collections.unmodifiableList(rows);
 }
 public static List<DialogControl> body(ProgrammableDialogLayout p,DiagonalWallDialog.Geometry geometry,PanelGlassSettings glass){
  List<DialogControl> rows=new ArrayList<DialogControl>();List<DialogControl> plain=DiagonalWallDialog.geometryControls(p,geometry,54,false);
  for(int i=0;i<3;i++)rows.add(plain.get(i));int x=p.controlsX,y=p.top+126,w=ProgrammableDialogLayout.CONTROLS_WIDTH;
  rows.add(new DialogControl(109,x,y,w/2-1,20,"Inside: "+((geometry.walls.fill&1)!=0?"On":"Off"),false,true));rows.add(new DialogControl(110,x+w/2+1,y,w/2-1,20,"Outside: "+((geometry.walls.fill&2)!=0?"On":"Off"),false,true));y+=24;
  rows.add(new DialogControl(113,x,y,w/2-1,20,GlassTextures.tintLabel(glass.tint),false,true));rows.add(new DialogControl(114,x+w/2+1,y,w/2-1,20,glass.framed?"Framed":"Bare",false,true));y+=24;
  if(glass.material>=0){rows.add(new DialogControl(115,x,y,w/2-1,20,"Texture: "+com.vandorlabs.render.TextureLayoutMode.label(glass.textureLayout()),false,true));rows.add(new DialogControl(116,x+w/2+1,y,w/2-1,20,"Join: "+(glass.textureJoin?"On":"Off"),false,true));}
  return Collections.unmodifiableList(rows);
 }
 public static PanelGlassSettings action(PanelGlassSettings state,int id,int direction){
  if(id==114||id==FRAME)return new PanelGlassSettings(state.shade,state.material,state.layout,state.stretch,state.tint,!state.framed,state.textureJoin);
  if(id==116)return new PanelGlassSettings(state.shade,state.material,state.layout,state.stretch,state.tint,state.framed,!state.textureJoin);
  return PortholeDialog.action(state,id,direction);
 }
}
