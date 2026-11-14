package com.vandorlabs.client;

import com.vandorlabs.CommonProxy;
import com.vandorlabs.VandorLabs;
import com.vandorlabs.blocks.BlockGlassWall;
import com.vandorlabs.blocks.BlockProgrammableGlass;
import com.vandorlabs.tiles.FrameAppearance;
import com.vandorlabs.tiles.ScreenHousingTextures;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import java.lang.reflect.Field;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.CoreModManager;

/** Reproduce real catalog emissive frames rejecting an entire joined native pane. */
public final class GlassPaneTerrainCoverageChecks {
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static int emissiveFrame(){
        for(int choice=0;choice<ScreenHousingTextures.BUILTIN_COUNT;choice++){
            com.google.gson.JsonObject entry=ScreenHousingTextures.entry(choice);
            if(FrameAppearance.valid(choice)&&entry!=null&&entry.has("frameLight")&&entry.get("frameLight").getAsInt()>0
                    &&FrameAppearance.border(choice))return choice;
        }
        throw new AssertionError("Packaged emissive frame fixture missing");
    }
    public static void main(String[] args)throws Exception{
        Field environment=CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");environment.setAccessible(true);environment.setBoolean(null,true);
        net.minecraft.init.Bootstrap.register();VandorLabs.proxy=new CommonProxy();
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityProgrammableGlass.class,new ResourceLocation("vandorlabs","native_coverage_checks"));
        BlockProgrammableGlass glass=new BlockProgrammableGlass("native_coverage_checks");
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        int frame=emissiveFrame(),interiors=0;
        try{
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int depth=0;depth<3;depth++)for(int tint=0;tint<3;tint++){
                world.clear();GlassSurfaceGroups.invalidate(world);
                IBlockState state=glass.getDefaultState().withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,depth);
                TileEntityProgrammableGlass center=null;
                for(int u=0;u<3;u++)for(int v=0;v<3;v++){
                    boolean rotated=state.getValue(BlockGlassWall.ROTATED);
                    BlockPos pos=new BlockPos(32+(rotated?0:u),64+v,48+(rotated?u:0));
                    NBTTagCompound data=new NBTTagCompound();data.setBoolean("GlassJoin",true);data.setBoolean("GlassFramed",true);
                    data.setInteger("GlassTint",tint);data.setInteger("GlassPanelFacing",facing.getHorizontalIndex());data.setInteger("FrameFront",frame);
                    TileEntityProgrammableGlass tile=new TileEntityProgrammableGlass();tile.readFromNBT(data);tile.setWorld(world);tile.setPos(pos);
                    world.states.put(pos,state);world.tiles.put(pos,tile);if(u==1&&v==1)center=tile;
                }
                for(net.minecraft.tileentity.TileEntity raw:world.tiles.values()){
                    TileEntityProgrammableGlass tile=(TileEntityProgrammableGlass)raw;
                    need(tile.frameAppearance.light()>0,"fixture lost emissive catalog selection");
                    need(GlassPaneTerrain.supported(tile,state),"emissive frame rejected stationary pane at "+tile.getPos()+" tint="+tint+" facing="+facing+" depth="+depth);
                }
                need(GlassSurfaceGroups.frameMask(center)==0,"joined center still has frame borders");interiors++;
                need(GlassPaneTerrain.supported(center,state),"empty joined interior inherited emissive-frame exclusion");
            }
        }finally{GlassSurfaceGroups.unload(world);}
        System.out.println("GlassPaneTerrainCoverage PASS checks="+checks+" catalog-emissive-frame="+frame+" joined-interiors="+interiors+" all facings/depths/translucent tints");
    }
    private GlassPaneTerrainCoverageChecks(){}
}
