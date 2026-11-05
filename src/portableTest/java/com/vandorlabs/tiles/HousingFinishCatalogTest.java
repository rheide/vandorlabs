package com.vandorlabs.tiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Saved legacy indices and authored texture paths must survive the source extraction. */
public final class HousingFinishCatalogTest {
    private HousingFinishCatalogTest() { }
    public static void main(String[] args) throws Exception {
        if (HousingFinishCatalog.legacyCount() != 78) throw new AssertionError("Legacy index count moved");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < HousingFinishCatalog.legacyCount(); i++) {
            String id = HousingFinishCatalog.id(i);
            if (HousingFinishCatalog.index(id) != i) throw new AssertionError("Index identity lost: " + id);
            digest.update((id + "\0" + HousingFinishCatalog.texture(i) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format("%02x", value & 255));
        if (!"2d22f07833b649d7bf1972ee9dada5fbe00da67d5357e328f9c2dd03c07f50c5".equals(hash.toString())) {
            throw new AssertionError("Previous-release ordered finish identities changed: " + hash);
        }
        if (HousingFinishCatalog.index("absent_finish") != -1) throw new AssertionError("Unknown ID resolved");
        System.out.println("Housing finish legacy identity contracts PASS");
    }
}
