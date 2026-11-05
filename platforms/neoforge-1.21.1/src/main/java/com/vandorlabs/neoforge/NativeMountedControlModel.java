package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.*;

/** Model-owned mounted meshes; section workers consume only immutable visual model data. */
@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NativeMountedControlModel extends BakedModelWrapper<BakedModel> {
    private record Key(BlockState state,int height,int tilt,int direction,int rotation){}
    private final NativeMountedSwitchBlock block;
    private final ControlMountSettings itemMount;
    private final Map<BlockState,BakedModel> poses;
    private final com.google.common.cache.Cache<Key,List<BakedQuad>> meshes=com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(1024*1024)
        .weigher((Key key,List<BakedQuad> quads)->160+quads.stream().mapToInt(q->96+q.getVertices().length*4).sum()).build();
    private NativeMountedControlModel(BakedModel base,NativeMountedSwitchBlock block,ControlMountSettings itemMount,Map<BlockState,BakedModel> poses){super(base);this.block=block;this.itemMount=itemMount;this.poses=poses;}
    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event){
        Map<BlockState,BakedModel> resolved=new HashMap<>();
        for(var block:List.of(VandorLabsBootstrap.PUSH_BUTTON.get(),VandorLabsBootstrap.ROCKER_SWITCH.get()))for(var state:block.getStateDefinition().getPossibleStates()){
            var model=event.getModels().get(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(state));if(model==null)throw new IllegalStateException("Missing binary pose "+state);resolved.put(state,model);
        }
        Map<BlockState,BakedModel> poses=Map.copyOf(resolved);
        event.getModels().replaceAll((id,model)->{
            NativeMountedSwitchBlock block=id.id().equals(ResourceLocation.parse("vandorlabs:push_button"))?VandorLabsBootstrap.PUSH_BUTTON.get():id.id().equals(ResourceLocation.parse("vandorlabs:rocker_switch"))?VandorLabsBootstrap.ROCKER_SWITCH.get():null;
            return block==null?model:new NativeMountedControlModel(model,block,ControlMountSettings.of(0,0,0),poses);
        });
    }
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return getQuads(state,side,random,ModelData.EMPTY,null);}
    @Override public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,BlockState state,ModelData data){return data;}
    @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random,ModelData data,RenderType layer){
        var visual=data.get(NativeBinaryControlBlockEntity.VISUAL);ControlMountSettings mount=visual==null?itemMount:visual.mount();
        BlockState pose=state==null?block.defaultBlockState().setValue(NativeMountedSwitchBlock.FACING,Direction.UP):state;
        int rotation=visual==null?pose.getValue(NativeMountedSwitchBlock.ROTATION):visual.rotation();
        pose=pose.setValue(NativeMountedSwitchBlock.ROTATION,NativeMountedControlGeometry.rotation(pose,rotation));
        BakedModel sourceModel=state==null?originalModel:poses.get(pose);
        if(mount.height==0&&mount.tilt==0)return sourceModel.getQuads(state==null?null:pose,side,random,data,layer);
        if(side!=null)return List.of();
        Key key=new Key(pose,mount.height,mount.tilt,mount.direction,NativeMountedControlGeometry.rotation(pose,rotation));
        List<BakedQuad> cached=meshes.getIfPresent(key);if(cached!=null)return cached;
        var transform=NativeMountedControlGeometry.transform(pose,mount,rotation);List<BakedQuad> result=new ArrayList<>();
        for(Direction source:faces())for(BakedQuad quad:sourceModel.getQuads(state==null?null:pose,source,random,data,layer)){
            int[] raw=quad.getVertices();int stride=raw.length/4;Vec3[] points=new Vec3[4];boolean bottom=true;
            for(int i=0;i<4;i++){int at=i*stride;double x=Float.intBitsToFloat(raw[at]),y=Float.intBitsToFloat(raw[at+1]),z=Float.intBitsToFloat(raw[at+2]);bottom&=Math.abs(transform.depth(x,y,z))<1e-6;points[i]=transform.transform(x,y,z);}
            if(bottom){Vec3[] projected=new Vec3[4];for(int i=0;i<4;i++)projected[i]=transform.project(points[i].x,points[i].y,points[i].z);add(result,quad,projected);for(int i=0;i<4;i++){int next=(i+1)%4;add(result,quad,new Vec3[]{points[i],points[next],projected[next],projected[i]});}}
            else add(result,quad,points);
        }
        cached=List.copyOf(result);meshes.put(key,cached);return cached;
    }
    private static final com.mojang.serialization.MapDecoder<ControlMountSettings> MOUNT=new com.mojang.serialization.MapDecoder.Implementation<>(){
        @Override public <T> java.util.stream.Stream<T> keys(com.mojang.serialization.DynamicOps<T> ops){return java.util.stream.Stream.of("RedstoneChannelSettings").map(ops::createString);}
        @Override public <T> com.mojang.serialization.DataResult<ControlMountSettings> decode(com.mojang.serialization.DynamicOps<T> ops,com.mojang.serialization.MapLike<T> input){
            T nested=input.get("RedstoneChannelSettings");if(nested==null)return com.mojang.serialization.DataResult.success(ControlMountSettings.of(0,0,0));var map=ops.getMap(nested).result();if(map.isEmpty())return com.mojang.serialization.DataResult.success(ControlMountSettings.of(0,0,0));input=map.get();
            int h=integer(input.get(ControlMountSettings.HEIGHT)),t=ControlMountSettings.migratedTilt(integer(input.get(ControlMountSettings.TILT)),input.get(ControlMountSettings.VERSION)!=null),d=integer(input.get(ControlMountSettings.DIRECTION));
            return com.mojang.serialization.DataResult.success(ControlMountSettings.valid(h,t,d)?ControlMountSettings.of(h,t,d):ControlMountSettings.of(0,0,0));
        }
        private int integer(Object value){return value instanceof net.minecraft.nbt.NumericTag number?number.getAsInt():0;}
    };
    private final com.google.common.cache.Cache<net.minecraft.world.item.component.CustomData,ControlMountSettings> projected=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(128).build();
    private final com.google.common.cache.Cache<Integer,BakedModel> items=com.google.common.cache.CacheBuilder.newBuilder().maximumSize(16).build();
    private final ItemOverrides overrides=new ItemOverrides(){
        @Override public BakedModel resolve(BakedModel original,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel world,net.minecraft.world.entity.LivingEntity entity,int seed){
            var custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);if(custom==null)return NativeMountedControlModel.this;
            ControlMountSettings mount=projected.getIfPresent(custom);if(mount==null){mount=custom.read(MOUNT).result().orElse(ControlMountSettings.of(0,0,0));projected.put(custom,mount);}
            if(mount.height==0&&mount.tilt==0)return NativeMountedControlModel.this;
            int key=mount.height+4*(mount.tilt+4*mount.direction);BakedModel model=items.getIfPresent(key);if(model==null){BakedModel base=originalModel.getOverrides().resolve(originalModel,stack,world,entity,seed);model=new NativeMountedControlModel(base==null?originalModel:base,block,mount,poses);items.put(key,model);}return model;
        }
    };
    @Override public ItemOverrides getOverrides(){return overrides;}
    private volatile ItemTransforms padded;
    @Override public ItemTransforms getTransforms(){
        ItemTransforms base=originalModel.getTransforms();if(itemMount.height==0&&itemMount.tilt==0)return base;ItemTransforms cached=padded;if(cached!=null)return cached;
        ItemTransform gui=base.gui;double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(Direction face:FACES)for(BakedQuad q:getQuads(null,face,RandomSource.create(0),ModelData.EMPTY,null)){
            int[] raw=q.getVertices();int stride=raw.length/4;for(int i=0;i<4;i++){
                double x=(Float.intBitsToFloat(raw[i*stride])-.5)*gui.scale.x(),y=(Float.intBitsToFloat(raw[i*stride+1])-.5)*gui.scale.y(),z=(Float.intBitsToFloat(raw[i*stride+2])-.5)*gui.scale.z();
                double rz=Math.toRadians(gui.rotation.z()),rx=Math.toRadians(gui.rotation.x()),ry=Math.toRadians(gui.rotation.y());double xx=x*Math.cos(rz)-y*Math.sin(rz);y=x*Math.sin(rz)+y*Math.cos(rz);x=xx;double xxY=x*Math.cos(ry)+z*Math.sin(ry);z=z*Math.cos(ry)-x*Math.sin(ry);x=xxY;y=y*Math.cos(rx)-z*Math.sin(rx);
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
        }
        if(!Double.isFinite(minX))return base;float scale=(float)Math.min(.75/Math.max(maxX-minX,maxY-minY),.85);
        ItemTransform guiFit=new ItemTransform(gui.rotation,new org.joml.Vector3f((float)(-(minX+maxX)*scale/2),(float)(-(minY+maxY)*scale/2),gui.translation.z()),new org.joml.Vector3f(gui.scale).mul(scale));
        padded=new ItemTransforms(base.thirdPersonLeftHand,base.thirdPersonRightHand,base.firstPersonLeftHand,base.firstPersonRightHand,base.head,guiFit,base.ground,base.fixed,base.moddedTransforms);return padded;
    }
    @Override public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,com.mojang.blaze3d.vertex.PoseStack pose,boolean left){getTransforms().getTransform(context).apply(left,pose);return this;}
    private final List<BakedModel> passes=List.of(this);
    @Override public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack,boolean fabulous){return passes;}
    private static final Direction[] FACES={null,Direction.UP,Direction.DOWN,Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST};
    private static Direction[] faces(){return FACES;}
    private static void add(List<BakedQuad> result,BakedQuad source,Vec3[] points){
        Vec3 normal=Vec3.ZERO;for(int i=1;i<3&&normal.lengthSqr()<1e-14;i++)normal=points[i].subtract(points[0]).cross(points[i+1].subtract(points[0]));if(normal.lengthSqr()<1e-14)return;normal=normal.normalize();
        int[] raw=source.getVertices().clone();int stride=raw.length/4;int packed=((int)Math.round(normal.x*127)&255)|(((int)Math.round(normal.y*127)&255)<<8)|(((int)Math.round(normal.z*127)&255)<<16);
        for(int i=0;i<4;i++){int at=i*stride;raw[at]=Float.floatToRawIntBits((float)points[i].x);raw[at+1]=Float.floatToRawIntBits((float)points[i].y);raw[at+2]=Float.floatToRawIntBits((float)points[i].z);raw[at+7]=packed;}
        result.add(new BakedQuad(raw,source.getTintIndex(),Direction.getNearest((float)normal.x,(float)normal.y,(float)normal.z),source.getSprite(),source.isShade(),source.hasAmbientOcclusion()));
    }
}
