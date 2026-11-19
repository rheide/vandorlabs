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
    public static void clear(net.minecraft.world.World world){RACKS.remove(world);BuilderFabricationMotion.clear(world);BuilderFabricationVoxels.clear();BuilderItemGeometry.clear();}
    public static void clear(){RACKS.clear();BuilderRecipePreview.clear();}
    public static void observe(TileEntityConstructor tile){if(tile.getWorld()!=null&&tile.builder())rack(tile,MachineVisualMotion.now());}
    private static EmbeddedRackMotion rack(TileEntityConstructor tile,double now){
        LinkedHashMap<net.minecraft.util.math.BlockPos,Rack> values=RACKS.get(tile.getWorld());if(values==null){values=new LinkedHashMap<>(16,.75F,true);RACKS.put(tile.getWorld(),values);}
        Rack rack=values.get(tile.getPos());if(rack==null||!rack.identity.equals(tile.renderIdentity())){rack=new Rack(tile.renderIdentity());values.put(tile.getPos().toImmutable(),rack);if(values.size()>256)values.remove(values.keySet().iterator().next());}
        int mask=0;for(int bay=0;bay<4;bay++)if(ModuleBatteryData.isBattery(tile.batteryStack(bay)))mask|=1<<bay;
        rack.motion.observe(mask,tile.batterySequence(0),tile.batterySequence(1),tile.batterySequence(2),tile.batterySequence(3),com.vandorlabs.rack.BatteryChargeBar.pixels(tile.batteryEnergy(0),10000),com.vandorlabs.rack.BatteryChargeBar.pixels(tile.batteryEnergy(1),10000),com.vandorlabs.rack.BatteryChargeBar.pixels(tile.batteryEnergy(2),10000),com.vandorlabs.rack.BatteryChargeBar.pixels(tile.batteryEnergy(3),10000),now);return rack.motion;
    }
    public static final double CORE_X=1,CORE_Y=SynthesisPresentation.CORE_Y,CORE_Z=1.125;
    public static void render(TileEntityConstructor tile,double x,double y,double z,float partial){
        if(!tile.builder())return;
        SynthesisPresentation.Spec presentation=SynthesisPresentation.forMachine(tile.builderMachine());
        Minecraft mc=Minecraft.getMinecraft();
        GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            GlStateManager.translate(x-tile.pivot().getX(),y-tile.pivot().getY(),z-tile.pivot().getZ());
            switch(tile.facing()){case EAST:GlStateManager.translate(presentation.depth,0,0);GlStateManager.rotate(-90,0,1,0);break;case SOUTH:GlStateManager.translate(presentation.width,0,presentation.depth);GlStateManager.rotate(180,0,1,0);break;case WEST:GlStateManager.translate(0,0,presentation.width);GlStateManager.rotate(-270,0,1,0);break;default:}
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            ports(tile.synthesisConnections(),presentation);
            double now=MachineVisualMotion.now();EmbeddedRackMotion motion=rack(tile,now);
            BufferBuilder rackBuffer=Tessellator.getInstance().getBuffer();rackBuffer.setTranslation(0,0,0);rackBuffer.begin(7,DefaultVertexFormats.BLOCK);
            for(int bay=0;bay<4;bay++)com.vandorlabs.client.ModuleRackModel.emitEmbedded(rackBuffer,EnumFacing.NORTH,bay,motion.level(bay),motion.occupied(bay),motion.visible(bay,now),motion.moving(bay,now),presentation.rackX,presentation.rackY,presentation.rackZ,motion.offset(bay,now),15728880);
            Tessellator.getInstance().draw();
            if(mc.getRenderViewEntity()==null||mc.getRenderViewEntity().getDistanceSq(tile.getPos())>24*24)return;
            boolean processing=tile.processing();ItemStack output=tile.synthesisInventory().getStackInSlot(1);
            String blueprint=tile.builderRecipe(processing||!output.isEmpty()?tile.buildDesign():tile.selectedDesign());
            if(tile.digitizing()&&(processing||!tile.digitizeBlock().isEmpty())){ItemStack source=tile.digitizeBlock();IBlockState state=com.vandorlabs.machines.BlockRecipeData.state(source);double remaining=processing?1-tile.buildProgress():1;if(state.getBlock() instanceof BlockConstructorCore){core(state,remaining);if(processing&&tile.working())lasers(((BlockConstructorCore)state.getBlock()).design,remaining,now);}else fabrication(tile,source,remaining);}
            else if(tile.synthesis()&&(processing||!output.isEmpty())){
                double progress=processing?tile.buildProgress():1;
                ItemStack result=processing?tile.builderResult(tile.buildDesign()):output;
                IBlockState state=Block.getBlockFromItem(result.getItem()).getDefaultState();
                if(state.getBlock() instanceof BlockConstructorCore){core(state,progress);if(processing&&tile.working())lasers(((BlockConstructorCore)state.getBlock()).design,progress,now);}
                else if(processing){fabrication(tile,result,progress);}
                else if(!result.isEmpty()&&result.getItem() instanceof net.minecraft.item.ItemBlock){
                    IBlockState placed=com.vandorlabs.machines.BlockRecipeData.state(result);
                    if("minecraft".equals(placed.getBlock().getRegistryName().getResourceDomain()))finishedBlock(tile,placed);
                    else fabrication(tile,result,1);
                }
            }
            if(!tile.synthesis()&&(processing||!output.isEmpty()))fabrication(tile,processing?tile.builderResult(tile.buildDesign()):output,processing?tile.buildProgress():1);
            screen(tile,blueprint,now,presentation);
        }finally{Tessellator.getInstance().getBuffer().setTranslation(0,0,0);GlStateManager.popMatrix();}
    }
    private static void finishedBlock(TileEntityConstructor tile,IBlockState state){
        Minecraft mc=Minecraft.getMinecraft();
        BlockRendererDispatcher dispatcher=mc.getBlockRendererDispatcher();
        int tint=mc.getBlockColors().colorMultiplier(state,tile.getWorld(),tile.getPos(),0);
        GlStateManager.pushMatrix();
        try(TurntableRenderState ignored=new TurntableRenderState()){
            GlStateManager.translate(CORE_X,CORE_Y,CORE_Z);
            if(state.getBlock().getBlockLayer()==net.minecraft.util.BlockRenderLayer.TRANSLUCENT){
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            }
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            dispatcher.getBlockModelRenderer().renderModelBrightnessColor(state,dispatcher.getModelForState(state),1,
                    (tint>>16&255)/255F,(tint>>8&255)/255F,(tint&255)/255F);
        }finally{GlStateManager.popMatrix();}
    }
    private static void core(IBlockState state,double amount){
        if(!(state.getBlock() instanceof com.vandorlabs.blocks.BlockConstructorCore))return;
        String design=((com.vandorlabs.blocks.BlockConstructorCore)state.getBlock()).design;
        GlStateManager.pushMatrix();try{GlStateManager.translate(CORE_X,CORE_Y,CORE_Z);BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(7,DefaultVertexFormats.BLOCK);CoreModels.emit(buffer,design,amount,15728880);Tessellator.getInstance().draw();}finally{GlStateManager.popMatrix();}
    }
    private static void fabrication(TileEntityConstructor tile,ItemStack result,double progress){
        if(result.isEmpty())return;boolean flat="assembly_press".equals(tile.builderMachine());
        com.vandorlabs.machines.BuilderOutputBounds bounds=com.vandorlabs.machines.BuilderOutputBounds.forMachine(tile.builderMachine());
        BuilderFabricationVoxels.Model model=BuilderFabricationVoxels.get(result,flat);
        GlStateManager.pushMatrix();try{GlStateManager.translate(bounds.centerX(),bounds.minY,bounds.centerZ());BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(7,DefaultVertexFormats.BLOCK);model.emit(buffer,progress);Tessellator.getInstance().draw();}finally{GlStateManager.popMatrix();}
        if(tile.processing()&&tile.working())fabricationLasers(tile,flat,bounds,model,bounds.minY+model.height(progress)+.008,BuilderFabricationMotion.sample(tile));
    }
    private static void fabricationLasers(TileEntityConstructor tile,boolean press,com.vandorlabs.machines.BuilderOutputBounds bounds,BuilderFabricationVoxels.Model model,double surface,BuilderFabricationMotion.Pose pose){
        try(TurntableRenderState saved=new TurntableRenderState()){
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);GlStateManager.depthMask(false);GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableTexture2D();
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);TextureAtlasSprite white=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/core_flat");BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
            if(tile.synthesis()){for(int i=0;i<4;i++){double sx=i%2==0?.70:2.30,sz=i<2?.95:2.30,ex=bounds.centerX()+.46*Math.sin(pose.work()*.12+i*Math.PI/2),ez=bounds.centerZ()+.46*Math.sin(pose.work()*.075+i*Math.PI);beam(b,white,sx,4.48,sz,ex,surface,ez,.014,com.vandorlabs.machines.SynthesisLaserStyle.color(i,pose.work()));}}
            else if(press){for(int i=0;i<4;i++){double sx=i%2==0?1.125:3.125,sz=i<2?.4375:2.5,ex=bounds.centerX()+(model.minX+model.maxX)/2+(model.maxX-model.minX)*.45*Math.sin(pose.work()*.12+i*Math.PI/2),ez=bounds.centerZ()+(model.minZ+model.maxZ)/2+(model.maxZ-model.minZ)*.45*Math.sin(pose.work()*.075+i*Math.PI);beam(b,white,sx,2.4375,sz,ex,surface,ez,.009,0xb04fee75);}}
            else {double sx=2.5+pose.headX(),sy=3.25,sz=1.5625+pose.armZ(),scan=bounds.centerZ()+(model.minZ+model.maxZ)/2+(model.maxZ-model.minZ)*.45*Math.sin(pose.work()*.11);
                beamVertex(b,white,sx,sy,sz,0x324baeff);beamVertex(b,white,bounds.centerX()+model.minX,surface,scan,0x324baeff);beamVertex(b,white,bounds.centerX()+model.maxX,surface,scan,0x324baeff);beamVertex(b,white,sx,sy,sz,0x324baeff);
                beamVertex(b,white,bounds.centerX()+model.minX,surface,scan-.009,0xc05bbcff);beamVertex(b,white,bounds.centerX()+model.maxX,surface,scan-.009,0xc05bbcff);beamVertex(b,white,bounds.centerX()+model.maxX,surface,scan+.009,0xc05bbcff);beamVertex(b,white,bounds.centerX()+model.minX,surface,scan+.009,0xc05bbcff);
                beam(b,white,sx,sy+.018,sz,sx,sy-.018,sz,.018,0xd06ccaff);
            }Tessellator.getInstance().draw();
        }
    }
    private static void beam(BufferBuilder b,TextureAtlasSprite white,double sx,double sy,double sz,double ex,double ey,double ez,double radius,int color){
        beamVertex(b,white,sx-radius,sy,sz,color);beamVertex(b,white,sx+radius,sy,sz,color);beamVertex(b,white,ex+radius,ey,ez,color);beamVertex(b,white,ex-radius,ey,ez,color);
        beamVertex(b,white,sx,sy,sz-radius,color);beamVertex(b,white,sx,sy,sz+radius,color);beamVertex(b,white,ex,ey,ez+radius,color);beamVertex(b,white,ex,ey,ez-radius,color);
    }
    private static void screen(TileEntityConstructor tile,String blueprint,double now,SynthesisPresentation.Spec presentation){
        GlStateManager.pushMatrix();GL11.glPushAttrib(GL11.GL_TRANSFORM_BIT|GL11.GL_ENABLE_BIT);
        try{
            GlStateManager.translate(presentation.screenX,presentation.screenY,presentation.screenZ-.01);
            double half=presentation.screenSize/2-.015625;
            screenClip(GL11.GL_CLIP_PLANE0,1,0,half);screenClip(GL11.GL_CLIP_PLANE1,-1,0,half);
            screenClip(GL11.GL_CLIP_PLANE2,0,1,half);screenClip(GL11.GL_CLIP_PLANE3,0,-1,half);
            double scale=presentation.screenSize*.875/BuilderRecipePreview.extent(blueprint,tile.getStackInSlot(0));
            GlStateManager.scale(scale,scale,.002);BuilderRecipePreview.render(blueprint,tile.getStackInSlot(0),(float)(now*1.4));
        }finally{GL11.glPopAttrib();GlStateManager.popMatrix();}
    }
    private static void screenClip(int plane,double x,double y,double edge){SCREEN_CLIP.clear();SCREEN_CLIP.put(x).put(y).put(0).put(edge).flip();GL11.glClipPlane(plane,SCREEN_CLIP);GL11.glEnable(plane);}
    private static void lasers(String design,double progress,double now){
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
        double surface=CORE_Y+com.vandorlabs.geometry.CoreMesh.height(design,progress)+.008;
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
    private static void ports(int connections,SynthesisPresentation.Spec presentation){
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.setTranslation(0,0,0);b.begin(7,DefaultVertexFormats.POSITION_TEX_COLOR);
        // Rear-right bottom RF input; rear-left bottom item input/output.
        rear(b,"power_in_"+((connections&1)!=0?"on":"off"),presentation.width-1,0,presentation.depth);bottom(b,"power_in_"+((connections&2)!=0?"on":"off"),presentation.width-1,0,presentation.depth);
        rear(b,"matter_in_"+((connections&4)!=0?"on":"off"),0,0,presentation.depth);bottom(b,"matter_out_"+((connections&16)!=0?"on":"off"),0,0,presentation.depth);
        TextureAtlasSprite s=sprite("matter_out_"+((connections&8)!=0?"on":"off"));vertex(b,s,-.002,0,presentation.depth-1,0,1);vertex(b,s,-.002,0,presentation.depth,1,1);vertex(b,s,-.002,1,presentation.depth,1,0);vertex(b,s,-.002,1,presentation.depth-1,0,0);
        Tessellator.getInstance().draw();
    }
    private static TextureAtlasSprite sprite(String name){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/resource_ports/"+name);}
    private static void rear(BufferBuilder b,String name,int x,int y,int depth){TextureAtlasSprite s=sprite(name);vertex(b,s,x+1,y,depth+.002,0,1);vertex(b,s,x,y,depth+.002,1,1);vertex(b,s,x,y+1,depth+.002,1,0);vertex(b,s,x+1,y+1,depth+.002,0,0);}
    private static void bottom(BufferBuilder b,String name,int x,int y,int depth){TextureAtlasSprite s=sprite(name);vertex(b,s,x,y-.002,depth-1,0,0);vertex(b,s,x+1,y-.002,depth-1,1,0);vertex(b,s,x+1,y-.002,depth,1,1);vertex(b,s,x,y-.002,depth,0,1);}
    private static void vertex(BufferBuilder b,TextureAtlasSprite s,double x,double y,double z,double u,double v){b.pos(x,y,z).tex(s.getInterpolatedU(u*16),s.getInterpolatedV(v*16)).color(255,255,255,255).endVertex();}
    private SynthesisTowerRenderer(){}
}
