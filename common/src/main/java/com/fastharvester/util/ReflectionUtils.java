package com.fastharvester.util;

import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;
import java.util.Map;

public class ReflectionUtils {
    @SuppressWarnings({"unchecked","rawtypes"})
    public static java.util.Map<String, Integer> readEnchantmentsFromStack(ItemStack stack) {
        try {
            // Try EnchantmentHelper.getEnchantments(ItemStack) via reflection
            Class<?> enchHelper = Class.forName("net.minecraft.world.item.enchantment.EnchantmentHelper");
            Method[] methods = enchHelper.getDeclaredMethods();
            for (Method m : methods) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (!m.getName().equals("getEnchantments")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 1) continue;
                try {
                    m.setAccessible(true);
                    Object raw = m.invoke(null, stack);
                    if (raw instanceof Map) {
                        Map<?,?> rawMap = (Map) raw;
                        java.util.Map<String,Integer> out = new java.util.HashMap<>();
                        for (Map.Entry<?,?> e : rawMap.entrySet()) {
                            Object key = e.getKey();
                            Object val = e.getValue();
                            String id = null;
                            if (key != null) {
                                try {
                                    Method desc = key.getClass().getMethod("getDescriptionId");
                                    Object d = desc.invoke(key);
                                    if (d != null) id = d.toString();
                                } catch (Throwable t) {
                                    // ignore
                                }
                                if (id == null) id = key.toString();
                            }
                            int level = 0;
                            if (val instanceof Number) level = ((Number) val).intValue();
                            else if (val != null) {
                                try { level = Integer.parseInt(val.toString()); } catch (Throwable tt) { level = 0; }
                            }
                            if (id != null) out.put(id, level);
                        }
                        return out;
                    }
                } catch (Throwable t) {
                    // ignore and try next
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return java.util.Collections.emptyMap();
    }
}
