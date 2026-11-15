package com.vandorlabs.neoforge.transfer.client;

import com.vandorlabs.neoforge.transfer.NativeSnapshotAppearance;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Independent pre-refactor Gear algorithm compared with the shared palette on actual baked records. */
public final class NativeSnapshotPaletteChecks {
    private record Layer(RenderType type,List<BakedQuad> quads){}
    private record Accepted(BlockEntity tile,ModelData data,List<Layer> layers,boolean fallback,int weight,int quads){}
    private static int checks,generation;
    private static java.util.concurrent.CompletableFuture<Void> reload;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Snapshot palette: "+why);}
    private static Accepted accepted(Minecraft mc,BlockPos context,BlockState state,CompoundTag projection,int budget){
        BlockEntity tile=state.getBlock() instanceof EntityBlock factory?factory.newBlockEntity(context,state):null;
        if(tile!=null){tile.setLevel(mc.level);tile.loadWithComponents(projection.copy(),mc.level.registryAccess());}
        ModelData data=tile==null?ModelData.EMPTY:tile.getModelData();var layers=new ArrayList<Layer>();boolean fallback=false;int quadBytes=0;
        if(state.getRenderShape()==RenderShape.MODEL){var model=mc.getBlockRenderer().getBlockModel(state);var random=RandomSource.create(42);for(var layer:model.getRenderTypes(state,random,data)){
            var quads=new ArrayList<BakedQuad>();for(int side=-1;side<6;side++){random.setSeed(42);quads.addAll(model.getQuads(state,side<0?null:Direction.values()[side],random,data,layer));}
            long weight=128;for(var quad:quads)weight+=64L+4L*quad.getVertices().length;
            if(weight>budget-quadBytes){fallback=true;layers.clear();break;}
            quadBytes+=Math.toIntExact(weight);layers.add(new Layer(layer,List.copyOf(quads)));
        }}
        return new Accepted(tile,data,List.copyOf(layers),fallback,1024+projection.sizeInBytes()*4+quadBytes,quadBytes);
    }
    private static void compare(Minecraft mc,BlockState state,CompoundTag raw){
        var pos=new BlockPos(32,99,-16);var projection=NativeSnapshotAppearance.project(state,raw,mc.level.registryAccess());
        need(!projection.toString().contains("private_secret"),"private fields excluded");
        for(int budget:new int[]{0,64,8*1024*1024}){
            var before=accepted(mc,pos,state,projection,budget);var after=NativeSnapshotPalette.build(mc.level,pos,state,projection,budget);
            need(before.fallback==after.fallback()&&before.weight==after.retainedBytes()&&before.quads==after.quadBytes(),"fallback and byte budget "+state);
            need(before.layers.size()==after.layers().size(),"render layer count");
            need((before.tile==null)==(after.tile()==null),"visual tile availability");
            if(before.tile!=null)need(before.tile.getClass()==after.tile().getClass()&&before.tile.saveWithFullMetadata(mc.level.registryAccess()).equals(after.tile().saveWithFullMetadata(mc.level.registryAccess())),"same visual tile without private inventory");
            for(int l=0;l<before.layers.size();l++){
                var old=before.layers.get(l);var now=after.layers().get(l);need(old.type==now.type()&&old.quads.size()==now.quads().size(),"actual layer type/quad count");
                for(int q=0;q<old.quads.size();q++){var a=old.quads.get(q);var b=now.quads().get(q);need(Arrays.equals(a.getVertices(),b.getVertices())&&a.getDirection()==b.getDirection()&&a.getTintIndex()==b.getTintIndex()&&a.isShade()==b.isShade()&&a.hasAmbientOcclusion()==b.hasAmbientOcclusion()&&a.getSprite().contents().name().equals(b.getSprite().contents().name()),"actual complete quad/sprite identity");}
            }
        }
    }
    private static void verify(Minecraft mc){
        for(String id:new String[]{"minecraft:stone","minecraft:oak_sign","minecraft:chest","vandorlabs:programmable_block","vandorlabs:programmable_slab","vandorlabs:programmable_stairs","vandorlabs:programmable_storage","vandorlabs:module_rack"}){
            var state=BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();need(!state.isAir(),"registered visual type "+id);var raw=new CompoundTag();raw.putString("private_secret","private_secret");compare(mc,state,raw);
        }
        var surface=com.vandorlabs.neoforge.surface.NativeSurfaceBindings.BLOCK;
        for(var face:Direction.values())for(int mode=0;mode<3;mode++)for(String texture:new String[]{"material:door_observation_low","material:screen_console_inputs_analysis_framed_gas_giant_static"}){
            var raw=new CompoundTag();raw.putInt("NativeSurfaceSchema",1);raw.putString("SurfaceOff",texture);raw.putString("SurfaceOn",texture);raw.putInt("SurfaceLayout",mode);raw.putInt("SurfaceGroupSize",3);raw.putInt("SurfaceGroupHeight",2);raw.putInt("SurfaceGroupNormal",face.get3DDataValue());raw.putLong("SurfaceGroupOffset",BlockPos.ZERO.asLong());raw.putString("private_secret","private_secret");compare(mc,surface.defaultBlockState().setValue(com.vandorlabs.neoforge.surface.NativeSurfaceBlock.FACING,face),raw);
        }
        System.out.println("[vandorlabs][snapshot-palette] PASS generation="+generation+" checks="+checks+" actualQuads/sprites/ModelData/fallback/privacy");
    }
    public static boolean ready(Minecraft mc){if(generation==2)return true;if(reload!=null&&!reload.isDone())return false;if(reload!=null)reload.join();verify(mc);generation++;if(generation==1){reload=mc.reloadResourcePacks();return false;}return true;}
    private NativeSnapshotPaletteChecks(){}
}
