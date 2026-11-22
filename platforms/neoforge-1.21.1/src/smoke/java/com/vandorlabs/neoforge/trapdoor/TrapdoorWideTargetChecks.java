package com.vandorlabs.neoforge.trapdoor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;
import java.util.UUID;

/**
 * Wide joined trapdoor groups, as in Forge's distant-leaf fix: leaves of groups up to eight cells
 * wide can move that far from their own block, and must stay solid and selectable there.
 * Rows of five to eight floor trapdoors are placed through the item, opened, and the leaf that
 * ends farthest from its own block must be found by rays and block collisions.
 */
final class TrapdoorWideTargetChecks {
    private static final BlockPos BASE=new BlockPos(3260,80,3196);
    private static int checks;
    private static void need(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Wide trapdoor targets: "+why);}
    static void run(ServerLevel level){
        var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.fromString("6b1d0d7e-41a5-4c35-9a3e-1f0b3d2c7a11"),"WideTrapdoors"));
        double farthest=0;
        for(int width=5;width<=8;width++){
            BlockPos origin=BASE.offset(0,0,(width-5)*20);
            for(BlockPos at:BlockPos.betweenClosed(origin.offset(-10,-1,-10),origin.offset(18,10,10)))
                level.setBlock(at,at.getY()==origin.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
            for(int x=0;x<width;x++){
                BlockPos pos=origin.east(x);var stack=new ItemStack(NativeTrapdoorRegistrations.ITEM.get());
                actor.setPos(pos.getX()+.5,pos.getY()+2,pos.getZ()-2);actor.setItemInHand(InteractionHand.MAIN_HAND,stack);
                need(stack.useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5),Direction.UP,pos.below(),false))).consumesAction(),"places trapdoor "+x+" of "+width);
            }
            var root=NativeTrapdoorEntity.at(level,origin);
            need(root!=null&&root.group().size()==width,"a row of "+width+" forms one group: "+(root==null?"none":root.group().size()));
            root.requestOpen(true);
            NativeTrapdoorEntity far=null;AABB farBounds=null;double distance=0;
            for(var leaf:root.group()){
                var geometry=leaf.geometry();if(geometry==null)continue;
                AABB bounds=geometry.bounds().move(leaf.position());
                double d=bounds.getCenter().distanceTo(Vec3.atCenterOf(leaf.position()));
                if(d>distance){distance=d;far=leaf;farBounds=bounds;}
            }
            need(far!=null&&distance>2.5,"an opened "+width+"-wide group moves a leaf more than 2.5 blocks from its own block: "+distance);
            farthest=Math.max(farthest,distance);
            // Collision: a small box inside the far leaf's collision.
            var geometry=far.geometry();boolean solid=false,selected=false;
            for(int i=0;i<geometry.collisionCount();i++){
                AABB box=geometry.collision(i).move(far.position());Vec3 c=box.getCenter();
                AABB probe=new AABB(c,c).inflate(Math.min(.05,box.getXsize()/3),Math.min(.05,box.getYsize()/3),Math.min(.05,box.getZsize()/3));
                for(VoxelShape shape:level.getBlockCollisions(null,probe))if(Shapes.joinIsNotEmpty(shape,Shapes.create(probe),BooleanOp.AND))solid=true;
                for(var axis:Direction.Axis.values()){
                    double dx=axis==Direction.Axis.X?3:0,dy=axis==Direction.Axis.Y?3:0,dz=axis==Direction.Axis.Z?3:0;
                    var target=NativeTrapdoorTargets.trace(level,c.add(-dx,-dy,-dz),c.add(dx,dy,dz));
                    if(target!=null&&target.owner().equals(far.position()))selected=true;
                }
            }
            need(solid,"the far leaf of a "+width+"-wide group is solid "+String.format("%.1f",distance)+" blocks from its block at "+farBounds);
            need(selected,"the far leaf of a "+width+"-wide group is selectable "+String.format("%.1f",distance)+" blocks from its block");
            root.requestOpen(false);
        }
        System.out.println("[vandorlabs][trapdoor-targets] wide groups PASS checks="+checks+" 5-8 wide, farthest leaf "+String.format("%.1f",farthest)+" blocks from its block");
    }
    private TrapdoorWideTargetChecks(){}
}
