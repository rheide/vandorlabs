package com.vandorlabs.ports;

import java.util.Arrays;

/** Shared six-face transfer policy. Face indices use DOWN, UP, NORTH, SOUTH, WEST, EAST. */
public final class ResourcePortSettings {
    public enum Face { DISABLED, INPUT, OUTPUT }
    public enum OnTexture { REDSTONE, CONNECTED, TRANSFERRING }
    /** Zero is unlimited; an empty editor field has this representation. */
    public final long throughput;
    public final boolean pullInputs;
    public final OnTexture onTexture;
    private final Face[] faces;
    public ResourcePortSettings(Face[] faces,long throughput,boolean pullInputs,OnTexture onTexture){
        if(faces==null||faces.length!=6||throughput<0||onTexture==null)throw new IllegalArgumentException("Invalid resource port settings");
        this.faces=faces.clone();for(Face face:this.faces)if(face==null)throw new IllegalArgumentException("Missing resource port face");
        this.throughput=throughput;this.pullInputs=pullInputs;this.onTexture=onTexture;
    }
    public static ResourcePortSettings placed(int playerFacing){
        if(playerFacing<0||playerFacing>=6)throw new IllegalArgumentException("Invalid resource port placement face");
        Face[] faces=new Face[6];Arrays.fill(faces,Face.DISABLED);faces[playerFacing]=Face.INPUT;faces[playerFacing^1]=Face.OUTPUT;
        return new ResourcePortSettings(faces,0,false,OnTexture.TRANSFERRING);
    }
    public Face face(int side){return faces[side];}
    public Face[] faces(){return faces.clone();}
    public ResourcePortSettings withFace(int side,Face face){Face[] changed=faces();changed[side]=face;return new ResourcePortSettings(changed,throughput,pullInputs,onTexture);}
    public ResourcePortSettings withTransfer(long throughput,boolean pullInputs,OnTexture texture){return new ResourcePortSettings(faces,throughput,pullInputs,texture);}
    /** Null means invalid; refuse signs, exponents, overflow and ambiguous zero rates. */
    public static Long parseThroughput(String value){
        if(value==null||value.length()>19)return null;String text=value.trim();if(text.isEmpty())return 0L;
        for(int i=0;i<text.length();i++)if(text.charAt(i)<'0'||text.charAt(i)>'9')return null;
        try{long parsed=Long.parseLong(text);return parsed>0?parsed:null;}catch(NumberFormatException invalid){return null;}
    }
    public String throughputText(){return throughput==0?"":Long.toString(throughput);}
    @Override public boolean equals(Object other){if(!(other instanceof ResourcePortSettings))return false;ResourcePortSettings that=(ResourcePortSettings)other;return throughput==that.throughput&&pullInputs==that.pullInputs&&onTexture==that.onTexture&&Arrays.equals(faces,that.faces);}
    @Override public int hashCode(){return 31*(31*(31*Arrays.hashCode(faces)+Long.hashCode(throughput))+(pullInputs?1:0))+onTexture.hashCode();}
}
