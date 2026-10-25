package com.vandorlabs.neoforge.wall;

import com.vandorlabs.neoforge.VandorLabsModern;
import com.vandorlabs.persistence.WallAppearance;
import com.vandorlabs.ui.WallDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Real installed profiles, independently authorized commands and complete saved item snapshots. */
public final class WallRuntimeChecks {
    private static final BlockPos[] POS={new BlockPos(320,80,8),new BlockPos(324,80,8),new BlockPos(328,80,8)};
    private WallRuntimeChecks(){}
    private static void need(boolean value,String message){if(!value)throw new IllegalStateException("Modern flat wall: "+message);}
    private static WallBlock block(int index){return switch(index){case 0->WallRegistration.WALL.get();case 1->WallRegistration.PORTHOLE.get();default->WallRegistration.FULL_PORTHOLE.get();};}
    private static WallEntity install(ServerLevel level,BlockPos pos,BlockState state){level.getChunkAt(pos);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);for(Direction side:Direction.values())level.setBlock(pos.relative(side),Blocks.AIR.defaultBlockState(),3);level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,state,3);need(level.getBlockEntity(pos) instanceof WallEntity,"registered entity");return (WallEntity)level.getBlockEntity(pos);}
    private static WallDialog.State state(int main,int side,int layout){return new WallDialog.State(main,side,layout,-1,new WallAppearance(18,0,150,false,true,false,false,3,2));}
    private static boolean canHoldFluid(ServerLevel level,BlockPos pos,net.minecraft.world.level.material.Fluid fluid){
        try{var method=net.minecraft.world.level.material.FlowingFluid.class.getDeclaredMethod("canHoldFluid",net.minecraft.world.level.BlockGetter.class,BlockPos.class,BlockState.class,net.minecraft.world.level.material.Fluid.class);method.setAccessible(true);return (boolean)method.invoke(fluid,level,pos,level.getBlockState(pos),fluid);}catch(ReflectiveOperationException error){throw new IllegalStateException("Native fluid admission",error);}
    }
    public static void run(ServerLevel level){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.CREATIVE);player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        int poses=0,commands=0;
        for(int family=0;family<3;family++){
            var tile=install(level,POS[family],block(family).defaultBlockState());var fresh=tile.wallSettings();
            need(fresh.main==0&&fresh.side==-1&&fresh.layout==0&&fresh.appearance.textureLayout()==1&&fresh.appearance.join==(family!=0),"authored fresh profile defaults");
            need(tile.getBlockState().blocksMotion()&&tile.getBlockState().getDestroySpeed(level,POS[family])==5&&tile.getBlockState().requiresCorrectToolForDrops(),"solid metal material and mining policy");
            need(!canHoldFluid(level,POS[family],net.minecraft.world.level.material.Fluids.WATER)&&!canHoldFluid(level,POS[family],net.minecraft.world.level.material.Fluids.LAVA),"vanilla fluid cannot replace iron wall hosts");
            need(tile.getBlockState().getLightBlock(level,POS[family])==0&&tile.getBlockState().getBlockSupportShape(level,POS[family]).isEmpty(),"transparent lighting and non-supporting faces");
            for(Direction facing:Direction.Plane.HORIZONTAL)for(int depth=0;depth<3;depth++){
                var pose=block(family).defaultBlockState().setValue(WallBlock.FACING,facing).setValue(WallBlock.DEPTH,depth);level.setBlock(POS[family],pose,3);tile=(WallEntity)level.getBlockEntity(POS[family]);tile.refreshFluidGeometry();
                var shape=pose.getCollisionShape(level,POS[family]);need(!shape.isEmpty(),"installed collision");
                double volume=0;for(var box:shape.toAabbs())volume+=(box.maxX-box.minX)*(box.maxY-box.minY)*(box.maxZ-box.minZ);
                need(Math.abs(volume-(family==2?1:.25))<1e-12,"exact isolated quarter wall/full cube collision volume");
                need(tile.fluidVolumeSnapshot().cells.isEmpty()==(family==2),"full porthole excludes free fluid volume");
                if(family!=2){var bounds=shape.bounds();double low=depth==0?6D/16:depth==1?0:12D/16;double min=facing.getAxis()==Direction.Axis.Z?bounds.minZ:bounds.minX;double expected=facing==Direction.SOUTH||facing==Direction.EAST?1-low-.25:low;need(Math.abs(min-expected)<1e-12,"depth and facing coordinates");}
                poses++;
            }
            tile=install(level,POS[family],block(family).defaultBlockState());player.setPos(POS[family].getX()+.5,80,5);var menu=new WallMenu(221,player.getInventory(),POS[family]);player.containerMenu=menu;var configured=state(5,6,2);
            for(var effect:new WallDialog.Effect[]{WallDialog.Effect.MAIN,WallDialog.Effect.SIDE,WallDialog.Effect.LAYOUT,WallDialog.Effect.APPEARANCE}){
                var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
                try{WallPayload.CODEC.encode(buffer,new WallPayload(221,POS[family],configured,effect));need(buffer.readableBytes()<=34,"bounded encoded command");var decoded=WallPayload.CODEC.decode(buffer);need(!buffer.isReadable()&&decoded.menuId()==221&&decoded.pos().equals(POS[family])&&decoded.effect()==effect&&decoded.settings().main==5&&decoded.settings().side==6&&decoded.settings().appearance.material==150&&decoded.settings().appearance.shape==3,"actual codec scalar roundtrip");}finally{buffer.release();}
            }
            need(WallPayload.apply(player,new WallPayload(221,POS[family],configured,WallDialog.Effect.MAIN)),"main accepted");
            need(tile.wallSettings().main==5&&tile.wallSettings().layout==0&&tile.wallSettings().side==-1&&tile.wallSettings().appearance.join==(family!=0),"main command preserves concurrent appearance/layout");
            need(WallPayload.apply(player,new WallPayload(221,POS[family],configured,WallDialog.Effect.LAYOUT)),"layout accepted");
            need(WallPayload.apply(player,new WallPayload(221,POS[family],configured,WallDialog.Effect.SIDE))==(family!=0),"profile side permission");
            need(WallPayload.apply(player,new WallPayload(221,POS[family],configured,WallDialog.Effect.APPEARANCE))==(family!=0),"profile appearance permission");
            for(var effect:new WallDialog.Effect[]{WallDialog.Effect.NONE,WallDialog.Effect.CLOSE})need(!WallPayload.apply(player,new WallPayload(221,POS[family],configured,effect)),"non-command rejected");
            need(!WallPayload.apply(player,new WallPayload(222,POS[family],configured,WallDialog.Effect.MAIN))&&!WallPayload.apply(player,new WallPayload(221,POS[family].east(),configured,WallDialog.Effect.MAIN)),"wrong session/position rejected");
            need(!WallPayload.apply(player,new WallPayload(221,POS[family],state(5,6,3),WallDialog.Effect.LAYOUT)),"invalid layout rejected");
            player.setPos(1000,80,1000);need(!WallPayload.apply(player,new WallPayload(221,POS[family],configured,WallDialog.Effect.MAIN)),"distance rejected");player.setPos(POS[family].getX()+.5,80,5);
            player.setGameMode(GameType.SURVIVAL);need(!WallPayload.apply(player,new WallPayload(221,POS[family],configured,WallDialog.Effect.MAIN)),"unauthorized survival rejected");player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsModern.CONFIGURIZER.get()));need(WallPayload.apply(player,new WallPayload(221,POS[family],configured,WallDialog.Effect.MAIN)),"Configurizer accepted");player.setGameMode(GameType.CREATIVE);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var saved=tile.saveWithoutMetadata(level.registryAccess());var opaque=new net.minecraft.nbt.CompoundTag();opaque.putString("owner","retained-wall");opaque.putIntArray("values",new int[]{7,2,19});saved.put("FutureSettings",opaque);saved.putIntArray("FaceTextures",new int[]{1,2,3,4,-1,6});saved.putBoolean("FaceTexturesEnabled",false);var fluid=new net.minecraft.nbt.CompoundTag();fluid.putString("FluidName","missing_test:wall_fluid");fluid.putInt("Amount",1000);fluid.put("Tag",opaque.copy());var slots=new net.minecraft.nbt.CompoundTag();slots.put("Side1",fluid);saved.put("ContainedFluids",slots);tile.loadWithComponents(saved,level.registryAccess());
            var picked=block(family).getCloneItemStack(tile.getBlockState(),new BlockHitResult(Vec3.atCenterOf(POS[family]),Direction.NORTH,POS[family],false),level,POS[family],player);var drops=Block.getDrops(tile.getBlockState(),level,POS[family],tile,player,new ItemStack(Items.IRON_PICKAXE));need(drops.size()==1,"single configured drop");
            for(var stack:java.util.List.of(picked,drops.get(0))){var component=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);need(component!=null,"item settings component");var tag=component.copyTag();need(tag.getCompound("FutureSettings").equals(opaque)&&tag.getCompound("ContainedFluids").getCompound("Side1").equals(fluid)&&java.util.Arrays.equals(tag.getIntArray("FaceTextures"),new int[]{1,2,3,4,-1,6})&&tag.getInt("housingTexture")==5,"complete opaque/dormant item state");}
            need(tile.containedFluids().occupied(1)&&tile.containedFluids().get(1).isEmpty(),"unresolved fluid retained inert");
            install(level,POS[family],block(family).defaultBlockState());need(!WallPayload.apply(player,new WallPayload(221,POS[family],configured,WallDialog.Effect.MAIN)),"replacement invalidates stale menu");
            tile=(WallEntity)level.getBlockEntity(POS[family]);tile.loadWithComponents(saved,level.registryAccess());commands+=13;
        }
        joined(level);
        placement(level,player);
        player.containerMenu=player.inventoryMenu;
        System.out.println("[vandorlabs][modern] wall-write PASS "+poses+" installed collision/fluid poses, "+commands+" command/session/identity checks and complete configured snapshots");
    }
    private static void joined(ServerLevel level){
        BlockPos root=new BlockPos(348,80,8);int samples=0;
        for(Direction facing:Direction.Plane.HORIZONTAL){
            var owner=block(0).defaultBlockState().setValue(WallBlock.FACING,facing);install(level,root,owner);
            var arm=block(0).defaultBlockState().setValue(WallBlock.FACING,facing.getClockWise());
            for(int junction=0;junction<4;junction++){
                level.setBlock(root.relative(facing),arm,3);
                if(junction>=1)level.setBlock(root.relative(facing.getOpposite()),arm,3);
                if(junction>=2)level.setBlock(root.relative(facing.getCounterClockWise()),block(2).defaultBlockState().setValue(WallBlock.FACING,facing),3);
                if(junction>=3)level.setBlock(root.relative(facing.getClockWise()),owner,3);
                var pose=WallGeometry.live(owner,level,root);need(pose.corner()!=null,"real adjacent arm recognized");
                need(com.vandorlabs.render.FlatWallTopology.bar(pose.corner())==(junction==2),"actual T-bar ownership including full porthole straight target");
                var boxes=owner.getCollisionShape(level,root).toAabbs();var tile=(WallEntity)level.getBlockEntity(root);tile.refreshFluidGeometry();
                for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                    double localX=(x+.5)/16,localZ=(z+.5)/16,worldX=localX,worldZ=localZ;
                    if(facing==Direction.SOUTH){worldX=1-localX;worldZ=1-localZ;}else if(facing==Direction.EAST){worldX=1-localZ;worldZ=localX;}else if(facing==Direction.WEST){worldX=localZ;worldZ=1-localX;}
                    boolean expected=(z>=6&&z<10&&x>=(junction>=2?0:6)&&x<(junction==0||junction==3?16:10))||(x>=6&&x<10&&(z<6||junction>=1&&z>=10));
                    boolean solid=false;for(var box:boxes)if(box.contains(worldX,.5,worldZ)){solid=true;break;}
                    need(solid==expected,"native joined collision matches independent rotated L/T/cross occupancy");
                    need((tile.fluidVolumeSnapshot().sideAt(worldX,.5,worldZ)>=0)!=solid,"joined fluid free volume complements authoritative solid");samples++;
                }
            }
        }
        System.out.println("[vandorlabs][modern] wall-joins PASS "+samples+" real installed L/bar/T/cross collision/free-volume samples and full-depth straight targets");
    }
    private static void placement(ServerLevel level,net.minecraft.server.level.ServerPlayer player){
        BlockPos target=new BlockPos(344,80,8);level.getChunkAt(target);level.setBlock(target,Blocks.AIR.defaultBlockState(),3);level.setBlock(target.below(),Blocks.STONE.defaultBlockState(),3);
        for(int family=0;family<3;family++)for(Direction facing:Direction.Plane.HORIZONTAL)for(int depth=0;depth<3;depth++){
            player.setYRot(facing.getOpposite().toYRot());player.setPos(344.5,81,5);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(block(family)));
            double local=depth==0?.5:depth==1?.125:.875;double x=.5,z=.5;
            if(facing==Direction.NORTH)z=local;if(facing==Direction.SOUTH)z=1-local;if(facing==Direction.EAST)x=1-local;if(facing==Direction.WEST)x=local;
            var hit=new BlockHitResult(new Vec3(target.getX()+x,target.getY(),target.getZ()+z),Direction.UP,target.below(),false);
            var context=new net.minecraft.world.item.context.BlockPlaceContext(player,InteractionHand.MAIN_HAND,player.getMainHandItem(),hit);var placed=block(family).getStateForPlacement(context);
            need(context.getClickedPos().equals(target)&&placed.getValue(WallBlock.FACING)==facing&&placed.getValue(WallBlock.DEPTH)==(family==2?0:depth),"real native placement context facing/depth");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
    }
    public static void prepareRead(ServerLevel level){for(BlockPos pos:POS)level.getChunkAt(pos);}
    public static void read(ServerLevel level){for(int i=0;i<3;i++){need(level.getBlockEntity(POS[i]) instanceof WallEntity,"saved profile tile");var tile=(WallEntity)level.getBlockEntity(POS[i]);need(tile.profile()==WallDialog.Profile.values()[i]&&tile.wallSettings().main==5&&tile.wallSettings().layout==2,"profile settings restart");var tag=tile.saveWithoutMetadata(level.registryAccess());need(tag.getCompound("FutureSettings").getString("owner").equals("retained-wall")&&tile.containedFluids().occupied(1),"opaque snapshot restart");if(i!=0)need(tile.wallSettings().side==6&&tile.wallSettings().appearance.material==150&&tile.wallSettings().appearance.shape==3,"porthole appearance restart");}System.out.println("[vandorlabs][modern] wall-read PASS all profiles, material/appearance, dormant choices and opaque fluid across process restart");}
    public static void clientFixture(ServerLevel level){for(int x=312;x<=360;x++)for(int z=-7;z<=14;z++)level.setBlock(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);for(int i=0;i<3;i++)install(level,POS[i],block(i).defaultBlockState());}
}
