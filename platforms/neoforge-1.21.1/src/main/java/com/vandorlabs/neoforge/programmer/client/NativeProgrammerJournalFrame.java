package com.vandorlabs.neoforge.programmer.client;
import com.vandorlabs.neoforge.programmer.NativeProgrammerLayout;
import com.vandorlabs.neoforge.programmer.NativeProgrammerPicking;
import com.vandorlabs.programmer.ProgrammerJournalPlane;
import net.minecraft.core.Direction;
import org.joml.*;
/** Four immutable authored pixel frames relative to the oriented model's minimum origin. */
public final class NativeProgrammerJournalFrame {
 private static final Matrix4fc[] FRAMES=new Matrix4fc[6];
 static{var plane=new ProgrammerJournalPlane(NativeProgrammerPicking.authored());float[] values=new float[16];for(Direction facing:Direction.Plane.HORIZONTAL){plane.matrix(NativeProgrammerLayout.of(facing).transform(),values);FRAMES[facing.get3DDataValue()]=new Matrix4f().set(values);}}
 private NativeProgrammerJournalFrame(){}
 public static Matrix4fc matrix(Direction facing){NativeProgrammerLayout.of(facing);return FRAMES[facing.get3DDataValue()];}
}
