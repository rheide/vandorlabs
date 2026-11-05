package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Native save/restart and bounded authenticated transport contracts; excluded from the production JAR. */
@EventBusSubscriber(modid="vandorlabs")
public final class CubeRuntimeChecks {
    public static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native cube: "+message);}
    public static ProgrammableBlockDialog.State state(int main,int layout,boolean enabled){return new ProgrammableBlockDialog.State(main,layout,enabled,new int[]{1,2,3,4,5,6},-1);}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.cubePhase","");if(!phase.equals("write")&&!phase.equals("read"))return;
        var server=event.getServer();var level=server.overworld();int count=0;
        for(Direction facing:Direction.values())for(int layout=0;layout<3;layout++)for(boolean enabled:new boolean[]{false,true}){
            BlockPos pos=new BlockPos(facing.get3DDataValue()*4,80+(enabled?3:0),layout*4);level.getChunkAt(pos);
            if(phase.equals("write")){
                level.setBlock(pos,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState().setValue(ProgrammableBlock.FACING,facing),3);
                var tile=(ProgrammableBlockEntity)level.getBlockEntity(pos);tile.apply(state(8,layout,enabled));
                CompoundTag saved=tile.saveWithoutMetadata(level.registryAccess());saved.putString("FutureConfiguration","retained");saved.putInt("SideTexture",7);tile.loadWithComponents(saved,level.registryAccess());tile.setChanged();
            }else{
                require(level.getBlockState(pos).is(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get())&&level.getBlockState(pos).getValue(ProgrammableBlock.FACING)==facing,"saved facing "+pos);
                var tile=(ProgrammableBlockEntity)level.getBlockEntity(pos);require(tile.settings().mainTexture==8&&tile.settings().layout==layout&&tile.settings().facesEnabled==enabled&&Arrays.equals(tile.settings().faces(),new int[]{1,2,3,4,5,6}),"saved settings "+pos);
                CompoundTag saved=tile.saveWithoutMetadata(level.registryAccess());require(saved.getString("FutureConfiguration").equals("retained")&&saved.getInt("SideTexture")==7,"unknown settings/side retained");
                var picked=VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().getCloneItemStack(tile.getBlockState(),new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.NORTH,pos,false),level,pos,null);
                verifyItem(picked,level,tile);
                var drops=Block.getDrops(tile.getBlockState(),level,pos,tile,null,new ItemStack(Items.IRON_PICKAXE));require(drops.size()==1,"configured loot count");verifyItem(drops.getFirst(),level,tile);
            }
            count++;
        }
        if(phase.equals("write")){legacy(level);transport(level);
            recipe(level,"programmable_block",3,3,java.util.List.of(Items.AIR,VandorLabsBootstrap.ALLOY_INGOT.get(),Items.AIR,VandorLabsBootstrap.ALLOY_INGOT.get(),Items.REDSTONE,VandorLabsBootstrap.ALLOY_INGOT.get(),Items.AIR,VandorLabsBootstrap.ALLOY_INGOT.get(),Items.AIR),VandorLabsBootstrap.PROGRAMMABLE_ITEM.get(),4);
            recipe(level,"configurizer",2,2,java.util.List.of(Items.AIR,Items.REDSTONE,VandorLabsBootstrap.MATTER_INGOT.get(),Items.AIR),VandorLabsBootstrap.CONFIGURIZER.get(),1);
        }
        server.saveEverything(true,true,true);System.out.println("[vandorlabs][cube] server-"+phase+" PASS cases="+count+" six facings/three layouts/disabled saved overrides, native disk persistence, configured pick/drop, retained unknown fields");server.halt(false);
    }
    private static void recipe(net.minecraft.server.level.ServerLevel level,String id,int width,int height,java.util.List<net.minecraft.world.item.Item> ingredients,net.minecraft.world.item.Item output,int count){
        var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("vandorlabs",id)).orElseThrow().value();
        var items=ingredients.stream().map(ItemStack::new).toList();var input=net.minecraft.world.item.crafting.CraftingInput.of(width,height,items);
        require(recipe.matches(input,level),"canonical recipe ingredients "+id);var result=recipe.assemble(input,level.registryAccess());require(result.is(output)&&result.getCount()==count,"canonical recipe output "+id);
        var wrong=new java.util.ArrayList<>(items);wrong.set(0,new ItemStack(Items.DIRT));require(!recipe.matches(net.minecraft.world.item.crafting.CraftingInput.of(width,height,wrong),level),"wrong recipe accepted "+id);
        System.out.println("[vandorlabs][cube] recipe PASS "+id+" count="+count);
    }
    private static void verifyItem(ItemStack stack,net.minecraft.server.level.ServerLevel level,ProgrammableBlockEntity tile){
        require(stack.is(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get()),"configured item identity");
        var component=stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);require(component!=null,"configured item data");
        var saved=component.copyTag();require(saved.getString("FutureConfiguration").equals("retained")&&Arrays.equals(saved.getIntArray(HousingSettings.FACES),tile.settings().faces()),"configured item preserves opaque and dormant fields");
        var restored=new ProgrammableBlockEntity(new BlockPos(100,80,100),tile.getBlockState());restored.loadWithComponents(saved,level.registryAccess());require(restored.settings().mainTexture==tile.settings().mainTexture&&restored.settings().layout==tile.settings().layout&&restored.settings().facesEnabled==tile.settings().facesEnabled,"item placement state roundtrip");
    }
    private static void legacy(net.minecraft.server.level.ServerLevel level){
        var tile=new ProgrammableBlockEntity(new BlockPos(70,80,0),VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState());tile.apply(state(15,2,true));
        CompoundTag saved=new CompoundTag();tile.loadWithComponents(saved,level.registryAccess());require(tile.settings().mainTexture==15&&tile.settings().layout==0,"missing main preserves current and previous side fallback");
        saved.putInt(HousingSettings.MAIN,14);tile.loadWithComponents(saved,level.registryAccess());require(tile.settings().mainTexture==0,"unversioned grille");
        saved.putInt(HousingSettings.MAIN,15);saved.putBoolean(HousingSettings.TILE_SIDES,true);tile.loadWithComponents(saved,level.registryAccess());require(tile.settings().mainTexture==14&&tile.settings().layout==1,"unversioned index and layout fallback");
        saved.putInt(HousingSettings.VERSION,1);saved.putInt(HousingSettings.LAYOUT,2);tile.loadWithComponents(saved,level.registryAccess());
        tile.apply(state(9,2,true));require(tile.saveWithoutMetadata(level.registryAccess()).getBoolean(HousingSettings.TILE_SIDES),"main/face edit overwrote retained explicit-layout side fallback");
        saved.putInt(HousingSettings.LAYOUT,99);tile.loadWithComponents(saved,level.registryAccess());tile.apply(state(9,1,true));require(tile.saveWithoutMetadata(level.registryAccess()).getBoolean(HousingSettings.TILE_SIDES),"main/face edit overwrote raw sentinel fallback");
        saved.putInt(HousingSettings.MAIN,0x23456789);saved.putBoolean(HousingSettings.FACES_ENABLED,true);saved.putIntArray(HousingSettings.FACES,new int[]{0x456789ab,1,2,3,4,5});tile.loadWithComponents(saved,level.registryAccess());
        var written=tile.saveWithoutMetadata(level.registryAccess());require(written.getInt(HousingSettings.MAIN)==0x23456789&&written.getIntArray(HousingSettings.FACES)[0]==0x456789ab&&ModernFinishCatalog.renderChoice(tile.settings().mainTexture)==0,"unavailable external saved IDs preserved with render fallback");
    }
    private static void transport(net.minecraft.server.level.ServerLevel level){
        BlockPos pos=new BlockPos(60,80,0);level.setBlock(pos,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.CREATIVE);player.setPos(60.5,81,.5);player.containerMenu=new ProgrammableMenu(1,player.getInventory(),pos);
        FinishPayload packet=new FinishPayload(pos,8,2,true,new int[]{1,2,3,4,5,6});var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
        FinishPayload.CODEC.encode(buffer,packet);require(buffer.readableBytes()==38,"stable payload bytes");byte[] bytes=new byte[38];buffer.getBytes(0,bytes);FinishPayload decoded=FinishPayload.CODEC.decode(buffer);require(decoded.main()==8&&decoded.layout()==2&&Arrays.equals(decoded.faces(),packet.faces()),"payload roundtrip");buffer.release();
        for(int length=0;length<40;length++)if(length!=38){var malformed=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(Arrays.copyOf(bytes,length)),level.registryAccess());try{FinishPayload.CODEC.decode(malformed);throw new AssertionError("accepted payload length "+length);}catch(IllegalArgumentException expected){}finally{malformed.release();}}
        for(int at:new int[]{8,12,13,14}){byte[] malformedBytes=bytes.clone();malformedBytes[at]=(byte)0xff;var malformed=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(malformedBytes),level.registryAccess());try{FinishPayload.CODEC.decode(malformed);throw new AssertionError("accepted malformed header/face "+at);}catch(IllegalArgumentException expected){}finally{malformed.release();}}
        require(FinishPayload.apply(player,packet),"creative editing denied");
        player.setGameMode(GameType.SURVIVAL);require(!FinishPayload.apply(player,packet),"survival without tool accepted");
        player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));require(!FinishPayload.apply(player,packet),"offhand tool authorized");
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));require(FinishPayload.apply(player,packet),"survival main hand tool denied");
        var menu=player.containerMenu;player.containerMenu=player.inventoryMenu;require(!FinishPayload.apply(player,packet),"missing menu accepted");player.containerMenu=menu;
        player.setGameMode(GameType.SPECTATOR);require(!FinishPayload.apply(player,packet),"spectator accepted");player.setGameMode(GameType.CREATIVE);
        player.setPos(200,81,0);require(!FinishPayload.apply(player,packet),"remote edit accepted");player.setPos(60.5,81,.5);
        require(!FinishPayload.apply(player,new FinishPayload(new BlockPos(1000000,80,0),8,1,false,new int[]{-1,-1,-1,-1,-1,-1})),"unloaded target accepted");
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);require(!FinishPayload.apply(player,packet),"stale tile menu accepted");
        System.out.println("[vandorlabs][cube] transport PASS bytes=38 (unchanged), fixed-length and values checked before face allocation, roundtrip, creative/survival main-hand tool/menu/reach/loaded chunk/spectator/stale identity");
    }
}
