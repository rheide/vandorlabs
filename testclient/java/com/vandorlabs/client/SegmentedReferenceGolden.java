package com.vandorlabs.client;
/** Canonical LF records enforce the current Source geometry contract on Linux and Windows. */
final class SegmentedReferenceGolden {
 private SegmentedReferenceGolden(){}
 static void check(java.io.File file,String expected)throws Exception{
  java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");for(String line:java.nio.file.Files.readAllLines(file.toPath(),java.nio.charset.StandardCharsets.UTF_8)){digest.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));digest.update((byte)'\n');}StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!expected.equals(actual.toString()))throw new AssertionError("Segmented Door source vertex/clock contract changed: "+actual+" expected="+expected);
 }
}
