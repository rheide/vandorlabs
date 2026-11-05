package com.vandorlabs.client;

import com.vandorlabs.ui.InventorySampleLayout;
import java.lang.reflect.*;

/** Actual legacy wrapper comparisons without stacks, a Minecraft instance or rendering. */
public final class InventorySampleGridChecks {
    private InventorySampleGridChecks() { }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int invoke(InventorySamplePicker picker,String name,Class<?>[] types,Object... args)throws Exception{Method m=InventorySamplePicker.class.getDeclaredMethod(name,types);m.setAccessible(true);return (Integer)m.invoke(picker,args);}
    public static void main(String[] ignored)throws Exception{
        Field scroll=InventorySamplePicker.class.getDeclaredField("scroll");scroll.setAccessible(true);int cases=0;
        for(int width:new int[]{13,18,55,162,220})for(int height:new int[]{10,23,34,52,100}){
            int[] back={0},chosen={0};InventorySamplePicker picker=new InventorySamplePicker(10,20,width,height,s->false,s->chosen[0]++,()->back[0]++);
            InventorySampleLayout layout=new InventorySampleLayout(10,20,width,height);
            require(invoke(picker,"columns",new Class<?>[0])==layout.columns()&&invoke(picker,"visible",new Class<?>[0])==layout.visibleRows()&&invoke(picker,"maxScroll",new Class<?>[0])==layout.maxScroll(),"Legacy dimensions differ");
            for(int offset:new int[]{0,layout.maxScroll()}){
                scroll.setInt(picker,offset);
                for(int dx:new int[]{-1,0,12,17,18,width-1,width,layout.columns()*18-1,layout.columns()*18})for(int dy:new int[]{-1,0,13,14,15,16,height-1,height,16+layout.visibleRows()*18-1,16+layout.visibleRows()*18}){
                    int mx=10+dx,my=20+dy;require(invoke(picker,"index",new Class<?>[]{int.class,int.class},mx,my)==layout.slotAt(mx,my,offset),"Actual raw hover/index differs");
                    require(picker.click(mx,my,1)==layout.contains(mx,my),"Right-click panel consumption differs");cases++;
                }
            }
            require(chosen[0]==0&&back[0]==0,"Right click invoked stack/catalog callback");
            int[] before={scroll.getInt(picker)};require(!picker.wheel(10,20,0)&&scroll.getInt(picker)==before[0],"Zero wheel mutated state");
            require(!picker.wheel(10+width,20,-120)&&scroll.getInt(picker)==before[0],"Outside wheel mutated state");
            require(picker.wheel(10,20,120)&&scroll.getInt(picker)==layout.scrollAfterWheel(before[0],120),"Header wheel/normalization differs");
            require(picker.click(10,20,0)&&back[0]==1,"Catalog header callback differs");
            if(height>15){picker.click(10,34,0);picker.click(10,35,0);require(back[0]==1&&chosen[0]==0,"14-15px gap invoked callback");}
        }
        InventorySamplePicker hotbar=InventorySamplePicker.hotbar(3,4,s->false,s->{});require(hotbar.width==162&&hotbar.height==34&&invoke(hotbar,"index",new Class<?>[]{int.class,int.class},164,37)==8&&invoke(hotbar,"index",new Class<?>[]{int.class,int.class},3,38)==-1,"Actual hotbar rectangle/order differs");
        System.out.println("Legacy inventory sample grid PASS comparisons="+cases+" raw/outer bounds header gap wheel callback and9slot factory");
    }
}
