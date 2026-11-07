package com.vandorlabs.neoforge.storage;
import com.vandorlabs.neoforge.ModernFinishCatalog;
import com.vandorlabs.persistence.HousingSettings;
import com.vandorlabs.ui.ProgrammableBlockDialog;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
/** Fixed 42-byte selected-owner housing edit; inventory and private fields are never supplied. */
public record NativeStorageEdit(BlockPos pos,int window,int main,int layout,boolean enabled,int[] faces) {
 public static final int BYTES=42;
 public NativeStorageEdit {if(pos==null||window<0||!ModernFinishCatalog.validChoice(main)||layout<0||layout>2||faces==null||faces.length!=6)throw new IllegalArgumentException("Invalid Storage edit");for(int choice:faces)if(choice!=-1&&!ModernFinishCatalog.validChoice(choice))throw new IllegalArgumentException("Invalid Storage face");faces=faces.clone();}
 @Override public int[] faces(){return faces.clone();}
 public static NativeStorageEdit from(BlockPos pos,int window,ProgrammableBlockDialog.State state){return new NativeStorageEdit(pos,window,state.mainTexture,state.layout,state.facesEnabled,state.faces());}
 public NativeStorageSettings apply(NativeStorageSettings previous){var tag=new CompoundTag();previous.write(tag);tag.putInt(HousingSettings.MAIN,main);tag.putInt(HousingSettings.VERSION,1);if(layout!=previous.housing().layout()){tag.putInt(HousingSettings.LAYOUT,layout);tag.putBoolean(HousingSettings.TILE_SIDES,layout==1);}tag.putBoolean(HousingSettings.FACES_ENABLED,enabled);tag.putIntArray(HousingSettings.FACES,faces);return NativeStorageSettings.read(tag,previous);}
 public void encode(RegistryFriendlyByteBuf buffer){buffer.writeBlockPos(pos);buffer.writeInt(window);buffer.writeInt(main);buffer.writeByte(layout);buffer.writeBoolean(enabled);for(int choice:faces)buffer.writeInt(choice);}
 public static NativeStorageEdit decode(RegistryFriendlyByteBuf buffer){if(buffer.readableBytes()!=BYTES)throw new IllegalArgumentException("Invalid Storage edit length");var pos=buffer.readBlockPos();int window=buffer.readInt(),main=buffer.readInt(),layout=buffer.readUnsignedByte(),flag=buffer.readUnsignedByte();if(window<0||!ModernFinishCatalog.validChoice(main)||layout>2||flag>1)throw new IllegalArgumentException("Invalid Storage edit header");for(int i=0;i<6;i++){int value=buffer.getInt(buffer.readerIndex()+4*i);if(value!=-1&&!ModernFinishCatalog.validChoice(value))throw new IllegalArgumentException("Invalid Storage edit face");}int[] faces=new int[6];for(int i=0;i<6;i++)faces[i]=buffer.readInt();return new NativeStorageEdit(pos,window,main,layout,flag==1,faces);}
}
