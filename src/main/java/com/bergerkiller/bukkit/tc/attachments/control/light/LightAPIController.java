package com.bergerkiller.bukkit.tc.attachments.control.light;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.Task;
import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.tc.TrainCarts;

public abstract class LightAPIController {
    private static final Map<World, LightAPIController> _blockLightControllers = new HashMap<>();
    private static final Map<World, LightAPIController> _skyLightControllers = new HashMap<>();
    private static SyncTask _task;
    private boolean syncPending;

    protected LightAPIController() {
        syncPending = false;
    }

    protected void schedule() {
        if (!syncPending) {
            syncPending = true;
            if (_task == null) {
                _task = new SyncTask();
                if (_task.getPlugin().isEnabled()) {
                    _task.start(1, 1);
                }
            }
        }
    }

    /**
     * Gets a controller instance for a World
     * 
     * @param world The world on which light is manipulated
     * @param skyLight Whether to change the sky light (true) or block light (false)
     * @return controller
     */
    public static LightAPIController get(World world, boolean skyLight) {
        Map<World, LightAPIController> map = skyLight ? _skyLightControllers : _blockLightControllers;
        LightAPIController controller = map.get(world);
        if (controller == null) {
            try {
                controller = skyLight ? LightAPIControllerImpl.forSkyLight(world) : LightAPIControllerImpl.forBlockLight(world);
            } catch (Throwable t) {
                Plugin plugin = Bukkit.getPluginManager().getPlugin("LightAPI");
                if (plugin == null) {
                    // Not loaded
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to initialize LightAPI handler: LightAPI plugin is not enabled!");
                } else if (plugin.getDescription().getMain().equals("ru.beykerykt.minecraft.lightapi.bukkit.impl.BukkitPlugin")) {
                    // LightAPI is used instead of LightAPI-Fork - not supported!
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to initialize LightAPI handler: LightAPI is installed, but you need LightAPI-fork instead!");
                } else {
                    TrainCarts.plugin.getLogger().log(Level.SEVERE, "Failed to initialize LightAPI handler", t);
                }
                controller = LightAPIControllerUnavailable.INSTANCE;
            }
            map.put(world, controller);
        }
        return controller;
    }

    public static void disableWorld(World world) {
        _blockLightControllers.remove(world);
        _skyLightControllers.remove(world);
    }

    public static void disable() {
        _blockLightControllers.clear();
        _skyLightControllers.clear();
        Task.stop(_task);
        _task = null;
    }

    public abstract void add(IntVector3 position, int level);

    public abstract void remove(IntVector3 position, int level);

    public abstract void move(IntVector3 old_position, IntVector3 new_position, int level);

    protected abstract boolean onSync();

    public final boolean sync() {
        syncPending = false;
        return onSync();
    }

    private static class SyncTask extends Task {
        private int ticksIdle = 0;

        public SyncTask() {
            super(TrainCarts.plugin);
        }

        @Override
        public void run() {
            boolean busy = false;
            for (LightAPIController controller : _blockLightControllers.values()) {
                busy |= controller.sync();
            }
            for (LightAPIController controller : _skyLightControllers.values()) {
                busy |= controller.sync();
            }
            if (busy) {
                ticksIdle = 0;
            } else if (++ticksIdle > 100) {
                stop();
                _task = null;
            }
        }
    }

    /**
     * List of light levels configured for a given block
     */
    private static final class LevelList {
        private static final int[] NO_LEVELS = new int[0];
        private static final int[][] SINGLE_LEVEL = new int[16][1];
        static {
            for (int level = 0; level <= 15; level++) {
                SINGLE_LEVEL[level][0] = level;
            }
        }

        // Light level that was last synchronized
        private int sync = 0;
        // Sorted list of light levels, highest value first
        private int[] levels = NO_LEVELS;

        /**
         * Gets whether the original light source needs to be removed
         * to properly update the light
         * 
         * @return True if the previous light needs to be removed
         */
        public boolean needsRemoving() {
            return (levels == NO_LEVELS) ? (sync > 0) : (sync > levels[0]);
        }

        /**
         * Gets whether the list of levels is empty.
         * An empty list results in no light sources being created.
         * 
         * @return True if empty
         */
        public boolean isEmpty() {
            return levels == NO_LEVELS;
        }

        /**
         * Synchronizes the light level, returning the new maximum light level
         * applied. Should not be called when empty.
         * 
         * @return light level
         */
        public int sync() {
            return sync = levels[0];
        }

        /**
         * Adds a new light level to this list. Returns true
         * if the maximum light level stored has increased as a
         * result.
         * 
         * @param level Light level to add
         * @return True if the maximum light level has increased as a result
         */
        public boolean add(int level) {
            if (levels == NO_LEVELS) {
                levels = SINGLE_LEVEL[level];
                return true;
            } else if (level > levels[0]) {
                int[] new_levels = new int[levels.length + 1];
                new_levels[0] = level;
                System.arraycopy(levels, 0, new_levels, 1, levels.length);
                levels = new_levels;
                return true;
            } else {
                int[] new_levels = new int[levels.length + 1];
                for (int i = 0; i < levels.length; i++) {
                    int other_level = levels[i];
                    if (level <= other_level) {
                        new_levels[i] = other_level;
                        continue;
                    }

                    // Level must be put here, in place of a previous level
                    // Copy all remaining values after
                    new_levels[i] = level;
                    System.arraycopy(levels, i, new_levels, i+1, levels.length - i);
                    levels = new_levels;
                    return false;
                }

                // Add level at the end of the list
                new_levels[levels.length] = level;
                levels = new_levels;
                return false;
            }
        }

        /**
         * Removes a single light level from this list. Returns true if the maximum
         * light level has gone down as a result of removing it.
         * 
         * @param level Light level to remove
         * @return True if the maximum light level dropped
         */
        public boolean remove(int level) {
            int len = levels.length;
            if (len == 1) {
                // Optimized for if only one level is stored
                if (levels[0] == level) {
                    levels = NO_LEVELS;
                    return true;
                } else {
                    return false;
                }
            } else if (len == 2) {
                // Switch to a single by-level, when two values are stored
                if (levels[1] == level) {
                    levels = SINGLE_LEVEL[levels[0]];
                    return false;
                } else if (levels[0] == level) {
                    levels = SINGLE_LEVEL[levels[1]];
                    return true;
                } else {
                    return false;
                }
            } else if (levels[0] == level) {
                // List of levels, and the highest light level was removed
                // If the level following it is lower, then the light level changed
                int[] new_levels = new int[len - 1];
                System.arraycopy(levels, 1, new_levels, 0, len - 1);
                levels = new_levels;
                return new_levels[0] != level;
            } else {
                // 3 or more requires list remove logic in the middle of the list
                // The maximum light level doesn't change
                for (int i = 1; i < len; len++) {
                    if (levels[i] == level) {
                        int[] new_levels = new int[len - 1];
                        System.arraycopy(levels, 0, new_levels, 0, i);
                        System.arraycopy(levels, i+1, new_levels, i, len - i - 1);
                        levels = new_levels;
                        return false;
                    }
                }

                // Not found
                return false;
            }
        }
    }
}
