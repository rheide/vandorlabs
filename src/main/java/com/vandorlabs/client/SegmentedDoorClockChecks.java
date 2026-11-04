package com.vandorlabs.client;

import com.vandorlabs.segmented.*;
import com.vandorlabs.client.segmented.SegmentedDoorVisuals;
import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

/** Distinct column histories and delayed packets share one pause-aware motion timeline. */
final class SegmentedDoorClockChecks {
    private static void equal(double a,double b,String why){if(Math.abs(a-b)>1e-9)throw new IllegalStateException("Segmented clock: "+why+" "+a+" != "+b);}
    static void run(Minecraft mc){
        net.minecraft.block.Block block=net.minecraft.block.Block.getBlockFromName("vandorlabs:segmented_door");List<VehicleStructure.Cell> cells=new ArrayList<>();for(int col=0;col<3;col++)for(int row=0;row<4;row++)cells.add(new VehicleStructure.Cell(new BlockPos(col,3-row,0),block.getDefaultState(),null));VehicleWorld view=new VehicleWorld(mc.world,VehicleStructure.display(cells));SegmentedDoorTile[] roots=new SegmentedDoorTile[3];
        for(int col=0;col<3;col++){roots[col]=new SegmentedDoorTile();SegmentedDoorTile tile=roots[col];tile.setWorld(view);tile.setPos(new BlockPos(col,3,0));tile.column=col;tile.columns=3;tile.height=4;tile.ticksPerSection=10;tile.duration=40;tile.started=view.getTotalWorldTime();view.tiles.put(tile.getPos(),tile);}
        equal(SegmentedDoorVisuals.sample(roots[0],0),0,"closed baseline");UUID stroke=UUID.randomUUID();for(int col=0;col<3;col++){roots[col].targetOpen=true;roots[col].sequence=1+col*3;roots[col].motionIdentity=stroke;}
        equal(SegmentedDoorVisuals.sample(roots[0],0),0,"opening starts without jump");equal(SegmentedDoorVisuals.sample(roots[0],10),.25,"uniform opening speed");equal(SegmentedDoorVisuals.sample(roots[1],10),.25,"late second-column packet");equal(SegmentedDoorVisuals.sample(roots[2],17),.425,"later third-column packet");
        for(int col=0;col<3;col++){equal(SegmentedDoorVisuals.sample(roots[col],20),.5,"all columns identical phase");for(int row=0;row<4;row++){double[] a=new double[6],b=new double[6];SegmentedDoorGeometry.panel(row,4,col,3,SegmentedDoorVisuals.sample(roots[col],20),0,false,a);SegmentedDoorGeometry.panel(row,4,0,3,.5,0,false,b);for(int n=1;n<6;n++)equal(a[n],b[n],"actual row pose across columns");}}
        roots[0].started-=100000;roots[0].from=1;equal(SegmentedDoorVisuals.sample(roots[0],20),.5,"repeated packet and server-clock correction");
        UUID reverse=UUID.randomUUID();roots[1].targetOpen=false;roots[1].sequence++;roots[1].motionIdentity=reverse;equal(SegmentedDoorVisuals.sample(roots[1],20),.5,"reversal continuity");roots[0].targetOpen=false;roots[0].sequence++;roots[0].motionIdentity=reverse;equal(SegmentedDoorVisuals.sample(roots[0],23),.425,"late reversal shares timeline");
        roots[0].targetOpen=true;roots[0].sequence--;roots[0].motionIdentity=stroke;equal(SegmentedDoorVisuals.sample(roots[0],25),.375,"stale motion cannot restart");
        System.out.println("[vandorlabs][reprolab] segmented-clock PASS shared stroke different column sequences late/repeated/reordered packets reversals exact row poses");
    }
    private SegmentedDoorClockChecks(){}
}
