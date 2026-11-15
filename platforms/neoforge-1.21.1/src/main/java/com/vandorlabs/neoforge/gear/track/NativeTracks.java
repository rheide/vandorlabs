package com.vandorlabs.neoforge.gear.track;

import com.vandorlabs.neoforge.gear.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid="vandorlabs",bus=EventBusSubscriber.Bus.MOD)
public final class NativeTracks {
    public static MenuType<NativeTrackMenu> MENU;
    public static boolean enabled(){return Boolean.getBoolean("vandorlabs.trackRuntime");}
    @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent event){if(enabled())event.register(Registries.MENU,h->{MENU=net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((window,inventory,buffer)->new NativeTrackMenu(window,inventory,buffer.readBlockPos(),buffer.readUUID()));h.register(ResourceLocation.parse("vandorlabs:track"),MENU);});}
    @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event){NativeTrackStylePayload.register(event);}
    public static boolean open(Player actor,Level world,BlockPos pos){if(!enabled()||MENU==null||!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,world,pos)||!(world.getBlockEntity(pos) instanceof NativeTrackEntity track)||track.inert())return false;var root=track.controllerEntity();if(root!=null)return NativeGearBindings.openRoot(actor,root);if(actor instanceof ServerPlayer server){if(!server.getServer().isSameThread())return false;server.openMenu(new net.minecraft.world.SimpleMenuProvider((window,inventory,ignored)->new NativeTrackMenu(window,inventory,pos,track.identity()),track.getBlockState().getBlock().getName()),buffer->{buffer.writeBlockPos(pos);buffer.writeUUID(track.identity());});}return true;}
    public static int style(NativeGearEntity owner){return owner instanceof NativeTrackEntity track?track.style().ordinal():owner.trackDescriptor()==null?-1:owner.trackDescriptor().style().ordinal();}
    public static boolean style(ServerPlayer actor,NativeGearEntity owner,int style){if(style<0||style>2||!actor.getServer().isSameThread()||owner.getLevel()!=actor.level()||owner.inert())return false;var world=actor.serverLevel();var targets=new java.util.ArrayList<NativeTrackEntity>();if(owner instanceof NativeTrackEntity track&&track.controllerEntity()==null){if(!com.vandorlabs.neoforge.NativeConfigurationAccess.canEditDialog(actor,world,track.getBlockPos()))return false;targets.add(track);}else{if(!NativeGearAccess.menu(actor,owner)||owner.assembly()==null||owner.assembly().kind()!=com.vandorlabs.gear.core.GearTopology.FootKind.TRACK||owner.progress()!=1||!owner.completeMembers())return false;for(var cell:owner.assembly().feet()){var pos=owner.getBlockPos().offset(NativeGearOrientation.vector(owner.mount(),new BlockPos(cell.x,cell.y,cell.z)));if(!(NativeGearFormation.at(world,pos) instanceof NativeTrackEntity track))return false;targets.add(track);}}for(var track:targets)if(com.vandorlabs.neoforge.ramp.NativeRampHeldOwners.blocked(world,track.getBlockPos())||!world.mayInteract(actor,track.getBlockPos())||!actor.mayUseItemAt(track.getBlockPos(),net.minecraft.core.Direction.UP,actor.getMainHandItem()))return false;for(var track:targets)track.trackStyle(style);owner.requestRebuild();return true;}
    private NativeTracks(){}
}
