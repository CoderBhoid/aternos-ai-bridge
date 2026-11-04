package com.aternos.bridge.wiki

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Entry within the curated Minecraft Knowledge Base.
 */
data class WikiEntry(
    val title: String,
    val category: String,
    val summary: String,
    val details: String,
    val syntaxOrRecipe: String = ""
)

/**
 * Comprehensive Minecraft Wiki Database and Search Engine for 1.20 / 1.21+ Tricky Trials.
 * Supports fast in-memory indexing, semantic keyword matching, and live MediaWiki API queries.
 */
object MinecraftWikiEngine {

    private const val TAG = "MinecraftWikiEngine"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Curated, up-to-date Knowledge Base.
     */
    val KNOWLEDGE_BASE: List<WikiEntry> = listOf(
        // --- 26.2 "CHAOS CUBED" (LATEST 2026 RELEASE) ---
        WikiEntry(
            title = "Minecraft 26.2 (Chaos Cubed)",
            category = "26.2 Game Drop",
            summary = "Latest major release (June 2026) featuring Sulfur Caves, Sulfur Cube mob, Cinnabar, Potent Sulfur, and Vulkan support.",
            details = "Introduces the new calendar versioning (26.2). Key highlights: 1) Sulfur Caves Biome with geysers and falling Sulfur Spikes. 2) Sulfur Cube mob that absorbs blocks to gain elemental archetypes (e.g., Magma block grants 'Hot' burn damage, Stone grants 'Slow Bouncy') and can be carried in buckets. 3) Potent Sulfur blocks causing Nausea in water. 4) Java Edition in-game Friends List and experimental Vulkan rendering.",
            syntaxOrRecipe = "Command: summon sulfur_cube ~ ~ ~, give @p sulfur_block, give @p cinnabar_block"
        ),
        WikiEntry(
            title = "Sulfur Cube",
            category = "26.2 Mobs",
            summary = "Subterranean mob from 26.2 Sulfur Caves that absorbs blocks and acquires traits based on the absorbed block.",
            details = "Found in Sulfur Caves. Absorbs blocks in its environment: Magma Block makes it emit fire particles and deal contact damage; Stone increases weight and jump height. Players can transport it in buckets, leash it, or shear it with Shears to safely extract the absorbed block.",
            syntaxOrRecipe = "Spawn Command: summon sulfur_cube ~ ~ ~"
        ),
        WikiEntry(
            title = "Sulfur & Cinnabar Blocks",
            category = "26.2 Blocks",
            summary = "New decorative and functional block sets introduced in Minecraft 26.2.",
            details = "Sulfur blocks and Cinnabar blocks form complete building sets (blocks, stairs, slabs, pillars). Potent Sulfur emits yellow noxious gases and inflicts Nausea on entities swimming in nearby water.",
            syntaxOrRecipe = "Command: give @p sulfur, give @p sulfur_block, give @p cinnabar"
        ),

        // --- 26.1 "TINY TAKEOVER" (MARCH 2026 RELEASE) ---
        WikiEntry(
            title = "Minecraft 26.1 (Tiny Takeover)",
            category = "26.1 Game Drop",
            summary = "Major update (March 2026) featuring 30+ baby mob redesigns, Golden Dandelion, craftable Name Tags, and Trumpet Note Blocks.",
            details = "Overhauls over 30 baby mob models with custom cute textures and audio. Adds the Golden Dandelion (freezes baby aging) and craftable Name Tags (Paper + Metal Nugget). Note Blocks placed on Copper Blocks now sound like Trumpets, with pitch tied to copper oxidation level.",
            syntaxOrRecipe = "Command: give @p golden_dandelion, give @p name_tag"
        ),
        WikiEntry(
            title = "Golden Dandelion",
            category = "26.1 Items",
            summary = "Item introduced in 26.1 crafted with 1 Dandelion and 8 Gold Nuggets that halts baby mob growth permanently.",
            details = "Crafted by surrounding a Dandelion with 8 Gold Nuggets in a Crafting Table. Feeding it to any baby animal permanently locks it in its baby state. Feeding a second Golden Dandelion unfreezes the mob, allowing it to grow into an adult normally.",
            syntaxOrRecipe = "Crafting: [Row 1: Nugget, Nugget, Nugget] [Row 2: Nugget, Dandelion, Nugget] [Row 3: Nugget, Nugget, Nugget]. Command: give @p golden_dandelion"
        ),
        WikiEntry(
            title = "Craftable Name Tag",
            category = "26.1 Crafting",
            summary = "Crafting recipe for Name Tags introduced in 26.1 using Paper and Metal Nuggets.",
            details = "Players can craft Name Tags on a crafting table using 1 Paper and 1 Metal Nugget (Iron Nugget, Gold Nugget, or Copper Nugget). Used on an anvil to rename entities.",
            syntaxOrRecipe = "Crafting: 1 Paper + 1 Iron/Gold/Copper Nugget. Command: give @p name_tag"
        ),

        // --- 1.21.4 "THE GARDEN AWAKENS" (PALE GARDEN & CREAKING) ---
        WikiEntry(
            title = "Pale Garden & Creaking",
            category = "1.21.4 Biomes & Mobs",
            summary = "Eerie monochrome forest biome with the Creaking mob, Creaking Heart, Resin, Pale Oak, and Eyeblossoms.",
            details = "Pale Garden is a silent forest with Pale Oak trees and hanging Pale Moss. At night, the Creaking spawns: it is quantum-locked (cannot move while looked at, moves fast when unseen) and invulnerable to direct attacks. Defeating it requires finding and destroying its Creaking Heart located inside a Pale Oak tree.",
            syntaxOrRecipe = "Spawn Command: summon creaking ~ ~ ~, give @p creaking_heart"
        ),
        WikiEntry(
            title = "Creaking Heart & Resin",
            category = "1.21.4 Blocks & Items",
            summary = "Living block inside Pale Oak trees that generates Creakings; drops Resin clumps when the Creaking is struck.",
            details = "Hitting a Creaking reveals particle trails pointing to its Creaking Heart. Breaking the heart destroys the Creaking. Can be harvested with Silk Touch or crafted using 2 Pale Oak Logs and 1 Resin. Resin can be smelted into Resin Bricks for building or used for Resin Armor Trims.",
            syntaxOrRecipe = "Crafting: 2 Pale Oak Logs + 1 Resin -> Creaking Heart. Command: give @p resin_clump, give @p resin_bricks"
        ),
        WikiEntry(
            title = "Eyeblossom & Bundles",
            category = "1.21.4 Items",
            summary = "Pale Garden flowers that open at night and Bundles for managing 64 mixed items in one slot.",
            details = "Eyeblossoms are closed during day (harvests Gray Dye) and open with orange glowing eyes at night (harvests Orange Dye). Bundles store up to 64 items of any combination in a single inventory slot, crafted with 1 Leather and 1 String.",
            syntaxOrRecipe = "Crafting: 1 Leather + 1 String -> Bundle. Command: give @p eyeblossom, give @p bundle"
        ),

        // --- 1.21 TRICKY TRIALS FEATURES ---
        WikiEntry(
            title = "Mace",
            category = "1.21 Weapons",
            summary = "Heavy melee weapon introduced in Minecraft 1.21 with unique smash attack damage scaling based on fall distance.",
            details = "Crafted with 1 Heavy Core (placed top center) and 1 Breeze Rod (placed bottom center). Deals massive damage when falling from heights. Prevents fall damage if a smash attack hits an entity. Exclusive enchantments: Density (increases damage per block fallen), Breach (reduces target armor effectiveness), Wind Burst (launches player up on successful hit).",
            syntaxOrRecipe = "Crafting: [Row 1: None, Heavy Core, None] [Row 2: None, Breeze Rod, None]. Command: give @p mace"
        ),
        WikiEntry(
            title = "Crafter",
            category = "1.21 Redstone",
            summary = "Automated crafting table that creates items when receiving a redstone pulse.",
            details = "Crafted using 5 Iron Ingots, 1 Crafting Table, 2 Redstone Dust, and 1 Dropper. Slots can be toggled on/off to lock recipe patterns. When powered by redstone, it outputs the crafted item or drops it into an adjacent container.",
            syntaxOrRecipe = "Crafting: [Row 1: Iron, Iron, Iron] [Row 2: Iron, Crafting Table, Iron] [Row 3: Redstone, Dropper, Redstone]. Command: give @p crafter"
        ),
        WikiEntry(
            title = "Breeze",
            category = "1.21 Mobs",
            summary = "Hostile mob found in Trial Chambers that jumps around and shoots Wind Charges.",
            details = "Immune to projectiles (deflects arrows and tridents). Shoots Wind Charges that create knockback bursts and toggle buttons/trapdoors/doors. Drops 1-2 Breeze Rods upon defeat.",
            syntaxOrRecipe = "Spawn Command: summon breeze ~ ~ ~"
        ),
        WikiEntry(
            title = "Wind Charge",
            category = "1.21 Items",
            summary = "Throwable item dropped/crafted from Breeze Rods that propels players into the air and triggers redstone.",
            details = "Crafted by placing 1 Breeze Rod in a crafting grid (yields 4 Wind Charges). Players can throw it at their feet to gain a super jump without taking fall damage upon landing.",
            syntaxOrRecipe = "Crafting: 1 Breeze Rod -> 4 Wind Charges. Command: give @p wind_charge 16"
        ),
        WikiEntry(
            title = "Trial Spawner & Ominous Spawner",
            category = "1.21 Structures",
            summary = "Special spawner in Trial Chambers that adjusts combat difficulty based on player count and ejects loot.",
            details = "Spawns waves of mobs. When all mobs are defeated, it ejects loot including Trial Keys. Under Bad Omen (Ominous Trial), it transforms into an Ominous Spawner that spawns mobs with armor, weapons, and dropped potions, yielding Ominous Trial Keys for Ominous Vaults.",
            syntaxOrRecipe = "Command: give @p trial_spawner, give @p trial_key, give @p ominous_trial_key"
        ),
        WikiEntry(
            title = "Ominous Bottle",
            category = "1.21 Items",
            summary = "Consumable potion item dropped by Raid Captains or obtained from Trial Chambers that grants Bad Omen.",
            details = "Drinking the bottle inflicts Bad Omen I through V for 1 hour 40 minutes. Entering a village triggers a Raid. Entering a Trial Chamber converts spawners to Ominous Spawners with higher tier loot (like Heavy Core).",
            syntaxOrRecipe = "Command: give @p ominous_bottle[ominous_bottle_amplifier=4] 1"
        ),
        WikiEntry(
            title = "Bogged",
            category = "1.21 Mobs",
            summary = "Poison-arrow shooting skeleton variant found in Swamps, Mangrove Swamps, and Trial Chambers.",
            details = "Has 16 HP (8 hearts). Shoots Poison Arrows. Can be sheared with Shears to drop 2 Brown or Red Mushrooms.",
            syntaxOrRecipe = "Spawn Command: summon bogged ~ ~ ~"
        ),
        WikiEntry(
            title = "Armadillo & Wolf Armor",
            category = "1.20.5+ Mobs & Items",
            summary = "Passive mob in Savannas and Badlands that rolls into a shell and drops Armadillo Scutes.",
            details = "Brushing an Armadillo with a Brush yields Armadillo Scutes. 6 Armadillo Scutes are crafted into Wolf Armor, which protects tamed wolves from explosions and attacks and can be dyed in 16 colors.",
            syntaxOrRecipe = "Crafting: 6 Armadillo Scutes -> Wolf Armor. Command: give @p armadillo_scute, give @p wolf_armor"
        ),
        WikiEntry(
            title = "Copper Bulb",
            category = "1.21 Redstone",
            summary = "Light-emitting block that toggles state (ON/OFF) on redstone pulses (T-Flip-Flop).",
            details = "Crafted with 3 Copper Blocks, 1 Blaze Rod, and 1 Redstone Dust. Oxidation level affects light level output: Unofficially 15 down to 4 when oxidized. Waxing preserves light level.",
            syntaxOrRecipe = "Crafting: 3 Copper Blocks + 1 Blaze Rod + 1 Redstone. Command: give @p copper_bulb"
        ),

        // --- ORE GENERATION & Y-LEVELS (1.18 - 1.21) ---
        WikiEntry(
            title = "Diamond Ore Mining Levels",
            category = "Ore Generation",
            summary = "Optimal Y-level for finding Diamond Ore is Y = -58.",
            details = "Diamonds generate between Y = 14 and Y = -63. Generation density increases as you go deeper. Y = -58 is the safest optimal level because bedrock begins generating at Y = -60 to -64.",
            syntaxOrRecipe = "Recommended coordinates: Y = -58 (strip mining or cave exploration)"
        ),
        WikiEntry(
            title = "Ancient Debris Mining Levels",
            category = "Ore Generation",
            summary = "Optimal Y-level for Ancient Debris in the Nether is Y = 15.",
            details = "Generates between Y = 8 and Y = 119 in the Nether. The highest concentration is at Y = 15. Best mined using TNT, Bed explosions, or Netherite Pickaxes with Efficiency V.",
            syntaxOrRecipe = "Recommended coordinates: Nether Y = 15"
        ),
        WikiEntry(
            title = "Iron Ore Mining Levels",
            category = "Ore Generation",
            summary = "Optimal Y-levels for Iron Ore are Y = 16 (underground) and Y = 232 (high mountains).",
            details = "Iron generates from Y = -64 to Y = 320. Peaks at Y = 16 for standard underground mining, and massive veins generate in high mountain peaks around Y = 232.",
            syntaxOrRecipe = "Recommended coordinates: Y = 16 or Y = 232"
        ),
        WikiEntry(
            title = "Gold Ore Mining Levels",
            category = "Ore Generation",
            summary = "Optimal Y-levels for Gold are Y = -16 (worldwide) and Y = 32 to 256 in Badlands / Mesa biomes.",
            details = "Badlands biomes generate massive amounts of Gold Ore between Y = 32 and Y = 256. In other biomes, Gold generates from Y = -64 to Y = 32, peaking at Y = -16. Nether Gold Ore is abundant across all Nether levels.",
            syntaxOrRecipe = "Recommended: Badlands Y = 32-256, or underground Y = -16"
        ),

        // --- BREWING & POTIONS ---
        WikiEntry(
            title = "Potion of Invisibility",
            category = "Brewing",
            summary = "Brewing recipe: Fermented Spider Eye added to a Potion of Night Vision.",
            details = "Step 1: Awkward Potion (Nether Wart + Water Bottle). Step 2: Night Vision (Golden Carrot + Awkward Potion). Step 3: Invisibility (Fermented Spider Eye + Night Vision). Extend duration to 8:00 with Redstone Dust.",
            syntaxOrRecipe = "Brew: Nether Wart -> Golden Carrot -> Fermented Spider Eye (+ Redstone for 8:00). Command: give @p potion[potion_contents={potion:\"minecraft:invisibility\"}]"
        ),
        WikiEntry(
            title = "Potion of Slow Falling",
            category = "Brewing",
            summary = "Brewing recipe: Phantom Membrane added to an Awkward Potion.",
            details = "Step 1: Awkward Potion (Nether Wart + Water Bottle). Step 2: Slow Falling (Phantom Membrane + Awkward Potion). Extends with Redstone Dust to 4:00. Prevents all fall damage and allows gentle gliding.",
            syntaxOrRecipe = "Brew: Nether Wart -> Phantom Membrane (+ Redstone). Command: give @p potion[potion_contents={potion:\"minecraft:slow_falling\"}]"
        ),
        WikiEntry(
            title = "Potion of Strength",
            category = "Brewing",
            summary = "Brewing recipe: Blaze Powder added to an Awkward Potion.",
            details = "Step 1: Nether Wart + Water Bottle -> Awkward Potion. Step 2: Blaze Powder + Awkward Potion -> Strength I (+3 damage). Add Glowstone for Strength II (+6 damage) or Redstone for 8:00 duration.",
            syntaxOrRecipe = "Brew: Nether Wart -> Blaze Powder (+ Glowstone for Strength II). Command: give @p potion[potion_contents={potion:\"minecraft:strong_strength\"}]"
        ),
        WikiEntry(
            title = "Potion of Water Breathing",
            category = "Brewing",
            summary = "Brewing recipe: Pufferfish added to an Awkward Potion.",
            details = "Step 1: Nether Wart + Water Bottle -> Awkward Potion. Step 2: Pufferfish + Awkward Potion -> Water Breathing (3:00). Add Redstone Dust to extend to 8:00.",
            syntaxOrRecipe = "Brew: Nether Wart -> Pufferfish (+ Redstone for 8:00). Command: give @p potion[potion_contents={potion:\"minecraft:water_breathing\"}]"
        ),

        // --- COMMANDS & SYNTAX ---
        WikiEntry(
            title = "Give Command",
            category = "Commands",
            summary = "/give <targets> <item> [count]",
            details = "Gives specified items to target players. Example: give @p diamond 64, give GamerBhoid mace 1, give @a cooked_beef 16.",
            syntaxOrRecipe = "Syntax: give <player|selector> <item_id> [count]"
        ),
        WikiEntry(
            title = "Time Command",
            category = "Commands",
            summary = "/time set <day|night|noon|midnight|value>",
            details = "Sets the in-game world time. Values: day (1000), noon (6000), night (13000), midnight (18000). Example: time set day, time set 0.",
            syntaxOrRecipe = "Syntax: time set <day|night|noon|midnight|ticks>"
        ),
        WikiEntry(
            title = "Weather Command",
            category = "Commands",
            summary = "/weather <clear|rain|thunder> [duration]",
            details = "Changes the world weather. Duration is in seconds. Example: weather clear, weather thunder 600.",
            syntaxOrRecipe = "Syntax: weather <clear|rain|thunder> [duration]"
        ),
        WikiEntry(
            title = "Teleport Command",
            category = "Commands",
            summary = "/tp <target> <destination|x y z>",
            details = "Teleports players or entities. Example: tp GamerBhoid ~ ~10 ~, tp Steve Alex, tp @p 100 64 -200.",
            syntaxOrRecipe = "Syntax: tp <target> <x y z> or tp <target> <destination_player>"
        ),
        WikiEntry(
            title = "Effect Command",
            category = "Commands",
            summary = "/effect give <targets> <effect> [seconds] [amplifier] [hideParticles]",
            details = "Applies a status effect to target entities. Example: effect give GamerBhoid speed 120 1, effect give @p night_vision 300 0 true, effect clear @p.",
            syntaxOrRecipe = "Syntax: effect give <target> <effect_id> [duration] [amplifier] [hide_particles]"
        ),
        WikiEntry(
            title = "Summon Command",
            category = "Commands",
            summary = "/summon <entity> [pos] [nbt]",
            details = "Spawns an entity in the world at the specified location. Example: summon breeze ~ ~ ~, summon iron_golem ~ ~ ~, summon wolf ~ ~ ~.",
            syntaxOrRecipe = "Syntax: summon <entity_type> [x y z] [nbt]"
        ),
        WikiEntry(
            title = "Netherite Upgrade Template",
            category = "Smithing & Crafting",
            summary = "Item required in a Smithing Table to upgrade Diamond gear to Netherite.",
            details = "Found exclusively in Bastion Remnants chests. Duplicated in a Crafting Table using: 1 Netherite Upgrade Smithing Template + 7 Diamonds + 1 Netherrack (yields 2 templates). In Smithing Table: Template + Diamond Gear + Netherite Ingot -> Netherite Gear.",
            syntaxOrRecipe = "Duplication: 7 Diamonds + 1 Netherrack + 1 Template. Command: give @p netherite_upgrade_smithing_template"
        )
    )

    /**
     * Searches the built-in knowledge base with keyword matching and relevance ranking.
     */
    fun searchLocal(query: String): List<WikiEntry> {
        val tokens = query.lowercase().split(Regex("[\\s,;!?]+")).filter { it.isNotBlank() && it.length > 2 }
        if (tokens.isEmpty()) {
            return KNOWLEDGE_BASE.take(5)
        }

        return KNOWLEDGE_BASE.map { entry ->
            var score = 0
            val titleLower = entry.title.lowercase()
            val summaryLower = entry.summary.lowercase()
            val detailsLower = entry.details.lowercase()
            val categoryLower = entry.category.lowercase()
            val recipeLower = entry.syntaxOrRecipe.lowercase()

            val titleWords = titleLower.split(Regex("[\\s,()/:_-]+"))

            for (token in tokens) {
                if (titleWords.any { it == token }) {
                    score += 40 // Exact title word match (e.g. "mace", "crafter", "diamond")
                } else if (titleLower.contains(token)) {
                    score += 15
                }
                if (categoryLower.contains(token)) score += 8
                if (recipeLower.contains(token)) score += 6
                if (summaryLower.contains(token)) score += 4
                if (detailsLower.contains(token)) score += 2
            }
            Pair(entry, score)
        }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Searches the official Minecraft Wiki API (https://minecraft.wiki/api.php)
     * with graceful fallback to the indexed local knowledge base.
     *
     * @param query The search query string (e.g. "mace recipe", "diamond y level", "brewing invisibility").
     * @return Formatted summary markdown text containing relevant Minecraft Wiki facts.
     */
    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext "Please provide a Minecraft topic or item to search."

        // 1. Try local index first for instant match
        val localMatches = searchLocal(cleanQuery)
        if (localMatches.isNotEmpty()) {
            val top = localMatches.first()
            val formatted = buildString {
                appendLine("**[Minecraft Wiki] ${top.title}** (${top.category})")
                appendLine(top.summary)
                appendLine(top.details)
                if (top.syntaxOrRecipe.isNotBlank()) {
                    appendLine("• *Syntax / Recipe:* ${top.syntaxOrRecipe}")
                }
                if (localMatches.size > 1) {
                    appendLine("• *Related:* " + localMatches.drop(1).take(2).joinToString(", ") { it.title })
                }
            }.trim()
            return@withContext formatted
        }

        // 2. Query Minecraft Wiki MediaWiki API
        try {
            val url = "https://minecraft.wiki/api.php?action=opensearch&search=${java.net.URLEncoder.encode(cleanQuery, "UTF-8")}&limit=3&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AternosAiBridge/1.0 (Android; Minecraft Assistant)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() >= 4) {
                        val titles = jsonArray.getJSONArray(1)
                        val descriptions = jsonArray.getJSONArray(2)
                        val links = jsonArray.getJSONArray(3)

                        if (titles.length() > 0) {
                            val title = titles.optString(0)
                            val desc = descriptions.optString(0).ifBlank { "Minecraft wiki reference for $title." }
                            val link = links.optString(0)
                            return@withContext "**[Minecraft Wiki] $title**\n$desc\nRead more: $link"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Live wiki query failed, falling back: ${e.message}")
        }

        // 3. Fallback: General advice
        "**[Minecraft Wiki] Search for '$cleanQuery':** No exact match found. For 1.21 features, check Mace, Crafter, Breeze, Trial Chambers, Wind Charges, and Ominous Bottles."
    }
}
