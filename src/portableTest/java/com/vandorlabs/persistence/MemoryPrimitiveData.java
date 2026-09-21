package com.vandorlabs.persistence;

import java.util.HashMap;
import java.util.Map;

final class MemoryPrimitiveData implements PrimitiveData {
    private final Map<String,Object> values=new HashMap<>();
    @Override public boolean contains(String key) { return values.containsKey(key); }
    @Override public String getString(String key) {
        Object value=values.get(key); return value instanceof String?(String)value:"";
    }
    @Override public int getInt(String key) {
        Object value=values.get(key); return value instanceof Integer?(Integer)value:0;
    }
    @Override public long getLong(String key) {
        Object value=values.get(key); return value instanceof Long?(Long)value:0;
    }
    @Override public double getDouble(String key) {
        Object value=values.get(key); return value instanceof Double?(Double)value:0;
    }
    @Override public boolean getBoolean(String key) {
        Object value=values.get(key); return value instanceof Boolean&&(Boolean)value;
    }
    @Override public void putString(String key,String value) { values.put(key,value); }
    @Override public void putInt(String key,int value) { values.put(key,value); }
    @Override public void putLong(String key,long value) { values.put(key,value); }
    @Override public void putDouble(String key,double value) { values.put(key,value); }
    @Override public void putBoolean(String key,boolean value) { values.put(key,value); }
}
