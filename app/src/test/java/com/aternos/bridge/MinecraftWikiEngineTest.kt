package com.aternos.bridge

import com.aternos.bridge.wiki.MinecraftWikiEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftWikiEngineTest {

    @Test
    fun testVersion262ChaosCubedSearch() {
        val sulfurCubeResults = MinecraftWikiEngine.searchLocal("sulfur cube mob 26.2")
        assertFalse("Expected local matches for sulfur cube", sulfurCubeResults.isEmpty())
        val top = sulfurCubeResults.first()
        assertTrue("Expected top result to be Sulfur Cube", top.title.contains("Sulfur Cube", ignoreCase = true))
        assertTrue("Expected details to mention block absorption", top.details.contains("absorb", ignoreCase = true))

        val chaosCubed = MinecraftWikiEngine.searchLocal("minecraft 26.2 chaos cubed")
        assertFalse("Expected local matches for 26.2", chaosCubed.isEmpty())
        assertTrue("Expected 26.2 entry", chaosCubed.first().title.contains("26.2", ignoreCase = true))
        assertTrue("Expected details to mention Sulfur Caves", chaosCubed.first().details.contains("Sulfur Caves", ignoreCase = true))
    }

    @Test
    fun testVersion261TinyTakeoverSearch() {
        val dandelionResults = MinecraftWikiEngine.searchLocal("golden dandelion baby mob aging")
        assertFalse("Expected local matches for golden dandelion", dandelionResults.isEmpty())
        val top = dandelionResults.first()
        assertTrue("Expected top result to be Golden Dandelion", top.title.contains("Golden Dandelion", ignoreCase = true))
        assertTrue("Expected details to mention gold nuggets", top.details.contains("Gold Nugget", ignoreCase = true))

        val nameTagResults = MinecraftWikiEngine.searchLocal("craftable name tag recipe 26.1")
        assertFalse("Expected matches for craftable name tag", nameTagResults.isEmpty())
        assertTrue("Expected Craftable Name Tag", nameTagResults.first().title.contains("Name Tag", ignoreCase = true))
    }

    @Test
    fun testPaleGardenAndCreakingSearch() {
        val creakingResults = MinecraftWikiEngine.searchLocal("creaking heart resin pale garden")
        assertFalse("Expected local matches for pale garden / creaking", creakingResults.isEmpty())
        val top = creakingResults.first()
        assertTrue("Expected Creaking or Pale Garden", top.title.contains("Creaking", ignoreCase = true) || top.title.contains("Pale Garden", ignoreCase = true))
    }

    @Test
    fun testTrickyTrialsFeaturesSearch() {
        val maceResults = MinecraftWikiEngine.searchLocal("how to craft mace")
        assertFalse("Expected local matches for mace", maceResults.isEmpty())
        val top = maceResults.first()
        assertTrue("Expected top result to be Mace", top.title.contains("Mace", ignoreCase = true))
        assertTrue("Expected recipe details to mention Heavy Core", top.details.contains("Heavy Core", ignoreCase = true))
        assertTrue("Expected recipe details to mention Breeze Rod", top.details.contains("Breeze Rod", ignoreCase = true))

        val crafterResults = MinecraftWikiEngine.searchLocal("crafter redstone")
        assertFalse("Expected local matches for crafter", crafterResults.isEmpty())
        assertTrue("Expected top result to be Crafter", crafterResults.first().title.contains("Crafter", ignoreCase = true))

        val breezeResults = MinecraftWikiEngine.searchLocal("breeze mob trial chamber")
        assertFalse("Expected local matches for breeze", breezeResults.isEmpty())
        assertTrue("Expected top result to be Breeze", breezeResults.first().title.contains("Breeze", ignoreCase = true))
    }

    @Test
    fun testMiningLevelsSearch() {
        val diamondResults = MinecraftWikiEngine.searchLocal("diamond y level mining")
        assertFalse("Expected local matches for diamond", diamondResults.isEmpty())
        assertTrue("Expected Diamond entry", diamondResults.first().title.contains("Diamond", ignoreCase = true))
        assertTrue("Expected optimal Y level -58", diamondResults.first().details.contains("-58"))

        val netheriteResults = MinecraftWikiEngine.searchLocal("ancient debris level")
        assertFalse("Expected local matches for ancient debris", netheriteResults.isEmpty())
        assertTrue("Expected Ancient Debris entry", netheriteResults.first().title.contains("Ancient Debris", ignoreCase = true))
        assertTrue("Expected Nether Y level 15", netheriteResults.first().details.contains("15"))
    }

    @Test
    fun testBrewingRecipesSearch() {
        val invisResults = MinecraftWikiEngine.searchLocal("invisibility potion recipe")
        assertFalse("Expected local matches for invisibility potion", invisResults.isEmpty())
        assertTrue("Expected Invisibility entry", invisResults.first().title.contains("Invisibility", ignoreCase = true))
        assertTrue("Expected Fermented Spider Eye in recipe", invisResults.first().details.contains("Fermented Spider Eye", ignoreCase = true))
    }

    @Test
    fun testAsyncWikiSearchMethod() = runBlocking {
        val searchOutput = MinecraftWikiEngine.search("mace crafting recipe")
        assertTrue("Expected formatted output with [Minecraft Wiki]", searchOutput.contains("[Minecraft Wiki]"))
        assertTrue("Expected mention of Heavy Core", searchOutput.contains("Heavy Core"))
    }
}
