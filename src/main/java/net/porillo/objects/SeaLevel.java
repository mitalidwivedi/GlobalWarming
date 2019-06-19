package net.porillo.objects;

import lombok.Data;
import net.porillo.effect.api.change.SerializableBlockChange;
import org.bukkit.Chunk;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Data
public class SeaLevel implements Serializable {

    private Map<Integer, SerializableBlockChange> locationHashChangeMap;
    private Map<Integer, Integer> chunkSeaLevel;

    private Integer defaultLevel;
    private Integer currentLevel;

    public SeaLevel() {
        this.locationHashChangeMap = new HashMap<>();
        this.chunkSeaLevel = new HashMap<>();
    }

    public void debug() {
        System.out.println(String.format("%d,%d", defaultLevel, currentLevel));
    }

    public static int hashChunk(Chunk chunk) {
        return Objects.hash(chunk.getX(), chunk.getZ());
    }
}
