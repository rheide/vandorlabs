package com.vandorlabs.neoforge.wall;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;

/** Native model/VertexConsumer boundaries, including actual flat-light tessellation and its negative. */
public final class WallModelChecks {
    private static final int OWN=2<<20|3<<4;
    private static boolean done;
    private WallModelChecks(){}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Wall model: "+message);}
    public static boolean ready(Minecraft mc){if(mc.level==null)return false;for(int i=0;i<3;i++){var data=mc.level.getModelData(new BlockPos(320+i*4,80,8)).get(WallRenderSnapshot.DATA);if(data==null||data.opaque.isEmpty())return false;}return true;}
    public static void run(Minecraft mc){
        if(done)return;need(ready(mc),"three published actual scene models");int faces=0,panes=0;List<Object> report=new ArrayList<>();
        for(int family=0;family<3;family++){
            BlockPos pos=new BlockPos(320+family*4,80,8);var state=mc.level.getBlockState(pos);var snapshot=mc.level.getModelData(pos).get(WallRenderSnapshot.DATA);var model=mc.getBlockRenderer().getBlockModel(state);
            var captured=new RenderRegionCache().createRegion(mc.level,SectionPos.of(pos),true);need(captured!=null,"actual captured render region");var region=new Lights(captured,pos);var data=model.getModelData(region,pos,state,mc.level.getModelData(pos));need(data.get(WallModel.OWN_LIGHT)==OWN&&region.ownReads==2&&region.neighborReads==0,"light publication samples own sky/block only");
            need(model.useAmbientOcclusion(state,data,RenderType.cutoutMipped())==TriState.FALSE,"native AO overload disabled");var quads=model.getQuads(state,null,RandomSource.create(0),data,RenderType.cutoutMipped());need(quads.size()==snapshot.opaque.size()*2,"two-sided legacy opaque faces are emitted exactly once per winding");
            for(Direction side:Direction.values())need(model.getQuads(state,side,RandomSource.create(0),data,RenderType.cutoutMipped()).isEmpty(),"TESR surfaces cannot use native full-block neighbor culling");
            for(int i=0;i<snapshot.opaque.size();i++){var source=snapshot.opaque.get(i);for(int winding=0;winding<2;winding++){var quad=quads.get(i*2+winding);need(!quad.isShade()&&!quad.hasAmbientOcclusion(),"no additional native directional/AO shade");need(quad.getSprite().contents().name().equals(source.texture()),"selected atlas material identity");int[] vertices=quad.getVertices();for(int vertex=0;vertex<4;vertex++){var p=source.vertices().get(winding==0?vertex:3-vertex);int at=vertex*8;need(Math.abs(Float.intBitsToFloat(vertices[at])-p.x())<1e-6&&Math.abs(Float.intBitsToFloat(vertices[at+1])-p.y())<1e-6&&Math.abs(Float.intBitsToFloat(vertices[at+2])-p.z())<1e-6,"world-oriented vertex retains position");need(vertices[at+4]==Float.floatToRawIntBits(quad.getSprite().getU((float)p.u()))&&vertices[at+5]==Float.floatToRawIntBits(quad.getSprite().getV((float)p.v())),"UV remains attached to the same position in either winding");need(vertices[at+6]==OWN,"emitted own-position light");}faces++;}}
            Recorder actual=tessellate(mc,region,model,state,pos,data);need(actual.lights.size()==quads.size()*4&&actual.lights.stream().allMatch(value->value==OWN),"actual ModelBlockRenderer flat path must retain own light despite bright surrounding cells");
            var atlas=mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);for(var pane:snapshot.glass){Recorder output=new Recorder();WallPaneRenderer.emit(pane,new PoseStack().last(),OWN,atlas.apply(pane.texture()),output);need(output.lights.size()==4&&output.lights.stream().allMatch(value->value==OWN),"pane uses exact own light without neighboring MAX");need(output.alphas.size()==4&&output.alphas.stream().allMatch(value->value==(int)(pane.opacity()*255F)),"BLOCK color packing retains legacy float-to-byte opacity");panes++;}
            if(family==2){BakedQuad target=null;for(var q:quads){float[] n=normal(q);Direction real=Direction.getNearest(n[0],n[1],n[2]);boolean boundary=true;int axis=real.getAxis()==Direction.Axis.X?0:real.getAxis()==Direction.Axis.Y?1:2;for(int v=0;v<4;v++)boundary&=Math.abs(Float.intBitsToFloat(q.getVertices()[v*8+axis])-(real.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0))<1e-6;if(boundary){target=new BakedQuad(q.getVertices(),q.getTintIndex(),real,q.getSprite(),false,false);break;}}need(target!=null,"full porthole has an outer-face negative control");final BakedQuad wrong=target;var negative=new BakedModelWrapper<BakedModel>(model){@Override public List<BakedQuad> getQuads(BlockState s,Direction d,RandomSource r,ModelData md,RenderType layer){return d==null?List.of(wrong):List.of();}};Recorder bad=tessellate(mc,region,negative,state,pos,data);need(bad.lights.stream().anyMatch(value->value!=OWN),"real geometric nominal face must reject brighter-neighbor light contamination");}
            report.add(Map.of("profile",family,"opaque",snapshot.opaque,"glass",snapshot.glass,"quadCount",quads.size(),"light",OWN));
        }
        try{var path=mc.gameDirectory.toPath().resolve("screenshots/modern-wall-emission.json");Files.createDirectories(path.getParent());Files.writeString(path,new Gson().toJson(Map.of("format",1,"cases",report)));}catch(Exception e){throw new IllegalStateException("Wall emission report",e);}
        NativeWallGoldenChecks.run(mc);
        done=true;System.out.println("[vandorlabs][modern] wall-models PASS actual3models/"+faces+" two-sided opaque quads/"+panes+" pane faces, atlas UV/normal data, guarded captured regions, actual native flat-light renderer and bright-neighbor negative");
    }
    private static float[] normal(BakedQuad q){int value=q.getVertices()[7];return new float[]{(byte)value/127F,(byte)(value>>8)/127F,(byte)(value>>16)/127F};}
    private static Recorder tessellate(Minecraft mc,BlockAndTintGetter region,BakedModel model,BlockState state,BlockPos pos,ModelData data){Recorder result=new Recorder();mc.getBlockRenderer().getModelRenderer().tesselateBlock(region,model,state,pos,new PoseStack(),result,false,RandomSource.create(0),0,0,data,RenderType.cutoutMipped());return result;}
    private static final class Lights implements BlockAndTintGetter {
        final BlockAndTintGetter source;final BlockPos pos;int ownReads,neighborReads;
        Lights(BlockAndTintGetter source,BlockPos pos){this.source=source;this.pos=pos;}
        @Override public int getBrightness(LightLayer layer,BlockPos at){if(at.equals(pos)){ownReads++;return layer==LightLayer.SKY?2:3;}neighborReads++;return layer==LightLayer.SKY?15:14;}
        @Override public float getShade(Direction direction,boolean shade){return shade?.5F:1;}
        @Override public BlockState getBlockState(BlockPos at){return source.getBlockState(at);}
        @Override public FluidState getFluidState(BlockPos at){return source.getFluidState(at);}
        @Override public BlockEntity getBlockEntity(BlockPos at){throw new AssertionError("Wall section renderer attempted a live tile read");}
        @Override public ModelData getModelData(BlockPos at){return source.getModelData(at);}
        @Override public LevelLightEngine getLightEngine(){return source.getLightEngine();}
        @Override public int getBlockTint(BlockPos at,ColorResolver resolver){return source.getBlockTint(at,resolver);}
        @Override public int getHeight(){return source.getHeight();}
        @Override public int getMinBuildHeight(){return source.getMinBuildHeight();}
    }
    private static final class Recorder implements VertexConsumer {
        final List<Integer> lights=new ArrayList<>(),alphas=new ArrayList<>();
        @Override public VertexConsumer addVertex(float x,float y,float z){return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){alphas.add(a);return this;}
        @Override public VertexConsumer setUv(float u,float v){return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){lights.add(u&65535|v<<16);return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
}
