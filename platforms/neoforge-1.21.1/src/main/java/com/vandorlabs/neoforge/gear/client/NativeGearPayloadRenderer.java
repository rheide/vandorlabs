package com.vandorlabs.neoforge.gear.client;

import com.mojang.blaze3d.vertex.*;
import com.vandorlabs.neoforge.gear.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/** Public projections only. Shared palette quads avoid rebuilding geometry or loading inventories during a stroke. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NativeGearPayloadRenderer {
    private static final int LIMIT=16*1024*1024,QUAD_LIMIT=8*1024*1024;
    private record Key(BlockState state,CompoundTag projection){}
    private record Layer(RenderType type,List<BakedQuad> quads){}
    private record Palette(BlockState state,BlockEntity tile,ModelData data,List<Layer> layers,boolean fallback){}
    private record Cell(BlockPos offset,Palette palette){}
    private record Header(List<Cell> cells,int bytes){}
    // Values contain the public cells/projections, never the weak header key or its private source snapshot.
    private static final Map<NativeGearPayloadView,Header> CACHE=new WeakHashMap<>();
    private static Object world;
    public static void clear(){CACHE.clear();world=null;}
    @net.neoforged.bus.api.SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel()==world)clear();}
    public static int retainedBytes(){int bytes=0;for(var header:CACHE.values())bytes+=header.bytes;return bytes;}
    private static Header build(NativeGearEntity root,NativeGearPayloadView view){
        var mc=Minecraft.getInstance();var palettes=new HashMap<Key,Palette>();var cells=new ArrayList<Cell>(view.cells().size());int bytes=512+view.cells().size()*64,quadBytes=0;
        for(var cell:view.cells()){
            var key=new Key(cell.state(),cell.projection());var palette=palettes.get(key);
            if(palette==null){
                BlockEntity tile=cell.state().getBlock() instanceof EntityBlock factory?factory.newBlockEntity(root.getBlockPos(),cell.state()):null;
                if(tile!=null){tile.setLevel(root.getLevel());tile.loadWithComponents(key.projection,root.getLevel().registryAccess());}
                ModelData data=tile==null?ModelData.EMPTY:tile.getModelData();var layers=new ArrayList<Layer>();boolean fallback=false;
                if(cell.state().getRenderShape()==RenderShape.MODEL){var model=mc.getBlockRenderer().getBlockModel(cell.state());var random=RandomSource.create(42);for(var layer:model.getRenderTypes(cell.state(),random,data)){
                    var quads=new ArrayList<BakedQuad>();for(int side=-1;side<6;side++){random.setSeed(42);quads.addAll(model.getQuads(cell.state(),side<0?null:Direction.values()[side],random,data,layer));}
                    long weight=128;for(var quad:quads)weight+=64L+4L*quad.getVertices().length;
                    if(weight>QUAD_LIMIT-quadBytes){fallback=true;layers.clear();break;}
                    quadBytes+=Math.toIntExact(weight);layers.add(new Layer(layer,List.copyOf(quads)));
                }}
                int weight=1024+key.projection.sizeInBytes()*4;bytes=Math.addExact(bytes,weight);palette=new Palette(cell.state(),tile,data,List.copyOf(layers),fallback);palettes.put(key,palette);
            }
            cells.add(new Cell(cell.source(),palette));
        }
        return new Header(List.copyOf(cells),Math.addExact(bytes,quadBytes));
    }
    public static void draw(NativeGearEntity root,double phase,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        var view=root.payloadView();if(view==null||view.materialized()||root.inert())return;
        if(world!=root.getLevel()){clear();world=root.getLevel();}
        var header=CACHE.get(view);if(header==null){header=build(root,view);while(retainedBytes()+header.bytes>LIMIT&&!CACHE.isEmpty())CACHE.remove(CACHE.keySet().iterator().next());if(header.bytes<=LIMIT)CACHE.put(view,header);}
        var transform=NativeGearPayload.relativeTransform(root,phase,view.reference());
        var matrix=new Matrix4f((float)transform.xx(),(float)transform.yx(),(float)transform.zx(),0,(float)transform.xy(),(float)transform.yy(),(float)transform.zy(),0,(float)transform.xz(),(float)transform.yz(),(float)transform.zz(),0,(float)transform.tx(),(float)transform.ty(),(float)transform.tz(),1);
        poses.pushPose();poses.mulPose(matrix);var mc=Minecraft.getInstance();
        for(var cell:header.cells){var palette=cell.palette;poses.pushPose();poses.translate(cell.offset.getX(),cell.offset.getY(),cell.offset.getZ());
            if(palette.fallback)mc.getBlockRenderer().renderSingleBlock(palette.state,poses,buffers,light,overlay,palette.data,null);
            else for(var layer:palette.layers){var buffer=buffers.getBuffer(layer.type);for(var quad:layer.quads){int color=quad.isTinted()?mc.getBlockColors().getColor(palette.state,root.getLevel(),root.getBlockPos().offset(cell.offset),quad.getTintIndex()):0xffffff;var normal=transform.vector(quad.getDirection().getStepX(),quad.getDirection().getStepY(),quad.getDirection().getStepZ());float shade=quad.isShade()?(float)(normal.x*normal.x*.6+normal.y*normal.y*(normal.y>0?1:.5)+normal.z*normal.z*.8):1;buffer.putBulkData(poses.last(),quad,(color>>16&255)/255F*shade,(color>>8&255)/255F*shade,(color&255)/255F*shade,1,light,overlay);}}
            if(palette.tile!=null&&mc.getBlockEntityRenderDispatcher().getRenderer(palette.tile)!=null)mc.getBlockEntityRenderDispatcher().render(palette.tile,partial,poses,buffers);
            poses.popPose();
        }
        poses.popPose();
    }
    private NativeGearPayloadRenderer(){}
}
