package com.vandorlabs.physics;

public final class CruiseGeographyTest {
    private static void check(double actual,double expected){
        if(Math.abs(actual-expected)>.00001)throw new AssertionError(actual+" expected "+expected);
    }
    public static void main(String[] args){
        CruiseGeography geo=new CruiseGeography();
        double tick=40000000;
        geo.accept(-30000000,29999999,CruiseGeography.fractions(-29999999.75,29999999.75),tick);
        check(geo.at(true,tick),-29999999.75);
        check(geo.at(false,tick),29999999.75);
        geo.accept(-29999994,29999993,CruiseGeography.fractions(-29999993.75,29999993.75),tick+3);
        check(geo.at(true,tick+3.5),-29999998.75);
        check(geo.at(false,tick+4.25),29999997.25);
        check(geo.at(false,tick+15),29999993.75);
        geo.accept(2000,-500,0,tick+16);
        check(geo.at(true,tick+16),2000);
        geo.clear();
        geo.accept(2001,-500,0,tick+17);
        check(geo.at(true,tick+17),2001);
        System.out.println("Cruise geography PASS fractional motion, negative border, old aircraft, teleport and reentry");
    }
}
