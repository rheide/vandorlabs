package com.vandorlabs.neoforge.builders;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.builders.client.*;
import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.joml.*;

/** Test-only observation of the actual world dispatcher; never substitutes a title/GUI render. */
@EventBusSubscriber(modid="vandorlabs",value=Dist.CLIENT)
public final class NativeBuilderWorldChecks {
 private static boolean armed,done;private static long calls,vertices,coreVertices,laserVertices;private static String failure="";
 public static void arm(){armed=true;done=false;calls=vertices=coreVertices=laserVertices=0;failure="";}
 public static boolean done(){if(!failure.isEmpty())throw new AssertionError(failure);return done;}
 public static String status(){return "worldCalls="+calls+" vertices="+vertices+" core="+coreVertices+" lasers="+laserVertices;}
 @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
 public static final class Registration {
  @SubscribeEvent(priority=EventPriority.LOWEST)public static void renderers(EntityRenderersEvent.RegisterRenderers e){if(NativeBuilderClientFixture.enabled())e.registerBlockEntityRenderer(NativeBuilderBindings.ENTITY,context->new Observed(new NativeBuilderRenderer(context)));}
 }
 private static final class Observed implements BlockEntityRenderer<NativeBuilderEntity>{
  private final NativeBuilderRenderer delegate;Observed(NativeBuilderRenderer delegate){this.delegate=delegate;}
  public void render(NativeBuilderEntity tile,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
   if(!armed||!tile.getBlockPos().equals(NativeBuilderClientFixture.ROOT)){delegate.render(tile,partial,poses,buffers,light,overlay);return;}
   long before=vertices;var inverse=new Matrix4f(poses.last().pose()).invert();
   MultiBufferSource observed=layer->{var original=buffers.getBuffer(layer);boolean target=layer==NativeBuilderModelCache.LAYER;var position=new Vector3f();int[] color={-1};
    return (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
     if(target&&method.getName().equals("addVertex")&&args!=null&&args.length==3&&args[0] instanceof Float){vertices++;inverse.transformPosition((Float)args[0],(Float)args[1],(Float)args[2],position);color[0]=-1;}
     if(target&&method.getName().equals("setColor")&&args!=null&&args.length==1&&args[0] instanceof Integer value){color[0]=value;if(value==0xffff4132)laserVertices++;}
     if(target&&method.getName().equals("setLight")&&args!=null&&args.length==1&&args[0] instanceof Integer value&&value==0xf000f0&&color[0]!=0xffff4132&&position.x>=1-1e-4&&position.x<=2+1e-4&&position.y>=2.125-1e-4&&position.y<=3.125+1e-4&&position.z>=1.125-1e-4&&position.z<=2.125+1e-4)coreVertices++;
     try{var result=method.invoke(original,args);return result==original?proxy:result;}catch(InvocationTargetException error){throw error.getCause();}
    });
   };
   delegate.render(tile,partial,poses,observed,light,overlay);
   if(tile.formed()&&tile.processing()&&vertices-before>=NativeBuilderModelCache.mesh(tile.design().id(),false).vertexCount())calls++;
  }
  public int getViewDistance(){return delegate.getViewDistance();}public AABB getRenderBoundingBox(NativeBuilderEntity tile){return delegate.getRenderBoundingBox(tile);}
 }
 @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(!armed||calls<2||coreVertices==0||laserVertices<32)return;var mc=Minecraft.getInstance();if(mc.screen!=null)return;
  try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("builder-world-processing.png"));System.out.println("[vandorlabs][builder-client] world-render PASS "+status()+" actualDispatcher/body/partialCore/nearbyLasers");armed=false;done=true;}catch(Exception error){failure="World capture failed: "+error;armed=false;}
 }
 private NativeBuilderWorldChecks(){}
}
