package org.enthusia.events.infrastructure;
import org.bukkit.configuration.file.YamlConfiguration;
import org.enthusia.events.domain.LootLimits;
import org.enthusia.events.event.EventType;
import org.enthusia.events.loot.LootTableService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LootPersistenceTest {
    @TempDir Path directory;
    private LootTableService editor(Path file) throws Exception {
        var service = mock(LootTableService.class, CALLS_REAL_METHODS);
        doNothing().when(service).reload();
        var field = LootTableService.class.getDeclaredField("file");field.setAccessible(true);field.set(service,file.toFile());
        return service;
    }
    @Test void editsPersistBeforeReloadAndPreserveEnchantmentsAndUnknownMetadata() throws Exception {
        Path file=directory.resolve("loot.yml");
        var yaml=new YamlConfiguration();String path="loot.SKYWARS.tier3.sword";
        yaml.set(path+".material","DIAMOND_SWORD");yaml.set(path+".enchantments.sharpness",2);yaml.set(path+".future-custom-value","keep");yaml.save(file.toFile());
        var service=editor(file);service.edit(EventType.SKYWARS,3,"sword",1,1,0.25);
        var saved=YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals(0.25,saved.getDouble(path+".chance"));assertEquals(2,saved.getInt(path+".enchantments.sharpness"));
        assertEquals("keep",saved.getString(path+".future-custom-value"));verify(service).reload();
        String before=Files.readString(file);
        assertThrows(IllegalArgumentException.class,()->service.edit(EventType.SKYWARS,3,"sword",2,1,0.2));
        assertEquals(before,Files.readString(file));
    }
    @Test void failedAtomicReplacementDoesNotReloadAndRetainsExistingDestination() throws Exception {
        Path destination=directory.resolve("loot.yml");Files.createDirectory(destination);Files.writeString(destination.resolve("keep"),"old");
        var service=editor(destination);
        assertThrows(java.io.IOException.class,()->service.setLimits(EventType.SKYWARS,3,new LootLimits(2,2,12)));
        verify(service,never()).reload();assertEquals("old",Files.readString(destination.resolve("keep")));
    }
}
