package com.aternos.bridge.ai

import android.util.Log
import com.aternos.bridge.data.AppConfig
import com.aternos.bridge.wiki.MinecraftWikiEngine
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.FunctionDeclaration
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native Google GenAI SDK provider supporting Gemini 2.0 Flash, Gemini 1.5, and Gemma 2 models.
 * Implements structured function calling for `execute_minecraft_command` and `search_minecraft_wiki`.
 */
class GoogleGenAiProvider : AiProvider {

    private val tag = "GoogleGenAiProvider"

    private val mcCommandTool = FunctionDeclaration(
        name = "execute_minecraft_command",
        description = "Runs an OP command on the Minecraft server. Do NOT include leading slashes. Example: give GamerBhoid diamond 64, time set day, weather clear",
        parameters = listOf(
            Schema.str("command", "Exact MC command without leading slash (e.g. 'give GamerBhoid diamond 64', 'time set day')"),
            Schema.str("explanation", "Short in-game broadcast message explaining action to players")
        ),
        requiredParameters = listOf("command")
    )

    private val wikiSearchTool = FunctionDeclaration(
        name = "search_minecraft_wiki",
        description = "Searches the up-to-date Minecraft wiki for crafting recipes, mob facts, 1.21 Tricky Trials items (Mace, Crafter, Breeze, Wind Charge), ore Y-levels, or command syntax.",
        parameters = listOf(
            Schema.str("query", "Keywords or topic to search (e.g. 'mace recipe', 'diamond y level', 'brewing invisibility')")
        ),
        requiredParameters = listOf("query")
    )

    override suspend fun processPrompt(player: String, prompt: String, config: AppConfig): AgentResult =
        withContext(Dispatchers.IO) {
            val apiKey = config.apiKey.trim()
            if (apiKey.isBlank()) {
                return@withContext AgentResult.Error("Google AI Studio API key is missing. Please enter your key in Setup.")
            }

            val modelName = config.effectiveModel.ifBlank { "gemini-2.0-flash" }

            val isOperator = !config.enforceOperatorOnlyCommands ||
                    player.equals(config.authorizedOperatorPlayer.trim(), ignoreCase = true)

            val operatorNotice = if (isOperator) {
                "Player '$player' IS the authorized server operator with full OP command execution privileges. If they ask to run a command (give items, change weather/time, teleport, summon, or 'slime/kill' someone), invoke 'execute_minecraft_command'."
            } else {
                "Player '$player' is a standard player (NOT operator). Only operator '${config.authorizedOperatorPlayer}' can execute OP commands. If '$player' asks to run commands or spawn items, DO NOT invoke execute_minecraft_command. Instead, answer with funny Gen Z refusal or Minecraft Wiki information in chat."
            }

            val systemInstructionText = """
                You are an intelligent, high-energy Minecraft server homie with deep knowledge up to Minecraft 26.2 Chaos Cubed, 26.1 Tiny Takeover, 1.21.4 Pale Garden, and 1.21 Tricky Trials (Mace, Crafter, Breeze, Sulfur Cube, Golden Dandelion, Creaking, Wind Charges, Ominous Bottles, Potions, Ore Y-levels).

                CRITICAL CHAT LIMIT:
                - Minecraft in-game chat has a strict 256-character limit per message!
                - Always keep your chat replies concise, snappy, and under 200 characters (preferably 1-2 punchy lines, max ~120-140 chars per line). Never write walls of text.

                PERSONALITY & GEN Z GAMER HUMOR:
                - You speak with authentic Gen Z gamer slang, charisma, and good humor.
                - Use slang naturally: "twin", "gng", "chat", "no cap", "fr", "cooked", "rizz", "bussin", "valid", "locked in", "clutch", "lowkey", "highkey", "goated", "bet".
                - Slang Decoder:
                  * "slime this guy" / "slime him" / "slime [player]" = kill/eliminate him (e.g. run command 'kill <player>' if authorized operator).
                  * "opp" / "opps" = enemy mob or hostile player.
                  * "cook" / "cooked" = doomed, destroyed, or crafting/grinding.
                  * "bussin" = great item, gear, or recipe.

                COMMAND EXECUTION:
                $operatorNotice
                - Do NOT include leading slashes in commands (e.g., use 'time set day' or 'kill Steve', not '/time set day').
                - If the player is asking a question or chatting, answer in a concise, humorous Gen Z chat message under 140 characters without calling command tools.
                - If you need to verify crafting recipes, mob mechanics, or wiki facts, call 'search_minecraft_wiki'.
            """.trimIndent()

            try {
                // Try function-calling model configuration
                val generativeModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = apiKey,
                    systemInstruction = content { text(systemInstructionText) },
                    tools = listOf(Tool(listOf(mcCommandTool, wikiSearchTool))),
                    safetySettings = listOf(
                        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
                        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
                        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
                    )
                )

                val response = generativeModel.generateContent(
                    content {
                        text("Player $player says: $prompt")
                    }
                )

                // Check for function calls
                val functionCalls = response.functionCalls
                if (!functionCalls.isNullOrEmpty()) {
                    val firstCall = functionCalls.first()
                    if (firstCall.name == "execute_minecraft_command") {
                        if (!isOperator) {
                            return@withContext AgentResult.ChatMessage("Only operator '${config.authorizedOperatorPlayer}' can execute server commands.")
                        }
                        val command = firstCall.args["command"]?.toString()?.trim() ?: ""
                        val explanation = firstCall.args["explanation"]?.toString()?.trim()
                        if (command.isNotBlank()) {
                            return@withContext AgentResult.ExecuteCommand(command, explanation)
                        }
                    } else if (firstCall.name == "search_minecraft_wiki") {
                        val query = firstCall.args["query"]?.toString()?.trim() ?: prompt
                        val wikiResult = MinecraftWikiEngine.search(query)
                        return@withContext AgentResult.ChatMessage(wikiResult)
                    }
                }

                // Fallback to text response
                val textReply = response.text?.trim()
                if (!textReply.isNullOrBlank()) {
                    // Check if model emitted JSON command format as text fallback
                    val parsedFallback = tryParseJsonCommand(textReply)
                    if (parsedFallback != null) {
                        if (!isOperator && parsedFallback is AgentResult.ExecuteCommand) {
                            return@withContext AgentResult.ChatMessage("Only operator '${config.authorizedOperatorPlayer}' can execute server commands.")
                        }
                        return@withContext parsedFallback
                    }
                    return@withContext AgentResult.ChatMessage(textReply)
                }

                AgentResult.ChatMessage("Action processed.")
            } catch (e: Exception) {
                Log.e(tag, "Google GenAI generation failed: ${e.message}", e)
                if (modelName.contains("gemma", ignoreCase = true)) {
                    return@withContext fallbackTextPrompt(player, prompt, apiKey, modelName, systemInstructionText, isOperator, config.authorizedOperatorPlayer)
                }
                AgentResult.Error("AI Generation error: ${e.localizedMessage ?: e.message}")
            }
        }

    private suspend fun fallbackTextPrompt(
        player: String,
        prompt: String,
        apiKey: String,
        modelName: String,
        systemInstruction: String,
        isOperator: Boolean,
        operatorName: String
    ): AgentResult = withContext(Dispatchers.IO) {
        try {
            val textModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                systemInstruction = content {
                    text("$systemInstruction If a command is needed and player is authorized operator, respond in JSON: {\"command\": \"...\", \"explanation\": \"...\"}")
                }
            )
            val response = textModel.generateContent("Player $player: $prompt")
            val reply = response.text?.trim() ?: "No response"
            val jsonCmd = tryParseJsonCommand(reply)
            if (jsonCmd is AgentResult.ExecuteCommand && !isOperator) {
                return@withContext AgentResult.ChatMessage("Only operator '$operatorName' can execute server commands.")
            }
            jsonCmd ?: AgentResult.ChatMessage(reply)
        } catch (e: Exception) {
            AgentResult.Error("Fallback AI error: ${e.message}")
        }
    }

    private fun tryParseJsonCommand(text: String): AgentResult? {
        return try {
            val clean = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            if (clean.startsWith("{") && clean.endsWith("}")) {
                val cmdRegex = Regex("\"command\"\\s*:\\s*\"([^\"]+)\"")
                val expRegex = Regex("\"explanation\"\\s*:\\s*\"([^\"]+)\"")
                val cmdMatch = cmdRegex.find(clean)
                if (cmdMatch != null) {
                    val cmd = cmdMatch.groupValues[1].trim()
                    val exp = expRegex.find(clean)?.groupValues?.get(1)?.trim()
                    return AgentResult.ExecuteCommand(cmd, exp)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun validateApiKey(config: AppConfig): Result<String> = withContext(Dispatchers.IO) {
        val key = config.apiKey.trim()
        if (key.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be blank"))
        }
        val modelName = config.effectiveModel.ifBlank { "gemini-2.0-flash" }

        try {
            val model = GenerativeModel(
                modelName = modelName,
                apiKey = key
            )
            val res = model.generateContent("Hello, reply with 'OK' only.")
            val reply = res.text?.trim() ?: "Connected"
            Result.success("Success: $reply")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
