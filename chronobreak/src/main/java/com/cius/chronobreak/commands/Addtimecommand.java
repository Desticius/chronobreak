package com.cius.chronobreak.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.cius.chronobreak.config.PlaytimeData;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.UUID;

public class AddtimeCommand {
    
    public static void register(CommandDispatcher<CommandSource> dispatcher, PlaytimeData playtimeData) {
        dispatcher.register(
            Commands.literal("addtime")
                .requires(source -> source.hasPermissionLevel(2)) // Require op level for this command
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "player");
                            int minutes = IntegerArgumentType.getInteger(context, "minutes");
                            return addTime(context.getSource(), playerName, minutes, playtimeData);
                        })
                    )
                )
        );
    }
    
    private static int addTime(CommandSource source, String targetPlayerName, int minutes, PlaytimeData playtimeData) {
        MinecraftServer server = source.getServer();
        UUID targetUUID = null;
        String displayName = targetPlayerName;
        
        // Try to get player UUID from online players
        for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(targetPlayerName)) {
                targetUUID = player.getUUID();
                displayName = player.getName().getString();
                break;
            }
        }
        
        if (targetUUID != null) {
            // Use the addTime method from PlaytimeData
            playtimeData.addTime(targetUUID, minutes);
            
            source.sendFeedback(new StringTextComponent(
                    TextFormatting.GREEN + "Added " + minutes + " minutes to " + displayName + "'s time limit."), true);
            
            // Notify player if online
            ServerPlayerEntity targetPlayer = server.getPlayerList().getPlayerByUUID(targetUUID);
            if (targetPlayer != null) {
                targetPlayer.sendMessage(new StringTextComponent(
                        TextFormatting.GREEN + "An admin granted you " + minutes + " minute" + 
                        (minutes != 1 ? "s" : "") + " of bonus playtime!"), targetUUID);
            }
        } else {
            source.sendFeedback(new StringTextComponent(
                    TextFormatting.RED + "Player not found: " + targetPlayerName), false);
        }
        
        return 1;
    }
}