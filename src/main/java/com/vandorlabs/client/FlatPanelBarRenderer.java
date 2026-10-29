package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** An aligned T is rendered entirely with its center tile appearance, including the continuous bar. */
final class FlatPanelBarRenderer {
    // Pure geometry only. Source mesh identities change with sprites, settings and geometry invalidation.
    private static final Map<Key,StaticSurfaceMesh> PARTS=new LinkedHashMap<Key,StaticSurfaceMesh>(256,.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key,StaticSurfaceMesh> e){return size()>256;}
    };
    private static final class Key {
        final StaticSurfaceMesh source;final int turns,half;final double near,depth,left,width,padding;
        Key(StaticSurfaceMesh source,int turns,int half,double near,double depth,double left,double width,double padding){this.source=source;this.turns=turns;this.half=half;this.near=near;this.depth=depth;this.left=left;this.width=width;this.padding=padding;}
        @Override public int hashCode(){return Objects.hash(System.identityHashCode(source),turns,half,near,depth,left,width,padding);}
        @Override public boolean equals(Object other){if(!(other instanceof Key))return false;Key k=(Key)other;return source==k.source && turns==k.turns && half==k.half && near==k.near && depth==k.depth && left==k.left && width==k.width && padding==k.padding;}
    }
    static final class Material {
        float opacity=1;boolean solidPane;
        final StaticSurfaceMesh opaque,transparent;final TextureAtlasSprite cap;final double depth,left,width;
        Material(StaticSurfaceMesh opaque,StaticSurfaceMesh transparent,double depth,double left,double width,TextureAtlasSprite cap) {
            this.opaque=opaque;this.transparent=transparent;this.cap=cap;this.depth=depth;this.left=left;this.width=width;
        }
    }
    static Material material(TileEntity tile,IBlockState state) {
        if(tile instanceof TileEntityProgrammableGlass) {
            TileEntityProgrammableGlass glass=(TileEntityProgrammableGlass)tile;
            TEProgrammableGlass.CornerMeshes m=TEProgrammableGlass.meshes(glass,state,PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos()));
            Material result=new Material(glass.isFramed()?m.frame:null,glassMesh(glass,state,m.pane),0,0,1,null);result.opacity=GlassMaterials.opacity(glass.getMaterial(),glass.getTint());result.solidPane=GlassMaterials.opaque(glass.getMaterial(),glass.getTint());return result;
        }
        if(!(tile instanceof TileEntityAnimatedScreenSelector))return null;
        TileEntityAnimatedScreenSelector wall=(TileEntityAnimatedScreenSelector)tile;
        BlockProgrammableWall block=(BlockProgrammableWall)state.getBlock();
        TextureAtlasSprite face=TEAnimatedScreenSelector.wallSprite(wall);
        TextureAtlasSprite rim=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(wall.frameAppearance.interior(block.isPortholeShape() && wall.getSideTexture()>=0
                ?ScreenHousingTextures.texture(wall.getSideTexture()):"vandorlabs:blocks/programmable_glass/metal_side"));
        PortholeHex.Slice slice=block.isPortholeShape()?TEAnimatedScreenSelector.portholeGroup(wall,state).slice(tile.getPos(),state.getValue(BlockProgrammableWall.FACING).rotateY(),EnumFacing.UP):null;
        StaticSurfaceMesh opaque=DiagonalWallMeshCache.get(wall,state,face,rim,slice);
        StaticSurfaceMesh pane=slice==null?null:DiagonalWallMeshCache.glass(wall,state,face,rim,slice);
        BlockProgrammableWall.FlatCorner corner=PanelJoins.flatCorner(state,tile.getWorld(),tile.getPos());
        double depth=block.isPortholeShape() || corner==null?PanelDepth.offset(state.getValue(BlockProgrammableWall.DEPTH)):0;
        if(pane!=null)pane=PortholePane.mesh(wall,state,pane);
        Material result=new Material(opaque,pane,depth,block.isPortholeShape() && corner!=null?corner.left():0,block.isPortholeShape() && corner!=null?(corner.right()-corner.left())/16:1,rim);
        result.opacity=GlassMaterials.opacity(wall.getGlassMaterial(),wall.getGlassTint());result.solidPane=GlassMaterials.opaque(wall.getGlassMaterial(),wall.getGlassTint());return result;
    }
    private static StaticSurfaceMesh glassMesh(TileEntityProgrammableGlass tile,IBlockState state,StaticSurfaceMesh mesh){
        if(tile.getMaterial()<0)return mesh;
        GlassSurfaceGroups.Layout layout=GlassSurfaceGroups.layout(tile);
        if(PanelJoins.flatFacing(state,tile.getWorld(),tile.getPos())!=PanelJoins.facing(state))layout=new GlassSurfaceGroups.Layout(layout.columns-1-layout.column,layout.row,layout.columns,layout.rows);
        TextureAtlasSprite sprite=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(ScreenHousingTextures.texture(tile.getMaterial()));
        return GlassSurfaceMapping.map(mesh,sprite,layout,false,0,0,tile.getTextureLayout());
    }
    private static final class BarKey {
        final TextureAtlasSprite wall,metal,outer;final int shape;
        BarKey(TextureAtlasSprite wall,TextureAtlasSprite metal,TextureAtlasSprite outer,int shape){this.wall=wall;this.metal=metal;this.outer=outer;this.shape=shape;}
        @Override public int hashCode(){return Objects.hash(System.identityHashCode(wall),System.identityHashCode(metal),System.identityHashCode(outer),shape);}
        @Override public boolean equals(Object value){if(!(value instanceof BarKey))return false;BarKey k=(BarKey)value;return wall==k.wall && metal==k.metal && outer==k.outer && shape==k.shape;}
    }
    private static final Map<BarKey,StaticSurfaceMesh[]> BARS=new LinkedHashMap<BarKey,StaticSurfaceMesh[]>(128,.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<BarKey,StaticSurfaceMesh[]> e){return size()>128;}
    };
    static Material barMaterial(TileEntity root,IBlockState state,double near) {
        if(root instanceof TileEntityProgrammableGlass) {
            TileEntityProgrammableGlass glass=(TileEntityProgrammableGlass)root;
            TEProgrammableGlass.CornerMeshes m=TEProgrammableGlass.meshes(glass,state,null);
            Material result=new Material(glass.isFramed()?m.frame:null,glassMesh(glass,state,m.pane),near-PanelJoins.flatNear(state,root.getWorld(),root.getPos()),0,1,null);result.opacity=GlassMaterials.opacity(glass.getMaterial(),glass.getTint());result.solidPane=GlassMaterials.opaque(glass.getMaterial(),glass.getTint());return result;
        }
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)root;
        BlockProgrammableWall block=(BlockProgrammableWall)state.getBlock();
        TextureAtlasSprite wall=TEAnimatedScreenSelector.wallSprite(tile);
        TextureAtlasSprite metal=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(tile.frameAppearance.interior(block.isPortholeShape() && tile.getSideTexture()>=0
                ?ScreenHousingTextures.texture(tile.getSideTexture()):"vandorlabs:blocks/programmable_glass/metal_side"));
        TextureAtlasSprite outer=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(tile.frameAppearance.exterior(block.isPortholeShape()&&tile.getSideTexture()>=0?ScreenHousingTextures.texture(tile.getSideTexture()):"vandorlabs:blocks/programmable_glass/metal_side"));
        if(block.isPortholeShape()&&tile.frameAppearance.front>=0)wall=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(tile.frameAppearance.front(""));
        BarKey key=new BarKey(wall,metal,outer,block.isPortholeShape()?tile.getPortholeShape():-1);
        StaticSurfaceMesh[] meshes=BARS.get(key);
        if(meshes==null) {
            PortholeHex.Slice slice=key.shape<0?null:new PortholeHex(1,1,key.shape).slice(0,0);
            StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawFlatBar(capture,wall,metal,outer,slice);
            StaticSurfaceMesh opaque=capture.finish(),pane=null;
            if(slice!=null){capture=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawPortholeGlassGeometry(capture,slice,null);pane=capture.finish();}
            meshes=new StaticSurfaceMesh[]{opaque,pane};BARS.put(key,meshes);
        }
        StaticSurfaceMesh pane=meshes[1]==null?null:PortholePane.mesh(tile,state,meshes[1]);
        Material result=new Material(meshes[0],pane,near-6,0,1,metal);result.opacity=GlassMaterials.opacity(tile.getGlassMaterial(),tile.getGlassTint());result.solidPane=GlassMaterials.opaque(tile.getGlassMaterial(),tile.getGlassTint());return result;
    }
    static StaticSurfaceMesh part(StaticSurfaceMesh source,int turns,int half,double near,double depth,double left,double width) {
        return part(source,turns,half,near,depth,left,width,0);
    }
    static StaticSurfaceMesh part(StaticSurfaceMesh source,int turns,int half,double near,double depth,double left,double width,double padding) {
        if(source==null)return null;
        Key key=new Key(source,turns,half,near,depth,left,width,padding);StaticSurfaceMesh mesh=PARTS.get(key);
        if(mesh==null) {
            mesh=source.panelTransform(turns,depth,left,width).box(near-padding,-1,-1,near+4+padding,17,17,half==0);
            PARTS.put(key,mesh);
        }
        return mesh;
    }
    static StaticSurfaceMesh stemCap(Material material,double cut,double near,boolean right) {
        if(material.cap==null)return null;
        Key key=new Key(material.opaque,0,right?3:4,cut,material.depth,material.left,material.width,near);
        StaticSurfaceMesh cap=PARTS.get(key);
        if(cap==null) {
            cap=material.opaque.panelTransform(0,material.depth,material.left,material.width).panelEnd(cut,near,material.cap,right);
            PARTS.put(key,cap);
        }
        return cap;
    }
    static boolean render(TileEntity root,IBlockState state,double x,double y,double z) {
        BlockProgrammableWall.FlatCorner bar=PanelJoins.flatBar(state,root.getWorld(),root.getPos());
        if(bar==null)return false;
        EnumFacing facing=PanelJoins.flatFacing(state,root.getWorld(),root.getPos());
        Material[] materials={material(root,state),barMaterial(root,state,bar.frontArm)};
        int[] turns={0,3};
        if(materials[0]==null || materials[1]==null)return false;
        int light=root.getWorld().getCombinedLight(root.getPos(),0),sky=light/65536,block=light%65536;
        boolean transparent=net.minecraftforge.client.MinecraftForgeClient.getRenderPass()==1;
        GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);
        GlStateManager.translate(.5,0,.5);GlStateManager.rotate(180-facing.getHorizontalAngle(),0,1,0);GlStateManager.translate(-.5,0,-.5);
        GlStateManager.scale(1D/16,1D/16,1D/16);GlStateManager.disableLighting();GlStateManager.disableCull();
        GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
        if(transparent) {
            GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
            GlStateManager.alphaFunc(GL11.GL_GREATER,.003F);GlStateManager.depthMask(false);
        } else {GlStateManager.disableBlend();GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);}
        for(int i=0;i<2;i++) {
            Material m=materials[i];StaticSurfaceMesh mesh=part(transparent?m.transparent:m.opaque,turns[i],i==0?0:5,bar.frontArm,m.depth,m.left,m.width,!transparent && m.cap==null?.02:0);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            if(transparent){
                if(!m.solidPane&&mesh!=null&&mesh.vertexCount()>0){GlStateManager.depthMask(false);drawPane(mesh,sky,block,m.opacity);}
            }else{
                if(mesh!=null&&mesh.vertexCount()>0)draw(mesh,sky,block);
                if(m.solidPane){StaticSurfaceMesh pane=part(m.transparent,turns[i],i==0?0:5,bar.frontArm,m.depth,m.left,m.width);if(pane!=null&&pane.vertexCount()>0)drawPane(pane,sky,block,m.opacity);}
            }
            if(!transparent && i==0 && m.opaque!=null) {
                StaticSurfaceMesh cap=stemCap(m,bar.straightLeft?bar.frontArm:bar.frontArm+4,PanelJoins.flatNear(state,root.getWorld(),root.getPos()),bar.straightLeft);
                if(cap!=null)draw(cap,sky,block);
            }

        }
        GlStateManager.depthMask(true);GlStateManager.alphaFunc(GL11.GL_GREATER,.1F);GlStateManager.disableBlend();GlStateManager.enableCull();GlStateManager.enableLighting();GlStateManager.popMatrix();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        return true;
    }
    private static void drawPane(StaticSurfaceMesh mesh,int sky,int block,float opacity){
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());mesh.drawColored(b,sky,block,1,1,1,opacity);Tessellator.getInstance().draw();
    }
    private static void draw(StaticSurfaceMesh mesh,int sky,int block) {
        BufferBuilder b=Tessellator.getInstance().getBuffer();b.begin(GL11.GL_QUADS,BlockSurfaceFormat.get());mesh.draw(b,sky,block);Tessellator.getInstance().draw();
    }
    private FlatPanelBarRenderer() { }
}
