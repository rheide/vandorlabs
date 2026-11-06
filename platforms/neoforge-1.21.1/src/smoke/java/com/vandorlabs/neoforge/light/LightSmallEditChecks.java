package com.vandorlabs.neoforge.light;
import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
/** Actual Source setter results: effective Small does not erase ignored full/slab saved data. */
public final class LightSmallEditChecks {
 public static void run(ServerLevel world,ServerPlayer actor,NativeLightBlock[] blocks,BlockPos pos)throws Exception{var previous=actor.containerMenu;var held=actor.getMainHandItem().copy();int checks=0;try{actor.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.vandorlabs.neoforge.VandorLabsBootstrap.CONFIGURIZER.get()));var rows=JsonParser.parseString(Files.readString(Path.of(System.getProperty("vandorlabs.lightEditReference")))).getAsJsonObject().getAsJsonArray("small");for(var element:rows){var row=element.getAsJsonObject();world.setBlock(pos,Blocks.AIR.defaultBlockState(),18);world.setBlock(pos,blocks[row.get("family").getAsInt()].defaultBlockState(),18);var owner=(NativeLightEntity)world.getBlockEntity(pos);var raw=new net.minecraft.nbt.CompoundTag();raw.putBoolean("smallInput",row.get("raw").getAsBoolean());owner.loadWithComponents(raw,world.registryAccess());owner.onLoad();NativeLightService.refresh(owner);actor.containerMenu=new NativeLightMenu(null,87,owner);var edit=new NativeLightSettingsEdit(pos,87,new NativeLightEdit(78,-1,15,false,0,0,row.get("requested").getAsBoolean(),0,false,0),com.vandorlabs.redstone.ChannelList.EMPTY);if(!LightSettingsPayload.apply(actor,edit)||owner.inheritedSettings().scalar().smallInput!=row.get("saved").getAsBoolean()||owner.visual().small()!=row.get("effective").getAsBoolean())throw new AssertionError("Light Source Small callback mismatch "+row);checks++;}if(checks!=12)throw new AssertionError("Light Small coverage");}finally{actor.containerMenu=previous;actor.setItemInHand(InteractionHand.MAIN_HAND,held);world.setBlock(pos,Blocks.AIR.defaultBlockState(),18);}System.out.println("[vandorlabs][light-small-edit] PASS cases="+checks);}
}
