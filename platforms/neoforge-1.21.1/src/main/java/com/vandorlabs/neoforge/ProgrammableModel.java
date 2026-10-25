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
        event.getModels().replaceAll((id,model) -> id.id().equals(ResourceLocation.parse("vandorlabs:programmable_block"))&&!id.getVariant().equals("inventory")?new ProgrammableModel(model,sprites,margin):model);
    }
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data) {
        com.vandorlabs.neoforge.client.JoinedCubeSnapshots.request(pos);
        return data;
    }
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
        return result;
    }
    public static int localFace(Direction world,Direction facing) {
        if(world==Direction.DOWN)return 0;if(world==Direction.UP)return 1;
        Direction local=switch(facing) { case EAST,UP,DOWN -> world.getCounterClockWise();case SOUTH -> world.getOpposite();case WEST -> world.getClockWise();default -> world; };
        return switch(local) { case NORTH -> 2;case SOUTH -> 3;case WEST -> 4;case EAST -> 5;default -> throw new IllegalArgumentException(); };
    }
}
