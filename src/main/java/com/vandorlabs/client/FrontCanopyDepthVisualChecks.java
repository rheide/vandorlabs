package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.canopy.CanopyMesh;
import com.vandorlabs.tiles.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import java.io.File;
import java.util.*;

/** Real diagonal-wall quads meet the broad rear rim; draw order must not select the artwork. */
final class FrontCanopyDepthVisualChecks {
    private static final BlockPos ROOT=new BlockPos(3,3,3);
    private static final int SIZE=384;
    private static void require(boolean yes,String message){if(!yes)throw new IllegalStateException("front-canopy depth: "+message);}
    private static VehicleWorld fixture(Minecraft mc,EnumFacing facing,boolean open,boolean opaque){
        List<VehicleStructure.Cell> cells=new ArrayList<>();cells.add(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),null));
        net.minecraft.block.Block canopy=net.minecraft.block.Block.getBlockFromName("vandorlabs:large_front_canopy_glass");
        TileEntityCanopy tile=new TileEntityCanopy();tile.setPos(ROOT);tile.anchor=ROOT;tile.centeredPlacement=true;tile.hollowPlacement=true;
        tile.large.depth=16;tile.large.scale=71;tile.large.openable=true;tile.large.hinge=0;tile.progress=tile.previous=open?1:0;tile.targetOpen=open;
        if(opaque){tile.large.material=CustomBlockMaterials.choice(new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.IRON_BLOCK));tile.large.tint=2;}
        // This tile is not attached to a World yet, so writeToNBT cannot infer
        // its configurable block type. Include the authored settings explicitly.
        NBTTagCompound saved=tile.writeToNBT(new NBTTagCompound());tile.large.write(saved);
        cells.add(new VehicleStructure.Cell(ROOT,canopy.getDefaultState().withProperty(BlockCanopy.FACING,facing),saved));
        for(int x=0;x<5;x++)for(int y=0;y<5;y++)if(x==0||x==4||y==0||y==4){
            BlockPos pos=ROOT.offset(facing).offset(facing.rotateY(),x).up(y);
            TileEntityAnimatedScreenSelector wall=new TileEntityAnimatedScreenSelector();wall.setPos(pos);wall.setHousingTexture(CustomBlockMaterials.choice(new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.GOLD_BLOCK)));
            cells.add(new VehicleStructure.Cell(pos,ModBlocks.PROGRAMMABLE_DIAGONAL_WALL.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing),wall.writeToNBT(new NBTTagCompound())));
        }
        VehicleWorld view=new VehicleWorld(mc.world,new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));view.origin=mc.player.getPosition();
        TileEntityCanopy restored=(TileEntityCanopy)view.getTileEntity(ROOT);
        require(restored.opening()&&restored.large.depth==16&&restored.large.scale==71,"fixture retains openable configured geometry");
        require(restored.progress==(open?1:0)&&restored.large.material==tile.large.material,"fixture retains actual pose and material");
        if(opaque)for(CanopyMesh.Face face:restored.baseFaces())require(!com.vandorlabs.canopy.CanopyFinishes.translucent(face,restored.large.material,restored.large.tint),"opaque order fixture contains only opaque faces");
        return view;
    }
    private static void walls(Minecraft mc,VehicleWorld view){
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);GlStateManager.disableLighting();GlStateManager.disableBlend();GlStateManager.enableCull();GlStateManager.depthMask(true);GlStateManager.color(1,1,1,1);
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.BLOCK);
        net.minecraftforge.client.ForgeHooksClient.setRenderLayer(net.minecraft.util.BlockRenderLayer.SOLID);
        try{for(VehicleStructure.Cell cell:view.structure.cells)if(cell.state.getBlock()==ModBlocks.PROGRAMMABLE_DIAGONAL_WALL)mc.getBlockRendererDispatcher().renderBlock(cell.state,cell.pos,view,buffer);}
        finally{net.minecraftforge.client.ForgeHooksClient.setRenderLayer(null);}
        require(buffer.getVertexCount()>0,"fixture draws actual diagonal-wall geometry");
        Tessellator.getInstance().draw();
    }
    private static BufferedImage draw(Minecraft mc,VehicleWorld view,EnumFacing facing,boolean canopyFirst){
        int oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        Framebuffer target=new Framebuffer(SIZE,SIZE,true);TileEntityRendererDispatcher dispatcher=TileEntityRendererDispatcher.instance;
        net.minecraft.world.World oldWorld=dispatcher.world;double oldX=dispatcher.entityX,oldY=dispatcher.entityY,oldZ=dispatcher.entityZ;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(-4.3,4.3,-4.3,4.3,-30,30);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{
            target.bindFramebuffer(true);
            // Raw attribute restoration in other offscreen checks can leave the
            // state-manager cache ahead of GL. Establish the actual fixture state.
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_CULL_FACE);GL11.glDepthMask(true);
            GlStateManager.depthMask(true);GlStateManager.clearColor(.04F,.05F,.07F,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            // Both draw orders must use the same winding/depth policy. walls()
            // enables culling; starting the canopy-first order without it also
            // drew rear-facing artwork and invalidated the depth comparison.
            GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.enableCull();GlStateManager.color(1,1,1,1);
            Vec3d center=CanopyMesh.rotate(new Vec3d(2.5,2.5,.5),facing).add(new Vec3d(ROOT));
            GlStateManager.loadIdentity();GlStateManager.rotate(16,1,0,0);GlStateManager.rotate(facing==EnumFacing.EAST?115:25,0,1,0);GlStateManager.translate(-center.x,-center.y,-center.z);
            dispatcher.world=view;dispatcher.entityX=center.x+8;dispatcher.entityY=center.y+5;dispatcher.entityZ=center.z+10;
            TileEntityCanopy canopy=(TileEntityCanopy)view.getTileEntity(ROOT);TECanopy renderer=(TECanopy)(Object)dispatcher.getRenderer(canopy);
            require(renderer!=null,"canopy renderer registered");
            require(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glIsEnabled(GL11.GL_CULL_FACE)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),"order comparison starts with identical depth and culling state");
            if(canopyFirst)renderer.render(canopy,ROOT.getX(),ROOT.getY(),ROOT.getZ(),1,-1,1);
            walls(mc,view);
            if(!canopyFirst)renderer.render(canopy,ROOT.getX(),ROOT.getY(),ROOT.getZ(),1,-1,1);
            GL11.glFinish();return ScreenShotHelper.createScreenshot(SIZE,SIZE,target);
        }finally{
            dispatcher.world=oldWorld;dispatcher.entityX=oldX;dispatcher.entityY=oldY;dispatcher.entityZ=oldZ;
            GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();
            GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_CULL_FACE);GL11.glEnable(GL11.GL_LIGHTING);GL11.glDepthMask(true);GL11.glColor4f(1,1,1,1);
            GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);target.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.bindTexture(oldTexture);
        }
    }
    static void run(Minecraft mc,File output)throws Exception{
        BufferedImage montage=new BufferedImage(SIZE*2,(SIZE+24)*4,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=montage.createGraphics();graphics.setColor(Color.WHITE);
        int row=0;
        for(boolean opaque:new boolean[]{false,true})for(EnumFacing facing:new EnumFacing[]{EnumFacing.NORTH,EnumFacing.EAST}){
            BufferedImage closed=null;
            for(boolean open:new boolean[]{false,true}){
                VehicleWorld view=fixture(mc,facing,open,opaque);BufferedImage normal=draw(mc,view,facing,false);
                javax.imageio.ImageIO.write(normal,"png",new File(output,"shot_canopy_depth_"+(opaque?"opaque_":"glass_")+facing+"_"+(open?"open":"closed")+".png"));
                if(!open)closed=normal;else {int moved=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(closed.getRGB(x,y)!=normal.getRGB(x,y))moved++;require(moved>100,"opened canopy changes visible fixture pixels: facing="+facing+" opaque="+opaque+" changed="+moved);}
                if(opaque){BufferedImage reverse=draw(mc,view,facing,true);int changed=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(normal.getRGB(x,y)!=reverse.getRGB(x,y))changed++;
                    javax.imageio.ImageIO.write(reverse,"png",new File(output,"shot_canopy_depth_opaque_"+facing+"_"+(open?"open":"closed")+"_reverse.png"));
                    require(changed<=12,"opaque rear rim remains independent of wall/canopy draw order: "+facing+" open="+open+" changed="+changed);}
                int col=open?1:0;graphics.drawImage(normal,col*SIZE,row*(SIZE+24)+24,null);graphics.drawString((opaque?"Opaque panels":"Glass")+" / "+facing+" / "+(open?"open":"closed"),col*SIZE+6,row*(SIZE+24)+17);
            }row++;
        }
        graphics.dispose();javax.imageio.ImageIO.write(montage,"png",new File(output,"shot_large_canopy_diagonal_wall_depth.png"));
        System.out.println("[vandorlabs][reprolab] canopy-wall-depth-client PASS real-diagonal-wall-quads rear-rim opaque-draw-order stable glass/open/closed North/East screenshot");
    }
    private FrontCanopyDepthVisualChecks(){}
}
