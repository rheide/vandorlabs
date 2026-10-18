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
            world.setBlockState(ownerPos,controller.getDefaultState().withProperty(BlockVandorDirectional.FACING,front));TileEntityTurntableController tile=(TileEntityTurntableController)world.getTileEntity(ownerPos);tile.actor=player.getUniqueID();tile.height=2;tile.rotationMode=0;
            BlockPos chestPos=found.cell(1,1,1),stone=found.cell(0,0,1);world.setBlockState(chestPos,Blocks.CHEST.getDefaultState());((TileEntityChest)world.getTileEntity(chestPos)).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,23));world.setBlockState(stone,Blocks.BRICK_BLOCK.getDefaultState());
            EntityItem item=new EntityItem(world,chestPos.getX()+.5,chestPos.getY()+.5,chestPos.getZ()+.5,new ItemStack(Items.GOLD_INGOT,7));item.setInfinitePickupDelay();require(world.spawnEntity(item),"source entity");UUID itemId=item.getUniqueID();
            TurntableSnapshot probe=TurntableSnapshot.capture(world,found,2,player);EntityArmorStand stand=new EntityArmorStand(world,chestPos.getX()+.5,probe.origin.getY(),chestPos.getZ()+.5);stand.setCustomNameTag("Turntable boundary fixture");stand.setNoGravity(true);require(world.spawnEntity(stand),"source entity on lower scan boundary");UUID standId=stand.getUniqueID();
            tile.scan(player);EntityTurntable display=tile.display();require(display!=null&&world.isAirBlock(chestPos)&&item.isDead&&stand.isDead,"scan transfers source exactly once");require(display.snapshot.entities.size()==2,"entities frozen including exact lower boundary");
            tile.rotationMode=1;require(!tile.running(),"ON gate unpowered");tile.rotationMode=2;require(tile.running(),"OFF gate unpowered");world.setBlockState(ownerPos.down(),Blocks.REDSTONE_BLOCK.getDefaultState());require(!tile.running(),"OFF gate powered");tile.rotationMode=1;require(tile.running(),"ON gate powered");world.setBlockToAir(ownerPos.down());tile.rotationMode=0;
            TurntableSnapshot copied=TurntableSnapshot.decode(display.snapshot.encode());require(copied.blocks.cells.size()==2&&copied.entities.stream().anyMatch(e->e.id.equals(itemId)),"snapshot codec");
            try{VehicleStructure.read(copied.blocks.write());throw new AssertionError("Vehicle decoder accepted inert display");}catch(IllegalArgumentException expected){}
            display.onUpdate();require(display.angle()>0&&display.angle()<1,"clockwise rotation "+normal);double before=display.angle();tile.enabled=false;display.onUpdate();require(display.angle()==before,"disabled stops");tile.enabled=true;tile.clockwise=false;display.onUpdate();require(Math.abs(display.angle())<.001,"counterclockwise rotation");
            EntityTurntable loaded=new EntityTurntable(world);loaded.readFromNBT(display.writeToNBT(new NBTTagCompound()));require(loaded.snapshot.blocks.cells.size()==2&&loaded.controllerKey.equals(tile.key),"entity persistence");
            if(normal==EnumFacing.UP){DenyRestore deny=new DenyRestore();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(deny);try{tile.restore(player);throw new AssertionError("Protection cancellation ignored");}catch(java.io.IOException expected){}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);}require(world.isAirBlock(chestPos)&&!display.isDead&&tile.captureId.equals(display.getUniqueID()),"cancelled restore rolls back to captured state");require(!TurntableTransfer.pending(display),"successful rollback retires journal");}
            TurntableSurfaceLayout reversed=TurntableSurfaceLayout.read(found.reversed().write());require(reversed.normal==normal.getOpposite()&&reversed.size==3,"opposite capture side persists");
            world.setBlockState(chestPos,Blocks.STONE.getDefaultState());try{tile.restore(player);throw new AssertionError("Restore overwrote an obstacle");}catch(java.io.IOException expected){}require(!display.isDead&&tile.captureId!=null,"blocked restore retains snapshot");world.setBlockToAir(chestPos);
            tile.restore(player);require(display.isDead&&tile.captureId==null,"restore consumes display");TileEntityChest chest=(TileEntityChest)world.getTileEntity(chestPos);require(chest.getStackInSlot(0).getCount()==23&&chest.getStackInSlot(0).getItem()==Items.DIAMOND,"inventory restored");
            int count=0;for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(!entity.isDead&&entity.getUniqueID().equals(itemId)){require(((EntityItem)entity).getItem().getCount()==7,"entity stack restored");count++;entity.setDead();}require(count==1,"entity UUID restored once");
            count=0;for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(!entity.isDead&&entity.getUniqueID().equals(standId)){require(entity.getName().equals("Turntable boundary fixture"),"entity NBT restored");count++;entity.setDead();}require(count==1,"boundary entity restored once");
            if(normal==EnumFacing.EAST){
                ((TileEntityChest)world.getTileEntity(chestPos)).clear();world.setBlockToAir(chestPos);world.setBlockToAir(stone);BlockPos far=found.cell(1,1,16);world.getChunkFromBlockCoords(far);
                EntityArmorStand only=new EntityArmorStand(world,far.getX()+.5,far.getY()+.5,far.getZ()+.5);only.setNoGravity(true);require(world.spawnEntity(only),"entity-only far edge fixture");UUID onlyId=only.getUniqueID();tile.height=16;tile.scan(player);require(tile.display().snapshot.blocks.cells.isEmpty()&&tile.display().snapshot.entities.size()==1,"entity-only sixteen-block capture");tile.restore(player);
                count=0;for(net.minecraft.entity.Entity entity:world.loadedEntityList)if(!entity.isDead&&entity.getUniqueID().equals(onlyId)){count++;entity.setDead();}require(count==1,"entity-only far edge restored once");
            }
            TileEntityTurntableSurface stale=(TileEntityTurntableSurface)world.getTileEntity(found.root);tile.release();stale.bind(tile);require(stale.controller()==null,"a retired layout cannot retain a live surface claim");stale.release();clear(world,seed);
        }
        require(VehicleStructure.readDisplay(VehicleStructure.display(Collections.emptyList()).write()).cells.isEmpty(),"empty display round trip");
        System.out.println("[vandorlabs][reprolab] turntable PASS six normals square holes rotation stop persistence inventory/entity round trip blocked restore and vehicle isolation");
    }
    private static void clear(WorldServer world,BlockPos seed){for(BlockPos p:BlockPos.getAllInBox(seed.add(-5,-5,-5),seed.add(5,5,5)))if(!world.isAirBlock(p))world.setBlockToAir(p);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("turntable: "+message);}
    static void fixture(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockTurntableSurface surface=(BlockTurntableSurface)Block.getBlockFromName("vandorlabs:turntable_surface");Block controller=Block.getBlockFromName("vandorlabs:turntable_controller");
        for(int i=0;i<3;i++){
            EnumFacing normal=new EnumFacing[]{EnumFacing.UP,EnumFacing.NORTH,EnumFacing.DOWN}[i];TurntableGeometry.Face basis=TurntableGeometry.Face.valueOf(normal.name());BlockPos seed=new BlockPos(342+i*9,31,120);
            for(int u=0;u<3;u++)for(int v=0;v<3;v++){double[] p=basis.point(u,v,0);world.setBlockState(seed.add(p[0],p[1],p[2]),surface.getDefaultState().withProperty(BlockTurntableSurface.FACING,normal));}
            EnumFacing front=EnumFacing.getFacingFromVector(basis.ux,basis.uy,basis.uz);BlockPos owner=seed.offset(front.getOpposite());world.setBlockState(owner,controller.getDefaultState().withProperty(BlockVandorDirectional.FACING,front));TileEntityTurntableController tile=(TileEntityTurntableController)world.getTileEntity(owner);tile.height=2;tile.rotationMode=0;tile.rpm=2;TurntableSurfaceLayout area=TurntableSurfaceLayout.scan(world,seed);
            world.setBlockState(area.cell(1,1,1),Blocks.GOLD_BLOCK.getDefaultState());world.setBlockState(area.cell(0,1,1),Blocks.BRICK_BLOCK.getDefaultState());world.setBlockState(area.cell(1,1,2),Blocks.SEA_LANTERN.getDefaultState());tile.scan(player);
        }
    }
}
