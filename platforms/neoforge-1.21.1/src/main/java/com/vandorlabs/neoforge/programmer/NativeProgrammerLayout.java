package com.vandorlabs.neoforge.programmer;

import com.vandorlabs.programmer.ProgrammerTransform;
import java.util.*;
import net.minecraft.core.Direction;

/** Immutable four-cell projection derived from the actual shared source assembly transform. */
public final class NativeProgrammerLayout {
 public record Cell(int part,int x,int y,int z){}
 public record Layout(Direction facing,ProgrammerTransform.Affine transform,int originX,int originZ,List<Cell> cells){public Layout{cells=List.copyOf(cells);}public double width(){return transform.worldWidth;}public double depth(){return transform.worldDepth;}public double height(){return 2;}}
 private static final Layout[] LAYOUTS=new Layout[6];
 static {for(Direction facing:Direction.Plane.HORIZONTAL){var transform=ProgrammerTransform.of(ProgrammerTransform.Facing.valueOf(facing.name()),2,1);int ox=facing==Direction.SOUTH?-1:0,oz=facing==Direction.WEST?-1:0;var cells=new ArrayList<Cell>(4);for(int part=0;part<4;part++)cells.add(new Cell(part,(int)Math.round(ox+transform.x(.5+(part&1),.5)-.5),part>>1,(int)Math.round(oz+transform.z(.5+(part&1),.5)-.5)));LAYOUTS[facing.get3DDataValue()]=new Layout(facing,transform,ox,oz,cells);}}
 private NativeProgrammerLayout(){}
 public static Layout of(Direction facing){if(facing==null||facing.getAxis()==Direction.Axis.Y)throw new IllegalArgumentException("Programmer requires horizontal facing");return LAYOUTS[facing.get3DDataValue()];}
 public static int metadata(Direction facing,int part){of(facing);if(part<0||part>3)throw new IllegalArgumentException("Programmer part");return facing.get2DDataValue()|(part<<2);}
 public static Direction facing(int sourceMetadata){return Direction.from2DDataValue(sourceMetadata&3);}
 public static int part(int sourceMetadata){return sourceMetadata>>2&3;}
}
