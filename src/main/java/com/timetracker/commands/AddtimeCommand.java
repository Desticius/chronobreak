package com.timetracker.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.timetracker.config.PlayerTimeData;
import com.timetracker.config.ConfigManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.ChatFormatting;

import java.util.UUID;

public class AddtimeCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, PlayerTimeData playerTimeData) {
        dispatcher.register(
            Commands.literal("addtime")
                .requires(source -> source.hasPermission(2)) // Require op level for this command
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "player");
                            int minutes = IntegerArgumentType.getInteger(context, "minutes");
                            return addTime(context.getSource(), playerName, minutes, playerTimeData);
                        })
                    )
                )
        );
    }
    
    private static int addTime(CommandSourceStack source, String targetPlayerName, int minutes, PlayerTimeData playerTimeData) {
        MinecraftServer server = source.getServer();
        UUID targetUUID = null;
        String displayName = targetPlayerName;
        
        // Try to get player UUID from online players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(targetPlayerName)) {
                targetUUID = player.getUUID();
                displayName = player.getName().getString();
                break;
            }
        }
        
        if (targetUUID != null) {
            // Add the bonus time
            playerTimeData.addTime(targetUUID, minutes);
            
            // Notify admin
            String messageTemplate = ConfigManager.getConfig().timeAddedMessage;
            String formattedMessage = messageTemplate
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{player}", displayName);
            Component message = Component.literal(formattedMessage)
                .withStyle(ChatFormatting.GREEN);
            source.sendSuccess(() -> message, true);
            
            // Notify player if online
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUUID);
            if (targetPlayer != null) {
                String playerMessageTemplate = ConfigManager.getConfig().playerTimeAddedMessage;
                String formattedPlayerMessage = playerMessageTemplate
                    .replace("{minutes}", String.valueOf(minutes))
                    .replace("{s}", minutes != 1 ? "s" : "");
                Component playerMessage = Component.literal(formattedPlayerMessage)
                    .withStyle(ChatFormatting.GREEN);
                targetPlayer.sendSystemMessage(playerMessage);
            }
        } else {
            Component errorMessage = Component.literal("Chronobreak: Player not found: " + targetPlayerName)
                .withStyle(ChatFormatting.RED);
            source.sendFailure(errorMessage);
        }
        
        return 1;
    }
}
