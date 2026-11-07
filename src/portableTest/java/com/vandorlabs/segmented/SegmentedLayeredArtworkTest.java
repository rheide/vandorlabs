package com.vandorlabs.segmented;

import java.util.*;
import com.vandorlabs.surface.WindowOutline;

/** Alpha body faces retain the existing panel phase; opaque edges and window opacity remain independent. */
public final class SegmentedLayeredArtworkTest {
    private static int checks;private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final SegmentedPanelMesh.Texture IMAGE=new SegmentedPanelMesh.Texture(){public float u(double pixel){return(float)(pixel/16);}public float v(double pixel){return(float)(pixel/16);}};
    private static final class Capture implements SegmentedPanelMesh.Sink {
        final List<double[]> vertices=new ArrayList<>();public void vertex(double x,double y,double z,float u,float v,float nx,float ny,float nz){vertices.add(new double[]{x,y,z,u,v,nx,ny,nz});}
    }
    private static void same(List<double[]> actual,List<double[]> expected,String why){need(actual.size()==expected.size(),why+" vertex count");for(int i=0;i<actual.size();i++)need(Arrays.equals(actual.get(i),expected.get(i)),why+" vertex/UV/normal");}
    public static void main(String[] args){
        int profiles=0;for(boolean window:new boolean[]{false,true})for(int shape=0;shape<4;shape++)for(int mode=0;mode<3;mode++)for(boolean opaque:new boolean[]{false,true})for(double aspect:new double[]{.5,1,2}){
            SegmentedPanelMesh.Surface body=new SegmentedPanelMesh.Surface(IMAGE,IMAGE,aspect,false),pane=new SegmentedPanelMesh.Surface(IMAGE,IMAGE,1,false);WindowOutline.Slice slice=window?WindowOutline.localSlice(2,3,shape,0,1,1):null;Capture oldSolid=new Capture(),oldPane=new Capture(),solid=new Capture(),alphaBody=new Capture(),alphaPane=new Capture();
            SegmentedPanelMesh.build(window,shape,mode,1,1,4,5,1,1,2,3,mode,opaque,0,body,pane,IMAGE,oldSolid,slice);SegmentedPanelMesh.build(window,shape,mode,1,1,4,5,1,1,2,3,mode,opaque,1,body,pane,IMAGE,oldPane,slice);
            SegmentedPanelMesh.buildLayered(window,shape,mode,1,1,4,5,1,1,2,3,mode,opaque,0,body,pane,IMAGE,solid,slice,true,3);SegmentedPanelMesh.buildLayered(window,shape,mode,1,1,4,5,1,1,2,3,mode,opaque,1,body,pane,IMAGE,alphaBody,slice,true,1);SegmentedPanelMesh.buildLayered(window,shape,mode,1,1,4,5,1,1,2,3,mode,opaque,1,body,pane,IMAGE,alphaPane,slice,true,2);
            List<double[]> expectedOpaque=new ArrayList<>(),expectedBody=new ArrayList<>();for(double[] vertex:oldSolid.vertices){boolean face=Math.abs(vertex[7])>.5&&Math.abs(vertex[2])>1e-7;(face?expectedBody:expectedOpaque).add(vertex);}same(solid.vertices,expectedOpaque,"opaque edges/opaque glass preserved");same(alphaBody.vertices,expectedBody,"unchanged body geometry/continuous UV in alpha pass");same(alphaPane.vertices,oldPane.vertices,"independent pane geometry/UV");for(double[] vertex:alphaBody.vertices)need(Math.abs(vertex[7])>.5&&Math.abs(vertex[2])==1,"only authored front/back image faces become translucent");profiles++;
        }
        for(int kind=0;kind<3;kind++){String id=SegmentedArtworkPolicy.gateId(kind);need(SegmentedArtworkPolicy.gate(id)==kind&&SegmentedArtworkPolicy.complex(id)&&!SegmentedArtworkPolicy.translucent(id),"gate stable identity");need(SegmentedArtworkPolicy.gate(id+"_unknown")==-1,"unknown model identity is not guessed");String tile=SegmentedArtworkPolicy.tileId(kind);need(SegmentedArtworkPolicy.gate(tile)==-1&&SegmentedArtworkPolicy.translucent(tile)==(kind==2)&&SegmentedArtworkPolicy.complex(tile)==(kind==2),"flat/glazing stable identity");}
        need(SegmentedArtworkPolicy.legacyGeneral("segmented_door_panel")&&SegmentedArtworkPolicy.legacyGeneral("segmented_door_panel_light"),"old general-material choices retained");need(!SegmentedArtworkPolicy.complex(null)&&!SegmentedArtworkPolicy.legacyGeneral(null),"default/unknown remain ordinary panels");
        System.out.println("Segmented layered artwork PASS profiles="+profiles+" checks="+checks+" bodyAlpha/opaqueEdges/independentGlass/unchangedTilePhase/stableIDs");
    }
}
