package com.vandorlabs.rack;

import net.minecraft.nbt.NBTTagCompound;
import java.util.*;

/** Preserves opaque legacy data without interpreting a capability's contents or freezing live capability changes. */
public final class LegacyAutopilotData {
    private static final Set<String> OWN = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "id","x","y","z","ForgeData","ForgeCaps","Channel","Channels","RedstoneChannels","AutopilotTrigger","RouteMarker","RouteMarkerMeta",
            "AutopilotHigh","AutopilotEnabled","AutopilotStatus","AutopilotDepartureAt","AutopilotDepartureSeat",
            "AutopilotDepartureOwnerMost","AutopilotDepartureOwnerLeast","AutopilotDepartureMode","AutopilotDepartureHeight",
            "AutopilotDepartureDirect","AutopilotDepartureWaypoint")));
    private final NBTTagCompound extras = new NBTTagCompound();
    private NBTTagCompound originalCaps;
    private final NBTTagCompound capsAtRead;
    public LegacyAutopilotData(NBTTagCompound original, NBTTagCompound nativeAtRead) {
        for (String key : original.getKeySet()) if (!OWN.contains(key)) extras.setTag(key, original.getTag(key).copy());
        originalCaps = original.hasKey("ForgeCaps",10) ? original.getCompoundTag("ForgeCaps").copy() : null;
        capsAtRead = nativeAtRead.getCompoundTag("ForgeCaps").copy();
    }
    public NBTTagCompound preserve(NBTTagCompound current) {
        for (String key : extras.getKeySet()) if (!current.hasKey(key)) current.setTag(key, extras.getTag(key).copy());
        if (originalCaps != null) {
            if (current.getCompoundTag("ForgeCaps").equals(capsAtRead)) current.setTag("ForgeCaps", originalCaps.copy());
            else originalCaps = null; // A physical provider changed its state after deserialization; retain that current state.
        }
        return current;
    }
    public static NBTTagCompound moduleBackup(NBTTagCompound original) {
        NBTTagCompound result=original.copy();
        for (String key : new String[]{"id","x","y","z","ForgeCaps","ForgeData"}) result.removeTag(key);
        return result;
    }
}
