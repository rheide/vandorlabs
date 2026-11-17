package com.vandorlabs.neoforge.navigation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vandorlabs.rack.EmbeddedRackMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
/** Attachments are one reader and one cassette per assembly; the static shell remains chunk-batched. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeNavigationTableClient {
 @FunctionalInterface public interface MapRenderer {void map(NativeNavigationTableEntity tile,PoseStack poses,MultiBufferSource buffers,int light,float partial);}
 public static MapRenderer MAP=(t,p,b,l,f)->{};
 static ResourceLocation part(String name){return ResourceLocation.fromNamespaceAndPath("vandorlabs","block/navigation_table/"+name);}
 @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(NativeNavigationTableBindings.ENTITY,Renderer::new);}
 @SubscribeEvent public static void menus(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event){event.register(NativeNavigationTableBindings.MENU,NativeNavigationTableScreen::new);}
 @SubscribeEvent public static void models(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event){for(var p:new String[]{"bay_frame","bay_module","chip_frame"})event.register(ModelResourceLocation.standalone(part(p)));}
 private static final class Renderer implements BlockEntityRenderer<NativeNavigationTableEntity> {
  private final com.vandorlabs.animation.VisualClock clock=new com.vandorlabs.animation.VisualClock();
  private final java.util.Map<net.minecraft.world.level.Level,java.util.LinkedHashMap<net.minecraft.core.BlockPos,EmbeddedRackMotion>> motion=new java.util.WeakHashMap<>();
  private record Bounds(com.vandorlabs.navigation.NavigationLayout.Screen plane,net.minecraft.world.phys.AABB box,net.minecraft.world.phys.AABB near){}
  private final java.util.Map<NativeNavigationTableEntity,Bounds> bounds=new java.util.WeakHashMap<>();
  Renderer(BlockEntityRendererProvider.Context c){}
  private static void face(PoseStack p,Direction d){p.translate(.5,0,.5);p.mulPose(Axis.YP.rotationDegrees(-d.toYRot()+180));p.translate(-.5,0,-.5);}
  private static void model(String name,PoseStack p,MultiBufferSource b,int light,int overlay){var mc=Minecraft.getInstance();var m=mc.getModelManager().getModel(ModelResourceLocation.standalone(part(name)));mc.getBlockRenderer().getModelRenderer().renderModel(p.last(),b.getBuffer(RenderType.cutout()),null,m,1,1,1,light,overlay,net.neoforged.neoforge.client.model.data.ModelData.EMPTY,RenderType.cutout());}

  private static void artwork(NativeNavigationTableEntity t,PoseStack poses,MultiBufferSource buffers,int overlay){if(!t.ownsReader()||t.texture()<0)return;var mc=Minecraft.getInstance();var sprite=mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.parse(com.vandorlabs.neoforge.ModernFinishCatalog.option(t.texture()).texture()));var target=buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));var q=t.plane();point(target,poses,t,q.topLeft,q.normal,sprite.getU0(),sprite.getV0(),overlay);point(target,poses,t,q.bottomLeft,q.normal,sprite.getU0(),sprite.getV1(),overlay);point(target,poses,t,q.bottomRight,q.normal,sprite.getU1(),sprite.getV1(),overlay);point(target,poses,t,q.topRight,q.normal,sprite.getU1(),sprite.getV0(),overlay);}
  private static void point(com.mojang.blaze3d.vertex.VertexConsumer target,PoseStack poses,NativeNavigationTableEntity t,com.vandorlabs.navigation.NavigationLayout.Vec3 p,com.vandorlabs.navigation.NavigationLayout.Vec3 normal,float u,float v,int overlay){double offset=com.vandorlabs.navigation.NavigationLayout.OVERLAY_OFFSET_BLOCKS;var pos=t.getBlockPos();target.addVertex(poses.last().pose(),(float)(p.x-pos.getX()+normal.x*offset),(float)(p.y-pos.getY()+normal.y*offset),(float)(p.z-pos.getZ()+normal.z*offset)).setColor(255,255,255,255).setUv(u,v).setOverlay(overlay).setLight(0xf000f0).setNormal(poses.last(),(float)normal.x,(float)normal.y,(float)normal.z);}
  @Override public void render(NativeNavigationTableEntity t,float partial,PoseStack p,MultiBufferSource b,int light,int overlay){if(!t.formed())return;var mc=Minecraft.getInstance();if(t.hasMount()){p.pushPose();face(p,t.getBlockState().getValue(NativeNavigationTableBlock.FACING));p.translate(0,t.kind()==com.vandorlabs.navigation.NavigationLayout.Kind.FLAT?.375:0,0);model("chip_frame",p,b,light,overlay);if(!t.chipStack().isEmpty()){p.translate(.5,.28125,-.035);p.scale(.30F,.30F,.30F);mc.getItemRenderer().renderStatic(t.chipStack(),ItemDisplayContext.FIXED,light,overlay,p,b,t.getLevel(),0);}p.popPose();}if(t.hasModuleBay()){p.pushPose();face(p,t.moduleFacing());model("bay_frame",p,b,light,overlay);double now=clock.sample(System.nanoTime(),mc.isPaused());var worldMotion=motion.computeIfAbsent(t.getLevel(),k->new java.util.LinkedHashMap<>());var anim=worldMotion.computeIfAbsent(t.visualPosition(),k->new EmbeddedRackMotion());if(worldMotion.size()>2048){var iterator=worldMotion.keySet().iterator();iterator.next();iterator.remove();}anim.observe(t.moduleStack().isEmpty()?0:1,t.moduleSequence(),0,0,0,0,0,0,0,now);if(anim.visible(0,now)){p.translate(0,0,-anim.offset(0,now));model("bay_module",p,b,light,overlay);}p.popPose();}if(t.selectedNavigation()&&!t.publicVisual())MAP.map(t,p,b,light,partial);else artwork(t,p,b,overlay);}
  @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(NativeNavigationTableEntity t){var s=t.plane();var cached=bounds.get(t);if(cached!=null&&cached.plane==s)return cached.box;if(!t.ownsReader()){var box=new net.minecraft.world.phys.AABB(t.getBlockPos()).inflate(.25);bounds.put(t,new Bounds(s,box,box.inflate(64)));return box;}double minX=Math.min(Math.min(s.bottomLeft.x,s.bottomRight.x),Math.min(s.topLeft.x,s.topRight.x)),minY=Math.min(s.bottomLeft.y,s.topRight.y),minZ=Math.min(Math.min(s.bottomLeft.z,s.bottomRight.z),Math.min(s.topLeft.z,s.topRight.z)),maxX=Math.max(Math.max(s.bottomLeft.x,s.bottomRight.x),Math.max(s.topLeft.x,s.topRight.x)),maxY=Math.max(s.bottomLeft.y,s.topRight.y),maxZ=Math.max(Math.max(s.bottomLeft.z,s.bottomRight.z),Math.max(s.topLeft.z,s.topRight.z));var box=new net.minecraft.world.phys.AABB(minX,t.getBlockPos().getY(),minZ,maxX,maxY,maxZ).inflate(.25);bounds.put(t,new Bounds(s,box,box.inflate(64)));return box;}
  @Override public boolean shouldRender(NativeNavigationTableEntity t,net.minecraft.world.phys.Vec3 camera){getRenderBoundingBox(t);return bounds.get(t).near.contains(camera);}
  @Override public boolean shouldRenderOffScreen(NativeNavigationTableEntity t){return false;}
 }
 private NativeNavigationTableClient(){}
}
