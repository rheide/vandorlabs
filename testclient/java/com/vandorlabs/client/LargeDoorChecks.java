package com.vandorlabs.client;

import com.vandorlabs.blocks.*;
import com.vandorlabs.items.*;
import com.vandorlabs.tiles.*;
import net.minecraft.block.BlockDoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.util.UUID;

/** Actual placement/copy/role code in a world that throws on unloaded reads. */
final class LargeDoorChecks {
    static void run() {
        timingAndPanel();
        BlockDetailedDoor paired=new BlockDetailedDoor("large_check_model",BlockVandorDoor.DoorMotion.SLIDING,true,true,false,1,15,7,90,-90,-16,16);
        BlockLargeProgrammableDoor block=new BlockLargeProgrammableDoor("large_programmable_door",paired);
        ForgeRegistries.BLOCKS.register(block);
        ItemLargeProgrammableDoor item=new ItemLargeProgrammableDoor(block);
        ForgeRegistries.ITEMS.register(item.setRegistryName(block.getRegistryName()));
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(TileEntityLargeProgrammableDoor.class,new ResourceLocation("minecraft:large_door_check"));
        int cells=0;
        for(EnumFacing front:EnumFacing.Plane.HORIZONTAL) {
            NonRenderingChecks.MemoryWorld world=new NonRenderingChecks.MemoryWorld(false);
            EntityPlayer player=new EntityPlayer(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"LargeDoorCheck")) {
                @Override public boolean isSpectator(){return false;}
                @Override public boolean isCreative(){return true;}
                @Override public boolean canPlayerEdit(BlockPos pos,EnumFacing side,ItemStack stack){return capabilities.allowEdit;}
            };
            player.capabilities.allowEdit=true;player.capabilities.isCreativeMode=true;
            BlockPos center=new BlockPos(8,100,8),anchor=center.offset(front.rotateYCCW(),-1);
            player.rotationYaw=front.getOpposite().getHorizontalAngle();player.setPosition(8,101,8);
            for(int x=0;x<3;x++)world.setBlockState(anchor.offset(front.rotateYCCW(),x).down(),Blocks.STONE.getDefaultState(),2);
            ItemStack stack=new ItemStack(item,2);player.setHeldItem(EnumHand.MAIN_HAND,stack);
            require(item.onItemUse(player,world,center.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.SUCCESS && stack.getCount()==1,"placement/item count");
            TileEntityLargeProgrammableDoor root=block.root(world,center.up(2));
            require(root!=null && root.getPos().equals(anchor) && block.complete(world,root),"complete anchored opening");
            int renderers=0;
            for(int x=0;x<3;x++)for(int y=0;y<3;y++) {
                BlockPos p=anchor.offset(front.rotateYCCW(),x).up(y);
                require(block.getActualState(world.getBlockState(p),world,p).getValue(BlockConnectingDetailedDoor.PAIRED),"single leaf exposed");
                require(ProgrammableTarget.settingsPos(world,p).equals(anchor),"tool target");
                TileEntityLargeProgrammableDoor saved=new TileEntityLargeProgrammableDoor();
                saved.readFromNBT(world.getTileEntity(p).writeToNBT(new NBTTagCompound()));saved.setWorld(world);saved.setPos(p);
                world.tiles.put(p,saved);require(saved.anchorPos().equals(anchor),"saved role");
                if(saved.shouldRenderInPass(0))renderers++;cells++;
            }
            require(renderers==1,"duplicate renderers");
            // Test a player's body in the middle of the opening, not just OPEN flags.
            for(boolean frame:new boolean[]{false,true})for(boolean slide:new boolean[]{false,true})
                for(int direction=0;direction<(slide?3:1);direction++)for(int depth=0;depth<3;depth++)
                    for(boolean hinges:new boolean[]{false,true}) {
                        root=block.root(world,anchor);root.configure(2,1,frame,direction,false,slide,hinges,0,false);root.setPlacementDepth(depth);
                        double localX=1.5,localZ=(slide?.5:13.24/16)+root.positionOffset();
                        double wx=front==EnumFacing.NORTH?1-localX:front==EnumFacing.EAST?localZ:front==EnumFacing.WEST?1-localZ:localX;
                        double wz=front==EnumFacing.NORTH?1-localZ:front==EnumFacing.EAST?1-localX:front==EnumFacing.WEST?localX:localZ;
                        AxisAlignedBB body=new AxisAlignedBB(anchor.getX()+wx-.3,100.25,anchor.getZ()+wz-.3,anchor.getX()+wx+.3,102.05,anchor.getZ()+wz+.3);
                        require(!collisions(block,world,anchor,front,body).isEmpty(),"closed door fails to block passage");
                        block.onBlockActivated(world,center,world.getBlockState(center),player,EnumHand.MAIN_HAND,front,.5F,.5F,.5F);
                        require(collisions(block,world,anchor,front,body).isEmpty(),"open door blocks passage");
                        block.onBlockActivated(world,center,world.getBlockState(center),player,EnumHand.MAIN_HAND,front,.5F,.5F,.5F);
                    }
            root=block.root(world,anchor);root.configure(15,2,false,0,true,true,false,0,false);
            require(block.getPickBlock(world.getBlockState(center),null,world,center,player).getSubCompound("SpaceDoorSettings").getInteger("SpaceDesign")==15,"pick lost anchor design");
            require(block.getActualState(world.getBlockState(center),world,center).getValue(BlockDoor.HALF)==BlockDoor.EnumDoorHalf.UPPER,"follower renders");
            block.breakBlock(world,center.up(),world.getBlockState(center.up()));world.setBlockToAir(center.up());
            for(int x=0;x<3;x++)for(int y=0;y<3;y++)require(world.getBlockState(anchor.offset(front.rotateYCCW(),x).up(y)).getBlock()!=block,"partial removal");
            world.setBlockState(anchor.up(2),Blocks.GOLD_BLOCK.getDefaultState(),2);stack=new ItemStack(item,2);player.setHeldItem(EnumHand.MAIN_HAND,stack);
            require(item.onItemUse(player,world,center.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.FAIL && stack.getCount()==2,"obstruction not atomic");
            world.setBlockToAir(anchor.up(2));player.capabilities.allowEdit=false;
            require(item.onItemUse(player,world,center.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.FAIL && stack.getCount()==2,"edit denial");
            world.chunkLimit=true;
            require(item.onItemUse(player,world,new BlockPos(16,99,8),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.FAIL,"unloaded click");
            player.capabilities.allowEdit=true;
            world.chunkLimit=false;
            BlockPos boundary=front.rotateYCCW().getAxis()==EnumFacing.Axis.X?new BlockPos(15,100,8):new BlockPos(8,100,15);
            for(int x=-1;x<=1;x++)world.setBlockState(boundary.offset(front.rotateYCCW(),x).down(),Blocks.STONE.getDefaultState(),2);
            world.chunkLimit=true;
            require(item.onItemUse(player,world,boundary.down(),EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F)==EnumActionResult.FAIL && stack.getCount()==2,"unloaded footprint");
        }
        System.out.println("PASS: Large Door four-facing placement, "+cells+" cell roles/NBT, one renderer, 192 open/closed passage configurations, shared picking, atomic removal/obstruction/edit denial and unloaded chunk guards");
    }
    private static void timingAndPanel(){
        double regularTicks=TESlidingDoor.animationTicks(new TileEntitySpaceDoor());
        double largeTicks=TESlidingDoor.animationTicks(new TileEntityLargeProgrammableDoor());
        require(regularTicks==9 && largeTicks==12,"animation durations");
        com.vandorlabs.animation.DoorAnimation regular=new com.vandorlabs.animation.DoorAnimation(regularTicks),large=new com.vandorlabs.animation.DoorAnimation(largeTicks);
        regular.sample(false,0);large.sample(false,0);regular.sample(true,0);large.sample(true,0);
        require(regular.sample(true,9)==1 && large.sample(true,9)<1,"large animation not slower");
        require(large.sample(true,12)==1,"large opening endpoint");large.sample(false,12);
        require(large.sample(false,24)==0,"large closing endpoint");
        for(boolean slide:new boolean[]{false,true})for(boolean far:new boolean[]{false,true})for(int facing=0;facing<4;facing++){
            java.util.List<com.vandorlabs.render.LargeDoorGeometry.Box> geometry=com.vandorlabs.render.LargeDoorGeometry.boxes(true,slide,0,false,0,facing,true,far);
            com.vandorlabs.render.LargeDoorGeometry.Box panel=geometry.get(geometry.size()-1);
            require(Math.abs((panel.y0+panel.y1)/2-1.125)<1e-9,"panel center differs from regular door");
            double z=(com.vandorlabs.render.SpaceDoorControlPanel.z0(slide,far)+com.vandorlabs.render.SpaceDoorControlPanel.z1(slide,far))/32;
            require(com.vandorlabs.render.SpaceDoorControlPanel.containsScaled(z,1.125,slide,far,1.5),"lowered panel not clickable");
            require(!com.vandorlabs.render.SpaceDoorControlPanel.containsScaled(z,1.6875,slide,far,1.5),"old high click area retained");
        }
        System.out.println("PASS: large-door animation 12 versus 9 ticks; panel center/click region at regular-door height in all facings and depth sides");
    }
    private static java.util.List<AxisAlignedBB> collisions(BlockLargeProgrammableDoor block,NonRenderingChecks.MemoryWorld world,BlockPos anchor,EnumFacing front,AxisAlignedBB body){
        java.util.List<AxisAlignedBB> result=new java.util.ArrayList<>();
        for(int x=0;x<3;x++)for(int y=0;y<3;y++){
            BlockPos p=anchor.offset(front.rotateYCCW(),x).up(y);
            block.addCollisionBoxToList(world.getBlockState(p),world,p,body,result,null,false);
        }
        return result;
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException("large door: "+message);}
}
