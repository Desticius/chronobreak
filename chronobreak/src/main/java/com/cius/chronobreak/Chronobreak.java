package com.cius.chronobreak;

import com.cius.chronobreak.commands.AddtimeCommand;
import com.cius.chronobreak.commands.PlaytimeCommand;
import com.cius.chronobreak.commands.TimeleftCommand;
import com.cius.chronobreak.config.PlaytimeData;
import com.cius.chronobreak.events.PlayerEvents;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Chronobreak.MOD_ID)
public class Chronobreak {
    public static final String MOD_ID = "chronobreak";
    private static final Logger LOGGER = LogManager.getLogger();
    
    private PlaytimeData playtimeData;

    public Chronobreak() {
        LOGGER.info("Chronobreak mod is initializing");
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);
        
        MinecraftForge.EVENT_BUS.register(this);
        
        // Initialize playtime data
        playtimeData = new PlaytimeData();
    }
    
    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Chronobreak mod setup phase");
        
        // Load playtime data
        playtimeData.loadData();
        
        // Register player events
        MinecraftForge.EVENT_BUS.register(new PlayerEvents(playtimeData));
    }
    
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering Chronobreak commands");
        
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        // Register commands
        TimeleftCommand.register(dispatcher, playtimeData);
        PlaytimeCommand.register(dispatcher, playtimeData);
        AddtimeCommand.register(dispatcher, playtimeData);
    }
}