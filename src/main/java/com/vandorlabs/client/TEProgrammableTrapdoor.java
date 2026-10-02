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
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.fullTexture(tile.getHousingTexture()));
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);GlStateManager.disableLighting();
        GlStateManager.color(1,1,1,1);
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,BlockSurfaceFormat.get());
        drawConfiguredLeaf(buffer,sprite,tile,state,pose,light);
        Tessellator.getInstance().draw();GlStateManager.enableLighting();GlStateManager.popMatrix();
    }
    /** Broad artwork and native dark-wall leaf edges share the exact collision mesh. */
    static void drawConfiguredLeaf(BufferBuilder buffer,TextureAtlasSprite sprite,TileEntityProgrammableTrapdoor tile,IBlockState state,double pose,int light) {
        boolean diagonal=tile instanceof TileEntityProgrammableDiagonalTrapdoor,tall=diagonal && tile.getPosition()!=2;
        double[][] vertices=diagonal?com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor.corners(state,(TileEntityProgrammableDiagonalTrapdoor)tile,pose):tile.corners(state,pose);
        int first=tall?2:0,end=first+2;
        boolean door=ScreenHousingTextures.isDoor(tile.getHousingTexture());
        TrapdoorSurfaceMesh.draw(buffer,sprite,vertices,materialCoordinates(tile,state),light,tile.getHousingTexture(),first,end,tile.isTileTexture(),tile.isTileTexture() && door);
        TextureAtlasSprite edge=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/dark_wall_panel");
        double[][] edgeUv=new double[8][3];
        for(int i=0;i<8;i++)edgeUv[i]=new double[]{(i&1)==0?0:1,(i&2)==0?0:tall?1:diagonal?2/16D:TrapdoorGeometry.THICKNESS,(i&4)==0?0:tall?2/16D:1};
        for(int face=0;face<6;face++)if(face<first || face>=end)drawMesh(buffer,edge,vertices,edgeUv,light,face,face+1);
    }
    static double[][] materialCoordinates(TileEntityProgrammableTrapdoor tile,IBlockState state) {
        java.util.List<TileEntityProgrammableTrapdoor> group=tile.group();
        boolean diagonal=tile instanceof TileEntityProgrammableDiagonalTrapdoor;
        boolean tall=diagonal && tile.getPosition()!=2;
        net.minecraft.util.EnumFacing facing=state.getValue(BlockProgrammableTrapdoor.FACING);
        boolean widthX=facing.getAxis()==(diagonal?net.minecraft.util.EnumFacing.Axis.Z:net.minecraft.util.EnumFacing.Axis.X);
        boolean door=ScreenHousingTextures.isDoor(tile.getHousingTexture());
        double minU=Double.POSITIVE_INFINITY,minV=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY,maxV=Double.NEGATIVE_INFINITY;
        for(TileEntityProgrammableTrapdoor leaf:group) {
            double u=widthX?leaf.getPos().getX():leaf.getPos().getZ();
            double v=tall?leaf.getPos().getY():widthX?leaf.getPos().getZ():leaf.getPos().getX();
            minU=Math.min(minU,u);maxU=Math.max(maxU,u+1);minV=Math.min(minV,v);maxV=Math.max(maxV,v+1);
        }
        double[][] closed=diagonal?com.vandorlabs.blocks.BlockProgrammableDiagonalTrapdoor.corners(state,(TileEntityProgrammableDiagonalTrapdoor)tile,0)
                :tile.corners(state,0);
        double[][] uv=new double[8][3];
        for(int i=0;i<8;i++) {
            double u=(widthX?tile.getPos().getX()+closed[i][0]:tile.getPos().getZ()+closed[i][2]);
            double v=tall?tile.getPos().getY()+closed[i][1]:widthX?tile.getPos().getZ()+closed[i][2]:tile.getPos().getX()+closed[i][0];
            // Offset covers live in the neighboring cell, independent of their owning tile.
            if(tile.isCover()){net.minecraft.util.math.BlockPos target=tile.getPos().offset(facing);minU=widthX?target.getX():target.getZ();maxU=minU+1;minV=widthX?target.getZ():target.getX();maxV=minV+1;}
            double mappedU=u-minU,mappedV=tall?maxV-v:v-minV;
            if(tile.isTileTexture()){if(door){mappedV/=2;if(maxV-minV<1.5)mappedV+=.5;}}
            else{mappedU/=maxU-minU;mappedV/=maxV-minV;if(door && maxV-minV<1.5)mappedV=.5+.5*mappedV;}
            uv[i]=new double[]{mappedU,tall?mappedV:(i&2)==0?0:1,tall?(i&4)==0?0:1:mappedV};
        }
        return uv;
    }
    /** Buffer-only entry point also verifies actual submitted geometry without GL. */
    static void drawLeaf(BufferBuilder buffer,TextureAtlasSprite sprite,int position,boolean sliding,int turns,double pose,int light) {
        drawLeaf(buffer,sprite,position,sliding,turns,pose,light,TrapdoorGeometry.OPEN_HINGE,15/16D);
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
    /** Split custom door artwork at the upper/lower boundary even across a connected surface. */
    static void drawMaterialMesh(BufferBuilder buffer,TextureAtlasSprite sprite,double[][] vertices,double[][] uv,int light,int choice) {
        drawMaterialMesh(buffer,sprite,vertices,uv,light,choice,0,6);
    }
    static void drawMaterialMesh(BufferBuilder buffer,TextureAtlasSprite sprite,double[][] vertices,double[][] uv,int light,int choice,int first,int end) {
        if(!com.vandorlabs.tiles.CustomBlockMaterials.isCustom(choice) || !CustomBlockTextures.isDoor(choice)){drawMesh(buffer,sprite,vertices,uv,light,first,end);return;}
        for(int face=first;face<end;face++) {
            int[] indices=TrapdoorGeometry.FACES[face];
            double[] a=vertices[indices[0]],b=vertices[indices[1]],c=vertices[indices[2]];
            double nx=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
            double ny=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
            double nz=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            for(boolean upper:new boolean[]{true,false}) {
                java.util.List<double[]> polygon=new java.util.ArrayList<>();
                for(int index:indices)polygon.add(new double[]{vertices[index][0],vertices[index][1],vertices[index][2],face<4?uv[index][0]:uv[index][2],face<2?uv[index][2]:uv[index][1]});
                java.util.List<double[]> clipped=new java.util.ArrayList<>();
                for(int j=0;j<polygon.size();j++) {
                    double[] previous=polygon.get((j+polygon.size()-1)%polygon.size()),current=polygon.get(j);
                    boolean before=upper?previous[4]<=.5:previous[4]>=.5,after=upper?current[4]<=.5:current[4]>=.5;
                    if(before!=after){double t=(.5-previous[4])/(current[4]-previous[4]);double[] point=new double[5];for(int k=0;k<5;k++)point[k]=previous[k]+t*(current[k]-previous[k]);clipped.add(point);}
                    if(after)clipped.add(current);
                }
                TextureAtlasSprite part=CustomBlockTextures.sprite(choice,upper);
                for(int j=1;j+1<clipped.size();j++)for(double[] p:new double[][]{clipped.get(0),clipped.get(j),clipped.get(j+1),clipped.get(j+1)})
                    buffer.pos(p[0],p[1],p[2]).color(255,255,255,255).tex(part.getInterpolatedU(p[3]*16),part.getInterpolatedV((upper?p[4]*2:(p[4]-.5)*2)*16))
                        .lightmap(light>>>16,light&65535).normal((float)(nx/length),(float)(ny/length),(float)(nz/length)).endVertex();
            }
        }
    }

    static void drawMesh(BufferBuilder buffer,TextureAtlasSprite sprite,double[][] vertices,double[][] original,int light) {
        drawMesh(buffer,sprite,vertices,original,light,0,6);
    }
    private static void drawMesh(BufferBuilder buffer,TextureAtlasSprite sprite,double[][] vertices,double[][] original,int light,int first,int end) {
        for(int face=first;face<end;face++) {
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
