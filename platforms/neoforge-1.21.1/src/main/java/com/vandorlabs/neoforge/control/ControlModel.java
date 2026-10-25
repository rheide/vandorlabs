package com.vandorlabs.neoforge.control;

import com.vandorlabs.render.ControlMount;
import com.vandorlabs.render.ControlMount.Point;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Retain the canonical baked faces/UVs while applying the shared filled mount transform. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ControlModel extends BakedModelWrapper<BakedModel> {
    private final MountedControlBlock block;
    private final int itemMount,itemSize;
    private final Map<String,BakedModel> models;
    private final Map<String,List<BakedQuad>> cache=Collections.synchronizedMap(new LinkedHashMap<>(16,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,List<BakedQuad>> e){return size()>8;}});
    private ControlModel(BakedModel base,MountedControlBlock block,int itemMount,int itemSize,Map<String,BakedModel> models){super(base);this.block=block;this.itemMount=itemMount;this.itemSize=itemSize;this.models=models;}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        Map<String,BakedModel> originals=new HashMap<>();event.getModels().forEach((id,model)->originals.put(id.id()+"/"+id.getVariant(),model));
        event.getModels().replaceAll((id,model)->{
            if(!id.id().getNamespace().equals("vandorlabs"))return model;
            for(var holder:ControlRegistration.ALL)if(id.id().equals(holder.getId())){MountedControlBlock block=holder.get();return new ControlModel(model,block,0,block instanceof MountedPowerLeverBlock power?power.defaultSize:0,originals);}return model;
        });
    }
    private BakedModel selected(BlockState state,int size){if(!(block instanceof MountedPowerLeverBlock)||state==null)return originalModel;String key=ControlGeometry.resourceKey(state,size);String[] parts=key.split("/",2);BakedModel model=models.get("vandorlabs:"+parts[0]+"/"+parts[1]);if(model==null)throw new IllegalStateException("Missing control model "+key);return model;}
    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return List.of(this);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType renderType){Integer value=data.get(ControlGeometry.MOUNT),sizeValue=data.get(ControlGeometry.SIZE);int mount=value==null?itemMount:value,size=sizeValue==null?itemSize:sizeValue;BakedModel selected=selected(state,size);if(mount%16==0)return selected.getQuads(state,side,random,data,renderType);if(side!=null)return List.of();BlockState pose=state==null?itemPose():state;return cache.computeIfAbsent(pose+"/"+mount+"/"+size,key->{ControlMount transform=ControlGeometry.transform(pose,mount,size);List<BakedQuad> result=new ArrayList<>();for(Direction direction:new Direction[]{null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})for(BakedQuad quad:selected.getQuads(state,direction,random,data,renderType)){int[] source=quad.getVertices();int stride=source.length/4;Point[] points=new Point[4];boolean bottom=true;for(int i=0;i<4;i++){int at=i*stride;Point p=new Point(Float.intBitsToFloat(source[at]),Float.intBitsToFloat(source[at+1]),Float.intBitsToFloat(source[at+2]));bottom&=Math.abs(transform.depth(p))<1e-6;points[i]=transform.transform(p);}if(bottom){Point[] projected=new Point[4];for(int i=0;i<4;i++)projected[i]=transform.project(points[i]);add(result,quad,projected);for(int i=0;i<4;i++){int next=(i+1)%4;add(result,quad,new Point[]{points[i],points[next],projected[next],projected[i]});}}else add(result,quad,points);}return List.copyOf(result);});}
    private static void add(List<BakedQuad> result,BakedQuad source,Point[] points){Point normal=new Point(0,0,0);for(int i=1;i<3&&normal.dot(normal)<1e-14;i++)normal=points[i].subtract(points[0]).cross(points[i+1].subtract(points[0]));double length=Math.sqrt(normal.dot(normal));if(length<1e-7)return;normal=normal.scale(1/length);int packed=((int)Math.round(normal.x*127)&255)|(((int)Math.round(normal.y*127)&255)<<8)|(((int)Math.round(normal.z*127)&255)<<16);int[] vertices=source.getVertices().clone();int stride=vertices.length/4;for(int i=0;i<4;i++){int at=i*stride;vertices[at]=Float.floatToRawIntBits((float)points[i].x);vertices[at+1]=Float.floatToRawIntBits((float)points[i].y);vertices[at+2]=Float.floatToRawIntBits((float)points[i].z);vertices[at+7]=packed;}result.add(new BakedQuad(vertices,source.getTintIndex(),Direction.getNearest((float)normal.x,(float)normal.y,(float)normal.z),source.getSprite(),source.isShade(),source.hasAmbientOcclusion()));}
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
    @Override public ItemTransforms getTransforms(){ItemTransforms source=originalModel.getTransforms();if(itemMount%16==0)return source;ItemTransform gui=source.gui;double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;for(Direction side:new Direction[]{null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST})for(BakedQuad quad:getQuads(null,side,RandomSource.create(0),ModelData.EMPTY,null)){int[] data=quad.getVertices();int stride=data.length/4;for(int i=0;i<4;i++){int at=i*stride;Point p=com.vandorlabs.render.ControlItemProjection.project(new Point(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2])),gui.rotation.x,gui.rotation.y,gui.rotation.z,gui.scale.x,gui.scale.y,gui.scale.z);x0=Math.min(x0,p.x);x1=Math.max(x1,p.x);y0=Math.min(y0,p.y);y1=Math.max(y1,p.y);}}if(!Double.isFinite(x0))return source;float scale=com.vandorlabs.render.ControlItemProjection.scale(x0,y0,x1,y1);ItemTransform padded=new ItemTransform(gui.rotation,new org.joml.Vector3f((float)(-(x0+x1)*scale/2),(float)(-(y0+y1)*scale/2),gui.translation.z),new org.joml.Vector3f(gui.scale).mul(scale));return new ItemTransforms(source.thirdPersonLeftHand,source.thirdPersonRightHand,source.firstPersonLeftHand,source.firstPersonRightHand,source.head,padded,source.ground,source.fixed);}
    private BlockState itemPose(){if(block instanceof MountedPowerLeverBlock)return block.defaultBlockState();return block.defaultBlockState().setValue(MountedControlBlock.FACING,block instanceof MountedSignalControlBlock signal&&!signal.selectableType()?Direction.NORTH:Direction.UP);}
    @Override public ItemOverrides getOverrides(){return new ItemOverrides(){@Override public BakedModel resolve(BakedModel model,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){
        var data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(data==null)return model;
        var tag=data.copyTag().getCompound("RedstoneChannelSettings");int h=tag.getInt("BaseHeight"),t=ControlMount.migratedTilt(tag.getInt("BaseTilt"),tag.contains("ControlMountVersion")),d=tag.getInt("TiltDirection");
        int mount=ControlMount.valid(h,t,d)?h+4*(t+4*d):0;MountedControlBlock selectedBlock=block;int size=itemSize;
        if(block instanceof MountedPowerLeverBlock power){int saved=com.vandorlabs.persistence.ControlSettings.readSize(new com.vandorlabs.neoforge.persistence.NbtPrimitiveData(tag));size=com.vandorlabs.persistence.ControlSettings.size(saved,power.defaultSize);selectedBlock=power.twin?(size==0?ControlRegistration.SMALL_POWER.get():ControlRegistration.LARGE_POWER.get()):(size==0?ControlRegistration.COMPACT_POWER.get():ControlRegistration.INDUSTRIAL_POWER.get());}
        if(block instanceof MountedSignalControlBlock signal&&signal.selectableType())selectedBlock=ControlRegistration.signal(Math.max(0,Math.min(2,tag.getInt("ControlType"))));
        String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(selectedBlock).toString();BakedModel base=models.get(id+"/inventory");if(base==null)throw new IllegalStateException("Missing configured control item "+id);
        return new ControlModel(base,selectedBlock,mount,size,models);
    }};}
}
