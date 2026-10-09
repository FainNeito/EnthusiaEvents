package org.enthusia.events.infrastructure;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ContainerCodecTest {
 @Test void unlockedChestPreservesSerializedItemsPdcAndNameAndChecksUpdate() throws Exception {
  Chest chest=mock(Chest.class);Block block=mock(Block.class);Inventory inventory=mock(Inventory.class);
  var pdc=mock(PersistentDataContainer.class);ItemStack[] items=new ItemStack[27];
  when(chest.getType()).thenReturn(Material.CHEST);when(chest.getSnapshotInventory()).thenReturn(inventory);
  when(inventory.getContents()).thenReturn(items);when(inventory.getSize()).thenReturn(27);
  when(chest.getPersistentDataContainer()).thenReturn(pdc);when(pdc.serializeToBytes()).thenReturn(new byte[]{7,8});
  when(chest.customName()).thenReturn(net.kyori.adventure.text.Component.text("Named chest"));
  when(block.getState()).thenReturn(chest);when(block.getType()).thenReturn(Material.CHEST);when(chest.update(true,false)).thenReturn(true);
  try(var codec=mockStatic(ItemStack.class)) {
   codec.when(()->ItemStack.serializeItemsAsBytes(items)).thenReturn(new byte[]{1,2,3});
   codec.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{1,2,3})).thenReturn(items);
   String payload=ContainerCodec.capture(chest);ContainerCodec.apply(block,payload);
   verify(inventory).setContents(items);verify(pdc).readFromBytes(new byte[]{7,8},true);
   verify(chest).customName(net.kyori.adventure.text.Component.text("Named chest"));
   when(chest.update(true,false)).thenReturn(false);assertThrows(IOException.class,()->ContainerCodec.apply(block,payload));
  }
 }
 @Test void unsupportedTilesLocksAndLootTablesFailClosed() throws Exception {
  assertThrows(IOException.class,()->ContainerCodec.capture(mock(Sign.class)));
  Chest chest=mock(Chest.class);when(chest.isLocked()).thenReturn(true);assertThrows(IOException.class,()->ContainerCodec.capture(chest));
  when(chest.isLocked()).thenReturn(false);when(chest.hasBeenFilled()).thenReturn(true);assertThrows(IOException.class,()->ContainerCodec.capture(chest));
  assertEquals("",ContainerCodec.capture(mock(BlockState.class)));
 }
}
