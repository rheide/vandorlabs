package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.render.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

/** CPU native quad projection against actual current Forge output; no game/client launch. */
public final class NativeHousingQuadChecks {
    private static final class Sprite extends TextureAtlasSprite {
        Sprite(SpriteContents contents){super(TextureAtlas.LOCATION_BLOCKS,contents,16,16,0,0);}
    }
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static ProgrammableModel.MaterialSnapshot material(TextureAtlasSprite sprite,int choice){return new ProgrammableModel.MaterialSnapshot(sprite,choice==1288700935?.5F:1,choice==150?.5F:1);}
    private static void padding(JsonObject row,TextureAtlasSprite sprite,int tread,int roll){
        var points=NativeHousingQuads.stairs(Direction.EAST,false,StairGeometry.Shape.STRAIGHT,tread|(roll<<5),sprite);
        var model=new net.neoforged.neoforge.client.model.BakedModelWrapper<net.minecraft.client.resources.model.BakedModel>(null){
            @Override public List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(net.minecraft.world.level.block.state.BlockState state,Direction face,net.minecraft.util.RandomSource random,net.neoforged.neoforge.client.model.data.ModelData data,net.minecraft.client.renderer.RenderType layer){return face==null?points:List.of();}
        };
        var none=net.minecraft.client.renderer.block.model.ItemTransform.NO_TRANSFORM;
        var gui=new net.minecraft.client.renderer.block.model.ItemTransform(new org.joml.Vector3f(30,135,0),new org.joml.Vector3f(),new org.joml.Vector3f(.625F));
        var base=new net.minecraft.client.renderer.block.model.ItemTransforms(none,none,none,none,none,gui,none,none,com.google.common.collect.ImmutableMap.of());
        var padded=NativeItemPadding.fit(model,base).gui;float[] actual={padded.rotation.x(),padded.rotation.y(),padded.rotation.z(),padded.translation.x(),padded.translation.y(),padded.translation.z(),padded.scale.x(),padded.scale.y(),padded.scale.z()};
        for(int i=0;i<9;i++)need(Float.floatToRawIntBits(actual[i])==Float.floatToRawIntBits(row.getAsJsonArray("gui").get(i).getAsFloat()),"item GUI transform "+row.get("key")+" axis="+i);
    }
    public static void main(String[] ignored)throws Exception{
        String file=System.getProperty("vandorlabs.shapeTextureReference","");if(file.isEmpty())throw new IllegalArgumentException("shapeTextureReference is required");
        var path=Path.of(file);var digest=MessageDigest.getInstance("SHA-256");try(var in=new java.security.DigestInputStream(Files.newInputStream(path),digest)){in.transferTo(java.io.OutputStream.nullOutputStream());}need(HexFormat.of().formatHex(digest.digest()).equals("73555d9b877d2f479531646310d86878696c47abb9b36cf6f34b65563dcf7cdc"),"frozen actual Forge reference");
        try(var image=new NativeImage(16,16,false);var contents=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs","shape-reference"),new FrameSize(16,16),image,ResourceMetadata.EMPTY)){
            var sprite=new Sprite(contents);int slabs=0,stairs=0,items=0;long vertices=0;
            try(var input=Files.newBufferedReader(path)){String line;while((line=input.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();if(!row.has("kind"))continue;if(row.get("kind").getAsString().equals("item")){String[] itemKey=row.get("key").getAsString().split("/");if(itemKey.length==5&&itemKey[0].equals("stairs")){padding(row,sprite,Integer.parseInt(itemKey[1]),Integer.parseInt(itemKey[2]));items++;}continue;}String[] key=row.get("key").getAsString().split("/");boolean slab=key[0].equals("slab");var facing=Direction.byName(key[1]);boolean upper=Boolean.parseBoolean(key[2]);int tread=Integer.parseInt(key[4]),roll=Integer.parseInt(key[5]),layout=Integer.parseInt(key[6]);if(!slab&&tread==8&&roll==0)continue;
                var expected=row.getAsJsonArray("quads");List<net.minecraft.client.renderer.block.model.BakedQuad> source=slab?List.of():NativeHousingQuads.stairs(facing,upper,StairGeometry.Shape.valueOf(key[3]),tread|(roll<<5),sprite);need(slab?expected.size()>=5&&expected.size()<=6:source.size()==expected.size(),"quad count "+row.get("key"));
                for(int i=0;i<expected.size();i++){
                    var ref=expected.get(i).getAsJsonObject();var direction=Direction.byName(ref.get("face").getAsString());int meshFace=0;if(slab){int[] locals={1,0,4,5,2,3};while(meshFace<6&&HousingTexturePolicy.housingWorldFace(locals[meshFace],facing.get3DDataValue())!=direction.get3DDataValue())meshFace++;need(meshFace<6,"slab world face mapping");}int face=slab?new int[]{1,0,4,5,2,3}[meshFace]:HousingTexturePolicy.stairLocalFace(NativeStairGeometry.unrolledFace(facing,upper,roll,direction).get3DDataValue(),facing.get3DDataValue());int choice=HousingTexturePolicy.baseChoice(face,row.get("main").getAsInt(),row.get("side").getAsInt(),true);if(row.get("facesEnabled").getAsBoolean()&&row.getAsJsonArray("faces").get(face).getAsInt()>=0)choice=row.getAsJsonArray("faces").get(face).getAsInt();var mat=material(sprite,choice);
                    var actual=slab?NativeHousingQuads.slab(upper,facing,meshFace,layout,mat):NativeHousingQuads.retextureStair(source.get(i),layout,NativeStairGeometry.unrolledFace(facing,upper,roll,direction),mat);
                    need(actual.getDirection()==direction&&actual.isShade()==ref.get("shade").getAsBoolean(),"face/shade "+row.get("key"));int[] data=actual.getVertices();for(int v=0;v<4;v++){var point=ref.getAsJsonArray("points").get(v).getAsJsonArray();for(int axis=0;axis<3;axis++)need(Float.floatToRawIntBits(point.get(axis).getAsFloat())==data[v*8+axis],"position "+row.get("key"));for(int uv=0;uv<2;uv++){double value=Float.intBitsToFloat(data[v*8+4+uv])/(uv==0?mat.usedU():mat.usedV());need(Math.abs(value-point.get(3+uv).getAsDouble())<.00003,"UV "+row.get("key")+" quad="+i+" vertex="+v+" axis="+uv+" expected="+point.get(3+uv)+" actual="+value);}vertices++;}
                }
                if(slab)slabs++;else stairs++;
            }}need(slabs==288&&stairs==3672&&items==360,"world profile coverage "+slabs+"/"+stairs);System.out.println("[vandorlabs][shape-quads] PASS slabs="+slabs+" customStairs="+stairs+" itemTransforms="+items+" vertices="+vertices+" checks="+checks+" actual Forge geometry/shade/UV; authored vanilla stairs and item adapters separate");
        }
    }
}
