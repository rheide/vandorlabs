package com.vandorlabs.render;

/** Camera-independent intersections which cannot safely become one ordered draw. */
public final class GlassPaneOrderingTest {
    private static int checks;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        for(double origin:new double[]{-30000000,-16,0,16,29999984}){
            need(GlassPaneOrdering.requiresSplit(0,origin+8,origin,origin+16,0,16,2,origin+8,origin,origin+16,0,16),"crossing panes need original granularity");
            need(!GlassPaneOrdering.requiresSplit(0,origin+8,origin,origin+16,0,16,0,origin+9,origin,origin+16,0,16),"parallel joined windows remain supported");
            need(!GlassPaneOrdering.requiresSplit(0,origin+16,origin,origin+16,0,16,2,origin,origin,origin+16,0,16),"L-shaped room edges remain supported");
            need(!GlassPaneOrdering.requiresSplit(0,origin+16,origin,origin+16,0,16,2,origin+8,origin,origin+8,0,16),"separated perpendicular walls remain supported");
            need(!GlassPaneOrdering.requiresSplit(0,origin+8,origin,origin+16,0,8,2,origin+8,origin,origin+16,8,16),"stacked floors do not force fallback");
            need(GlassPaneOrdering.requiresSplit(0,origin+8,origin,origin+16,0,9,2,origin+8,origin,origin+16,8,16),"partial vertical overlap is guarded");
            for(int x=0;x<=16;x++)for(int z=0;z<=16;z++){
                boolean expected=x>0&&x<16&&z>0&&z<16;
                boolean forward=GlassPaneOrdering.requiresSplit(0,origin+x,origin,origin+16,0,16,2,origin+z,origin,origin+16,0,16);
                boolean reverse=GlassPaneOrdering.requiresSplit(2,origin+z,origin,origin+16,0,16,0,origin+x,origin,origin+16,0,16);
                need(forward==expected,"axis intersection agrees with independent grid occupancy");
                need(forward==reverse,"ambiguity does not depend on iteration order");
            }
        }
        System.out.println("glass-pane-ordering PASS "+checks+" checks: crossing planes, L rooms, touching edges, vertical separation and world-coordinate limits");
    }
    private GlassPaneOrderingTest(){}
}
