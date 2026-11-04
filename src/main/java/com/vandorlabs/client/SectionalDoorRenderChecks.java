package com.vandorlabs.client;

import com.vandorlabs.garage.*;
import com.vandorlabs.client.garage.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL11;
import java.io.File;
import java.util.*;

final class SectionalDoorRenderChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("sectional render: "+why);}
    static void run(Minecraft mc,File output)throws Exception{
        boolean authored=GarageDoorVisuals.authored;GarageDoorVisuals.authored=true;int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass(),cases=0;GarageDoorRenderer renderer=new GarageDoorRenderer();
        try{
            for(boolean extended:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.HORIZONTALS){GarageDoorTile tile=tile(mc,facing);VertexFormat format=VehicleVertexFormatRuntimeChecks.format(extended);
                for(int position=0;position<3;position++)for(int direction=0;direction<4;direction++)for(boolean straight:new boolean[]{false,true})for(int shape=-1;shape<4;shape++)for(boolean join:new boolean[]{false,true})for(double pose:new double[]{0,.5,1})for(int pass=0;pass<2;pass++){
                    tile.settings=new GarageDoorSettings(direction,straight,(direction+shape+3)%3,0,shape>=0,Math.max(0,shape),join,new DoorGlassAppearance(-1,Math.max(0,shape),Math.max(0,shape),1),position);tile.from=pose;tile.targetOpen=pose>0;tile.started=tile.getWorld().getTotalWorldTime();tile.duration=1000000;net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);
                    BufferBuilder reference=new BufferBuilder(4096),cached=new BufferBuilder(4096);reference.begin(GL11.GL_QUADS,format);renderer.renderUncached(tile,.25,4,.75,0,reference);reference.finishDrawing();cached.begin(GL11.GL_QUADS,format);renderer.renderTileEntityFast(tile,.25,4,.75,0,-1,1,cached);cached.finishDrawing();require(reference.getVertexCount()==cached.getVertexCount(),"cached vertex count");
                    AxisAlignedBB bounds=tile.getRenderBoundingBox().offset(.25,4,.75).grow(1e-5);int stride=format.getNextOffset();
                    for(int v=0;v<reference.getVertexCount();v++){int at=v*stride;require(bounds.contains(new Vec3d(reference.getByteBuffer().getFloat(at),reference.getByteBuffer().getFloat(at+4),reference.getByteBuffer().getFloat(at+8))),"swept render bounds facing="+facing+" direction="+direction+" straight="+straight+" pose="+pose);for(int byteAt=0;byteAt<stride;byteAt+=4){int p=at+byteAt;if(byteAt<12)require(Math.abs(reference.getByteBuffer().getFloat(p)-cached.getByteBuffer().getFloat(p))<1e-5,"cached position");else require(reference.getByteBuffer().getInt(p)==cached.getByteBuffer().getInt(p),"cached UV/color/light/shader payload extended="+extended+" facing="+facing+" direction="+direction+" straight="+straight+" shape="+shape+" join="+join+" pose="+pose+" pass="+pass+" vertex="+v+" byte="+byteAt+" expected="+Integer.toHexString(reference.getByteBuffer().getInt(p))+" actual="+Integer.toHexString(cached.getByteBuffer().getInt(p)));}}
                    cases++;
                }
            }
            for(int i=0;i<ScreenHousingTextures.IDS.length;i++){int choice=ScreenHousingTextures.choiceAt(i);String category=ScreenHousingTextures.category(choice);if(("Frame".equals(category)||"Frames".equals(category))&&GarageDoorTile.validTexture(choice)){GarageDoorTile frame=tile(mc,EnumFacing.NORTH);frame.texture=choice;for(double progress:new double[]{0,.5,1}){frame.from=progress;frame.targetOpen=progress>0;require(!frame.canBake(),"transparent Frame artwork never enters solid terrain at endpoints");}break;}}
            customUV(mc,renderer);
            for(int shape=0;shape<4;shape++)image(mc,renderer,output,shape,true,0,false,0,"window_"+shape+"_fit");
            image(mc,renderer,output,3,false,0,false,0,"round_tile");for(int dir=0;dir<4;dir++){image(mc,renderer,output,2,true,dir,false,1,"open_bent_"+dir);image(mc,renderer,output,2,true,dir,true,1,"open_straight_"+dir);}
            require(GarageDoorRenderer.retainedBytes()<=22*1024*1024,"bounded template/column/panel caches");System.out.println("[vandorlabs][reprolab] sectional-render PASS cases="+cases+" all facings/directions angles window shapes Tile/Fit vanilla/extended exact cached vertex parity swept bounds");
        }finally{GarageDoorVisuals.authored=authored;net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);}
    }
    private static void customUV(Minecraft mc,GarageDoorRenderer renderer){
        GarageDoorTile t=tile(mc,EnumFacing.NORTH);t.texture=CustomBlockMaterials.identifier(net.minecraft.init.Blocks.FURNACE,0);t.settings=new GarageDoorSettings(0,false,0,0,false,0,false,GarageDoorSettings.DEFAULT.glass);t.from=0;t.targetOpen=false;t.started=t.getWorld().getTotalWorldTime();t.duration=100000;net.minecraftforge.client.ForgeHooksClient.setRenderPass(0);net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=CustomBlockTextures.sprite(t.texture,false);int assertions=0;
        for(int column=0;column<3;column++){t.column=column;BufferBuilder b=new BufferBuilder(4096);b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);renderer.renderUncached(t,0,0,0,0,b);b.finishDrawing();int stride=DefaultVertexFormats.BLOCK.getNextOffset();for(int q=0;q<b.getVertexCount();q+=4){float z=b.getByteBuffer().getFloat(q*stride+8);boolean plane=true;for(int v=1;v<4;v++)plane&=Math.abs(b.getByteBuffer().getFloat((q+v)*stride+8)-z)<1e-6;if(!plane||Math.abs(Math.abs(z-.5)-GarageDoorGeometry.HALF_THICKNESS)>1e-5)continue;for(int v=0;v<4;v++){int at=(q+v)*stride;double x=b.getByteBuffer().getFloat(at),u=(b.getByteBuffer().getFloat(at+16)-sprite.getMinU())/(sprite.getMaxU()-sprite.getMinU());require(Math.abs(u-(column+x)/3)<1e-4,"custom artwork mirrored across a column or reverse face");assertions++;}}}
        require(assertions>24,"custom UV negative-control coverage");System.out.println("[vandorlabs][reprolab] sectional-custom-UV PASS assertions="+assertions+" asymmetric vanilla block front/back all three columns no artwork reflection");
    }
    private static GarageDoorTile tile(Minecraft mc,EnumFacing facing){net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:garage_door");BlockPos pos=BlockPos.ORIGIN;VehicleStructure structure=VehicleStructure.display(Collections.singletonList(new VehicleStructure.Cell(pos,block.getDefaultState().withProperty(GarageDoorBlock.FACING,facing),null)));VehicleWorld world=new VehicleWorld(mc.world,structure);world.origin=new BlockPos(0,80,0);GarageDoorTile tile=new GarageDoorTile();tile.setWorld(world);tile.setPos(pos);tile.height=3;tile.columns=3;tile.column=1;tile.formed=true;NBTTagCompound tag=tile.getUpdateTag();tile.readFromNBT(tag);world.tiles.put(pos,tile);return tile;}
    private static void image(Minecraft mc,GarageDoorRenderer renderer,File output,int shape,boolean join,int direction,boolean straight,double progress,String name)throws Exception{
        Framebuffer frame=new Framebuffer(400,320,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();if(progress==0)GlStateManager.ortho(-1.625,4.625,-2,3,-40,40);else GlStateManager.ortho(-6.5,9.5,-6.4,6.4,-40,40);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.01F);GlStateManager.color(1,1,1,1);GlStateManager.rotate(12,1,0,0);GlStateManager.rotate(18,0,1,0);mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);mc.entityRenderer.disableLightmap();
            for(int pass=0;pass<2;pass++){net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);if(pass==1){GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);GlStateManager.depthMask(false);}else{GlStateManager.disableBlend();GlStateManager.depthMask(true);}BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);for(int col=0;col<3;col++){GarageDoorTile t=tile(mc,EnumFacing.NORTH);t.column=col;t.settings=new GarageDoorSettings(direction,straight,0,0,true,shape,join,new DoorGlassAppearance(-1,1,1,1));t.from=progress;t.targetOpen=progress>0;t.started=t.getWorld().getTotalWorldTime();t.duration=1000000;renderer.renderUncached(t,col,1,0,0,b);}Tessellator.getInstance().draw();}
            GL11.glFinish();java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(400,320,frame);int ink=0;for(int y=0;y<320;y++)for(int x=0;x<400;x++)if((image.getRGB(x,y)&0xffffff)!=0x203040)ink++;require(ink>50,"visible window/mechanism image "+name);javax.imageio.ImageIO.write(image,"png",new File(output,"shot_sectional_"+name+".png"));require(GL11.glGetError()==GL11.GL_NO_ERROR,"GL state "+name);
        }finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private SectionalDoorRenderChecks(){}
}
