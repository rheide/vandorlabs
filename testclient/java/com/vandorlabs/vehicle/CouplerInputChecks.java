package com.vandorlabs.vehicle;
import com.vandorlabs.coupler.CouplerInput;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
/** Pure policy replay of independently frozen installed Source callbacks. */
public final class CouplerInputChecks {
    public static void main(String[] args)throws Exception{
        int rows=0,profiles=0;CouplerInput input=null;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(Paths.get("testclient/fixtures/coupler-input-source.csv.gz"))),StandardCharsets.US_ASCII))){String line;while((line=reader.readLine())!=null){if(line.length()>128||++rows>12288)throw new AssertionError("Source corpus bound");String[] fields=line.split(",");if(fields.length!=11)throw new AssertionError("Source schema");int[] n=new int[11];for(int i=0;i<n.length;i++)n[i]=Integer.parseInt(fields[i]);if(n[5]==0){input=new CouplerInput();input.read(n[1]!=0,n[1]==2);profiles++;}boolean channel=(n[3]&(1<<n[5]))!=0,power=channel||(n[4]&(1<<n[5]))!=0;boolean requested=input.sample(power,n[0]!=0);if(requested!=(n[6]!=0)||input.overridden()!=(n[8]!=0)||(input.overridden()&&input.manualRequested())!=(n[9]!=0)||requested!=(n[10]!=0)||channel!=(n[7]!=0))throw new AssertionError("Source input mismatch row="+rows+" "+line);}}
        if(rows!=12288||profiles!=3072)throw new AssertionError("Source coverage");System.out.println("Coupler shared input PASS profiles="+profiles+" actualSourceTransitions="+rows+" requested/manual history/physical lock/legacy defaults");
    }
}
