package com.vandorlabs.gear;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Real placement/mining must retain native block identity and leave no virtual endpoint copy. */
public final class ArmPayloadSelectionChecks {
    private static int checks;
    private static void require(boolean value,String reason){checks++;if(!value)throw new IllegalStateException("Arm payload selection: "+reason);}
    private static void clear(WorldServer world,BlockPos root){for(BlockPos at:BlockPos.getAllInBox(root.add(-5,-5,-5),root.add(5,5,5))){world.removeTileEntity(at);world.setBlockToAir(at);}}
    private static RayTraceResult payloadRay(WorldServer world,BlockPos cell,EnumFacing mount,boolean virtual){
        EnumFacing side=mount.getAxis()==EnumFacing.Axis.X?EnumFacing.SOUTH:EnumFacing.EAST;Vec3d center=new Vec3d(cell).addVector(.5,.5,.5),normal=new Vec3d(side.getDirectionVec());Vec3d start=center.add(normal.scale(2)),end=center.subtract(normal.scale(.48));return virtual?GearInteractions.trace(world,start,end):world.rayTraceBlocks(start,end,false,false,false);
    }
    private static void noGhost(WorldServer world,GearTileEntity arm,BlockPos removed,EnumFacing mount){
        require(payloadRay(world,removed,mount,false)==null,"normal ray sees empty former payload cell "+mount);
        require(payloadRay(world,removed,mount,true)==null,"custom ray cannot route removed payload to arm "+mount);
        AxisAlignedBB empty=new AxisAlignedBB(removed).shrink(.15);for(AxisAlignedBB box:world.getCollisionBoxes(null,empty))require(!box.intersects(empty),"removed physical payload leaves no collision "+mount);
        Vec3d center=new Vec3d(arm.getPos()).addVector(.5,.5,.5),back=new Vec3d(mount.getOpposite().getDirectionVec());RayTraceResult body=world.rayTraceBlocks(center.add(back.scale(2)),center,false,false,false);require(body!=null&&body.getBlockPos().equals(arm.getPos()),"real mechanism body remains normally selectable "+mount);
    }
    private static void placePayload(WorldServer world,EntityPlayerMP actor,GearTileEntity arm,EnumFacing mount){
        BlockPos root=arm.getPos();Vec3d center=new Vec3d(root).addVector(.5,.5,.5),normal=new Vec3d(mount.getDirectionVec());RayTraceResult tip=GearInteractions.trace(world,center.add(normal.scale(3)),center.subtract(normal.scale(.25)));require(tip!=null&&tip.getBlockPos().equals(root)&&tip.sideHit==mount,"actual construction tip selects native arm face "+mount);
        actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(Blocks.STONE,2));require(Item.getItemFromBlock(Blocks.STONE).onItemUse(actor,world,tip.getBlockPos(),EnumHand.MAIN_HAND,tip.sideHit,(float)(tip.hitVec.x-root.getX()),(float)(tip.hitVec.y-root.getY()),(float)(tip.hitVec.z-root.getZ()))==EnumActionResult.SUCCESS,"normal ItemBlock click attaches payload "+mount);require(world.getBlockState(root.offset(mount)).getBlock()==Blocks.STONE,"one physical stone at clicked arm endpoint "+mount);
    }
    private static void mine(WorldServer world,EntityPlayerMP actor,GearTileEntity arm,EnumFacing mount){
        BlockPos cell=arm.getPos().offset(mount);RayTraceResult hit=payloadRay(world,cell,mount,false);require(hit!=null&&hit.getBlockPos().equals(cell),"native ray retains real materialized payload identity "+mount);require(payloadRay(world,cell,mount,true)==null,"materialized payload is not an arm-root selection proxy "+mount);require(actor.interactionManager.tryHarvestBlock(hit.getBlockPos())&&world.isAirBlock(cell),"normal Minecraft harvest removes only payload "+mount);require(world.getTileEntity(arm.getPos())==arm,"payload mining preserves actual arm "+mount);noGhost(world,arm,cell,mount);
    }
    public static void run(WorldServer world,EntityPlayerMP actor){
        checks=0;BlockPos root=new BlockPos(656,178,288);double x=actor.posX,y=actor.posY,z=actor.posZ;float yaw=actor.rotationYaw;boolean creative=actor.capabilities.isCreativeMode;ItemStack held=actor.getHeldItemMainhand();
        for(int cx=-1;cx<=1;cx++)for(int cz=-1;cz<=1;cz++)world.getChunkFromChunkCoords((root.getX()>>4)+cx,(root.getZ()>>4)+cz);
        try{actor.capabilities.isCreativeMode=true;actor.setPosition(root.getX()+9,root.getY()+8,root.getZ()+9);
            for(EnumFacing mount:EnumFacing.values()){
                clear(world,root);BlockPos support=root.offset(mount.getOpposite());world.setBlockState(support,Blocks.STONE.getDefaultState(),2);actor.setHeldItem(EnumHand.MAIN_HAND,new ItemStack(GearRegistry.GEAR_SWING_ARM,2));require(Item.getItemFromBlock(GearRegistry.GEAR_SWING_ARM).onItemUse(actor,world,support,EnumHand.MAIN_HAND,mount,.5F,.5F,.5F)==EnumActionResult.SUCCESS,"actual arm placement on support "+mount);GearTileEntity arm=(GearTileEntity)world.getTileEntity(root);arm.operator(actor);require(arm.rebuild()&&arm.mountDirection()==mount,"mounted rotating arm formed "+mount);world.setBlockToAir(support);
                placePayload(world,actor,arm,mount);arm.collisionBoxes();mine(world,actor,arm,mount);require(arm.payload()==null,"fresh ordinary removal has no retained snapshot");
                placePayload(world,actor,arm,mount);arm.target(false);for(int tick=0;tick<50;tick++)arm.update();require(arm.progress()==0&&arm.payload()!=null,"actual rotated endpoint retains one owned payload "+mount+" error="+arm.error());arm.target(true);for(int tick=0;tick<50;tick++)arm.update();require(arm.progress()==1&&arm.payload()!=null&&arm.payload().materialized(),"return installs payload as real native block "+mount);arm.collisionBoxes();mine(world,actor,arm,mount);arm.update();require(arm.payload()==null&&!arm.hasRetainedPayload(),"mined materialized endpoint retires obsolete snapshot/cache "+mount);noGhost(world,arm,root.offset(mount),mount);
                arm.target(false);for(int tick=0;tick<50;tick++)arm.update();require(arm.progress()==0&&arm.payload()==null,"empty arm can rotate after payload removal "+mount);noGhost(world,arm,root.offset(mount),mount);
            }
        }finally{clear(world,root);actor.setHeldItem(EnumHand.MAIN_HAND,held);actor.capabilities.isCreativeMode=creative;actor.setPosition(x,y,z);actor.rotationYaw=yaw;}
        System.out.println("[vandorlabs][reprolab] arm-payload-selection PASS checks="+checks+" six mounts actual ItemBlock tip placement native harvest before/after rotation materialized world ownership no ghost collision/ray actual arm selectable");
    }
    private ArmPayloadSelectionChecks(){}
}
