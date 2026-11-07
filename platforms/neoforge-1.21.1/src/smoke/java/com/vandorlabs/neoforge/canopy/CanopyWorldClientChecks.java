package com.vandorlabs.neoforge.canopy;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.*;
import com.vandorlabs.neoforge.client.NativeCanopyBlockRenderer;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual private world dispatch, root-section exclusion, chunk/BER negatives and reload. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class CanopyWorldClientChecks {
    private static int stage,ticks,wait,frames,checks,gallery;private static String capture;private static volatile boolean captured;private static volatile boolean body=true,chunk=true;private static BlockPos watched=CanopyWorldFixture.ROOT;private static long calls,helpers,vertices;private static java.util.concurrent.CompletableFuture<Void> reload;private static Object oldBake;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Canopy placed client stage="+stage+" "+why);}
    private static final CountingBuffers COUNTING=new CountingBuffers();
    private static final class CountingBuffers implements MultiBufferSource {MultiBufferSource source;final Map<RenderType,CountingVertex> consumers=new IdentityHashMap<>();public VertexConsumer getBuffer(RenderType type){var found=consumers.computeIfAbsent(type,ignored->new CountingVertex());found.source=source.getBuffer(type);return found;}}
    private static final class CountingVertex implements VertexConsumer {VertexConsumer source;public VertexConsumer addVertex(float x,float y,float z){vertices++;source.addVertex(x,y,z);return this;}public VertexConsumer setColor(int r,int g,int b,int a){source.setColor(r,g,b,a);return this;}public VertexConsumer setUv(float u,float v){source.setUv(u,v);return this;}public VertexConsumer setUv1(int u,int v){source.setUv1(u,v);return this;}public VertexConsumer setUv2(int u,int v){source.setUv2(u,v);return this;}public VertexConsumer setNormal(float x,float y,float z){source.setNormal(x,y,z);return this;}}
    private static final class Renderer implements BlockEntityRenderer<NativeCanopyEntity> {final NativeCanopyBlockRenderer original;Renderer(BlockEntityRendererProvider.Context context){original=new NativeCanopyBlockRenderer(context);}Renderer(NativeCanopyBlockRenderer source){original=source;}public void render(NativeCanopyEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){if(!tile.publication().draws()){helpers++;return;}if(!tile.getBlockPos().equals(watched))return;calls++;if(body){COUNTING.source=buffers;original.render(tile,partial,poses,COUNTING,light,overlay);}}public boolean shouldRenderOffScreen(NativeCanopyEntity tile){return original.shouldRenderOffScreen(tile);}public boolean shouldRender(NativeCanopyEntity tile,Vec3 camera){return original.shouldRender(tile,camera);}public int getViewDistance(){return original.getViewDistance();}public AABB getRenderBoundingBox(NativeCanopyEntity tile){return original.getRenderBoundingBox(tile);}}
    private static final class Gate extends BakedModelWrapper<BakedModel>{Gate(BakedModel original){super(original);}@Override public List<BakedQuad>getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return chunk?originalModel.getQuads(state,side,random,data,layer):List.of();}}
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Binding {
        @SubscribeEvent public static void renderer(EntityRenderersEvent.RegisterRenderers event){if(CanopyWorldFixture.enabled()&&!CanopyWorldFixture.canonical())event.registerBlockEntityRenderer(CanopyWorldFixture.type(),Renderer::new);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public static void models(ModelEvent.ModifyBakingResult event){if(!CanopyWorldFixture.enabled())return;event.getModels().replaceAll((id,model)->id.id().toString().equals(CanopyWorldFixture.canonical()?"vandorlabs:regular_canopy_glass":"vandorlabs:canopy_world_regular_canopy_glass")&&!id.getVariant().equals("inventory")?new Gate(CanopyWorldFixture.canonical()?model:NativeCanopyRenderer.terrain(event.getModelBakery(),model)):model);}
    }
    @SuppressWarnings("unchecked") private static void instrumentCanonical(Minecraft mc){
        if(!CanopyWorldFixture.canonical())return;
        try{var dispatcher=mc.getBlockEntityRenderDispatcher();var field=BlockEntityRenderDispatcher.class.getDeclaredField("renderers");field.setAccessible(true);var active=(Map<Object,Object>)field.get(dispatcher);var source=active.get(CanopyWorldFixture.type());need(source instanceof NativeCanopyBlockRenderer,"production canonical renderer registered/reloaded");var copy=new HashMap<>(active);copy.put(CanopyWorldFixture.type(),new Renderer((NativeCanopyBlockRenderer)source));field.set(dispatcher,copy);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Canopy actual dispatcher instrumentation",failure);}
    }
    private static void next(int value){PacketDistributor.sendToServer(new CanopyWorldFixture.Step(value));wait=ticks;}
    private static boolean marker(Minecraft mc,int value){var state=mc.level.getBlockState(CanopyWorldFixture.MARKER);return state.is(Blocks.NOTE_BLOCK)&&state.getValue(NoteBlock.NOTE)==value;}
    private static void capture(String name){capture=name;captured=false;frames=0;calls=helpers=vertices=0;}
    private static boolean ready(Minecraft mc,BlockPos pos){return mc.level.getBlockEntity(pos) instanceof NativeCanopyEntity owner&&owner.available()&&owner.publication().geometry()!=null&&mc.levelRenderer.isSectionCompiled(pos);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!CanopyWorldFixture.enabled())return;var mc=Minecraft.getInstance();if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen){screen.onClose();return;}if(mc.level==null||mc.player==null||mc.gameMode==null)return;need(++ticks<3000,"timeout camera="+mc.player.position()+" mode="+mc.gameMode.getPlayerMode()+" held="+mc.player.getMainHandItem()+" capture="+capture);if(ticks%100==0)System.out.println("[vandorlabs][canopy-world] waiting stage="+stage+" rootCalls="+calls+" helperCalls="+helpers+" submitted="+vertices);
        switch(stage){
            case 0->{if(ticks<80||!marker(mc,0)||mc.gameMode.getPlayerMode()!=GameType.CREATIVE||mc.player.getInventory().selected!=0||!mc.player.getMainHandItem().isEmpty()||!ready(mc,CanopyWorldFixture.ROOT))return;instrumentCanonical(mc);mc.getWindow().setWindowed(1280,720);mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.hideGui=true;mc.options.fov().set(70);mc.options.bobView().set(false);mc.options.cloudStatus().set(CloudStatus.OFF);capture("canopy-world-initial.png");stage=1;}
            case 1->{if(!captured)return;need(calls>0&&vertices>0,"actual visible-owner BER submits authored vertices");next(1);mc.options.fov().set(30);stage=2;}
            case 2->{if(!marker(mc,1)||ticks<wait+50||mc.player.position().distanceToSqr(2.5,94,-2.5)>.02)return;var owner=(NativeCanopyEntity)mc.level.getBlockEntity(CanopyWorldFixture.ROOT);var renderer=mc.getBlockEntityRenderDispatcher().getRenderer(owner);var frustum=mc.levelRenderer.getFrustum();need(!frustum.isVisible(new AABB(0,96,0,16,112,16)),"camera excludes entire root section");need(frustum.isVisible(renderer.getRenderBoundingBox(owner)),"visible overhang inside conservative bounds");capture("canopy-world-cull-positive.png");stage=3;}
            case 3->{if(!captured)return;need(calls>0&&vertices>0,"visible overhang dispatches when root section is culled; calls="+calls+" vertices="+vertices);body=false;capture("canopy-world-cull-negative.png");stage=4;}
            case 4->{if(!captured)return;need(vertices==0,"disabled BER submits no vertices");body=true;watched=CanopyWorldFixture.REGULAR;next(2);mc.options.fov().set(55);stage=5;}
            case 5->{if(!marker(mc,2)||ticks<wait+50||!ready(mc,watched))return;var owner=(NativeCanopyEntity)mc.level.getBlockEntity(watched);need(owner.getModelData().get(NativeCanopyEntity.TERRAIN)!=null,"fixed glass immutable terrain data");need(mc.getBlockRenderer().getBlockModelShaper().getBlockModel(owner.getBlockState()) instanceof Gate,"actual pending-bake terrain wrapper");capture("canopy-world-regular-positive.png");stage=6;}
            case 6->{if(!captured)return;need(vertices==0,"regular glass has no duplicate BER vertices");chunk=false;mc.levelRenderer.allChanged();wait=ticks;stage=7;}
            case 7->{if(ticks<wait+100||!ready(mc,watched)||!mc.levelRenderer.hasRenderedAllSections())return;capture("canopy-world-regular-negative.png");stage=8;}
            case 8->{if(!captured)return;chunk=true;mc.levelRenderer.allChanged();gallery=0;watched=CanopyWorldFixture.root(gallery);next(3);stage=9;}
            case 9->{if(!marker(mc,3+gallery)||ticks<wait+40||!ready(mc,watched)||!mc.levelRenderer.hasRenderedAllSections())return;var owner=(NativeCanopyEntity)mc.level.getBlockEntity(watched);need(owner.kind().equals(CanopyWorldFixture.KINDS[gallery])&&owner.getType()==CanopyWorldFixture.type(),"exact placed kind/factory");need(!owner.getUpdateTag(mc.level.registryAccess()).toString().contains("server-only"),"private owner bytes absent from client");capture("canopy-world-kind-"+gallery+".png");stage=10;}
            case 10->{if(!captured)return;if(gallery!=1)need(vertices>0,"placed authored body kind="+gallery);else need(vertices==0,"regular exclusively terrain");if(++gallery<CanopyWorldFixture.KINDS.length){watched=CanopyWorldFixture.root(gallery);next(3+gallery);stage=9;}else{oldBake=NativeCanopyRenderer.currentBake();reload=mc.reloadResourcePacks();wait=ticks;stage=11;}}
            case 11->{if(!reload.isDone()||ticks<wait+50||!mc.levelRenderer.hasRenderedAllSections())return;reload.join();instrumentCanonical(mc);need(NativeCanopyRenderer.currentBake()!=oldBake,"new resource generation active");capture("canopy-world-reloaded.png");stage=12;}
            case 12->{if(!captured)return;need(vertices>0,"placed BER survives resource reload");next(14);stage=13;}
            case 13->{if(!marker(mc,14))return;var moving=(NativeCanopyEntity)mc.level.getBlockEntity(CanopyWorldFixture.root(2));if(moving==null||!moving.links().open()||moving.links().progress()!=1)return;need(moving.publication().progress()==1,"automatic client physical opening endpoint");next(15);stage=14;}
            case 14->{if(!marker(mc,15))return;var moving=(NativeCanopyEntity)mc.level.getBlockEntity(CanopyWorldFixture.root(2));if(moving==null||moving.links().open()||moving.links().progress()>.65F||moving.links().progress()<=0)return;need(moving.publication().progress()>0,"actual partial client closing pose");next(16);stage=15;}
            case 15->{if(!marker(mc,16))return;var moving=(NativeCanopyEntity)mc.level.getBlockEntity(CanopyWorldFixture.root(2));if(moving==null||!moving.links().open()||moving.links().progress()!=1)return;next(17);stage=16;}
            case 16->{if(!marker(mc,17))return;System.out.println("[vandorlabs][canopy-world] client PASS checks="+checks+" actualrootculling/chunkBERnegatives/11kinds/reload");mc.stop();stage=17;}
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!CanopyWorldFixture.enabled()||capture==null||++frames<8)return;var mc=Minecraft.getInstance();String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{captured=true;});}
}
