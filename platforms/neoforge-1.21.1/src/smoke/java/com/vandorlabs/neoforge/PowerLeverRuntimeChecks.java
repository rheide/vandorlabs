package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.*;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.neoforge.redstone.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Four actual retained lever blocks, their shared host and real recipe/loot/item adapters. */
@EventBusSubscriber(modid="vandorlabs")
public final class PowerLeverRuntimeChecks {
    private static final BlockPos POS=new BlockPos(4200,100,4200);private static int checks;private static ServerLevel world;private static ServerPlayer player;
    private static void require(boolean value,String message){checks++;if(!value)throw new IllegalStateException("Native power lever: "+message);}
    private static NativePowerLeverBlock[] blocks(){return new NativePowerLeverBlock[]{VandorLabsBootstrap.INDUSTRIAL_POWER_LEVER.get(),VandorLabsBootstrap.COMPACT_POWER_LEVER.get(),VandorLabsBootstrap.SMALL_POWER_LEVER.get(),VandorLabsBootstrap.LARGE_POWER_LEVER.get()};}
    private static NativeBinaryControlBlockEntity install(BlockPos pos,NativePowerLeverBlock block,boolean floor,Direction heading){world.getChunkAt(pos);BlockState state=block.defaultBlockState().setValue(NativePowerLeverBlock.FLOOR,floor).setValue(NativePowerLeverBlock.FACING,heading);BlockPos support=pos.relative(block.supportDirection(state));world.getChunkAt(support);world.setBlock(support,Blocks.STONE.defaultBlockState(),3);world.setBlock(pos,state,3);var tile=(NativeBinaryControlBlockEntity)world.getBlockEntity(pos);tile.onLoad();NativeRedstoneChannels.flush(world);return tile;}
    private static CompoundTag bank(int first,int second){var tag=new CompoundTag();tag.putInt("RedstoneChannel",first);tag.putIntArray("RedstoneChannels",new int[]{first,second});tag.putBoolean("LocalOn",true);tag.putBoolean("ChannelInitialized",true);tag.putIntArray("LatchedChannels",new int[]{first,second});tag.putIntArray("LatchedLevels",new int[]{4,11});tag.putString("foreign:opaque","preserve");var forge=new CompoundTag();forge.putString("external","keep");tag.put("ForgeData",forge);var caps=new CompoundTag();caps.putInt("opaque",17);tag.put("ForgeCaps",caps);return tag;}
    private static void savedSizeSemantics(){
        int index=0;for(var block:blocks()){
            int expectedDefault=new int[]{1,0,0,1}[index];var tile=install(POS.east(index*4),block,false,Direction.NORTH);require(tile.getBlockState().getDestroySpeed(world,tile.getBlockPos())==.5f&&block.getExplosionResistance()==.5f&&!tile.getBlockState().requiresCorrectToolForDrops(),"legacy circuits material hardness/resistance/hand harvest");require(tile.getPowerLeverSize()==expectedDefault&&tile.getModelData().get(NativeBinaryControlBlockEntity.VISUAL).size()==expectedDefault,"registry default and initial immutable visual "+index);require(tile.getBlockState().getProperties().size()==3&&!tile.getBlockState().getProperties().stream().anyMatch(property->property.getName().contains("size")),"size not listed state");
            for(int type=0;type<7;type++){
                var tag=bank(1001+index*20+type*2,1002+index*20+type*2);int expected=expectedDefault;boolean present=false;
                if(type==1)tag.putString(PowerLeverSize.KEY,"1");else if(type>=2){int raw=new int[]{0,0,-3,0,1,7,Integer.MIN_VALUE}[type];tag.putInt(PowerLeverSize.KEY,raw);expected=raw<=0?0:1;present=true;}
                var before=tag.copy();tile.loadWithComponents(tag,world.registryAccess());require(tag.equals(before)&&tile.getPowerLeverSize()==expected&&tile.getOutputLevel()==4&&tile.latchedLevel(tile.getRedstoneChannels().get(1))==11,"typed saved size/bank ownership "+index+"/"+type);
                var saved=tile.saveWithFullMetadata(world.registryAccess());require(saved.contains(PowerLeverSize.KEY)==present&&saved.contains(PowerLeverSize.KEY,Tag.TAG_INT)==present&&(!present||saved.getInt(PowerLeverSize.KEY)==expected)&&saved.getCompound("ForgeCaps").equals(tag.getCompound("ForgeCaps"))&&saved.getCompound("ForgeData").getString("external").equals("keep"),"saved typed field/opaque data "+index+"/"+type);
                var identity=world.getBlockEntity(tile.getBlockPos());var state=tile.getBlockState();var channels=tile.getRedstoneChannels();var levels=tile.saveWithFullMetadata(world.registryAccess()).getIntArray("LatchedLevels");
                int previous=tile.getPowerLeverSize();require(!tile.setPowerLeverSize(-1)&&!tile.setPowerLeverSize(2)&&tile.getPowerLeverSize()==previous,"live invalid size rejected");
                for(int size=0;size<=1;size++){require(tile.setPowerLeverSize(size)&&world.getBlockEntity(tile.getBlockPos())==identity&&tile.getBlockState()==state&&tile.getRedstoneChannels().equals(channels)&&Arrays.equals(tile.saveWithFullMetadata(world.registryAccess()).getIntArray("LatchedLevels"),levels)&&tile.getOutputLevel()==4,"size edit preserves host identity/state/banks");}
            }
            require(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(tile.getType()).toString().equals("vandorlabs:redstone_channel"),"retained shared BE saved ID");world.removeBlock(tile.getBlockPos(),false);index++;
        }
    }
    private static void placementAndGeometry(){
        int index=0;for(var block:blocks()){
            for(Direction face:Direction.values()){
                BlockPos target=POS.east(index*8).south(8),support=target.relative(face.getOpposite());world.getChunkAt(target);world.getChunkAt(support);world.setBlock(target,Blocks.AIR.defaultBlockState(),3);world.setBlock(support,Blocks.STONE.defaultBlockState(),3);player.setYRot(90);var item=new ItemStack(block);var hit=new BlockHitResult(Vec3.atCenterOf(support).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5),face,support,false);var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,item,hit);var placed=block.getStateForPlacement(context);
                require((placed==null)==(face==Direction.DOWN),"UP plus4walls only face="+face);if(placed==null)continue;require(placed.getValue(NativePowerLeverBlock.FLOOR)==(face==Direction.UP)&&placed.getValue(NativePowerLeverBlock.FACING)==(face==Direction.UP?player.getDirection().getOpposite():face)&&!placed.getValue(NativePowerLeverBlock.POWERED),"placement orientation/OFF");
                var tile=install(target,block,face==Direction.UP,placed.getValue(NativePowerLeverBlock.FACING));tile.configureMount(ControlMountSettings.of(2,1,3));tile.configureTransmission(ChannelList.of(2001+index*2,2002+index*2),16,true);tile.applyLinkedLevels(Map.of(2001+index*2,4,2002+index*2,11));NativeRedstoneChannels.outputChanged(tile);
                AABB[] bounds=new AABB[2];for(int size=0;size<=1;size++){tile.setPowerLeverSize(size);var state=tile.getBlockState();bounds[size]=state.getCollisionShape(world,target).bounds();require(!state.getShape(world,target).isEmpty()&&state.getShape(world,target).bounds().equals(bounds[size]),"actual dynamic collision/selection size="+size);for(Direction side:Direction.values())require(!state.isFaceSturdy(world,target,side),"legacy undefined support face "+side);for(int value=0;value<=15;value++){tile.applyLinkedLevels(Map.of(2001+index*2,value,2002+index*2,11));NativeRedstoneChannels.outputChanged(tile);var current=tile.getBlockState();for(Direction side:Direction.values())require(current.getSignal(world,target,side)==(value==0?0:Math.min(value,11))&&current.getDirectSignal(world,target,side)==(value==0?0:Math.min(value,11)),"all16levels/all6 physical directions min numeric bank");}}
                require(!bounds[0].equals(bounds[1]),"size changes actual geometry without state replacement");tile.applyLinkedLevels(Map.of(2001+index*2,4,2002+index*2,11));NativeRedstoneChannels.outputChanged(tile);tile.configureTransmission(tile.getRedstoneChannels(),16,false);require(tile.getBlockState().getDirectSignal(world,target,Direction.UP)==0&&NativeRedstoneChannels.level(world,target,2001+index*2)==4,"Local gated physical but channel persists");
                var state=tile.getBlockState();BlockPos backing=target.relative(block.supportDirection(state));world.removeBlock(backing,false);require(world.getBlockState(target).isAir(),"loaded support removal drops retained lever ID");
            }index++;
        }
    }
    private static void itemsAndPayload(){
        int index=0;for(var block:blocks()){
            var tile=install(POS.east(index*4),block,false,Direction.NORTH);tile.configureTransmission(ChannelList.of(4001+index*2,4002+index*2),8,false);tile.configureMount(ControlMountSettings.of(3,2,1));tile.setLocalOn(true);
            for(int size=0;size<2;size++){
                tile.setPowerLeverSize(size);var canonical=block.isTwin()?VandorLabsBootstrap.SMALL_POWER_LEVER_ITEM.get():VandorLabsBootstrap.INDUSTRIAL_POWER_LEVER_ITEM.get();var stack=block.getCloneItemStack(tile.getBlockState(),new BlockHitResult(Vec3.atCenterOf(tile.getBlockPos()),Direction.NORTH,tile.getBlockPos(),false),world,tile.getBlockPos(),player);var drops=Block.getDrops(tile.getBlockState(),world,tile.getBlockPos(),tile,player,ItemStack.EMPTY);
                require(stack.is(canonical)&&drops.size()==1&&drops.getFirst().is(canonical)&&stack.get(DataComponents.BLOCK_ENTITY_DATA)==null&&drops.getFirst().get(DataComponents.BLOCK_ENTITY_DATA)==null,"canonical pick/drop identity without runtime BE");var config=stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("RedstoneChannelSettings");require(config.getInt(PowerLeverSize.KEY)==size&&config.getAllKeys().equals(Set.of("Channel","RedstoneChannels","ControlMountVersion","BaseHeight","BaseTilt","TiltDirection",PowerLeverSize.KEY))&&drops.getFirst().get(DataComponents.CUSTOM_DATA).copyTag().getCompound("RedstoneChannelSettings").equals(config),"exact sized pick/drop subset");
                var fresh=new NativeBinaryControlBlockEntity(BlockPos.ZERO,canonical.getBlock().defaultBlockState());fresh.place(config,0);require(!fresh.isLocalOn()&&fresh.getPowerLeverSize()==size&&fresh.transmissionRange()==16&&fresh.localRedstone(),"fresh configured canonical item OFF/default inline settings");config.putInt(PowerLeverSize.KEY,9);fresh.place(config,0);require(fresh.getPowerLeverSize()==((NativePowerLeverBlock)canonical.getBlock()).defaultSize(),"invalid typed item size rejected rather than saved clamp");
            }
            player.setPos(tile.getBlockPos().getX()+.5,tile.getBlockPos().getY(),tile.getBlockPos().getZ()+2);player.containerMenu=new BinaryControlMenu(31,player.getInventory(),tile.getBlockPos());var bank=tile.saveWithFullMetadata(world.registryAccess()).getIntArray("LatchedLevels");var identity=world.getBlockEntity(tile.getBlockPos());var visual=tile.getModelData().get(NativeBinaryControlBlockEntity.VISUAL);require(!PowerLeverPayload.apply(player,new PowerLeverPayload(tile.getBlockPos(),31,ControlMountSettings.of(2,3,1),2))&&tile.getModelData().get(NativeBinaryControlBlockEntity.VISUAL)==visual,"invalid atomic draft changes nothing");require(PowerLeverPayload.apply(player,new PowerLeverPayload(tile.getBlockPos(),31,ControlMountSettings.of(1,2,3),0))&&world.getBlockEntity(tile.getBlockPos())==identity&&Arrays.equals(tile.saveWithFullMetadata(world.registryAccess()).getIntArray("LatchedLevels"),bank)&&tile.mountSettings().height==1,"valid atomic size+mount keeps BE/bank");require(!PowerLeverPayload.apply(player,new PowerLeverPayload(tile.getBlockPos(),32,ControlMountSettings.of(0,0,0),1)),"stale power size window");
            var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),world.registryAccess());PowerLeverPayload.CODEC.encode(bytes,new PowerLeverPayload(tile.getBlockPos(),31,ControlMountSettings.of(3,1,2),1));require(bytes.readableBytes()==16&&PowerLeverPayload.CODEC.decode(bytes).size()==1,"bounded16byte power draft");bytes.release();for(int length:new int[]{0,15,17}){var bad=new RegistryFriendlyByteBuf(Unpooled.buffer(),world.registryAccess());bad.writeZero(length);try{PowerLeverPayload.CODEC.decode(bad);throw new AssertionError("accepted power draft length="+length);}catch(IllegalArgumentException expected){}finally{bad.release();}}var bad=new RegistryFriendlyByteBuf(Unpooled.buffer(),world.registryAccess());bad.writeBlockPos(tile.getBlockPos());bad.writeInt(31);bad.writeByte(0);bad.writeByte(0);bad.writeByte(0);bad.writeByte(2);try{PowerLeverPayload.CODEC.decode(bad);throw new AssertionError("accepted invalid bounded power size");}catch(IllegalArgumentException expected){}finally{bad.release();}player.containerMenu=player.inventoryMenu;world.removeBlock(tile.getBlockPos(),false);index++;
        }
    }
    private static void catalog(){
        var tab=BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.fromNamespaceAndPath("vandorlabs","vandorlabs"));tab.buildContents(new CreativeModeTab.ItemDisplayParameters(world.enabledFeatures(),false,world.registryAccess()));for(var block:blocks()){boolean visible=block==VandorLabsBootstrap.INDUSTRIAL_POWER_LEVER.get()||block==VandorLabsBootstrap.SMALL_POWER_LEVER.get();require(tab.getDisplayItems().stream().anyMatch(stack->stack.is(block.asItem()))==visible,"only canonical lever creative entries");}
        for(String hidden:new String[]{"compact_power_lever","large_power_lever"})require(world.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",hidden)).isEmpty(),"no hidden retained-ID recipe");
        for(boolean twin:new boolean[]{false,true}){String name=twin?"small_power_lever":"industrial_power_lever";var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)world.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("vandorlabs",name)).orElseThrow().value();var items=List.of(new ItemStack(Items.LEVER),new ItemStack(twin?Items.LEVER:VandorLabsBootstrap.ALLOY_INGOT.get()),new ItemStack(VandorLabsBootstrap.ALLOY_INGOT.get()));var input=net.minecraft.world.item.crafting.CraftingInput.of(3,1,items);require(recipe.matches(input,world)&&recipe.assemble(input,world.registryAccess()).is((twin?VandorLabsBootstrap.SMALL_POWER_LEVER_ITEM:VandorLabsBootstrap.INDUSTRIAL_POWER_LEVER_ITEM).get()),"canonical recipe actual manager "+name);}
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.transmitterPhase","");if(!Set.of("power-write","power-read").contains(phase))return;world=event.getServer().overworld();player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(world);checks=0;
        if(phase.equals("power-write")){catalog();savedSizeSemantics();placementAndGeometry();itemsAndPayload();var saved=install(POS,VandorLabsBootstrap.COMPACT_POWER_LEVER.get(),true,Direction.EAST);saved.loadWithComponents(bank(771,773),world.registryAccess());saved.setPowerLeverSize(1);saved.configureMount(ControlMountSettings.of(3,2,1));NativeRedstoneChannels.flush(world);}
        else{world.getChunkAt(POS);var tile=(NativeBinaryControlBlockEntity)world.getBlockEntity(POS);require(tile!=null&&tile.getBlockState().is(VandorLabsBootstrap.COMPACT_POWER_LEVER.get())&&tile.getPowerLeverSize()==1&&tile.latchedLevel(771)==4&&tile.latchedLevel(773)==11&&tile.getOutputLevel()==4&&tile.mountSettings().height==3,"second JVM retains ID/explicit size/numeric bank/mount");require(tile.saveWithFullMetadata(world.registryAccess()).getString("foreign:opaque").equals("preserve")&&tile.saveWithFullMetadata(world.registryAccess()).getCompound("ForgeCaps").getInt("opaque")==17,"second JVM opaque data retained");}
        System.out.println("[vandorlabs][power-levers] server-"+phase+" PASS checks="+checks);event.getServer().halt(false);
    }
}
