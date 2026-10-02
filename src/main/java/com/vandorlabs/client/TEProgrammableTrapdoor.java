package com.vandorlabs.client;

import com.vandorlabs.animation.*;
import com.vandorlabs.blocks.BlockProgrammableTrapdoor;
import com.vandorlabs.render.TrapdoorGeometry;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import java.util.*;

/** Rigid leaves only: no frame or hinge hardware. No per-tile ticking. */
public final class TEProgrammableTrapdoor extends TileEntitySpecialRenderer<TileEntityProgrammableTrapdoor> {
    private static final VisualClock CLOCK=new VisualClock();
    private static final Map<TileEntityProgrammableTrapdoor,DoorAnimation> ANIMATIONS=new WeakHashMap<>();
    @Override public void render(TileEntityProgrammableTrapdoor tile,double x,double y,double z,float partial,int stage,float alpha) {
        if(tile.getWorld()==null)return;
        IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        if(!(state.getBlock() instanceof BlockProgrammableTrapdoor))return;
        double now=CLOCK.sample(System.nanoTime(),Minecraft.getMinecraft().isGamePaused());
        DoorAnimation animation=ANIMATIONS.computeIfAbsent(tile,t->new DoorAnimation(9));
        double pose=animation.sample(state.getValue(BlockProgrammableTrapdoor.OPEN),now);
        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(tile.getHousingTexture()));
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);GlStateManager.disableLighting();
        GlStateManager.color(1,1,1,1);
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());
        if(ScreenHousingTextures.isDoor(tile.getHousingTexture())) {
            double[][] vertices=tile instanceof TileEntityProgrammableDiagonalTrapdoor
                    ?com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor.corners(state,(TileEntityProgrammableDiagonalTrapdoor)tile,pose)
                    :TrapdoorGeometry.corners(tile.getPosition(),tile.isSliding(),BlockProgrammableTrapdoor.quarterTurns(state.getValue(BlockProgrammableTrapdoor.FACING)),pose,tile.motionHinge(),tile.motionTravel());
            drawMesh(buffer,sprite,vertices,materialCoordinates(tile,state),light);
        } else {
        if(tile instanceof TileEntityProgrammableDiagonalTrapdoor) {
            TileEntityProgrammableDiagonalTrapdoor diagonal=(TileEntityProgrammableDiagonalTrapdoor)tile;
            drawDiagonalLeaf(buffer,sprite,diagonal.getPosition(),diagonal.isInverted(),BlockProgrammableTrapdoor.quarterTurns(state.getValue(BlockProgrammableTrapdoor.FACING)),diagonal.isSliding(),diagonal.isReverse(),pose,light,diagonal.motionHinge(),diagonal.motionTravel());
        } else drawLeaf(buffer,sprite,tile.getPosition(),tile.isSliding(),BlockProgrammableTrapdoor.quarterTurns(state.getValue(BlockProgrammableTrapdoor.FACING)),pose,light,tile.motionHinge(),tile.motionTravel());
        }
        Tessellator.getInstance().draw();GlStateManager.enableLighting();GlStateManager.popMatrix();
    }
    private static double[][] materialCoordinates(TileEntityProgrammableTrapdoor tile,IBlockState state) {
        java.util.List<TileEntityProgrammableTrapdoor> group=tile.group();
        boolean diagonal=tile instanceof TileEntityProgrammableDiagonalTrapdoor;
        boolean tall=diagonal && tile.getPosition()!=2;
        net.minecraft.util.EnumFacing facing=state.getValue(BlockProgrammableTrapdoor.FACING);
        boolean widthX=!diagonal || facing.getAxis()==net.minecraft.util.EnumFacing.Axis.Z;
        double minU=Double.POSITIVE_INFINITY,minV=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,maxV=Double.NEGATIVE_INFINITY;
        for(TileEntityProgrammableTrapdoor leaf:group) {
            double u=widthX?leaf.getPos().getX():leaf.getPos().getZ();
            double v=tall?leaf.getPos().getY():diagonal && !widthX?leaf.getPos().getX():leaf.getPos().getZ();
            minU=Math.min(minU,u);maxU=Math.max(maxU,u+1);minV=Math.min(minV,v);maxV=Math.max(maxV,v+1);
        }
        double[][] closed=diagonal?com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor.corners(state,(TileEntityProgrammableDiagonalTrapdoor)tile,0)
                :TrapdoorGeometry.corners(tile.getPosition(),tile.isSliding(),BlockProgrammableTrapdoor.quarterTurns(facing),0);
        double[][] uv=new double[8][3];
        for(int i=0;i<8;i++) {
            double u=(widthX?tile.getPos().getX()+closed[i][0]:tile.getPos().getZ()+closed[i][2]);
            double v=tall?tile.getPos().getY()+closed[i][1]:diagonal && !widthX?tile.getPos().getX()+closed[i][0]:tile.getPos().getZ()+closed[i][2];
            double mappedV=tall?1-(v-minV)/(maxV-minV):(v-minV)/(maxV-minV);
            if(group.size()==1)mappedV=.5+.5*mappedV;
            uv[i]=new double[]{(u-minU)/(maxU-minU),tall?mappedV:(i&2)==0?0:1,tall?(i&4)==0?0:1:mappedV};
        }
        return uv;
    }
    /** Buffer-only entry point also verifies actual submitted geometry without GL. */
    static void drawLeaf(BufferBuilder buffer,TextureAtlasSprite sprite,int position,boolean sliding,int turns,double pose,int light) {
        drawLeaf(buffer,sprite,position,sliding,turns,pose,light,1/16D,15/16D);
    }
    static void drawLeaf(BufferBuilder buffer,TextureAtlasSprite sprite,int position,boolean sliding,int turns,double pose,int light,double hinge,double travel) {
        double[][] vertices=TrapdoorGeometry.corners(position,sliding,turns,pose,hinge,travel);
        double[][] original=TrapdoorGeometry.corners(position,true,0,0);
        double[][] uv=new double[8][3];
        for(int i=0;i<8;i++)uv[i]=new double[]{original[i][0],original[i][1]-TrapdoorGeometry.low(position),original[i][2]};
        drawMesh(buffer,sprite,vertices,uv,light);
    }
    static void drawDiagonalLeaf(BufferBuilder buffer,TextureAtlasSprite sprite,int mode,boolean inverted,int turns,boolean sliding,boolean reverse,double pose,int light) {
        drawDiagonalLeaf(buffer,sprite,mode,inverted,turns,sliding,reverse,pose,light,reverse?15/16D:1/16D,15/16D);
    }
    static void drawDiagonalLeaf(BufferBuilder buffer,TextureAtlasSprite sprite,int mode,boolean inverted,int turns,boolean sliding,boolean reverse,double pose,int light,double hinge,double travel) {
        double[][] vertices=com.vandorlabs.render.DiagonalTrapdoorGeometry.corners(mode,inverted,turns,sliding,reverse,pose,hinge,travel);
        double[][] uv=new double[8][3];
        for(int i=0;i<8;i++)uv[i]=new double[]{(i&1)==0?0:1,(i&2)==0?0:mode==2?2/16D:1,(i&4)==0?0:mode==2?1:2/16D};
        drawMesh(buffer,sprite,vertices,uv,light);
    }
    static void drawMesh(BufferBuilder buffer,TextureAtlasSprite sprite,double[][] vertices,double[][] original,int light) {
        for(int face=0;face<TrapdoorGeometry.FACES.length;face++) {
            int[] indices=TrapdoorGeometry.FACES[face];
            double[] a=vertices[indices[0]],b=vertices[indices[1]],c=vertices[indices[2]];
            double ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            for(int index:indices) {
                double[] p=vertices[index],uv=original[index];
                double u=(face<4?uv[0]:uv[2])*16;
                double v=face<2?uv[2]*16:uv[1]*16;
                buffer.pos(p[0],p[1],p[2]).color(255,255,255,255)
                        .tex(sprite.getInterpolatedU(u),sprite.getInterpolatedV(v))
                        .lightmap(light>>>16,light&65535).normal((float)(nx/length),(float)(ny/length),(float)(nz/length)).endVertex();
            }
        }
    }
}
