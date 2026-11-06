package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.persistence.TrapdoorData;
import com.vandorlabs.redstone.ChannelList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** World-free packet boundaries, including complete-list validation before array allocation. */
public final class TrapdoorEditChecks {
    private static int checks;
    private static void need(boolean ok){checks++;if(!ok)throw new AssertionError("Trapdoor edit codec");}
    private static void rejects(ByteBuf bytes){try{NativeTrapdoorEdit.decode(bytes,v->v==42);throw new AssertionError("Accepted malformed trapdoor edit");}catch(IllegalArgumentException expected){checks++;}finally{bytes.release();}}
    public static void main(String[] args) {
        for(int flags=0;flags<256;flags++)for(int position=0;position<3;position++)for(int layout=0;layout<3;layout++) {
            int[] ids=new int[flags%3==0?0:flags%3==1?1:ChannelList.MAX_CHANNELS];
            for(int i=0;i<ids.length;i++)ids[i]=1000+i;
            var c=new TrapdoorData(42,position,layout,flags%3,(flags&16)!=0,(flags&2)!=0,(flags&1)!=0,(flags&4)!=0,(flags&8)!=0);
            var s=new NativeTrapdoorSettings(c,ChannelList.of(ids),flags%4,flags%3,(flags&64)!=0,(flags&128)!=0);
            var edit=new NativeTrapdoorEdit(17,s,(flags&32)!=0);var bytes=Unpooled.buffer();
            edit.encode(bytes);need(bytes.readableBytes()==15+4*ids.length);
            var actual=NativeTrapdoorEdit.decode(bytes,v->v==42);var d=actual.settings().choices();
            need(!bytes.isReadable()&&actual.window()==17&&actual.inverted()==edit.inverted());
            need(actual.settings().channels().equals(s.channels())&&actual.settings().facing()==s.facing()
                    &&actual.settings().hingeSide()==s.hingeSide()&&actual.settings().rotationFlipped()==s.rotationFlipped()
                    &&actual.settings().diagonal()==s.diagonal());
            need(d.texture==42&&d.position==position&&d.layout()==layout&&d.trigger==c.trigger&&d.tileTexture==c.tileTexture
                    &&d.cover==c.cover&&d.sliding==c.sliding&&d.intoWall==c.intoWall&&d.overSurface==c.overSurface);
            bytes.release();
        }
        var valid=Unpooled.buffer();new NativeTrapdoorEdit(1,new NativeTrapdoorSettings(new TrapdoorData(42,1,2,0,true,false,true,false,true),ChannelList.of(123),2,1,false,false),false).encode(valid);
        for(int length=0;length<valid.readableBytes();length++)rejects(valid.copy(0,length));
        rejects(valid.copy().writeByte(0));
        for(int[] invalid:new int[][]{{0,-1},{4,-1},{8,3},{9,3},{11,4},{12,3},{13,3},{14,65},{15,-1}}) {
            var broken=valid.copy();if(invalid[0]==0||invalid[0]==4||invalid[0]==15)broken.setInt(invalid[0],invalid[1]);else broken.setByte(invalid[0],invalid[1]);rejects(broken);
        }
        valid.release();
        System.out.println("Native Trapdoor edit PASS checks="+checks+" body=15+4N bytes max="+NativeTrapdoorEdit.MAX_BYTES+" malformed lengths/ranges/channels refused");
    }
}
