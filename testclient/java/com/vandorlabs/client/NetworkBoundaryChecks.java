package com.vandorlabs.client;

import com.vandorlabs.vehicle.VehicleNetwork;
import io.netty.buffer.*;

/** State fidelity and bounded malformed-packet behavior, without a network connection. */
public final class NetworkBoundaryChecks {
    private static void require(boolean ok,String text){if(!ok)throw new IllegalStateException("network boundary: "+text);}
    public static void main(String[] args){
        VehicleNetwork.Packet sent=new VehicleNetwork.Packet(VehicleNetwork.STATE);
        sent.entity=73;sent.token=new java.util.UUID(123,456);sent.sequence=Integer.MAX_VALUE;
        sent.flag=true;sent.serverTick=987654321;sent.x=-12345.25;sent.y=120.75;sent.z=30985.5;
        sent.dy=-.125;sent.speed=.47;sent.yaw=273.25F;sent.steering=-.625F;
        ByteBuf wire=Unpooled.buffer();
        try{
            sent.toBytes(wire);int bytes=wire.readableBytes();
            System.out.println("vehicle-state-wire bytes="+bytes+" legacy=105");
            VehicleNetwork.Packet received=new VehicleNetwork.Packet();received.fromBytes(wire.duplicate());
            require(received.valid&&received.type==sent.type&&received.entity==sent.entity&&received.token.equals(sent.token)
                &&received.sequence==sent.sequence&&received.flag==sent.flag&&received.serverTick==sent.serverTick
                &&received.x==sent.x&&received.y==sent.y&&received.z==sent.z&&received.dy==sent.dy
                &&received.speed==sent.speed&&received.yaw==sent.yaw&&received.steering==sent.steering,"state round trip");
            for(int length=0;length<bytes;length++){
                VehicleNetwork.Packet shortPacket=new VehicleNetwork.Packet();shortPacket.fromBytes(wire.slice(0,length));
                require(!shortPacket.valid,"truncated state accepted "+length);
            }
            // Every non-finite motion component must be rejected before client prediction sees it.
            sent.x=Double.NaN;ByteBuf malformed=Unpooled.buffer();
            try{sent.toBytes(malformed);received=new VehicleNetwork.Packet();received.fromBytes(malformed);require(!received.valid,"NaN state accepted");}finally{malformed.release();}
            require(bytes==82,"state wire size regression");
            for(int type=VehicleNetwork.REQUEST;type<=VehicleNetwork.MODE_SELECT;type++)if(type!=VehicleNetwork.STATE){
                VehicleNetwork.Packet packet=new VehicleNetwork.Packet(type);packet.data=new byte[17];packet.text="bounded";
                ByteBuf encoded=Unpooled.buffer();
                try{packet.toBytes(encoded);received=new VehicleNetwork.Packet();received.fromBytes(encoded);require(received.valid&&received.type==type&&received.data.length==17&&received.text.equals(packet.text),"generic packet round trip "+type);}finally{encoded.release();}
            }
            System.out.println("Network boundary PASS exact-state truncated-packets nonfinite-motion");
        }finally{wire.release();}
    }
}
