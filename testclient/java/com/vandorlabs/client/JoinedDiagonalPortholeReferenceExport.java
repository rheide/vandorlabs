package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.TileEntityAnimatedScreenSelector;
import java.lang.reflect.*;
import java.io.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Complete current drawConfiguredWall path including manual/Auto joined arms and solid nodes. */
public final class JoinedDiagonalPortholeReferenceExport {
    private static TextureAtlasSprite sprite(String id,int x){TextureAtlasSprite s=new TextureAtlasSprite(id){};s.setIconWidth(16);s.setIconHeight(16);s.initSprite(32,16,x,0,false);return s;}
    private static void number(JsonObject row,String key,double value){if(Double.isNaN(value))row.add(key,JsonNull.INSTANCE);else row.addProperty(key,value);}
    public static void main(String[] args)throws Exception{
        net.minecraft.init.Bootstrap.register();TextureAtlasSprite wall=sprite("joined_diagonal",0),metal=sprite("joined_inner",16);Field vertices=StaticSurfaceMesh.class.getDeclaredField("vertices");vertices.setAccessible(true);
        Method adjacent=TEAnimatedScreenSelector.class.getDeclaredMethod("adjacentPorthole",TileEntityAnimatedScreenSelector.class,net.minecraft.block.state.IBlockState.class,EnumFacing.class);adjacent.setAccessible(true);Method rowDirection=TEAnimatedScreenSelector.class.getDeclaredMethod("portholeRowDirection",TileEntityAnimatedScreenSelector.class,net.minecraft.block.state.IBlockState.class,boolean.class);rowDirection.setAccessible(true);
        BlockProgrammableWall block=new BlockProgrammableWall("diagonal_porthole_wall",BlockProgrammableWall.Shape.DIAGONAL_PORTHOLE),flat=new BlockProgrammableWall("programmable_wall",BlockProgrammableWall.Shape.PLAIN);File out=new File(System.getProperty("vandorlabs.joinedDiagonalPortholeReference","build/test-artifacts/joined-diagonal-porthole-forge.jsonl"));out.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int records=0,solids=0,arms=0;
        try(PrintWriter writer=new PrintWriter(out,"UTF-8")){
            for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int profile:new int[]{0,6})for(int join=0;join<12;join++)for(int shape=0;shape<4;shape++){
                int fill=join%4;NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);DiagonalGeometryLegacyChecks.install(world,BlockPos.ORIGIN,block,EnumFacing.NORTH,inverted,mode,fill,join);DiagonalGeometryLegacyChecks.profile(world,block,flat,EnumFacing.NORTH,inverted,mode,profile);net.minecraft.block.state.IBlockState state=world.getBlockState(BlockPos.ORIGIN);TileEntityAnimatedScreenSelector tile=(TileEntityAnimatedScreenSelector)world.getTileEntity(BlockPos.ORIGIN);boolean solid=TEAnimatedScreenSelector.solidDiagonalPortholeJunction(tile,state);DiagonalPortholeMesh projection=TEAnimatedScreenSelector.portholeMesh(tile,state);PortholeHex.Slice slice=new PortholeHex(1,1,shape).slice(0,0);
                StaticSurfaceMesh.Capture capture=StaticSurfaceMesh.capture();TEAnimatedScreenSelector.drawConfiguredWall(capture,tile,state,wall,metal,solid?null:slice);StaticSurfaceMesh body=capture.finish();capture=StaticSurfaceMesh.capture();if(!solid)TEAnimatedScreenSelector.drawPortholeGlassGeometry(capture,slice,projection);StaticSurfaceMesh pane=capture.finish();
                for(int layout=0;layout<3;layout++){
                    JsonObject r=new JsonObject();r.addProperty("mode",mode);r.addProperty("inverted",inverted);r.addProperty("fill",fill);r.addProperty("profile",profile);r.addProperty("join",join);r.addProperty("shape",shape);r.addProperty("layout",layout);r.addProperty("solid",solid);number(r,"lower",projection.lower);number(r,"upper",projection.upper);r.add("clip",gson.toJsonTree(projection.bounds));
                    BlockProgrammableWall.Corner corner=mode==1?block.corner(state,world,BlockPos.ORIGIN):null;
                    if(corner==null)r.add("corner",JsonNull.INSTANCE);else{JsonObject c=new JsonObject();if(corner.frontRight!=null)c.addProperty("frontRight",corner.frontRight);if(corner.backRight!=null)c.addProperty("backRight",corner.backRight);c.addProperty("straightLeft",corner.straightLeft);c.addProperty("straightRight",corner.straightRight);if(corner.frontFlat!=null)c.addProperty("frontFlat",corner.frontFlat);if(corner.backFlat!=null)c.addProperty("backFlat",corner.backFlat);r.add("corner",c);arms++;}
                    r.addProperty("hideLower",DiagonalPanelGeometry.coveredEnd(world,BlockPos.ORIGIN,state,false)||BlockProgrammableWall.coveredFlatEnd(state,world,BlockPos.ORIGIN,false));r.addProperty("hideUpper",DiagonalPanelGeometry.coveredEnd(world,BlockPos.ORIGIN,state,true)||BlockProgrammableWall.coveredFlatEnd(state,world,BlockPos.ORIGIN,true));
                    r.addProperty("left",(Boolean)adjacent.invoke(null,tile,state,EnumFacing.WEST));r.addProperty("right",(Boolean)adjacent.invoke(null,tile,state,EnumFacing.EAST));r.addProperty("lowerContact",(Boolean)adjacent.invoke(null,tile,state,(EnumFacing)rowDirection.invoke(null,tile,state,false)));r.addProperty("upperContact",(Boolean)adjacent.invoke(null,tile,state,(EnumFacing)rowDirection.invoke(null,tile,state,true)));
                    GlassSurfaceGroups.Layout uv=new GlassSurfaceGroups.Layout(0,0,1,1);r.add("body",gson.toJsonTree(vertices.get(layout==0?body:GlassSurfaceMapping.map(body,wall,uv,false,0,0,layout))));r.add("pane",gson.toJsonTree(vertices.get(GlassSurfaceMapping.map(pane,wall,uv,true,2D/16,4D/16,layout))));writer.println(gson.toJson(r));records++;if(solid)solids++;
                }
            }
        }
        if(records!=1728||solids==0||arms==0)throw new AssertionError("Incomplete joined matrix "+records+" solid="+solids+" arms="+arms);System.out.println("Joined diagonal porthole actual complete PASS records="+records+" solid="+solids+" corner="+arms);
    }
}
