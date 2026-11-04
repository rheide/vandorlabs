package com.vandorlabs.client;

/** Existing client names share the portable outline with offline door rendering. */
final class PortholeHex extends com.vandorlabs.surface.WindowOutline {
    PortholeHex(double[][] vertices){super(vertices);}
    PortholeHex(int columns,int rows){super(columns,rows);}
    PortholeHex(int columns,int rows,int shape){super(columns,rows,shape);}
    PortholeHex(int columns,int rows,int shape,int border){super(columns,rows,shape,border);}
}
