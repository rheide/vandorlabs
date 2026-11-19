package com.vandorlabs.neoforge.door;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.fluids.FluidStack;

/** Survival side placement, source ownership, vertical fluid publication and render camera. */
final class HorizontalLargeDoorPlayerFixture {
    private static int index;
    private HorizontalLargeDoorPlayerFixture(){}

    static Direction facing(){return Direction.from2DDataValue(index);}
    static BlockPos center(){return LargeDoorWorldClientFixture.POS;}
    static BlockPos support(Direction front){return center().relative(front.getOpposite());}
    static BlockPos anchor(Direction front){return center().relative(front.getCounterClockWise(),-1);}
    static BlockPos passage(Direction front){return center().relative(front);}

    static void prepare(ServerPlayer player,int facingIndex){
        index=facingIndex;
        var level=player.serverLevel();
        for(var at:BlockPos.betweenClosed(center().offset(-6,-2,-6),center().offset(6,6,6))){
            level.setBlock(at,at.getY()==center().getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        }
        Direction front=facing();
        var support=support(front);
        for(int x=-1;x<=1;x++)level.setBlock(support.relative(front.getCounterClockWise(),x),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        player.getInventory().selected=0;
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(LargeDoorWorldClientFixture.item()));
        player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 axis=Vec3.atLowerCornerOf(front.getNormal());
        Vec3 point=Vec3.atCenterOf(support).add(axis.scale(.5));
        Vec3 feet=point.add(axis.scale(2)).subtract(0,.5,0);
        orient(player,feet,point);
    }
    private static void orient(ServerPlayer player,Vec3 feet,Vec3 target){
        Vec3 delta=target.subtract(feet.add(0,player.getEyeHeight(),0));
        float yaw=(float)-Math.toDegrees(Math.atan2(delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        player.connection.teleport(feet.x,feet.y,feet.z,yaw,pitch);
        player.onUpdateAbilities();
        player.inventoryMenu.broadcastChanges();
    }
    static void verifyAndView(ServerPlayer player){
        Direction front=facing();
        var level=player.serverLevel();
        var root=NativeLargeDoorLifecycle.root(level,passage(front));
        need(root!=null&&root.isHorizontal()&&root.getBlockPos().equals(anchor(front))
                &&NativeLargeDoorLifecycle.complete(level,root)&&player.getMainHandItem().isEmpty(),"one survival item and nine roles");
        for(int col=0;col<3;col++)for(int row=0;row<3;row++){
            var at=NativeLargeDoorLifecycle.cell(root.getBlockPos(),front,true,col,row);
            var tile=(NativeLargeDoorEntity)level.getBlockEntity(at);
            need(tile.isHorizontal()&&tile.anchorPos().equals(root.getBlockPos())&&tile.saveWithoutMetadata(level.registryAccess()).getBoolean("LargeDoorHorizontal"),"role saved at "+at);
        }
        var passage=passage(front);
        var query=new AABB(passage).inflate(0,2,0).deflate(.02);
        Vec3 middle=Vec3.atCenterOf(passage);
        var target=NativeLargeDoorTargets.trace(level,middle.add(0,2,0),middle.add(0,-2,0));
        need(level.getBlockCollisions(null,query).iterator().hasNext()&&target!=null&&target.hit().getBlockPos().equals(passage),"closed blocks downward path and ray resolves center");

        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying=true;
        orient(player,middle.add(Vec3.atLowerCornerOf(front.getNormal()).scale(2)).add(0,2,0),middle);
    }
    static void verifyOpened(ServerPlayer player){
        var level=player.serverLevel();
        var middle=passage(facing());
        var root=NativeLargeDoorLifecycle.root(level,middle);
        need(root!=null&&root.getBlockState().getValue(NativeLargeDoorBlock.OPEN),"actual player click opens hatch");
        // The temporary survival floor is below this swept aperture.
        var query=new AABB(middle.getX()+.04,middle.getY(),middle.getZ()+.04,middle.getX()+.96,middle.getY()+2,middle.getZ()+.96);
        Vec3 p=Vec3.atCenterOf(middle);
        need(!level.getBlockCollisions(null,query).iterator().hasNext()
                &&NativeLargeDoorTargets.trace(level,p.add(0,2,0),p.add(0,-2,0))==null,"open clears frame middle for vertical motion");
        var visible=level.clip(new ClipContext(p.add(0,2,0),p.add(0,-2,0),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        need(visible.getBlockPos().equals(middle.below()),"vanilla targeting through the open hatch reaches the floor");
        // Only the two independently wet members may claim their vertical source.
        var first=(NativeLargeDoorEntity)level.getBlockEntity(NativeLargeDoorLifecycle.cell(root.getBlockPos(),facing(),true,0,2));
        var second=(NativeLargeDoorEntity)level.getBlockEntity(NativeLargeDoorLifecycle.cell(root.getBlockPos(),facing(),true,2,2));
        var down=first.getBlockPos().below();
        var up=second.getBlockPos().above();
        level.setBlock(down,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        fill(first,0);fill(second,1);
        need(first.waterOutlets()==1&&second.waterOutlets()==2&&root.waterOutlets()==0
                &&level.getFluidState(down).isSource()&&level.getFluidState(up).isSource(),"followers own lower and upper sources separately");
        need(NativeLargeDoorLifecycle.setState(level,root,false,false),"close wetted door");
        first.tickWaterGush();second.tickWaterGush();
        need(first.waterOutlets()==0&&second.waterOutlets()==0&&level.getBlockState(down).getValue(LiquidBlock.LEVEL)==1
                &&level.getBlockState(up).getValue(LiquidBlock.LEVEL)==1,"close retires both owned sources");
        need(NativeLargeDoorLifecycle.setState(level,root,false,true),"reopen wetted door");
        first.tickWaterGush();second.tickWaterGush();
        level.removeBlock(second.getBlockPos(),false);
        need(NativeLargeDoorLifecycle.root(level,middle)==null&&level.getBlockState(down).getValue(LiquidBlock.LEVEL)==1
                &&level.getBlockState(up).getValue(LiquidBlock.LEVEL)==1,"breaking a follower drains both owned sources and removes assembly");
        System.out.println("[vandorlabs][large-door-world-client] horizontal-"+facing().getName()+" PASS side place/saved roles/open via real click/vertical fluid ownership and drain");
    }
    private static void fill(NativeLargeDoorEntity part,int side){
        part.containedFluids().set(side,new FluidStack(Fluids.WATER,1000),part.getLevel().registryAccess());
        part.containedFluidsChanged();
        part.tickWaterGush();
    }
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Horizontal Large Door "+facing()+": "+message);}
}
