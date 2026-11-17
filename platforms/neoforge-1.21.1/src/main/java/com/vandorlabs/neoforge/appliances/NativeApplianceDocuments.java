package com.vandorlabs.neoforge.appliances;

import com.vandorlabs.navigation.SavedMapPixels;
import com.vandorlabs.appliance.ApplianceBookText;
import com.vandorlabs.neoforge.navigation.NativeNavigationChip;
import com.vandorlabs.neoforge.screen.NativeChipJournal;
import com.vandorlabs.programmer.JournalText;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.material.MapColor;

/** Vanilla document adapters. Map colors are ARGB here; Minecraft's packed map RGB helper is ABGR. */
public final class NativeApplianceDocuments {
 private static final int[] PALETTE=palette();
 private static int[] palette(){int[] colors=new int[256];int[] shade={180,220,255,135};for(int id=1;id<64;id++){MapColor color=MapColor.byId(id);if(color.id!=id)continue;for(int s=0;s<4;s++){int rgb=color.col,k=shade[s];colors[id*4+s]=0xff000000|((rgb>>16&255)*k/255)<<16|((rgb>>8&255)*k/255)<<8|(rgb&255)*k/255;}}return colors;}
 public static int[] mapPalette(){return PALETTE.clone();}
 public static byte nearest(int color){int best=0,score=Integer.MAX_VALUE;int r=color>>16&255,g=color>>8&255,b=color&255;for(int i=4;i<PALETTE.length;i++){int candidate=PALETTE[i];if(candidate==0)continue;int dr=r-(candidate>>16&255),dg=g-(candidate>>8&255),db=b-(candidate&255),distance=dr*dr+dg*dg+db*db;if(distance<score){best=i;score=distance;}}return (byte)best;}
 public static ItemStack book(String title,List<String> pages){if(pages.isEmpty()||pages.size()>50)return ItemStack.EMPTY;var content=new ArrayList<Filterable<Component>>(pages.size());for(String page:pages){if(page.length()>32700)return ItemStack.EMPTY;content.add(Filterable.passThrough(Component.literal(page)));}var stack=new ItemStack(Items.WRITTEN_BOOK);stack.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(Filterable.passThrough(JournalText.bounded(title.isEmpty()?"Printed Journal":title,32)),"Vandor Labs",0,List.copyOf(content),true));return stack;}
 public static ItemStack journalBook(ItemStack chip){if(!NativeChipJournal.journal(chip))return ItemStack.EMPTY;var data=NativeChipJournal.textData(chip);var entries=data.getList("JournalPages",net.minecraft.nbt.Tag.TAG_STRING);var text=new StringBuilder();for(int i=0;i<Math.min(JournalText.MAX_ENTRIES,entries.size());i++){String page=JournalText.bounded(entries.getString(i),JournalText.PAGE_LIMIT);if(page.isEmpty())continue;if(text.length()>0)text.append('\n');text.append(page);}return book(data.getString("JournalTitle"),ApplianceBookText.pages(text.toString()));}
 /** Scan only bounded plain text; commands, hover events and selectors cannot enter chip readers. */
 public static ItemStack scanBook(ItemStack chip,ItemStack source){var book=source.get(DataComponents.WRITTEN_BOOK_CONTENT);if(!source.is(Items.WRITTEN_BOOK)||book==null)return ItemStack.EMPTY;var text=new StringBuilder();for(var page:book.getPages(false)){if(text.length()>=JournalText.MAX_ENTRIES*JournalText.PAGE_LIMIT)break;if(text.length()>0)text.append('\n');String plain=page.getString();int available=JournalText.MAX_ENTRIES*JournalText.PAGE_LIMIT-text.length();text.append(JournalText.bounded(plain,available));}String[] pages=new String[JournalText.MAX_ENTRIES];for(int i=0;i<pages.length;i++){int from=Math.min(text.length(),i*JournalText.PAGE_LIMIT),to=Math.min(text.length(),from+JournalText.PAGE_LIMIT);pages[i]=text.substring(from,to);}var result=chip.copyWithCount(1);NativeChipJournal.write(result,true,JournalText.bounded(book.title().raw(),JournalText.TITLE_LIMIT),pages);return result;}
 public static ItemStack scanMap(ServerLevel level,ItemStack chip,ItemStack source){if(!source.is(Items.FILLED_MAP))return ItemStack.EMPTY;var data=MapItem.getSavedData(source,level);if(data==null||data.colors.length!=SavedMapPixels.PIXELS)return ItemStack.EMPTY;try{var snapshot=new SavedMapPixels(data.dimension.location().toString(),data.centerX,data.centerZ,data.scale,data.colors,PALETTE);var result=chip.copyWithCount(1);NativeNavigationChip.writeMap(result,snapshot);return result;}catch(IllegalArgumentException malformed){return ItemStack.EMPTY;}}
 private NativeApplianceDocuments(){}
}
