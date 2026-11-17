package com.vandorlabs.navigation;

/** Physical scale, responsive controls and fine biome sampling never depend on assembly stretching. */
public final class NavigationCanvasTest {
    private static int assertions;
    private static void need(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        for(boolean write:new boolean[]{false,true})for(int w:new int[]{96,128,192,256,448,960,4032,65472})for(int h:new int[]{96,192,448,960,4032}){
            NavigationCanvas c=new NavigationCanvas(w,h,write);
            for(int i=0;i<c.count;i++){int action=c.actionAt(i);need(c.action(c.buttonU(action),c.buttonV(action))==action,"drawn control must hit its action");need(c.buttonX(i)>=0&&c.buttonX(i)+24<=w,"fixed button width fits");need(c.buttonY(i)>=c.mapBottom&&c.buttonY(i)+24<=h,"control outside map");}
            need(c.action(.5,0)==0,"header cannot activate");need(c.action(-1,1)==0,"outside cannot activate");
            for(int zoom=0;zoom<NavigationMap.SPANS.length;zoom++){need(c.gridWidth(zoom)<=NavigationCanvas.MAX_GRID&&c.gridHeight(zoom)<=NavigationCanvas.MAX_GRID,"bounded raster");need(c.sampleX(NavigationMap.LIMIT,zoom,c.gridWidth(zoom)-1)<=NavigationMap.LIMIT,"world edge clamp");}
        }
        for(int base:new int[]{1000,5000}){need(NavigationRange.radius(base,64)==base/2,"sea-level observation radius");need(NavigationRange.radius(base,164)==base,"height increases range");need(NavigationRange.radius(base,-100)==base/8,"underground minimum");need(NavigationRange.radius(base,100.5)>NavigationRange.radius(base,100),"fractional arm height updates radius");}need(NavigationRange.radius(0,200)==0,"non observer");
        NavigationCanvas small=new NavigationCanvas(.75,.75,true),large=new NavigationCanvas(3.75,3.75,true);
        need(small.scale(4)==large.scale(4),"larger table must preserve world units per display pixel");need(large.width>small.width*4,"larger table shows more world");need(large.gridWidth(4)>small.gridWidth(4),"extra area samples additional terrain");need(small.gridWidth(0)>small.gridWidth(4),"deeper zoom resolves more biome samples");need(small.sampleX(0,0,1)-small.sampleX(0,0,0)<=1,"deep zoom supports block-level biome boundaries");
        for(int z:new int[]{0,4,8}){double pixels=(large.width*large.scale(z))/(small.width*small.scale(z));need(Math.abs(pixels-large.width/(double)small.width)<1e-9,"fixed map scale through zoom");}
        com.vandorlabs.animation.VisualClock clock=new com.vandorlabs.animation.VisualClock();clock.sample(100,false);double before=clock.sample(50000100,false);need(before==1,"visual clock advances");need(clock.sample(5000000100L,true)==before,"pause freezes progress");need(clock.sample(9000000100L,false)==before,"resume has no jump");need(clock.sample(9050000100L,false)==before+1,"resume advances independently of server timestamps");
        need(com.vandorlabs.machines.BuilderRecipeCatalog.available("synthesis_tower",java.util.Arrays.asList("survey_dish_core","sentinel_array_core")).containsAll(java.util.Arrays.asList("standalone_dish_core","standalone_antenna_core")),"previous catalog blueprints remap");
        need(NavigationRange.radius(1000,64)==500,"sea-level baseline");need(NavigationRange.radius(5000,164)==5000,"altitude increases radius");need(NavigationRange.radius(1000,-100)==125,"minimum radius");need(NavigationRange.radius(0,100)==0,"non observer");
        System.out.println("Navigation canvas PASS assertions="+assertions+" fixed scale controls deep sampling bounds pause-aware animation");
    }
}
