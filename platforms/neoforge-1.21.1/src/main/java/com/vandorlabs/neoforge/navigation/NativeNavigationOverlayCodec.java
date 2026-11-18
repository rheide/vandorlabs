package com.vandorlabs.neoforge.navigation;

import com.vandorlabs.navigation.NavigationOverlay;
import io.netty.buffer.ByteBuf;

/** Empty overlays retain the existing raster and ACK size. */
final class NativeNavigationOverlayCodec {
    static void write(ByteBuf buffer,NavigationOverlay overlay){
        if(overlay.circles.length==0&&overlay.players.length==0&&overlay.radars.length==0)return;
        buffer.writeByte(overlay.circles.length/3).writeByte(overlay.players.length/2).writeByte(overlay.radars.length/3);
        for(int field:overlay.circles)buffer.writeInt(field);
        for(int field:overlay.players)buffer.writeInt(field);
        for(int field:overlay.radars)buffer.writeInt(field);
    }
    static NavigationOverlay read(ByteBuf buffer){
        if(!buffer.isReadable())return NavigationOverlay.EMPTY;
        if(buffer.readableBytes()<3)throw new IllegalArgumentException("Truncated navigation overlay");
        int circles=buffer.readUnsignedByte(),players=buffer.readUnsignedByte(),radars=buffer.readUnsignedByte();
        if(circles>NavigationOverlay.MAX_CIRCLES||players>NavigationOverlay.MAX_PLAYERS||radars>NavigationOverlay.MAX_CIRCLES||buffer.readableBytes()!=12*circles+8*players+12*radars)
            throw new IllegalArgumentException("Invalid navigation overlay length");
        int[] ranges=new int[3*circles],points=new int[2*players],sweeps=new int[3*radars];
        for(int i=0;i<ranges.length;i++)ranges[i]=buffer.readInt();
        for(int i=0;i<points.length;i++)points[i]=buffer.readInt();
        for(int i=0;i<sweeps.length;i++)sweeps[i]=buffer.readInt();
        return new NavigationOverlay(ranges,points,sweeps);
    }
    private NativeNavigationOverlayCodec(){}
}
