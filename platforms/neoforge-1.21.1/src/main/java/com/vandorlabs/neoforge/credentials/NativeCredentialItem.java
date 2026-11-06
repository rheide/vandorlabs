package com.vandorlabs.neoforge.credentials;

import com.vandorlabs.credentials.CredentialCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

/** Stable ID and stack components; data-chip programming/vehicle behavior remains separate. */
public final class NativeCredentialItem extends Item {
    private final boolean chip;
    public NativeCredentialItem(boolean chip){super(new Properties());this.chip=chip;}
    public boolean chip(){return chip;}
    @Override public Component getName(ItemStack stack){return Component.translatable("item.vandorlabs."+(chip?"data_chip":"access_card")+"."+CredentialCatalog.companyName(NativeCredentialItems.company(stack)));}
}
