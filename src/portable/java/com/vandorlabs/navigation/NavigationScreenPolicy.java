package com.vandorlabs.navigation;

/** Optional power and credential requirements are independent, cumulative gates. */
public final class NavigationScreenPolicy {
    private NavigationScreenPolicy(){}
    public static boolean visible(boolean redstone,boolean security,boolean powered,boolean credential){
        return (!redstone||powered)&&(!security||credential);
    }
}
