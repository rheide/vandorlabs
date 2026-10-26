package com.vandorlabs.gear;

import com.vandorlabs.tiles.WorldActionAuthorization;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.ChunkDataEvent;

/** Regression contracts for unload/save ordering and physical payload/socket contact. */
public final class ArmFollowupRuntimeChecks {
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("arm-followup: "+why);}
    public static void run(WorldServer world,EntityPlayerMP player)throws Exception {
        BlockPos root=new BlockPos(1008,170,288);world.getChunkFromBlockCoords(root);
        for(String id:new String[]{"gear_extender","copy_controller","turntable_controller","vehicle_controller","pilot_seat"}){
            Block block=Block.getBlockFromName("vandorlabs:"+id);require(block!=null,"registered "+id);world.setBlockState(root,block.getDefaultState(),2);TileEntity tile=world.getTileEntity(root);require(tile!=null,"tile "+id);WorldActionAuthorization.authorize(tile,player);
            // ChunkProviderServer calls onUnload before saveChunkData. Do not reverse this order.
            tile.onChunkUnload();NBTTagCompound chunkData=new NBTTagCompound();WorldActionAuthorization.saving(new ChunkDataEvent.Save(world.getChunkFromBlockCoords(root),chunkData));
            require(chunkData.getTagList("VandorActionProofs",10).tagCount()>0,"unload must retain proof for subsequent chunk save: "+id);
            WorldActionAuthorization.unload(new net.minecraftforge.event.world.WorldEvent.Unload(world));WorldActionAuthorization.loaded(new ChunkDataEvent.Load(world.getChunkFromBlockCoords(root),chunkData));com.vandorlabs.tiles.DeferredTileLoad.flush(world);
            require(player.getUniqueID().equals(WorldActionAuthorization.actorId(tile)),"saved proof rebinds after reconnect: "+id);
            tile.onChunkUnload();require(WorldActionAuthorization.actorId(tile)==null,"unloaded proof is inactive: "+id);tile.onLoad();com.vandorlabs.tiles.DeferredTileLoad.flush(world);require(player.getUniqueID().equals(WorldActionAuthorization.actorId(tile)),"dormant chunk reuse restores its identity proof: "+id);
            world.setBlockToAir(root);
        }
        System.out.println("[vandorlabs] arm-authorization-order PASS five controller types unload-before-save restored proofs");
        String second=System.getProperty("vandorlabs.armSecondFixture","");
        if(!second.isEmpty())diagnoseSecond(world,player,second);
        try(AutoCloseable matrix=ArmTransactions.matrix()){
            for(GearBlock block:new GearBlock[]{GearRegistry.GEAR_SWING_ARM,GearRegistry.LARGE_GEAR_SWING_ARM})for(EnumFacing face:EnumFacing.values())for(int direction=0;direction<4;direction++){
                for(BlockPos at:BlockPos.getAllInBox(root.add(-5,-5,-5),root.add(5,5,5)))world.setBlockToAir(at);
                for(int n=0;n<3;n++){BlockPos at=root.offset(face,n);world.setBlockState(at,block.getDefaultState(),2);GearTileEntity arm=(GearTileEntity)world.getTileEntity(at);arm.setMountDirection(face);arm.operator(player);}
                GearTileEntity arm=(GearTileEntity)world.getTileEntity(root);require(arm.rebuild(),"three-section arm forms");NBTTagCompound settings=arm.settings();settings.setBoolean("RotatePayload",false);settings.setInteger("Direction",direction);arm.configure(settings);world.setBlockState(root.offset(face,3),Blocks.STONE.getDefaultState(),2);require(arm.preparePayloadMotion(),"capture level payload");
                for(int n=0;n<=24;n++){
                    double progress=n/24D;
                    // The authored swivel underside is the payload's mounting face at rest.
                    com.vandorlabs.gear.core.GearMotion.Vec moved=arm.pose(progress).transformFootVertex(.5,-2,.5);
                    Vec3d socket=ArmOrientation.point(face,moved.x,moved.y,moved.z);
                    Vec3d local=ArmOrientation.point(face,.5,-2,.5).subtract(new Vec3d(arm.payload().offset));
                    Vec3d payloadFace=arm.payload().transform(arm,progress,local.x,local.y,local.z);
                    require(socket.squareDistanceTo(payloadFace)<1e-12,"level payload stays connected to swivel throughout swing: "+block.getRegistryName()+" "+face+" direction="+direction+" progress="+progress+" gap="+Math.sqrt(socket.squareDistanceTo(payloadFace)));
                }
                arm.captureProgress(0);Vec3d center=arm.payload().transform(arm,0,.5,.5,.5).add(new Vec3d(root));EnumFacing side=face.getAxis()==EnumFacing.Axis.X?EnumFacing.SOUTH:EnumFacing.EAST;
                RayTraceResult picked=GearInteractions.trace(world,center.add(new Vec3d(side.getDirectionVec()).scale(3)),center);
                require(picked!=null&&picked.hitInfo!=null,"captured payload has its own mining identity instead of the lower arm");
            }
        }finally{for(BlockPos at:BlockPos.getAllInBox(root.add(-5,-5,-5),root.add(5,5,5)))world.setBlockToAir(at);}
        System.out.println("[vandorlabs] arm-level-contact PASS regular/Large six axes four swing directions 25 motion samples");
    }
    private static void diagnoseSecond(WorldServer world,EntityPlayerMP player,String path)throws Exception {
        NBTTagCompound data;try(java.io.InputStream in=java.nio.file.Files.newInputStream(java.nio.file.Paths.get(path))){data=net.minecraft.nbt.CompressedStreamTools.readCompressed(in);}
        net.minecraft.nbt.NBTTagList cells=data.getTagList("Cells",10);
        for(int n=0;n<cells.tagCount();n++){NBTTagCompound cell=cells.getCompoundTagAt(n);BlockPos p=new BlockPos(cell.getInteger("X"),cell.getInteger("Y"),cell.getInteger("Z"));Block b=Block.getBlockFromName(cell.getString("Name"));world.setBlockState(p,b.getStateFromMeta(cell.getInteger("Meta")),2);if(cell.hasKey("Tile",10))world.setTileEntity(p,TileEntity.create(world,cell.getCompoundTag("Tile")));}
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(new BlockPos(-79,86,166));arm.operator(player);
        double before=arm.progress(),next=com.vandorlabs.gear.core.GearMotion.advance(before,false,1,GearTileEntity.ANIMATION_TICKS);
        java.util.List<AxisAlignedBB> a=GearGeometry.boxes(arm,before),b=GearGeometry.boxes(arm,next);
        System.out.println("[vandorlabs] arm-second-state progress="+before+" next="+next+" sizes="+a.size()+"/"+b.size());
        for(int i=0;i<b.size();i++){if(a.get(i).equals(b.get(i)))continue;AxisAlignedBB swept=GearGeometry.swept(arm,i,a.get(i),b.get(i),before,next);java.util.List<AxisAlignedBB> obstacles=GearGeometry.foreign(world,arm,swept);if(!obstacles.isEmpty())System.out.println("[vandorlabs] arm-second-obstruction piece="+i+" sweep="+swept+" obstacles="+obstacles);}
    }
    private ArmFollowupRuntimeChecks(){}
}
