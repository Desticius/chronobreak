package com.timetracker;

import com.timetracker.commands.AddtimeCommand;
import com.timetracker.commands.PlaytimeCommand;
import com.timetracker.commands.TimeleftCommand;
import com.timetracker.commands.RemoveTimeCommand;
import com.timetracker.config.PlayerTimeData;
import com.timetracker.events.PlayerTimeEvents;
import com.timetracker.config.ConfigManager;
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

@Mod(TimeTracker.MOD_ID)
public class TimeTracker {
    public static final String MOD_ID = "timetracker";
    private static final Logger LOGGER = LogManager.getLogger();
    
    private PlayerTimeData playerTimeData;

    public TimeTracker() {
        LOGGER.info("Chronobreak mod initializing");
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);
        
        MinecraftForge.EVENT_BUS.register(this);
        
        // Load configuration
        ConfigManager.loadConfig();
        LOGGER.info("Loaded Chronobreak configuration");
        
        // Initialize player time data
        playerTimeData = new PlayerTimeData();
    }
    
    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Chronobreak mod setup phase");
        
        // Load player time data
        playerTimeData.loadData();
        
        // Register player events
        MinecraftForge.EVENT_BUS.register(new PlayerTimeEvents(playerTimeData));
    }
    
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering Chronobreak commands");
        
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        // Register commands
        TimeleftCommand.register(dispatcher, playerTimeData);
        PlaytimeCommand.register(dispatcher, playerTimeData);
        AddtimeCommand.register(dispatcher, playerTimeData);
        RemoveTimeCommand.register(dispatcher, playerTimeData);
    }
}