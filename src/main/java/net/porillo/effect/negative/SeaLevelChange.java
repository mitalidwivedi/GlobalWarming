package net.porillo.effect.negative;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import net.porillo.GlobalWarming;
import net.porillo.effect.ClimateData;
import net.porillo.effect.SeaChange;
import net.porillo.effect.api.ClimateEffectType;
import net.porillo.effect.api.ListenerClimateEffect;
import net.porillo.effect.api.change.SerializableBlockChange;
import net.porillo.engine.ClimateEngine;
import net.porillo.engine.api.Distribution;
import net.porillo.engine.api.WorldClimateEngine;
import net.porillo.objects.SeaLevel;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

import static org.bukkit.Material.*;

/**
 * Sea-level rise
 * - Two asynchronous, repeating tasks
 * 1) Add jobs to the stack (once the stack is empty)
 * 2) Apply any required changes
 * <p>
 * - Sea level will rise with the temperature
 * - Raised blocks are tagged with meta data
 * - When sea levels lower, the tagged blocks are reset
 * - Will not dry out lakes, rivers, irrigation, machines, etc.
 * - Considerations made for growing kelp, player changes, and
 * other events: blocks that drop, etc.
 */
@ClimateData(type = ClimateEffectType.SEA_LEVEL_RISE, isSerialized = true)
public class SeaLevelChange extends ListenerClimateEffect {

    @Getter private Distribution seaMap;
    private int queueTicks;
    private SeaLevel seaLevel;
    private Set<Location> trackedBucketEmpties = new HashSet<>();
    private long lastChange;

    @Override
    public void serialize() {
        try {
            FileOutputStream fileOut = new FileOutputStream("plugins/GlobalWarming/seaLevel.dat");
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(seaLevel);
            out.flush();
            out.close();
            fileOut.close();
            GlobalWarming.getInstance().getLogger().info("Serialized sea level data to file.");
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    @Override
    public void deserialize() {
        try {
            FileInputStream fileIn = new FileInputStream("plugins/GlobalWarming/seaLevel.dat");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            seaLevel = (SeaLevel) in.readObject();
            seaLevel.debug();
            in.close();
            fileIn.close();
        } catch (IOException i) {
            seaLevel = new SeaLevel();
        } catch (ClassNotFoundException c) {
            System.out.println("Serialized Data class not found");
            c.printStackTrace();
        }

        if (seaLevel == null) {
            this.seaLevel = new SeaLevel();
        }
    }

    /**
     * Update the queue with loaded-chunks once the queue is empty
     */
    private void startQueueLoader() {
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(GlobalWarming.getInstance(), () -> {
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    final WorldClimateEngine wce = ClimateEngine.getInstance().getClimateEngine(world.getUID());

                    if (wce != null && wce.isEffectEnabled(ClimateEffectType.SEA_LEVEL_RISE)) {
                        if (seaLevel.getDefaultLevel() == null) {
                            seaLevel.setDefaultLevel(world.getSeaLevel() - 1);
                            GlobalWarming.getInstance().getLogger().info("Default sea level: " + (world.getSeaLevel() - 1));
                        }

                        if (seaLevel.getCurrentLevel() == null) {
                            seaLevel.setCurrentLevel(world.getSeaLevel() - 1);
                        }

                        // Calculate the custom sea level based on the temperature. This is what we want.
                        final int customSeaLevel = getCustomSeaLevel(wce);

                        for (Chunk chunk : world.getLoadedChunks()) {
                            int chunkSeaLevel = seaLevel.getDefaultLevel();
                            int chunkHash = SeaLevel.hashChunk(chunk);

                            if (seaLevel.getChunkSeaLevel().containsKey(chunkHash)) {
                                chunkSeaLevel = seaLevel.getChunkSeaLevel().get(chunkHash);
                            }

                            if (customSeaLevel > chunkSeaLevel) {
                                diffBlocks(chunk, customSeaLevel, SeaChange.UP);
                            } else if (customSeaLevel < chunkSeaLevel) {
                                diffBlocks(chunk, customSeaLevel, SeaChange.DOWN);
                            }

                            seaLevel.getChunkSeaLevel().put(SeaLevel.hashChunk(chunk), customSeaLevel);
                        }

                        seaLevel.setCurrentLevel(customSeaLevel);
                        lastChange = System.currentTimeMillis();

                    }
                }
            }
        }, 0L, queueTicks);
    }

    public int getCustomSeaLevel(final WorldClimateEngine wce) {
        final int deltaSeaLevel = (int) seaMap.getValue(wce.getTemperature());
        return seaLevel.getDefaultLevel() + deltaSeaLevel;
    }

    private void diffBlocks(Chunk chunk, final int customSeaLevel, SeaChange change) {
        System.out.println(String.format("[%d,%d] - %d - {%s}", chunk.getX(), chunk.getZ(), customSeaLevel, change.name()));
        if (change == SeaChange.UP) {
            //Scan chunk-blocks within the sea-level's range:
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    final int y = seaLevel.getDefaultLevel();
                    Block block = chunk.getBlock(x, y, z);

                    // Test a block at sea level in the chunk, if needed we fill above this block
                    if (block.getType() == WATER || block.getType() == GRASS || block.getType() == SAND
                            | block.getType() == GRASS_BLOCK || block.getType() == SEAGRASS) {
                        fillTo(chunk, customSeaLevel, x, z, y);
                    } else if (block.getType() == ICE || block.getType() == PACKED_ICE) {
                        SerializableBlockChange sbc1 = new SerializableBlockChange(block, WATER);
                        seaLevel.getLocationHashChangeMap().put(sbc1.getHashcode(), sbc1);
                        block.setType(WATER, true);

                        fillTo(chunk, customSeaLevel, x, z, y);
                    }
                }
            }
        } else if (change == SeaChange.DOWN) {
            Set<Integer> remove = new HashSet<>();
            for (SerializableBlockChange sbc : seaLevel.getLocationHashChangeMap().values()) {
                if (sbc.getY() > customSeaLevel) {
                    remove.add(sbc.getHashcode());
                    sbc.getBukkitWorld().getBlockAt(sbc.getLocation()).setType(sbc.getFrom());
                }
            }

            for (Integer hash : remove) {
                seaLevel.getLocationHashChangeMap().remove(hash);
            }
        }
    }

    private void fillTo(Chunk chunk, int customSeaLevel, int x, int z, int y) {
        for (int yy = y + 1; yy <= customSeaLevel; yy++) {
            Block block = chunk.getBlock(x, yy, z);

            if (block.getType() == AIR || block.getType() == LILY_PAD || block.getType() == SEAGRASS) {
                SerializableBlockChange sbc = new SerializableBlockChange(block, WATER);
                seaLevel.getLocationHashChangeMap().put(sbc.getHashcode(), sbc);
                chunk.getBlock(x, yy, z).setType(WATER, true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFromToEvent(BlockFromToEvent event) {
        Block source = event.getBlock();

        if (source.getType() == WATER && !trackedBucketEmpties.contains(source.getLocation())) {
            Block to = event.getToBlock();
            long now = System.currentTimeMillis();

            if (now - lastChange <= 60000) {
                event.setCancelled(true);
            } else if (seaLevel.getLocationHashChangeMap().containsKey(source.getLocation().hashCode())) {
                seaLevel.getLocationHashChangeMap().put(to.getLocation().hashCode(),
                        new SerializableBlockChange(to, WATER));
            }
        }
    }

    /**
     * Replacing sea-level-blocks will remove them from the tracked set
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        seaLevel.getLocationHashChangeMap().remove(event.getBlockPlaced().getLocation().hashCode());
    }

    /**
     * Emptying a bucket (water or lava) will remove the adjacent block
     * from the tracked set
     */
    @EventHandler(ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Block adjacent = event.getBlockClicked().getRelative(event.getBlockFace());
        seaLevel.getLocationHashChangeMap().remove(adjacent.getLocation().hashCode());
        trackedBucketEmpties.add(adjacent.getLocation());
    }

    /**
     * Load the sea-level distribution model
     */
    @Override
    public void setJsonModel(JsonObject jsonModel) {
        super.setJsonModel(jsonModel);
        seaMap = GlobalWarming.getInstance().getGson().fromJson(
                jsonModel.get("distribution"),
                new TypeToken<Distribution>() {
                }.getType());


        if (seaMap == null) {
            unregister();
        } else {
            queueTicks = jsonModel.get("queue-ticks").getAsInt();
            startQueueLoader();
        }
    }
}
