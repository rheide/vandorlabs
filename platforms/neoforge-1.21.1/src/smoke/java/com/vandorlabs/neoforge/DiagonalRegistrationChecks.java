package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual canonical factories, staged neighbour callbacks and survival item placement. */
@EventBusSubscriber(modid="vandorlabs")
public final class DiagonalRegistrationChecks {
    private static Block probe;private static BlockPos target;private static NativeWallEntity expectedHost;private static Direction expectedFacing;private static int expectedMode,callbacks,checks;private static boolean expectedInverted,observe;
    private static boolean enabled(){return System.getProperty("vandorlabs.transmitterPhase","").equals("diagonal-registration");}
    private static void need(boolean value,String message){checks++;if(!value)throw new AssertionError("Diagonal registration: "+message);}
    private static final class Probe extends Block {
        Probe(){super(Properties.of().noCollission().noOcclusion());}
        @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block changed,BlockPos from,boolean moving){
            if(!observe)return;callbacks++;need(level.getBlockEntity(target)==expectedHost,"neighbour retains configured host identity");var snapshot=expectedHost.wallAppearance();need(snapshot.walls()==expectedHost.wallSettings()&&snapshot.walls().halfHeight==(expectedMode==2)&&snapshot.walls().fullWidth==(expectedMode!=0),"neighbour sees complete normalized settings");var installed=level.getBlockState(target);need(installed.getValue(NativeDiagonalWallBlock.FACING)==expectedFacing&&installed.getValue(NativeDiagonalWallBlock.INVERTED)==expectedInverted,"neighbour sees complete staged facing");
        }
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(enabled())event.register(Registries.BLOCK,helper->{probe=new Probe();helper.register(ResourceLocation.fromNamespaceAndPath("vandorlabs","diagonal_neighbor_probe"),probe);});}
    }
    private static final class Throwing extends NativeWallEntity {
        Throwing(BlockPos pos,BlockState state){super(VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get(),pos,state,false);}
        @Override protected boolean stageDiagonalState(BlockState next){super.stageDiagonalState(next);throw new IllegalStateException("fixture stage failed after real state publication");}
    }
    @SubscribeEvent public static void start(ServerStartedEvent event){if(!enabled())return;
        var level=event.getServer().overworld();target=new BlockPos(3000,100,3000);level.getChunkAt(target);var raw=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_diagonal_wall"));need(raw instanceof NativeDiagonalWallBlock,"canonical diagonal family activated");var block=(NativeDiagonalWallBlock)raw;need(block.asItem() instanceof NativeDiagonalWallItem,"dedicated proportions item");int cases=0;
        for(Direction facing:Direction.Plane.HORIZONTAL)for(boolean inverted:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(int fill=0;fill<4;fill++){
            var state=block.defaultBlockState().setValue(NativeDiagonalWallBlock.FACING,facing).setValue(NativeDiagonalWallBlock.INVERTED,inverted);level.setBlock(target,state,3);var host=(NativeWallEntity)level.getBlockEntity(target);need(host!=null&&host.getType()==VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get()&&!host.isPorthole(),"canonical shared factory");need(host.configureDiagonal(mode,fill,0,facing,inverted),"actual installed geometry edit");host.refreshFluidGeometry();var geometry=(NativeDiagonalWallGeometry.Geometry)host.wallAppearance().geometry();need(geometry.pose().mode()==mode&&geometry.pose().fill()==fill&&geometry.facing()==facing&&geometry.pose().inverted()==inverted,"deferred owning-thread geometry reflects complete settings");need(!state.getCollisionShape(level,target).isEmpty(),"actual collision available");for(Direction side:Direction.values())need(!state.isFaceSturdy(level,target,side,SupportType.CENTER),"legacy non-solid panel attachment face");level.removeBlock(target,false);cases++;
        }
        level.setBlock(target,block.defaultBlockState(),3);expectedHost=(NativeWallEntity)level.getBlockEntity(target);level.setBlock(target.east(),probe.defaultBlockState(),3);expectedMode=2;expectedFacing=Direction.WEST;expectedInverted=true;observe=true;need(expectedHost.configureDiagonal(expectedMode,3,7,expectedFacing,expectedInverted),"actual state-setting edit");observe=false;need(callbacks>0,"actual neighbour callback observed final publication");level.removeBlock(target.east(),false);
        var state=level.getBlockState(target);var tag=expectedHost.saveWithoutMetadata(level.registryAccess());tag.putInt("HousingTextureVersion",1);tag.putInt("housingTexture",7);tag.putString("foreign_owner","retained");expectedHost.loadWithComponents(tag,level.registryAccess());var picked=block.getCloneItemStack(state,new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false),level,target,null);need(picked.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag().getString("foreign_owner").equals("retained"),"configured pick opaque data");var drops=Block.getDrops(state,level,target,expectedHost,null,new ItemStack(Items.IRON_PICKAXE));need(drops.size()==1&&drops.getFirst().is(block.asItem())&&drops.getFirst().get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)!=null,"configured loot");
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.SURVIVAL);player.setPos(target.getX()+.5,target.getY()+1,target.getZ()+3);BlockPos placed=target.above();
        for(boolean configured:new boolean[]{false,true}){
            level.removeBlock(placed,false);var held=configured?picked.copyWithCount(2):new ItemStack(block.asItem(),2);if(configured){var data=held.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag();data.putBoolean("DiagonalHalfHeight",false);data.putBoolean("DiagonalFullWidth",true);held.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,net.minecraft.world.item.component.CustomData.of(data));}
            player.setItemInHand(InteractionHand.MAIN_HAND,held);var result=held.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(target.getX()+.5,target.getY()+1,target.getZ()+.5),Direction.UP,target,false)));need(result.consumesAction()&&held.getCount()==1&&level.getBlockState(placed).is(block),"actual survival placement consumes one");var host=(NativeWallEntity)level.getBlockEntity(placed);need(host.wallSettings().halfHeight==!configured&&host.wallSettings().fullWidth,"plain inherits proportions; configured keeps own");if(configured)need(host.saveWithoutMetadata(level.registryAccess()).getString("foreign_owner").equals("retained"),"configured placement retains opaque fields");level.removeBlock(placed,false);
        }
        level.removeBlock(target,false);level.setBlock(target,block.defaultBlockState(),3);var throwing=new Throwing(target,block.defaultBlockState());level.setBlockEntity(throwing);var before=throwing.wallAppearance();boolean failed=false;try{throwing.configureDiagonal(2,3,7,Direction.EAST,true);}catch(IllegalStateException expected){failed=true;}need(failed&&level.getBlockEntity(target)==throwing&&level.getBlockState(target)==block.defaultBlockState()&&throwing.wallAppearance()==before&&throwing.wallSettings()==before.walls(),"installed post-state-stage exception restores original complete state");
        System.out.println("[vandorlabs][diagonal-registration] dedicated PASS cases="+cases+" checks="+checks+" actual factories geometry callbacks configured pick/loot survival proportions and installed rollback");event.getServer().halt(false);
    }
}
