package com.vandorlabs.render;

/** Final polygon output with raw position/UV attributes; target adapters own atlas and normals. */
public interface PanelQuadSink<C,M> {
    void quad(C context,M material,boolean reverse,double[] a,double[] b,double[] c,double[] d);
}
