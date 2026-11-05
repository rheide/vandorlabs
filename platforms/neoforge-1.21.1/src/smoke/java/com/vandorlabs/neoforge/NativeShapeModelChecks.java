package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** CPU housing adapter/item decoder check. Canonical source quads come from actual Forge output. */
public final class NativeShapeModelChecks {
    private static final class Sprite extends TextureAtlasSprite{Sprite(SpriteContents contents){super(TextureAtlas.LOCATION_BLOCKS,contents,16,16,0,0);}}
    private static int checks;
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static BakedModel source(JsonArray quads,TextureAtlasSprite sprite,boolean slab){
        List<BakedQuad> values=new ArrayList<>();for(var value:quads){var row=value.getAsJsonObject();int[] data=new int[32];for(int v=0;v<4;v++){var p=row.getAsJsonArray("points").get(v).getAsJsonArray();for(int axis=0;axis<3;axis++)data[v*8+axis]=Float.floatToRawIntBits(p.get(axis).getAsFloat());data[v*8+3]=-1;data[v*8+4]=Float.floatToRawIntBits(p.get(3).getAsFloat());data[v*8+5]=Float.floatToRawIntBits(p.get(4).getAsFloat());}values.add(new BakedQuad(data,-1,Direction.byName(row.get("face").getAsString()),sprite,row.get("shade").getAsBoolean(),!slab));}
        var none=ItemTransform.NO_TRANSFORM;var gui=new ItemTransform(new org.joml.Vector3f(30,slab?225:135,0),new org.joml.Vector3f(),new org.joml.Vector3f(.625F));var transforms=new ItemTransforms(none,none,none,none,none,gui,none,none,com.google.common.collect.ImmutableMap.of());
        Map<Direction,List<BakedQuad>> sides=new EnumMap<>(Direction.class);for(Direction side:Direction.values())sides.put(side,List.of());
        return new net.minecraft.client.resources.model.SimpleBakedModel(values,sides,!slab,true,true,sprite,transforms,ItemOverrides.EMPTY);
    }
    private static void compare(JsonObject row,List<BakedQuad> actual,boolean transforms,BakedModel model){
        var expected=row.getAsJsonArray("quads");need(actual.size()==expected.size(),"count "+row.get("key")+" "+actual.size()+"/"+expected.size());for(int q=0;q<actual.size();q++){var ref=expected.get(q).getAsJsonObject();var quad=actual.get(q);need(quad.getDirection()==Direction.byName(ref.get("face").getAsString())&&quad.isShade()==ref.get("shade").getAsBoolean(),"face/shade "+row.get("key"));var raw=quad.getVertices();double usedU=ref.get("sprite").getAsString().contains("file_4cd00407")?.5:1,usedV=ref.get("sprite").getAsString().contains("screen_console_inputs_analysis_framed_gas_giant_static")?.5:1;for(int v=0;v<4;v++){var p=ref.getAsJsonArray("points").get(v).getAsJsonArray();for(int axis=0;axis<3;axis++)need(raw[v*8+axis]==Float.floatToRawIntBits(p.get(axis).getAsFloat()),"position "+row.get("key"));for(int uv=0;uv<2;uv++){double value=Float.intBitsToFloat(raw[v*8+4+uv])/(uv==0?usedU:usedV);need(Math.abs(value-p.get(3+uv).getAsDouble())<.00003,"UV "+row.get("key")+" q="+q+" vertex="+v+" uv="+uv+" actual="+value+" expected="+p.get(3+uv));}}}
        if(transforms){var gui=model.getTransforms().gui;float[] values={gui.rotation.x(),gui.rotation.y(),gui.rotation.z(),gui.translation.x(),gui.translation.y(),gui.translation.z(),gui.scale.x(),gui.scale.y(),gui.scale.z()};for(int i=0;i<9;i++)need(Float.floatToRawIntBits(values[i])==Float.floatToRawIntBits(row.getAsJsonArray("gui").get(i).getAsFloat()),"GUI "+row.get("key")+" axis="+i);}
    }
    public static void main(String[] ignored)throws Exception{
        Path reference=Path.of(System.getProperty("vandorlabs.shapeTextureReference"));var digest=java.security.MessageDigest.getInstance("SHA-256");try(var input=new java.security.DigestInputStream(Files.newInputStream(reference),digest)){input.transferTo(java.io.OutputStream.nullOutputStream());}need(HexFormat.of().formatHex(digest.digest()).equals("73555d9b877d2f479531646310d86878696c47abb9b36cf6f34b65563dcf7cdc"),"frozen actual Forge reference");Map<String,JsonObject> bases=new HashMap<>();
        try(var input=Files.newBufferedReader(reference)){String line;while((line=input.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();if(!row.has("key"))continue;String key=row.get("key").getAsString();if(key.endsWith("/8/0/1/default")&&key.startsWith("stairs/")||key.endsWith("/no-tag"))bases.put(key,row);}}
        try(var image=new NativeImage(16,16,false);var contents=new SpriteContents(ResourceLocation.fromNamespaceAndPath("vandorlabs","shape-model-reference"),new FrameSize(16,16),image,ResourceMetadata.EMPTY)){
            var sprite=new Sprite(contents);Map<Integer,ProgrammableModel.MaterialSnapshot> materials=new HashMap<>();for(int i=0;i<ModernFinishCatalog.ENTRIES.size();i++)materials.put(i,new ProgrammableModel.MaterialSnapshot(sprite,1,1));materials.put(150,new ProgrammableModel.MaterialSnapshot(sprite,1,.5F));materials.put(1288700935,new ProgrammableModel.MaterialSnapshot(sprite,.5F,1));var bake=new ProgrammableShapeModel.Bake(Map.copyOf(materials));
            BakedModel slabItem=bake.wrap(source(bases.get("slab/no-tag").getAsJsonArray("quads"),sprite,true),true,true),stairItem=bake.wrap(source(bases.get("stairs/no-tag").getAsJsonArray("quads"),sprite,false),false,true);int worlds=0,items=0;
            try(var input=Files.newBufferedReader(reference)){String line;while((line=input.readLine())!=null){var row=JsonParser.parseString(line).getAsJsonObject();if(!row.has("kind"))continue;String[] key=row.get("key").getAsString().split("/");boolean slab=key[0].equals("slab");
                if(row.get("kind").getAsString().equals("item")){
                    BakedModel original=slab?slabItem:stairItem;var before=new CompoundTag();boolean tagged=!row.get("nbt").getAsString().equals("<absent>");if(tagged)before.put("BlockEntityTag",TagParser.parseTag(row.get("nbt").getAsString()));var custom=CustomData.of(before);var resolved=tagged?((ProgrammableShapeModel)original).resolveItem(custom):original;var quads=resolved.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null);compare(row,quads,true,resolved);need(custom.copyTag().equals(before),"opaque item payload retained");items++;continue;
                }
                var facing=Direction.byName(key[1]);boolean upper=Boolean.parseBoolean(key[2]);int tread=Integer.parseInt(key[4]),roll=Integer.parseInt(key[5]),layout=Integer.parseInt(key[6]);int[] faces=new int[6];for(int i=0;i<6;i++)faces[i]=row.getAsJsonArray("faces").get(i).getAsInt();var appearance=new ProgrammableShapeEntity.Appearance(new ProgrammableBlockDialog.State(row.get("main").getAsInt(),layout,row.get("facesEnabled").getAsBoolean(),faces,-1),row.get("side").getAsInt(),new StairState(tread,roll));var data=ModelData.builder().with(ProgrammableShapeEntity.APPEARANCE,appearance).build();
                var pose=new ProgrammableShapeModel.Pose(facing,upper,com.vandorlabs.render.StairGeometry.Shape.valueOf(key[3]));String baseKey="stairs/"+key[1]+"/"+key[2]+"/"+key[3]+"/8/0/1/default";var base=slab?slabItem:source(bases.get(baseKey).getAsJsonArray("quads"),sprite,false);var model=bake.wrap(base,slab,false);List<BakedQuad> quads=new ArrayList<>();if(slab)for(var face:row.getAsJsonArray("quads"))quads.addAll(((ProgrammableShapeModel)model).quadsForPose(pose,null,Direction.byName(face.getAsJsonObject().get("face").getAsString()),RandomSource.create(0),data,null));else quads.addAll(((ProgrammableShapeModel)model).quadsForPose(pose,null,null,RandomSource.create(0),data,null));compare(row,quads,false,model);worlds++;
            }}need(worlds==4920&&items==440,"complete reference coverage");bake.quads.cleanUp();long bytes=0;for(var values:bake.quads.asMap().values())bytes+=384+values.stream().mapToInt(quad->96+quad.getVertices().length*4).sum();need(bytes<=ProgrammableShapeModel.Bake.MESH_BUDGET,"generation mesh cache byte limit");need(bake.quads.size()<worlds,"weighted eviction exercised");
            var retainedTag=new CompoundTag();retainedTag.putInt("housingTexture",1288700935);var custom=CustomData.of(retainedTag);var retained=((ProgrammableShapeModel)stairItem).resolveItem(custom);var before=retained.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null);ModernFinishCatalog.publishCustom(Map.of());need(before==retained.getQuads(null,null,RandomSource.create(0),ModelData.EMPTY,null),"old captured model remains independent of global availability");
            System.out.println("[vandorlabs][shape-model] CPU PASS world="+worlds+" items="+items+" checks="+checks+" cacheBytes="+bytes+" actual Forge source quads; decoded fields/UVs/transforms/retainedNBT; native authored bake/live rendering separate");
        }
    }
}
