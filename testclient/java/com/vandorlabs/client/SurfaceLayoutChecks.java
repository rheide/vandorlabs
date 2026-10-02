package com.vandorlabs.client;

import com.vandorlabs.render.ScreenHousingMesh;
import com.vandorlabs.tiles.*;
import com.vandorlabs.items.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

final class SurfaceLayoutChecks {
    static void run() {
        for(ScreenHousingMesh mesh:new ScreenHousingMesh[]{ScreenHousingMesh.console(),ScreenHousingMesh.halfConsole()}) {
            ScreenHousingMesh fitted=mesh.sideLayout(false);
            require(mesh.sideLayout(true)==mesh && mesh.sideLayout(false)==fitted,"cached layouts");
            compare(mesh.quads,fitted.quads);compare(mesh.triangles,fitted.triangles);
        }
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
        com.vandorlabs.blocks.BlockAnimatedScreenSelector block=new com.vandorlabs.blocks.BlockAnimatedScreenSelector("programmable_viewscreen"){};
        BlockPos source=new BlockPos(10,100,10),target=source.east();world.setBlockState(source,block.getDefaultState());world.setBlockState(target,block.getDefaultState());
        TileEntityAnimatedScreenSelector a=(TileEntityAnimatedScreenSelector)world.getTileEntity(source),b=(TileEntityAnimatedScreenSelector)world.getTileEntity(target);
        require(a.isSurfaceTileSides(),"legacy Tile default");a.setSurfaceTileSides(false);a.setSurfaceTexture(0,ScreenHousingTextures.doorIndex(0,0));a.setSurfaceTexture(1,FilesystemTextures.identifier("Absent/second.png"));
        NBTTagCompound captured=ProgrammableSettings.capture(world,source);
        require(ProgrammableSettings.apply(world,target,DuplifierApplyOptions.selected(captured,DuplifierApplyOptions.ALL)),"surface copy apply");
        require(!b.isSurfaceTileSides() && b.getSurfaceTexture(0)==a.getSurfaceTexture(0) && b.getSurfaceTexture(1)==a.getSurfaceTexture(1),"static surface/layout copy");
        NBTTagCompound saved=a.writeToNBT(new NBTTagCompound());b.readFromNBT(saved);require(!b.isSurfaceTileSides(),"saved Fit");saved.removeTag("SurfaceTileSides");b.readFromNBT(saved);require(b.isSurfaceTileSides(),"old save Tile default");
        System.out.println("PASS: console Fit/Tile UVs preserve geometry, cached layouts, legacy defaults and static surface copying");
    }
    private static void compare(ScreenHousingMesh.Face[] before,ScreenHousingMesh.Face[] after) {
        require(before.length==after.length,"face count");
        for(int f=0;f<before.length;f++)for(int v=0;v<before[f].vertices.length;v++) {
            ScreenHousingMesh.Vertex a=before[f].vertices[v],b=after[f].vertices[v];require(a.x==b.x && a.y==b.y && a.z==b.z,"layout moved geometry");require(Double.isFinite(b.u)&&Double.isFinite(b.v)&&b.u>=0&&b.u<=16&&b.v>=0&&b.v<=16,"Fit UV range");
        }
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
