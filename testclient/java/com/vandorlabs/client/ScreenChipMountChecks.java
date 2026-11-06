package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.screen.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;

/** Fresh source frame cache for every complete configured pose; shared frames never depend on first-use order. */
public final class ScreenChipMountChecks {
    private static int profiles,rays;
    private static void same(double a,double b,String field){if(Math.abs(a-b)>1e-12)throw new AssertionError("Chip mount "+field+": source="+a+" shared="+b);}
    private static void point(Vec3d source,ScreenChipMount.Point shared,String name){same(source.x,shared.x,name+"X");same(source.y,shared.y,name+"Y");same(source.z,shared.z,name+"Z");}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();java.lang.reflect.Field frames=ScreenChipSupport.class.getDeclaredField("FRAMES");frames.setAccessible(true);
        java.lang.reflect.Constructor<com.vandorlabs.tiles.DeferredTileLoad> constructor=com.vandorlabs.tiles.DeferredTileLoad.class.getDeclaredConstructor();constructor.setAccessible(true);com.vandorlabs.tiles.DeferredTileLoad unload=constructor.newInstance();
        for(net.minecraft.block.Block candidate:new net.minecraft.block.Block[]{new BlockProgrammableConsole(),new BlockProgrammableHalfConsole(),new BlockProgrammableDiagonalScreen(),new BlockDiagonalHalfConsole("programmable_diagonal_half_screen")}){ScreenFamily family=ScreenFamily.fromId(candidate.getRegistryName().getResourcePath());if(family==null||!ScreenChipMount.integrated(family))continue;BlockAnimatedScreenSelector block=(BlockAnimatedScreenSelector)candidate;
            for(IBlockState state:block.getBlockState().getValidStates())for(boolean ceiling:new boolean[]{false,true})for(int position=0;position<3;position++){
                NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);BlockPos at=new BlockPos(0,80,0);world.setBlockState(at,state,18);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(at);NBTTagCompound raw=new NBTTagCompound();raw.setBoolean("CeilingMounted",ceiling);raw.setInteger("CeilingPosition",position);tile.readFromNBT(raw);((java.util.Map<?,?>)frames.get(null)).clear();MachineMountSurface source=ScreenChipSupport.mount(state,tile);
                ScreenFamily.State pose=family.decode(block.getMetaFromState(state));ScreenChipMount.Frame shared=ScreenChipMount.frame(family,pose.facing,family==ScreenFamily.DIAGONAL?pose.inverted:pose.upper,tile.isCeilingMounted(),tile.getCeilingPosition(1));point(source.center,shared.center,"center");point(source.u,shared.u,"u");point(source.v,shared.v,"v");point(source.normal,shared.normal,"normal");same(source.width,shared.width,"width");same(source.height,shared.height,"height");AxisAlignedBB bounds=ScreenChipSupport.bounds(state,tile);double[] expected={bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ};for(int field=0;field<6;field++)same(expected[field],shared.bound(field),"bound"+field);
                for(double x:new double[]{-.21,-.195,-.17,0,.17,.195,.21})for(double y:new double[]{-.21,-.195,-.17,0,.17,.195,.21})for(boolean reverse:new boolean[]{false,true}){Vec3d center=source.center.add(source.u.scale(x)).add(source.v.scale(y));Vec3d start=center.add(source.normal.scale(reverse?-1:1)),end=center.add(source.normal.scale(reverse?1:-1));boolean old=source.hit(start,end),next=shared.hit(start.x,start.y,start.z,end.x,end.y,end.z);if(old!=next)throw new AssertionError("Source ray mismatch "+family+" x="+x+" y="+y);rays++;}profiles++;
                tile.onChunkUnload();tile.invalidate();net.minecraftforge.event.world.WorldEvent.Unload event=new net.minecraftforge.event.world.WorldEvent.Unload(world);unload.worldUnload(event);com.vandorlabs.redstone.RedstoneChannels.worldUnloaded(event);
            }
        }
        System.out.println("Screen chip mounting PASS profiles="+profiles+" rays="+rays+" source frame/normal/physical bounds/front-only hit tolerance; complete pose cache keys");
    }
}
