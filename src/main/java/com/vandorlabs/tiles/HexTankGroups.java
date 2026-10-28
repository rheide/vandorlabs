package com.vandorlabs.tiles;

import com.vandorlabs.blocks.BlockAnimatedScreenSelector;
import com.vandorlabs.render.ConnectedSquare;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Bounded complete 2x2/3x3 tank cross sections; storage remains owned by each native cell. */
public final class HexTankGroups {
    private HexTankGroups(){}
    public static EnumFacing right(EnumFacing.Axis axis){return axis==EnumFacing.Axis.X?EnumFacing.SOUTH:EnumFacing.EAST;}
    public static EnumFacing up(EnumFacing.Axis axis){return axis==EnumFacing.Axis.Y?EnumFacing.SOUTH:EnumFacing.UP;}
    public static ConnectedSquare.Part part(TileEntityHexTank tile){
        World world=tile.getWorld();if(world==null||tile.isBlockShape())return ConnectedSquare.Part.SINGLE;
        BlockPos origin=tile.getPos();EnumFacing.Axis axis=world.getBlockState(origin).getValue(BlockAnimatedScreenSelector.FACING).getAxis();
        boolean[] unloaded={false};
        ConnectedSquare.Part part=ConnectedSquare.findPartitioned(3,(x,y)->{
            BlockPos at=origin.offset(right(axis),x).offset(up(axis),y);
            if(!world.isBlockLoaded(at)){unloaded[0]=true;return false;}
            if(!(world.getTileEntity(at) instanceof TileEntityHexTank))return false;
            TileEntityHexTank other=(TileEntityHexTank)world.getTileEntity(at);
            return !other.isBlockShape()&&world.getBlockState(at).getValue(BlockAnimatedScreenSelector.FACING).getAxis()==axis
                    &&other.getHousingTexture()==tile.getHousingTexture()&&other.getMainTextureLayout()==tile.getMainTextureLayout();
        });
        if(unloaded[0])return ConnectedSquare.Part.SINGLE;
        net.minecraftforge.fluids.FluidStack contents=null;BlockPos root=origin.offset(right(axis),-part.x).offset(up(axis),-part.y);
        for(int x=0;x<part.size;x++)for(int y=0;y<part.size;y++){
            TileEntityHexTank cell=(TileEntityHexTank)world.getTileEntity(root.offset(right(axis),x).offset(up(axis),y));
            net.minecraftforge.fluids.FluidStack fluid=cell.tank.getFluid();
            if(fluid!=null){if(contents!=null&&!contents.isFluidEqual(fluid))return ConnectedSquare.Part.SINGLE;contents=fluid;}
        }
        return part;
    }
    public static BlockPos root(TileEntityHexTank tile,ConnectedSquare.Part part){EnumFacing.Axis axis=tile.getWorld().getBlockState(tile.getPos()).getValue(BlockAnimatedScreenSelector.FACING).getAxis();return tile.getPos().offset(right(axis),-part.x).offset(up(axis),-part.y);}
}
