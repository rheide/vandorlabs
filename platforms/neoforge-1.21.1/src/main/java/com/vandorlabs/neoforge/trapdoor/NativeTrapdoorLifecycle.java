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
}
