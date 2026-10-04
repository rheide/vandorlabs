package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.model.pipeline.LightUtil;
import com.google.gson.*;
import java.io.*;
import java.util.*;

/** Export actual chunk quads for offline bounds and coplanar-merge investigation. */
public final class DiagonalMeshSurvey {
    public static void main(String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        BlockProgrammableWall block=new BlockProgrammableWall("programmable_diagonal_wall",BlockProgrammableWall.Shape.DIAGONAL);
        BlockProgrammableWall flat=new BlockProgrammableWall("programmable_wall",BlockProgrammableWall.Shape.PLAIN);
        NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(true);
        BlockPos pos=new BlockPos(8,88,8);
        TextureAtlasSprite wall=sprite("wall",0),metal=sprite("metal",32);
        try(PrintWriter out=new PrintWriter(new File("meshes.jsonl"))) {
            for(EnumFacing facing:EnumFacing.HORIZONTALS)for(boolean inverted:new boolean[]{false,true})
                for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++)for(int neighbor=0;neighbor<10;neighbor++) {
                    world.clear();IBlockState state=block.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing)
                            .withProperty(BlockProgrammableWall.INVERTED,inverted);
                    world.setBlockState(pos,state,2);
                    ((TileEntityAnimatedScreenSelector)world.getTileEntity(pos)).setDiagonalGeometry(mode,fill);
                    if(neighbor==1)world.setBlockState(pos.offset(facing),net.minecraft.init.Blocks.STONE.getDefaultState(),2);
                    if(neighbor==2)world.setBlockState(pos.up(),net.minecraft.init.Blocks.STONE.getDefaultState(),2);
                    if(neighbor>=3 && neighbor<=5) {
                        BlockPos next=neighbor==3?pos.offset(facing):neighbor==4?pos.up():pos.offset(facing.getOpposite());
                        world.setBlockState(next,neighbor==3?state.withProperty(BlockProgrammableWall.FACING,facing.rotateY()):state,2);
                        ((TileEntityAnimatedScreenSelector)world.getTileEntity(next)).setDiagonalGeometry(mode,fill);
                    }
                    if(neighbor==6 || neighbor==7)world.setBlockState(neighbor==6?pos.down():pos.up(),
                            flat.getDefaultState().withProperty(BlockProgrammableWall.FACING,facing),2);
                    if(neighbor==8) {
                        for(EnumFacing side:new EnumFacing[]{facing,facing.getOpposite()}) {
                            BlockPos next=pos.offset(side);
                            world.setBlockState(next,state.withProperty(BlockProgrammableWall.FACING,facing.rotateY()),2);
                            ((TileEntityAnimatedScreenSelector)world.getTileEntity(next)).setDiagonalGeometry(mode,fill);
                        }
                    }
                    if(neighbor==9)world.setBlockState(pos.offset(facing.rotateY()),net.minecraft.init.Blocks.STONE.getDefaultState(),2);
                    List<BakedQuad> mesh=DiagonalWallModel.bake(new DiagonalWallState(state,world,pos),wall,metal);
                    JsonObject row=new JsonObject();row.addProperty("facing",facing.getName());row.addProperty("inverted",inverted);
                    row.addProperty("mode",mode);row.addProperty("fill",fill);row.addProperty("neighbor",neighbor);
                    JsonArray quads=new JsonArray();
                    // Back faces are exact reversals; analyze each original surface once.
                    for(int q=0;q<mesh.size();q+=2) {
                        BakedQuad quad=mesh.get(q);JsonObject face=new JsonObject();face.addProperty("sprite",quad.getSprite().getIconName());
                        JsonArray points=new JsonArray();
                        for(int v=0;v<4;v++) {
                            JsonArray point=new JsonArray();float[] data=new float[4];
                            for(int element:new int[]{0,2,3}) {
                                LightUtil.unpack(quad.getVertexData(),data,quad.getFormat(),v,element);
                                for(int i=0;i<(element==2?2:3);i++)point.add(data[i]);
                            }
                            points.add(point);
                        }
                        face.add("points",points);quads.add(face);
                    }
                    row.add("quads",quads);out.println(row);
                }
        }
        System.out.println("Exported 960 diagonal wall fixtures to meshes.jsonl");
    }
    private static TextureAtlasSprite sprite(String name,int x) {
        TextureAtlasSprite sprite=new TextureAtlasSprite(name){};sprite.setIconWidth(16);sprite.setIconHeight(16);
        sprite.initSprite(64,64,x,0,false);return sprite;
    }
}
