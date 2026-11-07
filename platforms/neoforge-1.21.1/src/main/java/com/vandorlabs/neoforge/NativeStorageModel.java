package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.storage.*;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Source Storage cube faces. Workers consume scalar model data; vanilla performs ordinary side culling. */
public final class NativeStorageModel extends BakedModelWrapper<BakedModel> {
 private static final int[] MESH_FACE={1,0,4,5,2,3};
 record Artwork(ProgrammableModel.MaterialSnapshot front,ProgrammableModel.MaterialSnapshot side,ProgrammableModel.MaterialSnapshot top){ProgrammableModel.MaterialSnapshot face(int local){return local==2?front:local<=1?top:side;}}
 public static final class Bake {
  public static final int MESH_BUDGET=4*1024*1024;private static final int FACE_BYTES=512;
  private final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ProgrammableModel.MaterialSnapshot> general;private final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Artwork> artwork;
  private final Long2ObjectLinkedOpenHashMap<List<BakedQuad>> meshes=new Long2ObjectLinkedOpenHashMap<>(64);
  private static final ProgrammableBlockDialog.State MISSING=new ProgrammableBlockDialog.State(0,0,false,new int[]{-1,-1,-1,-1,-1,-1},-1);
  Bake(Map<Integer,ProgrammableModel.MaterialSnapshot> general,Function<Material,TextureAtlasSprite> textures){this(general,resolve(textures));}
  Bake(Map<Integer,ProgrammableModel.MaterialSnapshot> general,Map<Integer,Artwork> artwork){this.general=new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(general);this.artwork=new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>(artwork);if(!this.general.containsKey(0))throw new IllegalArgumentException("Storage fallback material");}
  private static Map<Integer,Artwork> resolve(Function<Material,TextureAtlasSprite> textures){var sprites=new HashMap<String,ProgrammableModel.MaterialSnapshot>();for(var entry:NativeStorageAssets.textures())sprites.put(entry.id(),new ProgrammableModel.MaterialSnapshot(textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(entry.id()))),entry.u(),entry.v()));var sets=new HashMap<Integer,Artwork>();for(var entry:NativeStorageAssets.sets().values())sets.put(entry.choice(),new Artwork(sprites.get(entry.front().id()),sprites.get(entry.side().id()),sprites.get(entry.top().id())));return Map.copyOf(sets);}
  public BakedModel wrap(BakedModel original){return new NativeStorageModel(original,this);}
  private List<BakedQuad> face(Direction facing,int local,ProgrammableBlockDialog.State state){boolean inherited=!state.facesEnabled||state.face(local)<0;int choice=ModernFinishCatalog.storedChoice(inherited?state.mainTexture:state.face(local));Artwork set=artwork.get(choice);int index=set!=null||general.containsKey(choice)?choice:0;boolean matched=set!=null&&inherited;long key=((long)index<<8)|((long)facing.get2DDataValue()<<6)|((long)local<<3)|((long)state.layout<<1)|(matched?1:0);
   synchronized(meshes){var found=meshes.getAndMoveToLast(key);if(found!=null)return found;}
   var material=set==null?general.get(index):matched?set.face(local):set.front;var built=List.of(NativeHousingQuads.cube(facing,MESH_FACE[local],state.layout,material));
   synchronized(meshes){var raced=meshes.getAndMoveToLast(key);if(raced!=null)return raced;while((meshes.size()+1L)*FACE_BYTES>MESH_BUDGET)meshes.removeFirst();meshes.putAndMoveToLast(key,built);return built;}
  }
  int renderedChoice(int choice){int stored=ModernFinishCatalog.storedChoice(choice);return artwork.containsKey(stored)||general.containsKey(stored)?stored:0;}
  ProgrammableModel.MaterialSnapshot itemMaterial(int choice,int local){var set=artwork.get(choice);return set==null?general.get(choice):set.face(local);}
  public long cacheBytes(){synchronized(meshes){return 8192L+meshes.size()*FACE_BYTES;}}
  public int cachedFaces(){synchronized(meshes){return meshes.size();}}
 }
 private final Bake bake;private NativeStorageModel(BakedModel original,Bake bake){super(original);this.bake=bake;}
 @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data){return data;}
 @Override public boolean useAmbientOcclusion(){return false;}
 @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return net.neoforged.neoforge.common.util.TriState.FALSE;}
 @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
 @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){if(state==null)return originalModel.getQuads(null,side,random,data,layer);if(side==null)return List.of();return quadsForPose(state.getValue(NativeStorageBlock.FACING),side,data);}
 List<BakedQuad> quadsForPose(Direction facing,Direction side,ModelData data){if(side==null)return List.of();int local=ProgrammableModel.localFace(side,facing);var visual=data.get(NativeStorageEntity.VISUAL);return bake.face(facing,local,visual==null?Bake.MISSING:visual.appearance());}
}
