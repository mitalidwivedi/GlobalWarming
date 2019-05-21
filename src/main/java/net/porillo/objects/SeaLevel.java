package net.porillo.objects;

import lombok.Data;
import lombok.NoArgsConstructor;
import net.porillo.effect.SeaChange;
import net.porillo.effect.api.change.SerializableBlockChange;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class SeaLevel implements Serializable {

    private Map<Integer, SerializableBlockChange> locationHashChangeMap = new HashMap<>();
    private Map<Integer, Integer> chunkSeaLevel = new HashMap<>();

    private Integer defaultLevel;
    private Integer currentLevel;
    private transient SeaChange change;

}
