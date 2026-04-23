package com.fastharvester.platform;

// 🧭 FabricPlatformHelper: translates platform specifics into friendly instructions.
// It helps the mod keep calm and carry on across Fabric's APIs.

import com.fastharvester.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.fastharvester.Constants;
import net.minecraft.world.phys.Vec3;

/**
 * FabricPlatformHelper: The Fabric fashionista of platform helpers!
 * <p>
 * This class tells the rest of the mod when it's running on Fabric, and how to play nice with other mods in the Fabric ecosystem.
 * </p>
 * <p>
 * Why does this matter? Because every platform wants to feel special, and Fabric is no exception.
 * </p>
 */
public class FabricPlatformHelper implements IPlatformHelper {
    /**
     * Returns the name of the platform. Spoiler: It's always "Fabric" here.
     */
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    /**
     * Checks if a mod is loaded in the Fabric universe. Because friends are important!
     */
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /**
     * Are we in a development environment? (Or is this the real deal?)
     */
    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
    
    /**
     * Extract enchantments from an ItemStack and return them as a simple map.
     * Humanized aside: we pry into the stack to see what magical stickers it has.
     */
    @Override
    public java.util.Map<String, Integer> getEnchantments(ItemStack stack) {
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
     * Attempt to compute correct block drops using the platform's LootContext if possible.
     * Emotional aside: we try our best to produce realistic drops so players don't feel cheated.
     */
    @Override
    public java.util.List<ItemStack> getBlockDrops(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        if (level == null || state == null) return java.util.Collections.emptyList();
        // Prefer accurate server-side loot using the platform's LootContext if available.
        try {
            if (level instanceof ServerLevel serverLevel) {
                try {
                    // Reflectively build a LootContext using whatever builder signature is available in mappings
                    ClassLoader cl = FabricPlatformHelper.class.getClassLoader();
                    Class<?> lootContextClass = Class.forName("net.minecraft.world.level.storage.loot.LootContext", true, cl);
                    Class<?>[] declared = lootContextClass.getDeclaredClasses();
                    Class<?> builderClass = null;
                    for (Class<?> c : declared) {
                        if (c.getSimpleName().equals("Builder")) { builderClass = c; break; }
                    }
                    if (builderClass != null) {
                        java.lang.Object builder = null;
                        // try constructor(ServerLevel)
                        try {
                            java.lang.reflect.Constructor<?> ctor = builderClass.getConstructor(ServerLevel.class);
                            builder = ctor.newInstance(serverLevel);
                        } catch (NoSuchMethodException ignore) {
                            // try other constructors
                            for (java.lang.reflect.Constructor<?> cstr : builderClass.getConstructors()) {
                                Class<?>[] ptypes = cstr.getParameterTypes();
                                if (ptypes.length == 1 && ptypes[0].isAssignableFrom(serverLevel.getClass())) {
                                    try { builder = cstr.newInstance(serverLevel); break; } catch (Throwable tt) { /* ignore */ }
                                }
                            }
                        }
                        if (builder != null) {
                            // find a method that accepts (LootContextParams, Object) or (ContextKey, Object)
                            Class<?> paramsClass = Class.forName("net.minecraft.world.level.storage.loot.parameters.LootContextParams", true, cl);
                            java.lang.reflect.Method withParam = null;
                            for (java.lang.reflect.Method m : builderClass.getMethods()) {
                                Class<?>[] pts = m.getParameterTypes();
                                if (pts.length == 2 && pts[0].isAssignableFrom(paramsClass)) { withParam = m; break; }
                            }
                            if (withParam != null) {
                                // set common parameters
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
                                    // try optional setter if present
                                    try {
                                        java.lang.reflect.Method opt = builderClass.getMethod("withOptionalParameter", beKey.getClass(), Object.class);
                                        opt.invoke(builder, beKey, be);
                                    } catch (NoSuchMethodException nsm) {
                                        // fallback to same withParam invocation
                                        try { withParam.invoke(builder, beKey, be); } catch (Throwable tt) { /* ignore */ }
                                    }
                                }

                                // find create/build method that produces a LootContext
                                java.lang.reflect.Method createMethod = null;
                                for (java.lang.reflect.Method m : builderClass.getMethods()) {
                                    if (m.getReturnType().getName().equals(lootContextClass.getName())) {
                                        createMethod = m; break;
                                    }
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
                                    // find state.getDrops(LootContext) method
                                    for (java.lang.reflect.Method m : state.getClass().getMethods()) {
                                        if (m.getName().equals("getDrops") && m.getParameterCount() == 1 && m.getParameterTypes()[0].getName().equals(lootContextClass.getName())) {
                                            Object res = m.invoke(state, lootCtx);
                                            if (res instanceof java.util.List) {
                                                @SuppressWarnings("unchecked")
                                                java.util.List<ItemStack> asList = (java.util.List<ItemStack>) res;
                                                return asList;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    Constants.LOG.debug("[FastHarvester][LOOT] Reflective LootContext attempt failed: {}", t.toString());
                }
            }
        } catch (Throwable t) {
            // fall through to reflective fallback for unexpected mappings or client-side environments
            Constants.LOG.debug("[FastHarvester][LOOT] Native loot lookup failed, falling back: {}", t.toString());
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
                        java.util.List<ItemStack> asList = (java.util.List<ItemStack>) res;
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
