package com.vandorlabs.neoforge.client;

import com.vandorlabs.credentials.CredentialCatalog;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Inventory-backed selector; its sample is cosmetic and never owns a player's credential. */
public final class NativeCredentialFilterPicker implements NativeCredentialFilter {
    private final IntFunction<ItemStack> companyItems;
    private final boolean chip;
    private int cachedMask;
    private boolean resolved;
    private ItemStack sample=ItemStack.EMPTY;
    private String label="";
    public NativeCredentialFilterPicker(IntFunction<ItemStack> companyItems,boolean chip){this.companyItems=Objects.requireNonNull(companyItems);this.chip=chip;}
    @Override public boolean click(double x,double y,int button,int left,int top,int mask,IntConsumer selected){
        int next=CredentialCatalog.filterSelection(x,y,button,left,top,mask);
        if(next==CredentialCatalog.NO_SELECTION)return false;
        selected.accept(next);return true;
    }
    private void resolve(int mask){
        if(resolved&&mask==cachedMask)return;
        int company=CredentialCatalog.sampleCompany(mask);
        sample=company<0?ItemStack.EMPTY:Objects.requireNonNull(companyItems.apply(company),"Credential sample provider").copyWithCount(1);
        label=CredentialCatalog.label(chip,mask);cachedMask=mask;resolved=true;
    }
    @Override public void render(GuiGraphics graphics,Font font,int left,int top,int mask){
        resolve(mask);int x=left+136;
        graphics.fill(x-1,top-1,x+17,top+17,0xFF647480);graphics.fill(x,top,x+16,top+16,0xFF28343E);
        graphics.fill(left,top-1,left+128,top+17,sample.isEmpty()?0xFF45765A:0xFF28343E);
        graphics.drawString(font,label,left+4,mask==CredentialCatalog.ANY?top+4:top,0xFFDAE8F0,false);
        if(mask!=CredentialCatalog.ANY)graphics.drawString(font,"Click to clear",left+4,top+9,0xFFAABCC8,false);
        graphics.renderItem(sample,x,top);
    }
}
