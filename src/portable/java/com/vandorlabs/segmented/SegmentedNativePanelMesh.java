package com.vandorlabs.segmented;
/** Fast rectangular sections retain four-vertex faces, exact Tile phase and metric reveal artwork. */
public final class SegmentedNativePanelMesh {
 private SegmentedNativePanelMesh(){}
 public static void build(int row,int height,boolean endpoint,SegmentedPanelMesh.Texture front,SegmentedPanelMesh.Texture edge,SegmentedPanelMesh.Sink out){build(row,height,0,endpoint,new SegmentedPanelMesh.Surface(front,front,1,false),edge,out);}
 public static void build(int row,int height,int column,boolean endpoint,SegmentedPanelMesh.Surface body,SegmentedPanelMesh.Texture edge,SegmentedPanelMesh.Sink out){SegmentedPanelMesh.nativeBody(row,height,column,endpoint,body,edge,out);}
}
