package com.vandorlabs.render;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

/** Float-record fingerprint exported from the original 1.12 renderer with an identity UV backend. */
public final class PanelMeshTransformChecks {
    private static final String EXPECTED="3f155f2ebaf5b54ed20ef34e5fe5256b0010d5660791025268544ac64435300d";
    private static float[] source(){return new float[]{0,0,6,0,1,.24253562F,0,-.9701425F,0,16,6,0,0,.24253562F,0,-.9701425F,16,16,10,1,0,.24253562F,0,-.9701425F,16,0,10,1,1,.24253562F,0,-.9701425F};}
    private static String fingerprint(float[] input)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        for(int turns=0;turns<4;turns++)for(double depth:new double[]{-6,0,6})for(double left:new double[]{0,2.25})for(double width:new double[]{.25,.625,1})for(double near:new double[]{0,6,12}){
            float[] transformed=PanelMeshTransform.transform(input,turns,depth,left,width);
            for(float[] data:new float[][]{transformed,PanelMeshTransform.box(transformed,near,-1,-1,near+4,17,17,false),PanelMeshTransform.box(transformed,near-.02,-1,-1,near+4.02,17,17,true),PanelMeshTransform.end(transformed,near,6,false),PanelMeshTransform.end(transformed,near+4,6,true)}){
                out.writeInt(data.length);for(float value:data)out.writeInt(Float.floatToRawIntBits(value));
            }
        }
        StringBuilder result=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()))result.append(String.format("%02x",value&255));return result.toString();
    }
    public static void main(String[] args)throws Exception{
        float[] input=source(),before=input.clone();String hash=fingerprint(input);
        if(!EXPECTED.equals(hash))throw new AssertionError("Original renderer float-record contract changed: "+hash);
        if(!Arrays.equals(input,before))throw new AssertionError("Panel transform mutated its source");
        input[3]=.125F;if(EXPECTED.equals(fingerprint(input)))throw new AssertionError("Changed UV negative accepted");
        float[] cap=PanelMeshTransform.end(source(),8,6,true);
        if(cap.length!=32)throw new AssertionError("Opaque stem cap lost its front section");
        for(int i=0;i<cap.length;i+=8)if(cap[i]!=8||cap[i+3]<0||cap[i+3]>.25||cap[i+4]<0||cap[i+4]>1||cap[i+5]!=1||cap[i+6]!=0||cap[i+7]!=0)throw new AssertionError("Cap position/metal UV/normal contract changed");
        System.out.println("Panel mesh transform PASS 1080 original-renderer float fingerprints, immutable source, interpolated attributes, cap semantics and changed-UV negative");
    }
}
