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
    private final BufferBuilder buffer=Tessellator.getInstance().getBuffer();
    private static final java.lang.reflect.Method SHADER_TILE=shaderTile();
    private static java.lang.reflect.Method shaderTile(){
        try{return Class.forName("net.optifine.shaders.Shaders").getMethod("nextBlockEntity",TileEntity.class);}
        catch(ClassNotFoundException absent){return null;}
        catch(ReflectiveOperationException|LinkageError incompatible){com.vandorlabs.VandorLabs.logger.warn("Glass batching requires a compatible shader material hook",incompatible);return null;}
    }
    boolean append(TileEntity raw,double x,double y,double z){
        if(!(raw instanceof TileEntityProgrammableGlass))return false;
        boolean shaderFormat=net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK.getNextOffset()>32;
        if(shaderFormat&&SHADER_TILE==null)return false;
        TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)raw;
        if(!GlazingDisplay.atlasStatic(tile)||GlassMaterials.opaque(tile.getMaterial(),tile.getTint())||tile.fluids.any())return false;
        IBlockState state=tile.getWorld().getBlockState(tile.getPos());
        if(!(state.getBlock() instanceof BlockProgrammableGlass)||PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos())!=null)return false;
        // OptiFine block aliases can depend on metadata. Keep the correct entity
        // payload for each state even when adjacent sorted panes use one atlas.
        if(drawing&&shaderFormat&&batchState!=state)flush();
        if(!drawing){
            // The dispatcher normally selects the block-entity shader material.
            // A batch bypasses its per-tile calls; select the same glass context once.
            if(shaderFormat)try{SHADER_TILE.invoke(null,tile);}catch(ReflectiveOperationException error){com.vandorlabs.VandorLabs.logger.warn("Glass batching could not select shader material",error);return false;}
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.disableLighting();GlStateManager.disableCull();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);GlStateManager.depthMask(false);GlStateManager.color(1,1,1,1);
            buffer.setTranslation(0,0,0);buffer.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());drawing=true;batchState=state;
        }
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassMaterials.texture(tile.getMaterial(),tile.getShade(),tile.getSize(),tile.getTint()));
        int light=tile.getWorld().getCombinedLight(tile.getPos(),0),block=GlassInsertLighting.block(tile,light&65535),sky=light>>>16;
        float opacity=GlassMaterials.opacity(tile.getMaterial(),tile.getTint());boolean rotated=state.getValue(BlockGlassWall.ROTATED);
        double offset=PanelDepth.offset(state.getValue(BlockGlassWall.DEPTH))/16D;
        if(tile.getMaterial()>=0){
            StaticSurfaceMesh mesh=GlassSurfaceMapping.map(TEProgrammableGlass.materialPlane(),sprite,GlassSurfaceGroups.layout(tile),true,0,0,tile.getTextureLayout());
            // The old TESR applies depth before its -90-degree Y rotation.
            mesh.drawPlacedColored(buffer,x-(rotated?offset:0),y,z+(rotated?0:offset),rotated?3:0,(sky<<16)|block,opacity);
            panes++;return true;
        }
        for(int face=0;face<2;face++)for(int i=0;i<4;i++){
            boolean near=face==0;
            int index=near?(4-i)&3:i;double px=index==1||index==2?1:0,py=index<2?0:1,depth=(near?7D/16:9D/16)+offset;
            buffer.pos(x+(rotated?1-depth:px),y+py,z+(rotated?px:depth)).color(1,1,1,opacity).tex(sprite.getInterpolatedU(px*16),sprite.getInterpolatedV((1-py)*16)).lightmap(sky,block).normal(rotated?(near?1:-1):0,0,rotated?0:(near?-1:1)).endVertex();
        }
        panes++;return true;
    }
    void flush(){
        if(!drawing)return;
        Tessellator.getInstance().draw();drawing=false;batchState=null;draws++;buffer.setTranslation(0,0,0);
        GlStateManager.enableCull();GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableBlend();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
    }
    @Override public void close(){flush();}
}
