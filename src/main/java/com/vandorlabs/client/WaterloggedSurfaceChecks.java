package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;
import java.io.File;
import java.util.List;

/** Joined fluid surfaces, inset glazing and continuous slopes checked against real tile geometry. */
final class WaterloggedSurfaceChecks {
    private static final String[] IDS={"programmable_wall","programmable_porthole_wall","programmable_glass","programmable_diagonal_wall","programmable_diagonal_porthole","programmable_diagonal_glass"};
    private static BlockPos at(int kind){return new BlockPos(306+kind*4,32,142);}
    static void build(World world){OpeningFluidVisualChecks.build(world);for(int kind=0;kind<IDS.length;kind++)for(int column=0;column<2;column++){
        BlockPos pos=at(kind).east(column);Block block=Block.getBlockFromName("vandorlabs:"+IDS[kind]);require(block!=null,"fluid fixture block "+IDS[kind]);world.setBlockState(pos,block.getDefaultState(),2);
        ContainedFluids fluids=WaterloggedParts.data(world.getTileEntity(pos));fluids.set(0,new FluidStack(FluidRegistry.WATER,1000));
    }}
    static void run(Minecraft mc,File output)throws Exception{
        DoorCompanyFilterChecks.run(mc,output);StairConfigurationChecks.client(mc);StairIconChecks.run(mc,output);
        OpeningFluidVisualChecks.run(mc,output);
        ContainedFluidUpdateChecks.run(mc.world);
        ContainedFluidImmersionChecks.run(mc);
        SubmergedContactViewChecks.run(mc,output);
        FluidBoundaryMatrixChecks.run(mc);
        PoolFluidRegressionChecks.run(mc,output);
        FluidHeightRegressionChecks.run(mc,output);
        FluidWaterlineChecks.run(mc,output);
        DoorFluidSurfaceChecks.run(mc,output);
        WaterloggedRenderChecks.run(mc,output);
        require(WaterloggedRenderer.sprite(new FluidStack(FluidRegistry.WATER,1000))==mc.getTextureMapBlocks().getAtlasSprite(FluidRegistry.WATER.getStill().toString()),"contained water must use still artwork on every face");
        DiagonalFluidJoinChecks.run(mc,output);
        NativeFluidContactChecks.run(mc,output);
        SubmergedGlassChecks.run(mc,output);
        for(int kind=0;kind<IDS.length;kind++){
            TileEntity tile=mc.world.getTileEntity(at(kind));require(tile!=null,"fluid fixture synchronized");List<WaterloggedSurfaceMesh.Surface> mesh=WaterloggedSurfaceMesh.get(tile);
            require(mesh==WaterloggedSurfaceMesh.get(tile),"fluid surface cache reuse");
            for(WaterloggedSurfaceMesh.Surface face:mesh){require(face.boundary!=EnumFacing.EAST,"matching adjacent fluids must not draw an internal border: "+IDS[kind]);
                for(Vec3d p:face.vertices){require(p.x>=-1e-8&&p.x<=1+1e-8&&p.y>=-1e-8&&p.y<=1+1e-8&&p.z>=-1e-8&&p.z<=1+1e-8,"fluid stays inside block");if(face.boundary==EnumFacing.UP)require(p.y>0&&p.y<1,"exposed water surface below block top");}
            }
            require(!mesh.isEmpty(),"filled fluid surface present");
            TileEntity neighbor=mc.world.getTileEntity(at(kind).east());ContainedFluids data=WaterloggedParts.data(neighbor);data.set(0,new FluidStack(FluidRegistry.LAVA,1000));
            boolean boundary=false;for(WaterloggedSurfaceMesh.Surface face:WaterloggedSurfaceMesh.get(tile))boundary|=face.boundary==EnumFacing.EAST;require(boundary,"different fluids retain their interface");data.set(0,new FluidStack(FluidRegistry.WATER,1000));
            if(kind==1||kind==2){double expected=PanelJoins.flatNear(mc.world.getBlockState(at(kind)),mc.world,at(kind))/16D+1D/16-.001;boolean inset=false;for(WaterloggedSurfaceMesh.Surface face:WaterloggedSurfaceMesh.get(tile))if(face.boundary==null){for(Vec3d p:face.vertices)require(Math.abs(p.z-expected)<1e-6,"fluid reaches recessed pane");inset=true;}require(inset,"recessed fluid contact surface");}
            picture(mc,output,kind);
        }
        System.out.println("[vandorlabs][reprolab] waterlogged-surfaces PASS six panel families continuous slopes recessed panes same-fluid joins different-fluid interfaces cached surfaces");
    }
    private static void picture(Minecraft mc,File output,int kind)throws Exception{
        net.minecraft.client.shader.Framebuffer frame=new net.minecraft.client.shader.Framebuffer(640,360,true);int pass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(5889);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-1.4,1.4,-.7875,.7875,-10,10);GlStateManager.matrixMode(5888);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        try{frame.bindFramebuffer(true);GlStateManager.clearColor(.16F,.18F,.2F,1);GlStateManager.depthMask(true);GlStateManager.clear(16640);GlStateManager.enableDepth();GlStateManager.rotate(18,1,0,0);GlStateManager.rotate(160,0,1,0);GlStateManager.translate(-1,-.5,-.5);
            for(int renderPass=0;renderPass<2;renderPass++){net.minecraftforge.client.ForgeHooksClient.setRenderPass(renderPass);
            for(int column=0;column<2;column++){TileEntity tile=mc.world.getTileEntity(at(kind).east(column));net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer renderer=net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.instance.getRenderer(tile);if(tile.shouldRenderInPass(renderPass))renderer.render(tile,column,0,0,0,-1,1);}}
            javax.imageio.ImageIO.write(net.minecraft.util.ScreenShotHelper.createScreenshot(640,360,frame),"png",new File(output,"waterlogged-joined-"+IDS[kind]+".png"));
        }finally{net.minecraftforge.client.ForgeHooksClient.setRenderPass(pass);GlStateManager.popMatrix();GlStateManager.matrixMode(5889);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);org.lwjgl.opengl.GL11.glPopAttrib();frame.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.color(1,1,1,1);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
