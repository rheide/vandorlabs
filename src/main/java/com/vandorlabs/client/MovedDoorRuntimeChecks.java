package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;

/** Real client targeting and right-click packets for leaves outside their original cells. */
final class MovedDoorRuntimeChecks {
    private static int fixture,stage,ticks;
    private static Future<?> pending;
    private static final BlockPos ANCHOR=new BlockPos(8,80,8);
    static void tick(Minecraft mc,File output) {
        try {
            if(pending!=null){if(!pending.isDone())return;pending.get();pending=null;}
            if(stage==0) {
                final int index=fixture;
                pending=mc.getIntegratedServer().addScheduledTask(()->build(mc,index));stage=1;ticks=0;return;
            }
            if(++ticks<15)return;
            if(stage==1) {
                IBlockState state=mc.world.getBlockState(ANCHOR);
                require(state.getValue(BlockVandorDoor.OPEN),"open fixture synchronized");
                AxisAlignedBB leaf=leaf(mc.world,fixture);
                Vec3d target=target(leaf,fixture),start=start(leaf,target);
                mc.player.setSneaking(false);
                mc.player.noClip=true;mc.player.capabilities.isFlying=true;
                mc.player.setPosition(start.x,start.y-mc.player.getEyeHeight(),start.z);
                mc.player.rotationYaw=mc.player.prevRotationYaw=leaf.maxX-leaf.minX<leaf.maxZ-leaf.minZ?90:180;
                mc.player.rotationPitch=mc.player.prevRotationPitch=0;
                Vec3d end=start.add(mc.player.getLookVec().scale(5));
                RayTraceResult moved=MovedDoorInteractions.trace(mc.world,start,end);
                require(moved!=null,"moved leaf trace missed");
                require(mc.world.getBlockState(new BlockPos(moved.hitVec)).getBlock()!=state.getBlock(),"fixture did not exercise an offset portion");
                mc.objectMouseOver=mc.world.rayTraceBlocks(start,end,false,false,true);
                mc.playerController.updateController();
                require(mc.objectMouseOver!=null && mc.objectMouseOver.typeOfHit==RayTraceResult.Type.BLOCK
                        && mc.world.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock()==state.getBlock(),"controller lost moved-door target");
                AxisAlignedBB probe=new AxisAlignedBB(moved.hitVec,moved.hitVec).grow(.02);
                require(!mc.world.getCollisionBoxes(mc.player,probe).isEmpty(),"moved panel has no collision in destination cell");
                if(fixture<40 && fixture%5==0 || fixture>=40 && (fixture-40)%6==0) {
                    String name=fixture<40?"large_door_selection_"+(fixture/5):"regular_door_selection_"+((fixture-40)/6);
                    ImageIO.write(ScreenShotHelper.createScreenshot(mc.displayWidth,mc.displayHeight,mc.getFramebuffer()),"png",new File(output,"shot_"+name+".png"));
                    System.out.println("[vandorlabs][reprolab] wrote shot_"+name+".png");
                }
                RayTraceResult hit=mc.objectMouseOver;
                require(mc.playerController.processRightClickBlock(mc.player,mc.world,hit.getBlockPos(),hit.sideHit,hit.hitVec,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS,"click was rejected");
                stage=2;ticks=0;return;
            }
            pending=mc.getIntegratedServer().addScheduledTask(()->require(!mc.getIntegratedServer().getEntityWorld().getBlockState(ANCHOR).getValue(BlockVandorDoor.OPEN),"right-click packet did not close door"));
            fixture++;stage=0;ticks=0;
            if(fixture==64){pending.get();pending=null;System.out.println("[vandorlabs][reprolab] moved-door-selection PASS large=40 regular=24 (all facings, open modes, hinges and depths; real close packets)");mc.shutdown();stage=3;}
        }catch(Exception e){throw new IllegalStateException("moved-door live check fixture="+fixture+" stage="+stage,e);}
    }
    private static void build(Minecraft mc,int index) {
        net.minecraft.world.World world=mc.getIntegratedServer().getEntityWorld();
        EntityPlayerMP player=mc.getIntegratedServer().getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
        for(BlockPos pos:BlockPos.getAllInBox(ANCHOR.add(-7,-5,-7),ANCHOR.add(8,8,8)))if(world.isBlockLoaded(pos))world.setBlockToAir(pos);
        boolean large=index<40;int local=large?index:index-40;
        EnumFacing facing=EnumFacing.getHorizontal(large?local/10:local/6);
        BlockConfigurableSpaceDoor block=(BlockConfigurableSpaceDoor)Block.REGISTRY.getObject(new ResourceLocation("vandorlabs",large?"large_programmable_door":"programmable_door"));
        IBlockState state=block.getDefaultState().withProperty(BlockVandorDoor.FACING,facing).withProperty(BlockVandorDoor.OPEN,true);
        if(large) {
            EnumFacing width=facing.rotateYCCW();
            for(int x=0;x<3;x++)world.setBlockState(ANCHOR.offset(width,x).down(),Blocks.STONE.getDefaultState(),2);
            for(int x=0;x<3;x++)for(int y=0;y<3;y++) {
                BlockPos pos=ANCHOR.offset(width,x).up(y);world.setBlockState(pos,state,2);
                ((TileEntityLargeProgrammableDoor)world.getTileEntity(pos)).assign(ANCHOR);
            }
            TileEntityLargeProgrammableDoor root=(TileEntityLargeProgrammableDoor)world.getTileEntity(ANCHOR);
            int mode=local%5;root.configure(23,1,local%10>=5,Math.max(0,mode-1),false,mode>0,true,0,false);
        }else {
            world.setBlockState(ANCHOR.down(),Blocks.STONE.getDefaultState(),2);
            state=state.withProperty(BlockVandorDoor.HINGE,local%6>=3?BlockDoor.EnumHingePosition.LEFT:BlockDoor.EnumHingePosition.RIGHT);
            world.setBlockState(ANCHOR,state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.LOWER),2);
            world.setBlockState(ANCHOR.up(),state.withProperty(BlockVandorDoor.HALF,BlockDoor.EnumDoorHalf.UPPER),2);
            TileEntitySpaceDoor root=(TileEntitySpaceDoor)world.getTileEntity(ANCHOR);root.configure(2,1,true,0,false,false,true,0,false);root.setPlacementDepth(local%3);
        }
        AxisAlignedBB leaf=leaf(world,index);Vec3d start=start(leaf,target(leaf,index));
        player.setSneaking(false);player.noClip=true;player.capabilities.isFlying=true;player.setPositionAndUpdate(start.x,start.y-player.getEyeHeight(),start.z);
        player.setHeldItem(EnumHand.MAIN_HAND,net.minecraft.item.ItemStack.EMPTY);
    }
    private static AxisAlignedBB leaf(net.minecraft.world.World world,int index) {
        if(index>=40)return world.getBlockState(ANCHOR).getBoundingBox(world,ANCHOR).offset(ANCHOR);
        List<AxisAlignedBB> boxes=((BlockLargeProgrammableDoor)world.getBlockState(ANCHOR).getBlock()).geometry(world,ANCHOR);
        return boxes.get(index%10>=5?4:0);
    }
    private static Vec3d target(AxisAlignedBB box,int index) {
        Vec3d point=box.getCenter();
        AxisAlignedBB owners=new AxisAlignedBB(ANCHOR).union(new AxisAlignedBB(ANCHOR.up()));
        if(index<40) {
            EnumFacing width=EnumFacing.getHorizontal(index/10).rotateYCCW();
            owners=new AxisAlignedBB(ANCHOR).union(new AxisAlignedBB(ANCHOR.offset(width,2).up(2)));
        }
        if(box.minX<owners.minX)return new Vec3d((box.minX+owners.minX)/2,point.y,point.z);
        if(box.maxX>owners.maxX)return new Vec3d((box.maxX+owners.maxX)/2,point.y,point.z);
        if(box.minZ<owners.minZ)return new Vec3d(point.x,point.y,(box.minZ+owners.minZ)/2);
        if(box.maxZ>owners.maxZ)return new Vec3d(point.x,point.y,(box.maxZ+owners.maxZ)/2);
        if(box.minY<owners.minY)return new Vec3d(point.x,(box.minY+owners.minY)/2,point.z);
        return new Vec3d(point.x,(box.maxY+owners.maxY)/2,point.z);
    }
    private static Vec3d start(AxisAlignedBB box,Vec3d point){return box.maxX-box.minX<box.maxZ-box.minZ?new Vec3d(box.maxX+.6,point.y,point.z):new Vec3d(point.x,point.y,box.maxZ+.6);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
