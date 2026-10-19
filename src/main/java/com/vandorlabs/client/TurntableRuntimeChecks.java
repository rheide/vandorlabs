package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.*;
import com.vandorlabs.vehicle.VehicleStructure;
import net.minecraft.block.Block;
import net.minecraft.entity.item.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Exercise actual raw transfers, inventories, frozen entities and every active-face orientation. */
final class TurntableRuntimeChecks {
    public static final class DenyRestore {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void place(net.minecraftforge.event.world.BlockEvent.MultiPlaceEvent event){event.setCanceled(true);}
    }
    static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockTurntableSurface surface=(BlockTurntableSurface)Block.getBlockFromName("vandorlabs:turntable_surface");Block controller=Block.getBlockFromName("vandorlabs:turntable_controller");
        for(EnumFacing normal:EnumFacing.values()){
            BlockPos seed=new BlockPos(342+normal.getIndex()*20,205,175);TurntableGeometry.Face basis=TurntableGeometry.Face.valueOf(normal.name());EnumFacing front=EnumFacing.getFacingFromVector(basis.ux,basis.uy,basis.uz);BlockPos ownerPos=seed.offset(front.getOpposite());
            clear(world,seed);
            for(int u=0;u<3;u++)for(int v=0;v<3;v++){double[] offset=basis.point(u,v,0);world.setBlockState(seed.add(offset[0],offset[1],offset[2]),surface.getDefaultState().withProperty(BlockTurntableSurface.FACING,normal));}
            TurntableSurfaceLayout found=TurntableSurfaceLayout.scan(world,seed);require(found.size==3&&found.normal==normal,"plane detection "+normal);
            BlockPos hole=found.cell(1,1,0);world.setBlockToAir(hole);try{TurntableSurfaceLayout.scan(world,seed);throw new AssertionError("Accepted square hole");}catch(IllegalArgumentException expected){}world.setBlockState(hole,surface.getDefaultState().withProperty(BlockTurntableSurface.FACING,normal));
            world.setBlockState(ownerPos,controller.getDefaultState().withProperty(BlockVandorDirectional.FACING,front));TileEntityTurntableController tile=(TileEntityTurntableController)world.getTileEntity(ownerPos);tile.actor=player.getUniqueID();tile.height=2;tile.rotationMode=2;
            BlockPos chestPos=found.cell(1,1,1),stone=found.cell(0,1,1);world.setBlockState(chestPos,Blocks.CHEST.getDefaultState());((TileEntityChest)world.getTileEntity(chestPos)).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,23));world.setBlockState(stone,Blocks.BRICK_BLOCK.getDefaultState());
            EntityItem item=new EntityItem(world,chestPos.getX()+.5,chestPos.getY()+.5,chestPos.getZ()+.5,new ItemStack(Items.GOLD_INGOT,7));item.setInfinitePickupDelay();require(world.spawnEntity(item),"source entity");UUID itemId=item.getUniqueID();
            TurntableSnapshot probe=TurntableSnapshot.capture(world,found,2,player);EntityArmorStand stand=new EntityArmorStand(world,chestPos.getX()+.5,normal.getAxis()==EnumFacing.Axis.Y?probe.origin.getY():chestPos.getY()+.5,chestPos.getZ()+.5);stand.setCustomNameTag("Turntable boundary fixture");stand.setNoGravity(true);require(world.spawnEntity(stand),"source entity on lower scan boundary");UUID standId=stand.getUniqueID();
            tile.scan(player);EntityTurntable display=tile.display();require(display!=null&&world.isAirBlock(chestPos)&&item.isDead&&stand.isDead,"scan transfers source exactly once");require(display.snapshot.entities.size()==2,"entities frozen including exact lower boundary");
            require(!surface.removedByPlayer(world.getBlockState(hole),world,hole,player,false)&&BlockTurntableSurface.locked(world,hole),"captured surface resists creative mining "+normal);
            require(!display.preventEntitySpawning&&world.checkNoEntityCollision(new AxisAlignedBB(found.root.offset(normal.getOpposite()))),"empty space behind surface is not reserved by display bounds");
            tile.rotationMode=1;require(!tile.running(),"ON gate unpowered");tile.rotationMode=2;require(tile.running(),"OFF gate unpowered");world.setBlockState(ownerPos.down(),Blocks.REDSTONE_BLOCK.getDefaultState());require(!tile.running(),"OFF gate powered");tile.rotationMode=1;require(tile.running(),"ON gate powered");world.setBlockToAir(ownerPos.down());tile.rotationMode=2;
            TurntableSnapshot copied=TurntableSnapshot.decode(display.snapshot.encode());require(copied.blocks.cells.size()==2&&copied.entities.stream().anyMatch(e->e.id.equals(itemId)),"snapshot codec");
            try{VehicleStructure.read(copied.blocks.write());throw new AssertionError("Vehicle decoder accepted inert display");}catch(IllegalArgumentException expected){}
            require(!display.canBeCollidedWith(),"display broad bounds never intercept block selection");
            display.onUpdate();require(display.angle()>0&&display.angle()<1,"clockwise rotation "+normal);tile.clockwise=false;display.onUpdate();require(Math.abs(display.angle())<.001,"counterclockwise rotation");
            if(normal==EnumFacing.UP){
                boolean flying=player.capabilities.isFlying;double px=player.posX,py=player.posY,pz=player.posZ;
                EntityItem rider=new EntityItem(world,found.pivot.x+.7,seed.getY()+1,found.pivot.z+.3,new ItemStack(Items.IRON_INGOT));rider.setInfinitePickupDelay();world.spawnEntity(rider);
                try{player.capabilities.isFlying=false;player.setPositionAndUpdate(found.pivot.x-.7,seed.getY()+1,found.pivot.z-.3);double oldZ=player.posZ,oldItemZ=rider.posZ;display.onUpdate();
                    require(Math.abs(player.posZ-oldZ)>.0001&&Math.abs(rider.posZ-oldItemZ)>.0001,"player and dropped item ride the bare surface after scanning");
                }finally{world.removeEntityDangerously(rider);player.capabilities.isFlying=flying;player.setPositionAndUpdate(px,py,pz);display.setAngle(0);}
            }
            EntityTurntable loaded=new EntityTurntable(world);loaded.readFromNBT(display.writeToNBT(new NBTTagCompound()));require(loaded.snapshot.blocks.cells.size()==2&&loaded.controllerKey.equals(tile.key),"entity persistence");
            if(normal==EnumFacing.UP){display.setAngle(74);DenyRestore deny=new DenyRestore();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(deny);try{tile.restore(player);throw new AssertionError("Protection cancellation ignored");}catch(java.io.IOException expected){}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);}require(world.isAirBlock(chestPos)&&!display.isDead&&tile.captureId.equals(display.getUniqueID()),"cancelled restore rolls back to captured state");require(!TurntableTransfer.pending(display),"successful rollback retires journal");
                TurntableSnapshot returned=display.snapshot.rotated(display.angle(),display.view);for(VehicleStructure.Cell c:returned.blocks.cells)require(world.isAirBlock(returned.origin.add(c.pos)),"cancelled restore clears the rotated return footprint");
            }
            TurntableSurfaceLayout reversed=TurntableSurfaceLayout.read(found.reversed().write());require(reversed.normal==normal.getOpposite()&&reversed.size==3,"opposite capture side persists");
            world.setBlockState(chestPos,Blocks.STONE.getDefaultState());try{tile.restore(player);throw new AssertionError("Restore overwrote an obstacle");}catch(java.io.IOException expected){}require(!display.isDead&&tile.captureId!=null,"blocked restore retains snapshot");world.setBlockToAir(chestPos);
            TurntableSnapshot expectedReturn=display.snapshot.rotated(display.angle(),display.view);tile.restore(player);require(display.isDead&&tile.captureId==null,"restore consumes display");
            for(VehicleStructure.Cell c:expectedReturn.blocks.cells)require(world.getBlockState(expectedReturn.origin.add(c.pos)).equals(c.state),"restored blocks preserve nearest displayed quarter turn");TileEntityChest chest=(TileEntityChest)world.getTileEntity(chestPos);require(chest.getStackInSlot(0).getCount()==23&&chest.getStackInSlot(0).getItem()==Items.DIAMOND,"inventory restored");
            int count=0;for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(!entity.isDead&&entity.getUniqueID().equals(itemId)){require(((EntityItem)entity).getItem().getCount()==7,"entity stack restored");count++;entity.setDead();}require(count==1,"entity UUID restored once");
            count=0;for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(!entity.isDead&&entity.getUniqueID().equals(standId)){require(entity.getName().equals("Turntable boundary fixture"),"entity NBT restored");count++;entity.setDead();}require(count==1,"boundary entity restored once");
            if(normal==EnumFacing.EAST){
                ((TileEntityChest)world.getTileEntity(chestPos)).clear();world.setBlockToAir(chestPos);world.setBlockToAir(stone);BlockPos far=found.cell(1,1,16);world.getChunkFromBlockCoords(far);
                EntityArmorStand only=new EntityArmorStand(world,far.getX()+.5,far.getY()+.5,far.getZ()+.5);only.setNoGravity(true);require(world.spawnEntity(only),"entity-only far edge fixture");UUID onlyId=only.getUniqueID();tile.height=16;tile.scan(player);require(tile.display().snapshot.blocks.cells.isEmpty()&&tile.display().snapshot.entities.size()==1,"entity-only sixteen-block capture");tile.restore(player);
                count=0;for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(!entity.isDead&&entity.getUniqueID().equals(onlyId)){count++;entity.setDead();}require(count==1,"entity-only far edge restored once");
            }
            for(BlockPos removed:new BlockPos[]{found.root,found.cell(1,1,0)}){
                tile.height=2;tile.scan(player);EntityTurntable retained=tile.display();
                world.setBlockToAir(removed);require(!tile.enabled&&tile.layout!=null&&tile.display()==retained,"damaged platform keeps its recovery snapshot");
                for(int tick=0;tick<40;tick++)tile.update();
                for(BlockPos cell:found.cells)if(!cell.equals(removed)){
                    TileEntityTurntableSurface remaining=(TileEntityTurntableSurface)world.getTileEntity(cell);
                    require(remaining.locked()&&remaining.controller()==null&&!surface.getActualState(world.getBlockState(cell),world,cell).getValue(BlockTurntableSurface.FORMED),"forced root/member removal retains protection and opaque standalone surface models "+normal);
                }
                retained.setAngle(0);tile.restore(player);require(tile.layout==null&&tile.captureId==null,"damaged platform still restores captured contents");
                world.setBlockState(removed,surface.getDefaultState().withProperty(BlockTurntableSurface.FACING,normal));
            }
            TileEntityTurntableSurface stale=(TileEntityTurntableSurface)world.getTileEntity(found.root);tile.release();stale.bind(tile);require(stale.controller()==null,"a retired layout cannot retain a live surface claim");stale.release();clear(world,seed);
        }
        // Redstone retries surface discovery even before a successful manual scan.
        BlockPos bad=new BlockPos(520,205,170),badOwner=bad.west();
        try{
            for(int x=0;x<2;x++)for(int z=0;z<3;z++)world.setBlockState(bad.add(x,0,z),surface.getDefaultState());
            world.setBlockState(badOwner,controller.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.EAST));
            TileEntityTurntableController invalid=(TileEntityTurntableController)world.getTileEntity(badOwner);invalid.actor=player.getUniqueID();
            world.setBlockState(badOwner.down(),Blocks.REDSTONE_BLOCK.getDefaultState());invalid.update();
            require(invalid.captureId==null&&invalid.lastError.contains("filled square"),"invalid automatic scan reports the actual shape error");
            for(int tick=0;tick<20;tick++)invalid.update();TileEntityTurntableController saved=new TileEntityTurntableController();saved.readFromNBT(invalid.writeToNBT(new NBTTagCompound()));
            require(saved.lastError.equals(invalid.lastError),"scan error remains visible and synchronized");
            world.setBlockToAir(badOwner.down());invalid.update();for(int z=0;z<3;z++)world.setBlockState(bad.add(2,0,z),surface.getDefaultState());
            world.setBlockState(bad.up(),Blocks.BRICK_BLOCK.getDefaultState());world.setBlockState(badOwner.down(),Blocks.REDSTONE_BLOCK.getDefaultState());invalid.update();
            require(invalid.display()!=null&&invalid.lastError.isEmpty()&&invalid.display().snapshot.blocks.cells.size()==1,"next rising edge rescans the repaired surface");
            world.setBlockToAir(badOwner.down());invalid.update();invalid.turnTo(90);
            world.setBlockState(bad.add(1,1,1),Blocks.GOLD_BLOCK.getDefaultState());world.setBlockState(badOwner.down(),Blocks.REDSTONE_BLOCK.getDefaultState());invalid.update();
            require(invalid.display()!=null&&invalid.display().snapshot.blocks.cells.size()==2&&Math.abs(invalid.display().angle()-90)<.001,"rising edge captures newly parked blocks without resetting the existing heading");
            invalid.restore(player);require(world.getBlockState(bad.add(1,1,1)).getBlock()==Blocks.GOLD_BLOCK,"rescanned blocks restore at their displayed heading");invalid.release();
        }finally{world.setBlockToAir(badOwner.down());world.setBlockToAir(badOwner);for(BlockPos p:BlockPos.getAllInBox(bad,bad.add(2,2,2)))world.setBlockToAir(p);}
        BlockPos fullSeed=new BlockPos(490,205,170),fullOwner=fullSeed.west();
        for(int x=-16;x<=16;x+=16)for(int z=-16;z<=16;z+=16)world.getChunkFromBlockCoords(fullSeed.add(x,0,z));
        for(int x=0;x<5;x++)for(int z=0;z<5;z++){BlockPos at=fullSeed.add(x,0,z);world.setBlockState(at,surface.getDefaultState());world.setBlockState(at.up(),Blocks.BRICK_BLOCK.getDefaultState());}
        world.setBlockState(fullOwner,controller.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.EAST));TileEntityTurntableController full=(TileEntityTurntableController)world.getTileEntity(fullOwner);
        full.rpm=3;require(full.rotationMode==1&&!full.running(),"new turntable waits for redstone ON");full.rotationMode=2;full.scan(player);EntityTurntable moving=full.display();
        require(moving.snapshot.blocks.cells.size()==25,"full square build captured above the fixed surface corners");

        for(int tick=0;tick<100;tick++)moving.onUpdate();require(Math.abs(moving.angle()-90)<.001,"full five-by-five captured layer rotates through ninety degrees");
        full.rotationMode=1;double stopped=moving.angle();for(int tick=0;tick<40;tick++){moving.onUpdate();TurntableEvents.finishTransfers(world);}
        require(!moving.isDead&&full.captureId!=null&&moving.angle()==stopped,"redstone OFF retains paused display and heading");
        full.turnTo(37);require(Math.abs(moving.angle()-37)<.001,"paused angle selection");
        full.turnTo(90);full.restore(player);require(moving.isDead&&full.captureId==null,"explicit Restore converts current selected heading");
        require(full.layout!=null,"normal surface layout retained for next activation");
        full.update();BlockPos power=fullOwner.west();BlockIndustrialLever lever=(BlockIndustrialLever)Block.getBlockFromName("vandorlabs:small_power_lever");
        world.setBlockState(power.down(),Blocks.STONE.getDefaultState());world.setBlockState(power,lever.getDefaultState().withProperty(BlockIndustrialLever.FLOOR,true));DeferredTileLoad.flush(world);
        lever.onBlockActivated(world,power,world.getBlockState(power),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);full.update();
        moving=full.display();require(moving!=null&&moving.snapshot.blocks.cells.size()==25,"new redstone activation recaptures the whole prior build");
        for(int tick=0;tick<200;tick++){full.update();moving.onUpdate();TurntableEvents.finishTransfers(world);require(!moving.isDead&&full.running(),"continuous redstone activation never stops after its initial tick");}
        moving.onUpdate();lever.onBlockActivated(world,power,world.getBlockState(power),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);moving.onUpdate();TurntableEvents.finishTransfers(world);require(!moving.isDead&&full.captureId!=null,"lever OFF pauses captured contents");full.restore(player);world.setBlockToAir(power);world.setBlockToAir(power.down());
        // Reproduce a saved pre-fix floor capture of terrain behind the active face.
        TurntableSurfaceLayout legacy=TurntableSurfaceLayout.scan(world,fullSeed).reversed();for(BlockPos cell:legacy.cells)for(int depth=1;depth<=4;depth++)world.setBlockState(cell.down(depth),Blocks.CONCRETE.getDefaultState());
        full.layout=legacy;full.reverse=true;for(BlockPos cell:legacy.cells)((TileEntityTurntableSurface)world.getTileEntity(cell)).bind(full);
        TurntableTransfer.capture(world,full,TurntableSnapshot.capture(world,legacy,4,player),player);require(full.display().snapshot.blocks.cells.size()==100&&!full.running(),"legacy terrain capture retained and prevented from rotating");
        full.display().onUpdate();require(full.display().angle()==0&&full.message.contains("Restore"),"legacy recovery instruction");full.restore(player);require(full.layout==null&&!full.reverse,"restoring legacy contents releases reversed surface claims");
        for(BlockPos cell:legacy.cells)for(int depth=1;depth<=4;depth++){require(world.getBlockState(cell.down(depth)).getBlock()==Blocks.CONCRETE,"legacy terrain restored exactly");world.setBlockToAir(cell.down(depth));}
        world.setBlockToAir(fullOwner);for(int x=0;x<5;x++)for(int z=0;z<5;z++){world.setBlockToAir(fullSeed.add(x,1,z));world.setBlockToAir(fullSeed.add(x,0,z));}
        require(VehicleStructure.readDisplay(VehicleStructure.display(Collections.emptyList()).write()).cells.isEmpty(),"empty display round trip");
        AxisAlignedBB base=new AxisAlignedBB(0,0,0,1,.5,1);Vec3d pivot=new Vec3d(.5,0,.5);
        require(!TurntableCollision.intersects(base,pivot,TurntableGeometry.Face.UP,45,new AxisAlignedBB(1.08,.1,1.08,1.15,.4,1.15)),"exact terrain collision excludes a rotated box's empty corner");
        require(TurntableCollision.intersects(base,pivot,TurntableGeometry.Face.UP,45,new AxisAlignedBB(.45,.1,.45,.55,.4,.55)),"exact terrain collision retains actual obstructions");
        require(TurntableCollision.boxes(base,pivot,TurntableGeometry.Face.UP,45,new AxisAlignedBB(1.08,.1,1.08,1.15,.4,1.15)).isEmpty(),"rotated stair lower step has no phantom corner wall");
        List<AxisAlignedBB> narrow=TurntableCollision.boxes(base,pivot,TurntableGeometry.Face.UP,45,new AxisAlignedBB(-1,-1,-1,2,2,2));
        require(narrow.size()>16,"rotated stair footprint uses narrow strips");for(AxisAlignedBB strip:narrow)require(strip.minY==0&&strip.maxY==.5,"lower stair step height preserved");
        AxisAlignedBB upper=new AxisAlignedBB(0,.5,.5,1,1,1);for(AxisAlignedBB strip:TurntableCollision.boxes(upper,pivot,TurntableGeometry.Face.UP,45,new AxisAlignedBB(-1,-1,-1,2,2,2)))require(strip.minY==.5&&strip.maxY==1,"upper stair step height preserved");
        TurntableVehicleRuntimeChecks.run(world,player);
        TurntableFollowupRuntimeChecks.run(world,player);
        System.out.println("[vandorlabs][reprolab] turntable PASS six normals square holes rotation stop persistence inventory/entity round trip blocked restore and vehicle isolation");
    }
    private static void clear(WorldServer world,BlockPos seed){for(BlockPos p:BlockPos.getAllInBox(seed.add(-5,-5,-5),seed.add(5,5,5)))if(!world.isAirBlock(p))world.setBlockToAir(p);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("turntable: "+message);}
    static void fixture(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockTurntableSurface surface=(BlockTurntableSurface)Block.getBlockFromName("vandorlabs:turntable_surface");Block controller=Block.getBlockFromName("vandorlabs:turntable_controller");
        for(int i=0;i<4;i++){
            int size=i==3?16:3;EnumFacing normal=new EnumFacing[]{EnumFacing.UP,EnumFacing.NORTH,EnumFacing.DOWN,EnumFacing.UP}[i];TurntableGeometry.Face basis=TurntableGeometry.Face.valueOf(normal.name());BlockPos seed=new BlockPos(i==3?780:342+i*9,31,120);
            for(int u=0;u<size;u++)for(int v=0;v<size;v++){double[] p=basis.point(u,v,0);world.setBlockState(seed.add(p[0],p[1],p[2]),surface.getDefaultState().withProperty(BlockTurntableSurface.FACING,normal));}
            EnumFacing front=EnumFacing.getFacingFromVector(basis.ux,basis.uy,basis.uz);BlockPos owner=seed.offset(front.getOpposite());world.setBlockState(owner,controller.getDefaultState().withProperty(BlockVandorDirectional.FACING,front));TileEntityTurntableController tile=(TileEntityTurntableController)world.getTileEntity(owner);tile.height=2;tile.rotationMode=2;tile.rpm=2;TurntableSurfaceLayout area=TurntableSurfaceLayout.scan(world,seed);
            world.setBlockState(area.cell(1,1,1),Blocks.GOLD_BLOCK.getDefaultState());world.setBlockState(area.cell(0,1,1),Blocks.BRICK_BLOCK.getDefaultState());world.setBlockState(area.cell(1,1,2),Blocks.SEA_LANTERN.getDefaultState());tile.scan(player);
        }
    }
}
