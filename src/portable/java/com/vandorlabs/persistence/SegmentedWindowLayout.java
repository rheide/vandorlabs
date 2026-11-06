package com.vandorlabs.persistence;
import java.util.*;
/** Derived connected window coordinates. Rows increase upward from the structure bottom. */
public final class SegmentedWindowLayout {
 public static final SegmentedWindowLayout SINGLE=new SegmentedWindowLayout(0,0,1,1);
 public final int column,row,width,height;
 public SegmentedWindowLayout(int column,int row,int width,int height){if(width<1||height<1||width>32||height>32||column<0||column>=width||row<0||row>=height)throw new IllegalArgumentException("Invalid Segmented window layout");this.column=column;this.row=row;this.width=width;this.height=height;}
 public interface Cells {boolean joined(int column,int row);int shape(int column,int row);int facing(int column,int row);}
 /** Caller supplies only loaded same-structure cells; no world access or ownership is retained. */
 public static SegmentedWindowLayout[] derive(int columns,int rows,Cells cells){
  if(columns<1||columns>32||rows<1||rows>32||cells==null)throw new IllegalArgumentException("Invalid Segmented window grid");
  SegmentedWindowLayout[] result=new SegmentedWindowLayout[columns*rows];boolean[] seen=new boolean[result.length];int[] queue=new int[result.length];
  for(int start=0;start<result.length;start++){
   if(seen[start])continue;int x=start%columns,y=start/columns;seen[start]=true;if(!cells.joined(x,y)){result[start]=SINGLE;continue;}
   int shape=cells.shape(x,y),facing=cells.facing(x,y),head=0,tail=1,minX=x,maxX=x,minY=y,maxY=y;queue[0]=start;
   while(head<tail){int at=queue[head++],cx=at%columns,cy=at/columns;minX=Math.min(minX,cx);maxX=Math.max(maxX,cx);minY=Math.min(minY,cy);maxY=Math.max(maxY,cy);
    for(int side=0;side<4;side++){int nx=cx+(side==2?-1:side==3?1:0),ny=cy+(side==0?1:side==1?-1:0);if(nx<0||nx>=columns||ny<0||ny>=rows)continue;int next=ny*columns+nx;if(seen[next]||!cells.joined(nx,ny)||cells.shape(nx,ny)!=shape||cells.facing(nx,ny)!=facing)continue;seen[next]=true;queue[tail++]=next;}
   }
   for(int i=0;i<tail;i++){int at=queue[i];result[at]=new SegmentedWindowLayout(at%columns-minX,at/columns-minY,maxX-minX+1,maxY-minY+1);}
  }
  return result;
 }
 @Override public boolean equals(Object o){if(!(o instanceof SegmentedWindowLayout))return false;SegmentedWindowLayout v=(SegmentedWindowLayout)o;return column==v.column&&row==v.row&&width==v.width&&height==v.height;}
 @Override public int hashCode(){return ((column*31+row)*31+width)*31+height;}
}
