package com.vandorlabs.neoforge;
import com.vandorlabs.neoforge.programmer.NativeProgrammerAssets;
import com.vandorlabs.neoforge.programmer.client.NativeProgrammerBake;
import com.vandorlabs.programmer.ProgrammerItemGeometry;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;
/** Inactive released empty complete-OBJ item. Physical ownership never participates in drawing. */
public final class NativeProgrammerItemModel extends BakedModelWrapper<BakedModel> {
 private final NativeProgrammerBake bake;private final List<BakedQuad> quads;private final ItemTransforms transforms;private final List<BakedModel> passes=List.of(this);private final long retained;
 public NativeProgrammerItemModel(NativeProgrammerBake bake){super(bake.source(NativeProgrammerAssets.item()));this.bake=bake;var initial=normalized(null,RandomSource.create(0),ModelData.EMPTY,null);quads=NativeDoorArtwork.isStatic(originalModel)?initial:null;var bounds=new ProgrammerItemGeometry.Bounds();for(var quad:initial)bounds.include(quad.getVertices(),quad.getVertices().length/4);var fit=bounds.gui();var gui=new ItemTransform(new Vector3f(fit[0],fit[1],fit[2]),new Vector3f(fit[3],fit[4],fit[5]),new Vector3f(fit[6],fit[7],fit[8]));transforms=new ItemTransforms(ItemTransform.NO_TRANSFORM,ItemTransform.NO_TRANSFORM,ItemTransform.NO_TRANSFORM,ItemTransform.NO_TRANSFORM,ItemTransform.NO_TRANSFORM,gui,ItemTransform.NO_TRANSFORM,ItemTransform.NO_TRANSFORM);long bytes=256;if(quads!=null)for(var quad:quads)bytes+=96L+quad.getVertices().length*4L;retained=bytes;}
 private List<BakedQuad> normalized(BlockState state,RandomSource random,ModelData data,RenderType layer){var result=new ArrayList<BakedQuad>();for(var source:bake.quads(NativeProgrammerAssets.item(),state,random,data,layer)){var values=source.getVertices().clone();ProgrammerItemGeometry.normalize(values,values.length/4);result.add(new BakedQuad(values,source.getTintIndex(),source.getDirection(),source.getSprite(),source.isShade(),source.hasAmbientOcclusion()));}return List.copyOf(result);}
 @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
 @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){return side!=null?List.of():quads!=null?quads:normalized(state,random,data,layer);}
 @Override public boolean useAmbientOcclusion(){return false;}
 @Override public boolean isGui3d(){return true;}
 @Override public boolean isCustomRenderer(){return false;}
 @Override public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}
 @Override public ItemTransforms getTransforms(){return transforms;}
 @Override public BakedModel applyTransform(ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack poses,boolean left){transforms.getTransform(context).apply(left,poses);return this;}
 @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return passes;}
 public long retainedBytes(){return retained;}
}
