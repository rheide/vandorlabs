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
    private record MaterialSnapshot(TextureAtlasSprite sprite,float usedU,float usedV) {}
    private final java.util.Map<Integer,MaterialSnapshot> sprites;
    private final TextureAtlasSprite margin;
    private ProgrammableModel(BakedModel model,java.util.Map<Integer,MaterialSnapshot> sprites,TextureAtlasSprite margin) { super(model);this.sprites=sprites;this.margin=margin; }
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        java.util.Map<Integer,MaterialSnapshot> resolved=new java.util.HashMap<>();
        for(var choice:ModernFinishCatalog.allChoices()) {
            var entry=choice.entry();
            var sprite=event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(entry.texture())));
            resolved.put(choice.choice(),new MaterialSnapshot(sprite,entry.u(),entry.v()));
        }
        java.util.Map<Integer,MaterialSnapshot> sprites=java.util.Map.copyOf(resolved);
        TextureAtlasSprite margin=event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.withDefaultNamespace("block/black_concrete")));
        event.getModels().replaceAll((id,model) -> {
            if(id.id().equals(ResourceLocation.parse("vandorlabs:programmable_trigger_block"))&&id.getVariant().equals("inventory"))return new UniformHousingItem(model,sprites,margin,null);
            return java.util.Set.of(ResourceLocation.parse("vandorlabs:programmable_block"),ResourceLocation.parse("vandorlabs:programmable_trigger_block")).contains(id.id())&&!id.getVariant().equals("inventory")?new ProgrammableModel(model,sprites,margin):model;
        });
    }
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data) {
        if(state!=null&&state.is(VandorLabsModern.PROGRAMMABLE_BLOCK.get()))com.vandorlabs.neoforge.client.JoinedCubeSnapshots.request(pos);
        return com.vandorlabs.neoforge.client.NativeHousingLighting.snapshot(level,pos,data);
    }
    @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return net.neoforged.neoforge.common.util.TriState.FALSE;}
    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType renderType) {
        List<BakedQuad> base=originalModel.getQuads(state,side,random,data,renderType);
        if(base.isEmpty())return base;
        ProgrammableBlockDialog.State settings=data.get(ProgrammableBlockEntity.SETTINGS);
        if(settings==null)settings=ProgrammableBlockEntity.defaults();
        List<BakedQuad> result=new ArrayList<>(base.size());
        for(BakedQuad quad:base) {
            int local=localFace(quad.getDirection(),state.getValue(ProgrammableBlock.FACING));
            int choice=settings.facesEnabled&&settings.face(local)>=0?settings.face(local):settings.mainTexture;
            int index=ModernFinishCatalog.renderChoice(choice);
            MaterialSnapshot material=sprites.getOrDefault(index,sprites.get(0));
            TextureAtlasSprite sprite=material.sprite();
            JoinedCubeFaces joined=data.get(ProgrammableBlockEntity.JOINED);
            var plane=joined==null?null:joined.face(quad.getDirection());
            if(settings.layout!=1&&plane!=null){result.addAll(JoinedCubeQuads.map(quad,state.getValue(ProgrammableBlock.FACING),plane,settings.layout,sprite,material.usedU(),material.usedV(),margin));continue;}
            float uScale=material.usedU(),vScale=material.usedV();
            double aspect=material.usedU()/material.usedV();
            if(!TextureLayoutMode.fit(settings.layout,1,1,aspect)) {
                uScale*=TextureLayoutMode.tileU(1,aspect);
                vScale*=TextureLayoutMode.tileV(1,aspect);
            }
            // Vanilla FaceBakery shrinks UVs for its original sprite. Rebuild the
            // authored unit orientation instead of carrying that unrelated inset
            // into a different material or a joined image.
            result.addAll(JoinedCubeQuads.map(quad,state.getValue(ProgrammableBlock.FACING),
                new com.vandorlabs.render.HousingFacePlane(new int[3],new int[]{1,1,1}),0,
                sprite,uScale,vScale,margin));
        }
        Integer light=data.get(com.vandorlabs.neoforge.client.NativeHousingLighting.LIGHT);
        if(light!=null)for(int i=0;i<result.size();i++)result.set(i,com.vandorlabs.neoforge.client.NativeHousingLighting.apply(result.get(i),light));
        return result;
    }
    /** Configured trigger items always preview the stored OFF material, independently of live signals. */
    private static final class UniformHousingItem extends BakedModelWrapper<BakedModel> {
        private final java.util.Map<Integer,MaterialSnapshot> materials;
        private final TextureAtlasSprite margin;
        private final ProgrammableBlockDialog.State settings;
        private final ProgrammableModel renderer;
        private final ModelData itemData;
        private final java.util.Map<Long,BakedModel> resolved=new java.util.HashMap<>();
        UniformHousingItem(BakedModel original,java.util.Map<Integer,MaterialSnapshot> materials,TextureAtlasSprite margin,ProgrammableBlockDialog.State settings){super(original);this.materials=materials;this.margin=margin;this.settings=settings;this.renderer=new ProgrammableModel(original,materials,margin);this.itemData=settings==null?ModelData.EMPTY:ModelData.builder().with(ProgrammableBlockEntity.SETTINGS,settings).build();}
        @Override public net.minecraft.client.renderer.block.model.ItemOverrides getOverrides(){
            return new net.minecraft.client.renderer.block.model.ItemOverrides(){
                @Override public BakedModel resolve(BakedModel original,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel world,net.minecraft.world.entity.LivingEntity entity,int seed){
                    var data=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
                    if(data==null)data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    if(data==null)return UniformHousingItem.this;
                    var tag=data.copyTag();if(tag.contains("BlockEntityTag",10))tag=tag.getCompound("BlockEntityTag");
                    int choice=ProgrammableBlockDialog.savedMainTexture(tag.getInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY),tag.contains("HousingTextureVersion",3));
                    int index=ModernFinishCatalog.renderChoice(choice);
                    int layout=ProgrammableBlockDialog.savedTextureLayout(tag.getInt(ProgrammableBlockDialog.LAYOUT_KEY),tag.contains(ProgrammableBlockDialog.LAYOUT_KEY,3),!tag.contains("SlabTileSides",1)||tag.getBoolean("SlabTileSides"));
                    long key=((long)index<<2)|layout;
                    var result=resolved.get(key);if(result==null){result=new UniformHousingItem(originalModel,materials,margin,new ProgrammableBlockDialog.State(index,layout,false,null,-1));resolved.put(key,result);}return result;
                }
            };
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction face,RandomSource random){
            if(settings==null)return originalModel.getQuads(state,face,random);
            return renderer.getQuads(VandorLabsModern.PROGRAMMABLE_BLOCK.get().defaultBlockState(),face,random,itemData,null);
        }
        @Override public List<BakedQuad> getQuads(BlockState state,Direction face,RandomSource random,ModelData data,RenderType layer){return getQuads(state,face,random);}
        @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return List.of(this);}
        @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
    }
    public static int localFace(Direction world,Direction facing) {
        if(world==Direction.DOWN)return 0;if(world==Direction.UP)return 1;
        Direction local=switch(facing) { case EAST,UP,DOWN -> world.getCounterClockWise();case SOUTH -> world.getOpposite();case WEST -> world.getClockWise();default -> world; };
        return switch(local) { case NORTH -> 2;case SOUTH -> 3;case WEST -> 4;case EAST -> 5;default -> throw new IllegalArgumentException(); };
    }
}
