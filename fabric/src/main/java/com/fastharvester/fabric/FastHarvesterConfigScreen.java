package com.fastharvester.fabric;

import com.fastharvester.Config;
import com.fastharvester.enums.DurabilityMode;
import com.fastharvester.enums.RotationMode;
import com.fastharvester.enums.SeedClutterMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * FastHarvesterConfigScreen: The control panel for all your farming dreams (and nightmares).
 * Lets you tweak every knob and button, with only a mild risk of breaking things. If you ever wondered who gives you too many options, it's this class.
 */
public class FastHarvesterConfigScreen extends Screen {
    private final Screen parent;
    private Page activePage = Page.SERVER;

    // Config fields
    private EditBox tickIntervalField, scanRangeField, chestFullCooldownField, maxSpiralDurationField, seedReservePerTypeField;
    // Store field positions for label rendering
    private int tickIntervalX, tickIntervalY, scanRangeX, scanRangeY, chestFullCooldownX, chestFullCooldownY, maxSpiralDurationX, maxSpiralDurationY, seedReservePerTypeX, seedReservePerTypeY;
    private DurabilityMode durabilityModeValue;
    private RotationMode rotationModeValue;
    private SeedClutterMode seedClutterModeValue;
    private boolean mendingNegationValue;
    private boolean debugLoggingValue;
    private boolean harvestParticlesValue;

    private String statusMessage;
    private int statusColor = 0xFF5555;

    public FastHarvesterConfigScreen(Screen parent) {
        super(Component.literal("FastHarvester Config"));
        this.parent = parent;
    }


    protected void init() {
        super.init();
        this.clearWidgets();

        // Always reload config from disk so UI matches file
        com.fastharvester.Config.load();
        // Re-initialize all config field values from the freshly loaded config
        this.durabilityModeValue = Config.durabilityMode;
        this.rotationModeValue = Config.rotationMode;
        this.seedClutterModeValue = Config.seedClutterMode;
        this.mendingNegationValue = Config.mendingNegation;
        this.debugLoggingValue = Config.debugLogging;
        this.harvestParticlesValue = Config.harvestParticles;

        int colW = 150, rowH = 34;
        int leftX = this.width / 2 - colW - 8;
        int rightX = this.width / 2 + 8;
        int startY = 64;

        if (activePage == Page.SERVER) {
            // Tick Interval
            tickIntervalX = leftX;
            tickIntervalY = startY;
            this.tickIntervalField = addIntField(leftX, startY, Config.tickInterval, v -> {});
            // Scan Range
            scanRangeX = rightX;
            scanRangeY = startY;
            this.scanRangeField = addIntField(rightX, startY, Config.scanRange, v -> {});
            // Durability Mode
            var durabilityBtn = CycleButton.<DurabilityMode>builder(v -> Component.literal(v.name()), durabilityModeValue).withValues(DurabilityMode.values())
                .create(leftX, startY + rowH, colW, 20, Component.literal("Durability Mode"), (b, v) -> durabilityModeValue = v);
            this.addRenderableWidget(durabilityBtn);
            // Mending Negation
            var mendingBtn = CycleButton.onOffBuilder(mendingNegationValue)
                .withValues(Boolean.TRUE, Boolean.FALSE)
                .create(rightX, startY + rowH, colW, 20, Component.literal("Mending Negation"), (b, v) -> mendingNegationValue = v);
            this.addRenderableWidget(mendingBtn);
            // Debug Logging
            var debugBtn = CycleButton.onOffBuilder(debugLoggingValue)
                .withValues(Boolean.TRUE, Boolean.FALSE)
                .create(leftX, startY + rowH * 2, colW, 20, Component.literal("Debug Logging"), (b, v) -> debugLoggingValue = v);
            this.addRenderableWidget(debugBtn);
            // Chest Full Cooldown
            chestFullCooldownX = rightX;
            chestFullCooldownY = startY + rowH * 2;
            this.chestFullCooldownField = addIntField(rightX, startY + rowH * 2, Config.chestFullCooldownTicks, v -> {});
            // Max Spiral Duration
            maxSpiralDurationX = leftX;
            maxSpiralDurationY = startY + rowH * 3;
            this.maxSpiralDurationField = addIntField(leftX, startY + rowH * 3, Config.maxSpiralDurationTicks, v -> {});
            // Rotation Mode
            var rotationBtn = CycleButton.<RotationMode>builder(v -> Component.literal(v.name()), rotationModeValue).withValues(RotationMode.values())
                .create(rightX, startY + rowH * 3, colW, 20, Component.literal("Rotation Mode"), (b, v) -> rotationModeValue = v);
            this.addRenderableWidget(rotationBtn);
            // Seed Clutter
            var seedClutterBtn = CycleButton.<SeedClutterMode>builder(v -> Component.literal(v.name()), seedClutterModeValue).withValues(SeedClutterMode.values())
                .create(leftX, startY + rowH * 4, colW, 20, Component.literal("Seed Clutter"), (b, v) -> seedClutterModeValue = v);
            this.addRenderableWidget(seedClutterBtn);
            // Seed Reserve Per Type
            seedReservePerTypeX = rightX;
            seedReservePerTypeY = startY + rowH * 4;
            this.seedReservePerTypeField = addIntField(rightX, startY + rowH * 4, Config.seedReservePerType, v -> {});
        } else {
            // Client page
            int x = this.width / 2 - colW / 2;
            int y = 76;
            var particlesBtn = CycleButton.onOffBuilder(mendingNegationValue)
                .withValues(Boolean.TRUE, Boolean.FALSE)
                .create(x, y, colW, 20, Component.literal("Harvest Particles"), (b, v) -> harvestParticlesValue = v);
            this.addRenderableWidget(particlesBtn);
        }

        int footerY = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
                .pos(this.width / 2 - 102, footerY).size(100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .pos(this.width / 2 + 2, footerY).size(100, 20).build());
    }

    private void buildClientPage() {
        int colW = 150;
        int x = this.width / 2 - colW / 2;
        int y = 76;
        // Harvest Particles
        var particlesBtn = CycleButton.onOffBuilder(mendingNegationValue)
            .withValues(Boolean.TRUE, Boolean.FALSE)
            .create(x, y, colW, 20, Component.literal("Harvest Particles"), (b, v) -> harvestParticlesValue = v);
        this.addRenderableWidget(particlesBtn);
    }



    private EditBox addIntField(int x, int y, int value, Consumer<String> onChange) {
        EditBox field = new EditBox(this.font, x, y + 10, 150, 20, Component.empty());
        field.setValue(Integer.toString(value));
        field.setResponder(onChange);
        this.addRenderableWidget(field);
        return field;
    }

    private void switchPage(Page page) {
        if (this.activePage != page) {
            this.activePage = page;
            this.init();
        }
    }

    private void saveAndClose() {
        try {
            Config.tickInterval = parsePositive(tickIntervalField.getValue(), "Tick Interval");
            Config.scanRange = parsePositive(scanRangeField.getValue(), "Scan Range");
            Config.chestFullCooldownTicks = parseNonNegative(chestFullCooldownField.getValue(), "Chest Full Cooldown");
            Config.maxSpiralDurationTicks = parsePositive(maxSpiralDurationField.getValue(), "Max Spiral Duration");
            Config.seedReservePerType = parseNonNegative(seedReservePerTypeField.getValue(), "Seed Reserve Per Type");
            Config.durabilityMode = durabilityModeValue;
            Config.mendingNegation = mendingNegationValue;
            Config.debugLogging = debugLoggingValue;
            Config.rotationMode = rotationModeValue;
            Config.seedClutterMode = seedClutterModeValue;
            Config.harvestParticles = harvestParticlesValue;
            Config.save();
            this.onClose();
        } catch (IllegalArgumentException e) {
            this.statusMessage = e.getMessage();
            this.statusColor = 0xFF5555;
        }
    }

    private int parsePositive(String value, String label) {
        int parsed = parseInteger(value, label);
        if (parsed <= 0) throw new IllegalArgumentException(label + " must be greater than 0.");
        return parsed;
    }
    private int parseNonNegative(String value, String label) {
        int parsed = parseInteger(value, label);
        if (parsed < 0) throw new IllegalArgumentException(label + " must be 0 or greater.");
        return parsed;
    }
    private int parseInteger(String value, String label) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(label + " must be a whole number."); }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title.getString(), this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, this.activePage == Page.SERVER ? "Server Settings" : "Client Settings", this.width / 2, 52, 0xA0A0A0);
        if (this.statusMessage != null) {
            graphics.drawCenteredString(this.font, this.statusMessage, this.width / 2, this.height - 40, this.statusColor);
        }
    }

    private enum Page { SERVER, CLIENT }
}




