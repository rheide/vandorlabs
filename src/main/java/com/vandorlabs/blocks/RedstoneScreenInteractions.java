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
    public static final int ROW_TOP=com.vandorlabs.ui.RedstoneScreenLayout.ROW_TOP,ROW_HEIGHT=com.vandorlabs.ui.RedstoneScreenLayout.ROW_HEIGHT,ROW_LEFT=com.vandorlabs.ui.RedstoneScreenLayout.ROW_LEFT,ROW_RIGHT=com.vandorlabs.ui.RedstoneScreenLayout.ROW_RIGHT,BUTTON_LEFT=com.vandorlabs.ui.RedstoneScreenLayout.BUTTON_LEFT,BUTTON_RIGHT=com.vandorlabs.ui.RedstoneScreenLayout.BUTTON_RIGHT;
    private RedstoneScreenInteractions(){}
    public static boolean activate(World world,BlockPos pos,IBlockState state,EntityPlayer player,EnumHand hand){
        if(hand!=EnumHand.MAIN_HAND)return false;
        if(player.isSneaking())return false;
        if(!(world.getTileEntity(pos) instanceof TileEntityAnimatedScreenSelector))return false;
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
        if(!supports(state.getBlock()))return false;
        double reach=Math.min(8,player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue());
        Vec3d eye=player.getPositionEyes(1).subtract(new Vec3d(pos));
        // A rotation packet updates yaw/pitch before the server's next head-animation tick.
        // Use those authoritative view angles for a click delivered in the same tick.
        Vec3d end=eye.add(Vec3d.fromPitchYaw(player.rotationPitch,player.rotationYaw).scale(reach));
        for(int slot=0;slot<2;slot++)if(tile.hasVehicleStatus(slot)&&supportsSlot(state.getBlock(),slot)){
            int action=statusAction(state,tile,slot,eye,end);if(action<0)continue;
            if(!world.isRemote)com.vandorlabs.vehicle.VehicleStatus.activate(world,pos,player,action);return true;
        }
        for(int slot=0;slot<2;slot++)if(tile.hasRedstoneScreen(slot) && supportsSlot(state.getBlock(),slot)){
            RedstoneScreenContents contents=tile.redstoneScreen(slot);
            int row=hitRow(state,tile,slot,eye,end,contents.rows().size());
            if(row>=0){
                RedstoneScreenContents.Row control=contents.rows().get(row);
                if(control.slider){
                    double u=hitColumn(state,tile,slot,eye,end);
                    if(u<RedstoneScreenContents.SLIDER_LEFT || u>=RedstoneScreenContents.SLIDER_RIGHT)return false;
                    int segment=(int)((u-RedstoneScreenContents.SLIDER_LEFT)*control.segments()/(RedstoneScreenContents.SLIDER_RIGHT-RedstoneScreenContents.SLIDER_LEFT));
                    if(!world.isRemote)contents.selectSegment(row,segment);
                }else if(!world.isRemote)contents.toggleRow(row);
                return true;
            }
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
        double[] local=com.vandorlabs.ui.RedstoneScreenLayout.local(point.x,point.y,point.z,facing.getIndex());return new Vec3d(local[0],local[1],local[2]);
    }
    public static double hitColumn(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot,Vec3d start,Vec3d end){
        return sharedHit(state,tile,slot,start,end,0).column;
    }
    public static int hitRow(IBlockState state,Vec3d start,Vec3d end,int count){
        return hitRow(state,null,0,start,end,count);
    }
    /** 0 toggles autopilot, 1..3 select Block/Taxi/Hover, 4 pulses departure. */
    public static int statusAction(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot,Vec3d start,Vec3d end){
        int row=hitRow(state,tile,slot,start,end,4);
        double x=hitColumn(state,tile,slot,start,end);
        if(row==0)return 0;
        if(row==1){for(int mode=0;mode<3;mode++)if(x>=6+mode*39&&x<43+mode*39)return mode+1;}
        return row==3&&x<48?4:-1;
    }
    public static int hitRow(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot,Vec3d start,Vec3d end,int count){
        return sharedHit(state,tile,slot,start,end,count).row;
    }
    private static com.vandorlabs.ui.RedstoneScreenLayout.Hit sharedHit(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot,Vec3d start,Vec3d end,int count){
        return new com.vandorlabs.ui.RedstoneScreenLayout(half(state.getBlock(),slot)).hit(surface(state,tile,slot),facing(state).getIndex(),new double[]{start.x,start.y,start.z},new double[]{end.x,end.y,end.z},count);
    }

}
