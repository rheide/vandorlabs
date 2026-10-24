package com.vandorlabs.network;

import com.vandorlabs.navigation.NavigationScreenSettings;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.tiles.TileEntityNavigationTable;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Preserve the existing navigation dialog's wire format. */
public final class MessageNavigationTable extends DisplaySettingsMessage {
    public MessageNavigationTable(){}
    public MessageNavigationTable(int window,NavigationScreenSettings settings,int mask,int behavior,ChannelList channels){super(window,settings,mask,behavior,channels);}
    @Override public void fromBytes(ByteBuf b){readHeader(b,0);if(b.isReadable())throw new IllegalArgumentException("Unexpected navigation payload");}
    @Override public void toBytes(ByteBuf b){writeHeader(b);}
    public static final class Handler implements IMessageHandler<MessageNavigationTable,IMessage>{
        @Override public IMessage onMessage(MessageNavigationTable m,MessageContext context){m.withContainer(context,c->{if(c.tile instanceof TileEntityNavigationTable)((TileEntityNavigationTable)c.tile).configureDisplay(m.settings(),false,m.mask,m.behavior,m.channels);});return null;}
    }
}
