package com.vandorlabs.blocks;

import com.vandorlabs.GuiHandler;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.render.ScreenSurface;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import com.vandorlabs.tiles.RedstoneScreenContents;
import com.vandorlabs.render.InputSurfaceLayout;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Uses the same display plane and pixel layout as the row renderer. */
public final class RedstoneScreenInteractions {
    public static final int ROW_TOP=24,ROW_HEIGHT=12,ROW_LEFT=6,ROW_RIGHT=122,BUTTON_LEFT=98,BUTTON_RIGHT=120;
    private RedstoneScreenInteractions(){}
    public static boolean activate(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand){
        if(hand!=EnumHand.MAIN_HAND)return false;
        if(player.isSneaking())return false;
        if(!(world.getTileEntity(pos) instanceof TileEntityAnimatedScreenSelector))return false;
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
        if(!supports(state.getBlock()))return false;
        double reach=Math.min(8,player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue());
        Vec3d eye=player.getPositionEyes(1).subtract(new Vec3d(pos));
        Vec3d end=eye.add(player.getLook(1).scale(reach));
        for(int slot=0;slot<2;slot++)if(tile.hasRedstoneScreen(slot) && supportsSlot(state.getBlock(),slot)){
            RedstoneScreenContents contents=tile.redstoneScreen(slot);
            int row=hitRow(state,tile,slot,eye,end,contents.rows().size());
            if(row>=0){if(!world.isRemote)contents.toggleRow(row);return true;}
        }
        return false;
    }
    public static boolean supports(net.minecraft.block.Block block){
        return block==ModBlocks.ANIMATED_SCREEN_SELECTOR || block instanceof BlockProgrammableDiagonalScreen
            || block instanceof BlockProgrammableConsole || block instanceof BlockProgrammableInput
            || block instanceof BlockProgrammableHalfConsole || block instanceof BlockDiagonalHalfConsole;
    }
    public static boolean supportsSlot(net.minecraft.block.Block block,int slot){
        return supports(block) && (slot==0 || slot==1 && (block instanceof BlockProgrammableConsole || block instanceof BlockProgrammableHalfConsole));
    }
    public static boolean half(net.minecraft.block.Block block,int slot){
        return block instanceof BlockProgrammableInput && !(block instanceof BlockProgrammableFullInput)
            || block instanceof BlockDiagonalHalfConsole || block instanceof BlockProgrammableHalfConsole
            || block instanceof BlockProgrammableConsole && slot==1;
    }
    public static int displayHeight(net.minecraft.block.Block block,int slot){return half(block,slot)?64:128;}
    public static int rowTop(net.minecraft.block.Block block,int slot){return half(block,slot)?8:ROW_TOP;}
    public static ScreenSurface.Quad surface(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot){
        net.minecraft.block.Block block=state.getBlock();
        if(block instanceof BlockProgrammableInput){
            boolean full=block instanceof BlockProgrammableFullInput;
            boolean keyboard=state.getValue(BlockProgrammableInput.KEYBOARD),upper=state.getValue(BlockProgrammableInput.UPPER);
            InputSurfaceLayout.Mounted layout=tile!=null && tile.isCeilingMounted()?InputSurfaceLayout.ceilingInput(full,!full && tile.isSmallInput(),tile.getCeilingPosition(full?1:2)):
                full?InputSurfaceLayout.fullInput(keyboard,upper):InputSurfaceLayout.halfInput(keyboard,upper,tile==null?(upper?2:0):tile.getWallPosition(upper?2:0),tile!=null && tile.isSmallInput());
            return ScreenSurface.input(layout.surface);
        }
        if(block instanceof BlockDiagonalHalfConsole)return ScreenSurface.halfDiagonal(state.getValue(BlockDiagonalHalfConsole.UPPER));
        if(block instanceof BlockProgrammableConsole)return slot==1?ScreenSurface.input(InputSurfaceLayout.halfConsoleFront()):ScreenSurface.quad(ScreenSurface.Kind.CONSOLE,false);
        if(block instanceof BlockProgrammableHalfConsole)return ScreenSurface.input(slot==1?InputSurfaceLayout.halfConsoleRear():InputSurfaceLayout.halfConsoleFront());
        return surface(state);
    }
    public static ScreenSurface.Quad surface(IBlockState state){
        boolean diagonal=state.getBlock() instanceof BlockProgrammableDiagonalScreen;
        return ScreenSurface.quad(diagonal?ScreenSurface.Kind.DIAGONAL:ScreenSurface.Kind.FLAT,
                diagonal && state.getValue(BlockProgrammableDiagonalScreen.INVERTED));
    }
    public static EnumFacing facing(IBlockState state){return state.getValue(state.getBlock() instanceof BlockProgrammableDiagonalScreen?BlockProgrammableDiagonalScreen.FACING:state.getBlock() instanceof BlockProgrammableInput?BlockProgrammableInput.FACING:BlockAnimatedScreenSelector.FACING);}
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
        return hitRow(state,null,0,start,end,count);
    }
    public static int hitRow(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot,Vec3d start,Vec3d end,int count){
        ScreenSurface.Quad q=surface(state,tile,slot);EnumFacing face=facing(state);
        Vec3d a=local(start,face),d=local(end,face).subtract(a);
        double denom=q.ny*d.y+q.nz*d.z;if(denom>=-1e-8)return -1;
        double t=(q.ny*(q.topRight.y-a.y)+q.nz*(q.topRight.z-a.z))/denom;
        if(t<0 || t>1)return -1;
        Vec3d hit=a.add(d.scale(t));
        double u=(q.topRight.x-hit.x)/(q.topRight.x-q.topLeft.x)*128;
        double dy=q.bottomRight.y-q.topRight.y,dz=q.bottomRight.z-q.topRight.z;
        double v=((hit.y-q.topRight.y)*dy+(hit.z-q.topRight.z)*dz)/(dy*dy+dz*dz)*displayHeight(state.getBlock(),slot);
        int top=rowTop(state.getBlock(),slot),row=(int)Math.floor((v-top)/ROW_HEIGHT);
        return u>=ROW_LEFT && u<=ROW_RIGHT && row>=0 && row<count && v-top-row*ROW_HEIGHT<10?row:-1;
    }
}
