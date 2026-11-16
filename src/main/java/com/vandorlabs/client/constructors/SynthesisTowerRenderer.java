package com.vandorlabs.client.constructors;

import com.vandorlabs.blocks.*;
import com.vandorlabs.machines.SynthesisPresentation;
import com.vandorlabs.rack.EmbeddedRackMotion;
import com.vandorlabs.rack.ModuleBatteryData;
import java.util.*;
import com.vandorlabs.client.TurntableRenderState;
import com.vandorlabs.tiles.TileEntityConstructor;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

/** Small native attachments remain visible; costly fabrication effects stop outside 24 blocks. */
public final class SynthesisTowerRenderer {
    private static final String[] DESIGNS={"synthesis_tower","assembly_press","enclosed_gantry"};
    private static final java.nio.DoubleBuffer SCREEN_CLIP=org.lwjgl.BufferUtils.createDoubleBuffer(4);
    private static final java.nio.DoubleBuffer CLIP=org.lwjgl.BufferUtils.createDoubleBuffer(4);
    private static final Map<net.minecraft.world.World,LinkedHashMap<net.minecraft.util.math.BlockPos,Rack>> RACKS=new WeakHashMap<>();
    private static final class Rack {final java.util.UUID identity;final EmbeddedRackMotion motion=new EmbeddedRackMotion();Rack(java.util.UUID identity){this.identity=identity;}}
    public static void clear(net.minecraft.world.World world){RACKS.remove(world);}
    public static void clear(){RACKS.clear();}
    public static void observe(TileEntityConstructor tile){if(tile.getWorld()!=null&&tile.synthesis())rack(tile,MachineVisualMotion.now());}
    private static EmbeddedRackMotion rack(TileEntityConstructor tile,double now){
        LinkedHashMap<net.minecraft.util.math.BlockPos,Rack> values=RACKS.get(tile.getWorld());if(values==null){values=new LinkedHashMap<>(16,.75F,true);RACKS.put(tile.getWorld(),values);}
        Rack rack=values.get(tile.getPos());if(rack==null||!rack.identity.equals(tile.renderIdentity())){rack=new Rack(tile.renderIdentity());values.put(tile.getPos().toImmutable(),rack);if(values.size()>256)values.remove(values.keySet().iterator().next());}
        int mask=0;for(int bay=0;bay<4;bay++)if(ModuleBatteryData.isBattery(tile.batteryStack(bay)))mask|=1<<bay;
        rack.motion.observe(mask,tile.batterySequence(0),tile.batterySequence(1),tile.batterySequence(2),tile.batterySequence(3),com.vandorlabs.rack.BatteryChargeBar.pixels(tile.batteryEnergy(0),10000),com.vandorlabs.rack.BatteryChargeBar.pixels(tile.batteryEnergy(1),10000),com.vandorlabs.rack.BatteryChargeBar.pixels(tile.batteryEnergy(2),10000),com.vandorlabs.rack.BatteryChargeBar.pixels(tile.batteryEnergy(3),10000),now);return rack.motion;
    }
    public static final double CORE_X=1,CORE_Y=SynthesisPresentation.CORE_Y,CORE_Z=1.125;
    public static void render(TileEntityConstructor tile,double x,double y,double z,float partial){
        if(!tile.synthesis())return;
        Minecraft mc=Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            GlStateManager.translate(x-tile.pivot().getX(),y-tile.pivot().getY(),z-tile.pivot().getZ());
            switch(tile.facing()){case EAST:GlStateManager.translate(3,0,0);GlStateManager.rotate(-90,0,1,0);break;case SOUTH:GlStateManager.translate(3,0,3);GlStateManager.rotate(180,0,1,0);break;case WEST:GlStateManager.translate(0,0,3);GlStateManager.rotate(-270,0,1,0);break;default:}
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            ports(tile.synthesisConnections());
            double now=MachineVisualMotion.now();EmbeddedRackMotion motion=rack(tile,now);
            BufferBuilder rackBuffer=Tessellator.getInstance().getBuffer();rackBuffer.setTranslation(0,0,0);rackBuffer.begin(7,DefaultVertexFormats.BLOCK);
            for(int bay=0;bay<4;bay++)com.vandorlabs.client.ModuleRackModel.emitEmbedded(rackBuffer,EnumFacing.NORTH,bay,motion.level(bay),motion.occupied(bay),motion.visible(bay,now),motion.moving(bay,now),SynthesisPresentation.RACK_X,SynthesisPresentation.RACK_Y,SynthesisPresentation.RACK_Z,motion.offset(bay,now),15728880);
            Tessellator.getInstance().draw();
            if(mc.getRenderViewEntity()==null||mc.getRenderViewEntity().getDistanceSq(tile.getPos())>24*24)return;
            boolean processing=tile.processing();ItemStack output=tile.synthesisInventory().getStackInSlot(1);
            if(processing||!output.isEmpty()){
                double progress=processing?tile.buildProgress():1;
                IBlockState state=processing?Block.getBlockFromName("vandorlabs:"+DESIGNS[tile.buildDesign()]+"_core").getDefaultState():Block.getBlockFromItem(output.getItem()).getDefaultState();
                core(state,progress);
                if(processing){if(tile.working())lasers(progress,now);screen(DESIGNS[tile.buildDesign()],now);}
            }
        }finally{Tessellator.getInstance().getBuffer().setTranslation(0,0,0);GlStateManager.popMatrix();}
    }
    private static void core(IBlockState state,double amount){
        if(!(state.getBlock() instanceof com.vandorlabs.blocks.BlockConstructorCore))return;
        String design=((com.vandorlabs.blocks.BlockConstructorCore)state.getBlock()).design;
        GlStateManager.pushMatrix();try{GlStateManager.translate(CORE_X,CORE_Y,CORE_Z);BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(7,DefaultVertexFormats.BLOCK);CoreModels.emit(buffer,design,amount,15728880);Tessellator.getInstance().draw();}finally{GlStateManager.popMatrix();}
    }
    private static void screen(String design,double now){
        GlStateManager.pushMatrix();GL11.glPushAttrib(GL11.GL_TRANSFORM_BIT|GL11.GL_ENABLE_BIT);
        try{
            GlStateManager.translate(SynthesisPresentation.SCREEN_X,SynthesisPresentation.SCREEN_Y,SynthesisPresentation.SCREEN_Z-.01);
            double half=SynthesisPresentation.SCREEN_SIZE/2-.015625;
            screenClip(GL11.GL_CLIP_PLANE0,1,0,half);screenClip(GL11.GL_CLIP_PLANE1,-1,0,half);
            screenClip(GL11.GL_CLIP_PLANE2,0,1,half);screenClip(GL11.GL_CLIP_PLANE3,0,-1,half);
            FrameMachineDesign spec=FrameMachineDesign.get(design);double scale=SynthesisPresentation.screenPreviewScale(spec.width,spec.height,spec.depth);
            GlStateManager.scale(scale,scale,.002);SynthesisDesignPreview.render(design,(float)(now*1.4));
        }finally{GL11.glPopAttrib();GlStateManager.popMatrix();}
    }
    private static void screenClip(int plane,double x,double y,double edge){SCREEN_CLIP.clear();SCREEN_CLIP.put(x).put(y).put(0).put(edge).flip();GL11.glClipPlane(plane,SCREEN_CLIP);GL11.glEnable(plane);}
    private static void lasers(double progress,double now){
        // Atlas-backed, fullbright quads also work with shader/extended vertex paths.
        // GL_LINES with no UV/lightmap records is not reliable in those pipelines.
        boolean blended=GL11.glIsEnabled(GL11.GL_BLEND),lit=GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int sourceRgb=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),targetRgb=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int sourceAlpha=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),targetAlpha=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);GlStateManager.depthMask(false);
        try{
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.disableLighting();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite white=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/concrete_white");
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
        double surface=CORE_Y+com.vandorlabs.geometry.CoreMesh.height(progress)+.008;
        for(int i=0;i<4;i++){
            double sweep=.5+.46*Math.sin(now*.12+i*Math.PI/2),cross=.5+.46*Math.sin(now*.075+i*Math.PI);
            double sx=i%2==0?.70:2.30,sz=i<2?.95:2.30,ex=CORE_X+sweep,ez=CORE_Z+cross,r=.014;
            int color=com.vandorlabs.machines.SynthesisLaserStyle.color(i,now);
            beamVertex(b,white,sx-r,4.48,sz,color);beamVertex(b,white,sx+r,4.48,sz,color);beamVertex(b,white,ex+r,surface,ez,color);beamVertex(b,white,ex-r,surface,ez,color);
            beamVertex(b,white,sx,4.48,sz-r,color);beamVertex(b,white,sx,4.48,sz+r,color);beamVertex(b,white,ex,surface,ez+r,color);beamVertex(b,white,ex,surface,ez-r,color);
        }
        Tessellator.getInstance().draw();
        }finally{
            GlStateManager.depthMask(depthWrite);GlStateManager.tryBlendFuncSeparate(sourceRgb,targetRgb,sourceAlpha,targetAlpha);
            if(!blended)GlStateManager.disableBlend();if(lit)GlStateManager.enableLighting();
        }
    }
    private static void beamVertex(BufferBuilder b,TextureAtlasSprite white,double x,double y,double z,int color){
        b.pos(x,y,z).color((color>>>16)&255,(color>>>8)&255,color&255,color>>>24).tex(white.getInterpolatedU(8),white.getInterpolatedV(8)).lightmap(240,240).endVertex();
    }
    private static void ports(int connections){
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR);
        // Rear-right bottom RF input; rear-left bottom item input/output.
        rear(b,"power_in_"+((connections&1)!=0?"on":"off"),2,0);bottom(b,"power_in_"+((connections&2)!=0?"on":"off"),2,0);
        rear(b,"matter_in_"+((connections&4)!=0?"on":"off"),0,0);bottom(b,"matter_out_"+((connections&16)!=0?"on":"off"),0,0);
        TextureAtlasSprite s=sprite("matter_out_"+((connections&8)!=0?"on":"off"));vertex(b,s,-.002,0,2,0,1);vertex(b,s,-.002,0,3,1,1);vertex(b,s,-.002,1,3,1,0);vertex(b,s,-.002,1,2,0,0);
        Tessellator.getInstance().draw();
    }
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/resource_ports/"+name);}
    private static void rear(BufferBuilder b,String name,int x,int y){TextureAtlasSprite s=sprite(name);vertex(b,s,x+1,y,3.002,0,1);vertex(b,s,x,y,3.002,1,1);vertex(b,s,x,y+1,3.002,1,0);vertex(b,s,x+1,y+1,3.002,0,0);}
    private static void bottom(BufferBuilder b,String name,int x,int y){TextureAtlasSprite s=sprite(name);vertex(b,s,x,y-.002,2,0,0);vertex(b,s,x+1,y-.002,2,1,0);vertex(b,s,x+1,y-.002,3,1,1);vertex(b,s,x,y-.002,3,0,1);}
    private static void vertex(BufferBuilder b,TextureAtlasSprite s,double x,double y,double z,double u,double v){b.pos(x,y,z).tex(s.getInterpolatedU(u*16),s.getInterpolatedV(v*16)).color(255,255,255,255).endVertex();}
    private SynthesisTowerRenderer(){}
}
