package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import org.lwjgl.opengl.GL11;

public class TEProgrammableGlass extends TileEntitySpecialRenderer<TileEntityProgrammableGlass> {
    private static final java.util.Map<CornerKey,CornerMeshes> CORNERS=new java.util.LinkedHashMap<CornerKey,CornerMeshes>(128,.75F,true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<CornerKey,CornerMeshes> entry){return size()>128;}
    };
    private static final class CornerKey {
        final net.minecraft.client.renderer.texture.TextureAtlasSprite glass,rail,inner,outer;
        final double near,left,right;
        final Double front,back;
        CornerKey(net.minecraft.client.renderer.texture.TextureAtlasSprite glass,net.minecraft.client.renderer.texture.TextureAtlasSprite rail,net.minecraft.client.renderer.texture.TextureAtlasSprite inner,net.minecraft.client.renderer.texture.TextureAtlasSprite outer,
                double near,com.vandorlabs.blocks.BlockProgrammableWall.FlatCorner corner) {
            this.glass=glass;this.rail=rail;this.inner=inner;this.outer=outer;this.near=near;left=corner==null?0:corner.left();right=corner==null?16:corner.right();front=corner==null?null:corner.frontArm;back=corner==null?null:corner.backArm;
        }
        @Override public int hashCode(){return java.util.Objects.hash(System.identityHashCode(glass),System.identityHashCode(rail),System.identityHashCode(inner),System.identityHashCode(outer),near,left,right,front,back);}
        @Override public boolean equals(Object other) {
            if(!(other instanceof CornerKey))return false;
            CornerKey k=(CornerKey)other;
            return glass==k.glass && rail==k.rail && inner==k.inner && outer==k.outer && near==k.near && left==k.left && right==k.right
                    && java.util.Objects.equals(front,k.front) && java.util.Objects.equals(back,k.back);
        }
    }
    static final class CornerMeshes {
        final StaticSurfaceMesh pane,frame;
        CornerMeshes(StaticSurfaceMesh pane,StaticSurfaceMesh frame){this.pane=pane;this.frame=frame;}
    }
    private static final double PANE_NEAR = 7D / 16D;
    private static final double PANE_FAR = 9D / 16D;
    private static StaticSurfaceMesh materialPlane;
    private static StaticSurfaceMesh materialPlane(){
        if(materialPlane==null){StaticSurfaceMesh.Capture buffer=StaticSurfaceMesh.capture();
            for(boolean near:new boolean[]{true,false}){
                double z=(near?PANE_NEAR+.0001:PANE_FAR-.0001)*16;int[] order=near?new int[]{0,3,2,1}:new int[]{0,1,2,3};
                for(int index:order){double px=index==1 || index==2?16:0,py=index<2?0:16;buffer.pos(px,py,z).tex(px/16,1-py/16).normal(0,0,near?-1:1).endVertex();}
            }materialPlane=buffer.finish();
        }return materialPlane;
    }
    @Override public void render(TileEntityProgrammableGlass tile, double x, double y,
            double z, float partial, int stage, float alpha) {
        if (tile.getWorld() == null) return;
        if (!(tile.getWorld().getBlockState(tile.getPos()).getBlock()
                instanceof com.vandorlabs.blocks.BlockProgrammableGlass)) return;
        if(tile.fluids.any()&&net.minecraftforge.client.MinecraftForgeClient.getRenderPass()==1)WaterloggedRenderer.render(tile,x,y,z);
        int size = tile.getSize();
        net.minecraft.block.state.IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        com.vandorlabs.blocks.BlockProgrammableWall.FlatCorner corner=com.vandorlabs.blocks.PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos());
        if(tile.isFramed() && (tile.frameAppearance.front>=0||tile.frameAppearance.interior>=0||tile.frameAppearance.exteriorChoice()>=0||tile.frameAppearance.tile) && corner==null && net.minecraftforge.client.MinecraftForgeClient.getRenderPass()!=1)renderSelectedFrame(tile,state,x,y,z);
        if(FlatPanelBarRenderer.render(tile,state,x,y,z))return;
        if(corner!=null) { renderCorner(tile,state,corner,x,y,z);return; }
        boolean opaque=com.vandorlabs.tiles.GlassMaterials.opaque(tile.getMaterial(),tile.getTint());
        int pass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        if(pass>=0&&pass!=(opaque?0:1))return;
        net.minecraft.client.renderer.texture.TextureAtlasSprite material=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(
                com.vandorlabs.tiles.GlassMaterials.texture(tile.getMaterial(),tile.getShade(),size,tile.getTint()));
        bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
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
        if(opaque)GlStateManager.disableBlend();else GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);
        float opacity=com.vandorlabs.tiles.GlassMaterials.opacity(tile.getMaterial(),tile.getTint());
        GlStateManager.depthMask(opaque);
        GlStateManager.disableCull();
        GlStateManager.color(1F, 1F, 1F, 1F);
        BufferBuilder b=Tessellator.getInstance().getBuffer();
        b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
        double inset=tile.getMaterial()<0?0:.0001;
        if(tile.getMaterial()<0){pane(b,PANE_NEAR+inset,1,1,1,opacity,material,new GlassSurfaceGroups.Layout(0,0,1,1));pane(b,PANE_FAR-inset,1,1,1,opacity,material,new GlassSurfaceGroups.Layout(0,0,1,1));}
        else{GlStateManager.scale(1F/16,1F/16,1F/16);GlassSurfaceMapping.map(materialPlane(),material,GlassSurfaceGroups.layout(tile),true,0,0,tile.getTextureLayout()).drawColored(b,(int)OpenGlHelper.lastBrightnessY,(int)OpenGlHelper.lastBrightnessX,1,1,1,opacity);}
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

    private void renderSelectedFrame(TileEntityProgrammableGlass tile,net.minecraft.block.state.IBlockState state,double x,double y,double z){
        bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlassSurfaceGroups.Layout layout=tile.frameAppearance.tile?new GlassSurfaceGroups.Layout(0,0,1,1):GlassSurfaceGroups.frameLayout(tile);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);
        if(state.getValue(BlockGlassWall.ROTATED)){GlStateManager.translate(.5,0,.5);GlStateManager.rotate(-90,0,1,0);GlStateManager.translate(-.5,0,-.5);}
        GlStateManager.translate(0,0,com.vandorlabs.blocks.PanelDepth.offset(state.getValue(BlockGlassWall.DEPTH))/16D);
        float savedBlockLight=OpenGlHelper.lastBrightnessX,savedSkyLight=OpenGlHelper.lastBrightnessY;
        int light=tile.getWorld().getCombinedLight(tile.getPos(),tile.frameAppearance.light());if(tile.frameAppearance.light()>0)light=(light&0xffff0000)|240;OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,light%65536,light/65536);
        GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.scale(1D/16,1D/16,1D/16);
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
        GlassFrameMesh.get(tile,layout).draw(b,light/65536,light%65536);Tessellator.getInstance().draw();
        GlStateManager.disableBlend();GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.popMatrix();OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,savedBlockLight,savedSkyLight);
    }

    private void renderCorner(TileEntityProgrammableGlass tile,net.minecraft.block.state.IBlockState state,
            com.vandorlabs.blocks.BlockProgrammableWall.FlatCorner corner,double x,double y,double z) {
        CornerMeshes meshes=meshes(tile,state,corner);
        StaticSurfaceMesh mesh=meshes.pane;
        if(tile.getMaterial()>=0){
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(com.vandorlabs.tiles.ScreenHousingTextures.texture(tile.getMaterial()));
            GlassSurfaceGroups.Layout layout=GlassSurfaceGroups.layout(tile);
            if(com.vandorlabs.blocks.PanelJoins.flatFacing(state,tile.getWorld(),tile.getPos())!=com.vandorlabs.blocks.PanelJoins.facing(state))layout=new GlassSurfaceGroups.Layout(layout.columns-1-layout.column,layout.row,layout.columns,layout.rows);
            mesh=GlassSurfaceMapping.map(mesh,sprite,layout,false,0,0,tile.getTextureLayout());
        }
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0),sky=light/65536,block=light%65536;
        bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);
        net.minecraft.util.EnumFacing facing=com.vandorlabs.blocks.PanelJoins.flatFacing(state,tile.getWorld(),tile.getPos());
        GlStateManager.translate(.5,0,.5);GlStateManager.rotate(180-facing.getHorizontalAngle(),0,1,0);GlStateManager.translate(-.5,0,-.5);
        GlStateManager.scale(1D/16,1D/16,1D/16);
        GlStateManager.disableLighting();GlStateManager.disableCull();
        GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();
        int pass=net.minecraftforge.client.MinecraftForgeClient.getRenderPass();
        boolean opaque=com.vandorlabs.tiles.GlassMaterials.opaque(tile.getMaterial(),tile.getTint());
        if(tile.isFramed() && corner!=null && pass!=1) {
            // Opaque rails own depth before translucent panes and world water.
            GlStateManager.disableBlend();GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
            StaticSurfaceMesh frame=meshes.frame;
            buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());frame.draw(buffer,sky,block);Tessellator.getInstance().draw();
        }
        if(opaque)GlStateManager.disableBlend();else GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        float opacity=com.vandorlabs.tiles.GlassMaterials.opacity(tile.getMaterial(),tile.getTint());
        GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);GlStateManager.depthMask(opaque);
        if(pass<0||pass==(opaque?0:1)){buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());mesh.drawColored(buffer,sky,block,1,1,1,opacity);Tessellator.getInstance().draw();}
        GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableBlend();GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.popMatrix();
    }

    static CornerMeshes meshes(TileEntityProgrammableGlass tile,net.minecraft.block.state.IBlockState state,
            com.vandorlabs.blocks.BlockProgrammableWall.FlatCorner corner) {
        net.minecraft.client.renderer.texture.TextureMap atlas=net.minecraft.client.Minecraft.getMinecraft().getTextureMapBlocks();
        net.minecraft.client.renderer.texture.TextureAtlasSprite glass=atlas.getAtlasSprite(com.vandorlabs.tiles.GlassMaterials.texture(tile.getMaterial(),tile.getShade(),tile.getSize(),tile.getTint()));
        net.minecraft.client.renderer.texture.TextureAtlasSprite rail=atlas.getAtlasSprite(tile.frameAppearance.rail("vandorlabs:blocks/space_doors/medium/double_frame_metal"));
        CornerKey key=new CornerKey(glass,rail,atlas.getAtlasSprite(tile.frameAppearance.interior("vandorlabs:blocks/programmable_glass/metal_side")),atlas.getAtlasSprite(tile.frameAppearance.exterior("vandorlabs:blocks/programmable_glass/metal_side")),com.vandorlabs.blocks.PanelJoins.flatNear(state,tile.getWorld(),tile.getPos()),corner);
        CornerMeshes meshes=CORNERS.get(key);
        if(meshes==null) {
            StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();
            TEAnimatedScreenSelector.renderFlatWall(capture,glass,glass,key.near,corner);
            StaticSurfaceMesh pane=capture.finish();
            meshes=new CornerMeshes(pane,pane.outlineFlatFrame(rail,glass,key.inner,key.outer));CORNERS.put(key,meshes);
        }
        return meshes;
    }

    static void pane(BufferBuilder buffer,double depth,float r,float g,float b,float a) {
        pane(buffer,depth,r,g,b,a,null,new GlassSurfaceGroups.Layout(0,0,1,1));
    }
    private static void pane(BufferBuilder buffer,double depth,float r,float g,float b,float a,net.minecraft.client.renderer.texture.TextureAtlasSprite material,GlassSurfaceGroups.Layout layout) {
        boolean near=depth<.5;
        for(int i=0;i<4;i++) {
            int index=near?(4-i)&3:i;
            double x=index==1 || index==2?1:0,y=index<2?0:1;
            double u=(layout.column+x)/layout.columns,v=(layout.rows-layout.row-y)/layout.rows;
            buffer.pos(x,y,depth).color(r,g,b,a).tex(material==null?x:material.getInterpolatedU(u*16),material==null?1-y:material.getInterpolatedV(v*16))
                    .lightmap((int)OpenGlHelper.lastBrightnessY,(int)OpenGlHelper.lastBrightnessX)
                    .normal(0,0,near?-1:1).endVertex();
        }
    }
}
