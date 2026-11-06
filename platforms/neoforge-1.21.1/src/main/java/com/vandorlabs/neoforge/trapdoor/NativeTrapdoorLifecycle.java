package com.vandorlabs.neoforge.trapdoor;

import java.util.*;
import net.minecraft.core.*;

/** Loaded-only source membership. Callers publish resolved scalar projections after a complete control phase. */
public final class NativeTrapdoorLifecycle {
    private NativeTrapdoorLifecycle(){}
    public interface Leaf {
        BlockPos position(); NativeTrapdoorLinks links(); boolean diagonal(); boolean cover(); int geometryPosition(); boolean sliding();
    }
    public interface Loaded {
        boolean loaded(BlockPos position);
        Leaf leaf(BlockPos position);
    }
    /** Pair lookup deliberately mirrors the released base tile: reciprocity and unit distance, independent of family. */
    public static Leaf mate(Leaf owner,Loaded world){
        var partner=owner.links().partner();if(partner==null||!world.loaded(partner))return null;var other=world.leaf(partner);
        return other!=null&&owner.position().equals(other.links().partner())&&owner.position().distSqr(partner)==1?other:null;
    }
    private static boolean compatibleGroup(Leaf a,Leaf b){
        return a.diagonal()?b.diagonal()&&a.geometryPosition()==b.geometryPosition():!b.diagonal()&&!a.cover()&&!b.cover()&&a.geometryPosition()==b.geometryPosition();
    }
    public static List<BlockPos> squareCells(Leaf owner){
        var base=owner.links().square();if(base==null)return List.of();Direction across=owner.diagonal()?owner.links().groupFacing().getCounterClockWise():Direction.EAST;
        Direction along=owner.diagonal()?(owner.geometryPosition()==2?owner.links().groupFacing().getOpposite():Direction.UP):Direction.SOUTH;
        return List.of(base,base.relative(across),base.relative(along),base.relative(across).relative(along));
    }
    public static List<Leaf> group(Leaf owner,Loaded world){
        if(owner.cover())return List.of(owner);var members=owner.links().assembly();List<Leaf> result=new ArrayList<>();
        if(!members.isEmpty()&&members.contains(owner.position())){
            for(var position:members){if(!world.loaded(position)){result.clear();break;}var other=world.leaf(position);if(other==null||!members.equals(other.links().assembly())||!compatibleGroup(owner,other)){result.clear();break;}result.add(other);}
            if(result.size()==members.size())return List.copyOf(result);
        }
        var square=squareCells(owner);if(square.contains(owner.position())){
            result.clear();for(var position:square){if(!world.loaded(position)){result.clear();break;}var other=world.leaf(position);if(other==null||!owner.links().square().equals(other.links().square())||!compatibleGroup(owner,other)||owner.sliding()!=other.sliding()){result.clear();break;}result.add(other);}
            if(result.size()==4)return List.copyOf(result);
        }
        var mate=mate(owner,world);return mate==null?List.of(owner):List.of(owner,mate);
    }
    public interface MutableLeaf extends Leaf {
        void installLinks(NativeTrapdoorLinks links); int trigger(); boolean open(); void installOpen(boolean value);
    }
    /** Repairs only decided loaded mismatches. All changes install before the caller publishes observers. */
    public static Set<MutableLeaf> repair(MutableLeaf owner,Loaded world){
        Set<MutableLeaf> changed=new LinkedHashSet<>();var links=owner.links();
        if(links.partner()!=null&&world.loaded(links.partner())&&mate(owner,world)==null){owner.installLinks(links.relations(null,links.square(),links.assembly()));changed.add(owner);}
        var old=owner.links().assembly();for(var cell:old)if(world.loaded(cell)){var other=world.leaf(cell);if(other==null||!old.equals(other.links().assembly())){
            for(var at:old)if(world.loaded(at)&&world.leaf(at) instanceof MutableLeaf leaf&&old.equals(leaf.links().assembly())){leaf.installLinks(leaf.links().relations(leaf.links().partner(),leaf.links().square(),List.of()));changed.add(leaf);}
            owner.installLinks(owner.links().relations(owner.links().partner(),owner.links().square(),List.of()));changed.add(owner);break;
        }}
        var square=owner.links().square();if(square!=null){var cells=squareCells(owner);if(!cells.contains(owner.position())){owner.installLinks(owner.links().relations(owner.links().partner(),null,owner.links().assembly()));changed.add(owner);}else for(var at:cells)if(world.loaded(at)){var other=world.leaf(at);if(other==null||!square.equals(other.links().square())){for(var cell:cells)if(world.loaded(cell)&&world.leaf(cell) instanceof MutableLeaf leaf&&square.equals(leaf.links().square())){leaf.installLinks(leaf.links().relations(leaf.links().partner(),null,leaf.links().assembly()));changed.add(leaf);}break;}}}
        return changed;
    }
    public static Set<MutableLeaf> evaluatePower(MutableLeaf owner,Loaded world,boolean force,java.util.function.ToIntFunction<Leaf> local){
        var members=group(owner,world);boolean powered=owner.links().channelSignal();if(!powered)for(var leaf:members)if(leaf.links().channelSignal()||local.applyAsInt(leaf)>0){powered=true;break;}
        boolean changed=!owner.links().powerKnown()||owner.links().lastPower()!=powered;Set<MutableLeaf> edits=new LinkedHashSet<>();
        for(var raw:members){var leaf=(MutableLeaf)raw;if(!leaf.links().powerKnown()||leaf.links().lastPower()!=powered){leaf.installLinks(leaf.links().power(leaf.links().channelSignal(),true,powered));edits.add(leaf);}}
        if(owner.trigger()!=0&&(force||changed)){boolean open=owner.trigger()==1?powered:!powered;for(var raw:members){var leaf=(MutableLeaf)raw;if(leaf.open()!=open){leaf.installOpen(open);edits.add(leaf);}}}
        return edits;
    }
}
