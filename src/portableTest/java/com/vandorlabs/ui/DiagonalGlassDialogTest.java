package com.vandorlabs.ui;
import com.vandorlabs.persistence.*;
import java.util.*;

/** Independent frozen actual Forge button output plus action/dormant-field contracts. */
public final class DiagonalGlassDialogTest {
 private static void need(boolean ok){if(!ok)throw new AssertionError("Current diagonal glass editor contract");}
 public static void main(String[] args)throws Exception{
  java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");int cases=0;
  // Forge horizontal order is south,west,north,east. Invalid requested tint7 keeps preceding tint3.
  for(int face:new int[]{3,4,2,5})for(boolean inv:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++)for(int join=0;join<12;join++)for(int material:new int[]{-1,7})for(int requested:new int[]{0,3,7}){
   WallSettings walls=new WallSettings(mode!=0,mode==2,fill,join,false,0);PanelGlassSettings glass=new PanelGlassSettings(0,material,join%3,join%3!=1,requested==7?3:requested,(fill&1)!=0,(join&1)==0);DiagonalWallDialog.Geometry geometry=new DiagonalWallDialog.Geometry(walls,face,inv);
   for(int[] size:new int[][]{{640,360},{320,220}}){ProgrammableDialogLayout p=new ProgrammableDialogLayout(size[0],size[1]);for(DialogControl c:DiagonalGlassDialog.body(p,geometry,glass)){String value=c.id+":"+c.x+":"+c.y+":"+c.width+":"+c.height+":"+c.label+":"+c.enabled+":"+DiagonalGlassDialog.visible(c.id)+";";hash.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}cases++;}
   PanelGlassSettings tint=DiagonalGlassDialog.action(glass,113,-1);need(tint.tint==(glass.tint+3)%4&&tint.framed==glass.framed&&tint.material==material);
   PanelGlassSettings layout=DiagonalGlassDialog.action(glass,115,-1);need(layout.textureLayout()==(join%3+2)%3&&layout.textureJoin==glass.textureJoin&&layout.shade==glass.shade);
   PanelGlassSettings framed=DiagonalGlassDialog.action(glass,3008,-1);need(framed.framed!=glass.framed&&framed.material==material&&framed.tint==glass.tint&&framed.layout==glass.layout);
   PanelGlassSettings joined=DiagonalGlassDialog.action(glass,116,-1);need(joined.textureJoin!=glass.textureJoin&&joined.framed==glass.framed&&joined.layout==glass.layout);
  }
  StringBuilder text=new StringBuilder();for(byte b:hash.digest())text.append(String.format("%02x",b&255));need(text.toString().equals("b1400329944f945fa254f32182649c82319c986dd95c3a96d12cc5beda7e6a48"));
  for(int width:new int[]{180,320,640})for(int page:new int[]{195,210,211,212}){ProgrammableDialogLayout p=new ProgrammableDialogLayout(width,240);List<DialogControl> tabs=DiagonalGlassDialog.tabs(p,page);need(tabs.size()==4);for(int i=0;i<4;i++){DialogControl tab=tabs.get(i);need(tab.x==p.left+12+i*((p.width-22)/4)&&tab.width==(p.width-22)/4-2&&tab.enabled==(tab.id!=page));}need(tabs.get(0).label.equals("Glass")&&tabs.get(1).label.equals("Front"));}
  FrameSettings raw=new FrameSettings(7,8,-2,0,-1,-1,false,false,false,true),draft=raw.editor(false,false,9);need(raw.exterior==-2&&draft.front==7&&draft.exterior==8&&draft.bare&&draft.splitTop==9); // FrameBare stays dormant.
  System.out.println("Diagonal glass profile PASS actualForgeGolden="+text+" cases="+cases+" compact controls reverse actions frame-draft isolation");
 }
}
