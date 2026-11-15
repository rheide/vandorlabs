package com.vandorlabs.neoforge.surface.client;

import com.vandorlabs.neoforge.surface.*;
import com.vandorlabs.surface.core.SurfaceUvMesh;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Immutable chunk quads per member; shared UV policy and a retained-byte bounded atlas-generation cache. */
public final class NativeSurfaceModel extends BakedModelWrapper<BakedModel> {
    private record Key(NativeSurfaceEntity.Visual visual){}
    private final LinkedHashMap<Key,List<BakedQuad>> cache=new LinkedHashMap<>(64,.75F,true);
    private int retained;
    private static final int[] ORDER={0,2,1,1};
    private final Direction facing;
    private final Map<Direction,List<BakedQuad>> body;
    public NativeSurfaceModel(BakedModel original,Direction facing){super(original);this.facing=facing;var quads=new ArrayList<BakedQuad>();var random=RandomSource.create(0);quads.addAll(original.getQuads(null,null,random));for(var side:Direction.values())quads.addAll(original.getQuads(null,side,random));var sides=new EnumMap<Direction,List<BakedQuad>>(Direction.class);for(var side:Direction.values())sides.put(side,side==facing?List.of():quads.stream().filter(quad->quad.getDirection()==side).toList());body=Map.copyOf(sides);}
    private synchronized List<BakedQuad> quads(NativeSurfaceEntity.Visual visual){
        var key=new Key(visual);var found=cache.get(key);if(found!=null)return found;var sprite=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.parse(NativeSurfaceArtwork.texture(visual.texture())));var result=new ArrayList<BakedQuad>();var basis=NativeSurfaceLayout.face(facing);double centerU=visual.u()+.5-visual.width()/2D,centerV=visual.v()+.5-visual.height()/2D;
        var black=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.parse("minecraft:block/black_concrete"));
        append(result,SurfaceUvMesh.buildCell(1,1,0,1,0,0),black,basis,0,0,.5005,1,1);
        append(result,SurfaceUvMesh.buildCell(visual.width(),visual.height(),visual.mode(),NativeSurfaceArtwork.aspect(visual.texture()),visual.u(),visual.v()),sprite,basis,centerU,centerV,.501,NativeSurfaceArtwork.usedU(visual.texture()),NativeSurfaceArtwork.usedV(visual.texture()));
        found=List.copyOf(result);int bytes=128+found.size()*176;while(!cache.isEmpty()&&(cache.size()>=4096||retained+bytes>2*1024*1024)){var iterator=cache.entrySet().iterator();var old=iterator.next();retained-=128+old.getValue().size()*176;iterator.remove();}if(bytes<=2*1024*1024){cache.put(key,found);retained+=bytes;}return found;
    }
    private static void append(List<BakedQuad> result,List<SurfaceUvMesh.Vertex[]> triangles,net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,com.vandorlabs.turntable.TurntableGeometry.Face basis,double centerU,double centerV,double depth,float usedU,float usedV){
        Direction facing=Direction.from3DDataValue(basis.ordinal());
        for(var triangle:triangles){int[] data=new int[32];for(int vertex=0;vertex<4;vertex++){var point=triangle[ORDER[vertex]];int offset=vertex*8;double u=point.x-centerU,v=point.y-centerV;data[offset]=Float.floatToRawIntBits((float)(.5+basis.ux*u+basis.vx*v+basis.x*depth));data[offset+1]=Float.floatToRawIntBits((float)(.5+basis.uy*u+basis.vy*v+basis.y*depth));data[offset+2]=Float.floatToRawIntBits((float)(.5+basis.uz*u+basis.vz*v+basis.z*depth));data[offset+3]=-1;data[offset+4]=Float.floatToRawIntBits((float)(sprite.getU0()+point.u*(sprite.getU1()-sprite.getU0())*usedU));data[offset+5]=Float.floatToRawIntBits((float)(sprite.getV0()+point.v*(sprite.getV1()-sprite.getV0())*usedV));data[offset+6]=0;data[offset+7]=(facing.getStepX()*127&255)|(facing.getStepY()*127&255)<<8|(facing.getStepZ()*127&255)<<16;}result.add(new BakedQuad(data,-1,facing,sprite,true,true));}
    }
    @Override public net.neoforged.neoforge.client.ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){return net.neoforged.neoforge.client.ChunkRenderTypeSet.of(RenderType.cutoutMipped());}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){if(side!=null)return body.get(side);var visual=data.get(NativeSurfaceEntity.VISUAL);return visual==null?quads(new NativeSurfaceEntity.Visual("original_off",2,1,1,0,0,facing,false)):quads(visual);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return originalModel.getQuads(state,side,random);}
}
