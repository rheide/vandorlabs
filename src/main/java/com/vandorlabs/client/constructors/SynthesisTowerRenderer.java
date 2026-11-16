package com.vandorlabs.client.constructors;

import com.vandorlabs.blocks.*;
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
    private static final java.nio.DoubleBuffer CLIP=org.lwjgl.BufferUtils.createDoubleBuffer(4);
    public static final double CORE_X=1,CORE_Y=2.125,CORE_Z=1.125;
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
            ports(tile.externalPowerConnected());
            ItemStack battery=tile.synthesisInventory().getStackInSlot(2);
            if(!battery.isEmpty()){
                GlStateManager.pushMatrix();try{
                    GlStateManager.translate(1.03,.20,0);GlStateManager.scale(-.72,.72,.72);
                    BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(7,DefaultVertexFormats.ITEM);
                    int level=Math.min(4,(com.vandorlabs.rack.ModuleBatteryData.energy(battery)*4+9999)/10000);
                    for(net.minecraft.client.renderer.block.model.BakedQuad q:com.vandorlabs.client.ModuleRackModel.batteryQuads(EnumFacing.NORTH,0,level))b.addVertexData(q.getVertexData());
                    Tessellator.getInstance().draw();
                }finally{GlStateManager.popMatrix();}
            }
            if(mc.getRenderViewEntity()==null||mc.getRenderViewEntity().getDistanceSq(tile.getPos())>24*24)return;
            boolean processing=tile.processing();ItemStack output=tile.synthesisInventory().getStackInSlot(1);
            if(processing||!output.isEmpty()){
                double progress=processing?tile.buildProgress():1;
                IBlockState state=processing?Block.getBlockFromName("vandorlabs:"+DESIGNS[tile.buildDesign()]+"_core").getDefaultState():Block.getBlockFromItem(output.getItem()).getDefaultState();
                core(state,progress);
                if(processing){double now=MachineVisualMotion.sample(tile,true,10).now;lasers(progress,now);screen(DESIGNS[tile.buildDesign()],now);}
            }
        }finally{Tessellator.getInstance().getBuffer().setTranslation(0,0,0);GlStateManager.popMatrix();}
    }
    private static void core(IBlockState state,double amount){
        double height=Math.max(.001,Math.min(1,amount));
        GlStateManager.pushMatrix();
        try{
            GlStateManager.translate(CORE_X,CORE_Y,CORE_Z);
            // Vanilla brightness rendering adds a quarter turn around the origin.
            GlStateManager.rotate(-90,0,1,0);
            CLIP.clear();CLIP.put(0).put(-1).put(0).put(height).flip();GL11.glClipPlane(GL11.GL_CLIP_PLANE0,CLIP);GL11.glEnable(GL11.GL_CLIP_PLANE0);
            Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlockBrightness(state,1);
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            // The cut has its own moving top surface; side artwork is clipped, never vertically stretched.
            TextureAtlasSprite sprite=Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state).getParticleTexture();
            BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR);
            vertex(b,sprite,0,height,0,0,0);vertex(b,sprite,0,height,1,0,1);vertex(b,sprite,1,height,1,1,1);vertex(b,sprite,1,height,0,1,0);Tessellator.getInstance().draw();
        }finally{GL11.glDisable(GL11.GL_CLIP_PLANE0);GlStateManager.popMatrix();}
    }
    private static void screen(String design,double now){
        GlStateManager.pushMatrix();try{
            GlStateManager.translate(2.01,1.00,.35);
            FrameMachineDesign spec=FrameMachineDesign.get(design);double scale=1.48/Math.sqrt(spec.width*spec.width+spec.depth*spec.depth+spec.height*spec.height);
            GlStateManager.scale(scale,scale,.002);SynthesisDesignPreview.render(design,(float)(now*1.4));
        }finally{GlStateManager.popMatrix();}
    }
    private static void lasers(double progress,double now){
        // Atlas-backed, fullbright quads also work with shader/extended vertex paths.
        // GL_LINES with no UV/lightmap records is not reliable in those pipelines.
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);GlStateManager.enableTexture2D();GlStateManager.disableLighting();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite white=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/concrete_white");
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
        double surface=CORE_Y+Math.max(.001,Math.min(1,progress))+.008;
        for(int i=0;i<4;i++){
            double sweep=.5+.46*Math.sin(now*.12+i*Math.PI/2),cross=.5+.46*Math.sin(now*.075+i*Math.PI);
            double sx=i%2==0?.70:2.30,sz=i<2?.95:2.30,ex=CORE_X+sweep,ez=CORE_Z+cross,r=.014;
            beamVertex(b,white,sx-r,4.48,sz);beamVertex(b,white,sx+r,4.48,sz);beamVertex(b,white,ex+r,surface,ez);beamVertex(b,white,ex-r,surface,ez);
            beamVertex(b,white,sx,4.48,sz-r);beamVertex(b,white,sx,4.48,sz+r);beamVertex(b,white,ex,surface,ez+r);beamVertex(b,white,ex,surface,ez-r);
        }
        Tessellator.getInstance().draw();
    }
    private static void beamVertex(BufferBuilder b,TextureAtlasSprite white,double x,double y,double z){
        b.pos(x,y,z).color(255,65,50,255).tex(white.getInterpolatedU(8),white.getInterpolatedV(8)).lightmap(240,240).endVertex();
    }
    private static void ports(boolean connected){
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR);
        // Rear-right bottom RF input; rear-left bottom item input/output.
        rear(b,"power_in_"+(connected?"on":"off"),2,0);bottom(b,"power_in_"+(connected?"on":"off"),2,0);
        rear(b,"matter_in_off",0,0);bottom(b,"matter_out_off",0,0);
        TextureAtlasSprite s=sprite("matter_out_off");vertex(b,s,-.002,0,2,0,1);vertex(b,s,-.002,0,3,1,1);vertex(b,s,-.002,1,3,1,0);vertex(b,s,-.002,1,2,0,0);
        Tessellator.getInstance().draw();
    }
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/resource_ports/"+name);}
    private static void rear(BufferBuilder b,String name,int x,int y){TextureAtlasSprite s=sprite(name);vertex(b,s,x+1,y,3.002,0,1);vertex(b,s,x,y,3.002,1,1);vertex(b,s,x,y+1,3.002,1,0);vertex(b,s,x+1,y+1,3.002,0,0);}
    private static void bottom(BufferBuilder b,String name,int x,int y){TextureAtlasSprite s=sprite(name);vertex(b,s,x,y-.002,2,0,0);vertex(b,s,x+1,y-.002,2,1,0);vertex(b,s,x+1,y-.002,3,1,1);vertex(b,s,x,y-.002,3,0,1);}
    private static void vertex(BufferBuilder b,TextureAtlasSprite s,double x,double y,double z,double u,double v){b.pos(x,y,z).tex(s.getInterpolatedU(u*16),s.getInterpolatedV(v*16)).color(255,255,255,255).endVertex();}
    private SynthesisTowerRenderer(){}
}
