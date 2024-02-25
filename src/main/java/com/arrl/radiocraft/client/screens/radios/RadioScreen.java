package com.arrl.radiocraft.client.screens.radios;

import com.arrl.radiocraft.client.RadiocraftClientValues;
import com.arrl.radiocraft.client.screens.widgets.Dial;
import com.arrl.radiocraft.client.screens.widgets.HoldButton;
import com.arrl.radiocraft.client.screens.widgets.ToggleButton;
import com.arrl.radiocraft.client.screens.widgets.ValueButton;
import com.arrl.radiocraft.common.init.RadiocraftPackets;
import com.arrl.radiocraft.common.menus.RadioMenu;
import com.arrl.radiocraft.common.network.packets.serverbound.SFrequencyPacket;
import com.arrl.radiocraft.common.network.packets.serverbound.SRadioPTTPacket;
import com.arrl.radiocraft.common.network.packets.serverbound.SRadioSSBPacket;
import com.arrl.radiocraft.common.network.packets.serverbound.STogglePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public abstract class RadioScreen<T extends RadioMenu<?>> extends AbstractContainerScreen<T> {

	protected final ResourceLocation texture;
	protected final ResourceLocation widgetsTexture;
	protected final T menu;

	public RadioScreen(T menu, Inventory inventory, Component title, ResourceLocation texture, ResourceLocation widgetsTexture) {
		super(menu, inventory, title);
		this.menu = menu;
		this.texture = texture;
		this.widgetsTexture = widgetsTexture;
	}

	@Override
	public void onClose() {
		super.onClose();
		RadiocraftClientValues.SCREEN_PTT_PRESSED = false; // Make sure to stop recording player's mic when the UI is closed, in case they didn't let go of PTT
		RadiocraftClientValues.SCREEN_SSB_ENABLED = false;
		RadiocraftClientValues.SCREEN_CW_ENABLED = false;
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
		renderBackground(poseStack);
		super.render(poseStack, mouseX, mouseY, partialTicks);

		renderAdditionalTooltips(poseStack, mouseX, mouseY, partialTicks);
	}

	/**
	 * Use to render additional tooltips like lights.
	 */
	protected void renderAdditionalTooltips(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) { }

	@Override
	protected void renderBg(PoseStack poseStack, float partialTicks, int x, int y) {
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShaderTexture(0, texture);

		int edgeSpacingX = (this.width - this.imageWidth) / 2;
		int edgeSpacingY = (this.height - this.imageHeight) / 2;
		blit(poseStack, edgeSpacingX, edgeSpacingY, 0, 0, this.imageWidth, this.imageHeight);

		renderAdditionalBg(poseStack, partialTicks, x, y);
	}

	/**
	 * Use to render additional background elements like lights.
	 */
	protected void renderAdditionalBg(PoseStack poseStack, float partialTicks, int x, int y) { }

	@Override
	protected void renderLabels(PoseStack poseStack, int x, int y) {}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		boolean value = super.mouseReleased(mouseX, mouseY, button);

		for(GuiEventListener listener : children()) { // Janky fix to make the dials detect a mouse release which isn't over them, forge allows for a mouse to go down but not back up.
			if(!listener.isMouseOver(mouseX, mouseY)) {
				if(listener instanceof Dial)
					listener.mouseReleased(mouseX, mouseY, button);
			}
		}

		return value;
	}

	protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
		mouseX -= leftPos;
		mouseY -= topPos;
		return mouseX >= (x - 1) && mouseX < (x + width + 1) && mouseY >= (y - 1) && mouseY < (y + height + 1);
	}

	/**
	 * Callback to do nothing, for readability.
	 */
	protected void doNothing(AbstractWidget button) {}

	/**
	 * Callback for pressing a PTT button.
	 */
	protected void onPressPTT(HoldButton button) {
		RadiocraftPackets.sendToServer(new SRadioPTTPacket(menu.blockEntity.getBlockPos(), true));
		RadiocraftClientValues.SCREEN_PTT_PRESSED = true;
	}

	/**
	 * Callback for releasing a PTT button.
	 */
	protected void onReleasePTT(HoldButton button) {
		RadiocraftPackets.sendToServer(new SRadioPTTPacket(menu.blockEntity.getBlockPos(), false));
		RadiocraftClientValues.SCREEN_PTT_PRESSED = false;
	}

	/**
	 * Callback to toggle power on a device.
	 */
	protected void onPressPower(ToggleButton button) {
		RadiocraftPackets.sendToServer(new STogglePacket(menu.blockEntity.getBlockPos()));
	}

	/**
	 * Callback for SSB toggle buttons.
	 */
	protected void onPressSSB(ValueButton button) {
		boolean ssbEnabled = menu.blockEntity.getSSBEnabled();

		RadiocraftPackets.sendToServer(new SRadioSSBPacket(menu.blockEntity.getBlockPos(), !ssbEnabled));
		menu.blockEntity.setSSBEnabled(!ssbEnabled); // Update instantly for GUI, server will re-sync this value though.
	}

	/**
	 * Callback for raising frequency by one step on the dial.
	 */
	protected void onFrequencyDialUp(Dial dial) {
		if(menu.blockEntity.isPowered())
			RadiocraftPackets.sendToServer(new SFrequencyPacket(menu.blockEntity.getBlockPos(), 1));
	}

	/**
	 * Callback for raising frequency by one step on the dial.
	 */
	protected void onFrequencyDialDown(Dial dial) {
		if(menu.blockEntity.isPowered())
			RadiocraftPackets.sendToServer(new SFrequencyPacket(menu.blockEntity.getBlockPos(), -1));
	}

	/**
	 * Callback for frequency up buttons.
	 */
	protected void onFrequencyButtonUp(Button button) {
		if(menu.blockEntity.isPowered())
			RadiocraftPackets.sendToServer(new SFrequencyPacket(menu.blockEntity.getBlockPos(), 1));
	}

	/**
	 * Callback for frequency down buttons.
	 */
	protected void onFrequencyButtonDown(Button button) {
		if(menu.blockEntity.isPowered())
			RadiocraftPackets.sendToServer(new SFrequencyPacket(menu.blockEntity.getBlockPos(), -1));
	}

}