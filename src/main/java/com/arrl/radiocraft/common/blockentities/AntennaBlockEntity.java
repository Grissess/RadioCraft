package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.RadiocraftServerConfig;
import com.arrl.radiocraft.api.antenna.AntennaTypes;
import com.arrl.radiocraft.api.antenna.IAntennaType;
import com.arrl.radiocraft.api.benetworks.IBENetworkItem;
import com.arrl.radiocraft.common.benetworks.BENetwork;
import com.arrl.radiocraft.common.benetworks.BENetwork.BENetworkEntry;
import com.arrl.radiocraft.common.benetworks.power.PowerNetwork;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.radio.AntennaManager;
import com.arrl.radiocraft.common.radio.AntennaNetwork;
import com.arrl.radiocraft.common.radio.antenna.Antenna;
import com.arrl.radiocraft.common.radio.antenna.AntennaMorsePacket;
import com.arrl.radiocraft.common.radio.antenna.AntennaVoicePacket;
import de.maxhenkel.voicechat.api.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Shared BlockEntity for all blocks which act as an antenna-- used for processing packets/sending them to the network, receiving packets from the network & scheduling antenna update checks.
 */
public class AntennaBlockEntity extends BlockEntity implements IBENetworkItem {

	private final Map<Direction, Set<BENetwork>> networks = new HashMap<>();
	public Antenna<?> antenna = null;

	// Cache the results of antenna/radio updates and only update them at delays, cutting down on resource usage. Keep BENetworkEntry to ensure that it uses weak refs.
	private final List<BENetworkEntry> radios = Collections.synchronizedList(new ArrayList<>());
	private int antennaCheckCooldown = -1;

	public AntennaBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.ANTENNA.get(), pos, state);
	}

	public void transmitAudioPacket(ServerLevel level, short[] rawAudio, int wavelength, int frequency, UUID sourcePlayer) {
		antenna.transmitAudioPacket(level, rawAudio, wavelength, frequency, sourcePlayer);
	}

	public void transmitMorsePacket(net.minecraft.server.level.ServerLevel level, int wavelength, int frequency) {
		antenna.transmitMorsePacket(level, wavelength, frequency);
	}

	/**
	 * Called from voice thread.
	 */
	public void receiveAudioPacket(AntennaVoicePacket packet) {
		if(radios.size() == 1) {
			AbstractRadioBlockEntity radio = (AbstractRadioBlockEntity)radios.get(0).getNetworkItem();
			if(radio.getFrequency() == packet.getFrequency()) // Only receive if listening to correct frequency.
				radio.getRadio().receive(packet);
		}
		else if(radios.size() > 1) {
			for(BENetworkEntry entry : radios)
				((AbstractRadioBlockEntity)entry.getNetworkItem()).overdraw();
		}
	}

	public void receiveMorsePacket(AntennaMorsePacket packet) {
		if(radios.size() == 1) {
			AbstractRadioBlockEntity radio = (AbstractRadioBlockEntity)radios.get(0).getNetworkItem();
		}
		else if(radios.size() > 1) {
			for(BENetworkEntry entry : radios)
				((AbstractRadioBlockEntity)entry.getNetworkItem()).overdraw();
		}
	}

	/**
	 * Updates the antenna at this position in the world
	 */
	private void updateAntenna() {
		AntennaNetwork network = AntennaManager.getNetwork(level);
		antenna = AntennaTypes.match(level, worldPosition);
		if(antenna != null) {
			network.addAntenna(worldPosition, antenna);
			antenna.setNetwork(network);
			setChanged();
		}
		else
			network.removeAntenna(worldPosition);
	}

	private void updateConnectedRadios() {
		radios.clear();
		for(Set<BENetwork> side : networks.values()) {
			for(BENetwork network : side) {
				if(!(network instanceof PowerNetwork)) {
					for(BENetworkEntry entry : network.getConnections()) {
						if(entry.getNetworkItem() instanceof AbstractRadioBlockEntity)
							if(!radios.contains(entry))
								radios.add(entry);
					}
				}
			}
		}
	}

	/**
	 * Reset the antenna check cooldown-- used every time a block is placed "on" the antenna.
	 */
	public void markAntennaChanged() {
		antennaCheckCooldown = RadiocraftServerConfig.ANTENNA_UPDATE_DELAY.get() * 20;
	}

	public static <T extends BlockEntity> void tick(Level level, BlockPos blockPos, BlockState blockState, T t) {
		if(t instanceof AntennaBlockEntity be) {
			if(!level.isClientSide) { // Serverside only
				if(be.antennaCheckCooldown-- == 0)
					be.updateAntenna();
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag nbt) {
		super.saveAdditional(nbt);
		nbt.putInt("antennaCheckCooldown", Math.max(antennaCheckCooldown, -1));
		if(antenna != null) {
			nbt.putString("antennaType", antenna.type.getId().toString());
			nbt.put("antennaData", antenna.serializeNBT());
		}
	}

	@Override
	public void load(CompoundTag nbt) {
		super.load(nbt);
		antennaCheckCooldown = nbt.getInt("antennaCheckCooldown");
		if(nbt.contains("antennaType")) {
			IAntennaType<?> type = AntennaTypes.getType(new ResourceLocation(nbt.getString("antennaType")));
			if(type != null) {
				antenna = new Antenna<>(type, worldPosition);
				antenna.deserializeNBT(nbt.getCompound("antennaData"));
			}
		}
	}

	@Override
	public void onLoad() {
		if(antenna != null) { // Handle network set here where level is not null
			AntennaNetwork network = AntennaManager.getNetwork(level);
			network.addAntenna(worldPosition, antenna);
			antenna.setNetwork(network);
		}
		else {
			updateAntenna();
		}
	}

	@Override
	public void setRemoved() {
		if(!level.isClientSide)
			AntennaManager.getNetwork(level).removeAntenna(worldPosition);
		super.setRemoved();
	}

	@Override
	public void onChunkUnloaded() {
		if(!level.isClientSide)
			AntennaManager.getNetwork(level).removeAntenna(worldPosition);
		super.onChunkUnloaded();
	}

	@Override
	public Map<Direction, Set<BENetwork>> getNetworkMap() {
		return networks;
	}

	@Override
	public void networkUpdated(BENetwork network) {
		updateConnectedRadios();
	}
}
