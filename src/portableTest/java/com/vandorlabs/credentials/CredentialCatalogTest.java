package com.vandorlabs.credentials;

/** Frozen actual Forge filter transcript plus authorization/visual-fallback separation. */
public final class CredentialCatalogTest {
 private static void need(boolean ok){if(!ok)throw new AssertionError("Credential catalog contract");}
 public static void main(String[] args)throws Exception{
  java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");int cases=0;
  for(boolean chip:new boolean[]{false,true})for(int mask=-256;mask<=512;mask++){
   String head=chip+":"+mask+":"+CredentialCatalog.label(chip,mask)+":"+CredentialCatalog.sampleCompany(mask)+":"+CredentialCatalog.singleCompany(mask)+";";digest.update(head.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   for(int button:new int[]{-1,0,1,2})for(int[] point:new int[][]{{10,20},{137,20},{138,20},{163,20},{164,20},{10,19},{10,37},{10,38},{9,20}}){int selected=CredentialCatalog.filterSelection(point[0],point[1],button,10,20,mask);String value=(selected!=Integer.MIN_VALUE)+":"+selected+";";digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));cases++;}
  }
  StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format("%02x",b&255));need(hash.toString().equals("8e140e5e4b2217161b2d102b872f1444eb00b07fcf440c800ccea50e934818c5"));
  for(int company=-2;company<10;company++)for(int mask=0;mask<128;mask++){boolean expected=company>=0&&company<7&&((mask&1)!=0||(mask&(1<<company))!=0);need(CredentialCatalog.authorized(company,mask)==expected);need(CredentialCatalog.effectiveAllowed(true,mask)==127);int single=CredentialCatalog.singleCompany(mask);need(single==127||Integer.bitCount(single)<=1);}
  need(CredentialCatalog.companyName(8).equals("unbranded")&&!CredentialCatalog.validCompany(8));need(CredentialCatalog.sampleCompany(128)==-1&&!CredentialCatalog.authorized(7,127));
  need(CredentialCatalog.filterSelection(Double.NaN,20,0,10,20,2)==CredentialCatalog.NO_SELECTION);need(CredentialCatalog.filterSelection(138,20,1,10,20,2)==127);need(CredentialCatalog.filterSelection(163.999,37.999,0,10,20,64)==127);need(CredentialCatalog.filterSelection(164,20,0,10,20,2)==CredentialCatalog.NO_SELECTION);
  need(CredentialCatalog.singleCompany(Integer.MIN_VALUE)==0&&CredentialCatalog.singleCompany(Integer.MAX_VALUE)==127);
  System.out.println("Credential catalog PASS actualForgeGolden="+hash+" cases="+cases+" mask/hit/previous policy and invalid credential versus fallback icon");
 }
}
