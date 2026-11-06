package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.screen.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;

/** Actual standalone row/column rays across every source role and configured input mounting pose. */
public final class ScreenControlLayoutChecks {
    private static Vec3d placed(Vec3d p,int face){double x=p.x/16-.5,y=p.y/16-.5,z=p.z/16-.5;switch(face){case 5:return new Vec3d(.5-z,.5+y,.5+x);case 3:return new Vec3d(.5-x,.5+y,.5-z);case 4:return new Vec3d(.5+z,.5+y,.5-x);case 1:return new Vec3d(.5-x,.5-z,.5-y);case 0:return new Vec3d(.5+x,.5+z,.5-y);default:return new Vec3d(.5+x,.5+y,.5+z);}}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();java.lang.reflect.Constructor<com.vandorlabs.tiles.DeferredTileLoad> constructor=com.vandorlabs.tiles.DeferredTileLoad.class.getDeclaredConstructor();constructor.setAccessible(true);com.vandorlabs.tiles.DeferredTileLoad unload=constructor.newInstance();int profiles=0,rays=0;
        for(net.minecraft.block.Block candidate:ModBlocks.BLOCKS){ScreenFamily family=ScreenFamily.fromId(candidate.getRegistryName().getResourcePath());if(family==null||!(candidate instanceof BlockAnimatedScreenSelector))continue;BlockAnimatedScreenSelector block=(BlockAnimatedScreenSelector)candidate;
            for(IBlockState state:block.getBlockState().getValidStates())for(boolean ceiling:new boolean[]{false,true})for(int position=0;position<3;position++){
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos at=new BlockPos(0,80,0);world.setBlockState(at,state,18);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("CeilingMounted",ceiling);tag.setInteger("CeilingPosition",position);tag.setInteger("wallPosition",position);tag.setBoolean("smallInput",position==2);if(family==ScreenFamily.HALF_CONSOLE&&position==2)tag.setInteger("SecondarySurfaceTexture",-4);tile.readFromNBT(tag);ScreenFamily.State pose=family.decode(block.getMetaFromState(state));
                for(int slot=0;slot<2;slot++)if(family.supportsSlot(slot)){
                    ScreenControlLayout.Projection shared=ScreenControlLayout.of(family,pose,slot,tile.isSmallInput(),tile.getWallPosition(pose.upper?2:0),tile.isCeilingMounted(),tile.getCeilingPosition(family==ScreenFamily.HALF_INPUT?2:1),family==ScreenFamily.HALF_CONSOLE&&ScreenChipSupport.selected(tile));com.vandorlabs.render.ScreenSurface.Quad source=RedstoneScreenInteractions.surface(state,tile,slot);
                    if(shared.height!=RedstoneScreenInteractions.displayHeight(block,slot)||shared.rowTop!=RedstoneScreenInteractions.rowTop(block,slot))throw new AssertionError("Source pixel grid "+family);profiles++;
                    for(double u:new double[]{5.9,6,6.1,59.9,60,60.1,119.9,120,120.1,122,122.1})for(double v:new double[]{0,7.9,8,23.9,24,33.99,34,35.99,36,60,117})for(boolean reverse:new boolean[]{false,true}){
                        double f=v/shared.height;Vec3d on=new Vec3d(source.topRight.x-(source.topRight.x-source.topLeft.x)*u/128,source.topRight.y+(source.bottomRight.y-source.topRight.y)*f,source.topRight.z+(source.bottomRight.z-source.topRight.z)*f);Vec3d normal=new Vec3d(0,source.ny,source.nz).scale(reverse?-16:16);Vec3d start=placed(on.add(normal),pose.facing),end=placed(on.subtract(normal),pose.facing);ScreenControlLayout.Sample sample=shared.sample(start.x,start.y,start.z,end.x,end.y,end.z);double column=RedstoneScreenInteractions.hitColumn(state,tile,slot,start,end);if(Math.abs(column-sample.column)>1e-10)throw new AssertionError("Source column "+family+" expected="+column+" actual="+sample.column);for(int count:new int[]{4,8,12})if(RedstoneScreenInteractions.hitRow(state,tile,slot,start,end,count)!=sample.row(count))throw new AssertionError("Source row "+family+" slot="+slot+" u="+u+" v="+v);int step=column<60||column>=120?-1:(int)((column-60)*4/60);if(sample.sliderStep()!=step)throw new AssertionError("Source slider region/step");rays++;
                    }
                }
                tile.onChunkUnload();tile.invalidate();net.minecraftforge.event.world.WorldEvent.Unload event=new net.minecraftforge.event.world.WorldEvent.Unload(world);unload.worldUnload(event);com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(event);
            }
        }
        System.out.println("Screen control layout PASS profiles="+profiles+" rays="+rays+" Source rows/columns/slider steps/full+half/mounted/small/chip deck/front-only");
    }
}
