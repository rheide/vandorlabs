package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/** Actual VBO/TESR fallback from inside a hull, with a separate synthetic shadow target. */
final class VehicleInteriorVisibilityChecks {
    @SuppressWarnings("unchecked") static void run(Minecraft mc,File output)throws Exception{
        Field shadow=null;try{shadow=Class.forName("net.optifine.shaders.Shaders").getField("isShadowPass");}catch(ClassNotFoundException absent){}
        boolean oldShadow=shadow!=null&&shadow.getBoolean(null);int oldView=mc.gameSettings.thirdPersonView;
        Vec3d before=mc.player.getPositionVector();double px=mc.player.prevPosX,py=mc.player.prevPosY,pz=mc.player.prevPosZ,lx=mc.player.lastTickPosX,ly=mc.player.lastTickPosY,lz=mc.player.lastTickPosZ;
        float yaw=mc.player.rotationYaw,pitch=mc.player.rotationPitch;
        Framebuffer frame=new Framebuffer(320,240,true),shadowTarget=new Framebuffer(320,240,true);EntityGroundVehicle craft=new EntityGroundVehicle(mc.world);
        VehicleStructure structure=fixture();craft.install(structure);craft.hover=true;craft.forceSpawn=true;
        BlockPos origin=new BlockPos(before.x-structure.seat.getX(),before.y+12,before.z-structure.seat.getZ());craft.setPosition(origin.getX(),origin.getY(),origin.getZ());craft.prevPosX=craft.lastTickPosX=craft.posX;craft.prevPosY=craft.lastTickPosY=craft.posY;craft.prevPosZ=craft.lastTickPosZ=craft.posZ;
        mc.world.addEntityToWorld(-47101,craft);require(craft.isAddedToWorld(),"isolated craft is registered for fallback");
        RenderManager manager=mc.getRenderManager();RenderGroundVehicle renderer=new RenderGroundVehicle(manager);
        java.util.Set<EntityGroundVehicle> drawn=net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(RenderGroundVehicle.class,null,"drawn");
        Vec3d seated=craft.seatPosition(craft.visualPose(1),mc.player);float forward=structure.facing.getHorizontalAngle();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            mc.gameSettings.thirdPersonView=0;
            mc.player.setPosition(seated.x,seated.y,seated.z);mc.player.prevPosX=mc.player.lastTickPosX=seated.x;mc.player.prevPosY=mc.player.lastTickPosY=seated.y;mc.player.prevPosZ=mc.player.lastTickPosZ=seated.z;
            manager.cacheActiveRenderInfo(mc.world,mc.fontRenderer,mc.player,null,mc.gameSettings,1);manager.setRenderPosition(seated.x,seated.y,seated.z);
            for(int i=0;i<32;i++){camera(mc,frame,forward);RenderGroundVehicle.beginFrame();renderer.doRender(craft,craft.posX-seated.x,craft.posY-seated.y,craft.posZ-seated.z,0,1);}
            int cases=0;
            for(int offset:new int[]{-90,-46,-44,-22,0,22,44,46,90,180}){
                float heading=forward+offset;mc.player.rotationYaw=mc.player.prevRotationYaw=heading;mc.player.rotationPitch=mc.player.prevRotationPitch=0;
                camera(mc,shadowTarget,heading);RenderGroundVehicle.beginFrame();
                if(shadow!=null){shadow.setBoolean(null,true);require(VehicleRenderPass.shadow(),"OptiFine shadow flag detected");renderer.doRender(craft,craft.posX-seated.x,craft.posY-seated.y,craft.posZ-seated.z,0,1);require(!drawn.contains(craft),"shadow draw must not satisfy main-view fallback");int fallback=RenderGroundVehicle.fallbackDraws();RenderGroundVehicle.renderMissing(1);require(RenderGroundVehicle.fallbackDraws()==fallback,"no late world fallback inside shadow pass");shadow.setBoolean(null,false);}
                else drawn.clear();
                camera(mc,frame,heading);RenderGroundVehicle.renderMissing(1);
                require(drawn.contains(craft)&&RenderGroundVehicle.fallbackDraws()==1,"main camera draws hull when origin is outside the visible section");
                BufferedImage image=net.minecraft.util.ScreenShotHelper.createScreenshot(320,240,frame);int pixels=colored(image);
                require(pixels>100,"interior pixels remain visible heading="+offset+" count="+pixels);
                if(offset==0){require(RenderGroundVehicle.tileDraws()>0,"interior screen/tile renderers survive fallback");javax.imageio.ImageIO.write(image,"png",new File(output,"vehicle-interior-forward.png"));
                    // The former shared-pass bookkeeping suppresses the only main-view draw.
                    camera(mc,frame,heading);drawn.add(craft);RenderGroundVehicle.renderMissing(1);BufferedImage hidden=net.minecraft.util.ScreenShotHelper.createScreenshot(320,240,frame);javax.imageio.ImageIO.write(hidden,"png",new File(output,"vehicle-interior-old-shadow-bookkeeping.png"));require(colored(hidden)==0,"old bookkeeping reproduces the empty interior");}
                cases++;
            }
            System.out.println("[vandorlabs][reprolab] vehicle-interior-visibility PASS headings="+cases+" first-person hull and tile pixels, hidden origin fallback, old-pass negative control; OptiFine shadow flag="+(shadow!=null));
        }finally{
            if(shadow!=null)shadow.setBoolean(null,oldShadow);craft.setDead();mc.world.removeEntityFromWorld(-47101);RenderGroundVehicle.cleanup(mc.world);
            mc.player.setPosition(before.x,before.y,before.z);mc.player.prevPosX=px;mc.player.prevPosY=py;mc.player.prevPosZ=pz;mc.player.lastTickPosX=lx;mc.player.lastTickPosY=ly;mc.player.lastTickPosZ=lz;mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;mc.gameSettings.thirdPersonView=oldView;manager.setRenderPosition(lx,ly,lz);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);frame.deleteFramebuffer();shadowTarget.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
        }
    }
    private static void camera(Minecraft mc,Framebuffer target,float yaw){target.bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.clearColor(0,0,0,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);GlStateManager.enableDepth();GlStateManager.enableAlpha();GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();Project.gluPerspective(75,320F/240,.05F,160);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();GlStateManager.rotate(yaw+180,0,1,0);GlStateManager.translate(0,-mc.player.getEyeHeight(),0);}
    private static int colored(BufferedImage image){int count=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)&0xffffff)!=0)count++;return count;}
    private static VehicleStructure fixture()throws Exception{
        String file=System.getProperty("vandorlabs.interiorFixture","");if(!file.isEmpty())try(InputStream input=new FileInputStream(file)){return VehicleStructure.read(CompressedStreamTools.readCompressed(input).getCompoundTag("Craft"));}
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        BlockPos seat=new BlockPos(12,1,4);cells.add(new VehicleStructure.Cell(seat,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        for(int x=10;x<=15;x++)for(int z=2;z<=6;z++)for(int y=0;y<=5;y++)if(y==0||y==5||x==10||x==15||z==2||z==6)cells.add(new VehicleStructure.Cell(new BlockPos(x,y,z),Blocks.GOLD_BLOCK.getDefaultState(),null));
        TileEntityAnimatedScreenSelector screen=new TileEntityAnimatedScreenSelector();BlockPos at=new BlockPos(14,2,4);screen.setPos(at);cells.add(new VehicleStructure.Cell(at,ModBlocks.ANIMATED_SCREEN_SELECTOR.getDefaultState().withProperty(BlockAnimatedScreenSelector.FACING,EnumFacing.WEST),screen.writeToNBT(new NBTTagCompound())));
        return new VehicleStructure(cells,seat,EnumFacing.EAST,1);
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Vehicle interior visibility: "+message);}
}
