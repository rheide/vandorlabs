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
    public static final int ROW_TOP=com.vandorlabs.screen.ScreenControlLayout.FULL_ROW_TOP,ROW_HEIGHT=com.vandorlabs.screen.ScreenControlLayout.ROW_HEIGHT,ROW_LEFT=com.vandorlabs.screen.ScreenControlLayout.ROW_LEFT,ROW_RIGHT=com.vandorlabs.screen.ScreenControlLayout.ROW_RIGHT,BUTTON_LEFT=com.vandorlabs.screen.ScreenControlLayout.BUTTON_LEFT,BUTTON_RIGHT=com.vandorlabs.screen.ScreenControlLayout.BUTTON_RIGHT;
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
        if(ScreenChipSupport.activate(tile,player,hand,eye,end))return true;
        if(com.vandorlabs.navigation.NavigationInteractions.screen(tile,player,hand,eye,end))return true;
        for(int slot=0;slot<2;slot++)if(tile.hasVehicleStatusControls(slot)&&supportsSlot(state.getBlock(),slot)){
            int action=statusAction(state,tile,slot,eye,end);if(action<0)continue;
            if(!world.isRemote)com.vandorlabs.vehicle.VehicleStatus.activate(world,pos,player,action);return true;
        }
        for(int slot=0;slot<TileEntityAnimatedScreenSelector.REDSTONE_SLOTS;slot++)if(tile.getSurfaceTexture(slot)==TileEntityAnimatedScreenSelector.REDSTONE_SURFACE && supportsSlot(state.getBlock(),slot)){
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
        return block!=null&&block.getClass()==BlockAnimatedScreenSelector.class || block instanceof BlockProgrammableDiagonalScreen
            || block instanceof BlockProgrammableConsole || block instanceof BlockProgrammableInput
            || block instanceof BlockProgrammableHalfConsole || block instanceof BlockDiagonalHalfConsole;
    }
    public static boolean supportsSlot(net.minecraft.block.Block block,int slot){
        if(slot==TileEntityAnimatedScreenSelector.SPLIT_TOP||slot==TileEntityAnimatedScreenSelector.SPLIT_BOTTOM)
            return supportsSplitVehiclePanel(block);
        return supports(block) && (slot==0 || slot==1 && (block instanceof BlockProgrammableConsole || block instanceof BlockProgrammableHalfConsole));
    }
    public static boolean supportsSplitVehiclePanel(net.minecraft.block.Block block){return supports(block)&&!half(block,0);}
    public static boolean half(net.minecraft.block.Block block,int slot){
        return slot>=TileEntityAnimatedScreenSelector.SPLIT_TOP||block instanceof BlockProgrammableInput && !(block instanceof BlockProgrammableFullInput)
            || block instanceof BlockDiagonalHalfConsole || block instanceof BlockProgrammableHalfConsole
            || block instanceof BlockProgrammableConsole && slot==1;
    }
    public static int displayHeight(net.minecraft.block.Block block,int slot){return half(block,slot)?com.vandorlabs.screen.ScreenControlLayout.HALF_HEIGHT:com.vandorlabs.screen.ScreenControlLayout.FULL_HEIGHT;}
    public static int rowTop(net.minecraft.block.Block block,int slot){return half(block,slot)?com.vandorlabs.screen.ScreenControlLayout.HALF_ROW_TOP:ROW_TOP;}
    public static ScreenSurface.Quad splitSurface(TileEntityAnimatedScreenSelector tile,ScreenSurface.Quad quad){
        return tile.frameAppearance.hasFront()?ScreenSurface.framedSplit(quad):quad;
    }
    public static ScreenSurface.Quad surface(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot){
        if(tile instanceof com.vandorlabs.tiles.TileEntityDisplayArm)
            return ((com.vandorlabs.tiles.TileEntityDisplayArm)tile).geometry().surface(slot);
        if(slot>=TileEntityAnimatedScreenSelector.SPLIT_TOP)
            return ScreenSurface.half(splitSurface(tile,surface(state,tile,0)),slot==TileEntityAnimatedScreenSelector.SPLIT_TOP);
        net.minecraft.block.Block block=state.getBlock();
        if(block instanceof BlockProgrammableInput){
            boolean full=block instanceof BlockProgrammableFullInput;
            boolean keyboard=state.getValue(BlockProgrammableInput.KEYBOARD),upper=state.getValue(BlockProgrammableInput.UPPER);
            InputSurfaceLayout.Mounted layout=tile!=null && tile.isCeilingMounted()?InputSurfaceLayout.ceilingInput(full,tile.isSmallInput(),tile.getCeilingPosition(full?1:2)):
                full?InputSurfaceLayout.fullInput(keyboard,upper,tile!=null && tile.isSmallInput()):InputSurfaceLayout.halfInput(keyboard,upper,tile==null?(upper?2:0):tile.getWallPosition(upper?2:0),tile!=null && tile.isSmallInput());
            return ScreenSurface.input(layout.surface);
        }
        if(block instanceof BlockDiagonalHalfConsole)return ScreenSurface.halfDiagonal(state.getValue(BlockDiagonalHalfConsole.UPPER));
        if(block instanceof BlockProgrammableConsole)return slot==1?ScreenSurface.input(InputSurfaceLayout.halfConsoleFront()):ScreenSurface.quad(ScreenSurface.Kind.CONSOLE,false);
        if(block instanceof BlockProgrammableHalfConsole)return slot==0&&tile!=null&&ScreenChipSupport.selected(tile)?ScreenChipSupport.deck():ScreenSurface.input(slot==1?InputSurfaceLayout.halfConsoleRear():InputSurfaceLayout.halfConsoleFront());
        if(block instanceof BlockProgrammableDiagonalScreen)return ScreenSurface.diagonal(
            state.getValue(BlockProgrammableDiagonalScreen.INVERTED),tile!=null&&tile.isShallowDiagonalScreen());
        return ScreenChipSupport.display(state,tile,surface(state));
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
    /** Display arms keep their artwork in a canonical plane and inverse-transform the ray. */
    public static Vec3d local(Vec3d point,EnumFacing facing,TileEntityAnimatedScreenSelector tile){
        if(tile instanceof com.vandorlabs.tiles.TileEntityDisplayArm){
            com.vandorlabs.display.DisplayArmGeometry.Vec p=((com.vandorlabs.tiles.TileEntityDisplayArm)tile)
                    .geometry().display.inverse(point.x*16,point.y*16,point.z*16);
            return new Vec3d(p.x,p.y,p.z);
        }
        return local(point,facing);
    }
    private static boolean visibleSurface(TileEntityAnimatedScreenSelector tile,int slot){
        return !(tile instanceof com.vandorlabs.tiles.TileEntityDisplayArm)
                ||((com.vandorlabs.tiles.TileEntityDisplayArm)tile).geometry().hasSurface(slot);
    }
    public static double hitColumn(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot,Vec3d start,Vec3d end){
        if(!visibleSurface(tile,slot))return -1;
        ScreenSurface.Quad q=surface(state,tile,slot);EnumFacing face=facing(state);
        Vec3d a=local(start,face,tile),d=local(end,face,tile).subtract(a);
        double denom=q.ny*d.y+q.nz*d.z;if(denom>=-1e-8)return -1;
        double t=(q.ny*(q.topRight.y-a.y)+q.nz*(q.topRight.z-a.z))/denom;
        return t<0 || t>1?-1:(q.topRight.x-a.add(d.scale(t)).x)/(q.topRight.x-q.topLeft.x)*128;
    }
    public static int hitRow(IBlockState state,Vec3d start,Vec3d end,int count){
        return hitRow(state,null,0,start,end,count);
    }
    /** Uses the same mode button bounds as the rendered Vehicle Status screen. */
    public static int statusAction(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot,Vec3d start,Vec3d end){
        ScreenSurface.Quad q=surface(state,tile,slot);
        if(tile.hasSplitRedstone()&&tile.hasSplitVehiclePanel(slot))q=splitSurface(tile,q);
        EnumFacing face=facing(state);
        Vec3d a=local(start,face),d=local(end,face).subtract(a);
        double denom=q.ny*d.y+q.nz*d.z;if(denom>=-1e-8)return -1;
        double t=(q.ny*(q.topRight.y-a.y)+q.nz*(q.topRight.z-a.z))/denom;
        if(t<0||t>1)return -1;
        Vec3d hit=a.add(d.scale(t));
        int height=displayHeight(state.getBlock(),slot);
        double x=(q.topRight.x-hit.x)/(q.topRight.x-q.topLeft.x)*128;
        double dy=q.bottomRight.y-q.topRight.y,dz=q.bottomRight.z-q.topRight.z;
        double v=((hit.y-q.topRight.y)*dy+(hit.z-q.topRight.z)*dz)/(dy*dy+dz*dz);
        if(tile.hasSplitVehiclePanel(slot)){
            if(v<0||v>=1)return -1;
            boolean top=v<.5;
            if(!com.vandorlabs.tiles.FrameAppearance.statusSplit(top?tile.frameAppearance.splitTop:tile.frameAppearance.splitBottom))return -1;
            return com.vandorlabs.vehicle.VehicleStatus.panelAction(top?tile.frameAppearance.splitTop:tile.frameAppearance.splitBottom,height/2,x,(v-(top?0:.5))*height);
        }
        double y=v*height;
        return com.vandorlabs.vehicle.VehicleStatus.panelAction(tile.getSurfaceTexture(slot),height,x,y);
    }
    public static int hitRow(IBlockState state,TileEntityAnimatedScreenSelector tile,int slot,Vec3d start,Vec3d end,int count){
        if(!visibleSurface(tile,slot))return -1;
        ScreenSurface.Quad q=surface(state,tile,slot);EnumFacing face=facing(state);
        Vec3d a=local(start,face,tile),d=local(end,face,tile).subtract(a);
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
