package com.vandorlabs.neoforge.trapdoor;

import com.mojang.serialization.MapCodec;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.fluids.FluidStack;

/** Private housing only; production host, world fluid publication and BER are unchanged. */
@EventBusSubscriber(modid="vandorlabs")
public final class TrapdoorFluidWorldFixture {
    public static final BlockPos POS=BlockPos.ZERO;
    public static Housing BLOCK;
    public static BlockEntityType<NativeTrapdoorEntity> TYPE;
    public static boolean enabled(){return "trapdoor-wet-world-client".equals(System.getProperty("vandorlabs.transmitterPhase"));}
    public static ResourceLocation id(){return ResourceLocation.fromNamespaceAndPath("vandorlabs","trapdoor_fluid_world_fixture");}
    public static final class Housing extends NativeTrapdoorBlock {
        Housing(){super(BlockBehaviour.Properties.of().strength(2.5F).noOcclusion().dynamicShape(),true);}
        protected BlockEntityType<?> entityType(){return TYPE;}
        protected MapCodec<? extends BaseEntityBlock> codec(){return MapCodec.unit(this);}
        @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    }
    @EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(!enabled())return;event.register(Registries.BLOCK,h->{BLOCK=new Housing();h.register(id(),BLOCK);});event.register(Registries.BLOCK_ENTITY_TYPE,h->{TYPE=BlockEntityType.Builder.of((pos,state)->new NativeTrapdoorEntity(TYPE,pos,state),BLOCK).build(null);h.register(id(),TYPE);});}
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!enabled()||!(event.getEntity() instanceof ServerPlayer player))return;var level=player.serverLevel();level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,level.getServer());level.setDayTime(6000);level.setWeatherParameters(0,6000,false,false);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=-2;y<=5;y++)level.setBlock(new BlockPos(x,y,z),y==-2?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18);
        level.setBlock(POS,BLOCK.defaultBlockState().setValue(NativeTrapdoorBlock.FACING,Direction.SOUTH),18);var tile=(NativeTrapdoorEntity)level.getBlockEntity(POS);var tag=new CompoundTag();tag.putInt("TrapdoorPosition",0);tag.putInt("TrapdoorTrigger",0);tag.putBoolean("TrapdoorSlideIntoWall",true);tag.putBoolean("DiagonalRotationFlipped",true);tag.putInt("DiagonalHingeSide",2);tile.loadWithComponents(tag,level.registryAccess());tile.onLoad();tile.tickServer();tile.requestOpen(true);tile.containedFluids().set(0,new FluidStack(Fluids.WATER,1000),level.registryAccess());tile.containedFluids().set(1,new FluidStack(Fluids.WATER,1000),level.registryAccess());NativeFluidWorld.changed(tile);level.sendBlockUpdated(POS,tile.getBlockState(),tile.getBlockState(),2);
        if(tile.fluidVolumeSnapshot().cells().size()!=71710)throw new AssertionError("Small-coordinate wet Source profile cells="+tile.fluidVolumeSnapshot().cells().size()+" pose="+tile.trapdoorVisual().pose());
        player.setGameMode(GameType.CREATIVE);player.getInventory().clearContent();player.getInventory().selected=0;player.getAbilities().flying=true;player.onUpdateAbilities();double x=.5,y=1,z=-3;Vec3 target=new Vec3(.5,.4,.5);double dx=target.x-x,dz=target.z-z,dy=target.y-(y+player.getEyeHeight());float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));player.connection.teleport(x,y,z,yaw,pitch);player.inventoryMenu.broadcastChanges();System.out.println("[vandorlabs][trapdoor-fluid-world] server READY cells=71710 bounded analytic regions private housing");
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(enabled())System.out.println("[vandorlabs][trapdoor-fluid-world] logout PASS");}
}
