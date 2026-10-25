package com.vandorlabs.programmer.client;

import com.vandorlabs.programmer.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.item.*;
import net.minecraft.util.EnumFacing;
import java.io.File;

/** Checks the shipped meshes, real retained item models and the actual editor widget path. */
public final class ProgrammerClientChecks {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Programmer client: "+message);}
    public static void run(Minecraft mc,File output)throws Exception{
        ProgrammerModelCache cache=ProgrammerModelCache.INSTANCE;require(cache.isReady(),"cache ready");cache.assertTexturesResolved();
        for(ProgrammerStatus status:ProgrammerStatus.values()){require(cache.mesh(status,"static").quadCount()>=100,"authored casing "+status);require(cache.mesh(status,"screen").quadCount()==1,"separate screen "+status);}
        ItemStack stack=new ItemStack(Item.getByNameOrId("vandorlabs:data_chip_programmer"));IBakedModel model=mc.getRenderItem().getItemModelWithOverrides(stack,mc.world,mc.player);require(model.getQuads(null,null,0).size()>=100,"authored item mesh");
        ItemTransformVec3f gui=model.getItemCameraTransforms().gui;for(BakedQuad quad:model.getQuads(null,null,0)){require(quad.getSprite()!=mc.getTextureMapBlocks().getMissingSprite(),"inventory quad missing native sprite");int[] data=quad.getVertexData();int stride=data.length/4;for(int i=0;i<4;i++){int v=i*stride;double x=(Float.intBitsToFloat(data[v])-.5)*gui.scale.x,y=(Float.intBitsToFloat(data[v+1])-.5)*gui.scale.y,z=(Float.intBitsToFloat(data[v+2])-.5)*gui.scale.z;double ry=Math.toRadians(gui.rotation.y),rx=Math.toRadians(gui.rotation.x);double px=x*Math.cos(ry)+z*Math.sin(ry),pz=z*Math.cos(ry)-x*Math.sin(ry),py=y*Math.cos(rx)-pz*Math.sin(rx);require(Math.abs(px+gui.translation.x)<.42&&Math.abs(py+gui.translation.y)<.42,"rotated GUI padding");}}
        int blackScreens=0,keyboards=0;for(BakedQuad quad:model.getQuads(null,null,0)){
            if(quad.getSprite().getIconName().equals("vandorlabs:models/cockpits/blank"))blackScreens++;
            if(quad.getSprite().getIconName().equals("vandorlabs:blocks/data_chip_programmer/keyboard")){
                keyboards++;int[] data=quad.getVertexData();int stride=data.length/4;double[] length=new double[2];for(int edge=0;edge<2;edge++)for(int axis=0;axis<3;axis++){double d=Float.intBitsToFloat(data[(edge+1)*stride+axis])-Float.intBitsToFloat(data[edge*stride+axis]);length[edge]+=d*d;}require(Math.abs(Math.sqrt(Math.max(length[0],length[1])/Math.min(length[0],length[1]))-2)<.02,"existing keyboard artwork retains native2:1 physical aspect");
                int uv=quad.getFormat().getUvOffsetById(0)/4;float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;for(int v=0;v<4;v++){float value=Float.intBitsToFloat(data[v*stride+uv+1]);min=Math.min(min,value);max=Math.max(max,value);}require(Math.abs((max-min)/(quad.getSprite().getMaxV()-quad.getSprite().getMinV())-.5)<.002,"keyboard samples exact top half of square atlas");
            }
        }require(blackScreens==1&&keyboards==1,"actual model uses one black screen and existing keyboard sprite");
        GuiProgrammer.checkEditor(mc,output);
        retainedTokens(mc,output);
        inventoryIcon(mc,output,stack);
        System.out.println("[vandorlabs][reprolab] programmer-client PASS four cached statuses authored inventory padding retained-token models and editor input");
    }
    /** Render each real slot separately so another occupied pad cannot conceal a missing token. */
    private static void retainedTokens(Minecraft mc,File output)throws Exception{
        net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:data_chip_programmer");
        net.minecraft.util.math.BlockPos root=new net.minecraft.util.math.BlockPos(2,0,2);int cases=0;
        for(EnumFacing facing:EnumFacing.HORIZONTALS){
            java.util.List<com.vandorlabs.vehicle.VehicleStructure.Cell> cells=new java.util.ArrayList<>();ProgrammerTile source=new ProgrammerTile();source.setPos(root);
            for(int part=0;part<4;part++)cells.add(new com.vandorlabs.vehicle.VehicleStructure.Cell(ProgrammerBlock.cell(root,facing,part),block.getDefaultState().withProperty(ProgrammerBlock.FACING,facing).withProperty(ProgrammerBlock.PART,part),part==0?source.writeToNBT(new net.minecraft.nbt.NBTTagCompound()):null));
            com.vandorlabs.vehicle.VehicleWorld world=new com.vandorlabs.vehicle.VehicleWorld(mc.world,com.vandorlabs.vehicle.VehicleStructure.display(cells));ProgrammerTile tile=(ProgrammerTile)world.getTileEntity(root);require(tile!=null&&tile.isProgrammerController(),"isolated native renderer owner "+facing);
            java.awt.image.BufferedImage empty=console(mc,tile);require(missingPixels(empty)==0,"actual native casing contains missing-texture magenta "+facing);require(ink(empty)>15000,"native console visibly renders "+facing);
            ProgrammerTransform.Affine nativePose=ProgrammerGeometry.transform(facing);net.minecraft.util.math.BlockPos nativeOrigin=tile.getProgrammerOrigin();double[] screen=project(tile,nativeOrigin.getX()+nativePose.x(1,.7932742597),nativeOrigin.getY()+1.59375,nativeOrigin.getZ()+nativePose.z(1,.7932742597));int dark=0;for(int sy=(int)screen[1]-2;sy<=(int)screen[1]+2;sy++)for(int sx=(int)screen[0]-2;sx<=(int)screen[0]+2;sx++)if((empty.getRGB(sx,sy)&0xffffff)==0)dark++;require(dark>=20,"actual giant screen renders opaque black "+facing);
            if(facing==EnumFacing.NORTH)javax.imageio.ImageIO.write(empty,"png",new File(output,"shot_programmer_empty.png"));
            for(boolean chip:new boolean[]{false,true}){
                for(int slot=0;slot<3;slot++){
                    tile.clear();tile.setInventorySlotContents(slot,new ItemStack(chip?com.vandorlabs.items.ModItems.DATA_CHIP:com.vandorlabs.items.ModItems.ACCESS_CARD,1,slot+1));
                    java.awt.image.BufferedImage occupied=console(mc,tile);java.awt.Rectangle pad=pad(tile,slot);int changed=0,outside=0;
                    for(int y=0;y<empty.getHeight();y++)for(int x=0;x<empty.getWidth();x++)if((empty.getRGB(x,y)&0xffffff)!=(occupied.getRGB(x,y)&0xffffff)){if(pad.contains(x,y))changed++;else outside++;}
                    if(changed<25||outside>Math.max(15,changed/12)){
                        javax.imageio.ImageIO.write(occupied,"png",new File(output,"shot_programmer_failed_"+facing+"_"+(chip?"chip":"card")+"_"+slot+".png"));
                        throw new IllegalStateException("Programmer retained "+(chip?"chip":"card")+" slot="+slot+" facing="+facing+" visible="+changed+" outside authored pad="+outside+" region="+pad);
                    }
                    cases++;
                }
                tile.clear();for(int slot=0;slot<3;slot++)tile.setInventorySlotContents(slot,new ItemStack(chip?com.vandorlabs.items.ModItems.DATA_CHIP:com.vandorlabs.items.ModItems.ACCESS_CARD,1,slot+1));
                java.awt.image.BufferedImage all=console(mc,tile);require(ink(all)>=ink(empty),"occupied console remains visible");if(facing==EnumFacing.NORTH)javax.imageio.ImageIO.write(all,"png",new File(output,"shot_programmer_"+(chip?"chips":"cards")+".png"));
            }
            world.update(com.vandorlabs.vehicle.VehicleStructure.display(java.util.Collections.emptyList()));require(ink(console(mc,tile))==0,"removed owner draws neither casing nor retained tokens");
        }
        System.out.println("[vandorlabs][reprolab] programmer-retained-render PASS isolated-slots="+cases+" four facings cards chips authored pad pixel bounds removed-owner negative controls");
    }
    private static final int WIDTH=768,HEIGHT=640,BACKGROUND=0x203040;
    private static int missingPixels(java.awt.image.BufferedImage image){int count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int rgb=image.getRGB(x,y),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;if(r>190&&g<35&&b>190)count++;}return count;}
    private static int ink(java.awt.image.BufferedImage image){int count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)&0xffffff)!=BACKGROUND)count++;return count;}
    /** Same independent authored pad bounds for both native card and native square-chip models. */
    private static java.awt.Rectangle pad(ProgrammerTile tile,int slot){
        double minX=slot==0?9:slot==1?25:1,maxX=slot==0?23:slot==1?31:7,minZ=slot==0?8:1,maxZ=14;
        ProgrammerTransform.Affine a=ProgrammerTransform.of(ProgrammerRenderHelper.facing(tile.getProgrammerFacing()),2,1);net.minecraft.util.math.BlockPos origin=tile.getProgrammerOrigin();
        double left=Double.POSITIVE_INFINITY,top=left,right=-left,bottom=-left;
        for(double x:new double[]{minX,maxX})for(double y:new double[]{16.02,16.85})for(double z:new double[]{minZ,maxZ}){
            double[] p=project(tile,origin.getX()+a.x(x/16,z/16),origin.getY()+y/16,origin.getZ()+a.z(x/16,z/16));left=Math.min(left,p[0]);right=Math.max(right,p[0]);top=Math.min(top,p[1]);bottom=Math.max(bottom,p[1]);
        }
        return new java.awt.Rectangle((int)Math.floor(left)-5,(int)Math.floor(top)-5,(int)Math.ceil(right-left)+11,(int)Math.ceil(bottom-top)+11);
    }
    private static double[] project(ProgrammerTile tile,double x,double y,double z){
        net.minecraft.util.math.BlockPos p=tile.getProgrammerOrigin();boolean wide=tile.getProgrammerFacing().getAxis()==EnumFacing.Axis.Z;x-=p.getX()+(wide?1:.5);y-=p.getY()+.9;z-=p.getZ()+(wide?.5:1);
        double yaw=Math.toRadians(tile.getProgrammerFacing().getHorizontalAngle()),pitch=Math.toRadians(50),rx=x*Math.cos(yaw)+z*Math.sin(yaw),rz=z*Math.cos(yaw)-x*Math.sin(yaw),ry=y*Math.cos(pitch)-rz*Math.sin(pitch);
        return new double[]{(rx+1.35)/2.7*WIDTH,(1.1-ry)/2.2*HEIGHT};
    }
    private static java.awt.image.BufferedImage console(Minecraft mc,ProgrammerTile tile){
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(WIDTH,HEIGHT,true);net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.ortho(-1.35,1.35,-1.1,1.1,-10,10);net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try(com.vandorlabs.client.TurntableRenderState saved=new com.vandorlabs.client.TurntableRenderState()){
            frame.bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.depthMask(true);net.minecraft.client.renderer.GlStateManager.clearColor(32/255F,48/255F,64/255F,1);net.minecraft.client.renderer.GlStateManager.clear(16640);net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.enableTexture2D();net.minecraft.client.renderer.GlStateManager.disableLighting();net.minecraft.client.renderer.GlStateManager.disableBlend();net.minecraft.client.renderer.GlStateManager.enableAlpha();net.minecraft.client.renderer.GlStateManager.alphaFunc(org.lwjgl.opengl.GL11.GL_GREATER,.1F);net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);
            net.minecraft.client.renderer.GlStateManager.rotate(50,1,0,0);net.minecraft.client.renderer.GlStateManager.rotate(tile.getProgrammerFacing().getHorizontalAngle(),0,1,0);net.minecraft.util.math.BlockPos p=tile.getProgrammerOrigin();boolean wide=tile.getProgrammerFacing().getAxis()==EnumFacing.Axis.Z;net.minecraft.client.renderer.GlStateManager.translate(-p.getX()-(wide?1:.5),-p.getY()-.9,-p.getZ()-(wide?.5:1));
            new ProgrammerRenderer().render(tile,tile.getPos().getX(),tile.getPos().getY(),tile.getPos().getZ(),0,-1,1);org.lwjgl.opengl.GL11.glFinish();return net.minecraft.util.ScreenShotHelper.createScreenshot(WIDTH,HEIGHT,frame);
        }finally{net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }

    private static void inventoryIcon(Minecraft mc,File output,ItemStack stack)throws Exception{
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(128,128,true);net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.ortho(0,32,32,0,-1000,1000);net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);net.minecraft.client.renderer.GlStateManager.pushMatrix();float previousLevel=mc.getRenderItem().zLevel;
        try(com.vandorlabs.client.TurntableRenderState saved=new com.vandorlabs.client.TurntableRenderState()){
            frame.bindFramebuffer(true);net.minecraft.client.renderer.GlStateManager.loadIdentity();net.minecraft.client.renderer.GlStateManager.depthMask(true);net.minecraft.client.renderer.GlStateManager.clearColor(32/255F,48/255F,64/255F,1);net.minecraft.client.renderer.GlStateManager.clear(16640);net.minecraft.client.renderer.GlStateManager.enableDepth();net.minecraft.client.renderer.GlStateManager.enableTexture2D();net.minecraft.client.renderer.GlStateManager.enableAlpha();net.minecraft.client.renderer.GlStateManager.enableRescaleNormal();net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();mc.getRenderItem().zLevel=0;mc.getRenderItem().renderItemAndEffectIntoGUI(stack,8,8);org.lwjgl.opengl.GL11.glFinish();java.awt.image.BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(128,128,frame);javax.imageio.ImageIO.write(image,"png",new File(output,"shot_programmer_inventory.png"));int pixels=0;
            for(int y=0;y<128;y++)for(int x=0;x<128;x++)if((image.getRGB(x,y)&0xffffff)!=BACKGROUND){pixels++;require(x>=35&&x<=92&&y>=35&&y<=92,"actual inventory icon retains padding after GUI transform at "+x+","+y);}require(pixels>250,"actual inventory console icon visible");
        }finally{mc.getRenderItem().zLevel=previousLevel;net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);net.minecraft.client.renderer.GlStateManager.popMatrix();net.minecraft.client.renderer.GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private ProgrammerClientChecks(){}
}
