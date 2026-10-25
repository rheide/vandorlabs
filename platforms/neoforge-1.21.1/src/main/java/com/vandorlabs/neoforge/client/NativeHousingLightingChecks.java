package com.vandorlabs.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.neoforge.trigger.TriggerRegistration;
import com.vandorlabs.render.HousingLightPolicy;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.*;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;

/** Actual baked-model and VertexConsumer checks; does not claim hardware/shader RGB acceptance. */
public final class NativeHousingLightingChecks {
    private NativeHousingLightingChecks(){}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Housing lighting: "+message);}
    public static void run(Minecraft mc){
        MixedRegion region=new MixedRegion();int packed=NativeHousingLighting.snapshot(region,BlockPos.ZERO);need(packed==0x00F000E0,"different sky/torch neighbours must combine");need(region.reads==12,"bounded six-neighbour snapshot");
        int count=0;
        for(BlockState state:List.of(VandorLabsModern.PROGRAMMABLE_BLOCK.get().defaultBlockState(),TriggerRegistration.BLOCK.get().defaultBlockState(),ShapeRegistration.SLAB.get().defaultBlockState())){
            var model=mc.getBlockRenderer().getBlockModel(state);ModelData input=state.getBlock() instanceof ProgrammableSlabBlock?new ProgrammableShapeEntity(BlockPos.ZERO,state).getModelData():ModelData.builder().with(ProgrammableBlockEntity.SETTINGS,ProgrammableBlockEntity.defaults()).build();
            ModelData data=model.getModelData(region,BlockPos.ZERO,state,input);need(data.get(NativeHousingLighting.LIGHT)!=null&&data.get(NativeHousingLighting.LIGHT)==packed,"actual model publishes immutable light data");need(model.useAmbientOcclusion(state,data,RenderType.solid())==TriState.FALSE,"actual renderer AO overload must be FALSE");
            List<BakedQuad> faces=new ArrayList<>();for(Direction side:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})faces.addAll(model.getQuads(state,side,RandomSource.create(0),data,RenderType.solid()));need(faces.size()==6,"canonical six world faces");
            for(BakedQuad quad:faces){need(!quad.isShade()&&!quad.hasAmbientOcclusion(),"world faces must not add directional/AO shading");Recorder consumer=new Recorder();consumer.putBulkData(new PoseStack().last(),quad,1,1,1,1,0x00300020,0);need(consumer.lights.size()==4&&consumer.lights.stream().allMatch(value->value==packed),"actual VertexConsumer uses both baked channels");need(consumer.white==4,"white material RGB remains uniform without artificial dimming");
                Recorder brighterTorch=new Recorder();brighterTorch.putBulkData(new PoseStack().last(),quad,1,1,1,1,0x00D000F0,0);need(brighterTorch.lights.stream().allMatch(value->value==0x00F000F0),"consumer independently merges brighter sampled torch with baked sky");payload(quad,packed);count++;
            }
            // The old missing-snapshot path must visibly differ in the emitted light coordinates.
            List<BakedQuad> negative=new ArrayList<>();for(Direction side:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})negative.addAll(model.getQuads(state,side,RandomSource.create(0),input,RenderType.solid()));Recorder old=new Recorder();old.putBulkData(new PoseStack().last(),negative.get(0),1,1,1,1,0x00300020,0);need(old.lights.stream().anyMatch(value->value!=packed),"missing light snapshot negative control");
        }
        System.out.println("[vandorlabs][modern] housing-lighting PASS actual3models/"+count+" faces, renderer AO FALSE/shadeoff, six-neighbour mixedsky/torch, realVertexConsumer channelMAX, extendedpayload unchanged and missing-snapshot negative");
    }
    private static void payload(BakedQuad quad,int packed){
        int[] source=quad.getVertices();int stride=source.length/4,extendedStride=stride+4;int[] extended=new int[extendedStride*4];for(int vertex=0;vertex<4;vertex++){System.arraycopy(source,vertex*stride,extended,vertex*extendedStride,stride);for(int offset=stride;offset<extendedStride;offset++)extended[vertex*extendedStride+offset]=0x5A000000+vertex*extendedStride+offset;}
        int[] before=extended.clone();BakedQuad original=new BakedQuad(extended,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),quad.isShade(),quad.hasAmbientOcclusion());BakedQuad applied=NativeHousingLighting.apply(original,packed);need(Arrays.equals(before,original.getVertices()),"source records immutable");for(int i=0;i<before.length;i++)need(applied.getVertices()[i]==(i%extendedStride==6?HousingLightPolicy.max(before[i],packed):before[i]),"complete extended record retained");need(applied.getDirection()==original.getDirection()&&applied.getTintIndex()==original.getTintIndex()&&applied.getSprite()==original.getSprite()&&applied.isShade()==original.isShade()&&applied.hasAmbientOcclusion()==original.hasAmbientOcclusion(),"quad metadata retained");
    }
    private static final class MixedRegion implements BlockAndTintGetter {
        int reads;
        @Override public int getBrightness(LightLayer layer,BlockPos pos){reads++;if(pos.equals(BlockPos.ZERO.relative(Direction.UP)))return layer==LightLayer.SKY?15:2;if(pos.equals(BlockPos.ZERO.relative(Direction.WEST)))return layer==LightLayer.SKY?3:14;return 0;}
        @Override public BlockState getBlockState(BlockPos pos){throw new AssertionError("Light snapshot queried block state");}
        @Override public BlockEntity getBlockEntity(BlockPos pos){throw new AssertionError("Light snapshot queried live block entity");}
        @Override public FluidState getFluidState(BlockPos pos){throw new AssertionError("Light snapshot queried fluid");}
        @Override public LevelLightEngine getLightEngine(){throw new AssertionError("Light snapshot bypassed standard region brightness");}
        @Override public int getBlockTint(BlockPos pos,ColorResolver color){return 0xFFFFFF;}
        @Override public float getShade(Direction face,boolean shade){return 1;}
        @Override public int getHeight(){return 384;}
        @Override public int getMinBuildHeight(){return -64;}
    }
    private static final class Recorder implements VertexConsumer {
        final List<Integer> lights=new ArrayList<>();int white;
        @Override public VertexConsumer addVertex(float x,float y,float z){return this;}
        @Override public VertexConsumer setColor(int red,int green,int blue,int alpha){if(red==255&&green==255&&blue==255&&alpha==255)white++;return this;}
        @Override public VertexConsumer setUv(float u,float v){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){lights.add((u&65535)|(v<<16));return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
}
