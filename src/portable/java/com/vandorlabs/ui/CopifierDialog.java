package com.vandorlabs.ui;

import com.vandorlabs.items.DuplifierOptionCatalog;
import com.vandorlabs.items.DuplifierOptionMask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared released five-page Copifier layout, labels and append-only control identifiers. */
public final class CopifierDialog {
 public static final int WIDTH=420,CONNECTED=98,IGNORE=96,UNDO=97,DONE=99,OPTION_START=100;
 private static final DuplifierOptionMask MASK=new DuplifierOptionMask(DuplifierOptionCatalog.size());
 private static final DuplifierOptionCatalog.Option[] OPTIONS=DuplifierOptionCatalog.options();
 private static final String[] PAGES=DuplifierOptionCatalog.pages();
 public static int height(){int rows=0;for(int page=0;page<PAGES.length;page++){int count=0;for(DuplifierOptionCatalog.Option option:OPTIONS)if(option.page==page)count++;rows=Math.max(rows,(count+1)/2);}return Math.max(234,82+rows*21+40);}
 public static final class Control {
  public final int id,x,y,width,height;public final String label;public final boolean enabled,tab;
  private Control(int id,int x,int y,int width,int height,String label,boolean enabled,boolean tab){this.id=id;this.x=x;this.y=y;this.width=width;this.height=height;this.label=label;this.enabled=enabled;this.tab=tab;}
 }
 public static List<Control> controls(int left,int top,int page,long[] words,boolean connected,boolean ignore,String done){
  if(page<0||page>=PAGES.length||!MASK.valid(words))throw new IllegalArgumentException("Invalid Copifier dialog state");List<Control> result=new ArrayList<Control>();
  result.add(new Control(CONNECTED,left+14,top+26,190,18,"Connected Matching Blocks: "+(connected?"On":"Off"),true,false));
  result.add(new Control(IGNORE,left+214,top+26,190,18,"Ignore directionality: "+(ignore?"On":"Off"),true,false));
  for(int i=0;i<PAGES.length;i++)result.add(new Control(i,left+10+i*80,top+48,76,18,PAGES[i],i!=page,true));
  int visible=0;for(int i=0;i<OPTIONS.length;i++)if(OPTIONS[i].page==page){result.add(new Control(OPTION_START+i,left+14+(visible%2)*200,top+82+(visible/2)*21,190,18,OPTIONS[i].label+": "+(MASK.enabled(words,i)?"On":"Off"),true,false));visible++;}
  result.add(new Control(UNDO,left+14,top+height()-28,132,18,"Undo last application",true,false));result.add(new Control(DONE,left+162,top+height()-28,96,18,done,true,false));return Collections.unmodifiableList(result);
 }
 public static boolean cycles(int id){return id==CONNECTED||id==IGNORE||id>=OPTION_START&&id<OPTION_START+OPTIONS.length;}
 private CopifierDialog(){}
}
