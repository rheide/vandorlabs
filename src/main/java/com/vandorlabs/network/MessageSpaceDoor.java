package com.vandorlabs.network;

import com.vandorlabs.container.ContainerSpaceDoor;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import com.vandorlabs.persistence.SpaceDoorData;
import com.vandorlabs.blocks.BlockVandorDoor;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.ArrayList;
import java.util.List;

public class MessageSpaceDoor implements IMessage {
    private BlockPos pos;
    private int design,detail,channel,slideDirection,trigger,shape;
    private boolean framed;
    private boolean middle;
    private boolean sliding,hinges,panel;
    public MessageSpaceDoor() {}
    public MessageSpaceDoor(BlockPos pos,int design,int detail,boolean framed,int channel,int slideDirection,boolean middle,boolean sliding,boolean hinges,int trigger,boolean panel,int shape) {
        this.pos=pos; this.design=design; this.detail=detail; this.framed=framed; this.channel=channel;
        this.slideDirection=slideDirection;
        this.middle=middle;
        this.sliding=sliding;
        this.hinges=hinges;
        this.trigger=trigger;
        this.panel=panel;
        this.shape=shape;
    }
    @Override public void fromBytes(ByteBuf b) {
        pos=BlockPos.fromLong(b.readLong()); design=b.readInt(); detail=b.readInt(); framed=b.readBoolean(); channel=b.readInt();
        slideDirection=b.readInt();
        middle=b.readBoolean();
        sliding=b.readBoolean();
        hinges=b.readBoolean();
        trigger=b.readInt();
        panel=b.readBoolean();
        shape=b.readInt();
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeLong(pos.toLong()); b.writeInt(design); b.writeInt(detail); b.writeBoolean(framed); b.writeInt(channel);
        b.writeInt(slideDirection);
        b.writeBoolean(middle);
        b.writeBoolean(sliding);
        b.writeBoolean(hinges);
        b.writeInt(trigger);
        b.writeBoolean(panel);
        b.writeInt(shape);
    }
    public static class Handler implements IMessageHandler<MessageSpaceDoor,IMessage> {
        @Override public IMessage onMessage(MessageSpaceDoor m,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if (!TileEntitySpaceDoor.valid(m.design,m.detail) || !TileEntitySpaceDoor.validSlideDirection(m.slideDirection)
                        || !SpaceDoorData.validTrigger(m.trigger) || m.shape<0 || m.shape>3
                        || m.channel<0 || m.pos==null || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        || !player.world.isBlockLoaded(m.pos) || !(player.openContainer instanceof ContainerSpaceDoor)) return;
                TileEntity raw=player.world.getTileEntity(m.pos);
                ContainerSpaceDoor container=(ContainerSpaceDoor)player.openContainer;
                if (!(raw instanceof TileEntitySpaceDoor) || container.member!=raw || !container.canInteractWith(player)) return;
                TileEntitySpaceDoor tile=(TileEntitySpaceDoor)raw;
                List<TileEntitySpaceDoor> shapeGroup=shapeGroup(tile);
                BlockPos mate=tile.mate();
                TileEntitySpaceDoor other=null;
                if (mate!=null) {
                    if (!player.world.isBlockLoaded(mate) || !(player.world.getTileEntity(mate) instanceof TileEntitySpaceDoor)) return;
                    other=(TileEntitySpaceDoor)player.world.getTileEntity(mate);
                    if (!other.usable(player)) return;
                }
                tile.configure(m.design,m.detail,m.framed,m.slideDirection,m.middle,m.sliding,m.hinges,m.trigger,m.panel); tile.setRedstoneChannel(m.channel);
                tile.setShape(m.shape);
                if (other!=null) { other.configure(m.design,m.detail,m.framed,m.slideDirection,m.middle,m.sliding,m.hinges,m.trigger,m.panel); other.setRedstoneChannel(m.channel); other.setShape(m.shape); }
                for (TileEntitySpaceDoor member:shapeGroup) member.setShape(m.shape);
            });
            return null;
        }

        private static List<TileEntitySpaceDoor> shapeGroup(TileEntitySpaceDoor tile) {
            List<TileEntitySpaceDoor> result=new ArrayList<>();
            result.add(tile);
            World world=tile.getWorld();
            IBlockState state=world.getBlockState(tile.getPos());
            EnumFacing positive=state.getValue(BlockVandorDoor.FACING).rotateYCCW();
            for (EnumFacing side:new EnumFacing[]{positive,positive.getOpposite()})
                for (int distance=1;distance<64;distance++) {
                    BlockPos pos=tile.getPos().offset(side,distance);
                    if (!world.isBlockLoaded(pos) || !world.isBlockLoaded(pos.up())) break;
                    IBlockState other=world.getBlockState(pos);
                    if (other.getBlock()!=state.getBlock()
                            || other.getValue(BlockVandorDoor.HALF)!=BlockDoor.EnumDoorHalf.LOWER
                            || other.getValue(BlockVandorDoor.FACING)
                            !=state.getValue(BlockVandorDoor.FACING)
                            || world.getBlockState(pos.up()).getBlock()!=other.getBlock()) break;
                    TileEntity raw=world.getTileEntity(pos);
                    if (!(raw instanceof TileEntitySpaceDoor)) break;
                    TileEntitySpaceDoor member=(TileEntitySpaceDoor)raw;
                    if (member.getShape()!=tile.getShape()
                            || member.getPlacementDepth()!=tile.getPlacementDepth()
                            || member.isSliding()!=tile.isSliding()
                            || member.isFramed()!=tile.isFramed()) break;
                    result.add(member);
                }
            return result;
        }
    }
}
