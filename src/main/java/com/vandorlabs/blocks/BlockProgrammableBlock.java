package com.vandorlabs.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.properties.IProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Full solid cube whose six faces share one selectable finish. */
public class BlockProgrammableBlock extends BlockAnimatedScreenSelector {
    public BlockProgrammableBlock() {
        this("programmable_block");
    }

    protected BlockProgrammableBlock(String name) {
        super(name);
        setLightLevel(0);
    }

    @Override public net.minecraft.tileentity.TileEntity createNewTileEntity(net.minecraft.world.World world,int meta){
        com.vandorlabs.tiles.TileEntityAnimatedScreenSelector tile=new com.vandorlabs.tiles.TileEntityAnimatedScreenSelector();
        if(getClass()==BlockProgrammableBlock.class)tile.setMainTextureLayout(1);
        return tile;
    }

    @Override public void onBlockPlacedBy(net.minecraft.world.World world,BlockPos pos,IBlockState state,net.minecraft.entity.EntityLivingBase placer,net.minecraft.item.ItemStack stack){
        super.onBlockPlacedBy(world,pos,state,placer,stack);
        if(getClass()!=BlockProgrammableBlock.class)return;
        net.minecraft.nbt.NBTTagCompound saved=stack.getSubCompound("BlockEntityTag");
        if(saved!=null&&saved.hasKey("MainTextureLayout"))return;
        net.minecraft.tileentity.TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)((com.vandorlabs.tiles.TileEntityAnimatedScreenSelector)tile).setMainTextureLayout(saved!=null&&saved.hasKey("SlabTileSides")?(saved.getBoolean("SlabTileSides")?1:0):1);
    }

    @Override protected BlockStateContainer createBlockState() {
        return new HousingBlockState(this, new IProperty<?>[]{FACING},
                new IUnlistedProperty<?>[]{ProgrammableHousingState.FINISH,ProgrammableHousingState.SIDE_FINISH, ProgrammableHousingState.FACES,
                        ProgrammableHousingState.TILE_SIDES, ProgrammableHousingState.VISIBLE,
                        ProgrammableHousingState.LIGHT,ProgrammableHousingState.JOINED});
    }

    @Override public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ProgrammableHousingState.extend(state, world, pos, false);
    }

    @Override public int getPackedLightmapCoords(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ProgrammableHousingState.light(state, world, pos);
    }

    @Override public int getLightValue(IBlockState state, IBlockAccess world,
            BlockPos pos) { return 0; }
}
