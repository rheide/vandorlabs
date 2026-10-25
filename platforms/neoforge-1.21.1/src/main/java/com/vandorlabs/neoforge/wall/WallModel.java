package com.vandorlabs.neoforge.wall;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.tiles.GlassTextures;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;

/** Native atlas emission over main-thread immutable wall geometry and captured own-cell light. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WallModel extends BakedModelWrapper<BakedModel> {
    public static final ModelProperty<Integer> OWN_LIGHT=new ModelProperty<>();
    private static final java.util.Set<ResourceLocation> IDS=java.util.Set.of(ResourceLocation.parse("vandorlabs:programmable_wall"),ResourceLocation.parse("vandorlabs:programmable_porthole_wall"),ResourceLocation.parse("vandorlabs:programmable_porthole_block"));
    private final Map<ResourceLocation,TextureAtlasSprite> sprites;
    private WallModel(BakedModel original,Map<ResourceLocation,TextureAtlasSprite> sprites){super(original);this.sprites=sprites;}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        Map<ResourceLocation,TextureAtlasSprite> sprites=new java.util.HashMap<>();
        for(var choice:ModernFinishCatalog.allChoices())resolve(event,sprites,ResourceLocation.parse(choice.entry().texture()));
        for(int shade=0;shade<GlassTextures.IDS.length;shade++)for(int tint=0;tint<3;tint++)resolve(event,sprites,texture(GlassTextures.texture(shade,1,tint)));
        resolve(event,sprites,texture("vandorlabs:blocks/programmable_glass/metal_side"));
        var immutable=Map.copyOf(sprites);event.getModels().replaceAll((id,model)->IDS.contains(id.id())&&!id.getVariant().equals("inventory")?new WallModel(model,immutable):model);
    }
    @SubscribeEvent public static void reload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event){event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Boolean>(){
        @Override protected Boolean prepare(net.minecraft.server.packs.resources.ResourceManager resources,net.minecraft.util.profiling.ProfilerFiller profiler){return Boolean.TRUE;}
        @Override protected void apply(Boolean prepared,net.minecraft.server.packs.resources.ResourceManager resources,net.minecraft.util.profiling.ProfilerFiller profiler){WallClientSnapshots.reload();}
    });}
    private static void resolve(ModelEvent.ModifyBakingResult event,Map<ResourceLocation,TextureAtlasSprite> map,ResourceLocation texture){map.put(texture,event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,texture)));}
    public static ResourceLocation texture(String legacy){return ResourceLocation.parse(legacy.replace(":blocks/",":block/"));}
    @Override public ModelData getModelData(BlockAndTintGetter region,BlockPos pos,BlockState state,ModelData data){
        // Native light propagation already schedules region rebuilds; never cache light in a tile snapshot.
        int own=(region.getBrightness(LightLayer.SKY,pos)<<20)|(region.getBrightness(LightLayer.BLOCK,pos)<<4);
        return data.derive().with(OWN_LIGHT,own).build();
    }
    @Override public boolean useAmbientOcclusion(){return false;}
    @Override public TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return TriState.FALSE;}
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){var snapshot=data.get(WallRenderSnapshot.DATA);var fluid=data.get(com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot.DATA);return fluid!=null&&!fluid.faces.isEmpty()?ChunkRenderTypeSet.of(RenderType.cutoutMipped(),RenderType.translucent()):ChunkRenderTypeSet.of(RenderType.cutoutMipped());}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        if(side!=null)return List.of();var snapshot=data.get(WallRenderSnapshot.DATA);if(snapshot==null)return List.of();int light=data.has(OWN_LIGHT)?data.get(OWN_LIGHT):0;List<BakedQuad> result=new ArrayList<>();
        if(layer==null||layer==RenderType.cutoutMipped())for(var face:snapshot.opaque){result.add(quad(face,light,false));result.add(quad(face,light,true));}
        if(layer==null||layer==RenderType.translucent()){
            var fluid=data.get(com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot.DATA);if(fluid!=null)result.addAll(com.vandorlabs.neoforge.fluid.client.NativeFluidClient.quads(fluid));
        }
        return result;
    }
    private BakedQuad quad(WallRenderSnapshot.Face face,int light,boolean reverse){
        TextureAtlasSprite sprite=sprites.get(face.texture());if(sprite==null)throw new IllegalStateException("Unstitched wall material "+face.texture());int[] data=new int[32];
        int argb=face.argb(),abgr=argb&0xFF00FF00|(argb>>>16)&255|(argb&255)<<16;
        int normal=((int)Math.round(face.nx()*127)&255)|(((int)Math.round(face.ny()*127)&255)<<8)|(((int)Math.round(face.nz()*127)&255)<<16);
        for(int i=0;i<4;i++){var p=face.vertices().get(reverse?3-i:i);int at=i*8;data[at]=Float.floatToRawIntBits((float)p.x());data[at+1]=Float.floatToRawIntBits((float)p.y());data[at+2]=Float.floatToRawIntBits((float)p.z());data[at+3]=abgr;data[at+4]=Float.floatToRawIntBits(sprite.getU((float)p.u()));data[at+5]=Float.floatToRawIntBits(sprite.getV((float)p.v()));data[at+6]=light;data[at+7]=normal;}
        return new BakedQuad(data,-1,nominal(face),sprite,false,false);
    }
    /** A varying coordinate keeps vanilla flat lighting on the own cell, even at outer block boundaries. */
    static Direction nominal(WallRenderSnapshot.Face face){
        double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(var p:face.vertices()){double[] xyz={p.x(),p.y(),p.z()};for(int a=0;a<3;a++){min[a]=Math.min(min[a],xyz[a]);max[a]=Math.max(max[a],xyz[a]);}}
        int axis=0;for(int a=1;a<3;a++)if(max[a]-min[a]>max[axis]-min[axis])axis=a;
        return axis==0?Direction.EAST:axis==1?Direction.UP:Direction.SOUTH;
    }
}
