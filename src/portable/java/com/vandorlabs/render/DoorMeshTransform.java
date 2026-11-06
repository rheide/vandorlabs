package com.vandorlabs.render;

/** Released double-precision CPU door transform; preserves the authored 0..32 pixel height. */
public final class DoorMeshTransform {
    private DoorMeshTransform(){}
    /** Input/output retain the caller's complete vertex format. Only positions and optional light change. */
    public static void write(int[] source,int stride,int[] out,boolean panel,boolean writeLight,int facing,double x,double y,double z,double depth,int light,double shiftX,double shiftY,double pivotX,double pivotZ,double angle,double size,double leafOffset){
        if(stride<7||source.length%stride!=0||out.length<source.length)throw new IllegalArgumentException("Door vertex record size");
        double radians=Math.toRadians(angle),cos=Math.cos(radians),sin=Math.sin(radians);
            // Keep the panel's existing lightmap convention; item leaves use constant block/sky coordinates.
            int packed=panel?(light>>>16)|(light<<16):light;
            for(int i=0;i<source.length;i+=stride){
                double px=Float.intBitsToFloat(source[i]),py=Float.intBitsToFloat(source[i+1]),pz=Float.intBitsToFloat(source[i+2]);
                px*=size;py*=size;
                if(panel)py+=SpaceDoorControlPanel.verticalOffset(size);
                double dx=px-pivotX,dz=pz-pivotZ;
                px=cos*dx+sin*dz+pivotX+shiftX+leafOffset;pz=-sin*dx+cos*dz+pivotZ+depth;py+=shiftY;
                double worldX,worldZ;
                switch(facing){
                    case 2:worldX=1-px;worldZ=1-pz;break;
                    case 5:worldX=pz;worldZ=1-px;break;
                    case 4:worldX=1-pz;worldZ=px;break;
                    default:worldX=px;worldZ=pz;
                }
                out[i]=Float.floatToRawIntBits((float)(worldX+x));out[i+1]=Float.floatToRawIntBits((float)(py+y));
                out[i+2]=Float.floatToRawIntBits((float)(worldZ+z));if(writeLight)out[i+6]=packed;
            }
    }
}
