package com.vandorlabs.blocks;

import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;

/** Transparent panels share the wall's placement, joining and collision geometry. */
public final class BlockProgrammableDiagonalGlass extends BlockProgrammableWall {
    public BlockProgrammableDiagonalGlass(){super("programmable_diagonal_glass",Shape.DIAGONAL,net.minecraft.block.material.Material.GLASS);setLightOpacity(0);setHardness(.5F);setResistance(2);setSoundType(net.minecraft.block.SoundType.GLASS);}
    @Override public BlockRenderLayer getBlockLayer(){return BlockRenderLayer.TRANSLUCENT;}
    @Override public net.minecraft.tileentity.TileEntity createNewTileEntity(World world,int meta){
        TileEntityAnimatedScreenSelector tile=new TileEntityAnimatedScreenSelector();tile.setGlassShade(0);return tile;
    }
}
