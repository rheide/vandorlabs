package com.vandorlabs.neoforge.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vandorlabs.animation.CompactAnimationDecoder;
import com.vandorlabs.animation.ScreenBehavior;
import com.vandorlabs.animation.VisualClock;
import com.vandorlabs.render.ScreenHousingMesh;
import com.vandorlabs.render.ScreenSurface;
import com.mojang.math.Axis;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Canonical decoded frames and shared pause-aware clock; no server-time animation resets. */
public final class ScreenRenderer implements BlockEntityRenderer<ProgrammableScreenEntity> {
    private record Animation(ResourceLocation texture,int frames){}
    private static final Map<String,Animation> CACHE=new HashMap<>();
    private static final VisualClock CLOCK=new VisualClock();
    private static double visualTicks;
    private static final ResourceLocation BLACK=ResourceLocation.withDefaultNamespace("textures/block/black_concrete.png");
    private static ResourceLocation texture(String atlasId){ResourceLocation id=ResourceLocation.parse(atlasId);return id.withPath("textures/"+id.getPath()+".png");}
    private static ResourceLocation resource(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
    public static void tickClock(){visualTicks=CLOCK.sample(System.nanoTime(),Minecraft.getInstance().isPaused());}
    public static void clearResources(){for(Animation animation:CACHE.values())if(animation.texture.getPath().startsWith("dynamic/"))Minecraft.getInstance().getTextureManager().release(animation.texture);CACHE.clear();}
    static int framesForChecks(String id){return animation(id).frames;}
    private static Animation animation(String id){
        return CACHE.computeIfAbsent(id,key->{
            var manager=Minecraft.getInstance().getResourceManager();ResourceLocation base=resource("textures/screens/"+key+"_static.png");
            var encoded=manager.getResource(resource("textures/screens/"+key+"_animated.anim"));
            if(encoded.isEmpty())return new Animation(base,1);
            try(var baseStream=manager.open(base);var packed=encoded.get().open()){
                BufferedImage original=ImageIO.read(baseStream);var decoded=CompactAnimationDecoder.decode(original,packed);
                ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(decoded.strip,"PNG",png);
                NativeImage pixels=NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
                ResourceLocation texture=Minecraft.getInstance().getTextureManager().register("vandorlabs_screen/"+key,new DynamicTexture(pixels));
                return new Animation(texture,decoded.frameCount);
            }catch(java.io.IOException error){throw new IllegalStateException("Cannot decode canonical screen animation "+key,error);}
        });
    }
    @Override public void render(ProgrammableScreenEntity screen,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!ModernFinishCatalog.validChoice(screen.data().housingTexture))return;
        var housing=ModernFinishCatalog.entry(screen.data().housingTexture);
        ResourceLocation housingTexture=texture(housing.texture());
        Direction facing=screen.getBlockState().getValue(ProgrammableScreenBlock.FACING);
        pose.pushPose();pose.translate(.5,.5,.5);
        if(facing==Direction.UP){pose.mulPose(Axis.YP.rotationDegrees(180));pose.mulPose(Axis.XP.rotationDegrees(90));}
        else if(facing==Direction.DOWN)pose.mulPose(Axis.XP.rotationDegrees(-90));
        else pose.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));
        pose.translate(-.5,-.5,-.5);
        ScreenHousingMesh mesh=ScreenHousingMesh.cube().textureLayout(screen.sides(),housing.u()/housing.v());
        VertexConsumer walls=buffers.getBuffer(RenderType.entityCutout(housingTexture));
        for(ScreenHousingMesh.Face face:mesh.quads)for(int i=0;i<4;i++){
            ScreenHousingMesh.Vertex vertex=face.vertices[(4-i)&3];
            walls.addVertex(pose.last(),(float)vertex.x/16,(float)vertex.y/16,(float)vertex.z/16).setColor(255,255,255,255).setUv((float)vertex.u/16*housing.u(),(float)vertex.v/16*housing.v()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),face.nx,face.ny,face.nz);
        }
        if(screen.redstone().enabled()){RedstoneOverlayRenderer.draw(pose,buffers,screen.redstone());pose.popPose();return;}
        ResourceLocation texture;float v0=0,v1=1,u1=1;
        int mode=screen.effectiveMode();
        if(mode==ScreenBehavior.OFF)texture=BLACK;
        else if(screen.surface()>=0&&ModernFinishCatalog.validChoice(screen.surface())){var material=ModernFinishCatalog.entry(screen.surface());texture=texture(material.texture());u1=material.u();v1=material.v();}
        else {
            String id=ScreenArtwork.find(screen.data().selectedScreen).texture(screen.data().framed);
            if(mode==ScreenBehavior.STATIC)texture=resource("textures/screens/"+id+"_static.png");
            else {Animation animation=animation(id);texture=animation.texture;
                int frame=(int)(visualTicks/ScreenBehavior.animationTicks(screen.data().animationSpeedIndex))%animation.frames;
                v0=(float)frame/animation.frames;v1=(float)(frame+1)/animation.frames;
            }
        }
        ScreenSurface.Quad surface=ScreenSurface.quad(ScreenSurface.Kind.FLAT,false);
        VertexConsumer front=buffers.getBuffer(RenderType.entityCutout(texture));
        for(int i=0;i<4;i++){
            ScreenSurface.Vertex vertex=surface.vertices[i];
            front.addVertex(pose.last(),(float)vertex.x/16,(float)vertex.y/16,(float)vertex.z/16).setColor(255,255,255,255).setUv(i==0||i==3?u1:0,i<2?v0:v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose.last(),surface.nx,surface.ny,surface.nz);
        }
        pose.popPose();
    }
}
