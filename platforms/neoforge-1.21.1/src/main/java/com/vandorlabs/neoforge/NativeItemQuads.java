package com.vandorlabs.neoforge;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Retain authored geometry, sub-face UV crops and extended vertex payload when changing an item sprite. */
final class NativeItemQuads {
    static BakedQuad retexture(BakedQuad source,TextureAtlasSprite target,float usedU,float usedV){
        var data=source.getVertices().clone();int stride=data.length/4;var old=source.getSprite();
        for(int vertex=0;vertex<4;vertex++){
            int at=vertex*stride;
            float u=(Float.intBitsToFloat(data[at+4])-old.getU0())/(old.getU1()-old.getU0());
            float v=(Float.intBitsToFloat(data[at+5])-old.getV0())/(old.getV1()-old.getV0());
            data[at+4]=Float.floatToRawIntBits(target.getU(u*usedU));
            data[at+5]=Float.floatToRawIntBits(target.getV(v*usedV));
        }
        return new BakedQuad(data,source.getTintIndex(),source.getDirection(),target,source.isShade(),source.hasAmbientOcclusion());
    }
    private NativeItemQuads(){}
}
