package com.fastharvester;

/**
 * FrameScanner: The intrepid explorer of your blocky world!
 * <p>
 * This class is responsible for scanning farms, finding frames, and making sure your crops get the attention they deserve. It's loader-agnostic, so it works everywhere—like a universal translator, but for farming.
 * </p>
 * <p>
 * Why does this matter? Because without it, your crops would be lost, alone, and unharvested. And nobody wants that.
 * </p>
 * <p>
 * For the full adventure, see TECHNICAL.md (bring snacks).
 * </p>
 */
// 🔎 Emotional aside: the scanner sometimes daydreams about perfect rows of wheat. It powers through anyway.
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class FrameScanner {
    /**
     * The maximum number of frames we dare scan in a single run. Any more and the crops unionize.
     */
    public static final int MAX_FRAMES_PER_RUN = 24;

    /**
     * The maximum number of blocks to check per run. Because even farmers need a break.
     */
    /**
     * Creates a new FrameScanner. Ready to scan for farming greatness!
     */
    public FrameScanner() {}

    /**
     * Anchor: The holy trinity of farm automation—chest, frame, and hoe.
     * <p>
     * This class bundles together the key objects needed to anchor a farm. Treat it with respect (and maybe a little awe).
     * </p>
     */
    public static class Anchor {
        public final Container chest;
        public final BlockPos framePos;
        public final ItemStack hoe;

        public Anchor(Container chest, BlockPos framePos, ItemStack hoe) {
            this.chest = chest;
            this.framePos = framePos;
            this.hoe = hoe;
        }
        /**
         * String representation of the anchor for logging.
         * Emotional aside: anchors are small but full of purpose.
         */
        @Override
        public String toString() { return "Anchor[pos="+framePos+",hoe="+hoe+"]"; }
    }

    /**
    * Scans for a farm starting from a given anchor. Emits extremely verbose debug logs for every step.
    * @param anchor The anchor (chest, frame, hoe) to start scanning from.
    * @param level The world `Level` to scan in (server-level expected).
    * @return true if a valid farm was found and scanned, false otherwise.
     */
    public boolean scanFarm(Anchor anchor, Level level) {
        Constants.LOG.info("[FastHarvester][SCAN] Starting farm scan from anchor: {}", anchor);
        if (anchor == null || anchor.chest == null || anchor.hoe == null || level == null) {
            Constants.LOG.warn("[FastHarvester][SCAN] Anchor or environment missing, aborting scan.");
            return false;
        }

        int blocksScanned = 0;
        int cropsFound = 0;
        int range = Math.max(1, Config.scanRange);

        BlockPos center = anchor.framePos;
        HarvestContext ctx = new HarvestContext(anchor, level, anchor.hoe, anchor.chest, null);

        int maxRing = range;
        int baseRotation = getFrameRotation(level, center);
        boolean anyHarvested = false;

        // Use BFS-based discovery to find a connected farm area (complements previous ring scan).
        List<BlockPos> candidates = bfsDiscoverFarm(center, level, range);

        // If BFS found nothing, fall back to the old ring-style scan to preserve behavior
        if (candidates.isEmpty()) {
            Constants.LOG.debug("[FastHarvester][SCAN] BFS found no candidates, falling back to ring scan.");
            candidates = new ArrayList<>();
            for (int dx = -range; dx <= range; dx++) for (int dz = -range; dz <= range; dz++) candidates.add(center.offset(dx, 0, dz));
        }

        // Group candidates by ring distance so rotation pacing still works
        Map<Integer, List<BlockPos>> ringMap = new HashMap<>();
        int computedMaxRing = 0;
        for (BlockPos p : candidates) {
            int ring = Math.max(Math.abs(p.getX() - center.getX()), Math.abs(p.getZ() - center.getZ()));
            if (ring > maxRing) continue;
            ringMap.computeIfAbsent(ring, k -> new ArrayList<>()).add(p);
            computedMaxRing = Math.max(computedMaxRing, ring);
        }

        for (int ring = 0; ring <= computedMaxRing; ring++) {
            List<BlockPos> positions = ringMap.getOrDefault(ring, new ArrayList<>());
            for (BlockPos pos : positions) {
                BlockState state = level.getBlockState(pos);
                blocksScanned++;

                try {
                    Block block = state.getBlock();

                    // Direct fruit blocks (melons/pumpkins): harvest the fruit block itself, no replant.
                    if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
                        HarvestUtils.harvestCrop(ctx, pos, state, s -> true, s -> null);
                        anyHarvested = ctx.harvestedCount > 0;
                        cropsFound = Math.max(cropsFound, ctx.harvestedCount);
                        continue;
                    }

                    // If this is a stem, check for adjacent fruit and harvest that instead.
                    if (state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM)) {
                        Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                        boolean harvestedFruit = false;
                        for (Direction d : dirs) {
                            BlockPos npos = pos.relative(d);
                            BlockState ns = level.getBlockState(npos);
                            if ((ns.is(Blocks.MELON) && state.is(Blocks.MELON_STEM)) || (ns.is(Blocks.PUMPKIN) && state.is(Blocks.PUMPKIN_STEM))) {
                                HarvestUtils.harvestCrop(ctx, npos, ns, s -> true, s -> null);
                                harvestedFruit = true;
                                anyHarvested = ctx.harvestedCount > 0;
                                cropsFound = Math.max(cropsFound, ctx.harvestedCount);
                                break;
                            }
                        }
                        if (harvestedFruit) continue;
                    }

                    // Standard crop logic
                    boolean isCrop = block instanceof CropBlock || state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH);
                    if (!isCrop) continue;

                    // Maturity determination: read AGE defensively; different crops have different max ages.
                    int threshold = (state.is(Blocks.NETHER_WART) || state.is(Blocks.SWEET_BERRY_BUSH)) ? 3 : 7;
                    boolean mature = false;
                    try {
                        int age = state.getValue(CropBlock.AGE);
                        mature = age >= threshold;
                    } catch (Throwable t) {
                        mature = false;
                    }

                    if (!mature) continue;

                    HarvestUtils.harvestCrop(ctx, pos, state,
                            s -> {
                                try { int a = s.getValue(CropBlock.AGE); if (s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH)) return a >= 3; return a >= 7; } catch (Throwable tt) { return false; }
                            },
                            s -> {
                                try {
                                    if (s.is(Blocks.SWEET_BERRY_BUSH)) return s.setValue(CropBlock.AGE, 1);
                                    return s.setValue(CropBlock.AGE, 0);
                                } catch (Throwable tt) { return null; }
                            });
                    anyHarvested = ctx.harvestedCount > 0;
                    cropsFound = Math.max(cropsFound, ctx.harvestedCount);
                } catch (Throwable t) {
                    Constants.LOG.debug("[FastHarvester][SCAN] Exception while scanning {}: {}", center, t.toString());
                }
            }

            // After finishing this ring, advance frame rotation according to mode if we harvested anything
            if (anyHarvested) {
                int newRotation = baseRotation;
                switch (Config.rotationMode) {
                    case STEP_PER_HARVEST -> {
                        // Will be applied once at end of pass (handled below)
                    }
                    case FULL_ROTATION_PER_HARVEST -> {
                        int steps = (int) Math.floor((double)(ring + 1) * 8.0 / (maxRing + 1));
                        newRotation = (baseRotation + steps) & 7;
                        setFrameRotation(level, center, newRotation);
                    }
                    case FOLLOW_HARVEST_SPIRAL -> {
                        int steps = (int) Math.floor((double)(ring + 1) * 8.0 * maxRing / (maxRing + 1));
                        newRotation = (baseRotation + steps) & 7;
                        setFrameRotation(level, center, newRotation);
                    }
                }
            }
        }

        // Second pass: attempt neighbor-dominant auto-planting on empty farmland above, and auto-till repair when suitable.
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                BlockState cur = level.getBlockState(pos);
                if (!cur.isAir()) continue;
                BlockPos belowPos = pos.below();
                BlockState below = level.getBlockState(belowPos);

                // If we already have farmland, try to replant based on neighbors
                if (below != null && below.getBlock() == Blocks.FARMLAND) {
                    Map<Block, Integer> counts = new HashMap<>();
                    Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                    for (Direction d : dirs) {
                        BlockPos npos = pos.relative(d);
                        BlockState ns = level.getBlockState(npos);
                        Block b = ns.getBlock();
                        if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) {
                            counts.merge(b, 1, Integer::sum);
                        }
                    }
                    if (counts.isEmpty()) continue;
                    Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                    Item seed = seedForBlock(chosen);
                    if (seed == null) continue;
                    boolean taken = ChestUtils.removeOne(anchor.chest, seed);
                    if (!taken) continue;

                    BlockState plantState = chosen.defaultBlockState();
                    try { if (plantState.getBlock() instanceof CropBlock) plantState = plantState.setValue(CropBlock.AGE, 0); } catch (Throwable t) {}
                    level.setBlock(pos, plantState, 3);
                }

                // Nether-wart on soul sand: handle as a separate planting case
                if (below != null && below.getBlock() == Blocks.SOUL_SAND) {
                    Map<Block, Integer> counts = new HashMap<>();
                    Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                    for (Direction d : dirs) {
                        BlockPos npos = pos.relative(d);
                        BlockState ns = level.getBlockState(npos);
                        Block b = ns.getBlock();
                        if (b == Blocks.NETHER_WART) {
                            counts.merge(b, 1, Integer::sum);
                        }
                    }
                    if (!counts.isEmpty()) {
                        Block chosen = counts.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                        Item seed = seedForBlock(chosen);
                        if (seed != null && ChestUtils.removeOne(anchor.chest, seed)) {
                            BlockState plantState = chosen.defaultBlockState();
                            try { if (plantState.getBlock() instanceof CropBlock) plantState = plantState.setValue(CropBlock.AGE, 0); } catch (Throwable t) {}
                            level.setBlock(pos, plantState, 3);
                        }
                    }
                }

                // Auto-till: if below is dirt/grass and surrounded by farmland, convert and plant.
                if (below != null && (below.getBlock() == Blocks.DIRT || below.getBlock() == Blocks.GRASS_BLOCK)) {
                    Direction[] dirs = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
                    boolean surrounded = true;
                    for (Direction d : dirs) {
                        BlockState ns = level.getBlockState(belowPos.relative(d));
                        if (ns == null || ns.getBlock() != Blocks.FARMLAND) { surrounded = false; break; }
                    }
                    if (!surrounded) continue;

                    // Avoid tilling if this looks like part of a melon/pumpkin layout (fruit or stems nearby)
                    boolean nearMelonPumpkin = false;
                    for (Direction d : dirs) {
                        BlockState ns = level.getBlockState(pos.relative(d));
                        if (ns.is(Blocks.MELON) || ns.is(Blocks.PUMPKIN) || ns.is(Blocks.MELON_STEM) || ns.is(Blocks.PUMPKIN_STEM)) { nearMelonPumpkin = true; break; }
                    }
                    if (nearMelonPumpkin) continue;

                    // Till the dirt into farmland
                    BlockState farmland = Blocks.FARMLAND.defaultBlockState();
                    level.setBlock(belowPos, farmland, 3);

                    // Apply hoe durability
                    ItemStack before = anchor.hoe.copy();
                    com.fastharvester.DurabilityLogic.applyDamage(level, anchor.hoe, level.getRandom());
                    if (anchor.hoe.isEmpty()) {
                        HarvestUtils.handleBrokenHoe(ctx, before);
                    }

                    // After tilling, attempt neighbor-dominant planting similar to above
                    Map<Block, Integer> counts2 = new HashMap<>();
                    for (Direction d : dirs) {
                        BlockPos npos = pos.relative(d);
                        BlockState ns = level.getBlockState(npos);
                        Block b = ns.getBlock();
                        if (b == Blocks.WHEAT || b == Blocks.BEETROOTS || b == Blocks.CARROTS || b == Blocks.POTATOES || b == Blocks.MELON_STEM || b == Blocks.PUMPKIN_STEM) {
                            counts2.merge(b, 1, Integer::sum);
                        }
                    }
                    if (counts2.isEmpty()) continue;
                    Block chosen2 = counts2.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
                    Item seed2 = seedForBlock(chosen2);
                    if (seed2 == null) continue;
                    boolean taken2 = ChestUtils.removeOne(anchor.chest, seed2);
                    if (!taken2) continue;
                    BlockState plantState2 = chosen2.defaultBlockState();
                    try { if (plantState2.getBlock() instanceof CropBlock) plantState2 = plantState2.setValue(CropBlock.AGE, 0); } catch (Throwable t) {}
                    level.setBlock(pos, plantState2, 3);
                }
            }
        }

        // STEP_PER_HARVEST: advance by one step if we harvested anything this pass
        if (anyHarvested && Config.rotationMode == com.fastharvester.enums.RotationMode.STEP_PER_HARVEST) {
            int newRot = (getFrameRotation(level, center) + 1) & 7;
            setFrameRotation(level, center, newRot);
        }

        Constants.LOG.info("[FastHarvester][SCAN] Scan complete. Blocks scanned: {}, crops found: {}.", blocksScanned, cropsFound);
        return cropsFound > 0;
    }

    /**
     * Choose a seed Item for the given crop block. Returns null if unknown.
     * Humanized aside: we try to pick the seed the plant would recognize at breakfast.
     */
    private static Item seedForBlock(Block b) {
        if (b == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (b == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (b == Blocks.CARROTS) return Items.CARROT;
        if (b == Blocks.POTATOES) return Items.POTATO;
        if (b == Blocks.MELON_STEM) return Items.MELON_SEEDS;
        if (b == Blocks.PUMPKIN_STEM) return Items.PUMPKIN_SEEDS;
        // Mod crops like Torchflower may not be in Items; fall back to block's item when plausible
        try {
            String cls = b.getClass().getName().toLowerCase();
            if (cls.contains("torchflower")) return b.asItem();
        } catch (Throwable ignored) {}
        if (b == Blocks.NETHER_WART) return Items.NETHER_WART;
        return null;
    }

    /**
     * BFS-based discovery for connected farm nodes starting from `center`.
     * Emotional aside: this explores like a curious mole sniffing out crops.
     */
    private static List<BlockPos> bfsDiscoverFarm(BlockPos center, Level level, int range) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> q = new ArrayDeque<>();

        // Seed BFS by finding any crop/fruit/stem within the bounding square
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                BlockPos p = center.offset(dx, 0, dz);
                BlockState s = level.getBlockState(p);
                Block b = s.getBlock();
                boolean isSeed = s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON_STEM) || s.is(Blocks.PUMPKIN_STEM)
                        || b instanceof CropBlock || s.is(Blocks.NETHER_WART) || s.is(Blocks.SWEET_BERRY_BUSH);
                if (isSeed) {
                    visited.add(p);
                    q.add(p);
                }
            }
        }

        Constants.LOG.info("[FastHarvester][SCAN] BFS seeded {} nodes around {} (range {}).", visited.size(), center, range);

        while (!q.isEmpty()) {
            BlockPos cur = q.poll();
            result.add(cur);

            for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                BlockPos np = cur.relative(d);
                if (visited.contains(np)) continue;
                int ring = Math.max(Math.abs(np.getX() - center.getX()), Math.abs(np.getZ() - center.getZ()));
                if (ring > range) continue;
                BlockState ns = level.getBlockState(np);
                Block nb = ns.getBlock();
                boolean traverse = nb instanceof CropBlock || ns.is(Blocks.FARMLAND) || ns.is(Blocks.DIRT) || ns.is(Blocks.GRASS_BLOCK)
                        || ns.is(Blocks.MELON) || ns.is(Blocks.PUMPKIN) || ns.is(Blocks.MELON_STEM) || ns.is(Blocks.PUMPKIN_STEM)
                        || ns.is(Blocks.SWEET_BERRY_BUSH) || ns.is(Blocks.NETHER_WART) || ns.is(Blocks.SOUL_SAND);
                if (traverse) {
                    visited.add(np);
                    q.add(np);
                }
            }
        }

        Constants.LOG.info("[FastHarvester][SCAN] BFS discovered {} connected nodes for center {}.", result.size(), center);

        return result;
    }

    /**
     * Read the rotation of an item frame or FastItemFrames block-entity at `pos`.
     * Humanized aside: frames have feelings; rotation is how they express them.
     */
    private static int getFrameRotation(Level level, BlockPos pos) {
        try {
            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(pos));
            for (ItemFrame f : frames) {
                if (f.blockPosition().equals(pos)) {
                    // try getter methods
                    for (Method m : f.getClass().getMethods()) {
                        String name = m.getName().toLowerCase();
                        if ((name.contains("get") || name.contains("getitem")) && name.contains("rotation") && m.getParameterCount() == 0) {
                            Object r = m.invoke(f);
                            if (r instanceof Number) return ((Number) r).intValue() & 7;
                        }
                    }
                    // fallback to field
                    try {
                        Field fld = f.getClass().getDeclaredField("rotation");
                        fld.setAccessible(true);
                        Object v = fld.get(f);
                        if (v instanceof Number) return ((Number) v).intValue() & 7;
                    } catch (Throwable ignored) {}
                    return 0;
                }
            }

            // Block-entity fallback (FastItemFrames): if there is no vanilla ItemFrame
            // at `pos` we try to read rotation from a FIF block-entity using our adapter.
            // This keeps behavior consistent across vanilla and FastItemFrames worlds.
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                try { return FastItemFrameAdapterImpl.getRotation(be); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Constants.LOG.debug("[FastHarvester][ROT] getFrameRotation failed at {}: {}", pos, t.toString());
        }
        return 0;
    }

    /**
     * Set the rotation of an item frame or FastItemFrames block-entity at `pos`.
     * Emotional aside: rotate gently — it's a bit sensitive.
     */
    private static void setFrameRotation(Level level, BlockPos pos, int newRotation) {
        try {
            List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, new AABB(pos));
            for (ItemFrame f : frames) {
                if (f.blockPosition().equals(pos)) {
                    // try setter methods
                    for (Method m : f.getClass().getMethods()) {
                        String name = m.getName().toLowerCase();
                        if (name.contains("set") && name.contains("rotation") && m.getParameterCount() == 1) {
                            Class<?> p = m.getParameterTypes()[0];
                            if (p == int.class || p == Integer.class) {
                                m.invoke(f, newRotation);
                                return;
                            }
                            if (p == byte.class || p == Byte.class) {
                                m.invoke(f, (byte) newRotation);
                                return;
                            }
                        }
                    }
                    // fallback to field
                    try {
                        Field fld = f.getClass().getDeclaredField("rotation");
                        fld.setAccessible(true);
                        fld.setInt(f, newRotation & 7);
                        return;
                    } catch (Throwable ignored) {}
                }
            }

            // Block-entity path: try setting rotation on a FIF block-entity and
            // notify the world about the change. This is a best-effort operation
            // and intentionally tolerant of failures (no panicking allowed).
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                try {
                    FastItemFrameAdapterImpl.setRotation(be, newRotation);
                    try { level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3); } catch (Throwable ignored) {}
                    return;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Constants.LOG.debug("[FastHarvester][ROT] setFrameRotation failed at {}: {}", pos, t.toString());
        }
    }
}
