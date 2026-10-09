package org.enthusia.events.infrastructure;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.enthusia.events.event.EventManager;
import org.enthusia.events.event.EventPhase;
import org.enthusia.events.event.EventSession;
import org.enthusia.events.event.EventType;
import java.util.UUID;
import java.util.function.Function;

/** Redirect a melee click intercepted by a carried player's hitbox to its carrier.
 * Player.attack preserves vanilla weapon, cooldown and normal damage listeners.
 */
public final class CaptureAttackListener implements Listener {
    private final EventManager manager;
    private final Function<UUID, UUID> carrierOf;
    public CaptureAttackListener(EventManager manager, Function<UUID, UUID> carrierOf) {
        this.manager = manager;
        this.carrierOf = carrierOf;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        // Paper pre-cancels attacks on invulnerable captives (willAttack=false).
        // Respect cancellation of an otherwise valid attack by earlier listeners.
        if (event.willAttack() && event.isCancelled()) return;
        EventSession session = manager.session();
        if (session == null || session.phase() != EventPhase.ACTIVE
                || session.definition().type() != EventType.CAPTURE_PLAYERS
                || !(event.getAttacked() instanceof Player captive)) return;
        UUID carrierId = carrierOf.apply(captive.getUniqueId());
        if (carrierId == null) return;
        event.setCancelled(true); // A captive is never the combat target.
        Player attacker = event.getPlayer();
        Player carrier = Bukkit.getPlayer(carrierId);
        if (carrier == null || !carrier.isOnline()
                || !session.participants().contains(attacker.getUniqueId())
                || !session.participants().contains(carrierId)
                || attacker.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || attacker.equals(carrier)
                || !attacker.getWorld().equals(carrier.getWorld())
                || attacker.getLocation().distanceSquared(carrier.getLocation()) > 16.0D
                || !attacker.hasLineOfSight(carrier)) return;
        // Friendly fire, prisoner/rescue-carrier restrictions and lethal capture
        // remain authoritative in EventGameplayListener for the forwarded attack.
        attacker.attack(carrier);
    }
}
