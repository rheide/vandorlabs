package com.vandorlabs.neoforge.screen;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Explicit disposable-world checks invoked by the opt-in server harness. */
public final class ScreenRuntimeChecks {
    private ScreenRuntimeChecks(){}
    private static void require(boolean result,String message){if(!result)throw new IllegalStateException("Modern screen: "+message);}
    public static void run(ServerLevel level){
        BlockPos pos=new BlockPos(4,80,0);level.getChunkAt(pos);level.setBlock(pos,ScreenRegistration.BLOCK.get().defaultBlockState(),3);
        var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.CREATIVE);player.setPos(4.5,81,.5);
        var menu=new ScreenMenu(3,player.getInventory(),pos);player.containerMenu=menu;
        ScreenPayload packet=new ScreenPayload(3,pos,"engineering_screen",1,2,0,2,true,"5, 9",3,-1);
        require(ScreenPayload.apply(player,packet),"authorized edit rejected");
        var tile=(ProgrammableScreenEntity)level.getBlockEntity(pos);
        require(tile.data().displayMode==1&&tile.data().animationSpeedIndex==2&&tile.sides()==2&&tile.getRedstoneChannels().toString().equals("5, 9"),"shared screen settings mismatch");
        require(tile.effectiveMode()==1,"ungated static mode changed");
        var saved=tile.saveWithoutMetadata(level.registryAccess());var restored=new ProgrammableScreenEntity(pos,tile.getBlockState());restored.loadWithComponents(saved,level.registryAccess());
        require(restored.data().selectedScreen.equals("engineering_screen")&&restored.data().housingTexture==3&&restored.data().animationSpeedIndex==2&&restored.sides()==2&&restored.getRedstoneChannels().equals(tile.getRedstoneChannels()),"saved schema roundtrip");
        var drops=net.minecraft.world.level.block.Block.getDrops(tile.getBlockState(),level,pos,tile,player,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        require(drops.size()==1&&drops.get(0).has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA),"configured screen drop missing settings");
        var dropped=drops.get(0).get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag();require(dropped.getInt("displayMode")==1&&dropped.getInt("SurfaceTextureLayout")==2&&!dropped.contains("ChannelLevel")&&!dropped.contains("channelSignal"),"configured drop loses settings or retains transient power");
        var picked=tile.getBlockState().getBlock().getCloneItemStack(tile.getBlockState(),new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.NORTH,pos,false),level,pos,player);require(picked.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA),"pick-block loses screen settings");
        String longest=ScreenArtwork.ENTRIES.stream().flatMap(entry->java.util.stream.Stream.of(entry.id(),entry.bare(),entry.framed())).max(java.util.Comparator.comparingInt(String::length)).orElseThrow();
        ScreenPayload longestId=new ScreenPayload(3,pos,longest,1,2,0,2,true,"5, 9",3,-1);
        var idBuffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());try{ScreenPayload.CODEC.encode(idBuffer,longestId);require(ScreenPayload.CODEC.decode(idBuffer).equals(longestId),"longest canonical artwork ID roundtrip");}finally{idBuffer.release();}
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());try{ScreenPayload.CODEC.encode(buffer,packet);require(ScreenPayload.CODEC.decode(buffer).equals(packet),"packet roundtrip");}finally{buffer.release();}
        int[] maximum=new int[64];for(int i=0;i<maximum.length;i++)maximum[i]=Integer.MAX_VALUE-i;
        String maximumText=com.vandorlabs.redstone.ChannelList.of(maximum).toString();
        require(maximumText.length()==766,"maximum valid channel text fixture");
        ScreenPayload maxChannels=new ScreenPayload(3,pos,"engineering_screen",1,2,0,2,true,maximumText,3,-1);
        var maxBuffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());try{ScreenPayload.CODEC.encode(maxBuffer,maxChannels);require(ScreenPayload.CODEC.decode(maxBuffer).equals(maxChannels),"64 largest channel IDs must survive codec");}finally{maxBuffer.release();}
        require(ScreenPayload.apply(player,maxChannels),"full valid maximum channel membership denied");require(ScreenPayload.apply(player,packet),"restore after maximum channel test");
        require(tile.getBlockState().getLightEmission(level,pos)==11,"static screen light emission");require(tile.getBlockState().isCollisionShapeFullBlock(level,pos)&&level.getFluidState(pos).isEmpty(),"screen must retain full solid dry volume");
        require(!ScreenPayload.apply(player,new ScreenPayload(4,pos,"engineering_screen",1,2,0,2,true,"5, 9",3,-1)),"wrong session accepted");
        require(!ScreenPayload.apply(player,new ScreenPayload(3,pos,"invalid_texture",1,2,0,2,true,"5, 9",3,-1)),"invalid artwork accepted");
        require(!ScreenPayload.apply(player,new ScreenPayload(3,pos,"engineering_screen",3,2,0,2,true,"5, 9",3,-1)),"invalid mode accepted");
        require(!ScreenPayload.apply(player,new ScreenPayload(3,pos,"engineering_screen",1,2,0,2,true,"-1",3,-1)),"invalid channels accepted");
        player.containerMenu=player.inventoryMenu;require(!ScreenPayload.apply(player,packet),"missing session accepted");player.containerMenu=menu;
        player.setPos(200,81,200);require(!ScreenPayload.apply(player,packet),"distant editor accepted");player.setPos(4.5,81,.5);
        player.setGameMode(GameType.SURVIVAL);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);require(!ScreenPayload.apply(player,packet),"survival without configurizer accepted");
        player.setGameMode(GameType.CREATIVE);tile.setChannelLevel(15);
        require(ScreenPayload.apply(player,new ScreenPayload(3,pos,"engineering_screen",2,1,2,0,true,"5, 9",3,-1)),"inverted trigger edit rejected");
        tile.setChannelLevel(15);require(tile.effectiveMode()==0,"inverted powered screen must sleep");require(tile.getBlockState().getLightEmission(level,pos)==0,"sleeping screen light emission");
        tile.setChannelLevel(0);require(tile.effectiveMode()==2,"inverted unpowered screen must animate");
        ScreenPayload.apply(player,packet);
        RedstoneOverlayRuntimeChecks.run(level);
        ScreenFamilyRuntimeChecks.run(level);
        System.out.println("[vandorlabs][modern] screen-server PASS schema, codec roundtrip, mode/speed/sides/channels, inverted power, exact menu, distance and permissions");
    }
    public static void read(ServerLevel level){
        BlockPos pos=new BlockPos(4,80,0);level.getChunkAt(pos);
        require(level.getBlockEntity(pos) instanceof ProgrammableScreenEntity,"saved screen missing");
        var tile=(ProgrammableScreenEntity)level.getBlockEntity(pos);
        require(tile.data().displayMode==1&&tile.data().animationSpeedIndex==2&&tile.sides()==2&&tile.data().housingTexture==3&&tile.getRedstoneChannels().toString().equals("5, 9"),"disk reload mismatch");
        RedstoneOverlayRuntimeChecks.read(level);
        ScreenFamilyRuntimeChecks.read(level);
        System.out.println("[vandorlabs][modern] screen-read PASS settings recovered from disk");
    }
}
