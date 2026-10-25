package com.vandorlabs.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PanelTextureMappingChecks {
    private static int checks,cases;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void close(double actual,double expected,String message){check(Math.abs(actual-expected)<1E-8,message+": "+actual+" != "+expected);}
    private static double[][] quad(double width,double height){return new double[][]{{0,0,0,0,0,0,0,1},{width,0,0,1,0,0,0,1},{width,height,0,1,1,0,0,1},{0,height,0,0,1,0,0,1}};}
    private static double signedArea(double[][] p){return ((p[1][0]-p[0][0])*(p[2][1]-p[0][1])-(p[1][1]-p[0][1])*(p[2][0]-p[0][0]))/2;}
    public static void main(String[] args){
        check(new PanelTextureMapping.Layout(1,2,3,4,true,false).key().equals("1:2:3:4:true:false"),"stable layout cache key");
        PanelTextureMapping.Layout single=new PanelTextureMapping.Layout(0,0,1,1);
        close(PanelTextureMapping.stretchU(1D/16,single,1D/16),0,"left glass border");close(PanelTextureMapping.stretchU(15D/16,single,1D/16),1,"right glass border");close(PanelTextureMapping.stretchU(.25,single,1D/16),3D/14,"interior border scale");close(PanelTextureMapping.stretchV(.5,single,1D/16),.5,"center border scale");
        close(PanelTextureMapping.stretchU(0,new PanelTextureMapping.Layout(0,0,2,1),0),0,"joined first left");close(PanelTextureMapping.stretchU(1,new PanelTextureMapping.Layout(0,0,2,1),0),.5,"joined first right");close(PanelTextureMapping.stretchU(0,new PanelTextureMapping.Layout(1,0,2,1),0),.5,"joined second left");close(PanelTextureMapping.stretchU(1,new PanelTextureMapping.Layout(1,0,2,1),0),1,"joined second right");
        close(PanelTextureMapping.stretchV(0,new PanelTextureMapping.Layout(0,0,1,2),0),.5,"row origin is bottom of upper row");close(PanelTextureMapping.stretchV(1,new PanelTextureMapping.Layout(0,1,1,2),0),.5,"adjacent row seam");close(PanelTextureMapping.stretchU(.25,new PanelTextureMapping.Layout(0,0,1,1,true,true),0),.75,"U mirror");close(PanelTextureMapping.stretchV(.25,new PanelTextureMapping.Layout(0,0,1,1,true,true),0),.75,"V mirror");
        for(double width:new double[]{1,4,8,16,32})for(double height:new double[]{1,4,8,16,32})for(double aspect:new double[]{.5,1,2})for(int[] group:new int[][]{{1,1},{2,3},{3,2}})for(int flips=0;flips<4;flips++)for(double border:new double[]{0,1D/16})for(int mode=0;mode<3;mode++){
            cases++;double[][] source=quad(width,height);double[][] frozen=new double[4][];for(int i=0;i<4;i++)frozen[i]=source[i].clone();
            List<double[][]> mapped=PanelTextureMapping.map(source,new PanelTextureMapping.Layout(group[0]-1,group[1]-1,group[0],group[1],(flips&1)!=0,(flips&2)!=0),border,border,mode,aspect);
            check(mapped!=null&&!mapped.isEmpty(),"selected panel emitted no surface");check(Arrays.deepEquals(source,frozen),"mapping mutated source records");double area=0;
            for(double[][] face:mapped){check(face.length==4&&Arrays.equals(face[2],face[3]),"triangle fourth point");double signed=signedArea(face);check(signed>=-1E-8,"clipping reversed winding");area+=signed;
                for(double[] p:face){check(p[0]>=-1E-8&&p[0]<=width+1E-8&&p[1]>=-1E-8&&p[1]<=height+1E-8&&p[2]==0,"clipping moved geometry outside panel");check(p[3]>=0&&p[3]<=1&&p[4]>=0&&p[4]<=1,"UV escaped chosen sprite");check(p[5]==0&&p[6]==0&&p[7]==1,"normal changed");}
            }
            close(area,width*height,"mapping changed surface area");
        }
        List<double[][]> repeated=PanelTextureMapping.map(quad(32,16),single,0,0,1,1);check(repeated.size()==4,"two square repeat cells must produce four triangles");for(int i=0;i<repeated.size();i++)for(double[] p:repeated.get(i)){check(p[0]>=i/2*16-1E-8&&p[0]<=i/2*16+16+1E-8,"repeat traversal/cell order changed");close(p[3],(p[0]-i/2*16)/16,"square image repeat coordinates");close(p[4],p[1]/16,"square image vertical coordinates");}
        List<double[][]> portrait=PanelTextureMapping.map(quad(16,32),single,0,0,1,.5);check(portrait.size()==2,"portrait must cover two-block height without repeated V");for(double[][] face:portrait)for(double[] p:face){close(p[3],p[0]/16,"portrait native U size");close(p[4],p[1]/32,"portrait native V size");}
        List<double[][]> wide=PanelTextureMapping.map(quad(32,16),single,0,0,1,2);check(wide.size()==2,"landscape must cover two-block width without repeated U");for(double[][] face:wide)for(double[] p:face){close(p[3],p[0]/32,"landscape native U size");close(p[4],p[1]/16,"landscape native V size");}
        List<double[][]> joined=PanelTextureMapping.map(quad(16,16),new PanelTextureMapping.Layout(1,0,2,1),0,0,2,2);for(double[][] face:joined)for(double[] p:face)close(p[3],.5+p[0]/32,"matched-aspect Fit spans joined group");
        double[][] trapezoid=quad(16,16);trapezoid[2][0]=8;List<double[][]> measured=PanelTextureMapping.map(trapezoid,single,0,0,1,1);close(measured.get(0)[1][3],.5,"legacy final eligible edge determines U scale");
        double[][] mixed=quad(16,16);mixed[2][3]=1.001;check(PanelTextureMapping.map(mixed,single,0,0,1,1)==null,"mixed frame sprite must pass through");check(mixed[2][3]==1.001,"mixed frame input changed");mixed[2][3]=1.0001;check(PanelTextureMapping.map(mixed,single,0,0,0,1)!=null,"inclusive selected-sprite tolerance");mixed[2][3]=Double.NaN;check(PanelTextureMapping.map(mixed,single,0,0,0,1)==null,"non-selected NaN UV must pass through");
        double[][] constant=quad(16,16);for(double[] p:constant){p[3]=0;p[4]=0;}check(PanelTextureMapping.map(constant,single,0,0,1,1).isEmpty(),"integer zero-span repeat retains empty legacy output");
        double[][] triangle=quad(16,16);triangle[3]=triangle[2].clone();List<double[][]> triangleFaces=PanelTextureMapping.map(triangle,single,0,0,1,1);check(triangleFaces.size()==2,"legacy triangle input retains both fan emissions");close(signedArea(triangleFaces.get(0)),128,"triangle first fan area");close(signedArea(triangleFaces.get(1)),0,"legacy degenerate fan is preserved");for(double[] p:constant){p[3]=.5;p[4]=.5;}check(PanelTextureMapping.map(constant,single,0,0,1,1).size()==2,"noninteger zero-span repeat preserves original surface");
        List<double[]> polygon=new ArrayList<>();for(double[] p:quad(16,16))polygon.add(new double[]{p[0],p[1],p[2],p[3],p[4],p[0]/16,p[1]/16,1,p[0]+2*p[1]});List<double[]> clipped=PolygonClip.clip(polygon,3,.25,true);check(clipped.size()==4,"inclusive clipping rectangle");close(clipped.get(0)[0],4,"clipping traversal first intersection");for(double[] p:clipped){check(p.length==9,"generic clip lost extra attributes");close(p[5],p[0]/16,"interpolated U normal attribute");close(p[6],p[1]/16,"interpolated V normal attribute");close(p[8],p[0]+2*p[1],"interpolated ninth attribute");}check(PolygonClip.clip(new ArrayList<double[]>(),3,0,true).isEmpty(),"empty clip");
        System.out.println("PanelTextureMappingChecks PASS "+cases+" area/winding/bounds matrices, "+checks+" analytic stretch/repeat/portrait/landscape/fit/frame/interpolation assertions");
    }
}
