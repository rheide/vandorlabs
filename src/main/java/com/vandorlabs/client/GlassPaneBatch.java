package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;

/** Consecutive atlas-backed glass panes share a draw without changing their sorted order.
 * Corners, fluids and animated displays remain in their existing renderer. */
final class GlassPaneBatch implements AutoCloseable {
    static int panes,draws;
    private boolean drawing;
    private IBlockState batchState;
    private int batchShaderKey=GlassShaderMaterial.UNAVAILABLE;
    private final BufferBuilder buffer=Tessellator.getInstance().getBuffer();
    private static final java.lang.reflect.Method SHADER_TILE=shaderTile();
    private static final GlassShaderMaterial SHADER_MATERIAL=GlassShaderMaterial.detect();
    private static java.lang.reflect.Method shaderTile(){
        try{return Class.forName("net.optifine.shaders.Shaders").getMethod("nextBlockEntity",TileEntity.class);}
        catch(ClassNotFoundException absent){return null;}
        catch(ReflectiveOperationException|LinkageError incompatible){com.vandorlabs.VandorLabs.logger.warn("Glass batching requires a compatible shader material hook",incompatible);return null;}
    }
    boolean append(TileEntity raw,double x,double y,double z){
        return append(raw,x,y,z,null);
    }
    boolean append(TileEntity raw,double x,double y,double z,GlassPanePreparation prepared){
        if(!(raw instanceof TileEntityProgrammableGlass))return false;
        boolean shaderFormat=net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK.getNextOffset()>32;
        if(shaderFormat&&SHADER_TILE==null)return false;
        TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)raw;
        if(!GlazingDisplay.atlasStatic(tile)||GlassMaterials.opaque(tile.getMaterial(),tile.getTint())||tile.fluids.any())return false;
        IBlockState state=GlassPanePreparation.flatState(tile,prepared);
        if(state==null)return false;
        // Different metadata may resolve to the same shader material. G5 exposes
        // the exact blockEntityId helper; keep full-state separation as fallback.
        int shaderKey=shaderFormat?SHADER_MATERIAL.key(tile):GlassShaderMaterial.UNAVAILABLE;
        if(drawing&&shaderFormat&&GlassShaderMaterial.split(batchState,batchShaderKey,state,shaderKey))flush();
        if(!drawing){
            // The dispatcher normally selects the block-entity shader material.
            // A batch bypasses its per-tile calls; select the same glass context once.
            if(shaderFormat)try{SHADER_TILE.invoke(null,tile);}catch(ReflectiveOperationException error){com.vandorlabs.VandorLabs.logger.warn("Glass batching could not select shader material",error);return false;}
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);GlStateManager.depthMask(false);GlStateManager.color(1,1,1,1);
            buffer.setTranslation(0,0,0);buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());drawing=true;batchState=state;batchShaderKey=shaderKey;
        }
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0),block=GlassInsertLighting.block(tile,light&65535),sky=light>>>16;
        boolean rotated=state.getValue(BlockGlassWall.ROTATED);int depth=state.getValue(BlockGlassWall.DEPTH);
        if(tile.getMaterial()>=0){
            TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassMaterials.texture(tile.getMaterial(),tile.getShade(),tile.getSize(),tile.getTint()));
            float opacity=GlassMaterials.opacity(tile.getMaterial(),tile.getTint());double offset=PanelDepth.offset(depth)/16D;
            StaticSurfaceMesh mesh=GlassSurfaceMapping.map(TEProgrammableGlass.materialPlane(),sprite,GlassSurfaceGroups.layout(tile),true,0,0,tile.getTextureLayout());
            // The old TESR applies depth before its -90-degree Y rotation.
            mesh.drawPlacedColored(buffer,x-(rotated?offset:0),y,z+(rotated?0:offset),rotated?3:0,(sky<<16)|block,opacity);
            panes++;return true;
        }
        GlassNativePanes.get(tile.getShade(),tile.getSize(),tile.getTint(),rotated,depth).draw(buffer,x,y,z,sky,block);
        panes++;return true;
    }
    void flush(){
        if(!drawing)return;
        Tessellator.getInstance().draw();drawing=false;batchState=null;batchShaderKey=GlassShaderMaterial.UNAVAILABLE;draws++;buffer.setTranslation(0,0,0);
        GlStateManager.enableCull();GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableBlend();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
    }
    @Override public void close(){flush();}
}
