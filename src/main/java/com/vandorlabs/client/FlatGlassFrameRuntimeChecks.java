package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityProgrammableGlass;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Probe opaque frame surfaces at the glass cross's bridge and every arm. */
final class FlatGlassFrameRuntimeChecks {
    static void check(World world,BlockPos root,String[] ids) {
        TextureAtlasSprite rail=Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/space_doors/medium/double_frame_metal");
        int count=0;
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int hands=0;hands<4;hands++)for(int assignment=0;assignment<81;assignment++) {
            EnumFacing left=facing.rotateYCCW(),right=facing.rotateY();
            BlockPos[] cells={root,root.offset(facing),root.offset(facing.getOpposite()),root.offset(left),root.offset(right)};
            EnumFacing[] directions={facing,(hands&1)==0?left:right,(hands&2)==0?left:right,facing,facing};
            try {
                int code=assignment;
                for(int i=0;i<cells.length;i++) {
                    int material=i==0?1:code%3;if(i>0)code/=3;
                    put(world,cells[i],ids[material],directions[i]);
                }
                IBlockState state=world.getBlockState(root);
                BlockProgrammableWall.FlatCorner corner=PanelJoins.flatCorner(state,world,root);
                require(corner!=null && corner.frontArm==6 && corner.backArm==6 && corner.straightLeft && corner.straightRight,"cross fixture lost a branch");
                require(PanelJoins.flatBar(state,world,root)==null,"cross entered T rendering");
                TEProgrammableGlass.CornerMeshes meshes=TEProgrammableGlass.meshes((TileEntityProgrammableGlass)world.getTileEntity(root),state,corner);
                require(meshes.frame.samplesOnly(rail),"cross cap samples outside the metal texture");
                double[][] probes={{2,8},{8,2},{8,8},{8,14},{14,8}};
                for(double[] point:probes) {
                    require(capAt(meshes.frame,point[0],point[1],true),"transparent glass exposed through cross roof at "+point[0]+","+point[1]);
                    require(capAt(meshes.frame,point[0],point[1],false),"transparent glass exposed through cross floor at "+point[0]+","+point[1]);
                }
                require(meshes==TEProgrammableGlass.meshes((TileEntityProgrammableGlass)world.getTileEntity(root),state,corner),"unchanged cross frame rebuilt");
                count++;
            } finally {for(BlockPos cell:cells)world.setBlockToAir(cell);}
        }
        System.out.println("[vandorlabs] flat-glass-cross-frame PASS "+count+" material/facing fixtures");
    }
    private static boolean capAt(StaticSurfaceMesh frame,double x,double z,boolean top) {
        return frame.box(x-.1,top?16.005:-.02,z-.1,x+.1,top?16.02:-.005,z+.1,false).vertexCount()>0;
    }
    private static void put(World world,BlockPos pos,String id,EnumFacing facing) {
        Block block=Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",id));
        if(block==null)throw new IllegalStateException("Missing frame fixture "+id);
        IBlockState state=block.getDefaultState();
        if(block instanceof BlockProgrammableGlass)state=state.withProperty(BlockGlassWall.ROTATED,facing.getAxis()==EnumFacing.Axis.X).withProperty(BlockGlassWall.DEPTH,0);
        else state=state.withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,0);
        world.setBlockState(pos,state,3);
        TileEntity tile=world.getTileEntity(pos);
        if(tile instanceof TileEntityProgrammableGlass)((TileEntityProgrammableGlass)tile).setPanelFacing(facing);
    }
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
    private FlatGlassFrameRuntimeChecks() { }
}
