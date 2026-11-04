package com.vandorlabs.client;

import com.vandorlabs.vehicle.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Actual vanilla mouse-over and Survival mining at a bent trailer's blocked corner. */
final class VehicleTerrainAccessChecks {
    private static final BlockPos ORIGIN=new BlockPos(1600,100,1600);
    private static UUID id;private static BlockPos target;private static Vec3d eye;private static float yaw,pitch;private static int stage,ticks;
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException("Vehicle terrain access stage="+stage+": "+why);}
    static boolean tick(Minecraft mc)throws Exception{
        EntityPlayerMP actor=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        require(++ticks<300,"timeout target="+target+" id="+id+" mode="+mc.playerController.getCurrentGameType()+" slot="+mc.player.inventory.currentItem+" held="+mc.player.getHeldItemMainhand()+" pos="+mc.player.getPositionVector());
        if(stage==0){mc.getIntegratedServer().addScheduledTask(()->setup(actor)).get();mc.displayGuiScreen(null);mc.playerController.setGameType(GameType.SURVIVAL);mc.player.inventory.currentItem=0;stage=1;ticks=0;return false;}
        if(stage==1){net.minecraft.entity.Entity e=mc.world.getEntityByID(mc.getIntegratedServer().getWorld(0).getEntityFromUuid(id).getEntityId());if(!(e instanceof EntityGroundVehicle)||((EntityGroundVehicle)e).structure==null||Math.abs(((EntityGroundVehicle)e).articulation.jointYaw(0)-35)>.1||mc.world.getBlockState(target).getBlock()!=Blocks.STONE||mc.player.getDistanceSq(eye.x,eye.y-mc.player.getEyeHeight(),eye.z)>.05||mc.player.getHeldItemMainhand().getItem()!=Items.DIAMOND_PICKAXE||mc.playerController.getCurrentGameType()!=GameType.SURVIVAL)return false;
            mc.player.rotationYaw=mc.player.prevRotationYaw=yaw;mc.player.rotationPitch=mc.player.prevRotationPitch=pitch;mc.entityRenderer.getMouseOver(1);OffsetTrapdoorSelection.updateTarget(1);RayTraceResult selected=mc.objectMouseOver;
            require(selected!=null&&selected.typeOfHit==RayTraceResult.Type.BLOCK&&selected.getBlockPos().equals(target),"blocking stone must remain selectable through empty vehicle bounds; selected="+(selected==null?"null":selected.typeOfHit+" "+selected.getBlockPos()));
            mc.playerController.clickBlock(target,selected.sideHit);stage=2;ticks=0;return false;
        }
        if(mc.world.getBlockState(target).getBlock()!=Blocks.AIR){mc.entityRenderer.getMouseOver(1);OffsetTrapdoorSelection.updateTarget(1);if(mc.objectMouseOver!=null&&mc.objectMouseOver.typeOfHit==RayTraceResult.Type.BLOCK)mc.playerController.onPlayerDamageBlock(target,mc.objectMouseOver.sideHit);return false;}
        require(mc.getIntegratedServer().callFromMainThread(()->actor.world.isAirBlock(target)).get(),"real Survival dig packet removes terrain on server");mc.getIntegratedServer().addScheduledTask(()->{net.minecraft.entity.Entity e=actor.getServerWorld().getEntityFromUuid(id);if(e!=null){e.setDead();actor.world.removeEntityDangerously(e);}for(BlockPos p:BlockPos.getAllInBox(ORIGIN.add(-15,-1,-5),ORIGIN.add(12,3,18)))actor.world.setBlockToAir(p);}).get();
        System.out.println("[vandorlabs][reprolab] vehicle-terrain-access PASS bent35 blocked steering actual vanilla mouse-over Survival mining server/client terrain preserved until dig");return true;
    }
    private static void setup(EntityPlayerMP actor){
        WorldServer world=actor.getServerWorld();for(BlockPos p:BlockPos.getAllInBox(ORIGIN.add(-15,-1,-5),ORIGIN.add(12,3,18)))world.setBlockState(p,p.getY()==99?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        EntityGroundVehicle craft=new EntityGroundVehicle(world);craft.install(VehicleCouplerSectionChecks.train(world,new int[]{3,3},false,false,false,true,true));craft.setPosition(ORIGIN.getX(),ORIGIN.getY(),ORIGIN.getZ());craft.articulation.receive(craft.articulation.topologyId(),new short[]{3500,0},true);craft.setPosition(craft.posX,craft.posY,craft.posZ);
        AxisAlignedBB bounds=craft.getEntityBoundingBox();boolean found=false;
        outer:for(BlockPos raw:BlockPos.getAllInBox(new BlockPos(bounds.minX,100,bounds.minZ),new BlockPos(bounds.maxX,100,bounds.maxZ))){BlockPos p=raw.toImmutable();world.setBlockState(p,Blocks.STONE.getDefaultState(),2);boolean clear=craft.articulation.validPose();if(clear)for(EnumFacing side:EnumFacing.HORIZONTALS){Vec3d center=new Vec3d(p).addVector(.5,.5,.5),start=center.addVector(side.getFrontOffsetX()*2,2,side.getFrontOffsetZ()*2),end=center.subtract(start.subtract(center).normalize().scale(.2));RayTraceResult terrain=world.rayTraceBlocks(start,end,false,false,true),broad=bounds.calculateIntercept(start,end);
                if(terrain==null||terrain.typeOfHit!=RayTraceResult.Type.BLOCK||!p.equals(terrain.getBlockPos())||broad==null||start.squareDistanceTo(broad.hitVec)>=start.squareDistanceTo(terrain.hitVec)||VehicleSelection.trace(craft,start,end,start.squareDistanceTo(terrain.hitVec))!=null)continue;
                boolean blocked=false;for(int sign:new int[]{-1,1}){craft.articulation.begin();float accepted=craft.articulation.turn(craft.posX,craft.posY,craft.posZ,0,sign*10);craft.articulation.end();craft.articulation.receive(craft.articulation.topologyId(),new short[]{3500,0},true);if(Math.abs(accepted)<9.9)blocked=true;}
                if(blocked){target=p;eye=start;Vec3d d=center.subtract(start);yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));found=true;break outer;}
            }world.setBlockToAir(p);
        }
        require(found,"native blocked-corner witness inside enclosing bounds");craft.stopForParking();id=craft.getUniqueID();require(world.spawnEntity(craft),"spawn bent vehicle");actor.dismountRidingEntity();actor.setGameType(GameType.SURVIVAL);actor.capabilities.allowFlying=true;actor.capabilities.isFlying=true;actor.sendPlayerAbilities();actor.inventory.currentItem=0;actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));actor.sendContainerToPlayer(actor.inventoryContainer);actor.connection.setPlayerLocation(eye.x,eye.y-actor.getEyeHeight(),eye.z,yaw,pitch);
    }
    private VehicleTerrainAccessChecks(){}
}
