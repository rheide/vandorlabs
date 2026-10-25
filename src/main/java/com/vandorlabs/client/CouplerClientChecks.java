package com.vandorlabs.client;

import com.vandorlabs.coupler.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/** Native artwork, emitted geometry, real item padding and the common coupler dialog. */
public final class CouplerClientChecks {
    private static final EnumFacing[] SIDES={null,EnumFacing.UP,EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST};
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Coupler client: "+message);}
    private static List<BakedQuad> quads(Minecraft mc,IBlockState state){IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);require(model!=mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel(),"native model resolves "+state);List<BakedQuad> quads=new ArrayList<>();for(EnumFacing side:SIDES)quads.addAll(model.getQuads(state,side,0));return quads;}
    public static void run(Minecraft mc,File output)throws Exception{
        BlockCoupler full=(BlockCoupler)Block.getBlockFromName("vandorlabs:square_coupler");BlockCouplerSlab slab=(BlockCouplerSlab)Block.getBlockFromName("vandorlabs:vehicle_coupler_slab");BufferedImage sheet=new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);int captures=0,cases=0;
        for(boolean locked:new boolean[]{false,true})for(EnumFacing facing:EnumFacing.values()){
            IBlockState source=full.getDefaultState().withProperty(BlockCoupler.FACING,facing).withProperty(BlockCoupler.LOCKED,locked);List<BakedQuad> nativeFaces=quads(mc,source);checkModel(mc,source,nativeFaces,false);require(visible(render(mc,nativeFaces))>500,"full actual endpoint renders "+facing+locked);cases++;
            if(!facing.getAxis().isHorizontal())continue;
            for(BlockSlab.EnumBlockHalf half:BlockSlab.EnumBlockHalf.values()){
                IBlockState state=slab.getDefaultState().withProperty(BlockCoupler.FACING,facing).withProperty(BlockCoupler.LOCKED,locked).withProperty(BlockCouplerSlab.HALF,half);List<BakedQuad> faces=quads(mc,state);checkModel(mc,state,faces,true);require(faces.size()==nativeFaces.size(),"slab retains same authored native face decomposition");
                for(int i=0;i<faces.size();i++){BakedQuad q=faces.get(i),old=nativeFaces.get(i);require(q.getFace()==old.getFace()&&q.getSprite()==old.getSprite(),"slab preserves native face artwork/order");double ratio=uvArea(q)/uvArea(old);require(Math.abs(ratio-(q.getFace().getAxis()==EnumFacing.Axis.Y?1:.5))<.003,"halfheight crops vertical UVs and retains horizontal UVs without stretching "+q.getFace());}
                BufferedImage image=render(mc,faces);require(visible(image)>250,"actual slab endpoint visibly renders "+state);if(captures<16){for(int y=0;y<128;y++)for(int x=0;x<128;x++)sheet.setRGB(captures%4*128+x,captures/4*128+y,image.getRGB(x,y));captures++;}cases++;
            }
        }
        javax.imageio.ImageIO.write(sheet,"png",new File(output,"shot_vehicle_coupler_slab_endpoints.png"));inventory(mc,output,new ItemStack(full),"full");inventory(mc,output,new ItemStack(slab),"slab");dialog(mc,output);
        System.out.println("[vandorlabs][reprolab] coupler-client PASS native-endpoints="+cases+" full6axes slab4horizontal top/bottom locked/released missingtexture negativefront-overlap UVcrop-preserved artwork actual FBO padded icons compact common dialog");
    }
    private static double uvArea(BakedQuad quad){int[] data=quad.getVertexData();int stride=data.length/4,uv=quad.getFormat().getUvOffsetById(0)/4;double minU=Double.POSITIVE_INFINITY,minV=minU,maxU=-minU,maxV=-minU;for(int v=0;v<4;v++){double u=Float.intBitsToFloat(data[v*stride+uv]),w=Float.intBitsToFloat(data[v*stride+uv+1]);minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,w);maxV=Math.max(maxV,w);}return(maxU-minU)*(maxV-minV);}
    private static double[] bounds(BakedQuad quad){double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};int[] data=quad.getVertexData();for(int v=0;v<4;v++)for(int axis=0;axis<3;axis++){double p=Float.intBitsToFloat(data[v*data.length/4+axis]);b[axis]=Math.min(b[axis],p);b[axis+3]=Math.max(b[axis+3],p);}return b;}
    private static void checkModel(Minecraft mc,IBlockState state,List<BakedQuad> faces,boolean slab){
        require(faces.size()==53,"native hidden-front duplicate removed, remaining53faces");EnumFacing front=state.getValue(BlockCoupler.FACING);List<double[]> fronts=new ArrayList<>();int plane=front.getAxis()==EnumFacing.Axis.X?0:front.getAxis()==EnumFacing.Axis.Y?1:2;boolean top=slab&&state.getValue(BlockCouplerSlab.HALF)==BlockSlab.EnumBlockHalf.TOP;
        for(BakedQuad quad:faces){require(quad.getSprite()!=mc.getTextureMapBlocks().getMissingSprite(),"native face sprite resolves");double[] b=bounds(quad);if(slab)require(b[1]>=(top?.5:0)-1e-6&&b[4]<=(top?1:.5)+1e-6,"actual emitted slab stays in selected half");if(quad.getFace()==front)fronts.add(b);}
        for(int i=0;i<fronts.size();i++)for(int j=0;j<i;j++){double[] a=fronts.get(i),b=fronts.get(j);if(Math.abs(a[plane]-b[plane])>1e-6)continue;double overlap=1;for(int axis=0;axis<3;axis++)if(axis!=plane)overlap*=Math.max(0,Math.min(a[axis+3],b[axis+3])-Math.max(a[axis],b[axis]));require(overlap<1e-7,"no coincident overlapping front-facing quads/Z fighting");}
    }
    private static int visible(BufferedImage image){int count=0;for(int y=0;y<128;y++)for(int x=0;x<128;x++)if((image.getRGB(x,y)&0xffffff)!=0x203040)count++;return count;}
    private static BufferedImage render(Minecraft mc,List<BakedQuad> quads){Framebuffer frame=new Framebuffer(128,128,true);projection(-1,1,-1,1);try(TurntableRenderState saved=new TurntableRenderState()){
        clear(frame);GlStateManager.rotate(30,1,0,0);GlStateManager.rotate(225,0,1,0);GlStateManager.translate(-.5,-.5,-.5);mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.setTranslation(0,0,0);buffer.begin(7,quads.get(0).getFormat());for(BakedQuad q:quads)buffer.addVertexData(q.getVertexData());Tessellator.getInstance().draw();GL11.glFinish();return ScreenShotHelper.createScreenshot(128,128,frame);
    }finally{finish(mc,frame);}}
    private static void inventory(Minecraft mc,File output,ItemStack stack,String name)throws Exception{IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);ItemTransformVec3f gui=model.getItemCameraTransforms().gui;for(EnumFacing side:SIDES)for(BakedQuad quad:model.getQuads(null,side,0)){int[] d=quad.getVertexData();for(int v=0;v<4;v++){int i=v*d.length/4;net.minecraft.util.math.Vec3d projected=ControlItemPadding.project(new net.minecraft.util.math.Vec3d(Float.intBitsToFloat(d[i]),Float.intBitsToFloat(d[i+1]),Float.intBitsToFloat(d[i+2])),gui);require(Math.abs(projected.x+gui.translation.x)<.42&&Math.abs(projected.y+gui.translation.y)<.42,"native rotated icon geometry padding "+name);}}
        Framebuffer frame=new Framebuffer(128,128,true);projection(0,32,32,0);float level=mc.getRenderItem().zLevel;try(TurntableRenderState saved=new TurntableRenderState()){clear(frame);GlStateManager.enableRescaleNormal();RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().zLevel=0;mc.getRenderItem().renderItemAndEffectIntoGUI(stack,8,8);GL11.glFinish();BufferedImage image=ScreenShotHelper.createScreenshot(128,128,frame);int ink=0;for(int y=0;y<128;y++)for(int x=0;x<128;x++)if((image.getRGB(x,y)&0xffffff)!=0x203040){ink++;require(x>=35&&x<=92&&y>=35&&y<=92,"actual item icon padding "+name);}require(ink>100,"actual item visible "+name);javax.imageio.ImageIO.write(image,"png",new File(output,"shot_vehicle_coupler_inventory_"+name+".png"));}finally{mc.getRenderItem().zLevel=level;finish(mc,frame);}}
    private static void dialog(Minecraft mc,File output)throws Exception{Field inverted=GuiCoupler.class.getDeclaredField("inverted");inverted.setAccessible(true);net.minecraft.inventory.Container previous=mc.player.openContainer;try{for(int[] size:new int[][]{{320,240},{460,340}}){TileCoupler tile=new TileCoupler();GuiCoupler gui=new GuiCoupler(new ContainerCoupler(tile));gui.inventorySlots.windowId=-92;gui.setWorldAndResolution(mc,size[0],size[1]);require(gui.getGuiLeft()>=0&&gui.getGuiTop()>=0&&gui.getGuiLeft()+gui.getXSize()<=size[0]&&gui.getGuiTop()+gui.getYSize()<=size[1],"common dialog fits viewport");gui.mouseClicked(gui.getGuiLeft()+16,gui.getGuiTop()+80,1);require((Boolean)inverted.get(gui),"right click previous coupling mode");gui.mouseClicked(gui.getGuiLeft()+16,gui.getGuiTop()+80,0);require(!(Boolean)inverted.get(gui),"left click next coupling mode");Framebuffer frame=new Framebuffer(size[0],size[1],true);projection(0,size[0],size[1],0);try(TurntableRenderState saved=new TurntableRenderState()){clear(frame);gui.drawScreen(-1,-1,0);GL11.glFinish();BufferedImage image=ScreenShotHelper.createScreenshot(size[0],size[1],frame);require((image.getRGB(gui.getGuiLeft()+3,gui.getGuiTop()+3)&0xffffff)==0x304858,"common dark dialog header unaffected by item lighting");javax.imageio.ImageIO.write(image,"png",new File(output,"shot_vehicle_coupler_dialog_"+size[0]+".png"));}finally{finish(mc,frame);}}}finally{mc.player.openContainer=previous;org.lwjgl.input.Keyboard.enableRepeatEvents(false);}}
    private static void projection(double left,double right,double bottom,double top){GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(left,right,bottom,top,-1000,1000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();}
    private static void clear(Framebuffer frame){frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.depthMask(true);GlStateManager.clearColor(32/255F,48/255F,64/255F,1);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.disableBlend();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.color(1,1,1,1);Minecraft.getMinecraft().entityRenderer.disableLightmap();}
    private static void finish(Minecraft mc,Framebuffer frame){GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    private CouplerClientChecks(){}
}
