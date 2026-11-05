package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.shape.*;
import com.vandorlabs.persistence.PanelGlassSettings;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Registered thin porthole save/configuration/item path; rendered GUI acceptance remains separate. */
@EventBusSubscriber(modid="vandorlabs")
public final class PortholeRegistrationChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError("Registered porthole: "+message);}
    private static final class MenuPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
        private int window;
        MenuPlayer(net.minecraft.server.level.ServerLevel level){super(level,new com.mojang.authlib.GameProfile(java.util.UUID.fromString("094739d2-f482-47af-81da-e4e58f61537b"),"PortholeFixture"));}
        @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer){containerMenu=provider.createMenu(++window,getInventory(),this);require(containerMenu instanceof ProgrammableMenu,"Actual dedicated menu provider");var bytes=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),serverLevel().registryAccess());try{writer.accept(bytes);require(bytes.readBlockPos().equals(((ProgrammableMenu)containerMenu).tile.getBlockPos())&&!bytes.isReadable(),"Exact target position opening data");}finally{bytes.release();}return java.util.OptionalInt.of(window);}
    }
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!System.getProperty("vandorlabs.transmitterPhase","").equals("porthole-registration"))return;var level=event.getServer().overworld();var registries=level.registryAccess();var block=VandorLabsBootstrap.PROGRAMMABLE_PORTHOLE_WALL.get();var pos=new BlockPos(2620,105,2620);level.getChunkAt(pos);require(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString().equals("vandorlabs:programmable_porthole_wall"),"Canonical registry ID");require(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE)&&block.defaultBlockState().is(BlockTags.NEEDS_STONE_TOOL),"Actual harvest tags");
        for(var facing:Direction.Plane.HORIZONTAL)for(int depth=0;depth<3;depth++)for(boolean inverted:new boolean[]{false,true}){var state=block.defaultBlockState().setValue(NativeFlatWallBlock.FACING,facing).setValue(NativeFlatWallBlock.DEPTH,depth).setValue(NativeFlatWallBlock.INVERTED,inverted);level.removeBlock(pos,false);level.setBlock(pos,state,3);require(level.getBlockEntity(pos) instanceof NativeWallEntity host&&host.isPorthole()&&host.wallSettings().joinPortholes,"Actual registered factory, fresh join default");var host=(NativeWallEntity)level.getBlockEntity(pos);require(host.saveWithId(registries).getString("id").equals("vandorlabs:programmable_viewscreen"),"Legacy shared BE ID");require(!state.getCollisionShape(level,pos).isEmpty(),"Actual selection/collision available");}
        var host=(NativeWallEntity)level.getBlockEntity(pos);CompoundTag tag=host.saveWithId(registries);tag.remove("JoinPortholes");tag.putString("FuturePorthole","opaque");host.loadWithComponents(tag,registries);require(!host.wallSettings().joinPortholes,"Missing legacy JoinPortholes loads false");require(host.configurePanelGlass(new PanelGlassSettings(18,129,2,true,3,true,true),new PanelPlayback(2,0),true,3),"Complete saved insert settings");var player=new MenuPlayer(level);player.setGameMode(GameType.CREATIVE);player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2.5);var state=level.getBlockState(pos);var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false);ItemStack picked=block.getCloneItemStack(state,hit,level,pos,player);var data=picked.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);require(picked.is(VandorLabsBootstrap.PROGRAMMABLE_PORTHOLE_WALL_ITEM.get())&&data!=null&&data.copyTag().getString("FuturePorthole").equals("opaque"),"Actual configured pick preserves opaque settings");
        var support=pos.east(3).below();level.setBlock(support,Blocks.STONE.defaultBlockState(),3);level.removeBlock(support.above(),false);player.setItemInHand(InteractionHand.MAIN_HAND,picked.copy());var place=new BlockHitResult(new Vec3(support.getX()+.5,support.getY()+1,support.getZ()+.5),Direction.UP,support,false);require(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,place)).consumesAction(),"Actual configured BlockItem placement");require(level.getBlockEntity(support.above()) instanceof NativeWallEntity placed&&placed.isPorthole()&&placed.panelPlayback().equals(new PanelPlayback(2,0))&&placed.panelGlassSettings().material==129&&placed.wallSettings().portholeShape==3&&placed.saveWithoutMetadata(registries).getString("FuturePorthole").equals("opaque"),"Configured placement retains complete insert and unknown data");
        player.setGameMode(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));for(boolean sneaking:new boolean[]{false,true}){player.setShiftKeyDown(sneaking);player.closeContainer();var result=player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));require(result.consumesAction()&&player.containerMenu instanceof ProgrammableMenu menu&&menu.tile==host&&menu.slots.isEmpty(),"Actual Configurizer normal/sneak opens same slotless menu sneaking="+sneaking+" result="+result+" menu="+player.containerMenu.getClass().getName()+" exactTile="+(level.getBlockEntity(pos)==host)+" canEdit="+NativeConfigurationAccess.canEdit(player,host)+" alive="+player.isAlive()+" held="+player.getMainHandItem());}
        player.setGameMode(GameType.CREATIVE);player.setShiftKeyDown(true);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.closeContainer();require(state.useWithoutItem(level,player,hit).consumesAction()&&player.containerMenu instanceof ProgrammableMenu menu&&menu.tile==host,"Ordinary creative shift-click opens same menu");
        var input=net.minecraft.world.item.crafting.CraftingInput.of(3,1,java.util.List.of(new ItemStack(VandorLabsBootstrap.ALLOY_INGOT.get()),new ItemStack(Items.GLASS_PANE),new ItemStack(VandorLabsBootstrap.ALLOY_INGOT.get())));var recipe=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,level).orElseThrow();var result=recipe.value().assemble(input,registries);require(result.is(VandorLabsBootstrap.PROGRAMMABLE_PORTHOLE_WALL_ITEM.get())&&result.getCount()==2,"Actual canonical recipe result");
        var drops=net.minecraft.world.level.block.Block.getDrops(state,level,pos,host,player,new ItemStack(Items.IRON_PICKAXE));require(drops.size()==1&&drops.getFirst().is(VandorLabsBootstrap.PROGRAMMABLE_PORTHOLE_WALL_ITEM.get())&&drops.getFirst().get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag().getByte("GlassPlaybackMode")==2,"Actual configured loot retains playback");
        System.out.println("[vandorlabs][porthole-registration] dedicated PASS checks="+checks+" 24 states actual factory tags pick/placement loot Configurizer/shift menu; GUI/render acceptance separate");event.getServer().halt(false);
    }
}
