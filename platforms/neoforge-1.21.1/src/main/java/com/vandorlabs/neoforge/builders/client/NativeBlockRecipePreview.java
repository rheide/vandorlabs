package com.vandorlabs.neoforge.builders.client;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.builders.NativeBlockRecipeData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

/** Bounded immutable native item quads; no tile construction or world/chunk observation. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeBlockRecipePreview {
 private record Point(float x,float y,float z,float u,float v,int color,float nx,float ny,float nz){Point mix(Point b,float t){return new Point(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,u+(b.u-u)*t,v+(b.v-v)*t,color,nx,ny,nz);}}
 private record Mesh(ItemStack stack,List<Point[]> faces,float minY,float maxY,long bytes){}
 private static final Point[] CLIPPED=new Point[6];
 private static final LinkedHashMap<Integer,List<Mesh>> CACHE=new LinkedHashMap<>(32,.75f,true);private static long bytes,builds;private static Object world;private static final long BUDGET=4*1024*1024;
 private static Mesh mesh(ItemStack input){var mc=Minecraft.getInstance();if(world!=mc.level){clear();world=mc.level;}int hash=ItemStack.hashItemAndComponents(input);var bucket=CACHE.get(hash);if(bucket!=null)for(var mesh:bucket)if(ItemStack.isSameItemSameComponents(mesh.stack,input))return mesh;if(!NativeBlockRecipeData.eligible(input))return null;
  var stack=input.copyWithCount(1);var model=mc.getItemRenderer().getModel(stack,mc.level,null,0);var faces=new ArrayList<Point[]>();float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
  for(var pass:model.getRenderPasses(stack,false))for(int side=-1;side<6;side++){var random=RandomSource.create(42);var direction=side<0?null:Direction.from3DDataValue(side);for(var quad:pass.getQuads(null,direction,random)){int[] raw=quad.getVertices();int stride=raw.length/4;if(stride<8||raw.length%4!=0)throw new IllegalArgumentException("Recipe preview vertex format");var face=new Point[4];int tint=quad.isTinted()?mc.getItemColors().getColor(stack,quad.getTintIndex()):-1;for(int i=0;i<4;i++){int at=i*stride;float x=Float.intBitsToFloat(raw[at]),y=Float.intBitsToFloat(raw[at+1]),z=Float.intBitsToFloat(raw[at+2]);if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Recipe preview nonfinite vertex");int c=raw[at+3],color=(c&0xff000000)|((c&255)*(tint>>16&255)/255)<<16|((c>>8&255)*(tint>>8&255)/255)<<8|(c>>16&255)*(tint&255)/255;var n=quad.getDirection();face[i]=new Point(x,y,z,Float.intBitsToFloat(raw[at+4]),Float.intBitsToFloat(raw[at+5]),color,n.getStepX(),n.getStepY(),n.getStepZ());minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}faces.add(face);if(faces.size()>32768)throw new IllegalArgumentException("Recipe preview face bound");}}
  if(faces.isEmpty())return null;float scale=1/Math.max(1,Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ))),cx=(maxX+minX)/2,cy=(maxY+minY)/2,cz=(maxZ+minZ)/2;for(var face:faces)for(int i=0;i<4;i++){var p=face[i];face[i]=new Point((p.x-cx)*scale+.5f,(p.y-cy)*scale+.5f,(p.z-cz)*scale+.5f,p.u,p.v,p.color,p.nx,p.ny,p.nz);}
  var built=new Mesh(stack,List.copyOf(faces),(minY-cy)*scale+.5f,(maxY-cy)*scale+.5f,1024L+faces.size()*256L);builds++;if(built.bytes<=BUDGET){CACHE.computeIfAbsent(hash,k->new ArrayList<>()).add(built);bytes+=built.bytes;while(bytes>BUDGET){var iterator=CACHE.values().iterator();for(var retired:iterator.next())bytes-=retired.bytes;iterator.remove();}}return built;
 }
 /** Unit cube centered at (.5,.5,.5); fraction clips from its bottom toward its top. */
 public static void render(ItemStack stack,PoseStack poses,MultiBufferSource buffers,int light,int overlay,float fraction){if(stack.isEmpty()||!Float.isFinite(fraction)||fraction<=0)return;var mesh=mesh(stack);if(mesh==null)return;var target=buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));float top=mesh.minY+(mesh.maxY-mesh.minY)*Math.min(1,fraction);for(var face:mesh.faces){if(fraction>=1){emit(face[0],face[1],face[2],face[3],poses,target,light,overlay);continue;}Point[] clipped=CLIPPED;int count=0;Point previous=face[3];boolean previousIn=previous.y<=top;for(var p:face){boolean in=p.y<=top;if(in!=previousIn)clipped[count++]=previous.mix(p,(top-previous.y)/(p.y-previous.y));if(in)clipped[count++]=p;previous=p;previousIn=in;}if(count<3)continue;if(count==4)emit(clipped[0],clipped[1],clipped[2],clipped[3],poses,target,light,overlay);else for(int i=1;i+1<count;i++)emit(clipped[0],clipped[i],clipped[i+1],clipped[i+1],poses,target,light,overlay);}}
 private static void emit(Point a,Point b,Point c,Point d,PoseStack poses,VertexConsumer target,int light,int overlay){point(a,poses,target,light,overlay);point(b,poses,target,light,overlay);point(c,poses,target,light,overlay);point(d,poses,target,light,overlay);}
 private static void point(Point p,PoseStack poses,VertexConsumer target,int light,int overlay){target.addVertex(poses.last().pose(),p.x,p.y,p.z).setColor(p.color).setUv(p.u,p.v).setOverlay(overlay).setLight(light).setNormal(poses.last(),p.nx,p.ny,p.nz);}
 public static float upperY(ItemStack stack,float fraction){var mesh=mesh(stack);return mesh==null?.5f:mesh.minY+(mesh.maxY-mesh.minY)*Math.clamp(fraction,0,1);}
 public static long builds(){return builds;}public static long retainedBytes(){return bytes;}public static void clear(){CACHE.clear();bytes=0;world=null;Arrays.fill(CLIPPED,null);}
 @SubscribeEvent public static void reload(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event){event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->{clear();com.vandorlabs.neoforge.NativeScreenCatalogRenderer.clearAll();});}
 @EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)public static final class Lifecycle{@SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){clear();com.vandorlabs.neoforge.NativeScreenCatalogRenderer.clearAll();}}
 private NativeBlockRecipePreview(){}
}
