package com.vandorlabs.neoforge.client;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

/** Client section/task identities. Completion callbacks never retain or republish a world. */
public final class NativeSegmentedSectionUploads {
    private NativeSegmentedSectionUploads(){}
    public interface Section {
        BlockPos vandorlabs$terrainOrigin();
        Object vandorlabs$terrainCompiled();
        Object vandorlabs$terrainTask();
        boolean vandorlabs$terrainDirty();
    }
    public interface Task {
        void vandorlabs$terrainPublication(Publication publication);
        Publication vandorlabs$terrainPublication();
        Object vandorlabs$terrainUploaded();
        void vandorlabs$terrainUploaded(Object compiled);
    }
    public record Publication(long world,long generation,BlockPos origin){
        public Publication {if(world<1||generation<1||origin==null)throw new IllegalArgumentException("Section publication");origin=origin.immutable();}
    }
    private static final ConcurrentHashMap<Section,Publication> TRACKED=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Section,Integer> OBSERVERS=new ConcurrentHashMap<>();
    /** Separate from Segmented publication ownership: only active terrain-backed mechanisms retain this lease. */
    public static final class Observation implements AutoCloseable {
        private Section section;
        private Observation(Section section){this.section=section;OBSERVERS.compute(section,(key,count)->{if(count==null&&OBSERVERS.size()>=4096)throw new IllegalStateException("Terrain observation bound");return count==null?1:Math.addExact(count,1);});}
        @Override public void close(){var previous=section;if(previous==null)return;section=null;OBSERVERS.computeIfPresent(previous,(key,count)->count==1?null:count-1);}
    }
    public static Observation observe(Section section){return new Observation(java.util.Objects.requireNonNull(section));}
    public static boolean observed(Section section){return OBSERVERS.containsKey(section);}
    public static int observers(){return OBSERVERS.size();}
    private static volatile long world;
    // Reflective smoke fault injection; never set by gameplay, packets, resources or configuration.
    private static volatile boolean holdAcknowledgements;
    /** Called on world replacement and reload before publishing fresh section generations. */
    public static void clear(){world++;TRACKED.clear();}
    public static Publication publication(Section section){return TRACKED.get(section);}
    public static Publication publish(Section section,long generation){
        if(world==0)clear();var previous=TRACKED.get(section);var origin=section.vandorlabs$terrainOrigin();
        if(previous!=null&&previous.world==world&&previous.generation==generation&&previous.origin.equals(origin))return previous;
        var next=new Publication(world,generation,origin);TRACKED.put(section,next);return next;
    }
    public static void remove(Section section,Publication publication){TRACKED.remove(section,publication);}
    public static boolean current(Section section,Publication publication){return publication!=null&&publication.world==world&&TRACKED.get(section)==publication&&publication.origin.equals(section.vandorlabs$terrainOrigin());}
    public static void completed(Section section,Task task,Object result,Throwable failure){
        // Record the actual current-task upload independently of a mechanism's generation claim.
        // Existing segmented acknowledgements still require their exact current publication below.
        if(failure==null&&result instanceof Enum<?> status&&status.name().equals("SUCCESSFUL")
                &&section.vandorlabs$terrainTask()==task)
            task.vandorlabs$terrainUploaded(section.vandorlabs$terrainCompiled());
    }
    /** Additive observer for other terrain-backed mechanisms. Callers also retain their world/section origin identity. */
    public static boolean observedUpload(Section section,Object beforeCompiled,Object beforeTask){
        if(holdAcknowledgements||section.vandorlabs$terrainDirty())return false;
        Object compiled=section.vandorlabs$terrainCompiled(),task=section.vandorlabs$terrainTask();
        return compiled!=beforeCompiled&&task!=beforeTask&&task instanceof Task receipt&&receipt.vandorlabs$terrainUploaded()==compiled;
    }
    public static boolean acknowledged(Section section,Publication publication,Object before){
        if(holdAcknowledgements||!current(section,publication)||section.vandorlabs$terrainDirty())return false;
        var current=section.vandorlabs$terrainCompiled();
        return current!=before&&section.vandorlabs$terrainTask() instanceof Task task
                &&task.vandorlabs$terrainPublication()==publication&&task.vandorlabs$terrainUploaded()==current;
    }
    public static int tracked(){return TRACKED.size();}
}
