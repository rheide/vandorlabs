package com.vandorlabs.appliance;

import com.vandorlabs.tiles.TileEntityChipAppliance;
import com.vandorlabs.programmer.*;
import com.vandorlabs.navigation.*;
import com.vandorlabs.blocks.FrameMachineDesign;
import net.minecraft.block.material.MapColor;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapData;
import java.util.*;

/** Bounded server-thread conversion; original documents and the physical chip remain owned by the tile. */
public final class ChipApplianceService {
    private ChipApplianceService(){}
    public static void activate(TileEntityChipAppliance tile,EntityPlayer actor){
        ItemStack chip=tile.getStackInSlot(0);if(!ChipJournal.isChip(chip)){message(actor,"Insert a Data Chip.");return;}
        try{if(tile.scanner())scan(tile,chip);else print(tile,chip,actor);}
        catch(IllegalArgumentException invalid){message(actor,invalid.getMessage());}
    }
    private static void message(EntityPlayer actor,String text){actor.sendStatusMessage(new TextComponentString(text),true);}
    public static int[] palette(){int[] result=new int[256],shades={180,220,255,135};for(int i=0;i<256;i++){MapColor color=MapColor.COLORS[i/4];if(i<4||color==null){result[i]=NavigationMap.OUTSIDE;continue;}int c=color.colorValue,s=shades[i&3];result[i]=0xff000000|((c>>16&255)*s/255)<<16|((c>>8&255)*s/255)<<8|(c&255)*s/255;}return result;}
    private static void scan(TileEntityChipAppliance tile,ItemStack original){
        ItemStack input=tile.getStackInSlot(1),chip=original.copy();
        if(input.getItem()==Items.FILLED_MAP){MapData map=(MapData)tile.getWorld().loadData(MapData.class,"map_"+input.getMetadata());if(map==null)throw new IllegalArgumentException("Map data is unavailable.");NavigationChip.writeMap(chip,new SavedMapPixels(Integer.toString(map.dimension),map.xCenter,map.zCenter,map.scale,map.colors,palette()));}
        else if(input.getItem()==Items.WRITTEN_BOOK){NBTTagCompound book=input.getTagCompound();if(book==null)throw new IllegalArgumentException("This book is empty.");NBTTagList pages=book.getTagList("pages",8);if(pages.tagCount()>100)throw new IllegalArgumentException("Book has too many pages.");StringBuilder text=new StringBuilder();for(int i=0;i<pages.tagCount()&&text.length()<ChipJournal.PAGE_LIMIT*ChipJournal.MAX_PAGES;i++){String json=pages.getStringTagAt(i);if(json.length()>32767||!boundedJson(json))throw new IllegalArgumentException("Invalid book page.");ITextComponent page;try{page=ITextComponent.Serializer.jsonToComponent(json);}catch(RuntimeException malformed){throw new IllegalArgumentException("Invalid book page.");}if(page!=null){if(text.length()>0)text.append('\n');text.append(page.getUnformattedText());}}journal(chip,book.getString("title"),text.toString(),false);}
        else if(input.getItem()==Items.ENCHANTED_BOOK){String[] words={"klaatu","berata","niktu","zyrax","veldor","mora","xen","ulthar","qor","esh","talan","vril"};StringBuilder text=new StringBuilder();while(text.length()<360){if(text.length()>0)text.append(' ');text.append(words[tile.getWorld().rand.nextInt(words.length)]);}journal(chip,"Enchanted Notes",text.toString(),true);}
        else throw new IllegalArgumentException("Place a map, written book or enchanted book on the Scanner.");
        tile.setInventorySlotContents(0,chip);tile.publish();
    }
    private static boolean boundedJson(String json){int depth=0;boolean quote=false,escape=false;for(int i=0;i<json.length();i++){char c=json.charAt(i);if(escape){escape=false;continue;}if(c=='\\'&&quote){escape=true;continue;}if(c=='"'){quote=!quote;continue;}if(!quote){if(c=='{'||c=='['){if(++depth>32)return false;}else if(c=='}'||c==']')depth--;}}return depth==0&&!quote;}
    private static void journal(ItemStack chip,String title,String text,boolean enchanting){String[] pages=new String[ChipJournal.MAX_PAGES];for(int i=0;i<pages.length;i++){int from=Math.min(text.length(),i*ChipJournal.PAGE_LIMIT),to=Math.min(text.length(),from+ChipJournal.PAGE_LIMIT);pages[i]=text.substring(from,to);}ChipJournal.write(chip,true,title,pages);if(enchanting)chip.getTagCompound().setString("JournalFont","enchanting");}
    private static void print(TileEntityChipAppliance tile,ItemStack chip,EntityPlayer actor){
        if(tile.getStackInSlot(1).isEmpty()||tile.getStackInSlot(2).isEmpty()){message(actor,"Add one ink sac and one paper per print.");return;}
        World world=tile.getWorld();if(!world.isBlockLoaded(tile.getPos().up())||!world.getCollisionBoxes(null,new net.minecraft.util.math.AxisAlignedBB(tile.getPos().up()).shrink(.1)).isEmpty()){message(actor,"Clear the space above the Printer.");return;}
        ItemStack expectedChip=chip.copy(),expectedInk=tile.getStackInSlot(1).copy(),expectedPaper=tile.getStackInSlot(2).copy();
        ItemStack result;
        if(ChipJournal.journal(chip)){StringBuilder text=new StringBuilder();for(int i=0;i<ChipJournal.MAX_PAGES;i++){String page=ChipJournal.page(chip,i);if(page.isEmpty())continue;if(text.length()>0)text.append('\n');text.append(page);}result=book(ChipJournal.title(chip),ApplianceBookText.pages(text.toString()),actor.getName());}
        else if(NavigationChip.programmed(chip)){
            if(!NavigationChip.inDimension(chip,world.provider.getDimension()))throw new IllegalArgumentException("Print this Navigation Chip in its saved dimension.");
            result=ItemMap.setupNewMap(world,NavigationChip.value(chip,"X"),NavigationChip.value(chip,"Z"),(byte)0,true,false);MapData map=(MapData)world.loadData(MapData.class,"map_"+result.getMetadata());int[] colors=palette();for(int i=4;i<colors.length;i++)if(MapColor.COLORS[i/4]==null)colors[i]=0;PrinterPalette palette=new PrinterPalette(colors);SavedMapPixels saved=NavigationChip.savedMap(chip);
            int minX=map.xCenter-64,minZ=map.zCenter-64;
            boolean covered=saved!=null&&saved.contains(minX,minZ)&&saved.contains(minX+127,minZ+127);
            int[] terrain=covered?null:NavigationService.printTerrain(world,minX,minZ);
            for(int i=0;i<map.colors.length;i++){
                int x=minX+(i&127),z=minZ+(i>>7);
                int color=saved!=null&&saved.contains(x,z)?saved.colorAt(x,z):terrain[i];
                map.colors[i]=color==NavigationMap.OUTSIDE||color==NavigationMap.ERROR?0:palette.pixel(color);
            }
            map.markDirty();
        }
        else if(CatalogChipData.catalog(chip)){List<String> recipes=new ArrayList<>();for(String id:CatalogChipData.designs(chip))for(FrameMachineDesign design:FrameMachineDesign.all())if(id.equals(design.design)||id.equals(design.block)||id.equals("vandorlabs:"+design.block)){int w=design.width,h=design.height,d=design.depth,frames=-1;com.vandorlabs.machines.CoreLayout layout=new com.vandorlabs.machines.CoreLayout(w,h,d,2,0,0);for(int y=0;y<h;y++)for(int x=0;x<w;x++)for(int z=0;z<d;z++)if(layout.frame(x,y,z))frames++;recipes.add(design.design.replace('_',' ')+"\n\nMaterials: "+frames+" Programmable Matter Blocks; 1 "+design.block.replace('_',' ')+".\n\n1. Build a "+w+" x "+d+" solid base.\n2. Raise four corner pillars to "+h+" blocks high.\n3. Join their tops with a Matter rim. Keep the sides, interior and roof center open.\n4. Replace one bottom corner with the core, facing outward.\n5. Right-click the core to form.");}List<String> pages=ApplianceBookText.recipes(recipes);if(pages.isEmpty())throw new IllegalArgumentException("This Catalog Chip has no printable multiblocks.");result=book("Multiblock Catalog",pages,actor.getName());}
        else throw new IllegalArgumentException("Insert a Journal, Navigation or Catalog Chip.");
        EntityItem output=new EntityItem(world,tile.getPos().getX()+.5,tile.getPos().getY()+1.15,tile.getPos().getZ()+.5,result);output.motionX=output.motionZ=0;output.motionY=.18;output.setDefaultPickupDelay();if(!world.spawnEntity(output))return;if(world.getTileEntity(tile.getPos())!=tile||!ItemStack.areItemStacksEqual(expectedChip,tile.getStackInSlot(0))||!ItemStack.areItemStacksEqual(expectedInk,tile.getStackInSlot(1))||!ItemStack.areItemStacksEqual(expectedPaper,tile.getStackInSlot(2))){output.setDead();return;}tile.decrStackSize(1,1);tile.decrStackSize(2,1);tile.publish();
    }
    private static ItemStack book(String title,List<String> pages,String author){ItemStack result=new ItemStack(Items.WRITTEN_BOOK);NBTTagCompound tag=new NBTTagCompound();tag.setString("title",ChipJournal.bounded(title.isEmpty()?"Printed Journal":title,32));tag.setString("author",ChipJournal.bounded(author,40));tag.setBoolean("resolved",true);NBTTagList list=new NBTTagList();for(int i=0;i<Math.min(ApplianceBookText.MAX_PAGES,pages.size());i++)list.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(pages.get(i)))));tag.setTag("pages",list);result.setTagCompound(tag);return result;}
}
