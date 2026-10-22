package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraftforge.fluids.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import java.util.*;

/** Water cannot repaint an opaque contact when an immersed camera turns past it. */
final class SubmergedContactViewChecks {
    static void run(Minecraft mc,java.io.File output)throws Exception{
        BlockPos pos=new BlockPos(350,52,146);int cases=0,failed=0;Integer clearPixel=null;List<String> examples=new ArrayList<>();
        net.minecraft.entity.Entity oldView=mc.getRenderViewEntity();
        net.minecraft.client.entity.EntityOtherPlayerMP probe=new net.minecraft.client.entity.EntityOtherPlayerMP(mc.world,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("902d3aee-c01d-4ae4-b74d-54925cd02691"),"ContactProbe"));
        java.lang.reflect.Field camera=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(ActiveRenderInfo.class,"position","field_178811_e");Object oldCamera=camera.get(null);
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(160,160,true);int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GLU.gluPerspective(35,1,.002F,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try{camera.set(null,Vec3d.ZERO);mc.setRenderViewEntity(probe);
            for(String id:new String[]{"programmable_wall","programmable_glass","programmable_porthole_wall","programmable_diagonal_wall","programmable_diagonal_glass","programmable_diagonal_porthole","programmable_door"})for(EnumFacing facing:EnumFacing.HORIZONTALS){
                clear(mc,pos);Block block=Block.getBlockFromName("vandorlabs:"+id);boolean door=block instanceof BlockConfigurableSpaceDoor;IBlockState state=block.getDefaultState();
                if(door)state=state.withProperty(BlockVandorDoor.FACING,facing);else if(block instanceof BlockProgrammableWall)state=state.withProperty(BlockProgrammableWall.FACING,facing);else state=state.withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X);
                mc.world.setBlockState(pos,state,2);if(door)mc.world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
                TileEntity tile=mc.world.getTileEntity(pos);for(int side=0;side<2;side++){WaterloggedParts.data(tile).set(side,new FluidStack(FluidRegistry.WATER,1000));if(door)WaterloggedParts.data(mc.world.getTileEntity(pos.up())).set(side,new FluidStack(FluidRegistry.WATER,1000));}
                for(EnumFacing side:EnumFacing.values())if(!door||side!=EnumFacing.UP)mc.world.setBlockState(pos.offset(side),Blocks.WATER.getDefaultState(),2);
                for(EnumFacing contact:EnumFacing.values()){
                    if(door&&contact.getAxis()!=facing.getAxis())continue;
                    Vec3d eye,look;BlockPos solid=pos.offset(contact);
                    if(door){double[] plane=OpeningFluids.plane(mc.world,pos);boolean first=contact==facing.getOpposite();double d=plane[first?3:4];Vec3d normal=new Vec3d(plane[0],0,plane[2]);Vec3d center=new Vec3d(.5,.5,.5);Vec3d target=center.add(normal.scale(d-normal.dotProduct(center)));eye=target.add(normal.scale(first?-.05:.05));look=normal.scale(first?1:-1);}
                    else {
                        mc.world.setBlockState(solid,Blocks.STONE.getDefaultState(),2);AxisAlignedBB chosen=null;double best=0;
                        // Ignore collision epsilon slivers that put the camera inside the near clipping plane.
                        for(WaterloggedParts.Cell cell:WaterloggedParts.volume(mc.world,pos).cells){AxisAlignedBB b=cell.box;double edge=contact==EnumFacing.WEST?b.minX:contact==EnumFacing.EAST?1-b.maxX:contact==EnumFacing.DOWN?b.minY:contact==EnumFacing.UP?1-b.maxY:contact==EnumFacing.NORTH?b.minZ:1-b.maxZ;double size=(b.maxX-b.minX)*(b.maxY-b.minY)*(b.maxZ-b.minZ);if(edge<.002&&Math.min(b.maxX-b.minX,Math.min(b.maxY-b.minY,b.maxZ-b.minZ))>.01&&size>best){chosen=b;best=size;}}
                        if(chosen==null){mc.world.setBlockState(solid,Blocks.WATER.getDefaultState(),2);continue;}
                        eye=chosen.getCenter();look=new Vec3d(contact.getDirectionVec());
                    }
                    for(int turn=-2;turn<=2;turn++){
                        frame.bindFramebuffer(true);GlStateManager.loadIdentity();Vec3d target=eye.add(look);Vec3d up=Math.abs(look.y)>.9?new Vec3d(0,0,1):new Vec3d(0,1,0);
                        GlStateManager.rotate(turn*.4F,0,1,0);GlStateManager.rotate(turn*.2F,1,0,0);GLU.gluLookAt((float)eye.x,(float)eye.y,(float)eye.z,(float)target.x,(float)target.y,(float)target.z,(float)up.x,(float)up.y,(float)up.z);
                        probe.setPosition(pos.getX()+eye.x,pos.getY()+eye.y,pos.getZ()+eye.z);probe.prevPosX=probe.posX;probe.prevPosY=probe.posY;probe.prevPosZ=probe.posZ;
                        GlStateManager.clearColor(.16F,.18F,.2F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.enableCull();GlStateManager.disableFog();GlStateManager.disableLighting();GlStateManager.color(1,1,1,1);
                        if(clearPixel==null)clearPixel=net.minecraft.util.ScreenShotHelper.createScreenshot(160,160,frame).getRGB(80,80);
                        mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
                        net.minecraftforge.client.ForgeHooksClient.setRenderPass(0);
                        if(door){IBlockState actual=state.getBlock().getActualState(state,mc.world,pos);TESlidingDoor.renderSpaceDoor((TileEntitySpaceDoor)tile,actual,actual.getValue(BlockVandorDoor.FACING),0,0,0,0);}
                        else {BufferBuilder b=new BufferBuilder(4096);b.begin(7,DefaultVertexFormats.BLOCK);b.setTranslation(-pos.getX(),-pos.getY(),-pos.getZ());IBlockState stone=mc.world.getBlockState(solid);mc.getBlockRendererDispatcher().getBlockModelRenderer().renderModel(mc.world,mc.getBlockRendererDispatcher().getModelForState(stone),stone,solid,b,false);b.finishDrawing();new WorldVertexBufferUploader().draw(b);}
                        java.awt.image.BufferedImage before=net.minecraft.util.ScreenShotHelper.createScreenshot(160,160,frame);
                        // A blank/occluded fixture must not turn this into a vacuous pass.
                        int opaque=0;for(int x=64;x<96;x++)for(int y=64;y<96;y++)if(before.getRGB(x,y)!=clearPixel)opaque++;
                        if(opaque<900){javax.imageio.ImageIO.write(before,"png",new java.io.File(output,"contact-fixture-failure.png"));throw new IllegalStateException("Opaque contact fixture missing: "+id+" "+facing+" "+contact+" pixels="+opaque+" eye="+eye+" turn="+turn);}
                        net.minecraftforge.client.ForgeHooksClient.setRenderPass(1);WaterloggedRenderer.render(tile,0,0,0);
                        java.awt.image.BufferedImage after=net.minecraft.util.ScreenShotHelper.createScreenshot(160,160,frame);int changed=0;
                        for(int x=64;x<96;x++)for(int y=64;y<96;y++)if(before.getRGB(x,y)!=after.getRGB(x,y))changed++;
                        if(changed>0){failed++;if(examples.size()<8)examples.add(id+" "+facing+" "+contact+" turn="+turn+" pixels="+changed);}
                        if(facing==EnumFacing.NORTH&&turn==0){String name="submerged-contact-"+id+"-"+contact;javax.imageio.ImageIO.write(before,"png",new java.io.File(output,name+"-opaque.png"));javax.imageio.ImageIO.write(after,"png",new java.io.File(output,name+"-water.png"));}
                        cases++;
                    }
                    if(!door)mc.world.setBlockState(solid,Blocks.WATER.getDefaultState(),2);
                }
            }
        }finally{clear(mc,pos);mc.setRenderViewEntity(oldView);camera.set(null,oldCamera);net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.depthMask(true);GlStateManager.enableCull();}
        if(cases!=760)throw new IllegalStateException("Submerged contact coverage incomplete: "+cases+" / 760");
        if(failed>0)throw new IllegalStateException("Submerged opaque contacts repainted by water: "+failed+" / "+cases+" "+examples);
        System.out.println("[vandorlabs][reprolab] submerged-contact-views PASS cases="+cases+" seven families four facings six contacts door both sides five yaw/pitch steps opaque pixels unchanged");
    }
    private static void clear(Minecraft mc,BlockPos pos){for(BlockPos p:BlockPos.getAllInBox(pos.add(-1,-1,-1),pos.add(1,2,1)))mc.world.setBlockToAir(p);}
}
