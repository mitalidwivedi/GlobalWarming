package net.porillo.objects;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Location;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GLocation implements Serializable {

    private String world;
    private int x, y, z;

    public GLocation(Location location) {
        this.world = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
    }
}
