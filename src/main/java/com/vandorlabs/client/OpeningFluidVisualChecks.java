package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;
import java.io.File;

final class OpeningFluidVisualChecks {
    private static BlockPos at(int kind){return new BlockPos(342+kind*5,32,142);}
    static void build(World world){for(int kind=0;kind<3;kind++){
        BlockPos pos=at(kind);Block block=Block.getBlockFromName("vandorlabs:"+OpeningFluidRuntimeChecks.IDS[kind]);world.setBlockState(pos.down(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);IBlockState state=block.getDefaultState();world.setBlockState(pos,state,2);
        if(kind==0){world.setBlockState(pos.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);((TileEntitySpaceDoor)world.getTileEntity(pos)).setPlacementDepth(0);}
        else ((TileEntityProgrammableTrapdoor)world.getTileEntity(pos)).configure(ScreenHousingTextures.DEFAULT_TRAPDOOR,1,false,com.vandorlabs.persistence.SpaceDoorData.TRIGGER_DISABLED,0);
        WaterloggedParts.data(world.getTileEntity(pos)).set(0,new FluidStack(FluidRegistry.WATER,1000));
    }}
    static void run(Minecraft mc,File output)throws Exception{for(int kind=0;kind<3;kind++){
        BlockPos pos=at(kind);TileEntity tile=mc.world.getTileEntity(pos);require(tile!=null,"fixture synchronized");IBlockState saved=mc.world.getBlockState(pos);
        try{for(boolean open:new boolean[]{false,true,false}){
            mc.world.setBlockState(pos,saved.withProperty(kind==0?BlockVandorDoor.OPEN:BlockProgrammableTrapdoor.OPEN,open),2);
            java.util.List<WaterloggedSurfaceMesh.Surface> mesh=WaterloggedSurfaceMesh.get(tile);require(!mesh.isEmpty(),"fluid mesh present");boolean divider=false;
            for(WaterloggedSurfaceMesh.Surface face:mesh){divider|=face.boundary==null;for(Vec3d point:face.vertices)require(point.x>=-1e-7&&point.x<=1+1e-7&&point.y>=-1e-7&&point.y<=1+1e-7&&point.z>=-1e-7&&point.z<=1+1e-7,"clipped fluid polygon");}
            require(divider!=open,"only the closed leaf has an internal fluid boundary");if(kind==0){boolean upperDivider=false;for(WaterloggedSurfaceMesh.Surface face:WaterloggedSurfaceMesh.get(mc.world.getTileEntity(pos.up())))upperDivider|=face.boundary==null;require(!upperDivider&&WaterloggedSurfaceMesh.get(mc.world.getTileEntity(pos.up())).isEmpty(),"dry upper half has no fluid mesh");}picture(mc,output,kind,tile,open);
        }}finally{mc.world.setBlockState(pos,saved,2);}
    }System.out.println("[vandorlabs][reprolab] opening-fluid-surfaces PASS closed diagonal half-spaces open joined passage restored closed side");}
    private static void picture(Minecraft mc,File output,int kind,TileEntity tile,boolean open)throws Exception{
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(640,420,true);int oldPass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-1.4,1.4,-.91875,.91875,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();
        try(TurntableRenderState saved=new TurntableRenderState()){
            frame.bindFramebuffer(true);GlStateManager.loadIdentity();GlStateManager.rotate(20,1,0,0);GlStateManager.rotate(145,0,1,0);GlStateManager.translate(-.5,kind==0?-1:-.5,-.5);GlStateManager.clearColor(.12F,.15F,.18F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();IBlockState state=mc.world.getBlockState(tile.getPos());
            for(int pass=0;pass<2;pass++){net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                if(kind==0){IBlockState actual=state.getBlock().getActualState(state,mc.world,tile.getPos());TESlidingDoor.renderSpaceDoor((TileEntitySpaceDoor)tile,actual,actual.getValue(BlockVandorDoor.FACING),open?1:0,0,0,0);}
                else if(pass==0){TileEntityProgrammableTrapdoor trap=(TileEntityProgrammableTrapdoor)tile;TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(trap.getHousingTexture()));BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(7,BlockSurfaceFormat.get());TEProgrammableTrapdoor.drawConfiguredLeaf(buffer,sprite,trap,state,open?1:0,15728880);Tessellator.getInstance().draw();}
                if(pass==1){WaterloggedRenderer.render(tile,0,0,0);if(kind==0)WaterloggedRenderer.render(mc.world.getTileEntity(tile.getPos().up()),0,1,0);}
            }
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(640,420,frame),"png",new File(output,"opening-fluid-"+kind+"-"+(open?"open":"closed")+".png"));
        }finally{net.minecraftforge.client.ForgeHooksClient.setRenderPass(oldPass);GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("opening fluid surfaces: "+message);}
}
