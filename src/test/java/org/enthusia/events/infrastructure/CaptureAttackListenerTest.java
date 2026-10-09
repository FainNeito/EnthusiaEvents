package org.enthusia.events.infrastructure;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.enthusia.events.event.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;

class CaptureAttackListenerTest {
    @Test void invulnerableTargetsReachListenerAndExplicitCancelledAttacksStayCancelled() throws Exception {
        var method = CaptureAttackListener.class.getMethod("onAttack", PrePlayerAttackEntityEvent.class);
        assertFalse(method.getAnnotation(org.bukkit.event.EventHandler.class).ignoreCancelled());
        var manager = mock(EventManager.class);
        var attacker = player(); var captive = player();
        var event = new PrePlayerAttackEntityEvent(attacker, captive, true); event.setCancelled(true);
        new CaptureAttackListener(manager, ignored -> UUID.randomUUID()).onAttack(event);
        verifyNoInteractions(manager); verify(attacker, never()).attack(any());
    }
    @Test void forwardsInterceptedHitToCarrierWithoutHittingInvulnerablePrisoner() {
        EventManager manager = mock(EventManager.class);
        Player attacker = player(), captive = player(), carrier = player();
        World world = mock(World.class);
        Location samePosition = new Location(world, 0, 70, 0);
        for (Player player : List.of(attacker, captive, carrier)) {
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(samePosition);
        }
        when(attacker.hasLineOfSight(carrier)).thenReturn(true);
        EventSession session = session();
        session.participants().addAll(List.of(attacker.getUniqueId(), captive.getUniqueId(), carrier.getUniqueId()));
        when(manager.session()).thenReturn(session);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(carrier.getUniqueId())).thenReturn(carrier);
            var event = new PrePlayerAttackEntityEvent(attacker, captive, false);
            new CaptureAttackListener(manager, id -> carrier.getUniqueId()).onAttack(event);
            assertTrue(event.isCancelled());
            verify(attacker).attack(carrier);
            verify(attacker, never()).attack(captive);
        }
    }
    @Test void spectatorCannotUseCaptiveAsProxyToAttack() {
        EventManager manager = mock(EventManager.class);
        Player attacker = player(), captive = player(), carrier = player();
        EventSession session = session();
        session.participants().add(carrier.getUniqueId());
        when(manager.session()).thenReturn(session);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(carrier.getUniqueId())).thenReturn(carrier);
            new CaptureAttackListener(manager, id -> carrier.getUniqueId())
                    .onAttack(new PrePlayerAttackEntityEvent(attacker, captive, false));
            verify(attacker, never()).attack(any());
        }
    }
    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }
    private static EventSession session() {
        return new EventSession(new EventDefinition(EventType.CAPTURE_PLAYERS, "Capture", Material.STICK,
                "", false, false, false), EventPhase.ACTIVE);
    }
}
