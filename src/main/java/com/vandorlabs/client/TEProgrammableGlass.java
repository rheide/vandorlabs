package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TEProgrammableGlass extends TileEntitySpecialRenderer<TileEntityProgrammableGlass> {
    private static final java.util.Map<CornerKey,CornerMeshes> CORNERS=new java.util.LinkedHashMap<CornerKey,CornerMeshes>(128,.75F,true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<CornerKey,CornerMeshes> entry){return size()>128;}
    };
    private static final class CornerKey {
        final net.minecraft.client.renderer.texture.TextureAtlasSprite glass,rail;
        final double near,left,right;
        final Double front,back;
        CornerKey(net.minecraft.client.renderer.texture.TextureAtlasSprite glass,net.minecraft.client.renderer.texture.TextureAtlasSprite rail,
                double near,com.vandorlabs.blocks.BlockProgrammableWall.FlatCorner corner) {
            this.glass=glass;this.rail=rail;this.near=near;left=corner==null?0:corner.left();right=corner==null?16:corner.right();front=corner==null?null:corner.frontArm;back=corner==null?null:corner.backArm;
        }
        @Override public int hashCode(){return java.util.Objects.hash(System.identityHashCode(glass),System.identityHashCode(rail),near,left,right,front,back);}
        @Override public boolean equals(Object other) {
            if(!(other instanceof CornerKey))return false;
            CornerKey k=(CornerKey)other;
            return glass==k.glass && rail==k.rail && near==k.near && left==k.left && right==k.right
                    && java.util.Objects.equals(front,k.front) && java.util.Objects.equals(back,k.back);
        }
    }
    static final class CornerMeshes {
        final StaticSurfaceMesh pane,frame;
        CornerMeshes(StaticSurfaceMesh pane,StaticSurfaceMesh frame){this.pane=pane;this.frame=frame;}
    }
    private static final double PANE_NEAR = 7D / 16D;
    private static final double PANE_FAR = 9D / 16D;
    @Override public void render(TileEntityProgrammableGlass tile, double x, double y,
            double z, float partial, int stage, float alpha) {
        if (tile.getWorld() == null) return;
        if (!(tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof com.vandorlabs.blocks.BlockProgrammableGlass)) return;
        int size = tile.getSize();
        net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        com.vandorlabs.blocks.BlockProgrammableWall.FlatCorner corner=com.vandorlabs.blocks.PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos());
        if(FlatPanelBarRenderer.render(tile,state,x,y,z))return;
        if(corner!=null) { renderCorner(tile,state,corner,x,y,z);return; }
        bindTexture(new ResourceLocation(com.vandorlabs.tiles.GlassTextures.fullTexture(tile.getShade(),size,tile.getTint())));
        int light = tile.getWorld().getCombinedLight(tile.getPos(), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light%65536,light/65536);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x,y,z);
        if (tile.getWorld().getBlockState(tile.getPos()).getValue(BlockGlassWall.ROTATED)) {
            GlStateManager.translate(.5,0,.5);
            GlStateManager.rotate(-90,0,1,0);
            GlStateManager.translate(-.5,0,-.5);
        }
        int depth = tile.getWorld().getBlockState(tile.getPos()).getValue(BlockGlassWall.DEPTH);
        GlStateManager.translate(0, 0, com.vandorlabs.blocks.PanelDepth.offset(depth) / 16D);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.color(1F, 1F, 1F, 1F);
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
        pane(b,PANE_NEAR,1,1,1,1);
        pane(b,PANE_FAR,1,1,1,1);
        Tessellator.getInstance().draw();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.popMatrix();
        bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    private void renderCorner(TileEntityProgrammableGlass tile,net.minecraft.block.state.IBlockState state,
            com.vandorlabs.blocks.BlockProgrammableWall.FlatCorner corner,double x,double y,double z) {
        CornerMeshes meshes=meshes(tile,state,corner);
        StaticSurfaceMesh mesh=meshes.pane;
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0),sky=light/65536,block=light%65536;
        bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);
        net.minecraft.util.EnumFacing facing=com.vandorlabs.blocks.PanelJoins.flatFacing(state,tile.getWorld(),tile.getPos());
        GlStateManager.translate(.5,0,.5);GlStateManager.rotate(180-facing.getHorizontalAngle(),0,1,0);GlStateManager.translate(-.5,0,-.5);
        GlStateManager.scale(1D/16,1D/16,1D/16);
        GlStateManager.disableLighting();GlStateManager.disableCull();
        GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();
        if(tile.isFramed()) {
            // Tile pass one enters with translucent state; metal must own depth
            // before the pane is blended over the surrounding clear area.
            GlStateManager.disableBlend();GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
            StaticSurfaceMesh frame=meshes.frame;
            buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());frame.draw(buffer,sky,block);Tessellator.getInstance().draw();
        }
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);GlStateManager.depthMask(false);
        buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());mesh.draw(buffer,sky,block);Tessellator.getInstance().draw();
        GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableBlend();GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.popMatrix();
    }

    static CornerMeshes meshes(TileEntityProgrammableGlass tile,net.minecraft.block.state.IBlockState state,
            com.vandorlabs.blocks.BlockProgrammableWall.FlatCorner corner) {
        net.minecraft.client.renderer.texture.TextureMap atlas=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks();
        net.minecraft.client.renderer.texture.TextureAtlasSprite glass=atlas.getAtlasSprite(com.vandorlabs.tiles.GlassTextures.texture(tile.getShade(),tile.getSize(),tile.getTint()));
        net.minecraft.client.renderer.texture.TextureAtlasSprite rail=atlas.getAtlasSprite("vandorlabs:blocks/space_doors/medium/double_frame_metal");
        CornerKey key=new CornerKey(glass,rail,com.vandorlabs.blocks.PanelJoins.flatNear(state,tile.getWorld(),tile.getPos()),corner);
        CornerMeshes meshes=CORNERS.get(key);
        if(meshes==null) {
            StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
            TEAnimatedScreenSelector.renderFlatWall(capture,glass,glass,key.near,corner);
            StaticSurfaceMesh pane=capture.finish();
            meshes=new CornerMeshes(pane,pane.outlineFlatFrame(rail));CORNERS.put(key,meshes);
        }
        return meshes;
    }

    static void pane(BufferBuilder buffer,double depth,float r,float g,float b,float a) {
        boolean near=depth==PANE_NEAR;
        for(int i=0;i<4;i++) {
            int index=near?(4-i)&3:i;
            double x=index==1 || index==2?1:0,y=index<2?0:1;
            buffer.pos(x,y,depth).color(r,g,b,a).tex(x,1-y)
                    .lightmap((int)OpenGlHelper.lastBrightnessY,(int)OpenGlHelper.lastBrightnessX)
                    .normal(0,0,near?-1:1).endVertex();
        }
    }
}
