package com.vandorlabs.persistence;

import com.vandorlabs.redstone.ChannelList;

public final class ChannelMembershipDataTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        ChannelList fallback = ChannelList.of(12);
        require("RedstoneChannels".equals(ChannelMembershipData.CHANNELS), "saved list key");
        require(ChannelMembershipData.read(null, fallback) == fallback, "legacy scalar when list absent or mistyped");
        require(ChannelMembershipData.read(new int[]{-1}, fallback) == fallback, "negative saved list fallback");
        require(ChannelMembershipData.read(new int[65], fallback) == fallback, "oversized saved list fallback");
        require(ChannelMembershipData.read(new int[0], fallback).isEmpty(), "explicit empty list must override scalar");
        require(ChannelMembershipData.read(new int[]{5, 0, 1, 5}, fallback).equals(ChannelList.of(1, 5)), "canonical legacy membership");
        int[] maximum = new int[64];
        for (int i = 0; i < maximum.length; i++) maximum[i] = Integer.MAX_VALUE - i;
        ChannelList loaded = ChannelMembershipData.read(maximum, fallback);
        require(loaded.size() == 64 && ChannelMembershipData.read(loaded.toArray(), fallback).equals(loaded), "64 maximum integers exact persistence");
        maximum[0] = 0;
        require(loaded.size() == 64, "saved array ownership");
        System.out.println("Channel membership data PASS: scalar fallback, malformed lists, explicit empty and 64 maximum integers");
    }
}
