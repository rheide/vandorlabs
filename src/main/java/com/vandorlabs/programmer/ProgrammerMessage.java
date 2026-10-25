package com.vandorlabs.programmer;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.*;

/** Lengths are bounded before decoding or allocating; all world access is scheduled. */
public final class ProgrammerMessage implements IMessage {
    private int window;private boolean program,journal;private String title="";private final String[] pages=new String[ChipJournal.MAX_PAGES];private boolean valid;
    boolean decodedValid(){return valid;}
    public ProgrammerMessage(){}
    public ProgrammerMessage(int window,boolean program,boolean journal,String title,String[] pages){this.window=window;this.program=program;this.journal=journal;this.title=title;System.arraycopy(pages,0,this.pages,0,this.pages.length);valid=true;}
    private static String read(ByteBuf b,int limit){int length=b.readUnsignedShort();if(length>limit*4||length>b.readableBytes())throw new IllegalArgumentException("Invalid programmer text length");String text=b.toString(b.readerIndex(),length,StandardCharsets.UTF_8);b.skipBytes(length);if(text.length()>limit)throw new IllegalArgumentException("Programmer text too long");return text;}
    private static void write(ByteBuf b,String text,int limit){byte[] bytes=ChipJournal.bounded(text,limit).getBytes(StandardCharsets.UTF_8);b.writeShort(bytes.length);b.writeBytes(bytes);}
    @Override public void fromBytes(ByteBuf b){try{window=b.readInt();program=b.readBoolean();journal=b.readBoolean();title=read(b,ChipJournal.TITLE_LIMIT);for(int i=0;i<pages.length;i++)pages[i]=read(b,ChipJournal.PAGE_LIMIT);valid=!b.isReadable();}catch(RuntimeException ex){valid=false;}}
    @Override public void toBytes(ByteBuf b){b.writeInt(window);b.writeBoolean(program);b.writeBoolean(journal);write(b,title,ChipJournal.TITLE_LIMIT);for(String page:pages)write(b,page,ChipJournal.PAGE_LIMIT);}
    public static final class Handler implements IMessageHandler<ProgrammerMessage,IMessage>{
        @Override public IMessage onMessage(ProgrammerMessage message,MessageContext context){net.minecraft.entity.player.EntityPlayerMP player=context.getServerHandler().player;player.getServerWorld().addScheduledTask(()->{
            if(!message.valid||!(player.openContainer instanceof ProgrammerContainer)||player.openContainer.windowId!=message.window)return;
            ProgrammerContainer container=(ProgrammerContainer)player.openContainer;if(!container.canInteractWith(player))return;
            container.tile.configure(message.journal,message.title,message.pages);if(message.program)container.tile.program();container.detectAndSendChanges();
        });return null;}
    }
}
