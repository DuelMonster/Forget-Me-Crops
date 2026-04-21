package com.duelmonster.FastHarvester;

import java.util.*;

/**
 * FrameScanner is the core logic for FastHarvester's farm scanning and automation.
 * It is strictly loader-agnostic and only uses vanilla and common code.
 *
 * This class finds anchors (chest + item frame + hoe), builds the farm area using BFS,
 * and coordinates harvesting, replanting, and maintenance.
 *
 * <3 Happy farming! <3
 */
public class FrameScanner {
    // Performance limits
    public static final int MAX_FRAMES_PER_RUN = 24;
    public static final int MAX_BLOCKS_PER_RUN = 3072;

    /**
     * Represents a farm anchor (chest + item frame + hoe).
     */
    public static class Anchor {
        public final BlockPos chestPos;
        public final BlockPos framePos;
        public final BlockPos hoePos;
        public Anchor(BlockPos chest, BlockPos frame, BlockPos hoe) {
            this.chestPos = chest;
            this.framePos = frame;
            this.hoePos = hoe;
        }
    }

    /**
     * Represents a block position in the world (immutable).
     */
    public static class BlockPos {
        public final int x, y, z;
        public BlockPos(int x, int y, int z) {
            this.x = x; this.y = y; this.z = z;
        }
        public BlockPos offset(int dx, int dy, int dz) {
            return new BlockPos(x+dx, y+dy, z+dz);
        }
        @Override public boolean equals(Object o) {
            if (!(o instanceof BlockPos)) return false;
            BlockPos b = (BlockPos)o;
            return x==b.x && y==b.y && z==b.z;
        }
        @Override public int hashCode() { return Objects.hash(x,y,z); }
    }

    /**
     * Scans for all valid anchors in the loaded world.
     * This method must be called from loader-specific code that provides the loaded block/entity state.
     *
     * @param worldView Loader-specific world view abstraction (must be provided by loader code)
     * @return List of detected anchors
     */
    public static List<Anchor> findAnchors(IWorldView worldView) {
        List<Anchor> anchors = new ArrayList<>();
        // Loader-specific code must implement IWorldView and provide block/entity queries.
        for (BlockPos chest : worldView.getAllChests()) {
            BlockPos frame = chest.offset(0, 1, 0);
            if (worldView.isItemFrameWithHoe(frame, chest)) {
                BlockPos hoe = frame; // For clarity
                anchors.add(new Anchor(chest, frame, hoe));
            }
        }
        return anchors;
    }

    /**
     * Performs a BFS to find all connected farm tiles from the anchor.
     *
     * @param anchor The anchor to scan from
     * @param worldView Loader-provided world view
     * @param scanRange Max scan radius
     * @return Set of farm block positions
     */
    public static Set<BlockPos> scanFarmArea(Anchor anchor, IWorldView worldView, int scanRange) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = anchor.chestPos;
        queue.add(start);
        visited.add(start);
        int y = start.y;
        while (!queue.isEmpty() && visited.size() < MAX_BLOCKS_PER_RUN) {
            BlockPos pos = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != 1) continue; // Only cardinal directions
                    BlockPos next = pos.offset(dx, 0, dz);
                    if (next.y != y) continue; // Only same Y level
                    if (visited.contains(next)) continue;
                    if (worldView.isFarmTile(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return visited;
    }

    /**
     * Performs harvest, replant, and maintenance for a single anchor.
     *
     * @param anchor The anchor
     * @param worldView Loader-provided world view
     * @param config The config (loader passes in current config)
     */
    public static void processAnchor(Anchor anchor, IWorldView worldView, IConfig config) {
        Set<BlockPos> farm = scanFarmArea(anchor, worldView, config.getScanRange());
        for (BlockPos pos : farm) {
            if (worldView.shouldHarvest(pos)) {
                worldView.harvestCrop(pos, anchor);
            } else if (worldView.shouldReplant(pos)) {
                worldView.replantCrop(pos, anchor);
            } else if (worldView.shouldAutoTill(pos)) {
                worldView.autoTill(pos, anchor);
            }
        }
    }

    /**
     * Loader must implement this to provide world/block/entity access.
     */
    public interface IWorldView {
        List<BlockPos> getAllChests();
        boolean isItemFrameWithHoe(BlockPos frame, BlockPos chest);
        boolean isFarmTile(BlockPos pos);
        boolean shouldHarvest(BlockPos pos);
        boolean shouldReplant(BlockPos pos);
        boolean shouldAutoTill(BlockPos pos);
        void harvestCrop(BlockPos pos, Anchor anchor);
        void replantCrop(BlockPos pos, Anchor anchor);
        void autoTill(BlockPos pos, Anchor anchor);
    }

    /**
     * Loader must implement this to provide config values.
     */
    public interface IConfig {
        int getScanRange();
    }
}
