package com.vandorlabs.neoforge.gear.track.client;

import com.vandorlabs.gear.track.core.*;
import com.vandorlabs.neoforge.gear.track.NativeTrackEntity;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Chunk terrain selection. No world scan or animation ticker is used while rendering a cell. */
public final class NativeTrackModel extends BakedModelWrapper<BakedModel> {
    private final List<List<BakedQuad>> parts;
    private final LinkedHashMap<NativeTrackEntity.Visual,List<BakedQuad>> cache=new LinkedHashMap<>(64,.75F,true);
    private int retained;
    public NativeTrackModel(BakedModel original,Map<ModelResourceLocation,BakedModel> models){super(original);var all=new ArrayList<List<BakedQuad>>();for(int n=0;n<TrackMeshPolicy.templateCount();n++){var part=TrackMeshPolicy.template(n);var model=Objects.requireNonNull(models.get(ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.parse("vandorlabs:block/track/"+part.name))),"Missing track part "+part.name);var quads=new ArrayList<BakedQuad>();var random=RandomSource.create(0);var source=new ArrayList<BakedQuad>(model.getQuads(null,null,random));for(var side:Direction.values())source.addAll(model.getQuads(null,side,random));for(var quad:source)quads.add(rotate(quad,part.quarterTurns));all.add(List.copyOf(quads));}parts=List.copyOf(all);}
    private static BakedQuad rotate(BakedQuad quad,int turns){if(turns==0)return quad;int[] data=quad.getVertices().clone();int stride=data.length/4;if(data.length%4!=0||stride<8)throw new IllegalArgumentException("Track vertex format");for(int n=0;n<4;n++){int offset=n*stride;double x=Float.intBitsToFloat(data[offset])-.5,z=Float.intBitsToFloat(data[offset+2])-.5;double xx=turns==1?-z:turns==2?-x:z,zz=turns==1?x:turns==2?-z:-x;data[offset]=Float.floatToRawIntBits((float)(xx+.5));data[offset+2]=Float.floatToRawIntBits((float)(zz+.5));int normal=data[offset+7],nx=(byte)normal,nz=(byte)(normal>>>16);int rx=turns==1?-nz:turns==2?-nx:nz,rz=turns==1?nx:turns==2?-nz:-nx;data[offset+7]=(normal&0xff00ff00)|(rx&255)|(rz&255)<<16;}
        var direction=quad.getDirection();if(direction.getAxis().isHorizontal())for(int n=0;n<turns;n++)direction=direction.getClockWise();return new BakedQuad(data,quad.getTintIndex(),direction,quad.getSprite(),quad.isShade(),quad.hasAmbientOcclusion());}
    private synchronized List<BakedQuad> quads(NativeTrackEntity.Visual visual){if(!visual.visible())return List.of();var found=cache.get(visual);if(found!=null)return found;var result=new ArrayList<BakedQuad>();TrackMeshPolicy.emit(TrackTopology.Style.values()[visual.style()],Math.floorMod(visual.facing()+2,4),visual.key(),part->result.addAll(parts.get(part.template)));found=List.copyOf(result);int bytes=128+found.size()*8;while(!cache.isEmpty()&&(cache.size()>=2048||retained+bytes>1024*1024)){var it=cache.entrySet().iterator();var old=it.next();retained-=128+old.getValue().size()*8;it.remove();}if(bytes<=1024*1024){cache.put(visual,found);retained+=bytes;}return found;}
    @Override public net.neoforged.neoforge.client.ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){return net.neoforged.neoforge.client.ChunkRenderTypeSet.of(RenderType.cutoutMipped());}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){if(side!=null)return List.of();var visual=data.get(NativeTrackEntity.VISUAL);return visual==null?List.of():quads(visual);}
}
