package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.TileEntityRedstoneScreen;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Uses the same display plane and pixel layout as the row renderer. */
public final class RedstoneScreenInteractions {
    public static final int ROW_TOP=24,ROW_HEIGHT=12,BUTTON_LEFT=98,BUTTON_RIGHT=120;
    private RedstoneScreenInteractions(){}
    public static boolean activate(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand){
        if(hand!=EnumHand.MAIN_HAND)return false;
        if(player.isSneaking() && com.vandorlabs.items.ConfigurationAccess.canConfigure(player)){
            if(!world.isRemote)player.openGui(VandorLabs.instance,GuiHandler.GUI_REDSTONE_SCREEN,world,pos.getX(),pos.getY(),pos.getZ());
            return true;
        }
        if(!(world.getTileEntity(pos) instanceof TileEntityRedstoneScreen))return false;
        TileEntityRedstoneScreen tile=(TileEntityRedstoneScreen)world.getTileEntity(pos);
        double reach=Math.min(8,player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue());
        Vec3d eye=player.getPositionEyes(1),end=eye.add(player.getLook(1).scale(reach));
        int row=hitRow(state,eye.subtract(new Vec3d(pos)),end.subtract(new Vec3d(pos)),tile.rows().size());
        if(row<0)return false;
        if(!world.isRemote)tile.toggleRow(row);
        return true;
    }
    public static ScreenSurface.Quad surface(IBlockState state){
        boolean diagonal=state.getBlock() instanceof BlockProgrammableDiagonalScreen;
        return ScreenSurface.quad(diagonal?ScreenSurface.Kind.DIAGONAL:ScreenSurface.Kind.FLAT,
                diagonal && state.getValue(BlockProgrammableDiagonalScreen.INVERTED));
    }
    public static EnumFacing facing(IBlockState state){return state.getValue(state.getBlock() instanceof BlockProgrammableDiagonalScreen?BlockProgrammableDiagonalScreen.FACING:BlockAnimatedScreenSelector.FACING);}
    public static Vec3d local(Vec3d point,EnumFacing facing){
        double x=point.x-.5,y=point.y-.5,z=point.z-.5,lx=x,ly=y,lz=z;
        switch(facing){
            case EAST:lx=z;lz=-x;break;
            case SOUTH:lx=-x;lz=-z;break;
            case WEST:lx=-z;lz=x;break;
            case UP:lx=-x;ly=-z;lz=-y;break;
            case DOWN:ly=-z;lz=y;break;
            default:break;
        }
        return new Vec3d((lx+.5)*16,(ly+.5)*16,(lz+.5)*16);
    }
    public static int hitRow(IBlockState state,Vec3d start,Vec3d end,int count){
        ScreenSurface.Quad q=surface(state);EnumFacing face=facing(state);
        Vec3d a=local(start,face),d=local(end,face).subtract(a);
        double denom=q.ny*d.y+q.nz*d.z;if(denom>=-1e-8)return -1;
        double t=(q.ny*(q.topRight.y-a.y)+q.nz*(q.topRight.z-a.z))/denom;
        if(t<0 || t>1)return -1;
        Vec3d hit=a.add(d.scale(t));
        double u=(q.topRight.x-hit.x)/(q.topRight.x-q.topLeft.x)*128;
        double v=(q.topRight.y-hit.y)/(q.topRight.y-q.bottomRight.y)*128;
        int row=(int)Math.floor((v-ROW_TOP)/ROW_HEIGHT);
        return u>=BUTTON_LEFT && u<=BUTTON_RIGHT && row>=0 && row<count && v-ROW_TOP-row*ROW_HEIGHT<10?row:-1;
    }
}
