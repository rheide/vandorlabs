package com.vandorlabs.ui;

import com.vandorlabs.persistence.TriggerState;
import java.util.Arrays;
import java.util.List;

/** Current Trigger tabs, editor transitions and preserved dormant playback. */
public final class TriggerDialogTest {
    private TriggerDialogTest() { }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static TriggerDialog.State state(int tab,int page,boolean bands,boolean front,int exact){return new TriggerDialog.State(new int[]{14,21,22,23},new int[]{2,0,1,2},new int[]{0,2,1,0},tab,page,exact,bands,front,7);}
    private static TriggerDialog.Control control(List<TriggerDialog.Control> controls,int id){for(TriggerDialog.Control c:controls)if(c.id==id)return c;throw new AssertionError("Missing control "+id);}
    private static void retains(TriggerDialog.State a,TriggerDialog.State b){require(Arrays.equals(a.textures(),b.textures())&&Arrays.equals(a.modes(),b.modes())&&Arrays.equals(a.speeds(),b.speeds())&&a.sidesTexture==b.sidesTexture,"Option/tab action changed dormant artwork");}
    public static void main(String[] args){
        require(Arrays.equals(TriggerDialog.rightClickIds(),new int[]{101,102,103}),"Only option controls accept reverse input");
        require(TriggerDialog.safeLeftDispatch(101)&&TriggerDialog.safeLeftDispatch(103)&&!TriggerDialog.safeLeftDispatch(102)&&!TriggerDialog.safeLeftDispatch(111),"Rebuild-safe click filter changed");
        int cases=0;
        for(boolean bands:new boolean[]{false,true})for(boolean front:new boolean[]{false,true})for(int page=0;page<3;page++)for(int tab=0;tab<4;tab++){
            TriggerDialog.State s=state(tab,page,bands,front,-1);
            require(TriggerState.validArtwork(s.modes(),s.speeds()),"Retained playback no longer validates against persistence policy");
            int[] exposed=s.modes();exposed[0]=99;require(s.mode(0)==2,"Mutable playback escaped editor snapshot");
            TriggerDialog.State toggled=s.action(101,-1);
            require(toggled.states!=bands&&toggled.textureTab==(!toggled.states&&(tab==1||tab==2)?3:tab)&&toggled.page==page,"Band toggle/page/cursor semantics changed");retains(s,toggled);
            toggled=s.action(103,-1);require(toggled.frontOnly!=front&&toggled.page==page&&toggled.textureTab==tab,"Front-only toggle changed cursor");retains(s,toggled);
            for(int id=90;id<=93;id++){
                TriggerDialog.State next=s.action(id,1);require(next.page==0&&next.textureTab==id-90,"Artwork tab dispatch");retains(s,next);
                require(TriggerDialog.enabled(id,s)==(page!=0||id!=90+tab),"Artwork tab enabled rule");
            }
            require(s.action(111,1).page==1&&s.action(112,1).page==2,"Redstone/Sides page dispatch");
            require(TriggerDialog.enabled(111,s)==(page!=1)&&TriggerDialog.enabled(112,s)==(page!=2),"Redstone/Sides enabled rules");
            require(TriggerDialog.label(101,s).equals(bands?"Artwork: Signal bands":"Artwork: Off / On"),"Artwork label");
            require(TriggerDialog.label(102,s).equals("Trigger: Any signal")&&TriggerDialog.label(103,s).equals(front?"Faces: Front Only":"Faces: All Faces"),"Current option labels");
            TriggerDialog.State chosen=s.choose(88);require(page==2?chosen.sidesTexture==88&&Arrays.equals(chosen.textures(),s.textures()):chosen.texture(tab)==88&&chosen.sidesTexture==7,"Picker routing changed");
            require(Arrays.equals(chosen.modes(),s.modes())&&Arrays.equals(chosen.speeds(),s.speeds()),"Texture choice discarded playback");
            for(int id=1;id<=3;id++)require(s.action(id,1).mode(tab)==id-1,"Dormant mode action changed");
            for(int id=10;id<=12;id++)require(s.action(id,1).speed(tab)==id-10,"Dormant speed action changed");
            for(int[] window:new int[][]{{320,240},{427,240},{640,360},{1280,720}}){
                ProgrammableDialogLayout p=new ProgrammableDialogLayout(window[0],window[1]);List<TriggerDialog.Control> controls=TriggerDialog.controls(p,s,"Done");
                int[] ids=bands?(front?new int[]{90,91,92,93,112,111}:new int[]{90,91,92,93,111}):(front?new int[]{90,93,112,111}:new int[]{90,93,111});
                String[] labels=bands?(front?new String[]{"0","1-5","6-10","11-15","Sides","Redstone"}:new String[]{"0","1-5","6-10","11-15","Redstone"}):(front?new String[]{"Off","On","Sides","Redstone"}:new String[]{"Off","On","Redstone"});
                int width=(p.width-22)/ids.length;
                for(int i=0;i<ids.length;i++){TriggerDialog.Control c=controls.get(i);require(c.id==ids[i]&&c.tab&&c.label.equals(labels[i])&&c.x==p.left+12+i*width&&c.y==p.top+30&&c.width==width-2&&c.height==18,"Current fullTab division/order/bounds changed");}
                require(controls.size()==ids.length+(page==1?3:0)+1,"Unexpected visible playback or option controls");
                if(page==1)for(int id=101;id<=103;id++){TriggerDialog.Control c=control(controls,id);require(!c.tab&&c.x==p.left+12&&c.y==p.top+96+(id-101)*24&&c.width==p.width-24&&c.height==20,"Redstone option bounds changed");}
                TriggerDialog.Control done=control(controls,100);require(done.x==p.controlsX&&done.y==p.doneY&&done.width==154&&done.height==20,"Done bounds");
                TriggerDialog.ChannelBox field=TriggerDialog.channelBox(p);require(field.x==p.left+12&&field.y==p.top+68&&field.width==p.width-24&&field.height==18,"Channel field geometry");
                require(TriggerDialog.listY(p)==p.top+54&&TriggerDialog.listRows(p)==p.rows(true),"Picker geometry changed");cases++;
            }
        }
        for(int exact=-1;exact<=15;exact++){
            TriggerDialog.State s=state(2,1,true,true,exact),previous=s.action(102,-1),next=s.action(102,1);
            require(previous.exact==(exact==-1?15:exact-1)&&next.exact==(exact==15?-1:exact+1),"Exact level boundaries");
            require(previous.action(102,1).exact==exact&&next.action(102,-1).exact==exact,"Reverse/forward exact cycle");retains(s,previous);
        }
        require(TriggerDialog.sendsImmediately(101)&&TriggerDialog.sendsImmediately(102)&&TriggerDialog.sendsImmediately(103)&&!TriggerDialog.sendsImmediately(90)&&!TriggerDialog.sendsImmediately(111)&&!TriggerDialog.sendsImmediately(112),"Immediate edit versus tab submission changed");
        require(TriggerDialog.rebuilds(101)&&TriggerDialog.rebuilds(103)&&!TriggerDialog.rebuilds(102),"Option rebuild policy changed");
        System.out.println("Current Trigger dialog contracts PASS layouts="+cases+" tabs/options/reverse input/dormant playback retained");
    }
}
