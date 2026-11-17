package com.vandorlabs.blocks;
import com.vandorlabs.tiles.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
/** Enclosed assembler cells support mounted controls; decorative machines remain non-solid. */
final class ConstructorWallSupport {
    private ConstructorWallSupport(){}
    static boolean solid(IBlockAccess world,BlockPos pos){
        TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof TileEntityConstructorPart){BlockPos owner=((TileEntityConstructorPart)tile).owner();if(world instanceof net.minecraft.world.World&&!((net.minecraft.world.World)world).isBlockLoaded(owner,false))return false;tile=world.getTileEntity(owner);}
        return tile instanceof TileEntityConstructor&&((TileEntityConstructor)tile).formed()&&((TileEntityConstructor)tile).builder();
    }
}
