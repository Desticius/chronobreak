from flask import Flask, render_template, jsonify
import json
import os
import time
import requests
from datetime import datetime
import uuid

app = Flask(__name__)

# Configuration
PLAYTIME_DATA_PATH = "../config/playtimedata.json"
MINECRAFT_WORLD_PATH = "../world/stats"
CACHE_TIMEOUT = 60  # Seconds

# Cache
players_cache = None
cache_timestamp = 0
uuid_to_name_cache = {}


def get_username_from_uuid(player_uuid):
    """Get username from UUID using Mojang API, with caching."""
    if player_uuid in uuid_to_name_cache:
        return uuid_to_name_cache[player_uuid]
    
    try:
        # Format UUID to standard format (with dashes)
        formatted_uuid = str(uuid.UUID(player_uuid))
        response = requests.get(f"https://api.mojang.com/user/profiles/{formatted_uuid.replace('-', '')}/names")
        if response.status_code == 200:
            data = response.json()
            if data and len(data) > 0:
                username = data[-1]["name"]  # Get the most recent name
                uuid_to_name_cache[player_uuid] = username
                return username
    except Exception as e:
        print(f"Error fetching username for UUID {player_uuid}: {e}")
    
    return player_uuid  # Fallback to using UUID if username can't be retrieved


def load_player_data():
    """Load player data from playtimedata.json and Minecraft stats files."""
    global players_cache, cache_timestamp
    
    # Check if cache is still valid
    current_time = time.time()
    if players_cache is not None and current_time - cache_timestamp < CACHE_TIMEOUT:
        return players_cache
    
    players = {}
    
    # Load playtime data
    try:
        with open(PLAYTIME_DATA_PATH, 'r') as f:
            playtime_data = json.load(f)
            
        for player_uuid, data in playtime_data.items():
            # Get or fetch player name
            player_name = data.get("playerName", get_username_from_uuid(player_uuid))
            
            players[player_uuid] = {
                "uuid": player_uuid,
                "name": player_name,
                "totalPlaytime": data.get("totalPlaytime", 0),
                "streak": data.get("streak", 0),
                "lastSeen": data.get("lastSeen", "Unknown"),
                "stats": {}
            }
    except Exception as e:
        print(f"Error loading playtime data: {e}")
    
    # Load Minecraft stats for each player
    if os.path.exists(MINECRAFT_WORLD_PATH):
        for filename in os.listdir(MINECRAFT_WORLD_PATH):
            if filename.endswith(".json"):
                player_uuid = filename.split('.')[0]
                if player_uuid in players:
                    try:
                        with open(os.path.join(MINECRAFT_WORLD_PATH, filename), 'r') as f:
                            mc_stats = json.load(f)
                            
                        # Process stats
                        stats = {}
                        
                        # Travel stats
                        travel_stats = {}
                        walked = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:walk_one_cm", 0) / 100
                        travel_stats["walked"] = round(walked, 2)
                        
                        sprinted = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:sprint_one_cm", 0) / 100
                        travel_stats["sprinted"] = round(sprinted, 2)
                        
                        swum = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:swim_one_cm", 0) / 100
                        travel_stats["swum"] = round(swum, 2)
                        
                        boated = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:boat_one_cm", 0) / 100
                        travel_stats["boated"] = round(boated, 2)
                        
                        flown = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:fly_one_cm", 0) / 100
                        travel_stats["flown"] = round(flown, 2)
                        
                        stats["travel"] = travel_stats
                        
                        # Combat stats
                        combat_stats = {}
                        damage_dealt = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:damage_dealt", 0) / 10
                        combat_stats["damage_dealt"] = round(damage_dealt, 1)
                        
                        damage_taken = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:damage_taken", 0) / 10
                        combat_stats["damage_taken"] = round(damage_taken, 1)
                        
                        mobs_killed = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:mob_kills", 0)
                        combat_stats["mobs_killed"] = mobs_killed
                        
                        stats["combat"] = combat_stats
                        
                        # Fun stats
                        fun_stats = {}
                        cake_slices = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:eat_cake_slice", 0)
                        fun_stats["cake_slices"] = cake_slices
                        
                        bell_rings = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:bell_ring", 0)
                        fun_stats["bell_rings"] = bell_rings
                        
                        flower_potted = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:pot_flower", 0)
                        fun_stats["flower_potted"] = flower_potted
                        
                        traded = mc_stats.get("stats", {}).get("minecraft:custom", {}).get("minecraft:traded_with_villager", 0)
                        fun_stats["villager_trades"] = traded
                        
                        stats["fun"] = fun_stats
                        
                        # Top mined blocks
                        mined_blocks = {}
                        for stat_key, value in mc_stats.get("stats", {}).get("minecraft:mined", {}).items():
                            block_name = stat_key.replace("minecraft:", "").replace("_", " ").title()
                            mined_blocks[block_name] = value
                        
                        stats["mined_blocks"] = dict(sorted(mined_blocks.items(), key=lambda item: item[1], reverse=True)[:10])
                        
                        # Most killed mobs
                        killed_mobs = {}
                        for stat_key, value in mc_stats.get("stats", {}).get("minecraft:killed", {}).items():
                            mob_name = stat_key.replace("minecraft:", "").replace("_", " ").title()
                            killed_mobs[mob_name] = value
                        
                        stats["killed_mobs"] = dict(sorted(killed_mobs.items(), key=lambda item: item[1], reverse=True)[:10])
                        
                        players[player_uuid]["stats"] = stats
                    except Exception as e:
                        print(f"Error loading stats for player {player_uuid}: {e}")
    
    # Update cache
    players_cache = players
    cache_timestamp = current_time
    
    return players


@app.route('/')
def index():
    """Display the leaderboard page."""
    players = load_player_data()
    
    # Sort players by total playtime
    sorted_players = sorted(players.values(), key=lambda x: x.get("totalPlaytime", 0), reverse=True)
    
    return render_template('index.html', players=sorted_players)


@app.route('/player/<uuid>')
def player(uuid):
    """Display individual player page."""
    players = load_player_data()
    
    if uuid in players:
        player_data = players[uuid]
        return render_template('player.html', player=player_data)
    else:
        return "Player not found", 404


@app.route('/api/players')
def api_players():
    """API endpoint to get all player data."""
    players = load_player_data()
    return jsonify(list(players.values()))


@app.route('/api/player/<uuid>')
def api_player(uuid):
    """API endpoint to get data for a specific player."""
    players = load_player_data()
    
    if uuid in players:
        return jsonify(players[uuid])
    else:
        return jsonify({"error": "Player not found"}), 404


if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)
