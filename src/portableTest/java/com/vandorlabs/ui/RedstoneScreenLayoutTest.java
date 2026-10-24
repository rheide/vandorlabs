package com.vandorlabs.ui;

import com.vandorlabs.render.ScreenSurface;
import java.util.*;

/** Authored overlay submissions and actual front-plane ray fixtures, including all six mounts. */
public final class RedstoneScreenLayoutTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static double[] world(double x,double y,double z,int face){x=x/16-.5;y=y/16-.5;z=z/16-.5;double wx=x,wy=y,wz=z;switch(face){case 5:wx=-z;wz=x;break;case 3:wx=-x;wz=-z;break;case 4:wx=z;wz=-x;break;case 1:wx=-x;wy=-z;wz=-y;break;case 0:wy=z;wz=-y;break;default:break;}return new double[]{wx+.5,wy+.5,wz+.5};}
    private static RedstoneScreenLayout.Hit hit(RedstoneScreenLayout layout,int face,double u,double v){double x=16-u/8,y=16-v*16/layout.height;return layout.hit(ScreenSurface.quad(ScreenSurface.Kind.FLAT,false),face,world(x,y,-16,face),world(x,y,16,face),layout.maxRows);}
    public static void main(String[] args){
        for(boolean half:new boolean[]{false,true}){
            RedstoneScreenLayout layout=new RedstoneScreenLayout(half);check(layout.height==(half?64:128)&&layout.maxRows==(half?4:8)&&layout.header!=half,"full/half capacities and header");
            for(int face=0;face<6;face++){
                for(int row=0;row<layout.maxRows;row++){
                    varCheck(hit(layout,face,100,layout.rowTop+row*12+5),row,100,"six-direction rendered row center");
                    check(hit(layout,face,100,layout.rowTop+row*12+10).row==-1,"two-pixel row gap cannot activate");
                }
                check(hit(layout,face,6,layout.rowTop+5).row==0,"inclusive row left edge");check(hit(layout,face,122,layout.rowTop+5).row==0,"inclusive row right edge");
                check(hit(layout,face,5.99,layout.rowTop+5).row==-1&&hit(layout,face,122.01,layout.rowTop+5).row==-1,"outside row edges rejected");
                double[] a=world(8,8,-16,face),b=world(8,8,16,face);check(layout.hit(ScreenSurface.quad(ScreenSurface.Kind.FLAT,false),face,b,a,8).row==-1,"back-face ray rejected");
            }
        }
        for(int segment=0;segment<4;segment++)check(RedstoneScreenLayout.sliderSegment(60+(segment+.5)*15,4)==segment,"slider hit uses authored four detents");
        check(RedstoneScreenLayout.sliderSegment(59.99,4)==-1&&RedstoneScreenLayout.sliderSegment(120,4)==-1,"slider endpoints reject label and outer edge");
        List<RedstoneScreenOverlay.Row> rows=Arrays.asList(new RedstoneScreenOverlay.Row("Door",false,false,true,4,0),new RedstoneScreenOverlay.Row("Throttle",true,false,false,4,2),new RedstoneScreenOverlay.Row("Depart",false,true,false,4,0));
        RedstoneScreenOverlay overlay=new RedstoneScreenOverlay(new RedstoneScreenLayout(false),"REDSTONE CONTROL",rows);
        check(overlay.solids.size()==15&&overlay.text.size()==6,"batched solid/text submission counts");
        check(overlay.solids.get(0).color==0xFF0A141D&&overlay.solids.get(1).color==0xFF23465A,"authored background/header colors");
        check(overlay.text.get(0).value.equals("REDSTONE CONTROL")&&overlay.text.get(0).maxWidth==112,"title budget and text");
        check(overlay.text.get(2).value.equals("ON")&&overlay.text.get(5).value.equals("GO"),"toggle/pulse status labels");
        check(overlay.text.get(3).maxWidth==50,"slider label budget");
        RedstoneScreenOverlay.RowAccess<RedstoneScreenOverlay.Row> access=new RedstoneScreenOverlay.RowAccess<RedstoneScreenOverlay.Row>(){
            public String label(RedstoneScreenOverlay.Row row){return row.label;}public boolean slider(RedstoneScreenOverlay.Row row){return row.slider;}public boolean pulse(RedstoneScreenOverlay.Row row){return row.pulse;}public boolean active(RedstoneScreenOverlay.Row row){return row.active;}public int segments(RedstoneScreenOverlay.Row row){return row.segments;}public int selected(RedstoneScreenOverlay.Row row){return row.selected;}
        };
        RedstoneScreenOverlay.Cache cache=new RedstoneScreenOverlay.Cache();List<RedstoneScreenOverlay.Row> state=new ArrayList<>(rows);
        RedstoneScreenOverlay cached=cache.plan(RedstoneScreenLayout.of(false),"REDSTONE CONTROL",state,access);
        for(int i=0;i<10000;i++)check(cache.plan(RedstoneScreenLayout.of(false),"REDSTONE CONTROL",state,access)==cached,"unchanged draw reuses exact immutable plan");
        check(cache.builds()==1&&cache.rowsCreated()==3&&cache.rectsCreated()==15&&cache.textsCreated()==6,"unchanged draws allocate no new plan rows/rectangles/text");
        for(int i=0;i<overlay.solids.size();i++){RedstoneScreenOverlay.Rect a=overlay.solids.get(i),b=cached.solids.get(i);check(a.left==b.left&&a.top==b.top&&a.right==b.right&&a.bottom==b.bottom&&a.layer==b.layer&&a.color==b.color,"cache preserves exact solid submissions");}
        check(cache.plan(RedstoneScreenLayout.of(false),"PORT CONTROL",state,access)!=cached&&cache.builds()==2,"title invalidates plan");
        state.set(0,new RedstoneScreenOverlay.Row("Hatch",false,false,true,4,0));cache.plan(RedstoneScreenLayout.of(false),"PORT CONTROL",state,access);check(cache.builds()==3,"label invalidates plan");
        state.set(0,new RedstoneScreenOverlay.Row("Hatch",false,false,false,4,0));cache.plan(RedstoneScreenLayout.of(false),"PORT CONTROL",state,access);check(cache.builds()==4,"linked latch visibility invalidates plan");
        state.set(0,new RedstoneScreenOverlay.Row("Hatch",false,true,false,4,0));cache.plan(RedstoneScreenLayout.of(false),"PORT CONTROL",state,access);check(cache.builds()==5,"pulse kind invalidates plan");
        state.set(1,new RedstoneScreenOverlay.Row("Throttle",true,false,false,4,3));cache.plan(RedstoneScreenLayout.of(false),"PORT CONTROL",state,access);check(cache.builds()==6,"slider selection invalidates plan");
        state.set(1,new RedstoneScreenOverlay.Row("Throttle",false,false,false,4,3));cache.plan(RedstoneScreenLayout.of(false),"PORT CONTROL",state,access);check(cache.builds()==7,"slider kind invalidates plan");
        state.remove(2);cache.plan(RedstoneScreenLayout.of(false),"PORT CONTROL",state,access);check(cache.builds()==8,"row count invalidates plan");
        cache.plan(RedstoneScreenLayout.of(true),"PORT CONTROL",state,access);check(cache.builds()==9,"surface dimensions invalidate plan");
        System.out.println("Overlay plan construction counts: 10001 unchanged draws, 1 plan, 3 snapshot rows, 15 rectangles, 6 text runs; subsequent unchanged plan lookups create none of these objects");
        boolean selected=false;for(RedstoneScreenOverlay.Rect rect:overlay.solids)if(rect.color==0xFF38DDE4){check(rect.left==91&&rect.right==104&&rect.top==37&&rect.bottom==45,"selected detent exact inset");selected=true;}check(selected,"selected slider detent emitted");
        RedstoneScreenOverlay empty=new RedstoneScreenOverlay(new RedstoneScreenLayout(true),"unused",Collections.emptyList());check(empty.solids.size()==1&&empty.text.get(0).value.equals("No controls")&&empty.text.get(1).value.equals("Configure to add"),"half-screen empty text and absent header");
        RedstoneDialogLayout dialog=new RedstoneDialogLayout(320,240,true);check(dialog.left==6&&dialog.top==6&&dialog.width==308&&dialog.height==228&&dialog.controlsX==150&&dialog.visibleRows()==6,"minimum GUI editor fixture");
        List<RedstoneDialogLayout.Control> controls=dialog.controls(false,0,1,8,1,5,15);int[] expected={0,1,2,3,5,6,7,8,9,4};for(int i=0;i<expected.length;i++)check(controls.get(i).bounds.id==expected[i],"legacy editor operation order");
        check(controls.get(6).bounds.label.equals("Control: Slider")&&controls.get(7).bounds.label.equals("Low: 5")&&controls.get(8).bounds.label.equals("High: 15"),"editor semantic labels");
        check(RedstoneDialogLayout.nextKind(0,-1)==2&&RedstoneDialogLayout.nextLow(1,15,-1)==13&&RedstoneDialogLayout.nextHigh(5,15,1)==7,"right-click and bounded range wrap");
        check(RedstoneDialogLayout.sliderLabel("abcdefghijklmnop",text->text.length()*6).equals("abcdefgh"),"font-measured slider label truncation");
        List<String> labels=new ArrayList<>(Arrays.asList("A","B"));List<Integer> channels=new ArrayList<>(Arrays.asList(10,20));check(RedstoneDialogLayout.moveRows(labels,channels,0,1)==1&&labels.equals(Arrays.asList("B","A"))&&channels.equals(Arrays.asList(20,10)),"row move retains channel pairing");
        System.out.println("RedstoneScreenLayoutTest PASS "+checks+" assertions");
    }
    private static void varCheck(RedstoneScreenLayout.Hit hit,int row,double column,String message){check(hit.row==row&&Math.abs(hit.column-column)<1e-8,message);}
}
