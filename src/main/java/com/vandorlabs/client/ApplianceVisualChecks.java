package com.vandorlabs.client;

import com.vandorlabs.appliance.ApplianceCatalog;
import com.vandorlabs.items.AppliancePresets;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

final class ApplianceVisualChecks {
    private static void need(boolean value,String why){if(!value)throw new IllegalStateException("Appliance visual: "+why);}
    private static void fullFaceUV(BakedQuad quad){
        net.minecraft.client.renderer.vertex.VertexFormat format=quad.getFormat();int uv=-1;for(int i=0;i<format.getElementCount();i++)if(format.getElement(i).getUsage()==net.minecraft.client.renderer.vertex.VertexFormatElement.EnumUsage.UV&&format.getElement(i).getIndex()==0)uv=i;need(uv>=0,"UV field");float[] values=new float[4];double minU=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,minV=minU,maxV=maxU;
        for(int vertex=0;vertex<4;vertex++){net.minecraftforge.client.model.pipeline.LightUtil.unpack(quad.getVertexData(),values,format,vertex,uv);double u=quad.getSprite().getUnInterpolatedU(values[0]),v=quad.getSprite().getUnInterpolatedV(values[1]);minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);}need(Math.abs(maxU-minU-16)<.01&&Math.abs(maxV-minV-16)<.01,"whole authored face retained, including slab sides "+(maxU-minU)+"x"+(maxV-minV));
    }
    private static final int BACKGROUND=0x202830;
    private static BufferedImage attachments(Minecraft mc,Framebuffer target,
                                               com.vandorlabs.tiles.TileEntityChipAppliance tile,EnumFacing facing){
        target.bindFramebuffer(true);
        GlStateManager.loadIdentity();
        GlStateManager.clearColor(32/255F,40/255F,48/255F,1);
        GlStateManager.depthMask(true);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.enableAlpha();
        GlStateManager.color(1,1,1,1);
        Tessellator.getInstance().getBuffer().setTranslation(0,0,0);
        int northAngle=facing==EnumFacing.EAST?270:facing==EnumFacing.SOUTH?180:facing==EnumFacing.WEST?90:0;
        GlStateManager.translate(.5,0,.5);
        // A slight view from above reveals the item/frame contact as well as the front.
        GlStateManager.rotate(12,1,0,0);
        GlStateManager.rotate(180-northAngle,0,1,0);
        GlStateManager.translate(-.5,0,-.5);
        TEChipAppliance renderer=new TEChipAppliance();
        renderer.setRendererDispatcher(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance);
        renderer.render(tile,0,0,0,0,-1,1);
        return net.minecraft.util.ScreenShotHelper.createScreenshot(target.framebufferWidth,target.framebufferHeight,target);
    }

    static void checkMountedMap(Minecraft mc,File output,com.vandorlabs.tiles.TileEntityChipAppliance tile,int rgb)throws Exception{
        Framebuffer target=new Framebuffer(192,320,true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        GlStateManager.ortho(-.25,1.25,0,2.5,-4,4);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState ignored=new TurntableRenderState()){
            GlStateManager.enableCull();
            EnumFacing facing=tile.getWorld().getBlockState(tile.getPos()).getValue(BlockVandorDirectional.FACING);
            BufferedImage image=attachments(mc,target,tile,facing);
            javax.imageio.ImageIO.write(image,"png",new File(output,"scanner-mounted-map.png"));
            int pixels=0;
            for(int y=160;y<202;y++)for(int x=48;x<144;x++){
                if((image.getRGB(x,y)&0xffffff)==(rgb&0xffffff))pixels++;
            }
            need(pixels>900,"actual map pixels face upward and cover the frame: "+pixels);
        }finally{
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
        }
        System.out.println("[vandorlabs] scanner-mounted-map PASS actual map pixels on the scanner with back-face culling");
    }

    private static void checkAttachments(Minecraft mc,File output,BlockPos pos)throws Exception{
        Framebuffer target=new Framebuffer(192,320,true);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        GlStateManager.ortho(-.25,1.25,0,2.5,-4,4);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState ignored=new TurntableRenderState()){
            for(String id:new String[]{"printer","scanner"})for(EnumFacing facing:EnumFacing.HORIZONTALS){
                IBlockState state=Block.getBlockFromName("vandorlabs:"+id).getDefaultState().withProperty(BlockVandorDirectional.FACING,facing);
                mc.world.setBlockToAir(pos);
                mc.world.setBlockState(pos,state,2);
                com.vandorlabs.tiles.TileEntityChipAppliance tile=(com.vandorlabs.tiles.TileEntityChipAppliance)mc.world.getTileEntity(pos);
                BufferedImage empty=attachments(mc,target,tile,facing);
                for(boolean pressed:new boolean[]{false,true}){
                    tile.pressedUntil=pressed?mc.world.getTotalWorldTime()+8:0;
                    BufferedImage image=attachments(mc,target,tile,facing);
                    int min=192,max=-1;
                    for(int y=210;y<260;y++)for(int x=96;x<192;x++){
                        if((image.getRGB(x,y)&0xffffff)==BACKGROUND)continue;
                        min=Math.min(min,x);max=Math.max(max,x);
                    }
                    need(min>=110&&max<=150&&max-min>=18,id+" "+facing+" button width/center "+min+".."+max);
                }
                if(tile.scanner()){
                    tile.pressedUntil=0;
                    for(net.minecraft.item.Item book:new net.minecraft.item.Item[]{net.minecraft.init.Items.WRITTEN_BOOK,net.minecraft.init.Items.ENCHANTED_BOOK}){
                        tile.setInventorySlotContents(1,new ItemStack(book));
                        BufferedImage image=attachments(mc,target,tile,facing);
                        int changed=0;
                        for(int y=0;y<205;y++)for(int x=0;x<192;x++){
                            if(image.getRGB(x,y)==empty.getRGB(x,y))continue;
                            need(y>=164&&y<=196&&x>=54&&x<=138,"book is seated within the top frame "+facing+" "+x+","+y);
                            changed++;
                        }
                        need(changed>30,"visible top-mounted book "+facing);
                        if(facing==EnumFacing.NORTH&&book==net.minecraft.init.Items.ENCHANTED_BOOK)
                            javax.imageio.ImageIO.write(image,"png",new File(output,"scanner-attachments.png"));
                    }
                }
            }
        }finally{
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
            mc.world.setBlockToAir(pos);
        }
        System.out.println("[vandorlabs] device-attachments PASS both buttons pressed and released, written and enchanted books, four facings");
    }

    static void run(Minecraft mc,File output)throws Exception{
        BlockPos pos=new BlockPos(46,90,40);
        HousingTextureList picker=HousingTextureList.forHousing(0,0,180,0,8,"");
        List<?> categories=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(HousingTextureList.class,picker,"categories");
        for(String label:ApplianceCatalog.LABELS)
            need(categories.contains("Devices/"+label),"actual texture picker contains Devices/"+label);
        for(int i=0;i<4;i++){
            Block block=i<2?ModBlocks.PROGRAMMABLE_SLAB:ModBlocks.PROGRAMMABLE_BLOCK;IBlockState state=block.getDefaultState();mc.world.setBlockToAir(pos);mc.world.setBlockState(pos,state,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)mc.world.getTileEntity(pos);NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());saved.merge(AppliancePresets.stack(i).getSubCompound("BlockEntityTag"));tile.readFromNBT(saved);IBlockState extended=block.getExtendedState(state,mc.world,pos);IBakedModel model=mc.getBlockRendererDispatcher().getModelForState(state);Set<String> sprites=new HashSet<>();
            for(int f=-1;f<6;f++)for(BakedQuad quad:model.getQuads(extended,f<0?null:EnumFacing.getFront(f),0)) {sprites.add(quad.getSprite().getIconName());fullFaceUV(quad); }for(int f=0;f<6;f++)need(sprites.contains("vandorlabs:blocks/"+ApplianceCatalog.texture(i,f)),"actual programmable world face "+i+" "+f+" "+sprites);mc.world.setBlockToAir(pos);
        }
        try(TurntableRenderState ignored=new TurntableRenderState()){
            for(int i=0;i<6;i++)for(int scale:new int[]{1,4}){
                ItemStack stack=i<4?AppliancePresets.stack(i):new ItemStack(Block.getBlockFromName("vandorlabs:"+ApplianceCatalog.IDS[i]));Framebuffer fb=new Framebuffer(20*scale,20*scale,true);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,20,20,0,-1000,1000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
                try{fb.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.clearColor(13/255F,17/255F,24/255F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableAlpha();RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().renderItemAndEffectIntoGUI(stack,2,2);GL11.glFinish();BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(20*scale,20*scale,fb);int minX=20*scale,minY=minX,maxX=-1,maxY=-1,count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)&0xffffff)!=0x0d1118){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);count++;}need(count>12*scale*scale,"nonempty icon");need(minX>=3*scale&&minY>=3*scale&&maxX<17*scale&&maxY<17*scale,"all-side inventory padding "+i+" "+scale+" "+minX+","+minY+".."+maxX+","+maxY);javax.imageio.ImageIO.write(image,"png",new File(output,"appliance-icon-"+i+"-"+scale+".png"));}
                finally{GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);fb.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
            }
        }
        checkAttachments(mc,output,pos);
        System.out.println("[vandorlabs] appliances-visual PASS actual per-face world meshes all6itemmodels padded1x4xicons");
    }
}
