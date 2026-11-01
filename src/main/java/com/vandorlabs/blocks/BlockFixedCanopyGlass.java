package com.vandorlabs.blocks;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.*;

/** Native fixed glass uses chunk geometry; saved block properties and gameplay stay unchanged. */
public final class BlockFixedCanopyGlass extends BlockCanopy {
    public static final IUnlistedProperty<Object> MESH=new IUnlistedProperty<Object>(){
        public String getName(){return "fixed_canopy_mesh";}
        public boolean isValid(Object value){return value!=null;}
        public Class<Object> getType(){return Object.class;}
        public String valueToString(Object value){return value.toString();}
    };
    public BlockFixedCanopyGlass(String id){super(id);}
    @Override protected BlockStateContainer createBlockState(){return new ExtendedBlockState(this,new IProperty[]{FACING},new IUnlistedProperty[]{MESH});}
    @Override public EnumBlockRenderType getRenderType(IBlockState state){return EnumBlockRenderType.MODEL;}
    @Override public boolean canRenderInLayer(IBlockState state,BlockRenderLayer layer){return layer==BlockRenderLayer.SOLID||layer==BlockRenderLayer.TRANSLUCENT;}
    @Override public IBlockState getExtendedState(IBlockState state,IBlockAccess world,BlockPos pos){
        TileEntity raw=world.getTileEntity(pos);
        Object mesh=raw instanceof com.vandorlabs.tiles.TileEntityCanopy?((com.vandorlabs.tiles.TileEntityCanopy)raw).chunkMesh:null;
        if(mesh==null&&raw instanceof com.vandorlabs.tiles.TileEntityCanopy&&com.vandorlabs.VandorLabs.proxy!=null)com.vandorlabs.VandorLabs.proxy.canopyAppearanceChanged((com.vandorlabs.tiles.TileEntityCanopy)raw);
        return mesh==null?state:((IExtendedBlockState)state).withProperty(MESH,mesh);
    }
}
