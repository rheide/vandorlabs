package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import com.vandorlabs.tiles.TileEntityCanopy;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import java.nio.ByteBuffer;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

/** No prewarm wait: the first closing step must keep exact physical geometry without a synchronous miss. */
final class VehicleCanopyColdCacheRuntimeChecks {
    private static int checks;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("cold canopy cache: "+why);checks++;}
    private static TileEntityCanopy shell(EntityGroundVehicle craft){for(TileEntity tile:craft.view.tiles.values())if(tile instanceof TileEntityCanopy){TileEntityCanopy canopy=(TileEntityCanopy)tile;if(canopy.draws()&&canopy.opening())return canopy;}throw new IllegalStateException("cold fixture lacks opening canopy");}
    private static EntityGroundVehicle craft(EntityGroundVehicle source,float progress){
        VehicleWorld view=new VehicleWorld(source.world,source.structure);List<VehicleStructure.Cell> cells=new ArrayList<>();
        for(VehicleStructure.Cell cell:source.structure.cells){TileEntity tile=view.getTileEntity(cell.pos);if(tile instanceof TileEntityCanopy){TileEntityCanopy canopy=(TileEntityCanopy)tile;if(canopy.draws()&&canopy.opening()){canopy.progress=canopy.previous=progress;canopy.targetOpen=true;}}
            cells.add(new VehicleStructure.Cell(cell.pos,cell.state,tile==null?cell.tileData():tile.writeToNBT(new NBTTagCompound())));}
        EntityGroundVehicle result=new EntityGroundVehicle(source.world);result.install(new VehicleStructure(cells,source.structure.seat,source.structure.facing,source.structure.gearCount));result.setPosition(1000,200,-1000);return result;
    }
    private static void exact(EntityGroundVehicle craft,String context){VehicleCollision fresh=new VehicleCollision(craft.view);require(craft.collision.boxes.equals(fresh.boxes),context+" exact ordered physical boxes");require(craft.collision.bounds.equals(fresh.bounds),context+" exact bounds");}
    private static void close(EntityGroundVehicle craft)throws Exception{
        TileEntityCanopy tile=shell(craft);tile.targetOpen=false;long misses=craft.collisionCache.misses();float initial=tile.progress;List<?> initialBoxes=craft.collision.boxes;
        VehicleCanopies.tick(craft);require(tile.progress==initial&&tile.previous==initial,"first cold frame holds current visual pose");require(craft.collision.boxes==initialBoxes,"first cold frame retains exact current collision object");require(craft.collisionCache.misses()==misses,"first frame does not synchronously collect a missing pose");
        long deadline=System.nanoTime()+12_000_000_000L;int steps=0;
        while(tile.progress>0&&System.nanoTime()<deadline){float before=tile.progress;Thread.sleep(2);VehicleCanopies.tick(craft);craft.collisionCache.tick(craft.view);if(tile.progress!=before){require(tile.progress==Math.max(0,before-1/16F),"cold motion advances exactly one unchanged speed step");exact(craft,"cold closing step="+(++steps));}}
        require(tile.progress==0,"cold closure finishes bounded, failure="+craft.collisionCache.failure());require(craft.collisionCache.misses()==misses,"all cold closing steps use prepared exact geometry");require(craft.collisionCache.waits()>0&&craft.collisionCache.fallbacks()==0,"cold closure genuinely waits asynchronously without fallback");
    }
    private static void failed(EntityGroundVehicle source,RuntimeException marker)throws Exception{
        EntityGroundVehicle craft=craft(source,1);try{
            TileEntityCanopy tile=shell(craft);tile.targetOpen=false;VehicleCanopies.tick(craft);
            Field field=VehicleCollisionCache.class.getDeclaredField("pending");field.setAccessible(true);Future<?> original=(Future<?>)field.get(craft.collisionCache);require(original!=null,"cold step schedules actual pure geometry work");original.cancel(false);
            FutureTask<Object> failure=new FutureTask<>(()->{throw marker;});failure.run();field.set(craft.collisionCache,failure);
            long misses=craft.collisionCache.misses();VehicleCanopies.tick(craft);require(tile.progress==15/16F,"failed or rejected preparation falls back without permanently holding animation");require(craft.collisionCache.misses()>misses&&craft.collisionCache.fallbacks()>0,"failure uses exact fresh fallback");exact(craft,"failure "+marker.getClass().getSimpleName());
        }finally{craft.setDead();}
    }
    static void run(EntityGroundVehicle source)throws Exception{
        checks=0;EntityGroundVehicle cold=craft(source,1),fractional=craft(source,.83F),packet=craft(source,1);
        try{
            require(cold.collisionCache.entries()==1,"install stores only current baseline before immediate closure");close(cold);close(fractional);
            TileEntityCanopy tile=shell(packet);BlockPos pos=tile.getPos();long misses=packet.collisionCache.misses();
            require(VehicleCanopies.apply(packet,ByteBuffer.allocate(13).putLong(pos.toLong()).put((byte)0).putFloat(0).array()),"cold authoritative endpoint packet accepted");require(tile.progress==1&&!tile.targetOpen,"cold correction retains old exact pose while updating target");
            long deadline=System.nanoTime()+6_000_000_000L;while(tile.progress>0&&System.nanoTime()<deadline){Thread.sleep(2);VehicleCanopies.tick(packet);packet.collisionCache.tick(packet.view);}
            require(tile.progress==0&&packet.collisionCache.misses()==misses,"authoritative cold endpoint also avoids synchronous subdivision");exact(packet,"authoritative endpoint");
            failed(source,new IllegalStateException("expected isolated worker failure"));failed(source,new RejectedExecutionException("expected rejected preparation"));
            System.out.println("[vandorlabs][reprolab] vehicle-canopy-cold-cache PASS checks="+checks+" immediate-close no-prewarm-wait noncanonical-progress exact-physical-pose 1/16-speed authoritative-endpoint failed-and-rejected-worker exact-fallback");
        }finally{cold.setDead();fractional.setDead();packet.setDead();}
    }
}
