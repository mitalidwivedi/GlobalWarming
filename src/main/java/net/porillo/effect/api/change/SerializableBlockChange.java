package net.porillo.effect.api.change;

import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.Serializable;

@Data
public class SerializableBlockChange implements Serializable {

    private transient World bukkitWorld;
    private transient Location blockLocation;
    private String world;
    private String materialFrom;
    private String materialTo;
    private int x, y, z;
    private int hashcode;

    public SerializableBlockChange(Block block, Material toType) {
        this.bukkitWorld = block.getWorld();
        this.blockLocation = block.getLocation();
        this.world = block.getWorld().getName();
        this.materialFrom = block.getType().name();
        this.materialTo = toType.name();
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
        this.hashcode = blockLocation.hashCode();
    }

    public Material getFrom() {
        return Material.valueOf(materialFrom);
    }

    public Material getTo() {
        return Material.valueOf(materialTo);
    }

    public World getBukkitWorld() {
        return Bukkit.getWorld(world);
    }

    public Location getLocation() {
        return getBukkitWorld().getBlockAt(x, y, z).getLocation();
    }
}
