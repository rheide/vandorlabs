package com.vandorlabs.neoforge.programmer;
import com.google.gson.*;
import com.vandorlabs.neoforge.programmer.client.NativeProgrammerJournalFrame;
import java.nio.file.*;
import net.minecraft.core.Direction;
public final class ProgrammerJournalFrameChecks {
 public static void main(String[] args)throws Exception{var reference=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();if(!reference.get("golden").getAsString().equals("c20e4807514ddb65c844b44ccdaacddc0d60c83c00f30c9e87883f658292ec7b"))throw new AssertionError("Released matrix reference");int checks=0;for(var value:reference.getAsJsonArray("cases")){var row=value.getAsJsonObject();Direction face=Direction.valueOf(row.get("facing").getAsString());var frame=NativeProgrammerJournalFrame.matrix(face);var values=row.getAsJsonArray("matrix");for(int i=0;i<16;i++){if(Float.floatToRawIntBits(frame.get(i/4,i%4))!=values.get(i).getAsInt())throw new AssertionError("Source journal frame "+face+" component="+i+" expected="+Float.intBitsToFloat(values.get(i).getAsInt())+" actual="+frame.get(i/4,i%4));checks++;}if(frame!=NativeProgrammerJournalFrame.matrix(face))throw new AssertionError("Frame cache identity");}if(checks!=64)throw new AssertionError("Whole four-facing matrix corpus");System.out.println("[vandorlabs][programmer-journal-frame] PASS exactSourceFloatComponents="+checks);}
}
