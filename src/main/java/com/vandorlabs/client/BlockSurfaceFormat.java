package com.vandorlabs.client;

import net.minecraft.client.renderer.vertex.*;

/** Explicit lightmap and normals, respecting OptiFine's live shader format. */
final class BlockSurfaceFormat {
    private static final VertexFormat NORMALS=new VertexFormat()
            .addElement(DefaultVertexFormats.POSITION_3F).addElement(DefaultVertexFormats.COLOR_4UB)
            .addElement(DefaultVertexFormats.TEX_2F).addElement(DefaultVertexFormats.TEX_2S)
            .addElement(DefaultVertexFormats.NORMAL_3B).addElement(DefaultVertexFormats.PADDING_1B);
    static VertexFormat get(){return DefaultVertexFormats.BLOCK.hasNormal()?DefaultVertexFormats.BLOCK:NORMALS;}
    private BlockSurfaceFormat(){}
}
