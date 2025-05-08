package flexcoral.seedutils.modules;

import flexcoral.seedutils.SeedUtilsAddon;
import flexcoral.seedutils.SeedUtilsSystem;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;

public class HashedSeedLogger extends Module {
    public HashedSeedLogger() {
        super(SeedUtilsAddon.CATEGORY, "hashed-seed-logger", "Automatically logs hashed seeds of dimensions.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event)  {
        if (event.packet instanceof GameJoinS2CPacket packet) {
            var s = packet.commonPlayerSpawnInfo().seed();
            SeedUtilsSystem.get().addHashedSeed(packet.commonPlayerSpawnInfo().seed());
            info(String.format("Hashed seed %d", s));
        } else if (event.packet instanceof PlayerRespawnS2CPacket packet) {
            var s = packet.commonPlayerSpawnInfo().seed();
            SeedUtilsSystem.get().addHashedSeed(packet.commonPlayerSpawnInfo().seed());
            info(String.format("Hashed seed %d", s));
        }
    }
}
