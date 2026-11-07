package com.vandorlabs.neoforge.programmer.client;
import com.google.gson.*;
import com.vandorlabs.neoforge.NativeDoorArtwork;
import com.vandorlabs.neoforge.programmer.NativeProgrammerAssets;
import com.vandorlabs.propulsion.PropulsionObjMesh;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
/** One immutable atlas generation. Replacement geometry and dynamic sources retain their own model. */
public final class NativeProgrammerBake {
 private record Source(BakedModel model,List<BakedQuad> quads){}
 private final Map<String,Source> sources;
 private final int retainedBytes;
 public NativeProgrammerBake(Map<ModelResourceLocation,BakedModel> models,Function<Material,TextureAtlasSprite> textures,ResourceManager resources){var prepared=new HashMap<String,Source>();int bytes=128;
  for(String id:NativeProgrammerAssets.models()){var source=Objects.requireNonNull(models.get(ModelResourceLocation.standalone(ResourceLocation.parse(id))),"Missing Programmer model "+id);List<BakedQuad> quads=null;
   if(NativeDoorArtwork.isStatic(source)){var original=all(source,null,RandomSource.create(0),ModelData.EMPTY,null);if(canonical(id,resources)){var corrected=new ArrayList<BakedQuad>();for(var face:NativeProgrammerAssets.model(id).geometry()){var sprite=textures.apply(new Material(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(face.sprite)));int[] data=template(original,sprite,face);int direction=PropulsionObjMesh.bake(face,0,0,image(sprite),data);corrected.add(new BakedQuad(data,-1,Direction.from3DDataValue(direction),sprite,true,source.useAmbientOcclusion()));}quads=NativeDoorArtwork.content(corrected);}else quads=NativeDoorArtwork.content(original);for(var quad:quads)bytes+=96+quad.getVertices().length*4;}
   prepared.put(id,new Source(source,quads));bytes+=128;
  }if(bytes>4*1024*1024)throw new IllegalArgumentException("Programmer atlas-generation mesh budget");sources=Map.copyOf(prepared);retainedBytes=bytes;
 }
 public BakedModel source(String id){return Objects.requireNonNull(sources.get(id),id).model;}
 public List<BakedQuad> quads(String id,BlockState state,RandomSource random,ModelData data,RenderType layer){var source=Objects.requireNonNull(sources.get(id),id);return source.quads!=null?source.quads:NativeDoorArtwork.content(all(source.model,state,random,data,layer));}
 public int retainedBytes(){return retainedBytes;}
 private static List<BakedQuad> all(BakedModel model,BlockState state,RandomSource random,ModelData data,RenderType layer){var result=new ArrayList<BakedQuad>();result.addAll(model.getQuads(state,null,random,data,layer));for(var side:Direction.values())result.addAll(model.getQuads(state,side,random,data,layer));return result;}
 private static PropulsionObjMesh.Sprite image(TextureAtlasSprite sprite){return new PropulsionObjMesh.Sprite(){public float minU(){return sprite.getU0();}public float maxU(){return sprite.getU1();}public float minV(){return sprite.getV0();}public float maxV(){return sprite.getV1();}public int width(){return sprite.contents().width();}public int height(){return sprite.contents().height();}};}
 private static int[] template(List<BakedQuad> originals,TextureAtlasSprite sprite,PropulsionObjMesh.Face face){BakedQuad best=null;double score=Double.POSITIVE_INFINITY;int[] map=new int[4],chosen=new int[4];boolean[] used=new boolean[4];for(var quad:originals){if(quad.getSprite()!=sprite)continue;int[] raw=quad.getVertices();int stride=raw.length/4;if(raw.length%4!=0||stride<8)continue;Arrays.fill(used,false);double error=0;for(int v=0;v<4;v++){int selected=-1;double distance=Double.POSITIVE_INFINITY;for(int w=0;w<4;w++)if(!used[w]){double delta=0;for(int a=0;a<3;a++)delta+=Math.abs(face.position(v,a)-Float.intBitsToFloat(raw[w*stride+a]));if(delta<distance){distance=delta;selected=w;}}used[selected]=true;map[v]=selected;error+=distance*1000;}int normal=raw[map[0]*stride+7];for(int a=0;a<3;a++)error+=Math.abs(face.normal(a)-(byte)(normal>>>(a*8))/127F)*.0001;if(error<score){score=error;best=quad;System.arraycopy(map,0,chosen,0,4);}}if(best!=null&&score<.01){int[] raw=best.getVertices(),result=new int[raw.length];int stride=result.length/4;for(int v=0;v<4;v++)System.arraycopy(raw,chosen[v]*stride,result,v*stride,stride);return result;}int[] result=new int[32];for(int v=0;v<4;v++)result[v*8+3]=-1;return result;}
 private static boolean same(ResourceLocation id,ResourceManager resources)throws IOException{try(var active=resources.getResource(id).orElseThrow().open();var original=NativeProgrammerBake.class.getResourceAsStream("/assets/"+id.getNamespace()+"/"+id.getPath())){return original!=null&&Arrays.equals(active.readAllBytes(),original.readAllBytes());}}
 private static boolean canonical(String model,ResourceManager resources){try{var id=ResourceLocation.parse(model);var json=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"models/"+id.getPath()+".json");JsonObject active,original;try(var reader=resources.getResource(json).orElseThrow().openAsReader();var builtin=NativeProgrammerBake.class.getResourceAsStream("/assets/"+json.getNamespace()+"/"+json.getPath());var source=new InputStreamReader(Objects.requireNonNull(builtin),StandardCharsets.UTF_8)){active=JsonParser.parseReader(reader).getAsJsonObject();original=JsonParser.parseReader(source).getAsJsonObject();}for(String field:List.of("loader","model","automatic_culling","shade_quads","flip_v","emissive_ambient"))if(!Objects.equals(active.get(field),original.get(field)))return false;var obj=ResourceLocation.parse(original.get("model").getAsString());if(!same(obj,resources))return false;try(var reader=resources.getResource(obj).orElseThrow().openAsReader();var lines=new BufferedReader(reader)){String line;while((line=lines.readLine())!=null)if(line.startsWith("mtllib ")){String path=obj.getPath();if(!same(ResourceLocation.fromNamespaceAndPath(obj.getNamespace(),path.substring(0,path.lastIndexOf('/')+1)+line.substring(7)),resources))return false;}}return true;}catch(IOException|RuntimeException unsupported){return false;}}
}
