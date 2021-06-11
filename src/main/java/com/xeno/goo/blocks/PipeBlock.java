package com.xeno.goo.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.IStringSerializable;

import java.util.Locale;

public class PipeBlock extends Block {

	public static enum ConnectionState implements IStringSerializable {
		DISCONNECTED,
		CONNECTED,
		;

		public final boolean canConnect = true;
		public final boolean isConnected = ordinal() != 0;

		@Override
		public String getString() {

			return name().toLowerCase(Locale.ROOT);
		}

		public static ConnectionState[] getConnectedStates() {

			return new ConnectionState[] { CONNECTED };
		}

		public static ConnectionState[] getDisconnectedStates() {

			return new ConnectionState[] { DISCONNECTED };
		}
	}

	public static enum AttachmentState implements IStringSerializable {
		OUTPUT,
		IN_OUT,
		INPUT,
		;

		@Override
		public String getString() {

			return name().toLowerCase(Locale.ROOT);
		}

		public static AttachmentState[] getVisibleStates() {

			return new AttachmentState[] { INPUT };
		}
	}

	public static final EnumProperty<ConnectionState>[] CONNECTIONS = new EnumProperty[] {
			EnumProperty.create("connection_down", ConnectionState.class),
			EnumProperty.create("connection_up", ConnectionState.class),
			EnumProperty.create("connection_north", ConnectionState.class),
			EnumProperty.create("connection_south", ConnectionState.class),
			EnumProperty.create("connection_west", ConnectionState.class),
			EnumProperty.create("connection_east", ConnectionState.class)
	};

	public static final EnumProperty<AttachmentState>[] ATTACHMENTS = new EnumProperty[] {
			EnumProperty.create("attachment_down", AttachmentState.class),
			EnumProperty.create("attachment_up", AttachmentState.class),
			EnumProperty.create("attachment_north", AttachmentState.class),
			EnumProperty.create("attachment_south", AttachmentState.class),
			EnumProperty.create("attachment_west", AttachmentState.class),
			EnumProperty.create("attachment_east", AttachmentState.class)
	};

	public PipeBlock() {

		super(Properties.create(Material.GLASS)
				.sound(SoundType.GLASS)
				.hardnessAndResistance(0.5f)
				.notSolid()
		);
	}

	@Override
	protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {

		builder
				.add(CONNECTIONS)
				.add(ATTACHMENTS)
		;
	}

}
