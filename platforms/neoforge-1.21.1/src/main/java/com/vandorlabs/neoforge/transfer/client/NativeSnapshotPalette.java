package com.vandorlabs.neoforge.transfer.client;

import com.vandorlabs.neoforge.transfer.NativeSnapshotAppearance;
import com.vandorlabs.neoforge.ports.NativeResourcePortEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Unpublished visual tiles and immutable quad lists; callers retain cache, transform, shading and ownership authority. */
public final class NativeSnapshotPalette {
    public record Layer(RenderType type,List<BakedQuad> quads){public Layer{quads=List.copyOf(quads);}}
    public record Palette(BlockState state,BlockEntity tile,ModelData data,List<Layer> layers,boolean fallback,int retainedBytes,int quadBytes){public Palette{layers=List.copyOf(layers);}}
    public static Palette build(Level level,BlockPos context,BlockState state,CompoundTag projection,int quadBudget){
        Objects.requireNonNull(level);Objects.requireNonNull(context);Objects.requireNonNull(state);Objects.requireNonNull(projection);
        if(quadBudget<0||quadBudget>8*1024*1024||projection.sizeInBytes()>256*1024||!NativeSnapshotAppearance.canonical(state,projection,level.registryAccess()))throw new IllegalArgumentException("Public snapshot palette bound/schema");
        BlockEntity tile=state.getBlock() instanceof EntityBlock factory?factory.newBlockEntity(context,state):null;
        if(tile!=null){
            tile.setLevel(level);
            // Older public Port projections were empty; their authored placement defaults still render.
            if(!projection.isEmpty()||!(tile instanceof NativeResourcePortEntity))tile.loadWithComponents(projection.copy(),level.registryAccess());
        }
        ModelData data=tile==null?ModelData.EMPTY:tile.getModelData();var layers=new ArrayList<Layer>();boolean fallback=false;int quadBytes=0;
        if(state.getRenderShape()==RenderShape.MODEL){var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(state);var random=RandomSource.create(42);for(var layer:model.getRenderTypes(state,random,data)){
            var quads=new ArrayList<BakedQuad>();for(int side=-1;side<6;side++){random.setSeed(42);quads.addAll(model.getQuads(state,side<0?null:Direction.values()[side],random,data,layer));}
            long weight=128;for(var quad:quads)weight+=64L+4L*quad.getVertices().length;
            if(weight>quadBudget-quadBytes){fallback=true;layers.clear();break;}
            quadBytes+=Math.toIntExact(weight);layers.add(new Layer(layer,quads));
        }}
        int weight=Math.addExact(1024,Math.multiplyExact(projection.sizeInBytes(),4));return new Palette(state,tile,data,layers,fallback,Math.addExact(weight,quadBytes),quadBytes);
    }
    private NativeSnapshotPalette(){}
}
