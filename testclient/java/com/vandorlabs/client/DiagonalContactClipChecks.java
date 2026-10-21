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
        System.out.println("PASS: "+cases+" neighbour cap regressions reproduce original epsilon removal, retain boundary faces and reject overhangs");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
