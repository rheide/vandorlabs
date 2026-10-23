package com.vandorlabs.client;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fluids.*;

/** Compare the actual contained-water vertex writer with vanilla, using unequal light channels. */
final class FluidLightmapChecks {
    static void run(Minecraft mc)throws Exception{
        BlockPos pos=new BlockPos(350,48,146);int cases=0;
        java.lang.reflect.Method vertex=WaterloggedRenderer.class.getDeclaredMethod("vertex",BufferBuilder.class,TextureAtlasSprite.class,int.class,Vec3d.class,int.class,float.class,int.class,Vec3d.class);
        vertex.setAccessible(true);
        java.lang.reflect.Method append=ConnectedFluidRenderer.class.getDeclaredMethod("append",BufferBuilder.class,double[].class,BlockPos.class);append.setAccessible(true);
        BlockFluidRenderer vanilla=new BlockFluidRenderer(mc.getBlockColors());
        for(int sky=0;sky<16;sky++)for(int block=0;block<16;block++){
            final int packed=(sky*16<<16)|(block*16);
            IBlockAccess access=new IBlockAccess(){
                public IBlockState getBlockState(BlockPos p){return p.equals(pos)?Blocks.WATER.getDefaultState():Blocks.AIR.getDefaultState();}
                public TileEntity getTileEntity(BlockPos p){return null;}
                public int getCombinedLight(BlockPos p,int minimum){return packed;}
                public boolean isAirBlock(BlockPos p){return !p.equals(pos);}
                public Biome getBiome(BlockPos p){return mc.world.getBiome(pos);}
                public int getStrongPower(BlockPos p,EnumFacing f){return 0;}
                public WorldType getWorldType(){return mc.world.getWorldType();}
                public boolean isSideSolid(BlockPos p,EnumFacing f,boolean fallback){return false;}
            };
            BufferBuilder nativeBuffer=new BufferBuilder(4096);nativeBuffer.begin(7,DefaultVertexFormats.BLOCK);
            vanilla.renderFluid(access,Blocks.WATER.getDefaultState(),pos,nativeBuffer);nativeBuffer.finishDrawing();
            if(nativeBuffer.getVertexCount()==0)throw new IllegalStateException("Vanilla light fixture has no water vertices");
            BufferBuilder contained=new BufferBuilder(4096);contained.begin(7,BlockSurfaceFormat.get());
            vertex.invoke(null,contained,WaterloggedRenderer.sprite(new FluidStack(FluidRegistry.WATER,1000)),1,new Vec3d(0,.888,0),0xFFFFFFFF,1F,packed,new Vec3d(0,1,0));contained.finishDrawing();
            int nativeOffset=nativeBuffer.getVertexFormat().getUvOffsetById(1),containedOffset=contained.getVertexFormat().getUvOffsetById(1);
            for(int component=0;component<2;component++)if(nativeBuffer.getByteBuffer().getShort(nativeOffset+component*2)!=contained.getByteBuffer().getShort(containedOffset+component*2))throw new IllegalStateException("Contained/vanilla water lightmap mismatch: sky="+sky+" block="+block+" component="+component);
            BufferBuilder joined=new BufferBuilder(4096);joined.begin(7,DefaultVertexFormats.BLOCK);
            double[] point={0,.888,0,0,0,255,255,255,255,nativeBuffer.getByteBuffer().getShort(nativeOffset)&65535,nativeBuffer.getByteBuffer().getShort(nativeOffset+2)&65535};
            append.invoke(null,joined,point,pos);joined.finishDrawing();
            for(int component=0;component<2;component++)if(nativeBuffer.getByteBuffer().getShort(nativeOffset+component*2)!=joined.getByteBuffer().getShort(nativeOffset+component*2))throw new IllegalStateException("Connected/vanilla water lightmap mismatch: sky="+sky+" block="+block+" component="+component);
            cases++;
        }
        System.out.println("[vandorlabs][reprolab] fluid-lightmap PASS cases="+cases+" actual vanilla/contained/connected vertex parity all sky/block light levels");
    }
}
