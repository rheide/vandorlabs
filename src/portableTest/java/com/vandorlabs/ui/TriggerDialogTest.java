package com.vandorlabs.ui;
public final class TriggerDialogTest {
    private static int checks;
    private static void check(boolean result){checks++;if(!result)throw new AssertionError("Trigger dialog "+checks);}
    public static void main(String[] args){for(int[] viewport:new int[][]{{640,360},{427,240},{320,240}})for(boolean bands:new boolean[]{false,true})for(int exact=-1;exact<=15;exact++)for(int tab=0;tab<4;tab++){
        if(!bands&&tab!=0&&tab!=3)continue;ProgrammableDialogLayout panel=new ProgrammableDialogLayout(viewport[0],viewport[1]);TriggerDialog.State state=new TriggerDialog.State(tab,bands,exact);int visible=0;int[] order={90,91,92,93,101,102,100};int index=0;
        for(TriggerDialog.Control control:TriggerDialog.controls(panel,state)){check(control.bounds.id==order[index++]);if(control.bounds.id>=90&&control.bounds.id<=93){if(control.visible)visible++;check(control.enabled==(control.bounds.id!=90+tab));}if(control.visible){check(control.bounds.x>=panel.left);check(control.bounds.y>=panel.top);check(control.bounds.x+control.bounds.width<=panel.left+panel.width);check(control.bounds.y+control.bounds.height<=panel.top+panel.height);}}
        check(visible==(bands?4:2));check(state.action(102,-1).exact==(exact==-1?15:exact-1));check(state.action(102,1).exact==(exact==15?-1:exact+1));TriggerDialog.State toggled=state.action(101,-1);check(toggled.bands!=bands);check(toggled.tab==(!toggled.bands&&(tab==1||tab==2)?3:tab));
    }check(TriggerDialog.bandsLabel(false).equals("Artwork: Off / On"));check(TriggerDialog.exactLabel(-1).equals("Trigger: Any signal"));System.out.println("TriggerDialogTest PASS "+checks+" assertions");}
}
