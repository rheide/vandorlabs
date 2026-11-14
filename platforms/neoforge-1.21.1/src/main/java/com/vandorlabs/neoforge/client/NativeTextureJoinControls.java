package com.vandorlabs.neoforge.client;

import com.vandorlabs.surface.TextureJoinSettings;
import com.vandorlabs.ui.*;
import java.util.*;

/** Texture layout, connectivity, and neighbor filter occupy consecutive matching rows. */
final class NativeTextureJoinControls {
 static final int JOIN=240,PARITY=241;
 private static final class Row extends DialogControl {Row(int id,int x,int y,int w,int h,String label,boolean tab,boolean enabled){super(id,x,y,w,h,label,tab,enabled);}}
 static List<DialogControl> add(List<? extends DialogControl> original,TextureJoinSettings settings,ProgrammableDialogLayout panel){
  List<DialogControl> result=new ArrayList<>();int anchor=-1,x=panel.controlsX,w=154;DialogControl oldJoin=null;
  for(var row:original){if(!row.tab&&row.label.startsWith("Join: "))oldJoin=row;if(!row.tab&&row.label.startsWith("Texture: ")){anchor=row.y+24;}}
  if(anchor<0&&oldJoin==null)return List.copyOf(original);
  if(oldJoin!=null&&anchor<0){anchor=oldJoin.y;x=oldJoin.x;w=oldJoin.width;}
  for(var row:original){if(row==oldJoin)continue;int y=row.y;if(!row.tab&&row.y>=anchor&&row.id!=ProgrammableBlockDialog.DONE&&row.id!=GlassDialog.DONE)y+=48;result.add(new Row(row.id,row.x,y,oldJoin!=null&&oldJoin.y==row.y&&row.label.startsWith("Texture: ")?154:row.width,row.height,row.label,row.tab,row.enabled));}
  result.add(new Row(JOIN,x,anchor,w,20,settings.directionLabel(),false,true));result.add(new Row(PARITY,x,anchor+24,w,20,settings.parityLabel(),false,settings.directional()));
  TreeSet<Integer> ys=new TreeSet<>();for(var row:result)if(!row.tab&&row.id!=ProgrammableBlockDialog.DONE&&row.id!=GlassDialog.DONE)ys.add(row.y);
  if(!ys.isEmpty()&&ys.last()+20>panel.doneY-4){int first=ys.first(),step=Math.max(14,Math.min(24,(panel.doneY-4-first)/ys.size()));List<Integer> order=new ArrayList<>(ys);List<DialogControl> packed=new ArrayList<>();for(var row:result)packed.add(new Row(row.id,row.x,row.tab||row.id==ProgrammableBlockDialog.DONE||row.id==GlassDialog.DONE?row.y:first+order.indexOf(row.y)*step,row.width,Math.min(row.height,step-2),row.label,row.tab,row.enabled));result=packed;}
  return List.copyOf(result);
 }
 private NativeTextureJoinControls(){}
}
