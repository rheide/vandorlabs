package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.neoforge.ProgrammableModel;
import com.vandorlabs.neoforge.client.NativeItemPadding;
import com.vandorlabs.neoforge.fluid.NativeFluidRenderSnapshot;
import com.vandorlabs.persistence.StairState;
import com.vandorlabs.render.*;
import com.vandorlabs.render.ControlMount.Point;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import com.vandorlabs.ui.SlabStairDialog;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Native atlas/buffer adapter over the shared slab mesh and authored stair UV policy. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ShapeModel extends BakedModelWrapper<BakedModel> {
    private record Finish(TextureAtlasSprite sprite,float u,float v) {double aspect(){return u/(double)v;}}
    /** Only immutable state is published to chunk workers; neighbour tiles are not queried. */
    public static final net.neoforged.neoforge.client.model.data.ModelProperty<BlockState> ACTUAL_POSE=new net.neoforged.neoforge.client.model.data.ModelProperty<>();
    private final Map<Integer,Finish> materials;
    private final Map<BlockState,BakedModel> stairSources;
    private final boolean stair,item;
    private final SlabStairDialog.State configured;
    private ItemTransforms padded;
    private final Map<String,List<BakedQuad>> cache=Collections.synchronizedMap(new LinkedHashMap<>(16,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,List<BakedQuad>> e){return size()>8;}});
    private ShapeModel(BakedModel original,Map<Integer,Finish> materials,boolean stair,boolean item,SlabStairDialog.State configured,Map<BlockState,BakedModel> stairSources){super(com.vandorlabs.neoforge.client.NativeLegacyUvs.wrap(original));this.materials=materials;this.stair=stair;this.item=item;this.configured=configured;this.stairSources=stairSources;}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        Map<Integer,Finish> values=new HashMap<>();for(var choice:ModernFinishCatalog.allChoices()){var entry=choice.entry();var sprite=event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,ResourceLocation.parse(entry.texture())));values.put(choice.choice(),new Finish(sprite,entry.u(),entry.v()));}Map<Integer,Finish> materials=Map.copyOf(values);
        Map<BlockState,BakedModel> sources=new HashMap<>();for(BlockState pose:ShapeRegistration.STAIRS.get().getStateDefinition().getPossibleStates()){BakedModel source=event.getModels().get(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(pose));if(source==null)throw new IllegalStateException("Missing canonical stair pose "+pose);sources.put(pose,com.vandorlabs.neoforge.client.NativeLegacyUvs.wrap(source));}Map<BlockState,BakedModel> canonical=Map.copyOf(sources);
        event.getModels().replaceAll((id,model)->id.id().getNamespace().equals("vandorlabs")&&Set.of("programmable_slab","programmable_stairs").contains(id.id().getPath())?new ShapeModel(model,materials,id.id().getPath().equals("programmable_stairs"),id.getVariant().equals("inventory"),null,canonical):model);
    }
    private static SlabStairDialog.State defaults(){int[] faces=new int[6];Arrays.fill(faces,-1);return new SlabStairDialog.State(0,-1,1,false,faces,-1,new StairState(8,0));}
    private Finish finish(int choice){return materials.getOrDefault(ModernFinishCatalog.renderChoice(choice),materials.get(0));}
    @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){return !item&&(!stair||layer==RenderType.translucent())?net.neoforged.neoforge.common.util.TriState.FALSE:originalModel.useAmbientOcclusion(state,data,layer);}
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data){
        if(item)return data;
        if(!stair)return com.vandorlabs.neoforge.client.NativeHousingLighting.snapshot(level,pos,data);
        SlabStairDialog.State settings=data.get(ProgrammableShapeEntity.SHAPE_SETTINGS);if(settings==null)settings=defaults();
        return data.derive().with(ACTUAL_POSE,ShapeGeometry.connected(state,level,pos,settings.stairs)).build();
    }
    @Override public boolean useAmbientOcclusion(){return item?originalModel.useAmbientOcclusion():stair&&originalModel.useAmbientOcclusion();}
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){var fluid=data.get(NativeFluidRenderSnapshot.DATA);return fluid!=null&&!fluid.faces.isEmpty()?ChunkRenderTypeSet.of(RenderType.solid(),RenderType.translucent()):ChunkRenderTypeSet.of(RenderType.solid());}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        if(layer==RenderType.translucent()){if(side!=null)return List.of();var fluid=data.get(NativeFluidRenderSnapshot.DATA);return fluid==null?List.of():com.vandorlabs.neoforge.fluid.client.NativeFluidClient.quads(fluid);}
        if(item&&configured==null&&!stair)return originalModel.getQuads(state,side,random,data,layer);
        SlabStairDialog.State settings=item?(configured==null?defaults():configured):data.get(ProgrammableShapeEntity.SHAPE_SETTINGS);if(settings==null)settings=defaults();
        BlockState pose=state==null?(stair?ShapeRegistration.STAIRS.get().defaultBlockState().setValue(StairBlock.FACING,Direction.EAST):ShapeRegistration.SLAB.get().defaultBlockState()):state;
        if(!item&&stair&&data.get(ACTUAL_POSE)!=null)pose=data.get(ACTUAL_POSE);
        String key=pose+"/"+side+"/"+settings.main+"/"+settings.side+"/"+settings.layout+"/"+settings.facesEnabled+"/"+Arrays.toString(settings.faces())+"/"+settings.stairs.packed();
        List<BakedQuad> found=cache.get(key);if(found!=null)return lit(found,data);
        List<BakedQuad> built=stair?stairs(pose,state,side,random,data,layer,settings):item?slabItem(state,side,random,data,layer,settings):slab(pose,side,settings);built=List.copyOf(built);cache.put(key,built);return lit(built,data);
    }
    private List<BakedQuad> lit(List<BakedQuad> quads,ModelData data){Integer light=data.get(com.vandorlabs.neoforge.client.NativeHousingLighting.LIGHT);if(item||stair||light==null)return quads;List<BakedQuad> result=new ArrayList<>(quads.size());for(BakedQuad quad:quads)result.add(com.vandorlabs.neoforge.client.NativeHousingLighting.apply(quad,light));return result;}
    private List<BakedQuad> slab(BlockState state,Direction side,SlabStairDialog.State settings){
        if(side!=null)return List.of();int[] faces={1,0,4,5,2,3};Direction facing=ShapeGeometry.facing(state);int turn=facing==Direction.NORTH?0:facing==Direction.WEST?1:facing==Direction.SOUTH?2:3;List<BakedQuad> result=new ArrayList<>();
        for(int face=0;face<6;face++){
            int local=faces[face],choice=settings.facesEnabled&&settings.faces()[local]>=0?settings.faces()[local]:local>=2&&settings.side>=0?settings.side:settings.main;Finish material=finish(choice);
            var mesh=ScreenHousingMesh.slab(ShapeGeometry.upper(state),true).textureLayout(settings.layout,material.aspect());Point[] points=new Point[4];double[] u=new double[4],v=new double[4];
            for(int i=0;i<4;i++){var p=mesh.quads[face].vertices[(4-i)&3];double x=p.x/16,z=p.z/16;if(turn==1){x=p.z/16;z=1-p.x/16;}else if(turn==2){x=1-p.x/16;z=1-p.z/16;}else if(turn==3){x=1-p.z/16;z=p.x/16;}points[i]=new Point(x,p.y/16,z);u[i]=p.u;v[i]=p.v;}
            // Preserve the legacy perpendicular flat-light sample instead of sampling through the slab.
            result.add(quad(points,u,v,Direction.from3DDataValue(local<2?2:1),material,false,false));
        }return result;
    }
    private List<BakedQuad> slabItem(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer,SlabStairDialog.State settings){
        List<BakedQuad> result=new ArrayList<>();for(BakedQuad source:originalModel.getQuads(state,side,random,data,layer)){
            // Slab item previews retain dormant per-face settings without displaying them.
            Finish material=finish(source.getDirection().getAxis()!=Direction.Axis.Y&&settings.side>=0?settings.side:settings.main);Point[] points=points(source);double[][] uv=uv(source);double[][] mapped=HousingItemTextureMapping.map(points,uv[0],uv[1],settings.layout,material.aspect());result.add(retexture(source,material,mapped));
        }return result;
    }
    private List<BakedQuad> stairs(BlockState pose,BlockState requested,Direction side,RandomSource random,ModelData data,RenderType layer,SlabStairDialog.State settings){
        List<BakedQuad> source;
        if(settings.stairs.packed()==8)source=(item?originalModel:stairSources.getOrDefault(pose,originalModel)).getQuads(item?requested:pose,side,random,data,layer);
        else {if(side!=null)return List.of();source=new ArrayList<>();Finish initial=finish(0);for(var face:StairGeometry.mesh(ShapeGeometry.facing(pose).get3DDataValue(),ShapeGeometry.upper(pose),settings.stairs.roll==0?StairGeometry.Shape.valueOf(pose.getValue(StairBlock.SHAPE).name()):StairGeometry.Shape.STRAIGHT,settings.stairs.packed()))source.add(quad(face.vertices,face.u,face.v,Direction.from3DDataValue(face.face),initial,true,true));}
        List<BakedQuad> result=new ArrayList<>();for(BakedQuad original:source){int textureFace=StairGeometry.unrolledFace(ShapeGeometry.facing(pose).get3DDataValue(),ShapeGeometry.upper(pose),settings.stairs.roll,original.getDirection().get3DDataValue()),local=StairTextureMapping.localFace(textureFace,ShapeGeometry.facing(pose).get3DDataValue());int choice=settings.facesEnabled&&settings.faces()[local]>=0?settings.faces()[local]:local>=2&&settings.side>=0?settings.side:settings.main;Finish material=finish(choice);double[][] uv=uv(original);float[] u=new float[4],v=new float[4];for(int i=0;i<4;i++){u[i]=(float)uv[0][i];v[i]=(float)uv[1][i];}result.add(retexture(original,material,StairTextureMapping.map(points(original),u,v,settings.layout,material.aspect(),textureFace)));}return result;
    }
    private static Point[] points(BakedQuad quad){int[] values=quad.getVertices();int stride=values.length/4;Point[] result=new Point[4];for(int i=0;i<4;i++)result[i]=new Point(Float.intBitsToFloat(values[i*stride]),Float.intBitsToFloat(values[i*stride+1]),Float.intBitsToFloat(values[i*stride+2]));return result;}
    private static double[][] uv(BakedQuad quad){int[] values=quad.getVertices();int stride=values.length/4;var sprite=quad.getSprite();double[][] result=new double[2][4];for(int i=0;i<4;i++){result[0][i]=(Float.intBitsToFloat(values[i*stride+4])-sprite.getU0())/(sprite.getU1()-sprite.getU0())*16;result[1][i]=(Float.intBitsToFloat(values[i*stride+5])-sprite.getV0())/(sprite.getV1()-sprite.getV0())*16;}return result;}
    private static BakedQuad retexture(BakedQuad source,Finish material,double[][] uv){int[] data=source.getVertices().clone();int stride=data.length/4;for(int i=0;i<4;i++){data[i*stride+4]=Float.floatToRawIntBits(material.sprite.getU((float)uv[i][0]/16*material.u));data[i*stride+5]=Float.floatToRawIntBits(material.sprite.getV((float)uv[i][1]/16*material.v));}return new BakedQuad(data,source.getTintIndex(),source.getDirection(),material.sprite,source.isShade(),source.hasAmbientOcclusion());}
    private static BakedQuad quad(Point[] points,double[] u,double[] v,Direction face,Finish material,boolean shade,boolean ao){int[] data=new int[32];Point normal=points[1].subtract(points[0]).cross(points[2].subtract(points[0]));double length=Math.sqrt(normal.dot(normal));normal=length>0?normal.scale(1/length):ControlMount.normal(face.get3DDataValue());int packed=((int)Math.round(normal.x*127)&255)|(((int)Math.round(normal.y*127)&255)<<8)|(((int)Math.round(normal.z*127)&255)<<16);for(int i=0;i<4;i++){int at=i*8;data[at]=Float.floatToRawIntBits((float)points[i].x);data[at+1]=Float.floatToRawIntBits((float)points[i].y);data[at+2]=Float.floatToRawIntBits((float)points[i].z);data[at+3]=-1;data[at+4]=Float.floatToRawIntBits(material.sprite.getU((float)u[i]/16*material.u));data[at+5]=Float.floatToRawIntBits(material.sprite.getV((float)v[i]/16*material.v));data[at+7]=packed;}return new BakedQuad(data,-1,face,material.sprite,shade,ao);}
    @Override public ItemTransforms getTransforms(){if(!stair)return originalModel.getTransforms();if(padded==null)padded=NativeItemPadding.fit(this,originalModel.getTransforms());return padded;}
    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return List.of(this);}
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
    @Override public ItemOverrides getOverrides(){return new ItemOverrides(){@Override public BakedModel resolve(BakedModel original,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){var data=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);if(data==null)data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(data==null)return ShapeModel.this;var tag=data.copyTag();if(tag.contains("BlockEntityTag",10))tag=tag.getCompound("BlockEntityTag");int main=ProgrammableBlockDialog.savedMainTexture(tag.getInt(ProgrammableBlockDialog.MAIN_TEXTURE_KEY),tag.contains("HousingTextureVersion",3));int[] faces=tag.getIntArray(ProgrammableBlockDialog.FACES_KEY);if(faces.length!=6){faces=new int[6];Arrays.fill(faces,-1);}boolean tiled=stair?tag.getBoolean("SlabTileSides"):!tag.contains("SlabTileSides",1)||tag.getBoolean("SlabTileSides");int layout=ProgrammableBlockDialog.savedTextureLayout(tag.getInt(ProgrammableBlockDialog.LAYOUT_KEY),tag.contains(ProgrammableBlockDialog.LAYOUT_KEY,3),tiled);return new ShapeModel(originalModel,materials,stair,true,new SlabStairDialog.State(main,tag.contains("SideTexture",3)?tag.getInt("SideTexture"):-1,layout,tag.getBoolean(ProgrammableBlockDialog.FACES_ENABLED_KEY),faces,-1,StairState.read(new com.vandorlabs.neoforge.persistence.NbtPrimitiveData(tag))),stairSources);}};}
}
