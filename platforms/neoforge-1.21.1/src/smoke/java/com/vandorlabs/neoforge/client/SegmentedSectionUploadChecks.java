package com.vandorlabs.neoforge.client;
import net.minecraft.core.BlockPos;
/** Identity/generation barrier negatives independent of GL; actual upload path is a world fixture. */
public final class SegmentedSectionUploadChecks {
 private enum Result{SUCCESSFUL,CANCELLED}
 private static final class Section implements NativeSegmentedSectionUploads.Section {
  BlockPos origin=new BlockPos(0,64,0);Object compiled=new Object(),task;boolean dirty;
  public BlockPos vandorlabs$terrainOrigin(){return origin;}public Object vandorlabs$terrainCompiled(){return compiled;}public Object vandorlabs$terrainTask(){return task;}public boolean vandorlabs$terrainDirty(){return dirty;}
 }
 private static final class Task implements NativeSegmentedSectionUploads.Task {
  NativeSegmentedSectionUploads.Publication publication;Object uploaded;
  public void vandorlabs$terrainPublication(NativeSegmentedSectionUploads.Publication value){publication=value;}public NativeSegmentedSectionUploads.Publication vandorlabs$terrainPublication(){return publication;}public Object vandorlabs$terrainUploaded(){return uploaded;}public void vandorlabs$terrainUploaded(Object value){uploaded=value;}
 }
 public static void main(String[] args){NativeSegmentedSectionUploads.clear();Section section=new Section();Object before=section.compiled;var p=NativeSegmentedSectionUploads.publish(section,1);Task task=new Task();task.publication=p;section.task=task;
  need(!ack(section,p,before),"pending future");section.compiled=new Object();need(!ack(section,p,before),"compiled change alone");NativeSegmentedSectionUploads.completed(section,task,Result.CANCELLED,null);need(!ack(section,p,before),"cancelled result");NativeSegmentedSectionUploads.completed(section,task,Result.SUCCESSFUL,new IllegalStateException("failed upload"));need(!ack(section,p,before),"failed future");
  Task old=task;task=new Task();task.publication=p;section.task=task;NativeSegmentedSectionUploads.completed(section,old,Result.SUCCESSFUL,null);need(old.uploaded==null&&!ack(section,p,before),"late old task while new upload pending");NativeSegmentedSectionUploads.completed(section,task,Result.SUCCESSFUL,null);section.dirty=true;need(!ack(section,p,before),"dirty current task");section.dirty=false;need(ack(section,p,before),"current successful installed upload");section.compiled=new Object();need(!ack(section,p,before),"installed identity changed after task");
  var next=NativeSegmentedSectionUploads.publish(section,2);NativeSegmentedSectionUploads.completed(section,task,Result.SUCCESSFUL,null);need(!ack(section,next,before),"new publication before old future completion");Task fresh=new Task();fresh.publication=next;section.task=fresh;NativeSegmentedSectionUploads.completed(section,fresh,Result.SUCCESSFUL,null);need(ack(section,next,before),"new generation ACK");
  section.origin=new BlockPos(16,64,0);need(!ack(section,next,before),"section recycled origin");NativeSegmentedSectionUploads.clear();section.origin=p.origin();NativeSegmentedSectionUploads.completed(section,fresh,Result.SUCCESSFUL,null);need(NativeSegmentedSectionUploads.tracked()==0&&!ack(section,next,before),"reload/unload cannot resurrect tracking");var newWorld=NativeSegmentedSectionUploads.publish(section,1);need(newWorld.world()!=p.world(),"world epoch separated");NativeSegmentedSectionUploads.remove(section,p);need(NativeSegmentedSectionUploads.tracked()==1,"old release cannot remove new publication");NativeSegmentedSectionUploads.remove(section,newWorld);need(NativeSegmentedSectionUploads.tracked()==0,"tracked section release");
  System.out.println("[vandorlabs][segmented-section-uploads] CPU PASS pending/delayed/cancelled/failure/stale task/generation/installed identity/dirty/recycled section/reload/world epoch/release; no world upload claim");
 }
 private static boolean ack(Section s,NativeSegmentedSectionUploads.Publication p,Object before){return NativeSegmentedSectionUploads.acknowledged(s,p,before);}private static void need(boolean value,String message){if(!value)throw new AssertionError(message);}
}
