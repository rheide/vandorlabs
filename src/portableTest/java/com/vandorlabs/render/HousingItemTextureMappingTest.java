package com.vandorlabs.render;

import com.vandorlabs.render.ControlMount.Point;

public final class HousingItemTextureMappingTest {
    private static void close(double actual,double expected){if(Math.abs(actual-expected)>1e-8)throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        Point[] half={new Point(0,0,0),new Point(1,0,0),new Point(1,.5,0),new Point(0,.5,0)};
        double[] u={0,16,16,0},v={16,16,8,8};
        double[][] stretch=HousingItemTextureMapping.map(half,u,v,0,1);close(stretch[0][1],16);close(stretch[2][1],0);
        double[][] tile=HousingItemTextureMapping.map(half,u,v,1,1);close(tile[0][1],8);close(tile[2][1],0);
        double[][] fit=HousingItemTextureMapping.map(half,u,v,2,2);close(fit[1][0],16);close(fit[0][1],16);
        double[][] wide=HousingItemTextureMapping.map(half,u,v,1,2);close(wide[1][0],8);close(wide[0][1],8);
        Point[] collapsed={new Point(0,0,0),new Point(0,0,0),new Point(0,0,0),new Point(0,0,0)};
        double[][] same=HousingItemTextureMapping.map(collapsed,u,v,0,1);for(int i=0;i<4;i++){close(same[i][0],u[i]);close(same[i][1],v[i]);}
        System.out.println("HousingItemTextureMapping PASS physical half-height item Stretch/Tile/Fit/aspect and degenerate preservation");
    }
}
