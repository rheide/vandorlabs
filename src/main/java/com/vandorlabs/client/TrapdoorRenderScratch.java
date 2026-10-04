package com.vandorlabs.client;

/** Numeric working storage only; nested draws borrow an independent buffer. */
final class TrapdoorRenderScratch implements AutoCloseable {
    private static final ThreadLocal<TrapdoorRenderScratch> LOCAL=ThreadLocal.withInitial(TrapdoorRenderScratch::new);
    final double[][] moving=new double[8][3],closed=new double[8][3],uv=new double[8][3];
    private boolean inUse;
    private TrapdoorRenderScratch() { }
    static TrapdoorRenderScratch acquire() {
        TrapdoorRenderScratch scratch=LOCAL.get();
        if(scratch.inUse)scratch=new TrapdoorRenderScratch();
        scratch.inUse=true;return scratch;
    }
    @Override public void close(){inUse=false;}
}
