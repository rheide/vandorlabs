package com.vandorlabs.client;

import net.minecraft.client.gui.GuiButton;

/** Exercise the actual legacy wrapper and native widget bounds without a rendered client. */
public final class DialogPanelDimensionsChecks {
    private DialogPanelDimensionsChecks() { }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void bounds(GuiButton b,int id,int x,int y,int width,int height){
        require(b.id==id&&b.x==x&&b.y==y&&b.width==width&&b.height==height,"Widget bounds changed: "+id);
    }
    public static void main(String[] ignored){
        require(HousingTextureList.ROW_HEIGHT==com.vandorlabs.ui.ProgrammableDialogLayout.ROW_HEIGHT,"Legacy picker no longer uses the shared22px row contract");
        int count=0;
        for(int[] size:new int[][]{{180,120},{320,240},{427,240},{640,360},{641,361},{1280,720}})for(int[] cap:new int[][]{{340,216},{460,332},{700,400}}){
            ProgrammableDialogLayout legacy=new ProgrammableDialogLayout(size[0],size[1],cap[0],cap[1]);
            com.vandorlabs.ui.ProgrammableDialogLayout shared=new com.vandorlabs.ui.ProgrammableDialogLayout(size[0],size[1],cap[0],cap[1]);
            require(legacy.left==shared.left&&legacy.top==shared.top&&legacy.width==shared.width&&legacy.height==shared.height&&legacy.controlsX==shared.controlsX&&legacy.listX==shared.listX&&legacy.listWidth==shared.listWidth&&legacy.doneY==shared.doneY,"Legacy coordinate projection differs");
            require(legacy.rows(false)==shared.rows(false)&&legacy.rows(true)==shared.rows(true),"Legacy allocated rows differ");
            bounds(legacy.control(103,54,"Texture"),103,shared.controlsX,shared.top+54,154,20);
            bounds(legacy.done(100),100,shared.controlsX,shared.doneY,154,20);
            for(int tabs:new int[]{1,2,3,7})for(int index=0;index<tabs;index++){
                int width=(shared.width-22)/tabs;
                bounds(legacy.fullTab(120+index,index,tabs,"Tab"),120+index,shared.left+12+index*width,shared.top+30,width-2,18);
                int choiceWidth=156/tabs;
                bounds(legacy.choice(200+index,index,tabs,78,"Choice"),200+index,shared.controlsX+index*choiceWidth,shared.top+78,choiceWidth-2,18);
            }
            count++;
        }
        System.out.println("Legacy dialog panel dimensions PASS cases="+count+" shared fields/rows native control/Done/fullTab/choice bounds");
    }
}
