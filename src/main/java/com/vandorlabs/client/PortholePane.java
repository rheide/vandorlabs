package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

/** The independently configured window surface follows the existing porthole outline. */
final class PortholePane {
    static TextureAtlasSprite sprite(TileEntityAnimatedScreenSelector tile){return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(GlassMaterials.texture(tile.getGlassMaterial(),tile.getGlassShade(),1,tile.getGlassTint()));}
    static StaticSurfaceMesh mesh(TileEntityAnimatedScreenSelector tile,IBlockState state,StaticSurfaceMesh raw){
        GlassSurfaceGroups.Layout layout=new GlassSurfaceGroups.Layout(0,0,1,1);double bx=0,by=0;
        if(tile.getGlassMaterial()>=0){
            TEAnimatedScreenSelector.PortholeGroup group=TEAnimatedScreenSelector.portholeGroup(tile,state);
            EnumFacing right=state.getValue(BlockProgrammableWall.FACING).rotateY(),up=((BlockProgrammableWall)state.getBlock()).isDiagonalShape() && tile.isDiagonalHalfHeight()?state.getValue(BlockProgrammableWall.FACING).getOpposite():EnumFacing.UP;
            int column=PanelPlane.axis(tile.getPos(),group.right)-group.minAxis,row=PanelPlane.axis(tile.getPos(),group.up)-group.minY;
            layout=new GlassSurfaceGroups.Layout(column,row,group.columns,group.rows,right!=group.right,up!=group.up);bx=(2+group.extraBorder)/16D;by=(4+group.extraBorder)/16D;
        }
        return GlassSurfaceMapping.map(raw,sprite(tile),layout,true,bx,by,tile.getGlassMaterial()<0?0:tile.getGlassTextureLayout());
    }
    private PortholePane(){}
}
