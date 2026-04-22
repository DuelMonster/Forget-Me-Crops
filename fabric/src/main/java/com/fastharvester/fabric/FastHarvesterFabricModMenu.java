
package com.fastharvester.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;

/**
 * ModMenu integration for FastHarvester (Fabric).
 * Opens the improved vanilla config screen.
 */
public class FastHarvesterFabricModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return FastHarvesterConfigScreen::new;
	}
}
