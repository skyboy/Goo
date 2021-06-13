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
		CONNECTED_PLAIN,
		CONNECTED_ATTACHMENT,
		;

		@Override
		public String getString() {

			return name().toLowerCase(Locale.ROOT);
		}

		private static final ConnectionState[] DISCONNECTED_STATES = new ConnectionState[] { DISCONNECTED },
				CONNECTED_STATES = new ConnectionState[] { CONNECTED_PLAIN, CONNECTED_ATTACHMENT },
				ATTACHMENT_STATES = new ConnectionState[] { CONNECTED_ATTACHMENT };

		public static ConnectionState[] getConnectedStates() {

			return CONNECTED_STATES;
		}

		public static ConnectionState[] getDisconnectedStates() {

			return DISCONNECTED_STATES;
		}

		public static ConnectionState[] getAttachmentStates() {

			return ATTACHMENT_STATES;
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
		;
	}

}
