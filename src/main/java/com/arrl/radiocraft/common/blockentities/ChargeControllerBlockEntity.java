package com.arrl.radiocraft.common.blockentities;

import com.arrl.radiocraft.RadiocraftCommonConfig;
import com.arrl.radiocraft.api.benetworks.IPowerNetworkItem;
import com.arrl.radiocraft.common.benetworks.BENetwork;
import com.arrl.radiocraft.common.benetworks.BENetwork.BENetworkEntry;
import com.arrl.radiocraft.common.benetworks.power.ConnectionType;
import com.arrl.radiocraft.common.benetworks.power.PowerNetwork;
import com.arrl.radiocraft.common.blocks.ChargeControllerBlock;
import com.arrl.radiocraft.common.init.RadiocraftBlockEntities;
import com.arrl.radiocraft.common.menus.ChargeControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChargeControllerBlockEntity extends AbstractPowerBlockEntity implements ITogglableBE {

	private final ItemStackHandler inventory = new ItemStackHandler(1);
	private final LazyOptional<IItemHandlerModifiable> inventoryHandler = LazyOptional.of(() -> inventory);
	public final Container inventoryWrapper = new RecipeWrapper(inventory);

	private int lastPowerTick = 0;

	// Using a ContainerData for one value is awkward, but it changes constantly and needs to be synchronised.
	private final ContainerData fields = new ContainerData() {

		@Override
		public int get(int index) {
			if(index == 0)
				return lastPowerTick;
			return 0;
		}

		@Override
		public void set(int index, int value) {
			if(index == 0)
				lastPowerTick = 0;
		}

		@Override
		public int getCount() {
			return 1;
		}
	};

	public ChargeControllerBlockEntity(BlockPos pos, BlockState state) {
		super(RadiocraftBlockEntities.CHARGE_CONTROLLER.get(), pos, state, RadiocraftCommonConfig.CHARGE_CONTROLLER_TICK.get(), RadiocraftCommonConfig.CHARGE_CONTROLLER_TICK.get());
	}

	public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T t) {
		if(t instanceof ChargeControllerBlockEntity be) {
			if(!level.isClientSide && be.getPoweredOn()) { // Serverside only
				int energyToPush = be.energyStorage.extractEnergy(be.energyStorage.getEnergyStored(), true); // Do not actually pull out power yet.
				be.lastPowerTick = energyToPush;

				List<LargeBatteryBlockEntity> batteries = new ArrayList<>(); // Specifically grab batteries to avoid having to use another sorted list.
				for(Set<BENetwork> side : be.getNetworkMap().values()) {
					for(BENetwork network : side) {
						if(network instanceof PowerNetwork) {
							for(BENetworkEntry entry : network.getConnections()) {
								IPowerNetworkItem item = (IPowerNetworkItem)entry.getNetworkItem(); // This cast is safe because the PowerNetwork errors if a non-IPowerNetworkItem is added
								if(item.getConnectionType() == ConnectionType.PUSH) // Double check here is faster as instanceof can be quite slow.
									if(item instanceof LargeBatteryBlockEntity battery)
										batteries.add(battery);
							}
						}
					}
				}

				for(LargeBatteryBlockEntity battery : batteries) {
					LazyOptional<IEnergyStorage> energyCap = battery.getCapability(ForgeCapabilities.ENERGY);
					if(energyCap.isPresent()) { // This is horrendous code but java doesn't like lambdas and vars.
						IEnergyStorage storage = energyCap.orElse(null);
						energyToPush -= storage.receiveEnergy(energyToPush, false);

						if(energyToPush <= 0)
							break;
					}
				}
				be.lastPowerTick -= energyToPush; // Set lastPowerTick to the amount which was actually pushed.
				be.energyStorage.setEnergy(energyToPush); // Set energy to the remainder after pushing.
			}
		}
	}

	public void toggle() {
		if(!level.isClientSide) {
			BlockState state = level.getBlockState(worldPosition);
			level.setBlockAndUpdate(worldPosition, state.setValue(ChargeControllerBlock.POWERED, !state.getValue(ChargeControllerBlock.POWERED)));
		}
	}

	public boolean getPoweredOn() {
		return level != null && level.getBlockState(worldPosition).getValue(ChargeControllerBlock.POWERED);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.charge_controller");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
		return new ChargeControllerMenu(id, this, fields);
	}

	@Override
	public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap) {
		return cap == ForgeCapabilities.ITEM_HANDLER ? inventoryHandler.cast() : super.getCapability(cap);
	}

	@Override
	public void setRemoved() {
		if(inventoryHandler != null)
			inventoryHandler.invalidate();
		super.setRemoved();
	}

}

