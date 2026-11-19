package com.vandorlabs.neoforge.navigation.observer.client;
import java.util.*;import com.mojang.blaze3d.vertex.PoseStack;import com.mojang.math.Axis;import com.vandorlabs.animation.VisualClock;import com.vandorlabs.neoforge.navigation.observer.*;import com.vandorlabs.neoforge.builders.client.*;import net.minecraft.client.Minecraft;import net.minecraft.client.renderer.*;import net.minecraft.client.renderer.blockentity.*;import net.minecraft.resources.ResourceLocation;import net.minecraft.client.resources.model.*;import net.minecraft.world.inventory.InventoryMenu;import net.minecraft.world.phys.AABB;
/** Three immutable meshes and one pause-aware clock; no per-frame world scans or tessellation. */
public final class NativeObserverRenderer implements BlockEntityRenderer<NativeObserverEntity> {
 private static VisualClock clock=new VisualClock();private static Map<String,NativeBuilderModel> MESHES=Map.of();
 public static final List<String> MODELS=List.of("standalone_dish/scanning/parts/static","standalone_dish/scanning/parts/rotor","standalone_antenna/scanning/complete");
 public static ResourceLocation model(String id){return ResourceLocation.parse("vandorlabs:block/navigation_observers/"+id);}
 public static void bake(Map<ModelResourceLocation,BakedModel> models){var next=new HashMap<String,NativeBuilderModel>();long bytes=0;for(var id:MODELS){var m=models.get(ModelResourceLocation.standalone(model(id)));if(m==null)throw new IllegalStateException("Missing observer mesh "+id);var mesh=new NativeBuilderModel(m);bytes+=mesh.retainedBytes();if(bytes>4*1024*1024)throw new IllegalStateException("Observer mesh generation byte budget");next.put(id,mesh);}MESHES=Map.copyOf(next);}
 public static void clearClock(){clock=new VisualClock();}public NativeObserverRenderer(BlockEntityRendererProvider.Context c){}
 @Override public void render(NativeObserverEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){if(tile.inert()||!tile.formed()||tile.getLevel()==null||MESHES.size()!=3)return;var d=tile.design();var l=tile.layout();double now=clock.sample(System.nanoTime(),Minecraft.getInstance().isPaused());poses.pushPose();try{drawPort(tile,poses,buffers,l,light,overlay);poses.translate(-l.pivotX(),0,-l.pivotZ());NativeBuilderRenderer.orient(poses,tile.facing(),d.width(),d.depth());if(tile.inverted()){poses.translate(0,1,0);poses.scale(1,-1,1);}float scale=(float)com.vandorlabs.navigation.ObserverScale.linearScale(d.id(),d.width());poses.scale(scale,scale,scale);var target=buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));if(d.id().equals("standalone_dish")){MESHES.get("standalone_dish/scanning/parts/static").emit(poses.last(),target,light,overlay);poses.translate(1.5,.125,1.5);double phase=(tile.key().getLeastSignificantBits()&65535)/65536D*80;poses.mulPose(Axis.YP.rotationDegrees((float)(tile.powered()?55*Math.sin((now+phase)*Math.PI/80):0)));poses.translate(-1.5,-.125,-1.5);MESHES.get("standalone_dish/scanning/parts/rotor").emit(poses.last(),target,light,overlay);}else MESHES.get("standalone_antenna/scanning/complete").emit(poses.last(),target,light,overlay);}finally{poses.popPose();}}
 private static void drawPort(NativeObserverEntity tile,PoseStack poses,MultiBufferSource buffers,com.vandorlabs.machines.CoreLayout layout,int light,int overlay){
  var sprite=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(
   ResourceLocation.parse("vandorlabs:blocks/resource_ports/power_in_"+(tile.powered()?"on":"off")));
  var output=buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
  var pose=poses.last();
  float x=layout.width/2F-layout.pivotX(),z=layout.depth/2F-layout.pivotZ();
  float y=(tile.inverted()?1-layout.height:0)-.0005F;
  portVertex(output,pose,x-.375F,y,z-.375F,sprite.getU0(),sprite.getV0(),light,overlay);
  portVertex(output,pose,x+.375F,y,z-.375F,sprite.getU1(),sprite.getV0(),light,overlay);
  portVertex(output,pose,x+.375F,y,z+.375F,sprite.getU1(),sprite.getV1(),light,overlay);
  portVertex(output,pose,x-.375F,y,z+.375F,sprite.getU0(),sprite.getV1(),light,overlay);
 }
 private static void portVertex(com.mojang.blaze3d.vertex.VertexConsumer target,PoseStack.Pose pose,float x,float y,float z,float u,float v,int light,int overlay){
  target.addVertex(pose,x,y,z).setColor(-1).setUv(u,v).setOverlay(overlay).setLight(light).setNormal(pose,0,-1,0);
 }
 @Override public int getViewDistance(){return 96;}@Override public AABB getRenderBoundingBox(NativeObserverEntity t){var d=t.design();return new AABB(t.cell(0,0,0)).minmax(new AABB(t.cell(d.width()-1,d.height()-1,d.depth()-1))).inflate(.75);}
}
