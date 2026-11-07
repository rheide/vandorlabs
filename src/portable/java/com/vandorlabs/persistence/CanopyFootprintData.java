package com.vandorlabs.persistence;

/** Released bounded footprint triples; invalid envelopes and valid empty filtered lists stay distinct. */
public final class CanopyFootprintData {
    private CanopyFootprintData(){}
    public static final class Offsets {
        private final int[] triples;
        private Offsets(int[] triples){this.triples=triples;}
        public int size(){return triples.length/3;}
        public int x(int index){return triples[index*3];}
        public int y(int index){return triples[index*3+1];}
        public int z(int index){return triples[index*3+2];}
    }
    private static boolean valid(int[] raw,int at){return Math.abs((long)raw[at])<=8&&Math.abs((long)raw[at+1])<=8&&Math.abs((long)raw[at+2])<=8;}
    public static Offsets decode(int[] raw){
        if(raw==null||raw.length==0||raw.length>3000||raw.length%3!=0)return null;int length=0;
        for(int i=0;i<raw.length;i+=3)if(valid(raw,i))length+=3;
        int[] result=new int[length];int at=0;for(int i=0;i<raw.length;i+=3)if(valid(raw,i)){result[at++]=raw[i];result[at++]=raw[i+1];result[at++]=raw[i+2];}return new Offsets(result);
    }
}
