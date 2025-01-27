package com.xeno.goo.network;

import com.xeno.goo.tiles.storage.GooBulbTile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class CrystalProgressTickPacket implements IGooModPacket {
    private RegistryKey<World> worldRegistryKey;
    private BlockPos pos;
    private int progressTicks;

    public CrystalProgressTickPacket(PacketBuffer buf) {
        read(buf);
    }

    public void read(PacketBuffer buf) {
        this.worldRegistryKey = RegistryKey.getOrCreateKey(Registry.WORLD_KEY, buf.readResourceLocation());
        this.pos = buf.readBlockPos();
        this.progressTicks = buf.readVarInt();
    }

    public CrystalProgressTickPacket(RegistryKey<World> registryKey, BlockPos pos, int ticks) {
        this.worldRegistryKey = registryKey;
        this.pos = pos;
        this.progressTicks = ticks;
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeResourceLocation(worldRegistryKey.getLocation());
        buf.writeBlockPos(pos);
        buf.writeVarInt(progressTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                if (Minecraft.getInstance().world == null) {
                    return;
                }
                if (Minecraft.getInstance().world.getDimensionKey() != worldRegistryKey) {
                    return;
                }
                TileEntity te = Minecraft.getInstance().world.getTileEntity(pos);
                if (te instanceof GooBulbTile) {
                    ((GooBulbTile) te).updateCrystalTicks(this.progressTicks);
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }
}
