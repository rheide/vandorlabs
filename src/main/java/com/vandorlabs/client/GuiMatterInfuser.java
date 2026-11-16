package com.vandorlabs.client;
import com.vandorlabs.tiles.TileEntityMatterInfuser;
import com.vandorlabs.container.ContainerMatterInfuser;
import com.vandorlabs.ui.InfuserDialog;
import com.vandorlabs.rack.ModuleBatteryData;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.client.gui.inventory.GuiContainer;
/** Standard machine dialog: sunlight and work/charge, with physical furnace ownership slots. */
public final class GuiMatterInfuser extends GuiContainer {
 private final TileEntityMatterInfuser tile;
 public GuiMatterInfuser(InventoryPlayer player,TileEntityMatterInfuser tile){super(new ContainerMatterInfuser(player,tile));this.tile=tile;xSize=InfuserDialog.WIDTH;ySize=InfuserDialog.HEIGHT;}
 @Override public void initGui(){super.initGui();buttonList.clear();buttonList.add(new net.minecraft.client.gui.GuiButton(InfuserDialog.DONE_ID,guiLeft+InfuserDialog.DONE_X,guiTop+InfuserDialog.DONE_Y,InfuserDialog.DONE_WIDTH,InfuserDialog.DONE_HEIGHT,InfuserDialog.DONE));}
 @Override protected void actionPerformed(net.minecraft.client.gui.GuiButton button){if(button.id==InfuserDialog.DONE_ID)mc.player.closeScreen();}
 @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();super.drawScreen(x,y,partial);renderHoveredToolTip(x,y);}
 public boolean controlsFit(){if(guiLeft<0||guiTop<0||guiLeft+xSize>width||guiTop+ySize>height||InfuserDialog.FOOTER_Y+fontRenderer.FONT_HEIGHT>ySize)return false;for(net.minecraft.inventory.Slot slot:inventorySlots.inventorySlots)if(slot.xPos<0||slot.yPos<0||slot.xPos+16>xSize||slot.yPos+16>ySize)return false;return true;}
 private net.minecraft.item.ItemStack battery(){net.minecraft.item.ItemStack input=tile.getStackInSlot(0);return ModuleBatteryData.isBattery(input)?input:tile.getStackInSlot(2);}
 private boolean charging(){return ModuleBatteryData.isBattery(battery());}
 @Override protected void drawGuiContainerBackgroundLayer(float partial,int x,int y){
  drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+ySize,InfuserDialog.STYLE.body);drawRect(guiLeft,guiTop,guiLeft+xSize,guiTop+InfuserDialog.STYLE.headerHeight,InfuserDialog.STYLE.header);
  for(int slot=0;slot<39;slot++){int sx=guiLeft+InfuserDialog.slotX(slot),sy=guiTop+InfuserDialog.slotY(slot);drawRect(sx-1,sy-1,sx+17,sy+17,InfuserDialog.FRAME);drawRect(sx,sy,sx+16,sy+16,InfuserDialog.SLOT);}
  int left=guiLeft+InfuserDialog.PROGRESS_X,top=guiTop+InfuserDialog.PROGRESS_Y;drawRect(left-1,top-1,left+InfuserDialog.PROGRESS_WIDTH+1,top+InfuserDialog.PROGRESS_HEIGHT+1,InfuserDialog.FRAME);drawRect(left,top,left+InfuserDialog.PROGRESS_WIDTH,top+InfuserDialog.PROGRESS_HEIGHT,InfuserDialog.SLOT);
  int fill=InfuserDialog.fill(tile.getField(2),charging(),ModuleBatteryData.energy(battery()),ModuleBatteryData.CAPACITY);if(fill>0)drawRect(left,top,left+fill,top+InfuserDialog.PROGRESS_HEIGHT,InfuserDialog.PROGRESS);
 }
 @Override protected void drawGuiContainerForegroundLayer(int x,int y){
  fontRenderer.drawString(InfuserDialog.TITLE,InfuserDialog.TITLE_X,InfuserDialog.TITLE_Y,InfuserDialog.TITLE_COLOR);DialogHeader.logo(0,0,xSize,InfuserDialog.STYLE.logoHeaderHeight);
  boolean sun=tile.hasSunlight(),battery=charging();fontRenderer.drawString(InfuserDialog.sunlight(sun),InfuserDialog.SUN_X,InfuserDialog.SUN_Y,InfuserDialog.sunlightColor(sun));
  fontRenderer.drawString(InfuserDialog.INPUT,InfuserDialog.INPUT_LABEL_X,InfuserDialog.LABEL_ROW_Y,InfuserDialog.TEXT_COLOR);fontRenderer.drawString(InfuserDialog.CATALYST,InfuserDialog.CATALYST_LABEL_X,InfuserDialog.LABEL_ROW_Y,InfuserDialog.TEXT_COLOR);fontRenderer.drawString(InfuserDialog.OUTPUT,InfuserDialog.OUTPUT_LABEL_X,InfuserDialog.LABEL_ROW_Y,InfuserDialog.TEXT_COLOR);
  fontRenderer.drawString(InfuserDialog.workLabel(battery),InfuserDialog.PROGRESS_X,InfuserDialog.LABEL_ROW_Y,InfuserDialog.TEXT_COLOR);fontRenderer.drawString(fontRenderer.trimStringToWidth(InfuserDialog.detail(tile.getField(2),battery,ModuleBatteryData.energy(battery()),ModuleBatteryData.CAPACITY),InfuserDialog.PROGRESS_WIDTH),InfuserDialog.PROGRESS_X,InfuserDialog.DETAIL_Y,InfuserDialog.MUTED_COLOR);
  fontRenderer.drawString(InfuserDialog.INVENTORY,InfuserDialog.INVENTORY_X,InfuserDialog.INVENTORY_LABEL_Y,InfuserDialog.TEXT_COLOR);fontRenderer.drawString(InfuserDialog.CATALYST_LEFT,InfuserDialog.LEFT_X,InfuserDialog.CATALYST_LABEL_Y,InfuserDialog.MUTED_COLOR);fontRenderer.drawString(InfuserDialog.catalyst(tile.getField(0)),InfuserDialog.LEFT_X,InfuserDialog.CATALYST_VALUE_Y,InfuserDialog.TEXT_COLOR);fontRenderer.drawString(InfuserDialog.recipe(battery),InfuserDialog.LEFT_X,InfuserDialog.RECIPE_HINT_Y,InfuserDialog.MUTED_COLOR);
  fontRenderer.drawString(InfuserDialog.status(!tile.getStackInSlot(0).isEmpty(),!tile.getStackInSlot(2).isEmpty(),sun,tile.getField(0)>0||TileEntityMatterInfuser.fuelTicks(tile.getStackInSlot(1))>0,battery),InfuserDialog.LEFT_X,InfuserDialog.FOOTER_Y,InfuserDialog.MUTED_COLOR);
 }
}
