package com.fastharvester.platform;

import com.fastharvester.Constants;

/**
 * PlatformReflective: shared reflection helpers used by platform implementations.
 *
 * This centralizes the reflection-heavy fallbacks (enchantment extraction,
 * loot/blocks drops, and block-entity item setters) so platform helpers can
 * remain thin and avoid duplicating complex reflective code.
 */
public final class PlatformReflective {
    private PlatformReflective() {}

    public static java.util.Map<String, Integer> extractEnchantments(net.minecraft.world.item.ItemStack stack) {
        try {
            Class<?> enchHelper = Class.forName("net.minecraft.world.item.enchantment.EnchantmentHelper");
            java.lang.reflect.Method[] methods = enchHelper.getDeclaredMethods();
            for (java.lang.reflect.Method m : methods) {
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                if (!m.getName().equals("getEnchantments")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 1) continue;
                try {
                    m.setAccessible(true);
                    Object raw = m.invoke(null, stack);
                    if (raw instanceof java.util.Map) {
                        java.util.Map rawMap = (java.util.Map) raw;
                        java.util.Map<String,Integer> out = new java.util.HashMap<>();
                        for (Object oe : rawMap.entrySet()) {
                            java.util.Map.Entry e = (java.util.Map.Entry) oe;
                            Object key = e.getKey();
                            Object val = e.getValue();
                            String id = null;
                            if (key != null) {
                                try {
                                    java.lang.reflect.Method desc = key.getClass().getMethod("getDescriptionId");
                                    Object d = desc.invoke(key);
                                    if (d != null) id = d.toString();
                                } catch (Throwable t) { /* ignore */ }
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

    public static java.util.List<net.minecraft.world.item.ItemStack> getBlockDropsReflective(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.item.ItemStack tool) {
        if (level == null || state == null) return java.util.Collections.emptyList();
        try {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                try {
                    ClassLoader cl = PlatformReflective.class.getClassLoader();
                    Class<?> lootContextClass = Class.forName("net.minecraft.world.level.storage.loot.LootContext", true, cl);
                    Class<?>[] declared = lootContextClass.getDeclaredClasses();
                    Class<?> builderClass = null;
                    for (Class<?> c : declared) {
                        if (c.getSimpleName().equals("Builder")) { builderClass = c; break; }
                    }
                    if (builderClass != null) {
                        java.lang.Object builder = null;
                        try {
                            java.lang.reflect.Constructor<?> ctor = builderClass.getConstructor(serverLevel.getClass());
                            builder = ctor.newInstance(serverLevel);
                        } catch (NoSuchMethodException ignore) {
                            for (java.lang.reflect.Constructor<?> cstr : builderClass.getConstructors()) {
                                Class<?>[] ptypes = cstr.getParameterTypes();
                                if (ptypes.length == 1 && ptypes[0].isAssignableFrom(serverLevel.getClass())) {
                                    try { builder = cstr.newInstance(serverLevel); break; } catch (Throwable tt) { /* ignore */ }
                                }
                            }
                        }
                        if (builder != null) {
                            Class<?> paramsClass = Class.forName("net.minecraft.world.level.storage.loot.parameters.LootContextParams", true, cl);
                            java.lang.reflect.Method withParam = null;
                            for (java.lang.reflect.Method m : builderClass.getMethods()) {
                                Class<?>[] pts = m.getParameterTypes();
                                if (pts.length == 2 && pts[0].isAssignableFrom(paramsClass)) { withParam = m; break; }
                            }
                            if (withParam != null) {
                                java.lang.reflect.Field originField = paramsClass.getField("ORIGIN");
                                Object originKey = originField.get(null);
                                withParam.invoke(builder, originKey, net.minecraft.world.phys.Vec3.atCenterOf(pos));
                                java.lang.reflect.Field stateField = paramsClass.getField("BLOCK_STATE");
                                Object stateKey = stateField.get(null);
                                withParam.invoke(builder, stateKey, state);
                                java.lang.reflect.Field toolField = paramsClass.getField("TOOL");
                                Object toolKey = toolField.get(null);
                                withParam.invoke(builder, toolKey, tool);
                                net.minecraft.world.level.block.entity.BlockEntity be = serverLevel.getBlockEntity(pos);
                                if (be != null) {
                                    java.lang.reflect.Field beField = paramsClass.getField("BLOCK_ENTITY");
                                    Object beKey = beField.get(null);
                                    try {
                                        java.lang.reflect.Method opt = builderClass.getMethod("withOptionalParameter", beKey.getClass(), Object.class);
                                        opt.invoke(builder, beKey, be);
                                    } catch (NoSuchMethodException nsm) {
                                        try { withParam.invoke(builder, beKey, be); } catch (Throwable tt) { /* ignore */ }
                                    }
                                }

                                java.lang.reflect.Method createMethod = null;
                                for (java.lang.reflect.Method m : builderClass.getMethods()) {
                                    if (m.getReturnType().getName().equals(lootContextClass.getName())) { createMethod = m; break; }
                                }
                                Object lootCtx = null;
                                if (createMethod != null) {
                                    if (createMethod.getParameterCount() == 1) {
                                        Class<?> paramSetsClass = Class.forName("net.minecraft.world.level.storage.loot.parameters.LootContextParamSets", true, cl);
                                        java.lang.reflect.Field blockField = paramSetsClass.getField("BLOCK");
                                        Object blockSet = blockField.get(null);
                                        lootCtx = createMethod.invoke(builder, blockSet);
                                    } else {
                                        lootCtx = createMethod.invoke(builder);
                                    }
                                }
                                if (lootCtx != null) {
                                    for (java.lang.reflect.Method m : state.getClass().getMethods()) {
                                        if (m.getName().equals("getDrops") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().equals(lootContextClass.getName())) {
                                            Object res = m.invoke(state, lootCtx);
                                            if (res instanceof java.util.List) {
                                                @SuppressWarnings("unchecked")
                                                java.util.List<net.minecraft.world.item.ItemStack> asList = (java.util.List<net.minecraft.world.item.ItemStack>) res;
                                                return asList;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    Constants.logDebug("[LOOT] Reflective LootContext attempt failed", t);
                }
            }
        } catch (Throwable t) {
            Constants.logDebug("[LOOT] Native loot lookup failed, falling back", t);
        }

        // Reflection fallback: try various getDrops signatures (client or mapping differences)
        try {
            java.lang.reflect.Method[] methods = state.getClass().getMethods();
            for (java.lang.reflect.Method m : methods) {
                if (!"getDrops".equals(m.getName())) continue;
                Class<?>[] params = m.getParameterTypes();
                Object res = null;
                try {
                    if (params.length == 2) {
                        try { res = m.invoke(state, level, pos); } catch (Throwable t) { /* ignore */ }
                    }
                    if (res == null && params.length == 3) {
                        try { res = m.invoke(state, level, pos, (Object) null); } catch (Throwable t1) { /* ignore */ }
                        if (res == null) {
                            try { res = m.invoke(state, level, pos, tool); } catch (Throwable t2) { /* ignore */ }
                        }
                        if (res == null) {
                            try { res = m.invoke(state, level, pos, level.getRandom()); } catch (Throwable t3) { /* ignore */ }
                        }
                    }
                } catch (Throwable ignored) { continue; }
                if (res instanceof java.util.List) {
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.List<net.minecraft.world.item.ItemStack> asList = (java.util.List<net.minecraft.world.item.ItemStack>) res;
                        return asList;
                    } catch (Throwable t) { return java.util.Collections.emptyList(); }
                }
            }
        } catch (Throwable t) {
            // ignore
        }
        return java.util.Collections.emptyList();
    }

    public static boolean reflectiveUpdateFrameItemFallback(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.entity.BlockEntity be, net.minecraft.world.item.ItemStack stack) {
        if (be == null) return false;
        try {
            // Try setter methods first
            try {
                for (java.lang.reflect.Method m : be.getClass().getMethods()) {
                    String name = m.getName().toLowerCase();
                    if (!(name.contains("set") || name.contains("display") || name.contains("held") || name.contains("item"))) continue;
                    if (m.getParameterCount() != 1) continue;
                    Class<?> p = m.getParameterTypes()[0];
                    try {
                        if (stack != null && (p.isAssignableFrom(stack.getClass()) || p.getName().contains("ItemStack") || p == Object.class) || stack == null) {
                            m.setAccessible(true);
                            m.invoke(be, stack == null ? net.minecraft.world.item.ItemStack.EMPTY : stack.copy());
                            try { be.setChanged(); } catch (Throwable ignored) {}
                            try { level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3); } catch (Throwable ignored) {}
                            return true;
                        }
                    } catch (Throwable ignored) { continue; }
                }
            } catch (Throwable ignored) {}

            // Try setting common field names
            try {
                String[] fields = new String[] {"item", "displayedItem", "heldItem", "stack"};
                for (String fn : fields) {
                    try {
                        java.lang.reflect.Field fld = be.getClass().getDeclaredField(fn);
                        fld.setAccessible(true);
                        fld.set(be, stack == null ? net.minecraft.world.item.ItemStack.EMPTY : stack.copy());
                        try { be.setChanged(); } catch (Throwable ignored) {}
                        try { level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3); } catch (Throwable ignored) {}
                        return true;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Constants.logDebug("[PLATFORM] reflectiveUpdateFrameItemFallback failed at {}", t);
        }
        return false;
    }
}
