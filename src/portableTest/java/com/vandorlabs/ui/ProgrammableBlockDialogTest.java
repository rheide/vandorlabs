package com.vandorlabs.ui;

import java.util.List;

/** Current cube layout, immediate edit semantics and bounded picker input. */
public final class ProgrammableBlockDialogTest {
    private ProgrammableBlockDialogTest() { }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ProgrammableBlockDialog.Control control(List<ProgrammableBlockDialog.Control> controls,int id){for(ProgrammableBlockDialog.Control c:controls)if(c.id==id)return c;throw new AssertionError("Missing control "+id);}
    public static void main(String[] args){
        int[] faces={-1,1,-1,3,4,5};
        ProgrammableBlockDialog.State state=new ProgrammableBlockDialog.State(9,1,false,faces,-1);faces[1]=99;
        require(state.face(1)==1,"Editor borrowed mutable caller array");
        state=state.action(106,-1);require(state.facesEnabled&&state.target==-1,"Reverse toggle semantics");
        state=state.action(123,1);require(state.target==2&&state.selectedTexture()==9,"Unassigned face must inherit main");
        ProgrammableBlockDialog.State chosen=state.choose(20);require(chosen.face(2)==20&&state.face(2)==-1,"Face edit must preserve previous state");
        chosen=chosen.action(108,1);require(chosen.face(2)==-1&&chosen.selectedTexture()==9,"Inherit must retain main");
        chosen=chosen.action(106,1);require(!chosen.facesEnabled&&chosen.target==-1&&chosen.face(3)==3,"Disabling overrides must preserve stored faces");
        chosen=chosen.choose(12);require(chosen.mainTexture==12&&chosen.face(3)==3,"Main edit overwrote face settings");
        require(chosen.action(103,-1).layout==0&&chosen.action(103,-1).action(103,-1).layout==2,"Previous layout wrap");
        require(chosen.action(103,-1).action(103,1).layout==1,"Forward/reverse layout parity");
        for(int[] dimensions:new int[][]{{320,240},{460,332},{1280,720}})for(boolean enabled:new boolean[]{false,true}){
            ProgrammableDialogLayout p=new ProgrammableDialogLayout(dimensions[0],dimensions[1]);
            ProgrammableBlockDialog.State s=new ProgrammableBlockDialog.State(9,1,enabled,new int[]{-1,-1,-1,-1,-1,-1},-1);
            List<ProgrammableBlockDialog.Control> controls=ProgrammableBlockDialog.controls(p,s,"Done");
            ProgrammableBlockDialog.Control appearance=control(controls,195);
            require(appearance.y==p.top+30&&appearance.width==p.width-24&&!appearance.enabled&&appearance.tab,"Current Appearance row changed");
            require(ProgrammableBlockDialog.listY(p,s)==p.top+(enabled?78:54),"Picker overlaps tabs");
            require(control(controls,106).y==ProgrammableBlockDialog.listY(p,s),"First control and picker must align");
            require(control(controls,100).y==p.top+p.height-26,"Done baseline changed");
            if(enabled){require(control(controls,121).label.equals("Down")&&control(controls,122).label.equals("Up"),"Current local face labels changed");require(!control(controls,108).enabled,"Inherit without selected face enabled");}
            for(ProgrammableBlockDialog.Control c:controls)require(c.x>=p.left&&c.x+c.width<=p.left+p.width&&c.y>=p.top&&c.y+c.height<=p.top+p.height,"Control exceeds panel: "+c.id);
        }
        require(DialogInput.maxScroll(20,4)==16&&DialogInput.thumbHeight(88,4,20)==17&&DialogInput.thumbY(100,88,17,16,16)==171,"Scrollbar sizing and last-row position");
        require(DialogInput.scrollForDrag(105,100,100,20,10,5)==0&&DialogInput.scrollForDrag(145,100,100,20,10,5)==5&&DialogInput.scrollForDrag(185,100,100,20,10,5)==10,"Drag endpoints/midpoint");
        require(DialogInput.scrollForDrag(-100,100,100,20,10,5)==0&&DialogInput.scrollForDrag(999,100,100,20,10,5)==10,"Drag clamping");
        require(DialogInput.scrollForDrag(999,0,10,10,10,0)==0,"Degenerate track");
        require(DialogInput.wheel(0,10,1)==0&&DialogInput.wheel(10,10,-1)==10&&DialogInput.wheel(4,10,-120)==5,"One row wheel steps");
        System.out.println("Current programmable cube dialog contracts PASS");
    }
}
