package org.enthusia.events.infrastructure;

import com.destroystokyo.paper.loottable.LootableInventory;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.block.*;
import org.bukkit.inventory.ItemStack;
import java.io.IOException;
import java.util.Base64;

/** Main-thread codec for unlocked, non-loot-table chests and barrels only. */
final class ContainerCodec {
    private ContainerCodec() { }
    static String capture(BlockState state) throws IOException {
        if (!(state instanceof TileState)) return "";
        if (!(state instanceof Chest || state instanceof Barrel) || !(state instanceof Container c))
            throw new IOException("Unsupported tile " + state.getType());
        if (c.isLocked()) throw new IOException("Locked containers require a lossless lock-component codec");
        if (c instanceof LootableInventory loot && (loot.getLootTable() != null || loot.getSeed() != 0
                || loot.hasBeenFilled() || loot.hasPendingRefill() || loot.isRefillEnabled()))
            throw new IOException("Loot-table/refill state unsupported");
        Base64.Encoder encoder = Base64.getEncoder();
        String name = c.customName() == null ? "" : GsonComponentSerializer.gson().serialize(c.customName());
        return "EE-CONTAINER-1\n" + c.getType().name() + "\n"
                + encoder.encodeToString(ItemStack.serializeItemsAsBytes(c.getSnapshotInventory().getContents())) + "\n"
                + encoder.encodeToString(c.getPersistentDataContainer().serializeToBytes()) + "\n" + name;
    }
    static void apply(Block block, String payload) throws IOException {
        String[] parts = payload.split("\n", 5);
        if (parts.length != 5 || !parts[0].equals("EE-CONTAINER-1") || !parts[1].equals(block.getType().name()))
            throw new IOException("Container identity/schema mismatch");
        BlockState state = block.getState();
        capture(state); // reject unsupported target state before writing metadata
        if (!(state instanceof Container c)) throw new IOException("Container state unavailable");
        ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(parts[2]));
        if (items.length != c.getSnapshotInventory().getSize()) throw new IOException("Container inventory size changed");
        c.getSnapshotInventory().setContents(items);
        c.getPersistentDataContainer().readFromBytes(Base64.getDecoder().decode(parts[3]), true);
        c.customName(parts[4].isEmpty() ? null : GsonComponentSerializer.gson().deserialize(parts[4]));
        if (!c.update(true, false)) throw new IOException("Container update rejected");
    }
}
