package com.vandorlabs.client;

import com.vandorlabs.blocks.BlockProgrammableCornerGlass;
import com.vandorlabs.render.CornerGlassGeometry;
import com.vandorlabs.tiles.GlassMaterials;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;

/** Reuse the ordinary glass atlas and frame mesh on the beveled surface. */
final class CornerGlassRenderer {
    private static final GlassSurfaceGroups.Layout UNIT=new GlassSurfaceGroups.Layout(0,0,1,1);
    private static final Map<java.util.List<?>,StaticSurfaceMesh> MESHES=new LinkedHashMap<java.util.List<?>,StaticSurfaceMesh>(128,.75F,true){
        @Override protected boolean removeEldestEntry(Map.Entry<java.util.List<?>,StaticSurfaceMesh> entry){return size()>128;}
    };
    private CornerGlassRenderer(){}
    private static StaticSurfaceMesh project(StaticSurfaceMesh source,CornerGlassGeometry geometry){
        return MESHES.computeIfAbsent(java.util.Arrays.asList(source,geometry),key->source.cornerGlass(geometry));
    }
    static void render(TileEntityProgrammableGlass tile,BlockProgrammableCornerGlass block,IBlockState state,double x,double y,double z){
        int pass=MinecraftForgeClient.getRenderPass();
        boolean opaque=GlassMaterials.opaque(tile.getMaterial(),tile.getTint());
        boolean pane=(pass<0||pass==(opaque?0:1))&&!GlassPanePolicy.omitPane(opaque,VehicleRenderPass.shadow());
        boolean frame=tile.isFramed()&&pass!=1;
        if(pass==1&&tile.fluids.any())WaterloggedRenderer.render(tile,x,y,z);
        if(!pane&&!frame)return;
        Minecraft mc=Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x,y,z);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.color(1,1,1,1);
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        CornerGlassGeometry geometry=block.geometry(state);
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0);
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();
        if(frame){
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            int mask=tile.frameAppearance.tile?15:block.frameMask(tile.getWorld(),tile.getPos(),state);
            buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
            project(GlassFrameMesh.get(tile,UNIT,mask),geometry).draw(buffer,light>>>16,tile.frameAppearance.light()>0?240:light&65535);
            Tessellator.getInstance().draw();
        }
        if(pane){
            String texture=GlassMaterials.texture(tile.getMaterial(),tile.getShade(),tile.getSize(),tile.getTint());
            StaticSurfaceMesh source=GlassSurfaceMapping.map(TEProgrammableGlass.materialPlane(),mc.getTextureMapBlocks().getAtlasSprite(texture),
                    UNIT,true,0,0,tile.getMaterial()<0?0:tile.getTextureLayout());
            if(opaque)GlStateManager.disableBlend();else GlStateManager.enableBlend();
            GlStateManager.depthMask(opaque);
            GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);
            buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());
            GlazingDisplay.draw(project(source,geometry),tile,buffer,light>>>16,GlassInsertLighting.block(tile,light&65535),
                    GlassMaterials.opacity(tile.getMaterial(),tile.getTint()));
            Tessellator.getInstance().draw();
        }
        GlStateManager.depthMask(true);
        GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }
}
