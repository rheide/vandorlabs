package com.vandorlabs.client;

import com.vandorlabs.canopy.CanopyDisplayHit;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.screen.VehicleInstrument;
import com.vandorlabs.tiles.TileEntityCanopy;
import com.vandorlabs.vehicle.VehicleWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Capture the shared vehicle panel outside world rendering and map it onto posed glass. */
@Mod.EventBusSubscriber(modid="vandorlabs",value=Side.CLIENT)
public final class CanopyDisplayRenderer {
    private static final int MAX_DISPLAYS=48,MAX_UPDATES=6,REFRESH_TICKS=3;
    private static final Map<TileEntityCanopy,Display[]> DISPLAYS=new LinkedHashMap<>(32,.75F,true);
    private static long tick;

    private static final class Display {
        final TileEntityCanopy tile;
        final int height;
        Framebuffer frame;
        VehicleInstrument requested,rendered;
        long seen,updated;
        Display(TileEntityCanopy tile,int height){this.tile=tile;this.height=height;}
        void delete(){if(frame!=null)frame.deleteFramebuffer();}
    }

    private CanopyDisplayRenderer() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        tick++;
        World world=Minecraft.getMinecraft().world;
        List<Display> ready=new ArrayList<>();
        Iterator<Map.Entry<TileEntityCanopy,Display[]>> entries=DISPLAYS.entrySet().iterator();
        while(entries.hasNext()){
            Map.Entry<TileEntityCanopy,Display[]> entry=entries.next();
            TileEntityCanopy tile=entry.getKey();
            World parent=tile.getWorld() instanceof VehicleWorld?((VehicleWorld)tile.getWorld()).parent:tile.getWorld();
            if(tile.isInvalid()||world==null||parent!=world){
                dispose(entry.getValue());entries.remove();continue;
            }
            Display[] displays=entry.getValue();
            for(int side=0;side<displays.length;side++){
                Display display=displays[side];
                if(display==null)continue;
                if(tick-display.seen>100){display.delete();displays[side]=null;continue;}
                if(tick-display.seen<=2&&(display.rendered!=display.requested||tick-display.updated>=REFRESH_TICKS))ready.add(display);
            }
            if(displays[0]==null&&displays[1]==null)entries.remove();
        }
        ready.sort(Comparator.comparingLong(display->display.updated));
        for(int i=0;i<Math.min(MAX_UPDATES,ready.size());i++)capture(ready.get(i));
    }

    private static void dispose(Display[] displays){
        for(Display display:displays)if(display!=null)display.delete();
    }

    static boolean draw(TileEntityCanopy tile,List<CanopyMesh.Face> faces){
        if(faces.isEmpty()||!OpenGlHelper.isFramebufferEnabled())return false;
        Display[] displays=DISPLAYS.computeIfAbsent(tile,key->new Display[2]);
        if(DISPLAYS.size()>MAX_DISPLAYS/2){
            Iterator<Display[]> entries=DISPLAYS.values().iterator();
            dispose(entries.next());entries.remove();
        }
        for(CanopyMesh.Face face:faces){
            int side=face.sideGlass?1:0;
            if(displays[side]==null)displays[side]=new Display(tile,CanopyDisplayHit.height(face));
            displays[side].requested=face.display;displays[side].seen=tick;
        }
        boolean drawn=false;
        for(int side=0;side<displays.length;side++){
            Display display=displays[side];
            if(display==null||display.frame==null||display.rendered!=display.requested)continue;
            boolean hud=display.rendered==VehicleInstrument.HUD;
            boolean alphaEnabled=hud&&GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            int alphaFunc=hud?GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC):0;
            float alphaRef=hud?GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF):0;
            if(hud){
                GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
                GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            }
            display.frame.bindFramebufferTexture();
            BufferBuilder buffer=Tessellator.getInstance().getBuffer();
            buffer.setTranslation(0,0,0);
            buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
            for(CanopyMesh.Face face:faces){
                if(face.sideGlass!=(side==1)||face.display!=display.rendered)continue;
                emit(buffer,face,tile);
            }
            Tessellator.getInstance().draw();
            if(hud){
                GlStateManager.disableBlend();
                GlStateManager.alphaFunc(alphaFunc,alphaRef);
                if(!alphaEnabled)GlStateManager.disableAlpha();
            }
            drawn=true;
        }
        return drawn;
    }

    static boolean captured(TileEntityCanopy tile,VehicleInstrument instrument,boolean side){
        Display[] displays=DISPLAYS.get(tile);
        return displays!=null&&displays[side?1:0]!=null&&displays[side?1:0].rendered==instrument;
    }

    private static void capture(Display display){
        TileEntityCanopy tile=display.tile;
        int height=display.height;
        int target=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();GlStateManager.ortho(0,128,height,0,-10,10);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            if(program!=0)GL20.glUseProgram(0);
            if(display.frame==null){
                display.frame=new Framebuffer(256,height*2,true);
                display.frame.setFramebufferFilter(GL11.GL_LINEAR);
            }
            display.frame.bindFramebuffer(true);
            GlStateManager.loadIdentity();GlStateManager.clearColor(0,0,0,display.requested==VehicleInstrument.HUD?0:1);
            GlStateManager.depthMask(true);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.enableDepth();
            if(display.requested==VehicleInstrument.HUD&&tile.resizable()&&height==128)
                VehicleInstrumentRenderer.draw(VehicleInstrument.HUD,tile.getWorld(),tile.getPos(),height,true);
            else RedstoneScreenRenderer.drawVehicleSelection(display.requested,tile.getWorld(),tile.getPos(),height);
            display.rendered=display.requested;display.updated=tick;
        }finally{
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,target);
            if(program!=0)GL20.glUseProgram(program);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        }
    }

    private static void emit(BufferBuilder buffer,CanopyMesh.Face face,TileEntityCanopy tile){
        for(int i=1;i<face.vertices.length-1;i++){
            Vec3d a=CanopyMesh.rotate(face.vertices[0],tile.facing());
            Vec3d b=CanopyMesh.rotate(face.vertices[i],tile.facing());
            Vec3d c=CanopyMesh.rotate(face.vertices[i+1],tile.facing());
            Vec3d normal=b.subtract(a).crossProduct(c.subtract(a));
            if(normal.lengthSquared()<1e-16)continue;
            normal=normal.normalize();
            boolean flip=CanopyDisplayHit.mirrored(face,i,i+1);
            vertex(buffer,a,normal,face.uv[0],flip,1);
            vertex(buffer,b,normal,face.uv[i],flip,1);
            vertex(buffer,c,normal,face.uv[i+1],flip,1);
            vertex(buffer,c,normal,face.uv[i+1],flip,1);
            if(face.doubleSided){
                vertex(buffer,c,normal,face.uv[i+1],!flip,-1);
                vertex(buffer,b,normal,face.uv[i],!flip,-1);
                vertex(buffer,a,normal,face.uv[0],!flip,-1);
                vertex(buffer,a,normal,face.uv[0],!flip,-1);
            }
        }
    }

    private static void vertex(BufferBuilder buffer,Vec3d point,Vec3d normal,double[] uv,boolean flip,int sign){
        buffer.pos(point.x,point.y,point.z).color(255,255,255,255).tex(flip?1-uv[0]:uv[0],uv[1]).lightmap(240,240)
                .normal((float)normal.x*sign,(float)normal.y*sign,(float)normal.z*sign).endVertex();
    }
}
