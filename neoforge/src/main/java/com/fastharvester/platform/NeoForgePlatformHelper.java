package com.fastharvester.platform;

// 🤝 NeoForgePlatformHelper: bridge-builder and polite translator between mod logic and NeoForge quirks.
// It smiles, mediates, and sometimes uses reflection when feeling brave.

import com.fastharvester.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.minecraft.server.level.ServerLevel;
import com.fastharvester.Constants;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;

/**
 * NeoForgePlatformHelper: The futuristic friend of platform helpers!
 * <p>
 * This class helps the mod know when it's running on NeoForge, and how to check for other mods in the NeoForge universe.
 * </p>
 * <p>
 * Why does this matter? Because NeoForge is the new kid on the block, and it wants to be noticed.
 * </p>
 */
public class NeoForgePlatformHelper implements IPlatformHelper {
    /**
     * Returns the name of the platform. (Spoiler: It's "NeoForge"!)
     */
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    /**
     * Checks if a mod is loaded in the NeoForge world. Because even the future needs friends.
     */
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * Are we in a development environment? (Or is it time to show off?)
     */
    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }

    /**
     * Extract enchantments from an ItemStack via reflection where necessary.
     * Humanized aside: we peek under the hood to see what spell levels are present.
     */
    @Override
    public java.util.Map<String, Integer> getEnchantments(net.minecraft.world.item.ItemStack stack) {
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
                        @SuppressWarnings({"rawtypes","unchecked"})
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

    /**
     * Compute block drops using NeoForge's loot APIs when possible; otherwise fall back to reflection.
     * Emotional aside: we do this so blocks give sensible loot and your chests stay trustworthy.
     */
    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getBlockDrops(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.item.ItemStack tool) {
        if (level == null || state == null) return java.util.Collections.emptyList();
        // Prefer accurate server-side loot using the platform's LootContext if available.
        try {
            if (level instanceof ServerLevel serverLevel) {
                try {
                    ClassLoader cl = NeoForgePlatformHelper.class.getClassLoader();
                    Class<?> lootContextClass = Class.forName("net.minecraft.world.level.storage.loot.LootContext", true, cl);
                    Class<?>[] declared = lootContextClass.getDeclaredClasses();
                    Class<?> builderClass = null;
                    for (Class<?> c : declared) {
                        if (c.getSimpleName().equals("Builder")) { builderClass = c; break; }
                    }
                    if (builderClass != null) {
                        Object builder = null;
                        try {
                            java.lang.reflect.Constructor<?> ctor = builderClass.getConstructor(ServerLevel.class);
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
                                withParam.invoke(builder, originKey, Vec3.atCenterOf(pos));
                                java.lang.reflect.Field stateField = paramsClass.getField("BLOCK_STATE");
                                Object stateKey = stateField.get(null);
                                withParam.invoke(builder, stateKey, state);
                                java.lang.reflect.Field toolField = paramsClass.getField("TOOL");
                                Object toolKey = toolField.get(null);
                                withParam.invoke(builder, toolKey, tool);
                                BlockEntity be = serverLevel.getBlockEntity(pos);
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
                    Constants.LOG.debug("[FastHarvester][LOOT] Reflective LootContext attempt failed on NeoForge: {}", t.toString());
                }
            }
        } catch (Throwable t) {
            // fall through to reflective fallback for client or mapping differences
            Constants.LOG.debug("[FastHarvester][LOOT] Native loot lookup failed on NeoForge, falling back: {}", t.toString());
        }

        // Reflection fallback for client or mapping differences
        try {
            java.lang.reflect.Method[] methods = state.getClass().getMethods();
            for (java.lang.reflect.Method m : methods) {
                if (!"getDrops".equals(m.getName())) continue;
                Object res = null;
                try {
                    Class<?>[] params = m.getParameterTypes();
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
}

