package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import com.vandorlabs.turntable.*;
import com.vandorlabs.vehicle.*;
import net.minecraft.command.CommandFill;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import java.util.*;

/** Real /fill removal, durable blocked returns and Configurizer interaction events. */
final class MovingDeviceRuntimeChecks {
    static void run(WorldServer world,EntityPlayerMP player)throws Exception{
        BlockPos root=new BlockPos(640,180,240);Vec3d old=player.getPositionVector();float yaw=player.rotationYaw,pitch=player.rotationPitch;ItemStack held=player.getHeldItemMainhand();boolean drops=world.getGameRules().getBoolean("doTileDrops");
        world.getGameRules().setOrCreateGameRule("doTileDrops","false");
        try{
            for(int x=-32;x<=32;x+=16)for(int z=-32;z<=32;z+=16)world.getChunkFromBlockCoords(root.add(x,0,z));
            player.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(ModItems.CONFIGURIZER));
            for(boolean blocked:new boolean[]{false,true}){
                BlockPos owner=root.west();for(int x=0;x<12;x++)for(int z=0;z<12;z++)world.setBlockState(root.add(x,0,z),net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_surface").getDefaultState());
                world.setBlockState(owner,net.minecraft.block.Block.getBlockFromName("vandorlabs:turntable_controller").getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.EAST));
                TileEntityTurntableController table=(TileEntityTurntableController)world.getTileEntity(owner);table.height=1;
                BlockPos cargo=root.add(11,1,11);world.setBlockState(cargo,Blocks.CHEST.getDefaultState());((TileEntityChest)world.getTileEntity(cargo)).setInventorySlotContents(0,new ItemStack(Items.DIAMOND,3));
                TurntableTransactionsChecks.scan(table,player);EntityTurntable display=table.display();display.setAngle(90);
                TurntableSnapshot returned=display.snapshot.rotated(90,display.view);BlockPos destination=returned.origin.add(returned.blocks.cells.get(0).pos);
                double[] rotated=TurntableGeometry.rotate(cargo.getX()+.5-display.posX,cargo.getY()+.5-display.posY,cargo.getZ()+.5-display.posZ,display.snapshot.layout.basis,90);
                Vec3d aim=new Vec3d(rotated[0]+display.posX,rotated[1]+display.posY,rotated[2]+display.posZ);player.setPositionAndRotation(aim.x,aim.y-player.getEyeHeight(),aim.z+4,180,0);
                require(MovingDeviceConfiguration.pointedTurntable(player)==table,"rotated captured block targets its controller");
                require(player.getDistanceSq(owner)>64&&MovingDeviceConfiguration.openTurntable(player,table),"far-side turntable Configurizer reach");
                require(player.openContainer instanceof com.vandorlabs.container.ContainerTurntable&&player.openContainer.canInteractWith(player),"turntable dialog remains usable through captured content");player.closeScreen();
                require(ProgrammableTarget.settingsPos(world,root).equals(root),"claimed Surface keeps its own texture target");
                player.connection.setPlayerLocation(root.getX()+.5,root.getY()+1,root.getZ()+.5,0,90);
                net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock surfaceClick=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,root,EnumFacing.UP,new Vec3d(root).addVector(.5,1,.5));
                ItemConfigurizer.onRightClickBlock(surfaceClick);require(surfaceClick.isCanceled()&&player.openContainer instanceof com.vandorlabs.container.ContainerSurface&&player.openContainer.canInteractWith(player),"actual Configurizer event opens claimed Surface texture dialog instead of Turntable motion");player.closeScreen();
                if(blocked)world.setBlockState(destination,Blocks.STONE.getDefaultState());
                fill(world,player,owner,"minecraft:stone");TurntableEvents.finishTransfers(world);
                require(display.isDead&&world.getBlockState(owner).getBlock()==Blocks.STONE,"fill removes orphan display and preserves replacement block");
                if(blocked){require(world.getBlockState(destination).getBlock()==Blocks.STONE,"blocked orphan return never overwrites terrain");world.setBlockToAir(destination);TurntableTransfer.recover(world);}
                require(world.getTileEntity(destination) instanceof TileEntityChest&&((TileEntityChest)world.getTileEntity(destination)).getStackInSlot(0).getCount()==3,"orphan return preserves rotated chest and inventory");
                for(int x=0;x<12;x++)for(int z=0;z<12;z++){TileEntity tile=world.getTileEntity(root.add(x,0,z));require(!(tile instanceof TileEntityTurntableSurface)||!((TileEntityTurntableSurface)tile).locked(),"surfaces unlock after command deletion");world.setBlockToAir(root.add(x,0,z));}
                world.setBlockToAir(destination);world.setBlockToAir(owner);
            }
            ramp(world,player,root);
            vehicle(world,player,root);
        }finally{player.closeScreen();player.setHeldItem(EnumHand.MAIN_HAND,held);player.setPositionAndRotation(old.x,old.y,old.z,yaw,pitch);world.getGameRules().setOrCreateGameRule("doTileDrops",Boolean.toString(drops));}
        System.out.println("[vandorlabs][reprolab] moving-device-ownership PASS fill cleanup, rotated inventory return, blocked durable recovery, ramp/turntable proxy dialogs and Taxi/Hover entity configuration");
    }
    private static void ramp(WorldServer world,EntityPlayerMP player,BlockPos root)throws Exception{
        player.setPosition(root.getX()+.5,root.getY()+1,root.getZ()-2);
        world.setBlockState(root,ModBlocks.PROGRAMMABLE_RAMP.getDefaultState().withProperty(BlockVandorDirectional.FACING,EnumFacing.SOUTH));
        TileEntityRampController controller=(TileEntityRampController)world.getTileEntity(root);List<BlockPos> sources=new ArrayList<>();
        for(int row=1;row<=3;row++)for(int x=0;x<2;x++){BlockPos p=root.add(x,0,row);sources.add(p);world.setBlockState(p,Blocks.GOLD_BLOCK.getDefaultState());}
        require(controller.configure(player,2,8,true,true,false,false),"ramp configuration");world.setBlockState(root.north(),Blocks.REDSTONE_BLOCK.getDefaultState());controller.updatePower();
        require(controller.attached(),"ramp capture");List<BlockPos> cells=new ArrayList<>(controller.vehicleCells());
        BlockPos part=cells.get(0);require(ProgrammableTarget.settingsPos(world,part).equals(root),"extended ramp Configurizer resolves owner");
        player.setPosition(root.getX()+.5,root.getY()+1,root.getZ()-2);ItemConfigurizer.onRightClickBlock(new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,EnumHand.MAIN_HAND,part,EnumFacing.UP,new Vec3d(part)));
        require(player.openContainer instanceof com.vandorlabs.container.ContainerRampController,"ramp proxy opens configuration");player.closeScreen();
        fill(world,player,root,"minecraft:stone");for(BlockPos p:cells){TileEntity tile=world.getTileEntity(p);if(tile instanceof TileEntityControlledRamp)((TileEntityControlledRamp)tile).recoverOnEvent();}
        for(BlockPos p:sources)require(world.getBlockState(p).getBlock()==Blocks.GOLD_BLOCK,"command deletion restores ramp sources");
        for(BlockPos p:cells)require(!(world.getTileEntity(p) instanceof TileEntityControlledRamp),"no orphan ramp reservations");
        for(BlockPos p:sources)world.setBlockToAir(p);world.setBlockToAir(root);world.setBlockToAir(root.north());
    }
    private static void vehicle(WorldServer world,EntityPlayerMP player,BlockPos root)throws Exception{
        TileEntityPilotSeat seat=new TileEntityPilotSeat();seat.setPos(BlockPos.ORIGIN);
        List<VehicleStructure.Cell> cells=Arrays.asList(new VehicleStructure.Cell(BlockPos.ORIGIN,ModBlocks.PILOT_SEAT.getDefaultState(),seat.writeToNBT(new NBTTagCompound())),new VehicleStructure.Cell(new BlockPos(1,1,1),Blocks.STONE.getDefaultState(),null));
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(new VehicleStructure(cells,BlockPos.ORIGIN,EnumFacing.NORTH,1));craft.setPosition(root.getX(),root.getY(),root.getZ());world.spawnEntity(craft);
        try{for(boolean hover:new boolean[]{false,true}){craft.hover=hover;aim(player,craft.toWorld(new Vec3d(1.5,1.5,1.5)));net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(player,EnumHand.MAIN_HAND,craft);ItemConfigurizer.onMovingEntity(event);require(event.isCanceled(),"Taxi/Hover entity click handled by Configurizer");}}
        finally{world.removeEntityDangerously(craft);}
    }
    private static void aim(EntityPlayerMP player,Vec3d point){player.setPositionAndRotation(point.x,point.y-player.getEyeHeight(),point.z-4,0,0);}
    private static void fill(WorldServer world,EntityPlayerMP player,BlockPos pos,String block)throws Exception{new CommandFill().execute(world.getMinecraftServer(),player,new String[]{""+pos.getX(),""+pos.getY(),""+pos.getZ(),""+pos.getX(),""+pos.getY(),""+pos.getZ(),block,"0","replace"});}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("moving device: "+message);}
}
