package com.vandorlabs.neoforge;

import com.vandorlabs.render.TextureLayoutMode;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ProgrammableModel extends BakedModelWrapper<BakedModel> {
    record MaterialSnapshot(TextureAtlasSprite sprite,float usedU,float usedV) {}
    private final java.util.Map<Integer,MaterialSnapshot> sprites;
    private final TextureAtlasSprite margin;
    private final boolean joining;
    private final ProgrammableBlockDialog.State defaults;
    private static final com.vandorlabs.render.HousingFacePlane UNIT=new com.vandorlabs.render.HousingFacePlane(new int[3],new int[]{1,1,1});
    private record QuadKey(Direction face,Direction facing,int material,int layout,int mappedLayout,int x,int y,int z,int sx,int sy,int sz){}
    private final com.google.common.cache.Cache<QuadKey,List<BakedQuad>> worldQuads=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(1024*1024).weigher((QuadKey key,List<BakedQuad> values)->160+values.stream().mapToInt(quad->96+quad.getVertices().length*4).sum()).build();
    private ProgrammableModel(BakedModel model,java.util.Map<Integer,MaterialSnapshot> sprites,TextureAtlasSprite margin,boolean joining) { super(model);this.sprites=sprites;this.margin=margin;this.joining=joining;this.defaults=joining?ProgrammableBlockEntity.defaults():NativeTriggerBlockEntity.triggerDefaults(); }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        java.util.Map<Integer,MaterialSnapshot> resolved=new java.util.HashMap<>();
        for(var choice:ModernFinishCatalog.choicesForBake()) {
            var entry=choice.entry();
            var sprite=event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(entry.texture())));
            resolved.put(choice.choice(),new MaterialSnapshot(sprite,entry.u(),entry.v()));
        }
        com.vandorlabs.neoforge.client.NativeBlockMaterials.bake(event.getModels(),resolved.get(0).sprite()).forEach((choice,material)->resolved.put(choice,new MaterialSnapshot(material.lower(),1,1)));
        java.util.Map<Integer,MaterialSnapshot> sprites=java.util.Map.copyOf(resolved);
        TextureAtlasSprite margin=event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.withDefaultNamespace("block/black_concrete")));
        var shapeBake=new ProgrammableShapeModel.Bake(sprites);
        var wallBake=new NativeFlatWallModel.Bake(sprites,event.getTextureGetter());
        var segmentedBake=NativeSegmentedRenderer.prepare(event.getModelBakery(),event.getTextureGetter(),wallBake);
        var segmentedTerrain=new NativeSegmentedTerrainModel.Bake(event.getModelBakery(),event.getModels(),segmentedBake,net.minecraft.client.Minecraft.getInstance().getResourceManager());
        event.getModels().replaceAll((id,model) -> {
            if(NativeDoorClientBindings.segmented(id.id()))return id.getVariant().equals("inventory")?model:com.vandorlabs.neoforge.fluid.client.NativeFluidModel.wrap(NativeSegmentedTerrainModel.wrap(model,segmentedTerrain),event.getTextureGetter());
            if(NativeDoorClientBindings.ordinary(id.id())||NativeDoorClientBindings.large(id.id())){
                if(id.getVariant().equals("inventory"))return NativeDoorItemModel.wrap(model,NativeDoorClientBindings.large(id.id()),name->event.getModels().get(net.minecraft.client.resources.model.ModelResourceLocation.standalone(ResourceLocation.parse(name))));
                return com.vandorlabs.neoforge.fluid.client.NativeFluidModel.wrap(model,event.getTextureGetter());
            }
            if(id.id().equals(ResourceLocation.parse("vandorlabs:programmable_porthole_wall"))||id.id().equals(ResourceLocation.parse("vandorlabs:programmable_porthole_block"))||id.id().equals(ResourceLocation.parse("vandorlabs:programmable_diagonal_porthole")))return com.vandorlabs.neoforge.fluid.client.NativeFluidModel.wrap(wallBake.wrap(model,id.getVariant().equals("inventory"),true),event.getTextureGetter());
            if(id.id().equals(ResourceLocation.parse("vandorlabs:programmable_wall"))||id.id().equals(ResourceLocation.parse("vandorlabs:programmable_diagonal_wall")))return com.vandorlabs.neoforge.fluid.client.NativeFluidModel.wrap(wallBake.wrap(model,id.getVariant().equals("inventory")),event.getTextureGetter());
            if(id.id().equals(ResourceLocation.parse("vandorlabs:programmable_diagonal_glass")))return com.vandorlabs.neoforge.fluid.client.NativeFluidModel.wrap(wallBake.wrap(model,id.getVariant().equals("inventory"),com.vandorlabs.neoforge.shape.NativePanelKind.GLASS),event.getTextureGetter());
            boolean cube=id.id().equals(ResourceLocation.parse("vandorlabs:programmable_block"));
            boolean trigger=id.id().equals(ResourceLocation.parse("vandorlabs:programmable_trigger_block"));
            boolean slab=id.id().equals(ResourceLocation.parse("vandorlabs:programmable_slab")),stairs=id.id().equals(ResourceLocation.parse("vandorlabs:programmable_stairs"));
            if(slab||stairs)return com.vandorlabs.neoforge.fluid.client.NativeFluidModel.wrap(shapeBake.wrap(model,slab,id.getVariant().equals("inventory")),event.getTextureGetter());
            if(!cube&&!trigger)return model;
            if(id.getVariant().equals("inventory"))return new UniformHousingItem(model,sprites,margin,null,cube?1:0);
            return new ProgrammableModel(model,sprites,margin,cube);
        });
        NativePanelPlaybackRenderer.prepare(event.getModelBakery(),wallBake);
        NativeDoorRenderer.prepare(event.getModelBakery(),event.getModels(),event.getTextureGetter(),wallBake);
    }
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data) {
        if(joining&&state!=null&&state.is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get()))com.vandorlabs.neoforge.client.JoinedCubeSnapshots.request(pos);
        return data;
    }
    static int renderIndex(java.util.Map<Integer,MaterialSnapshot> materials,int choice){int stored=ModernFinishCatalog.storedChoice(choice);return materials.containsKey(stored)?stored:0;}
    @Override public TextureAtlasSprite getParticleIcon(){return getParticleIcon(ModelData.EMPTY);}
    @Override public TextureAtlasSprite getParticleIcon(ModelData data){var settings=data.get(ProgrammableBlockEntity.SETTINGS);return sprites.get(renderIndex(sprites,settings==null?defaults.mainTexture:settings.mainTexture)).sprite();}
    @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return net.neoforged.neoforge.common.util.TriState.FALSE;}
    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType renderType) {
        List<BakedQuad> base=originalModel.getQuads(state,side,random,data,renderType);
        if(base.isEmpty())return base;
        ProgrammableBlockDialog.State settings=data.get(ProgrammableBlockEntity.SETTINGS);
        if(settings==null)settings=defaults;
        List<BakedQuad> result=new ArrayList<>(base.size());
        for(BakedQuad quad:base) {
            int local=localFace(quad.getDirection(),state.getValue(ProgrammableBlock.FACING));
            int choice=settings.facesEnabled&&settings.face(local)>=0?settings.face(local):settings.mainTexture;
            int index=renderIndex(sprites,choice);
            MaterialSnapshot material=sprites.getOrDefault(index,sprites.get(0));
            TextureAtlasSprite sprite=material.sprite();
            JoinedCubeFaces joined=joining?data.get(ProgrammableBlockEntity.JOINED):null;
            var plane=joined==null?null:joined.face(quad.getDirection());
            var actualPlane=settings.layout!=1&&plane!=null?plane:UNIT;
            int mappedLayout=settings.layout!=1&&plane!=null?settings.layout:0;
            QuadKey key=new QuadKey(quad.getDirection(),state.getValue(ProgrammableBlock.FACING),index,settings.layout,mappedLayout,actualPlane.min(0),actualPlane.min(1),actualPlane.min(2),actualPlane.size(0),actualPlane.size(1),actualPlane.size(2));
            List<BakedQuad> mapped=worldQuads.getIfPresent(key);
            if(mapped==null){
                float uScale=material.usedU(),vScale=material.usedV();double aspect=material.usedU()/material.usedV();
                if(mappedLayout==0&&!(settings.layout!=1&&plane!=null)&&!TextureLayoutMode.fit(settings.layout,1,1,aspect)){uScale*=TextureLayoutMode.tileU(1,aspect);vScale*=TextureLayoutMode.tileV(1,aspect);}
                mapped=JoinedCubeQuads.map(quad,state.getValue(ProgrammableBlock.FACING),actualPlane,mappedLayout,sprite,uScale,vScale,margin);worldQuads.put(key,mapped);
            }
            result.addAll(mapped);
        }
        return result;
    }
    private record Visual(int main,int layout){}
    private static final com.mojang.serialization.MapDecoder<Visual> VISUAL=new com.mojang.serialization.MapDecoder.Implementation<>(){
        @Override public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops){return java.util.stream.Stream.of(com.vandorlabs.persistence.HousingSettings.MAIN,com.vandorlabs.persistence.HousingSettings.LAYOUT,"SlabTileSides","BlockEntityTag").map(ops::createString);}
        @Override public <T> com.mojang.serialization.DataResult<Visual> decode(com.mojang.serialization.DynamicOps<T> ops,com.mojang.serialization.MapLike<T> input){
            if(input.get("BlockEntityTag")!=null){var nested=ops.getMap(input.get("BlockEntityTag")).result();if(nested.isPresent())input=nested.get();}
            Object main=input.get(com.vandorlabs.persistence.HousingSettings.MAIN),layout=input.get(com.vandorlabs.persistence.HousingSettings.LAYOUT),sides=input.get("SlabTileSides");
            int choice=main instanceof net.minecraft.nbt.NumericTag tag?tag.getAsInt():0;
            int mode=layout instanceof net.minecraft.nbt.IntTag tag&&TextureLayoutMode.valid(tag.getAsInt())?tag.getAsInt():sides instanceof net.minecraft.nbt.ByteTag?(((net.minecraft.nbt.ByteTag)sides).getAsByte()!=0?1:0):-1;
            return com.mojang.serialization.DataResult.success(new Visual(choice,mode));
        }
    };
    /** Item previews retain raw stored materials and dormant faces, independently of world migration or signals. */
    private static final class UniformHousingItem extends BakedModelWrapper<BakedModel> {
        private final java.util.Map<Integer,MaterialSnapshot> materials;
        private final TextureAtlasSprite margin;
        private final ProgrammableBlockDialog.State settings;
        private final int defaultLayout;
        private final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<BakedModel> resolved=new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();
        private final com.google.common.cache.Cache<net.minecraft.world.item.component.CustomData,Visual> visualKeys=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(256).build();
        UniformHousingItem(BakedModel original,java.util.Map<Integer,MaterialSnapshot> materials,TextureAtlasSprite margin,ProgrammableBlockDialog.State settings,int defaultLayout){super(original);this.materials=materials;this.margin=margin;this.settings=settings;this.defaultLayout=defaultLayout;}
        private final java.util.Map<Direction,List<BakedQuad>> quads=new java.util.HashMap<>();
        private final List<BakedModel> passes=List.of(this);
        private final net.minecraft.client.renderer.block.model.ItemOverrides overrides=new net.minecraft.client.renderer.block.model.ItemOverrides(){
                @Override public BakedModel resolve(BakedModel original,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel world,net.minecraft.world.entity.LivingEntity entity,int seed){
                    var data=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
                    if(data==null)data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    if(data==null)return UniformHousingItem.this;
                    Visual visual=visualKeys.getIfPresent(data);
                    if(visual==null){visual=data.read(VISUAL).getOrThrow();visualKeys.put(data,visual);}
                    int index=renderIndex(materials,visual.main),layout=visual.layout<0?defaultLayout:visual.layout;long key=((long)index<<2)|layout;
                    var result=resolved.getAndMoveToLast(key);if(result==null){result=new UniformHousingItem(originalModel,materials,margin,new ProgrammableBlockDialog.State(index,layout,false,new int[]{-1,-1,-1,-1,-1,-1},-1),defaultLayout);resolved.putAndMoveToLast(key,result);if(resolved.size()>256)resolved.removeFirst();}return result;
                }
            };
        @Override public TextureAtlasSprite getParticleIcon(){return materials.get(renderIndex(materials,settings==null?0:settings.mainTexture)).sprite();}
        @Override public TextureAtlasSprite getParticleIcon(ModelData data){return getParticleIcon();}
        @Override public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides(){return overrides;}
        @Override public List<BakedQuad> getQuads(BlockState state,Direction face,RandomSource random){
            if(quads.containsKey(face))return quads.get(face);
            List<BakedQuad> canonical=originalModel.getQuads(state,face,random),result=new ArrayList<>(canonical.size());
            MaterialSnapshot material=materials.get(renderIndex(materials,settings==null?0:settings.mainTexture));
            for(BakedQuad source:canonical){
                int[] data=source.getVertices().clone();int stride=data.length/4;double[][] uv=new double[4][2];double[] min={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},max={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
                for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<2;axis++){
                    float low=axis==0?source.getSprite().getU0():source.getSprite().getV0(),high=axis==0?source.getSprite().getU1():source.getSprite().getV1();
                    double value=(Float.intBitsToFloat(data[vertex*stride+4+axis])-low)/(high-low);uv[vertex][axis]=value;min[axis]=Math.min(min[axis],value);max[axis]=Math.max(max[axis],value);
                }
                for(int vertex=0;vertex<4;vertex++)for(int axis=0;axis<2;axis++){
                    double value=(uv[vertex][axis]-min[axis])/(max[axis]-min[axis]);
                    if(settings==null)value=.001+value*.998;
                    else if(!TextureLayoutMode.fit(settings.layout,1,1,material.usedU()/material.usedV()))value=axis==0?TextureLayoutMode.tileU(value,material.usedU()/material.usedV()):TextureLayoutMode.tileV(value,material.usedU()/material.usedV());
                    data[vertex*stride+4+axis]=Float.floatToRawIntBits(axis==0?material.sprite().getU((float)value*material.usedU()):material.sprite().getV((float)value*material.usedV()));
                }
                result.add(new BakedQuad(data,source.getTintIndex(),source.getDirection(),material.sprite(),source.isShade(),source.hasAmbientOcclusion()));
            }
            List<BakedQuad> frozen=List.copyOf(result);quads.put(face,frozen);return frozen;
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction face,RandomSource random,ModelData data,RenderType layer){return getQuads(state,face,random);}
        @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return passes;}
        @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
    }
    public record CacheUsage(long worldBytes,long worldEntries,int itemModels,long visualKeys){}
    public static CacheUsage cacheUsage(BakedModel model){
        if(model instanceof ProgrammableModel world){world.worldQuads.cleanUp();long bytes=0;for(var values:world.worldQuads.asMap().values())bytes+=160+values.stream().mapToInt(quad->96+quad.getVertices().length*4).sum();return new CacheUsage(bytes,world.worldQuads.size(),0,0);}
        if(model instanceof UniformHousingItem item){item.visualKeys.cleanUp();return new CacheUsage(0,0,item.resolved.size(),item.visualKeys.size());}
        throw new IllegalArgumentException("Not a programmable cube model");
    }
    public static int localFace(Direction world,Direction facing) {
        if(world==Direction.DOWN)return 0;if(world==Direction.UP)return 1;
        Direction local=switch(facing) { case EAST,UP,DOWN -> world.getCounterClockWise();case SOUTH -> world.getOpposite();case WEST -> world.getClockWise();default -> world; };
        return switch(local) { case NORTH -> 2;case SOUTH -> 3;case WEST -> 4;case EAST -> 5;default -> throw new IllegalArgumentException(); };
    }
}
