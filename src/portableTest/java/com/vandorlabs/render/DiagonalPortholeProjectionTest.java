package com.vandorlabs.render;

import java.security.MessageDigest;

/** Raw legacy BufferBuilder reference; actual atlas interpolation is checked in the Forge fixture. */
public final class DiagonalPortholeProjectionTest {
    private static final String RAW="33e5559edac3c9c0b439d1ae7433fc03a3d543060db7b04b892c40711b629f25";
    private static MessageDigest digest;private static int cases,vertices;
    private static void number(long value){for(int shift=56;shift>=0;shift-=8)digest.update((byte)(value>>>(shift)));}
    private static void integer(int value){for(int shift=24;shift>=0;shift-=8)digest.update((byte)(value>>>(shift)));}
    private static final PanelVertexEmitter.RawSink<Object> VERTEX=new PanelVertexEmitter.RawSink<Object>(){
        public void vertex(Object context,double x,double y,double z,double u,double v,float nx,float ny,float nz){number(Double.doubleToRawLongBits(x));number(Double.doubleToRawLongBits(y));number(Double.doubleToRawLongBits(z));number(Double.doubleToRawLongBits(u));number(Double.doubleToRawLongBits(v));integer(Float.floatToRawIntBits(nx));integer(Float.floatToRawIntBits(ny));integer(Float.floatToRawIntBits(nz));integer(((byte)(int)(nx*127)&255)|(((byte)(int)(ny*127)&255)<<8)|(((byte)(int)(nz*127)&255)<<16));vertices++;}
    };
    private static final PanelQuadSink<Object,Object> OUTPUT=new PanelQuadSink<Object,Object>(){
        public void quad(Object context,Object material,boolean reverse,double[] a,double[] b,double[] c,double[] d){PanelVertexEmitter.raw(VERTEX,context,reverse,a[0],a[1],a[2],a[3],a[4],b[0],b[1],b[2],b[3],b[4],c[0],c[1],c[2],c[3],c[4],d[0],d[1],d[2],d[3],d[4]);}
    };
    public static void main(String[] args)throws Exception{
        digest=MessageDigest.getInstance("SHA-256");DiagonalPortholeProjectionCases.visit(c->{if(!c.raw)return;integer(c.id);DiagonalPortholeProjection mesh=c.mode<0?null:new DiagonalPortholeProjection(c.mode,c.inverted,c.lower,c.upper,c.bounds,c.corner);DiagonalPortholeProjection.quad(OUTPUT,null,null,mesh,c.reverse,c.vertices);cases++;});StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format("%02x",value&255));if(!RAW.equals(hash.toString())||cases!=11552||vertices!=226240)throw new AssertionError("Actual legacy projection changed "+hash+" cases="+cases+" vertices="+vertices);System.out.println("Diagonal porthole projection PASS cases="+cases+" vertices="+vertices+" raw="+hash);
    }
}
