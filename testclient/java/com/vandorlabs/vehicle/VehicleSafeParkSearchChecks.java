package com.vandorlabs.vehicle;

import java.util.*;
import net.minecraft.util.math.*;

/** Search policy only; actual permissions/player/ownership are verified separately. */
public final class VehicleSafeParkSearchChecks {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Safe Park search: "+why);}
    private static final class Grid implements VehicleSafeParkSearch.Access {
        final Set<BlockPos> blocked=new HashSet<>(),holes=new HashSet<>(),protectedCells=new HashSet<>();final List<AxisAlignedBB> people=new ArrayList<>();int calls,loadedCalls;boolean coldWest,denyAll;
        public boolean loaded(BlockPos min,BlockPos max){calls++;loadedCalls++;return !coldWest||min.getX()>=0;}
        public boolean floor(BlockPos p){calls++;return p.getY()==64&&!holes.contains(p);}
        public boolean place(BlockPos p){calls++;return !denyAll&&!blocked.contains(p)&&!protectedCells.contains(p);}
        public boolean clear(AxisAlignedBB shape){calls++;for(BlockPos p:blocked)if(new AxisAlignedBB(p).intersects(shape))return false;for(AxisAlignedBB body:people)if(body.intersects(shape.grow(.01)))return false;return true;}
    }
    private static VehicleSafeParkSearch search(Grid grid,boolean overhang,int radius){
        List<BlockPos> targets=Arrays.asList(BlockPos.ORIGIN,BlockPos.ORIGIN.east(),BlockPos.ORIGIN.south(),BlockPos.ORIGIN.east().south());
        return new VehicleSafeParkSearch(targets,Collections.singletonList(new AxisAlignedBB(overhang?-.5:0,0,0,2,1,2)),new BlockPos(0,65,0),radius,256,grid);
    }
    private static BlockPos finish(VehicleSafeParkSearch search,Grid grid){int steps=0;while(true){int old=grid.calls;boolean done=search.step(1,Long.MAX_VALUE);need(grid.calls-old<=2,"one slice bounds candidate admission and physical probes");if(done)return search.result();need(++steps<20000,"bounded search completes");}}
    public static void main(String[] args){
        Grid clear=new Grid();need(finish(search(clear,false,2),clear).equals(new BlockPos(0,65,0)),"unchanged exact flat origin preferred");
        Grid changing=new Grid();VehicleSafeParkSearch accepted=search(changing,false,2);BlockPos admitted=finish(accepted,changing);changing.blocked.add(admitted);need(finish(accepted.recheck(),changing)==null,"recheck refuses a changed exact destination without searching a different height or position");
        Grid supporting=new Grid();VehicleSafeParkSearch stable=search(supporting,false,2);BlockPos supported=finish(stable,supporting);supporting.holes.add(supported.down());need(finish(stable.recheck(),supporting)==null,"recheck includes changed support under the exact destination");
        Grid obstacle=new Grid();obstacle.blocked.add(new BlockPos(0,65,0));need(finish(search(obstacle,false,2),obstacle).equals(new BlockPos(1,65,0)),"full vehicle footprint moves beside occupied cell");
        Grid hole=new Grid();hole.blocked.add(new BlockPos(0,65,0));hole.holes.add(new BlockPos(2,64,1));need(finish(search(hole,false,2),hole).equals(new BlockPos(0,65,1)),"one unsupported far footprint column rejects otherwise fitting origin");
        Grid claims=new Grid();claims.blocked.add(new BlockPos(0,65,0));claims.protectedCells.add(new BlockPos(2,65,0));need(finish(search(claims,false,2),claims).equals(new BlockPos(0,65,1)),"protected later target rejects entire placement");
        Grid occupied=new Grid();occupied.blocked.add(new BlockPos(0,65,0));occupied.people.add(new AxisAlignedBB(1.4,65,.2,1.8,67,.8));need(finish(search(occupied,false,2),occupied).equals(new BlockPos(0,65,1)),"living obstruction included in physical-volume admission");
        Grid overhang=new Grid();overhang.blocked.add(new BlockPos(-1,65,0));need(finish(search(overhang,true,2),overhang).equals(new BlockPos(1,65,0)),"emitted overhang beyond source cells controls collision footprint");
        Grid recovery=new Grid();BlockPos originalOrigin=new BlockPos(0,65,0),airSource=new BlockPos(4,0,0);recovery.blocked.add(originalOrigin.add(airSource));VehicleSafeParkSearch withRecovery=new VehicleSafeParkSearch(Arrays.asList(BlockPos.ORIGIN,airSource),Collections.singletonList(new AxisAlignedBB(0,0,0,1,1,1)),originalOrigin,2,256,recovery);BlockPos clearRecovery=finish(withRecovery,recovery);need(clearRecovery!=null&&!clearRecovery.equals(originalOrigin)&&!recovery.blocked.contains(clearRecovery.add(airSource)),"reserved Ramp recovery air outside emitted geometry participates in nearest-area search");
        Grid cold=new Grid();cold.coldWest=true;need(finish(search(cold,false,2),cold).equals(new BlockPos(1,65,0)),"cold region refused before its terrain/targets are read");
        Grid waiting=new Grid();VehicleSafeParkSearch paused=search(waiting,false,1);need(!paused.step(32,System.nanoTime()-1)&&waiting.calls==0,"expired deadline performs no world probes");
        Grid full=new Grid();full.denyAll=true;VehicleSafeParkSearch nowhere=search(full,false,0);need(finish(nowhere,full)==null,"no clear flat area leaves no candidate");need(full.blocked.isEmpty(),"search never writes terrain");
        System.out.println("Safe Park flat search PASS checks="+checks+" exact/blocked/hole/claims/people/overhang/cold/sliced/deadline/exhaustion; player transfer pending");
    }
}
