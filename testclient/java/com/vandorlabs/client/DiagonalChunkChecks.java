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
        BlockPos pos=new BlockPos(8,88,8);int cases=0;
        TextureAtlasSprite wall=sprite("wall",0),metal=sprite("metal",32);
        IBlockAccess cache=(IBlockAccess)java.lang.reflect.Proxy.newProxyInstance(IBlockAccess.class.getClassLoader(),new Class<?>[]{IBlockAccess.class},
                (proxy,method,args)->method.invoke(world,args));
        for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})
            for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++)for(int neighbor=0;neighbor<6;neighbor++) {
                world.clear();IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing)
                        .withProperty(BlockProgrammableWall.INVERTED,inverted);
                world.setBlockState(pos,state,2);
                TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(pos);
                tile.setDiagonalGeometry(mode,fill);
                if(neighbor==1)world.setBlockState(pos.offset(facing),Blocks.STONE.getDefaultState(),2);
                if(neighbor==2)world.setBlockState(pos.up(),Blocks.STONE.getDefaultState(),2);
                if(neighbor>=3) {
                    BlockPos next=neighbor==3?pos.offset(facing):neighbor==4?pos.up():pos.offset(facing.getOpposite());
                    world.setBlockState(next,neighbor==3?state.withProperty(BlockProgrammableWall.FACING,facing.rotateY()):state,2);
                    ((TileEntityAnimatedScreenSelector)world.getTileEntity(next)).setDiagonalGeometry(mode,fill);
                }
                DiagonalWallState shape=new DiagonalWallState(state,cache,pos);
                IBlockState extended=block.getExtendedState(state,cache,pos);
                require(extended instanceof IExtendedBlockState,"missing extended state");
                require(extended.getPackedLightmapCoords(cache,pos.up())==world.getCombinedLight(pos,0),"neighbor sample changed uniform owner light");
                require(!tile.shouldRenderInPass(0),"interior tile still renders");
                List<BakedQuad> quads=DiagonalWallModel.bake(shape,wall,metal);
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
                    for(int e=0;e<format.getElementCount();e++) {
                        LightUtil.unpack(quads.get(q).getVertexData(),actual,format,v,e);
                        switch(format.getElement(e).getUsage()) {
                            case UV:if(format.getElement(e).getIndex()==0) {
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
                    for(int e=0;e<format.getElementCount();e++) {
                        float[] back=new float[4],front=new float[4];
                        LightUtil.unpack(quads.get(q).getVertexData(),front,format,v,e);
                        LightUtil.unpack(quads.get(q+1).getVertexData(),back,format,(4-v)&3,e);
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
        System.out.println("PASS: "+cases+" diagonal chunk snapshots match TESR positions, UVs, normals and two-sided faces; uniform light and chunk-edge fallback preserved");
    }
    private static TextureAtlasSprite sprite(String name,int x) {
        TextureAtlasSprite sprite=new TextureAtlasSprite(name){};sprite.setIconWidth(16);sprite.setIconHeight(16);sprite.initSprite(64,64,x,0,false);return sprite;
    }
    private static void near(double a,double b,String name){if(Math.abs(a-b)>1E-6)throw new AssertionError(name+": "+a+" != "+b);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
