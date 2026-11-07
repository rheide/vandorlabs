package com.vandorlabs.items;

/** Conservative retained-tree accounting for settings-only undo across platform NBT APIs. */
public final class CopiedSettingsMemory {
 public static final long PLAYER_BYTES=16L*1024*1024,TOTAL_BYTES=64L*1024*1024;
 public interface Tags<T> {
  int type(T tag);
  Iterable<String> keys(T compound);
  T member(T compound,String key);
  /** List/array elements, or UTF-16 characters for a string. */
  int length(T tag);
  T element(T list,int index);
 }
 public static <T> long estimate(T tag,Tags<T> tags){
  int type=tags.type(tag);
  if(type==10){long size=128;for(String key:tags.keys(tag))size+=112L+2L*key.length()+estimate(tags.member(tag,key),tags);return size;}
  if(type==9){int count=tags.length(tag);long size=80L+8L*count;for(int i=0;i<count;i++)size+=estimate(tags.element(tag,i),tags);return size;}
  if(type==8)return 64L+2L*tags.length(tag);
  if(type==11)return 40L+4L*tags.length(tag);
  if(type==7)return 40L+tags.length(tag);
  // Released captures contain no long arrays. Keep future payloads unretained until measured.
  return type==12?PLAYER_BYTES+1:32;
 }
 private CopiedSettingsMemory(){}
}
