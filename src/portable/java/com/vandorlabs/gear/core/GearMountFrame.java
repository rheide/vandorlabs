package com.vandorlabs.gear.core;
/** Exact authored downward-arm basis; faces use stable DOWN,UP,NORTH,SOUTH,WEST,EAST ordinals. */
public final class GearMountFrame {
 private static void face(int face){if(face<0||face>5)throw new IllegalArgumentException("Unknown arm mounting face");}
 public static double x(int face,double x,double y,double z){face(face);switch(face){case 5:return -y;case 4:return y;default:return x;}}
 public static double y(int face,double x,double y,double z){face(face);switch(face){case 1:return -y;case 5:return x;case 4:return -x;case 2:return -z;case 3:return z;default:return y;}}
 public static double z(int face,double x,double y,double z){face(face);switch(face){case 1:return -z;case 2:return y;case 3:return -y;default:return z;}}
 private GearMountFrame(){}
}
