package com.vandorlabs.client;

import com.google.gson.*;
import com.vandorlabs.blocks.BlockCanopy;
import com.vandorlabs.canopy.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;

/** Final released canopy draw bytes: actual visibility, finish UVs, pose, orientation and emitter. */
public final class CanopyMeshLegacyOracle {
    private static final BlockPos ROOT=new BlockPos(8,80,8);
    private static Field field(Class<?> type,String name)throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static NBTTagCompound input(int profile) {
        NBTTagCompound tag=new NBTTagCompound();
        tag.setInteger("CanopyMount",profile%4);tag.setInteger("CanopyDepth",new int[]{16,0,17,80,24,1}[profile]);
        tag.setInteger("CanopyScale",new int[]{71,10,90,100,71,50}[profile]);tag.setInteger("CanopyShade",profile%3);
        tag.setInteger("CanopyTint",profile%4);tag.setInteger("CanopyMaterial",profile<2?-1:64);
        tag.setInteger("CanopyTextureLayout",profile%3);tag.setBoolean("CanopyTileMaterial",profile%3==1);
        tag.setInteger("CanopyHinge",profile%4);tag.setInteger("CanopyMotion",profile%2);
        tag.setInteger("CanopyAngle",profile%4);tag.setBoolean("CanopyOpenable",true);tag.setBoolean("CanopySlideForward",profile>=3);
        return tag;
    }
    public static void main(String[] args)throws Exception {
        List<BlockCanopy> blocks=CanopyPlacementLegacyOracle.initialize();
        Class<?> draw=Class.forName("com.vandorlabs.client.TECanopy$DrawFace");Constructor<?> constructor=draw.getDeclaredConstructor();constructor.setAccessible(true);
        Field source=field(draw,"source"),vertices=field(draw,"v"),opacity=field(draw,"opacity"),center=field(draw,"center");
        Method orient=TECanopy.class.getDeclaredMethod("orient",List.class,EnumFacing.class);orient.setAccessible(true);
        Method emit=TECanopy.class.getDeclaredMethod("emit",BufferBuilder.class,List.class);emit.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,TextureAtlasSprite> sprites=(Map<String,TextureAtlasSprite>)field(TECanopy.class,"SPRITES").get(null);
        TECanopy renderer=new TECanopy();TextureAtlasSprite sprite=new TextureAtlasSprite("canopy-source-reference"){};
        sprite.setIconWidth(64);sprite.setIconHeight(64);sprite.initSprite(256,256,64,128,false);
        File file=new File(args[0]);file.getAbsoluteFile().getParentFile().mkdirs();Gson gson=new Gson();int profiles=0;long emitted=0;
        try(PrintWriter out=new PrintWriter(file,"UTF-8");PrintWriter inputs=args.length>1?new PrintWriter(args[1],"UTF-8"):null) {
            for(BlockCanopy block:blocks)for(EnumFacing facing:EnumFacing.HORIZONTALS)for(int profile=0;profile<6;profile++)for(double progress:new double[]{0,.0625,.5,.9375,1}) {
                final double aspect=profile==4?2:profile==5?.5:1;
                com.vandorlabs.VandorLabs.proxy=new com.vandorlabs.CommonProxy(){@Override public double materialAspect(int ignored){return aspect;}};
                CanopyPlacementLegacyOracle.World world=new CanopyPlacementLegacyOracle.World();world.setBlockState(ROOT,block.getDefaultState().withProperty(BlockCanopy.FACING,facing),2);
                TileEntityCanopy tile=(TileEntityCanopy)world.getTileEntity(ROOT);tile.anchor=ROOT;tile.centeredPlacement=CanopyVariants.centered(block.kind);tile.hollowPlacement=tile.resizable();NBTTagCompound input=input(profile);tile.large.read(input);
                List<CanopyMesh.Face> base=tile.baseFaces();
                if(facing==EnumFacing.SOUTH&&progress==0)CanopyMeshInputsLegacy.write(inputs,block.kind+":"+profile,tile,sprite);
                List<CanopyMesh.Face> posed=new CanopyRenderGeometry(base).posed(block.kind,tile.width(),tile.large,tile.opening()?progress:0,tile.centeredPlacement);
                List<Object> opaque=new ArrayList<>(),glass=new ArrayList<>();JsonArray materials=new JsonArray();
                for(CanopyMesh.Face face:posed){sprites.put(face.material,sprite);Object d=constructor.newInstance();source.set(d,face);vertices.set(d,new Vec3d[face.vertices.length]);opacity.setFloat(d,CanopyFinishes.opacity(face,tile.large.material,tile.large.tint));(CanopyFinishes.translucent(face,tile.large.material,tile.large.tint)?glass:opaque).add(d);JsonObject material=new JsonObject();material.addProperty("material",face.material);material.addProperty("base",face.baseMaterial);material.addProperty("group",face.group);material.addProperty("glazing",face.glazing);material.addProperty("tiled",face.tiled);material.addProperty("double",face.doubleSided);material.addProperty("points",face.vertices.length);materials.add(material);}
                orient.invoke(null,opaque,facing);orient.invoke(null,glass,facing);
                glass.sort((a,b)->{try{return Double.compare(((Vec3d)center.get(b)).squareDistanceTo(new Vec3d(2.5,3,-4)),((Vec3d)center.get(a)).squareDistanceTo(new Vec3d(2.5,3,-4)));}catch(IllegalAccessException error){throw new AssertionError(error);}});
                JsonObject row=new JsonObject();row.addProperty("id",block.kind);row.addProperty("width",tile.width());row.addProperty("facing",facing.getIndex());row.addProperty("profile",profile);row.addProperty("progress",progress);row.addProperty("aspect",aspect);row.addProperty("centered",tile.centeredPlacement);row.add("settings",CanopySettingsLegacyOracle.tags(input));row.add("materials",materials);
                JsonArray layers=new JsonArray();
                for(List<Object> faces:Arrays.asList(opaque,glass)) {
                    BufferBuilder buffer=new BufferBuilder(4096);buffer.begin(org.lwjgl.opengl.GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);emit.invoke(renderer,buffer,faces);buffer.finishDrawing();
                    ByteBuffer bytes=buffer.getByteBuffer().duplicate();byte[] data=new byte[bytes.remaining()];bytes.get(data);int count=buffer.getVertexCount();
                    if(data.length!=count*28)throw new AssertionError("Actual Source canopy format stride");
                    JsonObject layer=new JsonObject();layer.addProperty("faces",faces.size());layer.addProperty("vertices",count);layer.addProperty("bytes",Base64.getEncoder().encodeToString(data));layers.add(layer);emitted+=count;
                }
                row.add("layers",layers);out.println(gson.toJson(row));profiles++;
            }
        }
        SegmentedReferenceGolden.check(file,"e6049daaafd96c9e9765fe03a61f4f8a60f5d84b9049439473007111b818fcb1");
        if(args.length>1)SegmentedReferenceGolden.check(new File(args[1]),"fc5b7dfdcfd9964515bb6149829259e3cab87fcaed2c6fc174f2bff976065e05");
        System.out.println("Actual standalone canopy final mesh PASS profiles="+profiles+" vertices="+emitted+" file="+file);
    }
}
