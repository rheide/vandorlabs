package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.common.property.IExtendedBlockState;
import java.util.List;

/** Compare chunk quads with the existing TESR emitter, including neighbor-dependent shapes. */
final class DiagonalChunkChecks {
    static void run() {
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        BlockProgrammableWall block=(BlockProgrammableWall)ModBlocks.PROGRAMMABLE_DIAGONAL_WALL;
        require(block.getBlockLayer()==net.minecraft.util.BlockRenderLayer.CUTOUT_MIPPED,"diagonal material alpha test was lost");
        BlockProgrammableWall flat=new BlockProgrammableWall("diagonal_merge_flat_fixture",BlockProgrammableWall.Shape.PLAIN);
        BlockPos pos=new BlockPos(8,88,8);int cases=0,originalQuads=0,mergedQuads=0;
        TextureAtlasSprite wall=sprite("wall",0),metal=sprite("metal",32);
        IBlockAccess cache=(IBlockAccess)java.lang.reflect.Proxy.newProxyInstance(IBlockAccess.class.getClassLoader(),new Class<?>[]{IBlockAccess.class},
                (proxy,method,args)->method.invoke(world,args));
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})
            for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++)for(int neighbor=0;neighbor<10;neighbor++) {
                world.clear();IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing)
                        .withProperty(BlockProgrammableWall.INVERTED,inverted);
                world.setBlockState(pos,state,2);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                tile.setDiagonalGeometry(mode,fill);
                if(neighbor==1)world.setBlockState(pos.offset(facing),Blocks.STONE.getDefaultState(),2);
                if(neighbor==2)world.setBlockState(pos.up(),Blocks.STONE.getDefaultState(),2);
                if(neighbor>=3 && neighbor<=5) {
                    BlockPos next=neighbor==3?pos.offset(facing):neighbor==4?pos.up():pos.offset(facing.getOpposite());
                    world.setBlockState(next,neighbor==3?state.withProperty(BlockProgrammableWall.FACING,facing.rotateY()):state,2);
                    ((TileEntityAnimatedScreenSelector)world.getTileEntity(next)).setDiagonalGeometry(mode,fill);
                }
                if(neighbor==6 || neighbor==7)world.setBlockState(neighbor==6?pos.down():pos.up(),
                        flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing),2);
                if(neighbor==8)for(EnumFacing side:new EnumFacing[]{facing,facing.getOpposite()}) {
                    BlockPos next=pos.offset(side);
                    world.setBlockState(next,state.withProperty(BlockProgrammableWall.FACING,facing.rotateY()),2);
                    ((TileEntityAnimatedScreenSelector)world.getTileEntity(next)).setDiagonalGeometry(mode,fill);
                }
                if(neighbor==9)world.setBlockState(pos.offset(facing.rotateY()),Blocks.STONE.getDefaultState(),2);
                DiagonalWallState shape=new DiagonalWallState(state,cache,pos);
                IBlockState extended=block.getExtendedState(state,cache,pos);
                require(extended instanceof IExtendedBlockState,"missing extended state");
                require(extended.getPackedLightmapCoords(cache,pos.up())==world.getCombinedLight(pos,0),"neighbor sample changed uniform owner light");
                require(!tile.shouldRenderInPass(0),"interior tile still renders");
                List<BakedQuad> quads=DiagonalWallModel.bake(shape,wall,metal,false);
                List<BakedQuad> merged=DiagonalWallModel.bake(shape,wall,metal);
                DiagonalMergeChecks.compare(quads,merged);
                originalQuads+=quads.size();mergedQuads+=merged.size();
                if(neighbor==0 && fill==0)require(merged.size()==20,"simple wall did not reduce to twenty two-sided quads");
                checkVanillaBuffer(cache,extended,pos,merged);
                checkVanillaBuffer(cache,extended,pos,quads);
                BufferBuilder reference=new BufferBuilder(65536);VertexFormat format=BlockSurfaceFormat.get();
                reference.begin(7,format);TEAnimatedScreenSelector.drawConfiguredDiagonalWall(reference,tile,state,wall,metal);
                int vertices=reference.getVertexCount();reference.finishDrawing();
                require(quads.size()*2==vertices,"two-sided surface count differs");
                java.nio.ByteBuffer data=reference.getByteBuffer();int stride=format.getNextOffset();
                int turns=((int)(180-facing.getHorizontalAngle())/90)&3;
                for(int q=0;q<quads.size();q+=2)for(int v=0;v<4;v++) {
                    int offset=(q/2*4+v)*stride;
                    double x=data.getFloat(offset)/16D,y=data.getFloat(offset+4)/16D,z=data.getFloat(offset+8)/16D;
                    float[] actual=new float[4];LightUtil.unpack(quads.get(q).getVertexData(),actual,quads.get(q).getFormat(),v,0);
                    near(actual[0],turns==1?z:turns==2?1-x:turns==3?1-z:x,"X");near(actual[1],y,"Y");
                    near(actual[2],turns==1?1-x:turns==2?1-z:turns==3?x:z,"Z");
                    VertexFormat bakedFormat=quads.get(q).getFormat();
                    for(int e=0;e<bakedFormat.getElementCount();e++) {
                        LightUtil.unpack(quads.get(q).getVertexData(),actual,bakedFormat,v,e);
                        switch(bakedFormat.getElement(e).getUsage()) {
                            case UV:if(bakedFormat.getElement(e).getIndex()==0) {
                                near(actual[0],data.getFloat(offset+format.getUvOffsetById(0)),"U");
                                near(actual[1],data.getFloat(offset+format.getUvOffsetById(0)+4),"V");
                            }break;
                            case NORMAL:
                                double nx=data.get(offset+format.getNormalOffset())/127D,ny=data.get(offset+format.getNormalOffset()+1)/127D,nz=data.get(offset+format.getNormalOffset()+2)/127D;
                                near(actual[0],turns==1?nz:turns==2?-nx:turns==3?-nz:nx,"normal X");near(actual[1],ny,"normal Y");
                                near(actual[2],turns==1?-nx:turns==2?-nz:turns==3?nx:nz,"normal Z");break;
                            default:break;
                        }
                    }
                    for(int e=0;e<bakedFormat.getElementCount();e++) {
                        float[] back=new float[4],front=new float[4];
                        LightUtil.unpack(quads.get(q).getVertexData(),front,bakedFormat,v,e);
                        LightUtil.unpack(quads.get(q+1).getVertexData(),back,bakedFormat,(4-v)&3,e);
                        require(java.util.Arrays.equals(front,back),"back face changed attributes");
                    }
                }
                cases++;
            }
        for(int x:new int[]{-17,-16,-15,-1,0,1,14,15,16,17})for(int y:new int[]{79,80,81,94,95,96}) {
            BlockPos edge=new BlockPos(x,y,8);IBlockState state=block.getDefaultState();
            boolean expected=(x&15)>0 && (x&15)<15 && (y&15)>0 && (y&15)<15;
            require(DiagonalWallState.baked(state,edge)==expected,"chunk-edge fallback differs");
        }
        TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
        net.minecraft.nbt.NBTTagCompound changed=tile.getUpdateTag();
        changed.setBoolean("DiagonalHalfHeight",!tile.isDiagonalHalfHeight());
        tile.onDataPacket(null,new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos,0,changed));
        require(pos.add(-1,-1,-1).equals(world.renderMin) && pos.add(1,1,1).equals(world.renderMax),
                "packet geometry change did not invalidate neighboring chunks");
        System.out.println("PASS: merged diagonal quads "+originalQuads+" -> "+mergedQuads+"; bidirectional surface, UV, normal and area checks");
        System.out.println("PASS: "+cases+" diagonal chunk snapshots match TESR positions, UVs, normals and two-sided faces; vanilla packed output, uniform light and chunk-edge fallback preserved");
    }
    /** Exercise the raw-copy renderer used when Forge's light pipeline is disabled. */
    private static void checkVanillaBuffer(IBlockAccess world,IBlockState state,BlockPos pos,List<BakedQuad> quads) {
        net.minecraft.client.renderer.block.model.IBakedModel model=
                (net.minecraft.client.renderer.block.model.IBakedModel)java.lang.reflect.Proxy.newProxyInstance(
                        DiagonalChunkChecks.class.getClassLoader(),
                        new Class<?>[]{net.minecraft.client.renderer.block.model.IBakedModel.class},
                        (proxy,method,args)-> {
                            if(method.getName().equals("getQuads"))return args[1]==null?quads:java.util.Collections.emptyList();
                            throw new AssertionError("Unexpected model call: "+method.getName());
                        });
        VertexFormat format=net.minecraft.client.renderer.vertex.DefaultVertexFormats.BLOCK;
        BufferBuilder buffer=new BufferBuilder(65536);buffer.begin(7,format);
        new net.minecraft.client.renderer.BlockModelRenderer(new net.minecraft.client.renderer.color.BlockColors())
                .renderModelFlat(world,model,state,pos,buffer,false,0);
        require(buffer.getVertexCount()==quads.size()*4,"vanilla vertex count differs");
        buffer.finishDrawing();java.nio.ByteBuffer data=buffer.getByteBuffer();
        for(int q=0;q<quads.size();q++)for(int v=0;v<4;v++) {
            int offset=(q*4+v)*format.getNextOffset();float[] point=new float[4];
            BakedQuad quad=quads.get(q);LightUtil.unpack(quad.getVertexData(),point,quad.getFormat(),v,0);
            near(data.getFloat(offset),point[0]+pos.getX(),"vanilla X");
            near(data.getFloat(offset+4),point[1]+pos.getY(),"vanilla Y");
            near(data.getFloat(offset+8),point[2]+pos.getZ(),"vanilla Z");
            require(data.getInt(offset+format.getColorOffset())==-1,"vanilla color corrupted");
            require(data.getInt(offset+format.getUvOffsetById(1))==state.getPackedLightmapCoords(world,pos),"vanilla lighting corrupted");
            LightUtil.unpack(quad.getVertexData(),point,quad.getFormat(),v,2);
            near(data.getFloat(offset+format.getUvOffsetById(0)),point[0],"vanilla U");
            near(data.getFloat(offset+format.getUvOffsetById(0)+4),point[1],"vanilla V");
        }
    }
    private static TextureAtlasSprite sprite(String name,int x) {
        TextureAtlasSprite sprite=new TextureAtlasSprite(name){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(64,64,x,0,false);return sprite;
    }
    private static void near(double a,double b,String name){if(!Double.isFinite(a) || !Double.isFinite(b) || Math.abs(a-b)>1E-6)throw new AssertionError(name+": "+a+" != "+b);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
