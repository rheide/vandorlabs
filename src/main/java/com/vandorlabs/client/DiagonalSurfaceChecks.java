package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableWall;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import java.nio.ByteBuffer;

/** Inspect submitted atlas coordinates, including the negative local bounds of upper fills. */
final class DiagonalSurfaceChecks {
    static void run() {
        try {
            TextureAtlasSprite wall = new TextureAtlasSprite("test_wall") { };
            TextureAtlasSprite trim = new TextureAtlasSprite("test_trim") { };
            wall.setIconWidth(16); wall.setIconHeight(16); wall.initSprite(256,256,32,48,false);
            trim.setIconWidth(16); trim.setIconHeight(16); trim.initSprite(256,256,128,160,false);
            java.lang.reflect.Method draw = TEAnimatedScreenSelector.class.getDeclaredMethod("renderDiagonalWall",
                    BufferBuilder.class, TextureAtlasSprite.class, TextureAtlasSprite.class, boolean.class,
                    BlockProgrammableWall.Corner.class, double.class, int.class, double.class, double.class);
            draw.setAccessible(true);
            for (boolean shallow : new boolean[]{false,true}) for (boolean inverted : new boolean[]{false,true})
                for (int fill = 1; fill <= 3; fill++) {
                    BufferBuilder buf = new BufferBuilder(4096);
                    buf.begin(7, DefaultVertexFormats.POSITION_TEX_NORMAL);
                    double low = shallow && inverted ? -6 : 0;
                    draw.invoke(null,buf,wall,trim,inverted,null,shallow?6D:12D,fill,low,low+16);
                    buf.finishDrawing();
                    ByteBuffer data = buf.getByteBuffer();
                    if (buf.getVertexCount() == 0) throw new IllegalStateException("empty filled panel");
                    int stride = DefaultVertexFormats.POSITION_TEX_NORMAL.getNextOffset();
                    for (int i=0;i<buf.getVertexCount();i++) {
                        float u=data.getFloat(i*stride+12),v=data.getFloat(i*stride+16);
                        if(u<wall.getMinU()-1e-6 || u>wall.getMaxU()+1e-6
                                || v<wall.getMinV()-1e-6 || v>wall.getMaxV()+1e-6)
                            throw new IllegalStateException("Filled face escaped main texture: "+u+", "+v);
                    }
                }
            System.out.println("[vandorlabs][reprolab] diagonal-fill-textures PASS");
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
