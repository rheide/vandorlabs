package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.tiles.CustomMaterialIdentity;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Dedicated-only persistence and a disposable survival client scene. Never packaged. */
@EventBusSubscriber(modid="vandorlabs")
public final class SampledMaterialRuntimeChecks {
    public static final BlockPos POS=new BlockPos(0,80,0),OLD=POS.offset(-2,0,0),MISSING=POS.offset(2,0,0),CHEST=POS.offset(4,0,0);
    public static final int NEW=0x28f93e8c,LEGACY=770554706,UNAVAILABLE=CustomMaterialIdentity.identifier("absent_peer:panel",3);
    static void require(boolean value,String message){if(!value)throw new IllegalStateException("Native sampled material: "+message);}
    static ProgrammableBlockDialog.State state(int main){return new ProgrammableBlockDialog.State(main,2,true,new int[]{NEW,LEGACY,UNAVAILABLE,-1,NEW,LEGACY},-1);}
    private static void registry(){
        var snapshot=NativeMaterialRegistry.snapshot();require(snapshot.samples().containsKey(VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get())&&VandorLabsBootstrap.PROGRAMMABLE_ITEM.get().getBlock()==VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get(),"finalized registry omitted native block/item registrations");
        require(snapshot.reservedLegacyIds().size()==4064,"all old IDs including unsupported metadata reserved");
        require(snapshot.ambiguous().isEmpty(),"unexpected current vanilla/sample CRC collisions");
        var ids=new java.util.HashSet<Integer>();int vanilla=0;
        for(var entry:snapshot.samples().entrySet()){
            require(ids.add(entry.getValue())&&!snapshot.reservedLegacyIds().contains(entry.getValue()),"new ID overlaps another sample or legacy saved ID");
            require(snapshot.states().get(entry.getValue()).equals(entry.getKey().defaultBlockState()),"new sample changed current default properties");
            if(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(entry.getKey()).getNamespace().equals("minecraft"))vanilla++;
        }
        require(vanilla>1000,"current vanilla registry coverage");
        require(snapshot.samples().get(Blocks.STONE_SLAB)==NEW&&snapshot.states().get(LEGACY).is(Blocks.SMOOTH_STONE_SLAB),"conflicting saved/new slab interpretations changed");
        require(!snapshot.states().containsKey(UNAVAILABLE),"missing external ID unexpectedly registered");
        System.out.println("[vandorlabs][sampled-material] registry PASS vanilla="+vanilla+" samples="+ids.size()+" legacyReserved=4064 collisions=0 current default properties retained");
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("vandorlabs.sampledMaterialPhase","");if(!phase.equals("write")&&!phase.equals("read"))return;
        registry();var server=event.getServer();var level=server.overworld();level.getChunkAt(POS);
        int[] choices={NEW,LEGACY,UNAVAILABLE};BlockPos[] positions={POS,OLD,MISSING};
        if(phase.equals("write")){
            level.setBlock(CHEST,Blocks.CHEST.defaultBlockState(),3);var chest=(ChestBlockEntity)level.getBlockEntity(CHEST);
            for(int i=0;i<positions.length;i++){
                level.setBlock(positions[i],VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);var tile=(ProgrammableBlockEntity)level.getBlockEntity(positions[i]);tile.apply(state(choices[i]));
                var tag=tile.saveWithoutMetadata(level.registryAccess());tag.putString("UnknownFutureSettings","retained");tile.loadWithComponents(tag,level.registryAccess());tile.setChanged();
                ItemStack item=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());tile.saveToItem(item,level.registryAccess());chest.setItem(i,item);
            }
            chest.setChanged();
        }else{
            var chest=(ChestBlockEntity)level.getBlockEntity(CHEST);require(chest!=null,"configured item chest missing after restart");
            for(int i=0;i<positions.length;i++){
                var tile=(ProgrammableBlockEntity)level.getBlockEntity(positions[i]);require(tile!=null&&tile.settings().mainTexture==choices[i],"saved main ID changed after restart");
                require(tile.settings().facesEnabled&&java.util.Arrays.equals(tile.settings().faces(),state(choices[i]).faces()),"all six saved override IDs changed");
                require(tile.saveWithoutMetadata(level.registryAccess()).getString("UnknownFutureSettings").equals("retained"),"unknown tile fields lost");
                var item=chest.getItem(i);var before=item.copy();var tag=item.get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
                require(tag.getInt(HousingSettings.MAIN)==choices[i]&&tag.getString("UnknownFutureSettings").equals("retained"),"configured saved item/NBT lost original ID or opaque fields");
                require(ItemStack.isSameItemSameComponents(before,item),"reading configured item changed payload");
            }
        }
        server.saveEverything(true,true,true);System.out.println("[vandorlabs][sampled-material] server-"+phase+" PASS dedicated registry/linkage, new/legacy/unavailable IDs, six overrides, opaque NBT, chest items and process restart");server.halt(false);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!System.getProperty("vandorlabs.sampledMaterialPhase","").equals("client")||!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))return;
        registry();var level=player.serverLevel();for(int x=-5;x<=6;x++)for(int z=-5;z<=3;z++)level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(POS,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);level.setBlock(OLD,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);level.setBlock(MISSING,VandorLabsBootstrap.PROGRAMMABLE_BLOCK.get().defaultBlockState(),3);
        ((ProgrammableBlockEntity)level.getBlockEntity(OLD)).apply(new ProgrammableBlockDialog.State(LEGACY,1,false,new int[]{-1,-1,-1,-1,-1,-1},-1));((ProgrammableBlockEntity)level.getBlockEntity(MISSING)).apply(new ProgrammableBlockDialog.State(UNAVAILABLE,1,false,new int[]{-1,-1,-1,-1,-1,-1},-1));
        player.getInventory().clearContent();player.setGameMode(GameType.SURVIVAL);player.teleportTo(.5,80,-3);player.setYRot(0);player.setXRot(8);player.getInventory().selected=0;
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VandorLabsBootstrap.CONFIGURIZER.get()));
        player.getInventory().setItem(1,new ItemStack(Items.STONE_SLAB,7));player.getInventory().setItem(2,new ItemStack(Items.STONE_STAIRS,5));player.getInventory().setItem(3,new ItemStack(Items.OAK_DOOR,3));player.getInventory().setItem(4,new ItemStack(Items.STONE,9));player.getInventory().setItem(5,new ItemStack(Items.STICK,11));
        var configured=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());((ProgrammableBlockEntity)level.getBlockEntity(OLD)).saveToItem(configured,level.registryAccess());player.getInventory().setItem(6,configured);
        var nested=new ItemStack(VandorLabsBootstrap.PROGRAMMABLE_ITEM.get());var data=new net.minecraft.nbt.CompoundTag();var housing=new net.minecraft.nbt.CompoundTag();housing.putInt(HousingSettings.MAIN,LEGACY);housing.putString("OpaqueFutureField","nested");data.put("BlockEntityTag",housing);nested.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(data));player.getInventory().setItem(7,nested);
        player.inventoryMenu.broadcastChanges();level.setDayTime(6000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false,level.getServer());level.setWeatherParameters(0,6000,false,false);
        System.out.println("[vandorlabs][sampled-material] fixture READY survival Configurizer and inventory samples");
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(!System.getProperty("vandorlabs.sampledMaterialPhase","").equals("client"))return;
        if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))return;
        var level=player.serverLevel();var tile=(ProgrammableBlockEntity)level.getBlockEntity(POS);require(tile!=null&&tile.settings().mainTexture==NEW,"real client sample packet did not reach dedicated tile");
        require(!tile.settings().facesEnabled&&tile.settings().face(2)==CustomMaterialIdentity.flattenedBlockIdentifier("minecraft:oak_door"),"dormant client face sample not retained on server");
        require(player.getInventory().getItem(1).getCount()==7&&player.getInventory().getItem(3).getCount()==3,"client sampling consumed server inventory");
        System.out.println("[vandorlabs][sampled-material] server-client-actions PASS authoritative new ID and nonconsuming inventory");
    }
}
