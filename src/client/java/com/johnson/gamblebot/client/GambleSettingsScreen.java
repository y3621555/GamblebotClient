package com.johnson.gamblebot.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class GambleSettingsScreen extends Screen {
    private TextFieldWidget minBetField;
    private TextFieldWidget maxBetField;
    private final GamblebotClient gamblebotClient;

    public GambleSettingsScreen(GamblebotClient gamblebotClient) {
        super(Text.literal("賭博設置"));
        this.gamblebotClient = gamblebotClient;
    }

    @Override
    protected void init() {
        int width = 200;
        int height = 20;
        int centerX = this.width / 2 - width / 2;

        // 最小下注輸入
        this.minBetField = new TextFieldWidget(this.textRenderer, centerX, 50, width, height, Text.literal("最小下注"));
        this.minBetField.setText(String.valueOf(gamblebotClient.getMinBet()));
        this.addDrawableChild(this.minBetField);

        // 最大下注輸入
        this.maxBetField = new TextFieldWidget(this.textRenderer, centerX, 80, width, height, Text.literal("最大下注"));
        this.maxBetField.setText(String.valueOf(gamblebotClient.getMaxBet()));
        this.addDrawableChild(this.maxBetField);

        // 保存
        this.addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> {
            saveSettings();
            this.close();
        }).dimensions(centerX, 110, width, height).build());
    }

    private void saveSettings() {
        try {
            int minBet = Integer.parseInt(minBetField.getText());
            int maxBet = Integer.parseInt(maxBetField.getText());
            gamblebotClient.setMinBet(minBet);
            gamblebotClient.setMaxBet(maxBet);
        } catch (NumberFormatException e) {
            // 處理無效情況
            System.out.println("Invalid input for bet limits");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("賭博設置"), this.width / 2, 20, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("最小下注:"), this.width / 2 - 100, 40, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("最大下注:"), this.width / 2 - 100, 70, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}