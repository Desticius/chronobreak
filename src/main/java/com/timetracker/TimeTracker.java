package com.timetracker;

import com.timetracker.config.PlayerTimeData;
import com.timetracker.events.PlayerTimeEvents;
import net.minecraftforge.common.MinecraftForge;
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
        LOGGER.info("Time Tracker mod initializing");
        
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        
        // Initialize player time data
        playerTimeData = new PlayerTimeData();
    }
    
    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Time Tracker setup phase");
        
        // Load player time data
        playerTimeData.loadData();
        
        // Register player events
        MinecraftForge.EVENT_BUS.register(new PlayerTimeEvents(playerTimeData));
    }
}
