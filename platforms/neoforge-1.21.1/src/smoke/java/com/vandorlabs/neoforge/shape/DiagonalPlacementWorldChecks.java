package com.vandorlabs.neoforge.shape;

import com.google.gson.*;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual original-click contexts, including replaceable targets and normal ItemStack use. */
@EventBusSubscriber(modid="vandorlabs")
public final class DiagonalPlacementWorldChecks {
    private static final BlockPos TARGET=new BlockPos(5008,112,5008);
    private static int checks,placed;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Diagonal placement: "+why);}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!"diagonal-placement".equals(System.getProperty("vandorlabs.transmitterPhase")))return;
        var server=event.getServer();
        try {
            ServerLevel level=server.overworld();level.getChunk(TARGET);
            var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.fromString("76a5d16f-b46a-447a-b62a-ac498cb814e7"),"DiagonalPlacement"));
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);player.setPos(TARGET.getX()+2,TARGET.getY(),TARGET.getZ()+2);
            var records=JsonParser.parseString(Files.readString(Paths.get(System.getProperty("vandorlabs.diagonalPlacementReference")))).getAsJsonArray();
            for(var element:records){var row=element.getAsJsonObject();
                int family=row.get("family").getAsInt(),mode=row.get("mode").getAsInt(),yaw=row.get("yaw").getAsInt();
                var id=ResourceLocation.parse("vandorlabs:"+new String[]{"programmable_diagonal_wall","programmable_diagonal_porthole","programmable_wall","programmable_porthole_wall"}[family]);
                var block=(NativePanelBlock)BuiltInRegistries.BLOCK.get(id);
                var face=Direction.from3DDataValue(row.get("face").getAsInt());
                float x=row.get("x").getAsFloat(),y=row.get("y").getAsFloat(),z=row.get("z").getAsFloat();
                for(boolean replacing:new boolean[]{false,true}){
                    for(var direction:Direction.values())level.setBlock(TARGET.relative(direction),Blocks.AIR.defaultBlockState(),2);
                    level.setBlock(TARGET,Blocks.AIR.defaultBlockState(),2);
                    BlockPos clicked=replacing?TARGET:TARGET.relative(face.getOpposite());
                    if(!replacing)level.setBlock(clicked,Blocks.STONE.defaultBlockState(),2);
                    var stack=new ItemStack(BuiltInRegistries.ITEM.get(id),2);var choices=new CompoundTag();choices.putBoolean("DiagonalHalfHeight",mode==1);choices.putBoolean("DiagonalFullWidth",mode==2);var root=new CompoundTag();root.put("BlockEntityTag",choices);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));
                    player.setItemInHand(InteractionHand.MAIN_HAND,stack);float rotation=Direction.from2DDataValue(yaw).toYRot();player.setYRot(rotation);player.setYHeadRot(rotation);player.setYBodyRot(rotation);
                    var hit=new BlockHitResult(Vec3.atLowerCornerOf(clicked).add(x,y,z),face,clicked,false);
                    var use=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);var context=new BlockPlaceContext(use);
                    String fixture="family="+family+" mode="+mode+" yaw="+yaw+" face="+face+" hit="+x+","+y+","+z+" replacing="+replacing;
                    need(context.getClickedPos().equals(TARGET),"actual target "+fixture);
                    var state=block.getStateForPlacement(context);
                    need(state.getValue(NativePanelBlock.FACING).get2DDataValue()==row.get("facing").getAsInt()&&state.getValue(NativeDiagonalWallBlock.INVERTED)==row.get("inverted").getAsBoolean(),"released callback "+fixture+" got="+state);
                    if(family>=2){int expected=row.get("depth").getAsInt();if(!replacing)expected=com.vandorlabs.render.PanelDepthPolicy.fromPlacement(state.getValue(NativePanelBlock.FACING).get3DDataValue(),face.get3DDataValue(),x,z);need(state.getValue(NativeFlatWallBlock.DEPTH)==expected,"released empty-cell depth / corrected support anchor "+fixture+" got="+state);}
                    if(mode==0&&yaw==0&&(x==.1F||y==.1F||z==.1F)){
                        var result=stack.useOn(use);need(result.consumesAction()&&level.getBlockState(TARGET).equals(state)&&stack.getCount()==1,"actual survival use "+fixture+" result="+result+" held="+stack);placed++;
                    }
                }
            }
            System.out.println("[vandorlabs][diagonal-placement] write PASS checks="+checks+" contexts="+records.size()*2+" actualUses="+placed);
        }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][diagonal-placement] FAIL "+failure);}
        finally{server.halt(false);}
    }
}
