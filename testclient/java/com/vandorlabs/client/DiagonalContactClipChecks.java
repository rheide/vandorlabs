package com.vandorlabs.client;

import com.vandorlabs.render.DiagonalMeshClip;

/** Boundary caps survive the neighbour epsilon; faces inside its cell remain clipped. */
final class DiagonalContactClipChecks {
    static void run(){int cases=0;
        for(double unit:new double[]{1,16})for(int axis=0;axis<3;axis++)for(int side=0;side<2;side++){
            double[] bounds={-2*unit,-2*unit,-2*unit,3*unit,3*unit,3*unit,0,0,0,unit,unit,unit};
            bounds[6+axis]=side==0?-unit:unit*(1-1D/4096);bounds[9+axis]=side==0?unit/4096:2*unit;
            double[][] cap=new double[4][5];for(int v=0;v<4;v++){cap[v][axis]=side*unit;cap[v][(axis+1)%3]=(v<2?.25:.75)*unit;cap[v][(axis+2)%3]=(v==0||v==3?.25:.75)*unit;cap[v][3]=v;cap[v][4]=v+1;}
            require(DiagonalMeshClip.quads(bounds,cap).isEmpty(),"original clipping must reproduce disappearing cap");
            require(DiagonalMeshClip.quadsKeepingContactCaps(bounds,cap).size()==4,"closed contact face lost");
            for(double[] vertex:cap)vertex[axis]+=(side==0?-1:1)*unit/8;
            require(DiagonalMeshClip.quadsKeepingContactCaps(bounds,cap).isEmpty(),"cap preservation leaked into solid neighbour");cases++;
        }
        checkExposedRails();
        System.out.println("PASS: "+cases+" neighbour cap regressions reproduce original epsilon removal, retain boundary faces and reject overhangs");
    }
    private static void checkExposedRails(){int cases=0;
        for(int mode=0;mode<3;mode++)for(boolean inverted:new boolean[]{false,true})for(int end=0;end<2;end++)for(int side=0;side<2;side++)for(int contacts=1;contacts<8;contacts++){
            int row=mode==2?2:1;double span=mode==1?16:8,y0=end==0?0:14,y1=y0+2;
            double[][] quad=new double[4][3];
            for(int v=0;v<4;v++){double y=v<2?y0:y1,z=(inverted?1-y/16:y/16)*span-2+side*4;
                quad[v]=new double[]{v==0||v==3?0:16,mode==2?z+(inverted?8:0):y,mode==2?y:z};}
            java.util.List<double[]> boxes=new java.util.ArrayList<>();
            for(int which=0;which<3;which++)if((contacts&(1<<which))!=0){
                int axis=which==2?row:0;boolean positive=which==1||which==2&&end==1;
                double[] box={0,0,0,16,16,16};box[axis]=positive?16*(1-1D/4096):-16;box[axis+3]=positive?32:16D/4096;boxes.add(box);
            }
            double[] bounds=new double[6+6*boxes.size()];double[] base={-32,-32,-32,48,48,48};System.arraycopy(base,0,bounds,0,6);
            for(int i=0;i<boxes.size();i++)System.arraycopy(boxes.get(i),0,bounds,6+i*6,6);
            java.util.List<double[]> clipped=DiagonalMeshClip.quadsKeepingContactCaps(bounds,quad);
            double[] a=quad[end==0?0:3],b=quad[end==0?1:2];int edges=0;
            for(int q=0;q<clipped.size();q+=4){
                double area=0;double[] origin=clipped.get(q);
                for(int v=1;v<3;v++){double[] u=clipped.get(q+v),w=clipped.get(q+v+1);double x=(u[1]-origin[1])*(w[2]-origin[2])-(u[2]-origin[2])*(w[1]-origin[1]),y=(u[2]-origin[2])*(w[0]-origin[0])-(u[0]-origin[0])*(w[2]-origin[2]),z=(u[0]-origin[0])*(w[1]-origin[1])-(u[1]-origin[1])*(w[0]-origin[0]);area+=Math.sqrt(x*x+y*y+z*z);}
                require(area>1e-8,"zero-area neighbour fragment can cancel a visible rail mode="+mode+" contacts="+contacts);
                for(int e=0;e<4;e++){double[] u=clipped.get(q+e),v=clipped.get(q+((e+1)&3));if(same(a,u)&&same(b,v)||same(a,v)&&same(b,u))edges++;}
            }
            require(edges==1,"exposed bottom/top rail has duplicate or missing edges mode="+mode+" contacts="+contacts+" edges="+edges);cases++;
        }
        System.out.println("PASS: "+cases+" exposed diagonal rails retain one boundary edge and no zero-area fragments across shapes, inversions, ends, pane sides and solid-neighbour combinations");
    }
    private static boolean same(double[] a,double[] b){for(int axis=0;axis<3;axis++)if(Math.abs(a[axis]-b[axis])>1e-7)return false;return true;}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
