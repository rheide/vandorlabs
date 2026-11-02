package com.vandorlabs.client;

import com.vandorlabs.canopy.CanopyInteractions;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import java.io.*;
import java.util.*;

/** Loaded-map population diagnostic plus native owner lifecycle assertions. */
final class CollisionOwnerChecks {
    private static void require(boolean b,String s){if(!b)throw new IllegalStateException("collision-owner: "+s);}
    private static List<TileEntityCanopy> reference(World world,AxisAlignedBB region){
        AxisAlignedBB q=region.grow(12);List<TileEntityCanopy> out=new ArrayList<>();
        for(int x=MathHelper.floor(q.minX)>>4;x<=MathHelper.floor(q.maxX)>>4;x++)for(int z=MathHelper.floor(q.minZ)>>4;z<=MathHelper.floor(q.maxZ)>>4;z++){
            Chunk chunk=world.getChunkProvider().getLoadedChunk(x,z);if(chunk==null)continue;
            for(TileEntity tile:chunk.getTileEntityMap().values())if(tile instanceof TileEntityCanopy&&q.intersects(new AxisAlignedBB(tile.getPos()))&&((TileEntityCanopy)tile).draws())out.add((TileEntityCanopy)tile);
        }
        return out;
    }
    private static long sample(Runnable action){long[] n=new long[7];for(int k=0;k<7;k++){long start=System.nanoTime();for(int i=0;i<2000;i++)action.run();n[k]=(System.nanoTime()-start)/2000;}Arrays.sort(n);return n[3];}
    static void run(Minecraft mc,File out)throws Exception{
        World world=mc.world;BlockPos base=new BlockPos((mc.player.getPosition().getX()>>4)*16,140,(mc.player.getPosition().getZ()>>4)*16),owner=base.add(16,-65,16);
        List<BlockPos> background=new ArrayList<>();
        try{
            // The original search traverses all tile heights in each chunk. The
            // unrelated entries reproduce that cost without ticking fake tiles.
            for(int x=0;x<32;x++)for(int z=0;z<32;z++)for(int y=0;y<6;y++){
                BlockPos p=base.add(x,y,z);Chunk chunk=world.getChunkProvider().getLoadedChunk(p.getX()>>4,p.getZ()>>4);require(chunk!=null,"fixture chunk not loaded "+p);
                if(chunk.getTileEntityMap().containsKey(p))continue;TileEntityProgrammableGlass t=new TileEntityProgrammableGlass();t.setWorld(world);t.setPos(p);chunk.getTileEntityMap().put(p,t);background.add(p);
            }
            world.setBlockState(owner,Block.getBlockFromName("vandorlabs:regular_canopy_glass").getDefaultState(),3);
            TileEntityCanopy tile=(TileEntityCanopy)world.getTileEntity(owner);require(tile!=null,"native tile missing");
            AxisAlignedBB near=new AxisAlignedBB(owner).grow(.2),particle=new AxisAlignedBB(owner.down(24)).grow(.1);
            require(CanopyInteractions.owners(world,near).contains(tile),"native onLoad not indexed");
            for(AxisAlignedBB q:new AxisAlignedBB[]{near,particle,new AxisAlignedBB(owner.east(10)).grow(.1),new AxisAlignedBB(owner.north(13)).grow(.1)})require(new HashSet<>(reference(world,q)).equals(new HashSet<>(CanopyInteractions.owners(world,q))),"owner broadphase changed "+q);
            tile.onChunkUnload();require(!CanopyInteractions.owners(world,near).contains(tile),"unloaded tile retained");tile.onLoad();require(CanopyInteractions.owners(world,near).contains(tile),"reload missing owner");
            List<AxisAlignedBB> direct=new ArrayList<>(),event=new ArrayList<>();tile.addCollisionBoxes(near,direct);new CanopyInteractions().collision(new net.minecraftforge.event.world.GetCollisionBoxesEvent(world,null,near,event));require(event.containsAll(direct),"indexed collision lost geometry");
            long before=sample(()->reference(world,particle)),after=sample(()->CanopyInteractions.owners(world,particle));
            try(PrintWriter csv=new PrintWriter(new File(out,"collision-owner-costs.csv"))){csv.println("unrelated_loaded_tiles,queries_per_sample,before_ns_per_query,after_ns_per_query");csv.println(background.size()+",2000,"+before+","+after);}
            world.setBlockToAir(owner);require(!CanopyInteractions.owners(world,near).contains(tile),"removed tile retained");
            List<AxisAlignedBB> expected=world.getEntitiesWithinAABB(com.vandorlabs.turntable.EntityTurntable.class,particle.grow(32)).isEmpty()?Collections.emptyList():null;
            if(expected!=null){List<AxisAlignedBB> actual=new ArrayList<>();com.vandorlabs.turntable.TurntableEvents.collisions(new net.minecraftforge.event.world.GetCollisionBoxesEvent(world,null,particle,actual));require(actual.isEmpty(),"empty turntable observer returned collision");}
            System.out.println("[vandorlabs][reprolab] collision-owner PASS tiles="+background.size()+" before-ns="+before+" after-ns="+after+" exact-candidates geometry removal chunk-unload reload empty-turntables");
        }finally{
            world.setBlockToAir(owner);
            for(BlockPos p:background){Chunk c=world.getChunkProvider().getLoadedChunk(p.getX()>>4,p.getZ()>>4);if(c!=null)c.getTileEntityMap().remove(p);}
        }
    }
    private CollisionOwnerChecks(){}
}
