package com.vandorlabs.client;

import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.CoreModManager;
import java.util.*;

/** Exercise actual compiled tile lists without a world or graphics context. */
public final class GlassPaneAdmissionChecks {
    private static int checks;
    private static Set<TileEntity> identities(){return Collections.newSetFromMap(new IdentityHashMap<>());}
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static CompiledChunk chunk(TileEntity... tiles){CompiledChunk chunk=new CompiledChunk();chunk.getTileEntities().addAll(Arrays.asList(tiles));return chunk;}
    private static int count(CompiledChunk chunk,TileEntity tile){int count=0;for(TileEntity found:chunk.getTileEntities())if(found==tile)count++;return count;}

    private static void admissions(){
        TileEntity anchor=new TileEntity(){},member=new TileEntity(){},frameFallback=new TileEntity(){},foreign=new TileEntity(){};
        Set<TileEntity> installed=identities();installed.addAll(Arrays.asList(anchor,member,frameFallback,foreign));
        Set<TileEntity> published=identities();
        GlassPaneAdmissions admissions=new GlassPaneAdmissions();
        CompiledChunk first=chunk(anchor,member,frameFallback,foreign);
        admissions.prune(first,published::contains,installed::contains);
        need(first.getTileEntities().size()==4&&admissions.removedCount()==0,"pending GPU publication suppressed original renderers");

        published.add(member);
        admissions.prune(first,published::contains,installed::contains);
        need(!first.getTileEntities().contains(member),"published member remained in render admission");
        need(first.getTileEntities().contains(anchor),"group anchor was suppressed");
        need(first.getTileEntities().contains(frameFallback),"unpublished selected frame lost its fallback");
        need(first.getTileEntities().contains(foreign),"unrelated tile was suppressed");
        need(admissions.removedCount()==1,"removed owner tracking disagreed with actual list");
        for(int i=0;i<100;i++)admissions.prune(first,published::contains,installed::contains);
        need(first.getTileEntities().size()==3&&admissions.removedCount()==1,"stable polling duplicated or lost admissions");

        // A selected frame can need a fallback again after its render chunk is recycled.
        published.clear();
        admissions.prune(first,published::contains,installed::contains);
        need(count(first,member)==1&&admissions.removedCount()==0,"lost frame readiness did not restore its tile");
        published.add(member);
        admissions.prune(first,published::contains,installed::contains);
        admissions.restore(installed::contains);
        admissions.restore(installed::contains);
        need(count(first,member)==1,"repeated invalidation restoration duplicated owner");

        admissions.prune(first,published::contains,installed::contains);
        first.getTileEntities().add(member); // Another publisher already restored the same owner.
        admissions.restore(installed::contains);
        need(count(first,member)==1,"restoration duplicated an externally restored owner");

        admissions.prune(first,published::contains,installed::contains);
        CompiledChunk rebuilt=chunk(anchor,member,frameFallback,foreign);
        admissions.prune(rebuilt,published::contains,installed::contains);
        need(count(first,member)==1,"replacement forgot to release its original compiled list");
        need(count(rebuilt,member)==0&&admissions.removedCount()==1,"new uploaded generation was not pruned");

        // The same position can have a new tile identity in a recycled world/section.
        TileEntity replacement=new TileEntity(){};installed.remove(member);installed.add(replacement);
        CompiledChunk recycled=chunk(replacement,foreign);
        admissions.prune(recycled,published::contains,installed::contains);
        need(count(rebuilt,member)==0,"removed owner was resurrected into its old generation");
        need(count(recycled,member)==0&&count(recycled,replacement)==1,"old owner leaked into a replacement section");
        need(admissions.removedCount()==0,"recycled section retained stale removal records");

        installed.add(member);published.add(member);
        CompiledChunk last=chunk(anchor,member);
        admissions.prune(last,published::contains,installed::contains);
        installed.remove(anchor);published.clear();
        admissions.prune(last,published::contains,installed::contains);
        need(count(last,member)==1,"losing the group anchor did not release surviving members");
        published.add(member);admissions.prune(last,published::contains,installed::contains);
        installed.clear();admissions.restore(installed::contains);
        need(count(last,member)==0,"world teardown restored an uninstalled owner");
        need(admissions.removedCount()==0,"world teardown retained strong owner references");
        int dummySize=CompiledChunk.DUMMY.getTileEntities().size();
        admissions.prune(CompiledChunk.DUMMY,tile->true,tile->true);admissions.restore(tile->true);
        need(CompiledChunk.DUMMY.getTileEntities().size()==dummySize,"shared dummy chunk was mutated");
    }

    private static void bounds(){
        for(int origin:new int[]{-30000000,-16,0,16,29999984})for(boolean rotated:new boolean[]{false,true})
                for(int depth=0;depth<3;depth++)for(int plane:new int[]{0,7,15}){
            BlockPos section=new BlockPos(origin,64,origin);
            AxisAlignedBB bounds=GlassPaneChunks.paneBounds(section,rotated,depth,plane,2,3,16,15);
            double offset=(depth==1?-6:depth==2?6:0)/16D;
            for(int u:new int[]{2,16})for(int v:new int[]{3,15})for(double face:new double[]{7D/16,.5,9D/16}){
                double x=origin+(rotated?plane+1-face-offset:u),y=64+v,z=origin+(rotated?u:plane+face+offset);
                need(x>=bounds.minX&&x<=bounds.maxX&&y>=bounds.minY&&y<=bounds.maxY&&z>=bounds.minZ&&z<=bounds.maxZ,
                        "anchor culling bounds omitted an actual batch corner");
            }
            need(bounds.minY==67&&bounds.maxY==79,"group bounds lost vertical extent");
            need((rotated?bounds.maxX-bounds.minX:bounds.maxZ-bounds.minZ)==.125,"group bounds changed native pane thickness");
            need((rotated?bounds.minZ:bounds.minX)==origin+2&&(rotated?bounds.maxZ:bounds.maxX)==origin+16,
                    "group bounds shrank to the anchor block");
        }
    }

    public static void main(String[] args)throws Exception{
        java.lang.reflect.Field environment=CoreModManager.class.getDeclaredField("deobfuscatedEnvironment");environment.setAccessible(true);
        boolean previous=environment.getBoolean(null);
        try{
            environment.setBoolean(null,true);
            admissions();bounds();
            System.out.println("glass-pane-admissions PASS "+checks+" checks: pending publication, retained anchor/frame fallback, stable pruning, invalidation, new uploads, stale identities, teardown and full batch bounds");
        }finally{environment.setBoolean(null,previous);}
    }

    private GlassPaneAdmissionChecks(){}
}
