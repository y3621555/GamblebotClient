package com.johnson.gamblebot.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.text.Text;
import net.minecraft.block.Blocks;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.util.InputUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GamblebotClient implements ClientModInitializer {

    private static KeyBinding findRedstoneKey;
    private static KeyBinding cycleRedstoneKey;
    private List<BlockPos> redstonePositions = new ArrayList<>();
    private int currentRedstoneIndex = 0;
    private Map<String, Integer> playerBets = new HashMap<>();
    private static final double WINNING_MULTIPLIER = 1.85;

    private static KeyBinding openSettingsKey;
    private int minBet = 10;  // 預設最小下注
    private int maxBet = 1000;  // 預設最大下注

    @Override
    public void onInitializeClient() {
        findRedstoneKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "搜索附近紅石",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "對賭機器人熱鍵"
        ));
        cycleRedstoneKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "設定紅石",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "對賭機器人熱鍵"
        ));

        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "打開賭博設置",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,  // 使用 'B' 鍵打開設置
                "對賭機器人熱鍵"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientReceiveMessageEvents.GAME.register(this::onChatMessage);
        System.out.println("高級賭博機器人紅石已初始化！");
    }

    private void onClientTick(MinecraftClient client) {
        if (findRedstoneKey.wasPressed()) {
            findNearbyRedstone(client);
        }
        if (cycleRedstoneKey.wasPressed()) {
            cycleRedstoneSelection();
        }
        if (openSettingsKey.wasPressed()) {
            client.setScreen(new GambleSettingsScreen(this));
        }
    }

    private void onChatMessage(Text message, boolean overlay) {
        String messageString = message.getString();
        System.out.println("收到訊息: " + messageString);

        if (messageString.startsWith("[系統] 您收到了") && messageString.contains("轉帳的")) {
            processBetPayment(messageString);
        } else if (messageString.contains("物品") && messageString.contains("被吐出")) {
            processDispenseResult(messageString);
        } else if (messageString.contains("綠寶石不足")) {
            handleInsufficientBalance();
        }
    }

    private void processBetPayment(String message) {
        Pattern pattern = Pattern.compile("\\[系統\\] 您收到了 (.+) 轉帳的 ([\\d,]+) 綠寶石");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String playerName = matcher.group(1);
            int betAmount = Integer.parseInt(matcher.group(2).replace(",", ""));

            if (betAmount < minBet || betAmount > maxBet) {
                // 下注金額超出範圍，退還給玩家
                refundBet(playerName, betAmount);
                sendGlobalMessage(playerName + " 的下注 " + betAmount + " 綠寶石超出範圍（" + minBet + "-" + maxBet + "）。已退還。");
            } else {
                // 下注金額在允許範圍內，記錄下注
                playerBets.put(playerName, betAmount);
                sendGlobalMessage(playerName + " 下注 " + betAmount + " 綠寶石。開始遊戲！");
                System.out.println("玩家 " + playerName + " 下注 " + betAmount + " 綠寶石");

                // 自動激活紅石
                MinecraftClient client = MinecraftClient.getInstance();
                clickSelectedRedstone(client);
            }
        }
    }

    private void refundBet(String playerName, int amount) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            String command = "pay " + playerName + " " + amount;
            client.player.networkHandler.sendCommand(command);
            System.out.println("退還賭注: " + command);
        }
    }

    private void processDispenseResult(String message) {
        Pattern pattern = Pattern.compile("物品 \\[(.+?)\\]\\((.+?)\\) x (\\d+) 自座標 \\( (\\d+) (\\d+) (-?\\d+) \\) 被吐出。");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            String itemName = matcher.group(2);
            boolean isWinning = itemName.equals("藍色羊毛");

            System.out.println("發射器吐出：" + itemName + (isWinning ? " (贏)" : " (輸)"));

            for (Map.Entry<String, Integer> bet : playerBets.entrySet()) {
                String playerName = bet.getKey();
                int betAmount = bet.getValue();

                if (isWinning) {
                    int winningAmount = (int) (betAmount * WINNING_MULTIPLIER);
                    boolean paymentSuccessful = payPlayer(playerName, winningAmount);
                    if (paymentSuccessful) {
                        sendGlobalMessage("恭喜 " + playerName + "！贏得了 " + winningAmount + " 綠寶石！");
                    }
                } else {
                    sendGlobalMessage(playerName + " 很遺憾，這次沒有贏。");
                }
            }

            playerBets.clear(); // 清空賭注記錄，準備下一輪
        } else {
            System.out.println("無法解析發射器訊息：" + message);
        }
    }

    private void findNearbyRedstone(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        redstonePositions.clear();
        BlockPos playerPos = client.player.getBlockPos();
        int radius = 5; // 搜索範圍，可以根據需要調整
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    if (isRedstoneBlock(client, checkPos)) {
                        redstonePositions.add(checkPos);
                    }
                }
            }
        }
        System.out.println("找到 " + redstonePositions.size() + " 個紅石方塊。");
        if (!redstonePositions.isEmpty()) {
            currentRedstoneIndex = 0;
            System.out.println("選擇了第一個紅石方塊，位置：" + redstonePositions.get(currentRedstoneIndex));
        }
    }

    private void cycleRedstoneSelection() {
        if (redstonePositions.isEmpty()) {
            System.out.println("沒有找到紅石方塊。");
            return;
        }
        currentRedstoneIndex = (currentRedstoneIndex + 1) % redstonePositions.size();
        System.out.println("選擇了第 " + (currentRedstoneIndex + 1) + " 個紅石方塊，共 " + redstonePositions.size() + " 個。");
    }

    private void clickSelectedRedstone(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        if (redstonePositions.isEmpty()) {
            client.player.sendMessage(Text.literal("沒有找到紅石方塊"), false);
            return;
        }
        BlockPos targetPos = redstonePositions.get(currentRedstoneIndex);
        Direction direction = Direction.UP; // 預設方向，可以根據需要調整
        simulateClick(client, targetPos, direction);
    }

    private boolean isRedstoneBlock(MinecraftClient client, BlockPos pos) {
        return client.world.getBlockState(pos).getBlock() == Blocks.REDSTONE_WIRE;
    }

    private void simulateClick(MinecraftClient client, BlockPos pos, Direction direction) {
        Vec3d hitPos = Vec3d.ofCenter(pos);
        BlockHitResult hitResult = new BlockHitResult(hitPos, direction, pos, false);
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);
        client.player.swingHand(Hand.MAIN_HAND);
        client.player.sendMessage(Text.literal("已點擊紅石，位置：" + pos), false);
    }

    private void sendWhisper(String playerName, String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            String command = "msg " + playerName + " " + message;
            client.player.networkHandler.sendCommand(command);
        }
    }

    private boolean payPlayer(String playerName, int amount) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            String command = "pay " + playerName + " " + amount;
            client.player.networkHandler.sendCommand(command);
            System.out.println("執行支付命令: " + command);
            return true; // 假設支付成功，實際上我們需要等待支付結果
        }
        return false;
    }

    private void handleInsufficientBalance() {
        sendGlobalMessage("機器人餘額不足，請聯繫賭場管理人員處理");
    }

    private void sendGlobalMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.networkHandler.sendChatMessage(message);
        }
    }

    // Getter 和 Setter 方法用於管理下注限制
    public int getMinBet() {
        return minBet;
    }

    public void setMinBet(int minBet) {
        this.minBet = minBet;
    }

    public int getMaxBet() {
        return maxBet;
    }

    public void setMaxBet(int maxBet) {
        this.maxBet = maxBet;
    }
}
