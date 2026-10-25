package com.vandorlabs.neoforge.client;

import com.vandorlabs.render.HousingLightPolicy;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Immutable light data sampled from the supplied render region, never a singleton client world. */
public final class NativeHousingLighting {
    public static final ModelProperty<Integer> LIGHT=new ModelProperty<>();
    private NativeHousingLighting(){}
    public static int snapshot(BlockAndTintGetter region,BlockPos pos){
        int packed=0;
        for(Direction side:Direction.values()){
            BlockPos other=pos.relative(side);
            int sample=(region.getBrightness(LightLayer.SKY,other)<<20)|(region.getBrightness(LightLayer.BLOCK,other)<<4);
            packed=HousingLightPolicy.max(packed,sample);
        }
        return packed;
    }
    public static ModelData snapshot(BlockAndTintGetter region,BlockPos pos,ModelData data){return data.derive().with(LIGHT,snapshot(region,pos)).build();}
    /** Copy the whole native record, including shader extension fields and baked normal/color data. */
    public static BakedQuad apply(BakedQuad source,int packed){
        int[] vertices=source.getVertices().clone();int stride=vertices.length/4;
        if(vertices.length%4!=0||stride<=IQuadTransformer.UV2)throw new IllegalArgumentException("Unsupported housing vertex format");
        for(int i=0;i<4;i++){int at=i*stride+IQuadTransformer.UV2;vertices[at]=HousingLightPolicy.max(vertices[at],packed);}
        return new BakedQuad(vertices,source.getTintIndex(),source.getDirection(),source.getSprite(),source.isShade(),source.hasAmbientOcclusion());
    }
}
