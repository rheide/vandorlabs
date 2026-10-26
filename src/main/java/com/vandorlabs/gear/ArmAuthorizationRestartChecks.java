package com.vandorlabs.gear;

import com.vandorlabs.tiles.WorldActionAuthorization;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Two independent client processes share only a disposable saved world. */
final class ArmAuthorizationRestartChecks {
    private static final BlockPos BASE=new BlockPos(2399,100,0);
    private static final String[] TYPES={"gear_extender","copy_controller","turntable_controller","vehicle_controller","pilot_seat"};
    private static int stage,ticks;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("authorization-restart: "+why);}
    static boolean run(WorldServer world,EntityPlayerMP player,boolean verify)throws Exception {
        require(++ticks<400,"bounded reconnect automation");
        for(int i=0;i<TYPES.length;i++)world.getChunkFromBlockCoords(BASE.add(i*8,0,0));
        if(!verify){
            for(int i=0;i<TYPES.length;i++){BlockPos at=BASE.add(i*8,0,0);world.setBlockState(at,Block.getBlockFromName("vandorlabs:"+TYPES[i]).getDefaultState(),2);TileEntity tile=world.getTileEntity(at);WorldActionAuthorization.authorize(tile,player);}
            GearTileEntity arm=(GearTileEntity)world.getTileEntity(BASE);arm.setMountDirection(EnumFacing.UP);world.setBlockState(BASE.down(),Blocks.STONE.getDefaultState(),2);world.setBlockState(BASE.up(),GearRegistry.GEAR_EXTENDER.getDefaultState(),2);GearTileEntity second=(GearTileEntity)world.getTileEntity(BASE.up());second.setMountDirection(EnumFacing.UP);second.operator(player);require(arm.rebuild(),"stack formed");world.setBlockState(BASE.up(2),Blocks.STONE.getDefaultState(),2);
            BlockPos copier=BASE.east(8);com.vandorlabs.copier.CopyControllerTile copy=(com.vandorlabs.copier.CopyControllerTile)world.getTileEntity(copier);copy.actor=player.getUniqueID();for(EnumFacing side:new EnumFacing[]{copy.front().rotateY(),copy.front().rotateYCCW()})world.setBlockState(copier.offset(side),Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState().withProperty(com.vandorlabs.blocks.BlockTurntableSurface.FACING,EnumFacing.UP),2);copy.refreshPads(player);copy.markDirty();
            player.connection.setPlayerLocation(BASE.getX()+4,102,3,90,0);
            System.out.println("[vandorlabs] arm-durable-recovery PASS authorization-save five types and copy pads ready for normal disconnect");return true;
        }
        for(int i=0;i<TYPES.length;i++){
            TileEntity tile=world.getTileEntity(BASE.add(i*8,0,0));if(WorldActionAuthorization.awaitingLoad(tile))return false;
            require(tile!=null&&player.getUniqueID().equals(WorldActionAuthorization.actorId(tile)),"receipt survives disconnect and new process: "+TYPES[i]);require(WorldActionAuthorization.actor(tile)==player,"online actor rebinds: "+TYPES[i]);
        }
        com.vandorlabs.copier.CopyControllerTile copy=(com.vandorlabs.copier.CopyControllerTile)world.getTileEntity(BASE.east(8));if(copy.input==null||copy.output==null||!copy.error.isEmpty())return false;
        GearTileEntity arm=(GearTileEntity)world.getTileEntity(BASE);
        if(stage==0){
            BlockPos elsewhere=BASE.north(8);NBTTagCompound cloned=arm.writeToNBT(new NBTTagCompound());cloned.setInteger("x",elsewhere.getX());cloned.setInteger("y",elsewhere.getY());cloned.setInteger("z",elsewhere.getZ());world.setBlockState(elsewhere,GearRegistry.GEAR_EXTENDER.getDefaultState(),2);world.setTileEntity(elsewhere,TileEntity.create(world,cloned));require(WorldActionAuthorization.actorId(world.getTileEntity(elsewhere))==null,"portable copied NBT cannot acquire receipt");world.setBlockToAir(elsewhere);
            arm.target(false);stage=1;return false;
        }
        if(arm.progress()!=0||ArmTransactions.locked(world,BASE))return false;
        require(arm.payload()!=null&&world.isAirBlock(BASE.up(2)),"arm acts after reconnect without reconfiguration");
        System.out.println("[vandorlabs] arm-durable-recovery PASS authorization-verify five controller receipts live arm retract copy pads refreshed copied NBT denied");return true;
    }
    private ArmAuthorizationRestartChecks(){}
}
