package com.vandorlabs.network;

import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelData;

import com.vandorlabs.container.ContainerSpaceDoor;
import com.vandorlabs.tiles.TileEntitySpaceDoor;
import com.vandorlabs.persistence.SpaceDoorData;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.*;

public class MessageSpaceDoor implements IMessage {
    private ChannelList channels;
    private boolean invalidChannels;
    public MessageSpaceDoor withChannels(ChannelList channels) {
        if(channels==null)throw new IllegalArgumentException("Invalid channel list");
        this.channels=channels;this.channel=channels.first();invalidChannels=false;return this;
    }
    public ChannelList getRedstoneChannels() {
        return invalidChannels?null:channels!=null?channels:ChannelList.of(Math.max(0,channel));
    }

    private int panelMode=-1,securityMode,securityAllowed=127;
    public MessageSpaceDoor withSecurity(int panelMode,int securityMode,int mask){this.panelMode=panelMode;this.securityMode=securityMode;this.securityAllowed=mask;return this;}
    private int layout=0;
    public MessageSpaceDoor withTextureLayout(int mode){layout=mode;tileTexture=mode==1;return this;}
    private BlockPos pos;
    private int design,detail,channel,slideDirection,trigger,depth,faceTexture=-1;
    private boolean framed,tileTexture;
    private boolean sliding,hinges,panel;
    public MessageSpaceDoor() {}
    public MessageSpaceDoor(BlockPos pos,int design,int detail,boolean framed,int channel,int slideDirection,int depth,boolean sliding,boolean hinges,int trigger,boolean panel) {
        this.pos=pos; this.design=design; this.detail=detail; this.framed=framed; this.channel=channel;
        this.slideDirection=slideDirection;
        this.depth=depth;
        this.sliding=sliding;
        this.hinges=hinges;
        this.trigger=trigger;
        this.panel=panel;
    }
    public MessageSpaceDoor(BlockPos pos,int design,int detail,boolean framed,int channel,int slideDirection,int depth,boolean sliding,boolean hinges,int trigger,boolean panel,int faceTexture){this(pos,design,detail,framed,channel,slideDirection,depth,sliding,hinges,trigger,panel);this.faceTexture=faceTexture;}
    public MessageSpaceDoor(BlockPos pos,int design,int detail,boolean framed,int channel,int slideDirection,int depth,boolean sliding,boolean hinges,int trigger,boolean panel,int faceTexture,boolean tileTexture){this(pos,design,detail,framed,channel,slideDirection,depth,sliding,hinges,trigger,panel,faceTexture);this.tileTexture=tileTexture;this.layout=tileTexture?1:0;}
    @Override public void fromBytes(ByteBuf b) {
        pos=BlockPos.fromLong(b.readLong()); design=b.readInt(); detail=b.readInt(); framed=b.readBoolean(); channel=b.readInt();
        slideDirection=b.readInt();
        depth=b.readInt();
        sliding=b.readBoolean();
        hinges=b.readBoolean();
        trigger=b.readInt();
        panel=b.readBoolean();faceTexture=b.readInt();tileTexture=b.readBoolean();

        panelMode=b.readUnsignedByte();securityMode=b.readUnsignedByte();securityAllowed=b.readUnsignedByte();layout=b.readUnsignedByte();channels=ChannelData.read(b,channel);invalidChannels=channels==null;
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeLong(pos.toLong()); b.writeInt(design); b.writeInt(detail); b.writeBoolean(framed); b.writeInt(channel);
        b.writeInt(slideDirection);
        b.writeInt(depth);
        b.writeBoolean(sliding);
        b.writeBoolean(hinges);
        b.writeInt(trigger);
        b.writeBoolean(panel);b.writeInt(faceTexture);b.writeBoolean(tileTexture);

        b.writeByte(panelMode<0?(panel?1:0):panelMode);b.writeByte(securityMode);b.writeByte(securityAllowed);b.writeByte(layout<0?(tileTexture?1:0):layout);ChannelData.write(b,channels!=null?channels:ChannelList.of(Math.max(0,channel)));
    }
    public static class Handler implements IMessageHandler<MessageSpaceDoor,IMessage> {
        @Override public IMessage onMessage(MessageSpaceDoor m,MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(()->{
                if (m.panelMode>2||m.panelMode==2&&m.depth!=0||m.securityMode>4||m.securityAllowed>127||!com.vandorlabs.render.TextureLayoutMode.valid(m.layout) || !TileEntitySpaceDoor.valid(m.design,m.detail) || !TileEntitySpaceDoor.validSlideDirection(m.slideDirection)
                        || !SpaceDoorData.validTrigger(m.trigger)
                        || m.faceTexture< -1 || (m.faceTexture!=-1 && !com.vandorlabs.tiles.ScreenHousingTextures.validChoice(m.faceTexture))
                        || m.depth<0 || m.depth>2 || m.channel<0 || m.pos==null || m.getRedstoneChannels()==null || !com.vandorlabs.items.ConfigurationAccess.canConfigure(player)
                        || !player.world.isBlockLoaded(m.pos) || !(player.openContainer instanceof ContainerSpaceDoor)) return;
                TileEntity raw=player.world.getTileEntity(m.pos);
                ContainerSpaceDoor container=(ContainerSpaceDoor)player.openContainer;
                if (!(raw instanceof TileEntitySpaceDoor) || container.member!=raw || !container.canInteractWith(player)) return;
                TileEntitySpaceDoor tile=(TileEntitySpaceDoor)raw;
                if(!tile.acceptsDesign(m.design) || m.slideDirection==3 && !(tile instanceof com.vandorlabs.tiles.TileEntityLargeProgrammableDoor))return;
                BlockPos mate=tile.mate();
                TileEntitySpaceDoor other=null;
                if (mate!=null) {
                    if (!player.world.isBlockLoaded(mate) || !(player.world.getTileEntity(mate) instanceof TileEntitySpaceDoor)) return;
                    other=(TileEntitySpaceDoor)player.world.getTileEntity(mate);
                    if (!other.usable(player)) return;
                }
                tile.setFaceTexture(m.faceTexture);tile.setTextureLayout(m.layout);
                tile.configure(m.design,m.detail,m.framed,m.slideDirection,m.depth==0,m.sliding,m.hinges,m.trigger,m.panel);
                tile.setPlacementDepth(m.depth);
                tile.setPanelMode(m.panelMode);tile.configureSecurity(m.securityMode,m.securityAllowed);tile.setRedstoneChannels(m.getRedstoneChannels());
                if (other!=null) {
                    other.setFaceTexture(m.faceTexture);other.setTextureLayout(m.layout);
                    other.configure(m.design,m.detail,m.framed,m.slideDirection,m.depth==0,m.sliding,m.hinges,m.trigger,m.panel);
                    other.setPlacementDepth(m.depth);
                    other.setPanelMode(m.panelMode);other.configureSecurity(m.securityMode,m.securityAllowed);other.setRedstoneChannels(m.getRedstoneChannels());
                }
            });
            return null;
        }

    }
}
