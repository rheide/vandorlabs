package com.vandorlabs.ui;
import com.vandorlabs.persistence.StairState;
public final class SlabStairDialogTest {
    private static int checks;private static void check(boolean b){checks++;if(!b)throw new AssertionError("Slab/stair dialog "+checks);}
    public static void main(String[] args){for(int[] viewport:new int[][]{{640,360},{427,240},{320,240}})for(boolean enabled:new boolean[]{false,true})for(boolean stairs:new boolean[]{false,true})for(int target=-2;target<6;target++){
        if(target>=0&&!enabled)continue;ProgrammableDialogLayout panel=new ProgrammableDialogLayout(viewport[0],viewport[1]);SlabStairDialog.State state=new SlabStairDialog.State(7,9,1,enabled,new int[]{-1,1,2,3,4,5},target,new StairState(8,2));int tabs=0,sliders=0,index=0;
        for(SlabStairDialog.Control c:SlabStairDialog.controls(panel,state,stairs,"Done")){int id=c.bounds.id;if(id==120||id==127||id>=121&&id<=126){tabs++;check(c.bounds.y==panel.top+30);check(c.enabled!=(id==120?target==-1:id==127?target==-2:target==id-121));}if(c.slider){sliders++;check(id==130);check(c.bounds.label.equals("Step size: 8 px"));}if(id==131)check(c.bounds.label.equals("Orientation: Sideways"));if(id==128)check(target==-2);if(id==108)check(c.enabled==(target>=0&&state.faces()[target]>=0));if(id==100)check(c.bounds.y==panel.doneY);index++;}
        check(tabs==(enabled?8:2));check(sliders==(stairs?1:0));check(index>=5);check(SlabStairDialog.rows(panel)==Math.max(2,(panel.height-60)/22));check(SlabStairDialog.listY(panel)==panel.top+54);
        check(state.action(103,-1).layout==0);check(state.action(103,1).layout==2);check(state.action(106,1).target==-1);check(state.action(106,1).facesEnabled!=enabled);check(state.action(128,1).side==-1);check(state.action(128,1).target==-2);check(state.action(131,-1).stairs.roll==0);
        SlabStairDialog.State chosen=state.choose(123);check(target==-2?chosen.side==123:target>=0?chosen.faces()[target]==123:chosen.main==123);check(chosen.selected()==123);if(target>=0)check(state.action(108,1).selected()==7);
    }System.out.println("SlabStairDialogTest PASS "+checks+" checks");}
}
