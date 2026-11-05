package com.vandorlabs.neoforge.shape;

/** Immutable cosmetic group coordinates published to chunk workers. */
public record NativePanelLayout(int column,int row,int columns,int rows,boolean flipU,boolean flipV) {
    public static final NativePanelLayout UNIT=new NativePanelLayout(0,0,1,1,false,false);
}
