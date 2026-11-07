package com.vandorlabs.neoforge.ramp;

import com.vandorlabs.neoforge.NativeVoxelShapes;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Prepared bodies only; durable source ownership, rollback and notification belong to the journal. */
public final class NativeRampPartPublisher {
    private static final long RETAINED_LIMIT=64L*1024*1024;
    private record Entry(BlockPos position,NativeRampPartEntity entity){}
    public static final class Prepared {
        private ServerLevel level;private final UUID operation;private final BlockPos root;private final BlockState proxy;private final long tick,revision,retainedBytes;private final Set<BlockPos> cells;private List<Entry> entries;private boolean consumed;
        private Prepared(ServerLevel level,UUID operation,BlockPos root,BlockState proxy,long revision,List<Entry> entries,long bytes){this.level=level;this.operation=operation;this.root=root.immutable();this.proxy=proxy;this.revision=revision;this.entries=List.copyOf(entries);tick=level.getGameTime();retainedBytes=bytes;var cells=new LinkedHashSet<BlockPos>();for(var entry:entries)cells.add(entry.position);this.cells=Collections.unmodifiableSet(cells);}
        public Set<BlockPos> cells(){return cells;}public long preparedBytes(){return retainedBytes;}
        /** Single attempt. No notification is sent; the caller must recover any partial installation. */
        public void install(){
            if(consumed)throw new IllegalStateException("Ramp publication already consumed");var world=level;NativeRampRiders.requireThread(world);
            try{
                if(world.getGameTime()!=tick)throw new IllegalStateException("Ramp prepared publication expired");
                // Check every target before touching the first one. Never load a chunk or replace a foreign owner.
                for(var entry:entries){var pos=entry.position;if(!NativeRampHeldOwners.mutationAllows(world,operation,root,pos))throw new IllegalStateException("Ramp publication requires exact durable operation scope");
                    var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)throw new IllegalStateException("Ramp publication waits for loaded target");var state=chunk.getBlockState(pos);var owner=chunk.getBlockEntities().get(pos);
                    if(state.isAir()&&owner==null&&chunk.getBlockEntityNbt(pos)==null)continue;
                    if(!state.is(proxy.getBlock())||!(owner instanceof NativeRampPartEntity part)||!part.belongs(operation,root)||part.publication()==null||(part.publication().revision()>revision||part.publication().revision()==revision&&part.publication().observationTick()>=tick))throw new IllegalStateException("Ramp publication encountered changed target");
                }
                for(var entry:entries){var pos=entry.position;
                    if(world.getBlockState(pos)!=proxy&&!world.setBlock(pos,proxy,Block.UPDATE_KNOWN_SHAPE))throw new IllegalStateException("Ramp part block publication refused");
                    // Replace the temporary/default tile (or an earlier same-operation body) without exposing it.
                    world.removeBlockEntity(pos);world.setBlockEntity(entry.entity);
                    var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
                    if(chunk==null||chunk.getBlockEntities().get(pos)!=entry.entity||entry.entity.getLevel()!=world||!entry.entity.belongs(operation,root)||entry.entity.publication()==null)throw new IllegalStateException("Ramp prepared body installation refused");
                }
            }finally{consumed=true;entries=List.of();level=null;}
        }
    }
    public static Prepared prepare(ServerLevel level,BlockState proxy,UUID operation,BlockPos root,NativeRampAssembly assembly,Set<BlockPos> targetCells,NativeRampPartData.Motion motion,NativeRampPartData.Belt belt,long revision){
        NativeRampRiders.requireThread(level);Objects.requireNonNull(operation);Objects.requireNonNull(root);Objects.requireNonNull(assembly);
        if(!assembly.footprint().root().equals(root)||revision<0||!(proxy.getBlock() instanceof NativeRampPartBlock)||!(proxy.getBlock() instanceof EntityBlock factory)||targetCells.isEmpty()||targetCells.size()>8192||!assembly.potential().containsAll(targetCells))throw new IllegalArgumentException("Ramp prepared publication identity or target bounds");
        var positions=new ArrayList<BlockPos>(targetCells.size());for(var cell:targetCells)positions.add(cell.immutable());positions.sort(Comparator.<BlockPos>comparingInt(p->p.getX()).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));var entries=new ArrayList<Entry>(positions.size());long bytes=512;
        for(var pos:positions){BlockEntity candidate=factory.newBlockEntity(pos,proxy);if(!(candidate instanceof NativeRampPartEntity part)||candidate.getLevel()!=null||!candidate.getBlockPos().equals(pos)||candidate.getBlockState()!=proxy||!candidate.getType().isValid(proxy))throw new IllegalArgumentException("Ramp part factory did not create a detached exact body");
            var data=assembly.part(pos,motion,belt);var publication=part.preparePublication(data,revision,level.getGameTime());
            bytes+=2048+(publication.beltGeometry()==null?publication.geometry().retainedBytes():publication.beltGeometry().retainedBytes())+NativeVoxelShapes.retainedCost(publication.collisionShape(),publication.geometry().pieces().size())+80L*publication.worldBoxes().size();
            for(var origin:data.spec().origins())bytes+=512+96L*origin.shapes().size();if(bytes>RETAINED_LIMIT)throw new IllegalArgumentException("Ramp prepared publication retained-size budget");
            part.stagePublication(operation,root,publication);entries.add(new Entry(pos,part));
        }return new Prepared(level,operation,root,proxy,revision,entries,bytes);
    }
    private NativeRampPartPublisher(){}
}
