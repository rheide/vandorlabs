package com.vandorlabs.gear.client;

import io.netty.channel.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.math.BlockPos;

/** Test-only delay of the original native Core packet; block and arm publications pass untouched. */
@net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class CoreTilePacketDelay extends ChannelInboundHandlerAdapter {
    private static final class Held {final ChannelHandlerContext context;final Object packet;Held(ChannelHandlerContext context,Object packet){this.context=context;this.packet=packet;}}
    private final BlockPos core,root;private boolean parent;private final List<Held> held=new ArrayList<>();private boolean armed;private int until=-1,delayed;
    public CoreTilePacketDelay(BlockPos core,BlockPos root){this.core=core.toImmutable();this.root=root.toImmutable();Minecraft.getMinecraft().getConnection().getNetworkManager().channel().pipeline().addBefore("packet_handler","vandor_core_delay",this);}
    public synchronized void arm(boolean parent){this.parent=parent;armed=true;until=-1;}
    @Override public synchronized void channelRead(ChannelHandlerContext context,Object packet)throws Exception{
        if(armed&&packet instanceof SPacketUpdateTileEntity){SPacketUpdateTileEntity tile=(SPacketUpdateTileEntity)packet;if(parent?root.equals(tile.getPos())&&tile.getNbtCompound().getCompoundTag("ArmPayload").getBoolean("Materialized"):core.equals(tile.getPos())&&"vandorlabs:constructor".equals(tile.getNbtCompound().getString("id"))){if(held.size()>=16)throw new IllegalStateException("Bounded native Core packet delay");if(until<0)until=ArmCoreTransitionObserver.frames()+12;System.out.println("[vandorlabs][core-handoff] held "+(parent?"parent materialized header":"native Core NBT")+" untilFrame="+until);held.add(new Held(context,packet));delayed++;return;}}
        context.fireChannelRead(packet);
    }
    public synchronized void tick(){if(until>=0&&ArmCoreTransitionObserver.frames()>=until)release();}
    public synchronized boolean pending(){return !held.isEmpty();}public synchronized int delayed(){return delayed;}
    public synchronized void release(){armed=false;for(Held item:held)item.context.executor().execute(()->item.context.fireChannelRead(item.packet));held.clear();until=-1;}
}
