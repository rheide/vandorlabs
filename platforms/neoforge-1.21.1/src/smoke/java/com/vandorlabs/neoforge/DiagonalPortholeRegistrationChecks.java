package com.vandorlabs.neoforge;

import com.google.gson.*;
import com.vandorlabs.neoforge.shape.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Installed canonical diagonal insert; actual Forge geometry input and saved-world restart. */
@EventBusSubscriber(modid="vandorlabs")
public final class DiagonalPortholeRegistrationChecks {
 private static final BlockPos POS=new BlockPos(2720,105,2720),PLACED=POS.west(5),MISSING=POS.east(5);
 private static int checks;
 private static void need(boolean ok,String message){checks++;if(!ok)throw new AssertionError("Diagonal porthole: "+message);}
 private static AABB box(JsonArray a){return new AABB(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble());}
 private static void saved(NativeWallEntity host){
  need(host.isPorthole()&&host.wallSettings().halfHeight&&!host.wallSettings().fullWidth&&host.wallSettings().joinPortholes&&host.wallSettings().portholeShape==3,"raw half/full and aperture settings");
  var tag=host.saveWithoutMetadata(host.getLevel().registryAccess());need(tag.getString("FutureDiagonalPorthole").equals("opaque")&&tag.getCompound("ForgeCaps").getIntArray("opaque")[1]==Integer.MAX_VALUE,"unknown and capability data retained");
 }
 private static void opaque(NativeWallEntity host){
  var tag=host.saveWithId(host.getLevel().registryAccess());tag.putBoolean("DiagonalHalfHeight",true);tag.putBoolean("DiagonalFullWidth",false);tag.putBoolean("JoinPortholes",true);tag.putInt("PortholeShape",3);tag.putString("FutureDiagonalPorthole","opaque");var caps=new CompoundTag();caps.putIntArray("opaque",new int[]{-9,Integer.MAX_VALUE});tag.put("ForgeCaps",caps);host.loadWithComponents(tag,host.getLevel().registryAccess());host.refreshFluidGeometry();saved(host);
 }
 @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
  String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!phase.equals("diagonal-porthole-write")&&!phase.equals("diagonal-porthole-read")&&!phase.equals("diagonal-glass-contract"))return;
  var level=event.getServer().overworld();var id=ResourceLocation.fromNamespaceAndPath("vandorlabs","programmable_diagonal_porthole");need(BuiltInRegistries.BLOCK.containsKey(id)&&BuiltInRegistries.ITEM.containsKey(id),"canonical IDs registered without AIR fallback");var raw=BuiltInRegistries.BLOCK.get(id);need(raw instanceof NativeDiagonalWallBlock b&&b.isPorthole(),"actual diagonal porthole class");var block=(NativeDiagonalWallBlock)raw;var item=BuiltInRegistries.ITEM.get(id);need(item instanceof NativeDiagonalWallItem&&((BlockItem)item).getBlock()==block,"proportion-aware canonical item");need(block.getStateDefinition().getPossibleStates().size()==8,"eight retained facing/inversion states");for(var p:java.util.List.of(POS,PLACED,MISSING))level.getChunkAt(p);
  if(phase.equals("diagonal-glass-contract")){NativeDiagonalGlassChecks.run(block);System.out.println("[vandorlabs][diagonal-glass-contract] dedicated PASS");event.getServer().halt(false);return;}
  if(phase.endsWith("read")){
   for(var p:java.util.List.of(POS,PLACED)){need(level.getBlockState(p).is(block),"saved canonical state");var host=(NativeWallEntity)level.getBlockEntity(p);saved(host);host.refreshFluidGeometry();need(host.wallAppearance().aperture().shape()==3&&host.wallAppearance().aperture().extraBorder()==0,"reloaded immutable aperture descriptor");}
   need(!((NativeWallEntity)level.getBlockEntity(MISSING)).wallSettings().joinPortholes,"missing saved Join remains false across restart");
  }else{
   JsonObject reference;try(var reader=Files.newBufferedReader(Paths.get(System.getProperty("vandorlabs.diagonalPortholeReference")))){reference=JsonParser.parseReader(reader).getAsJsonObject();}
   need(reference.get("golden").getAsString().equals("e5e8e78899db52f45e7c3f74888b9b39eb8e6dc1f92722206c24beedee9e2062"),"actual frozen Forge collision/selection input");int cases=0;
   for(var value:reference.getAsJsonArray("cases")){
    var c=value.getAsJsonObject();int profile=c.get("profile").getAsInt();if(!c.get("inset").getAsBoolean()||c.get("joinChoice").getAsInt()!=0||profile!=0&&(profile!=6||c.get("mode").getAsInt()!=1||c.get("fill").getAsInt()!=0))continue;
    for(Direction d:Direction.values())level.removeBlock(POS.relative(d),false);level.removeBlock(POS,false);var facing=Direction.byName(c.get("facing").getAsString());boolean inverted=c.get("inverted").getAsBoolean();var state=block.defaultBlockState().setValue(NativeDiagonalWallBlock.FACING,facing).setValue(NativeDiagonalWallBlock.INVERTED,inverted);level.setBlock(POS,state,3);var host=(NativeWallEntity)level.getBlockEntity(POS);need(host!=null&&host.isPorthole()&&host.getType()==VandorLabsBootstrap.PROGRAMMABLE_ENTITY.get()&&host.getType().isValid(state)&&host.wallSettings().joinPortholes,"real shared factory and fresh Join default");need(host.saveWithId(level.registryAccess()).getString("id").equals("vandorlabs:programmable_viewscreen"),"retained BE ID");
    int mode=c.get("mode").getAsInt(),fill=c.get("fill").getAsInt();need(host.configureDiagonal(mode,fill,0,facing,inverted),"installed mode/fill edit");if(profile==6){var right=facing.getClockWise();for(var d:new Direction[]{facing,facing.getOpposite(),right}){var p=POS.relative(d);var f=d==facing?right:d==facing.getOpposite()?right.getOpposite():facing;level.setBlock(p,block.defaultBlockState().setValue(NativeDiagonalWallBlock.FACING,f).setValue(NativeDiagonalWallBlock.INVERTED,inverted),3);((NativeWallEntity)level.getBlockEntity(p)).configureDiagonal(1,0,0,f,inverted);}}host.refreshFluidGeometry();var geometry=host.wallAppearance().geometry();need(geometry.selectionBounds().equals(box(c.getAsJsonArray("selection"))),"actual Forge broad selection bounds");var expected=Shapes.empty();for(var a:c.getAsJsonArray("boxes"))expected=Shapes.joinUnoptimized(expected,Shapes.create(box(a.getAsJsonArray())),BooleanOp.OR);var collision=state.getCollisionShape(level,POS);need(!Shapes.joinIsNotEmpty(expected,collision,BooleanOp.NOT_SAME),"mapped detailed collision occupancy");need(!Shapes.joinIsNotEmpty(collision,state.getShape(level,POS),BooleanOp.NOT_SAME),"detailed player ray shape retained");if(profile==6){var j=((NativeDiagonalWallGeometry.Geometry)geometry).pose().join();need(j!=null&&Boolean.TRUE.equals(j.frontRight())&&Boolean.FALSE.equals(j.backRight())&&!j.straightLeft()&&j.straightRight(),"actual installed solid-cross topology matches Forge profile");}var rendered=host.renderBounds();var selection=geometry.selectionBounds().move(POS);need(rendered.minX<=selection.minX&&rendered.minY<=selection.minY&&rendered.minZ<=selection.minZ&&rendered.maxX>=selection.maxX&&rendered.maxY>=selection.maxY&&rendered.maxZ>=selection.maxZ,"render box encloses displayed selection");for(var a:c.getAsJsonArray("boxes")){var b=box(a.getAsJsonArray()).move(POS);need(rendered.minX<=b.minX&&rendered.minY<=b.minY&&rendered.minZ<=b.minZ&&rendered.maxX>=b.maxX&&rendered.maxY>=b.maxY&&rendered.maxZ>=b.maxZ,"render box encloses physical overhang");}
    var old=host.wallAppearance().aperture();for(int shape=0;shape<4;shape++){need(host.configurePanelGlass(host.panelGlassSettings(),false,shape),"aperture edit");host.refreshFluidGeometry();var pane=host.wallAppearance().aperture();need(pane.shape()==shape&&pane.extraBorder()==0&&pane.pane().equals(NativePanelLayout.UNIT)&&!pane.solid(),"server immutable unit descriptor (client topology separate)");}need(old.shape()==0,"previous descriptor remains immutable");cases++;
   }need(cases==104,"eight states x three modes x four fills plus eight actual solid crossings from Forge");
   level.removeBlock(POS,false);level.setBlock(POS,block.defaultBlockState().setValue(NativeDiagonalWallBlock.FACING,Direction.WEST).setValue(NativeDiagonalWallBlock.INVERTED,true),3);var host=(NativeWallEntity)level.getBlockEntity(POS);opaque(host);var player=new ProgrammableFixturePlayer(level,DiagonalPortholeRegistrationChecks::need);player.setGameMode(GameType.SURVIVAL);player.setPos(POS.getX()+.5,POS.getY()+1,POS.getZ()+3);var hit=new BlockHitResult(Vec3.atCenterOf(POS),Direction.UP,POS,false);var picked=block.getCloneItemStack(level.getBlockState(POS),hit,level,POS,player);need(picked.is(item)&&picked.get(DataComponents.BLOCK_ENTITY_DATA)!=null,"configured canonical pick");
   for(boolean configured:new boolean[]{false,true}){
    var target=POS.above();level.removeBlock(target,false);var stack=configured?picked.copyWithCount(2):new ItemStack(item,2);if(configured){var tag=stack.get(DataComponents.BLOCK_ENTITY_DATA).copyTag();tag.putBoolean("DiagonalHalfHeight",false);tag.putBoolean("DiagonalFullWidth",true);stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(tag));}player.setItemInHand(InteractionHand.MAIN_HAND,stack);need(stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(POS.getX()+.5,POS.getY()+1,POS.getZ()+.5),Direction.UP,POS,false))).consumesAction()&&stack.getCount()==1,"real survival placement consumes one");var placed=(NativeWallEntity)level.getBlockEntity(target);need(placed.wallSettings().halfHeight==!configured&&placed.wallSettings().fullWidth,"plain inherits half size; configured keeps full size");if(configured)need(placed.saveWithoutMetadata(level.registryAccess()).getString("FutureDiagonalPorthole").equals("opaque"),"configured placement opaque data");level.removeBlock(target,false);
   }
   level.setBlock(PLACED.below(),Blocks.STONE.defaultBlockState(),3);level.removeBlock(PLACED,false);player.setPos(PLACED.getX()+.5,PLACED.getY(),PLACED.getZ()+3);player.setItemInHand(InteractionHand.MAIN_HAND,picked.copy());need(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(PLACED.below()).add(0,.5,0),Direction.UP,PLACED.below(),false))).consumesAction(),"configured restart sample placed through item");saved((NativeWallEntity)level.getBlockEntity(PLACED));
   level.setBlock(MISSING,block.defaultBlockState(),3);var missing=(NativeWallEntity)level.getBlockEntity(MISSING);var tag=missing.saveWithId(level.registryAccess());tag.remove("JoinPortholes");missing.loadWithComponents(tag,level.registryAccess());need(!missing.wallSettings().joinPortholes,"missing saved Join differs from fresh default");missing.setChanged();
   player.setPos(POS.getX()+.5,POS.getY(),POS.getZ()+2);var menuHit=new BlockHitResult(Vec3.atCenterOf(POS),Direction.NORTH,POS,false);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));for(boolean shift:new boolean[]{false,true}){player.setShiftKeyDown(shift);player.closeContainer();need(player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,menuHit)).consumesAction()&&player.containerMenu instanceof ProgrammableMenu m&&m.tile==host&&m.slots.isEmpty(),"tool opens same dedicated slotless menu");}player.setGameMode(GameType.CREATIVE);player.setShiftKeyDown(true);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.closeContainer();need(level.getBlockState(POS).useWithoutItem(level,player,menuHit).consumesAction()&&player.containerMenu instanceof ProgrammableMenu m&&m.tile==host,"ordinary shift opens same dialog");var drops=Block.getDrops(level.getBlockState(POS),level,POS,host,player,new ItemStack(Items.IRON_PICKAXE));need(drops.size()==1&&drops.getFirst().is(item)&&drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA).copyTag().getString("FutureDiagonalPorthole").equals("opaque"),"canonical configured loot");host.setChanged();
  }
  System.out.println("[vandorlabs]["+phase+"] dedicated PASS checks="+checks+" actual Forge geometry, canonical registry/item/menu and opaque saved restart; client solid/group/render separate");event.getServer().halt(false);
 }
}
