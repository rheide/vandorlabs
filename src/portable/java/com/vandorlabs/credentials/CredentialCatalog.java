package com.vandorlabs.credentials;

/** Stable legacy company indices and scalar reader/filter policy; no item or world ownership. */
public final class CredentialCatalog {
 public static final int COMPANY_COUNT=7,ANY=127,NO_SELECTION=Integer.MIN_VALUE;
 /** Shared with the existing public ItemDesignToken.DESIGNS array, preserving its mutable API. */
 public static final String[] DESIGNS={"unbranded","pinnacle","ashiro","paladyne","vektor","seyr","vandor"};
 private CredentialCatalog(){}
 public static boolean validCompany(int company){return company>=0&&company<COMPANY_COUNT;}
 public static String companyName(int company){return DESIGNS[validCompany(company)?company:0];}
 public static int singleCompany(int mask){mask&=ANY;return (mask&1)!=0?ANY:Integer.lowestOneBit(mask);}
 public static int effectiveAllowed(boolean any,int allowed){return any?ANY:singleCompany(allowed);}
 public static boolean authorized(int company,int mask){return validCompany(company)&&((mask&1)!=0||(mask&(1<<company))!=0);}
 public static int sampleCompany(int mask){return Integer.bitCount(mask)==1&&mask>1&&mask<=64?Integer.numberOfTrailingZeros(mask):-1;}
 public static String label(boolean chip,int mask){
  if(mask==0)return chip?"No chip":"No card";int company=sampleCompany(mask);if(company<0)return chip?"Any chip":"Any card";
  String name=companyName(company);return Character.toUpperCase(name.charAt(0))+name.substring(1);
 }
 public static int filterSelection(double x,double y,int button,int left,int top,int mask){
  if((button!=0&&button!=1)||!(x>=left&&x<left+154&&y>=top&&y<top+18))return NO_SELECTION;
  if(x<left+128)return ANY;int company=Integer.bitCount(mask)==1&&mask>1?Integer.numberOfTrailingZeros(mask):0;
  company=Math.floorMod(company+(button==1?-1:1),COMPANY_COUNT);return company==0?ANY:1<<company;
 }
 public static int behavior(int mode){return mode>=0&&mode<4?mode:0;}
 public static String behaviorName(int mode){return new String[]{"Stay Inserted","Stay Inserted: Pulse","Return After Open: Toggle","Return After Open: Pulse"}[Math.max(0,Math.min(3,mode))];}
 public static int output(int mode,boolean authorized,boolean toggled,long now,long pulseUntil){return mode==2?(toggled?15:0):mode==3?(now<pulseUntil?15:0):authorized&&(mode==0||now<pulseUntil)?15:0;}
}
