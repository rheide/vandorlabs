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

    private static ResourceLocation texture(String atlasId){ResourceLocation id=ResourceLocation.parse(atlasId);return id.withPath("textures/"+id.getPath()+".png");}
    private static ResourceLocation resource(String path){return ResourceLocation.fromNamespaceAndPath("vandorlabs",path);}
    public static void tickClock(){visualTicks=CLOCK.sample(System.nanoTime(),Minecraft.getInstance().isPaused());}
    public static void clearResources(){for(Animation animation:CACHE.values())if(animation.texture.getPath().startsWith("dynamic/"))Minecraft.getInstance().getTextureManager().release(animation.texture);CACHE.clear();ScreenRenderLayers.clear();}
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
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(ProgrammableScreenEntity screen){
        var pos=screen.getBlockPos();return screen.ceilingMounted()?new net.minecraft.world.phys.AABB(pos.getX()-1,pos.getY(),pos.getZ()-1,pos.getX()+2,pos.getY()+1,pos.getZ()+2):new net.minecraft.world.phys.AABB(pos);
    }
    @Override public void render(ProgrammableScreenEntity screen,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var housing=ModernFinishCatalog.entry(screen.data().housingTexture);
        Direction facing=screen.facing();var family=screen.family();
        pose.pushPose();pose.translate(.5,.5,.5);
        boolean fullRotation=family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.FLAT||family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.CONSOLE||family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.DIAGONAL;
        if(fullRotation&&facing==Direction.UP){pose.mulPose(Axis.YP.rotationDegrees(180));pose.mulPose(Axis.XP.rotationDegrees(90));}
        else if(fullRotation&&facing==Direction.DOWN)pose.mulPose(Axis.XP.rotationDegrees(-90));
        else pose.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));
        pose.translate(-.5,-.5,-.5);
        if(family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.HALF_CONSOLE&&screen.ceilingMounted())pose.translate(0,0,com.vandorlabs.render.InputSurfaceLayout.ceilingStart(16,screen.ceilingPosition())/16);
        switch(family){
            case FLAT->housing(pose,buffers,screen,housing,ScreenHousingMesh.cube(),light,false);
            case CONSOLE,HALF_CONSOLE->{housing(pose,buffers,screen,housing,ScreenHousingMesh.box(0,0,0,16,1,16),light,false);housing(pose,buffers,screen,housing,family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.CONSOLE?ScreenHousingMesh.console():ScreenHousingMesh.halfConsole(),light,false);}
            case DIAGONAL->housing(pose,buffers,screen,housing,ScreenHousingMesh.diagonal(screen.geometryInverted()),light,false);
            case FULL_INPUT,HALF_INPUT->{var mounted=com.vandorlabs.render.ScreenFamilyPolicy.input(family,screen.keyboard(),screen.upper(),screen.ceilingMounted(),screen.wallPosition(),screen.data().smallInput,screen.ceilingPosition());var box=mounted.housing;housing(pose,buffers,screen,housing,ScreenHousingMesh.box(box.x0,box.y0,box.z0,box.x1,box.y1,box.z1),light,false);}
            case DIAGONAL_HALF->{pose.pushPose();pose.translate(0,0,.5);pose.scale(1,.5f,.5f);housing(pose,buffers,screen,housing,ScreenHousingMesh.diagonal(false),light,screen.upper());pose.popPose();}
        }
        for(int slot=0;slot<2;slot++)if(screen.supportsSlot(slot)){
            ScreenSurface.Quad surface=com.vandorlabs.render.ScreenFamilyPolicy.surface(family,slot,screen.keyboard(),screen.upper(),screen.geometryInverted(),screen.ceilingMounted(),screen.wallPosition(),screen.data().smallInput,screen.ceilingPosition());
            if(screen.redstone(slot).enabled()){RedstoneOverlayRenderer.draw(pose,buffers,screen.redstone(slot),surface,com.vandorlabs.render.ScreenFamilyPolicy.half(family,slot));continue;}
            // Vehicle Status retains its saved identity until the vehicle adapter is installed.
            Frame frame=frame(screen,slot,light);var front=buffers.getBuffer(family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.DIAGONAL||family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.DIAGONAL_HALF?ScreenRenderLayers.diagonal(frame.texture):RenderType.entityCutoutNoCull(frame.texture));
            boolean normalU=family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.FULL_INPUT||family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.HALF_INPUT||family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.HALF_CONSOLE||family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.CONSOLE&&slot==1;
            boolean invertV=family==com.vandorlabs.render.ScreenFamilyPolicy.Kind.DIAGONAL_HALF&&screen.upper();
            for(int i=0;i<4;i++){var vertex=surface.vertices[i];boolean left=i==0||i==3,top=i<2;
                front.addVertex(pose.last(),(float)vertex.x/16,(float)vertex.y/16,(float)vertex.z/16).setColor(255,255,255,255).setUv(left==normalU?0:frame.u1,top!=invertV?frame.v0:frame.v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(frame.light).setNormal(pose.last(),surface.nx,surface.ny,surface.nz);
            }
        }
        pose.popPose();
    }
    private record Frame(ResourceLocation texture,float u1,float v0,float v1,int light){}
    private static Frame frame(ProgrammableScreenEntity screen,int slot,int worldLight){
        if(screen.surface(slot)>=0){var material=ModernFinishCatalog.entry(screen.surface(slot));return new Frame(texture(material.texture()),material.u(),0,material.v(),worldLight);}
        int mode=screen.effectiveMode();boolean input=screen.inputSurface(slot);String id=input?screen.inputArtwork(slot):ScreenArtwork.active(screen.data().selectedScreen,screen.data().framed);String key=(input?"input/":"")+id;
        if(mode==ScreenBehavior.OFF)return new Frame(resource("textures/screens/"+(input?key+"_off":ScreenArtwork.activeHasFrame(screen.data().selectedScreen,screen.data().framed)?"sequence_border_off":"screen_off")+".png"),1,0,1,worldLight);
        if(mode==ScreenBehavior.STATIC||input&&InputArtwork.frames(id)==1)return new Frame(resource("textures/screens/"+key+"_static.png"),1,0,1,input?worldLight:15728880);
        Animation animation=animation(key);int frame=(int)(visualTicks/ScreenBehavior.animationTicks(screen.data().animationSpeedIndex))%animation.frames;
        return new Frame(animation.texture,1,(float)frame/animation.frames,(float)(frame+1)/animation.frames,input?worldLight:15728880);
    }
    private static void housing(PoseStack pose,MultiBufferSource buffers,ProgrammableScreenEntity screen,ModernFinishCatalog.Entry material,ScreenHousingMesh source,int light,boolean ceiling){
        ScreenHousingMesh mesh=source.textureLayout(screen.sides(),material.u()/material.v());var out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(material.texture())));
        faces(pose,out,mesh.quads,material,light,ceiling);faces(pose,out,mesh.triangles,material,light,ceiling);
    }
    private static void faces(PoseStack pose,VertexConsumer out,ScreenHousingMesh.Face[] faces,ModernFinishCatalog.Entry material,int light,boolean ceiling){
        for(var face:faces)for(int i=0;i<4;i++){int count=face.vertices.length,j=i<count?i:count-1;var vertex=face.vertices[ceiling?j:(count-j)%count];
            out.addVertex(pose.last(),(float)vertex.x/16,(float)(ceiling?32-vertex.y:vertex.y)/16,(float)vertex.z/16).setColor(255,255,255,255).setUv((float)vertex.u/16*material.u(),(float)vertex.v/16*material.v()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),face.nx,ceiling?-face.ny:face.ny,face.nz);
        }
    }
}
