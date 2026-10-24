package com.vandorlabs.ui;

import com.vandorlabs.cockpit.CockpitScreenLayout;
import java.util.*;

/** Logical GUI fixtures run unchanged by both platform builds. */
public final class DialogContractChecks {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void equal(int actual,int expected,String message){check(actual==expected,message+": "+actual+" != "+expected);}
    public static void main(String[] args){
        int[][] fixtures={{854,480,197,74,460,332,491,209,263,380,12,13},{320,240,4,4,312,232,150,16,115,210,7,8},{240,180,4,4,232,172,70,16,35,150,5,6}};
        for(int[] f:fixtures){
            ProgrammableDialogLayout panel=new ProgrammableDialogLayout(f[0],f[1]);
            int[] actual={panel.left,panel.top,panel.width,panel.height,panel.controlsX,panel.listX,panel.listWidth,panel.doneY,panel.rows(true),panel.rows(false)};
            for(int i=0;i<actual.length;i++)equal(actual[i],f[i+2],"panel fixture "+f[0]+" component "+i);
            ProgrammableDialogLayout.Button done=panel.doneBounds(7);
            equal(done.id,7,"Done operation ID");check("Done".equals(done.label),"Done label");
            check(done.contains(done.x,done.y)&&done.contains(done.x+153,done.y+19),"button includes inner bounds");
            check(!done.contains(done.x+154,done.y)&&!done.contains(done.x,done.y+20),"button excludes outer bounds");
            ProgrammableDialogLayout.Button tab=panel.tabBounds(32,1,3,"Artwork");
            equal(tab.x,panel.listX+(panel.listWidth+2)/3,"second tab origin");equal(tab.height,18,"tab height");
            ProgrammableDialogLayout.Button choice=panel.choiceBounds(9,1,2,54,"Previous");
            equal(choice.width,76,"two choices fit settings column");equal(choice.y,panel.top+54,"choice offset");
        }
        for(int count=1;count<=32;count++)for(int value=0;value<count;value++){
            equal(DialogInput.cycle(DialogInput.cycle(value,count,1),count,-1),value,"opposite option clicks cancel");
            equal(DialogInput.cycle(value+5,5,count+4,1),5+(value+1)%count,"bounded option interval");
        }
        equal(DialogInput.cycle(0,3,DialogInput.direction(1)),2,"right click wraps previous");
        equal(DialogInput.cycle(2,3,DialogInput.direction(0)),0,"left click wraps next");
        equal(DialogInput.wheel(0,20,120),0,"wheel upper boundary");equal(DialogInput.wheel(20,20,-120),20,"wheel lower boundary");
        equal(DialogInput.wheel(8,20,0),8,"zero wheel retains row");equal(DialogInput.wheel(8,20,-120),9,"wheel advances one row");
        equal(DialogInput.thumbHeight(220,10,50),44,"scrollbar thumb fixture");equal(DialogInput.thumbY(30,220,44,20,40),118,"scrollbar middle fixture");
        equal(DialogInput.scrollForDrag(128,30,220,44,40,10),20,"drag retains grab offset");
        equal(DialogInput.scrollForDrag(-200,30,220,44,40,10),0,"drag clamps top");equal(DialogInput.scrollForDrag(500,30,220,44,40,10),40,"drag clamps bottom");
        equal(DialogInput.scrollForDrag(99,30,20,20,40,0),0,"no thumb travel");
        List<String> categories=TextureListLayout.categories(Arrays.asList("Walls/Metal","armor","Walls/Metal/Blue","Walls/Glass"));
        check(categories.equals(Arrays.asList("armor","Walls","Walls/Glass","Walls/Metal","Walls/Metal/Blue")),"stable parent-first category labels");
        Map<String,List<Integer>> groups=new HashMap<>();groups.put("Walls/Metal",Arrays.asList(12,4));groups.put("Walls/Metal/Blue",Arrays.asList(99));
        Set<String> expanded=new HashSet<>(Arrays.asList("Walls","Walls/Metal"));
        List<Integer> rows=TextureListLayout.rows(categories,groups,expanded,-100000,-100001,-100002);
        check(rows.equals(Arrays.asList(-1,-2,-3,-4,12,4,-5,-100000,-100001,-100002)),"expanded order and stable special rows");
        expanded.remove("Walls");check(TextureListLayout.rows(categories,groups,expanded).equals(Arrays.asList(-1,-2)),"collapsed parent hides descendants");
        equal(TextureListLayout.pinnedChoice(0,12,categories,"Walls/Metal"),-4,"first visible texture pins category");
        equal(TextureListLayout.pinnedChoice(1,12,categories,"Walls/Metal"),12,"later texture rows remain choices");
        check(TextureListLayout.compareOptions("Alpha",8,"alpha",9)<0,"case-insensitive label ties use stable IDs");
        ProgrammableDialogLayout blockPanel=new ProgrammableDialogLayout(854,480);
        equal(ProgrammableBlockDialog.savedMainTexture(13,false),13,"legacy finish before removed vent");
        equal(ProgrammableBlockDialog.savedMainTexture(14,false),0,"legacy removed vent falls back");
        equal(ProgrammableBlockDialog.savedMainTexture(15,false),14,"legacy finish after removed vent");
        equal(ProgrammableBlockDialog.savedMainTexture(14,true),14,"versioned finish unchanged");
        ProgrammableBlockDialog.State block=new ProgrammableBlockDialog.State(26,0,false,null,-1);
        List<ProgrammableBlockDialog.Control> blockControls=ProgrammableBlockDialog.controls(blockPanel,block);
        check(blockControls.stream().map(c->c.bounds.label).collect(java.util.stream.Collectors.toList()).equals(Arrays.asList("Face overrides: Off","Texture: Stretch","Done")),"plain block default labels and order");
        check(blockControls.stream().map(c->c.bounds.id).collect(java.util.stream.Collectors.toList()).equals(Arrays.asList(106,103,100)),"legacy plain block operation IDs");
        equal(blockControls.get(0).bounds.y,104,"default overrides position");equal(blockControls.get(1).bounds.y,128,"default layout position");
        equal(ProgrammableBlockDialog.listRows(blockPanel,block),12,"block list reserves bottom padding");
        block=block.action(106,-1);check(block.facesEnabled&&block.target==-1,"toggle enables face overrides and selects main");
        blockControls=ProgrammableBlockDialog.controls(blockPanel,block);
        check(blockControls.subList(0,7).stream().map(c->c.bounds.label).collect(java.util.stream.Collectors.toList()).equals(Arrays.asList("Main","Bottom","Top","Front","Back","Left","Right")),"face labels and saved face order");
        check(!blockControls.get(0).enabled&&!blockControls.get(8).enabled,"selected Main and unavailable inherit disabled");
        equal(blockControls.get(6).bounds.width,62,"last tab consumes division remainder");
        block=block.action(123,1);equal(block.target,2,"front face operation mapping");equal(block.selectedTexture(),26,"unset face inherits main");
        block=block.choose(42);equal(block.face(2),42,"face texture choice");equal(block.mainTexture,26,"face does not replace main texture");
        block=block.action(108,1);equal(block.face(2),-1,"inherit clears override");
        block=block.action(103,-1);equal(block.layout,2,"right layout click selects previous saved mode");
        int[] defensiveFaces=block.faces();defensiveFaces[2]=999;equal(block.face(2),-1,"state copies exported face values");
        block=block.action(120,1).choose(77);equal(block.mainTexture,77,"main tab changes main texture");
        block=block.action(106,1);check(!block.facesEnabled&&block.target==-1,"disable overrides resets selection");
        ScreenSelectorDialog.State screen=new ScreenSelectorDialog.State(false,0,2,1,0,0,false,true);
        List<ScreenSelectorDialog.Control> screenControls=ScreenSelectorDialog.controls(blockPanel,screen,key->key.substring(key.lastIndexOf('.')+1));
        check(screenControls.stream().map(c->c.bounds.id).collect(java.util.stream.Collectors.toList()).equals(Arrays.asList(90,91,1,2,3,10,11,12,4,0,32,20)),"screen operation order");
        check(screenControls.stream().map(c->c.bounds.label).collect(java.util.stream.Collectors.toList()).equals(Arrays.asList("Screen","Housing","off","static","animated","slow","normal","fast","[ ] framed","Trigger: Disabled","Sides: Stretch","Done")),"screen labels");
        check(!screenControls.get(4).enabled&&!screenControls.get(6).enabled,"selected animation mode and speed disabled");
        screen=screen.action(0,-1);check(screen.redstoneEnabled()&&screen.redstoneInverted(),"reverse trigger cycle selects redstone OFF");
        screen=screen.action(0,1);check(!screen.redstoneEnabled()&&!screen.redstoneInverted(),"trigger cycle restores Disabled");
        screen=screen.action(1,1).action(12,1).action(32,-1).action(4,-1);
        equal(screen.mode,0,"Off mode operation");equal(screen.speed,2,"Fast speed operation");equal(screen.sides,2,"reverse sides layout");check(screen.framed,"right framing toggle");
        ScreenSelectorDialog.State console=new ScreenSelectorDialog.State(true,1,1,0,1,1,true,true);
        check(ScreenSelectorDialog.controls(blockPanel,console,key->key).subList(0,3).stream().map(c->c.bounds.label).collect(java.util.stream.Collectors.toList()).equals(Arrays.asList("Screen","Controls","Housing")),"console tab order");
        equal(console.action(92,1).tab,2,"console housing tab operation");
        equal(ScreenSelectorDialog.channelBounds(blockPanel).y,251,"screen channels retain baseline field coordinate");
        for(String role:Arrays.asList("screen","touch"))for(int requested=0;requested<=8;requested++){
            CockpitScreenLayout layout=new CockpitScreenLayout(2,1,role,requested);
            equal(layout.height,64,"authored surface aspect uses square pixels");equal(layout.rows,Math.min(requested,role.equals("screen")?6:3),"screen/touch capacity");
            if(layout.rows==0){equal(layout.row(.5),-1,"empty screen has no hit row");continue;}
            for(int row=0;row<layout.rows;row++){
                equal(layout.row(1-(row+.5)/layout.rows),row,"render row maps to same hit row");
                check(layout.widgetTop(row)>=layout.top(row)&&layout.widgetTop(row)+10<=layout.bottom(row),"widget fits row");
            }
            equal(layout.row(1),0,"top edge row");equal(layout.row(0),layout.rows-1,"bottom edge row");
            for(int step=0;step<4;step++)check(layout.sliderColumn((60+(step+.5)*15)/128D)==step/3D,"four throttle hit steps");
        }
        System.out.println("DialogContractChecks PASS "+checks+" assertions");
    }
}
