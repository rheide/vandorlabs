package com.vandorlabs.neoforge.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Atlas boundary that preserves a canonical item's geometry, UV orientation and vertex payload. */
public final class NativeItemMaterials {
    public record Material(TextureAtlasSprite sprite,float usedU,float usedV){}
    public static BakedQuad retexture(BakedQuad source,Material target){
        int[] vertices=source.getVertices().clone();int stride=vertices.length/4;var original=source.getSprite();
        for(int i=0;i<4;i++){
            int at=i*stride;float u=(Float.intBitsToFloat(vertices[at+4])-original.getU0())/(original.getU1()-original.getU0());float v=(Float.intBitsToFloat(vertices[at+5])-original.getV0())/(original.getV1()-original.getV0());
            vertices[at+4]=Float.floatToRawIntBits(target.sprite.getU(u*target.usedU));vertices[at+5]=Float.floatToRawIntBits(target.sprite.getV(v*target.usedV));
        }
        return new BakedQuad(vertices,source.getTintIndex(),source.getDirection(),target.sprite,source.isShade(),source.hasAmbientOcclusion());
    }
    private NativeItemMaterials(){}
}
