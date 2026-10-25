package com.vandorlabs.programmer;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Lengths are bounded before decoding or allocating; all world access is scheduled. */
public final class ProgrammerMessage implements IMessage {
    public static final int DRAFT=0,PROGRAM=1,COPY=2;
    private int window,operation;private boolean journal;private String title="";private final String[] pages=new String[ChipJournal.MAX_PAGES];private boolean valid;
    boolean decodedValid(){return valid;}
    public ProgrammerMessage(){}
    public ProgrammerMessage(int window,boolean program,boolean journal,String title,String[] pages){this.window=window;this.operation=program?PROGRAM:DRAFT;this.journal=journal;this.title=title;java.util.Arrays.fill(this.pages,"");System.arraycopy(pages,0,this.pages,0,Math.min(pages.length,this.pages.length));valid=true;}
    public static ProgrammerMessage copy(int window){ProgrammerMessage message=new ProgrammerMessage(window,false,false,"",new String[0]);message.operation=COPY;return message;}
    private static String read(ByteBuf b,int limit){int length=b.readUnsignedShort();if(length>limit*4||length>b.readableBytes())throw new IllegalArgumentException("Invalid programmer text length");String text=b.toString(b.readerIndex(),length,StandardCharsets.UTF_8);b.skipBytes(length);if(text.length()>limit)throw new IllegalArgumentException("Programmer text too long");return text;}
    private static void write(ByteBuf b,String text,int limit){byte[] bytes=ChipJournal.bounded(text,limit).getBytes(StandardCharsets.UTF_8);b.writeShort(bytes.length);b.writeBytes(bytes);}
    @Override public void fromBytes(ByteBuf b){try{window=b.readInt();operation=b.readUnsignedByte();if(operation>COPY)throw new IllegalArgumentException("Unknown programmer operation");journal=b.readBoolean();title=read(b,ChipJournal.TITLE_LIMIT);for(int i=0;i<pages.length;i++)pages[i]=read(b,ChipJournal.PAGE_LIMIT);valid=!b.isReadable();}catch(RuntimeException ex){valid=false;}}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeByte(operation);b.writeBoolean(journal);write(b,title,ChipJournal.TITLE_LIMIT);for(String page:pages)write(b,page,ChipJournal.PAGE_LIMIT);}
    public static final class Handler implements IMessageHandler<ProgrammerMessage,IMessage>{
        public static boolean apply(net.minecraft.entity.player.EntityPlayerMP player,ProgrammerMessage message){
            if(!message.valid||!(player.openContainer instanceof ProgrammerContainer)||player.openContainer.windowId!=message.window)return false;
            ProgrammerContainer container=(ProgrammerContainer)player.openContainer;if(!container.canInteractWith(player))return false;boolean result=true;
            if(message.operation==COPY)result=container.tile.copyInput();else{container.tile.configure(message.journal,message.title,message.pages);if(message.operation==PROGRAM)result=container.tile.program();}container.detectAndSendChanges();return result;
        }
        @Override public IMessage onMessage(ProgrammerMessage message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->apply(player,message));return null;}
    }
}
