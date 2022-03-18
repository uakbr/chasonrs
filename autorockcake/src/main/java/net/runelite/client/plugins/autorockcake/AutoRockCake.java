package net.runelite.client.plugins.autorockcake;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.iutils.*;
import net.runelite.client.plugins.iutils.game.Game;
import net.runelite.client.plugins.iutils.ui.Chatbox;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
		name = "AutoRockCake",
		description = "Automatically breaks your teeth on rock cakes",
		tags = {"chas", "rock", "cake", "locator", "orb"}
)
@Slf4j
public class AutoRockCake extends Plugin
{
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private Client client;
	@Inject
	private Game game;
	@Inject
	private ClientThread clientThread;
	@Inject
	private iUtils utils;
	@Inject
	private WalkUtils walk;
	@Inject
	private ObjectUtils objectUtils;
	@Inject
	private CalculationUtils calc;
	@Inject
	private NPCUtils npcUtils;
	@Inject
	private BankUtils bank;
	@Inject
	private Chatbox chat;
	@Inject
	private InventoryUtils inv;
	ChatMessage message;

	@Inject
	PluginOverlay overlay;
	@Inject
	PluginConfig config;

	private Player player;
	private Rectangle bounds;
	LegacyMenuEntry targetMenu;
	PluginState state;
	PluginState lastState;
	boolean startPlugin;
	Instant botTimer;
	int timeout;
	List<Integer> bankRegion;

	Set<Integer> cakes = Set.of(ItemID.DWARVEN_ROCK_CAKE_7510, ItemID.LOCATOR_ORB);

	public AutoRockCake() {
		bankRegion = Arrays.asList();
		botTimer = null;
		startPlugin = false;
		state = PluginState.TIMEOUT;
		lastState = state;
	}

	@Provides
	PluginConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PluginConfig.class);
	}


	private void reset() {
		timeout = 0;
		startPlugin = false;
		botTimer = null;
		state = PluginState.TIMEOUT;
		lastState = state;
		overlayManager.remove(overlay);
	}

	@Override
	protected void startUp() { }

	@Override
	protected void shutDown(){ reset(); }

	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked configButtonClicked) {
		if (!configButtonClicked.getGroup().equalsIgnoreCase("AutoRockCake")) {
			return;
		}
		switch (configButtonClicked.getKey()) {
			case "startPlugin":
				if (!startPlugin) {
					startPlugin = true;
					timeout = calc.getRandomIntBetweenRange(3, 10);
					botTimer = Instant.now();
					state = PluginState.TIMEOUT;
					overlayManager.add(overlay);
				} else {
					reset();
				}
				break;
		}
	}

	@Subscribe
	private void onChatMessage(ChatMessage event) {
		if (event.getType() == ChatMessageType.CONSOLE) {
			return;
		}
		if (event.getMessage().equalsIgnoreCase("You drink some of your overload potion.")
				&& event.getType() == ChatMessageType.SPAM)
			timeout = 12;
	}

	@Subscribe
	private void onGameTick(GameTick event)
	{
		if (!startPlugin)
			return;
		player = client.getLocalPlayer();
		if (player != null && client != null) {
			state = getState();
			if (config.debug() && state != lastState && state != PluginState.TIMEOUT) {
				utils.sendGameMessage(this.getClass().getName() + ": " + state.toString());
			}
			if (state != PluginState.TIMEOUT)
				lastState = state;
			if (player.isMoving())
				return;
			switch (state) {
				case TIMEOUT:
					if (timeout <= 0)
						timeout = 0;
					else
						timeout--;
					break;
				case LOWER_HP:
					WidgetItem item = inv.getWidgetItem(cakes);
					if (item != null) {
						actionItem(item.getId(),
								item.getId() == ItemID.DWARVEN_ROCK_CAKE_7510 ? MenuAction.ITEM_THIRD_OPTION : MenuAction.ITEM_FIRST_OPTION);
					}
					timeout = 0;
					break;
				default:
					timeout = 1;
					break;
			}
		}
	}

	PluginState getState() {
		if (timeout > 0 || player.isMoving())
			return PluginState.TIMEOUT;
		if (client.getBoostedSkillLevel(Skill.HITPOINTS) > 1) {
			if (config.whileOverloaded()) {
				if (client.getVarbitValue(3955) == 0) {
					timeout = calc.getRandomIntBetweenRange(2, 18);
					return PluginState.TIMEOUT;
				} else {
					return PluginState.LOWER_HP;
				}
			} else {
				return PluginState.LOWER_HP;
			}
		}
		timeout = calc.getRandomIntBetweenRange(2, 18);
		return PluginState.TIMEOUT;
	}

	boolean inRegion(Client client, List<Integer> region) {
		return Arrays.stream(client.getMapRegions()).anyMatch(region::contains);
	}

	private boolean actionItem(int id, MenuAction action) {
		return actionItem(id, action, calc.getRandomIntBetweenRange(25, 400));
	}

	private boolean actionItem(int id, MenuAction action, int delay) {
		if (inv.containsItem(id)) {
			WidgetItem item = inv.getWidgetItem(id);
			targetMenu = new LegacyMenuEntry("", "", item.getId(), action, item.getIndex(), WidgetInfo.INVENTORY.getId(), false);
			utils.doInvokeMsTime(targetMenu, delay);
			return true;
		}
		return false;
	}

}