package com.vandorlabs.ramp;

import java.util.*;

/** Discovery tests exercise bounded world queries and connected vertical source journals. */
final class VerticalPlatformDiscoveryTest {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    static void run(){
        for(RampGeometry.Direction direction:RampGeometry.Direction.values()){
            boolean normalX=direction==RampGeometry.Direction.EAST||direction==RampGeometry.Direction.WEST;
            ControllerPlatform.Cell seed=new ControllerPlatform.Cell(14,-3,-27);List<ControllerPlatform.Cell> previous=null;
            for(int repeat=0;repeat<3;repeat++){
                Set<ControllerPlatform.Cell> queried=new HashSet<>();
                Set<ControllerPlatform.Cell> result=ControllerPlatform.discoverVertical(seed,direction,cell->{
                    check(normalX?cell.x==seed.x:cell.z==seed.z,"never query outside perpendicular plane");
                    check(cell.y>=seed.y && (long)cell.y-seed.y<16,"never query below seed or above sixteen-level boundary");
                    check(Math.abs((long)(normalX?cell.z:cell.x)-(normalX?seed.z:seed.x))<8,"never query beyond potential eight-wide extent");
                    check(queried.add(cell),"query each world cell once");return true;
                });
                check(result.size()==128 && queried.size()==128,"uniform wall discovers exactly8x16 bounded cells");
                int lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE;for(ControllerPlatform.Cell cell:result){int side=normalX?cell.z:cell.x;lo=Math.min(lo,side);hi=Math.max(hi,side);}
                check(hi-lo==7,"lateral width spans exactly8");List<ControllerPlatform.Cell> order=new ArrayList<>(result);
                if(previous!=null)check(previous.equals(order),"repeatable discovery iteration and clipping");previous=order;
            }
            int dx=normalX?0:1,dz=normalX?1:0;
            Set<ControllerPlatform.Cell> connected=new HashSet<>(Arrays.asList(seed,seed.offset(0,1,0),seed.offset(dx,1,dz),seed.offset(2*dx,1,2*dz),seed.offset(2*dx,0,2*dz)));
            Set<ControllerPlatform.Cell> material=new HashSet<>(connected);material.add(seed.offset(0,-1,0));material.add(seed.offset(0,-2,0));material.add(seed.offset(6*dx,8,6*dz));
            Set<ControllerPlatform.Cell> result=ControllerPlatform.discoverVertical(seed,direction,material::contains);
            check(result.equals(connected),"follow lateral/up/down-connected overhangs, exclude below seed and detached islands");
            Set<ControllerPlatform.Cell> tower=new HashSet<>();for(int y=-4;y<24;y++)tower.add(seed.offset(0,y,0));
            result=ControllerPlatform.discoverVertical(seed,direction,tower::contains);check(result.size()==16 && result.contains(seed.offset(0,15,0)) && !result.contains(seed.offset(0,16,0)),"one-wide tower clips at16 levels without following buried base");
            int[] probes={0};check(ControllerPlatform.discoverVertical(seed,direction,cell->{probes[0]++;return false;}).isEmpty() && probes[0]==1,"nonmatching seed does not inspect or capture neighbors");
        }
        ControllerPlatform.Cell edge=new ControllerPlatform.Cell(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE);
        Set<ControllerPlatform.Cell> result=ControllerPlatform.discoverVertical(edge,RampGeometry.Direction.NORTH,cell->{check(cell.y==Integer.MAX_VALUE && cell.z==Integer.MIN_VALUE,"integer overflow never reaches predicate");return true;});
        check(result.size()==8,"integer-boundary tower stays finite and within coordinates");
        System.out.println("Vertical platform discovery PASS ("+assertions+" assertions)");
    }
    private VerticalPlatformDiscoveryTest(){}
}
