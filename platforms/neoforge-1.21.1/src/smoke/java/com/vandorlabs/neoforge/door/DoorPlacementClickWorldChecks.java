package com.vandorlabs.neoforge.door;

import com.google.gson.*;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Actual public items with original-click contexts; no substitute placement implementation. */
@EventBusSubscriber(modid="vandorlabs")
public final class DoorPlacementClickWorldChecks {
    private static final BlockPos CENTER=new BlockPos(5744,112,5744);
    private static int checks,placed;
    private static void need(boolean value,String why){checks++;if(!value)throw new AssertionError("Door original click: "+why);}
    private static void nearHalf(ServerLevel level,ServerPlayer actor,JsonArray rows){
        for(int x=-3;x<=3;x++)for(int y=-1;y<=4;y++)for(int z=-3;z<=3;z++)level.setBlock(CENTER.offset(x,y,z),Blocks.AIR.defaultBlockState(),16);
        int samples=0;
        for(var element:rows){var row=element.getAsJsonObject();if(row.get("family").getAsInt()!=0||!row.get("success").getAsBoolean())continue;
            float x=row.get("x").getAsFloat(),z=row.get("z").getAsFloat();if(x!=.5F&&z!=.5F)continue;
            for(var direction:Direction.values())level.setBlock(CENTER.relative(direction),Blocks.AIR.defaultBlockState(),16);level.setBlock(CENTER,Blocks.AIR.defaultBlockState(),16);
            var side=Direction.from3DDataValue(row.get("face").getAsInt());var clicked=row.get("replace").getAsBoolean()?CENTER:CENTER.relative(side.getOpposite());if(!row.get("replace").getAsBoolean())level.setBlock(clicked,Blocks.STONE.defaultBlockState(),16);
            actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(NativeDoorRegistrations.ITEM.get()));float rotation=Direction.from2DDataValue(row.get("yaw").getAsInt()).toYRot();actor.setYRot(rotation);actor.setYHeadRot(rotation);actor.setYBodyRot(rotation);
            for(double epsilon:new double[]{-1e-10,1e-10}){
                double hx=x==.5F?x+epsilon:x,hz=z==.5F?z+epsilon:z;
                need((float)hx==x&&(float)hz==z,"near-half reference retains legacy float input");
                var hit=new BlockHitResult(Vec3.atLowerCornerOf(clicked).add(hx,row.get("y").getAsFloat(),hz),side,clicked,false);
                var state=NativeDoorRegistrations.ORDINARY.get().getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(new UseOnContext(actor,InteractionHand.MAIN_HAND,hit)));
                need((state.getValue(NativeProgrammableDoorBlock.HINGE)==DoorHingeSide.RIGHT)==row.get("rightHinge").getAsBoolean(),"legacy float hinge tie "+row+" epsilon="+epsilon);samples++;
            }
        }
        System.out.println("[vandorlabs][door-placement-clicks] near-half float ties PASS contexts="+samples);
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!"door-placement-clicks".equals(System.getProperty("vandorlabs.transmitterPhase")))return;var server=event.getServer();
        try{ServerLevel level=server.overworld();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.getChunk((CENTER.getX()>>4)+x,(CENTER.getZ()>>4)+z);
            var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.fromString("3c79bdfb-fbbd-4ca4-8b4d-4f9f11551e5b"),"DoorClicks"));actor.setGameMode(GameType.SURVIVAL);actor.setPos(CENTER.getX()+.5,CENTER.getY(),CENTER.getZ()+3.5);
            var rows=JsonParser.parseString(Files.readString(Paths.get(System.getProperty("vandorlabs.doorPlacementClickReference")))).getAsJsonArray();
            for(var element:rows){var row=element.getAsJsonObject();int family=row.get("family").getAsInt(),yaw=row.get("yaw").getAsInt();var side=Direction.from3DDataValue(row.get("face").getAsInt());boolean replace=row.get("replace").getAsBoolean(),configured=row.get("configured").getAsBoolean();
                for(int x=-3;x<=3;x++)for(int y=-1;y<=4;y++)for(int z=-3;z<=3;z++)level.setBlock(CENTER.offset(x,y,z),Blocks.AIR.defaultBlockState(),16);
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setBlock(CENTER.offset(x,-1,z),Blocks.STONE.defaultBlockState(),16);
                var clicked=replace?CENTER:CENTER.relative(side.getOpposite());if(!replace)level.setBlock(clicked,Blocks.STONE.defaultBlockState(),16);
                var id=ResourceLocation.parse("vandorlabs:"+(family==0?NativeDoorSettings.ORDINARY_ID:NativeDoorSettings.LARGE_ID));var stack=new ItemStack(BuiltInRegistries.ITEM.get(id),2);if(configured){var settings=new CompoundTag();settings.putInt("SpaceDesign",7);settings.putInt("SpaceDoorPlacementDepth",2);var root=new CompoundTag();root.put("SpaceDoorSettings",settings);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));}
                actor.getInventory().selected=0;actor.setItemInHand(InteractionHand.MAIN_HAND,stack);actor.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);float rotation=Direction.from2DDataValue(yaw).toYRot();actor.setYRot(rotation);actor.setYHeadRot(rotation);actor.setYBodyRot(rotation);
                var hit=new BlockHitResult(Vec3.atLowerCornerOf(clicked).add(row.get("x").getAsFloat(),row.get("y").getAsFloat(),row.get("z").getAsFloat()),side,clicked,false);
                var result=stack.useOn(new UseOnContext(actor,InteractionHand.MAIN_HAND,hit));boolean success=level.getBlockState(CENTER).is(BuiltInRegistries.BLOCK.get(id));String fixture=row.toString()+" result="+result;
                need(success==row.get("success").getAsBoolean(),"source placement outcome "+fixture);need(stack.getCount()==row.get("remaining").getAsInt(),"source item consumption "+fixture);
                if(success){placed++;var front=Direction.from2DDataValue(yaw).getOpposite();var anchor=family==0?CENTER:CENTER.relative(front.getCounterClockWise(),-1);var owner=(NativeProgrammableDoorEntity)level.getBlockEntity(anchor);need(owner!=null&&owner.settings().depth()==row.get("depth").getAsInt(),"source placement depth "+fixture+" native="+(owner==null?"missing":owner.settings().depth()));var state=level.getBlockState(anchor);need(state.getValue(NativeProgrammableDoorBlock.FACING).get3DDataValue()==row.get("facing").getAsInt(),"source facing "+fixture);if(family==0)need((state.getValue(NativeProgrammableDoorBlock.HINGE)==DoorHingeSide.RIGHT)==row.get("rightHinge").getAsBoolean(),"source hinge "+fixture+" native="+state);}
            }
            nearHalf(level,actor,rows);
            System.out.println("[vandorlabs][door-placement-clicks] PASS sourceCases="+rows.size()+" checks="+checks+" placed="+placed);
        }catch(Throwable failure){failure.printStackTrace();System.out.println("[vandorlabs][door-placement-clicks] FAIL "+failure);}
        finally{server.halt(false);}
    }
}
