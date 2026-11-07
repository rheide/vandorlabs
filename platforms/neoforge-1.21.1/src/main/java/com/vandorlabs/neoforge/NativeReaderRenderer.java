package com.vandorlabs.neoforge;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.credentials.CredentialCatalog;
import com.vandorlabs.neoforge.credentials.*;
import com.vandorlabs.render.ReaderVisualMesh;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Shared reader frame/contents for standalone holders and half-scale Door security pads. */
public final class NativeReaderRenderer {
 private NativeReaderRenderer(){}
 public static final class Bake {
  private final Map<String,NativeDoorMesh> frames;
  private final NativeDoorMesh[][][] indexed=new NativeDoorMesh[2][6][4];
  public Bake(java.util.function.Function<String,BakedModel> lookup){this(lookup,material->Minecraft.getInstance().getTextureAtlas(material.atlasLocation()).apply(material.texture()));}
  public Bake(java.util.function.Function<String,BakedModel> lookup,java.util.function.Function<Material,TextureAtlasSprite> textures){
   var meshes=new NativeDoorMesh.Bake();var frames=new HashMap<String,NativeDoorMesh>();
   for(String id:NativeReaderAssets.models()){
    var path="/assets/vandorlabs/models/"+id.split(":",2)[1]+".json";
    try(var reader=new InputStreamReader(Objects.requireNonNull(NativeReaderRenderer.class.getResourceAsStream(path),path),StandardCharsets.UTF_8)){
     var original=Objects.requireNonNull(lookup.apply(id),id);boolean canonical=NativeDoorArtwork.canonicalGeometry(id,Minecraft.getInstance().getResourceManager());if(!NativeDoorArtwork.isStatic(original)){original=NativeDoorCanonicalBake.model(id,textures);canonical=true;}var mesh=meshes.prepare(original,canonical?JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements"):null);frames.put(id,Objects.requireNonNull(mesh,id));
    }catch(java.io.IOException failure){throw new IllegalStateException("Reader frame resource "+id,failure);}
   }this.frames=Map.copyOf(frames);for(int kind=0;kind<2;kind++)for(var face:Direction.values())for(int rotation=0;rotation<4;rotation++)indexed[kind][face.get3DDataValue()][rotation]=Objects.requireNonNull(frames.get(NativeReaderAssets.model(kind==1,face.getSerializedName(),rotation)));
  }
  /** The supplied pose already contains any host/half-scale transforms. */
  public void frame(boolean chip,Direction face,int rotation,PoseStack.Pose pose,VertexConsumer target,int light,int overlay,boolean diffuse){
   var mesh=indexed[chip?1:0][face.get3DDataValue()][rotation&3];if(mesh.hasDiffuse(diffuse))mesh.emit(pose,target,Direction.SOUTH,0,light,overlay,0,0,0,0,0,1,0,1,diffuse);
  }
  public long retainedBytes(){return 512+frames.values().stream().mapToLong(NativeDoorMesh::retainedBytes).sum();}
 }
 private static final class Layers extends RenderType {
  private Layers(){super("vandorlabs_reader_color",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,()->{},()->{});}
  static final RenderType COLOR=RenderType.create("vandorlabs_reader_color",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,false,CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(NO_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
 }
 private static final class Emitter implements ReaderVisualMesh.Sink {
  PoseStack.Pose pose;VertexConsumer target;int light,overlay;boolean texture;final org.joml.Vector3f point=new org.joml.Vector3f();
  public void vertex(double x,double y,double z,float u,float v,int rgb){pose.pose().transformPosition((float)x,(float)y,(float)z,point);target.addVertex(point.x,point.y,point.z).setColor(0xff000000|rgb);if(texture)target.setUv(u,v).setLight(light).setOverlay(overlay).setNormal(0,1,0);}
 }
 private static final ThreadLocal<Emitter> EMITTER=ThreadLocal.withInitial(Emitter::new);
 private static final org.joml.Matrix4f[][] ITEMS=new org.joml.Matrix4f[6][4];
 static{for(int f=0;f<6;f++)for(int r=0;r<4;r++){var b=ReaderVisualMesh.basis(f,r);ITEMS[f][r]=new org.joml.Matrix4f((float)-b.ux,(float)-b.uy,(float)-b.uz,0,(float)b.vx,(float)b.vy,(float)b.vz,0,(float)b.nx,(float)b.ny,(float)b.nz,0,(float)(b.ox+b.nx*.06),(float)(b.oy+b.ny*.06),(float)(b.oz+b.nz*.06),1);}}
 private static final ResourceLocation[] LOGOS=new ResourceLocation[CredentialCatalog.COMPANY_COUNT];
 static{for(int i=0;i<LOGOS.length;i++)LOGOS[i]=ResourceLocation.fromNamespaceAndPath("vandorlabs","reader-artwork/blocks/access_reader/logos/"+CredentialCatalog.companyName(i));}
 private static final net.minecraft.world.item.ItemStack[][] ICONS=new net.minecraft.world.item.ItemStack[2][CredentialCatalog.COMPANY_COUNT];
 private static net.minecraft.world.item.ItemStack icon(NativeTokenReader.Visual visual){int kind=visual.chip()?1:0;var stack=ICONS[kind][visual.company()];if(stack==null){stack=NativeTokenReader.icon(visual);ICONS[kind][visual.company()]=stack;}return stack;}
 /** Age and rejection come from a pause-aware owner clock, never a server/world timestamp comparison. */
 public static void contents(NativeTokenReader.Visual visual,NativeTokenReader.Motion motion,float age,boolean rejected,Direction face,int rotation,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
  var basis=ReaderVisualMesh.basis(face.get3DDataValue(),rotation);var out=EMITTER.get();out.pose=poses.last();out.light=light;out.overlay=overlay;
  try{
   if(!rejected&&!visual.occupied()&&visual.logo()>=0){var id=LOGOS[visual.logo()];var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);out.texture=true;out.target=buffers.getBuffer(NativePanelPlaybackRenderer.frameRenderType());ReaderVisualMesh.logo(basis,sprite.getU0(),sprite.getU1(),sprite.getV0(),sprite.getV1(),out);}
   out.texture=false;out.target=buffers.getBuffer(Layers.COLOR);ReaderVisualMesh.overlays(basis,visual.chip(),rejected,visual.output()>0,out);
  }finally{out.pose=null;out.target=null;}
  if(visual.occupied()&&visual.icon()){
   poses.pushPose();try{poses.mulPose(ITEMS[face.get3DDataValue()][rotation&3]);poses.translate(0,ReaderVisualMesh.insertionOffset(age,motion.returning()),0);float scale=visual.chip()?.38F:.5F;poses.scale(scale,scale,1);Minecraft.getInstance().getItemRenderer().renderStatic(icon(visual),ItemDisplayContext.FIXED,light,overlay,poses,buffers,Minecraft.getInstance().level,0);}finally{poses.popPose();}
  }
 }
}
