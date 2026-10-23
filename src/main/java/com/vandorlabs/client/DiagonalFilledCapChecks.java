package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.client.Minecraft;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import java.io.File;

/** A flat wall only covers its own strip of a filled diagonal's end. */
final class DiagonalFilledCapChecks {
    static void run(Minecraft mc,File output)throws Exception{
        net.minecraft.world.World world=mc.world;BlockPos pos=new BlockPos(mc.player.posX+3,70,mc.player.posZ+3);int cases=0;
        BlockProgrammableWall diagonal=(BlockProgrammableWall)net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_diagonal_wall"),flat=(BlockProgrammableWall)net.minecraft.block.Block.getBlockFromName("vandorlabs:programmable_wall");
        TextureAtlasSprite sprite=mc.getTextureMapBlocks().getAtlasSprite("vandorlabs:blocks/space_doors/medium/double_frame_metal");
        java.lang.reflect.Field data=StaticSurfaceMesh.class.getDeclaredField("vertices");data.setAccessible(true);
        try{for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})for(boolean upper:new boolean[]{false,true})for(int fill=1;fill<=3;fill++){
            IBlockState state=diagonal.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.INVERTED,inverted);world.setBlockState(pos,state,2);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);tile.setDiagonalGeometry(0,fill);
            double expected=16*com.vandorlabs.render.DiagonalWallGeometry.near(0,inverted,upper?1:0);int depth=0;for(int d=1;d<3;d++)if(Math.abs(PanelDepth.start(d)-expected)<Math.abs(PanelDepth.start(depth)-expected))depth=d;
            BlockPos next=upper?pos.up():pos.down();world.setBlockState(next,flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing).withProperty(BlockProgrammableWall.DEPTH,depth),2);
            require(!Double.isNaN(BlockProgrammableWall.flatEnd(state,world,pos,upper)),"negative control: flat wall actually joins the diagonal");
            require(!BlockProgrammableWall.coveredFlatEnd(state,world,pos,upper),"thin wall cannot cover the entire filled cap");
            float[] vertices=(float[])data.get(DiagonalWallMeshCache.get(tile,state,sprite,sprite));boolean exposed=false;double plane=upper?16:0,near=16*BlockProgrammableWall.flatEnd(state,world,pos,upper);
            for(int i=0;i<vertices.length;i+=8)if(Math.abs(vertices[i+1]-plane)<1e-5&&Math.abs(vertices[i+6])>.5&&(vertices[i+2]<near-1e-5||vertices[i+2]>near+4+1e-5))exposed=true;
            if(fill==3||fill==1&&near>4||fill==2&&near<8)require(exposed,"exposed fill top/bottom texture survives flat contact");
            if(facing==EnumFacing.NORTH&&!inverted&&upper&&fill==1)ContactRenderCapture.save(mc,output,"filled-diagonal-under-wall",60,pos,next);
            world.setBlockToAir(next);cases++;
        }}finally{world.setBlockToAir(pos.up());world.setBlockToAir(pos.down());world.setBlockToAir(pos);}
        System.out.println("[vandorlabs][reprolab] diagonal-filled-caps PASS cases="+cases+" Inside/Outside/Both fills, top/bottom flat contacts and all facings");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("Filled diagonal cap: "+message);}
}
