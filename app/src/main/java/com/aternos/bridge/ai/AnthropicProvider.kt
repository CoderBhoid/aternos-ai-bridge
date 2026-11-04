package com.aternos.bridge.ai

import android.util.Log
import com.aternos.bridge.data.AppConfig
import com.aternos.bridge.wiki.MinecraftWikiEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Anthropic Claude API provider supporting Claude 3.7 Sonnet, 3.5 Sonnet, Haiku with tool calling.
 */
class AnthropicProvider : AiProvider {

    private val tag = "AnthropicProvider"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun processPrompt(player: String, prompt: String, config: AppConfig): AgentResult =
        withContext(Dispatchers.IO) {
            val apiKey = config.apiKey.trim()
            val baseUrl = config.effectiveBaseUrl.removeSuffix("/")
            val modelName = config.effectiveModel.ifBlank { "claude-3-5-haiku-20241022" }

            val isOperator = !config.enforceOperatorOnlyCommands ||
                    player.equals(config.authorizedOperatorPlayer.trim(), ignoreCase = true)

            val operatorNotice = if (isOperator) {
                "Player '$player' IS the authorized server operator with full OP command privileges. If they ask for an action (give items, set time/weather, teleport, summon, or 'slime/kill' someone), call 'execute_minecraft_command'."
            } else {
                "Player '$player' is a non-operator player. ONLY operator '${config.authorizedOperatorPlayer}' can execute server commands. If '$player' asks to run commands or give items, DO NOT call execute_minecraft_command. Instead, answer with funny Gen Z refusal or Minecraft Wiki advice in chat."
            }

            val systemInstruction = """
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
                - If you need to lookup wiki information, recipes, or syntax, call 'search_minecraft_wiki'.
            """.trimIndent()

            try {
                val rootJson = JSONObject().apply {
                    put("model", modelName)
                    put("max_tokens", 300)
                    put("system", systemInstruction)

                    val messagesArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Player $player says: $prompt")
                        })
                    }
                    put("messages", messagesArray)

                    // Anthropic tools schema
                    val toolsArray = JSONArray().apply {
                        // 1. Command execution tool
                        val cmdTool = JSONObject().apply {
                            put("name", "execute_minecraft_command")
                            put("description", "Execute an OP command on the Minecraft server.")
                            val inputSchemaObj = JSONObject().apply {
                                put("type", "object")
                                val props = JSONObject().apply {
                                    put("command", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "The Minecraft command without leading slash")
                                    })
                                    put("explanation", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "Short in-game broadcast message explaining action")
                                    })
                                }
                                put("properties", props)
                                put("required", JSONArray().put("command"))
                            }
                            put("input_schema", inputSchemaObj)
                        }
                        put(cmdTool)

                        // 2. Wiki search tool
                        val wikiTool = JSONObject().apply {
                            put("name", "search_minecraft_wiki")
                            put("description", "Search Minecraft wiki for crafting recipes, mob stats, or ore levels.")
                            val inputSchemaObj = JSONObject().apply {
                                put("type", "object")
                                val props = JSONObject().apply {
                                    put("query", JSONObject().apply {
                                        put("type", "string")
                                        put("description", "Search keywords or item name")
                                    })
                                }
                                put("properties", props)
                                put("required", JSONArray().put("query"))
                            }
                            put("input_schema", inputSchemaObj)
                        }
                        put(wikiTool)
                    }
                    put("tools", toolsArray)
                }

                val request = Request.Builder()
                    .url("$baseUrl/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(rootJson.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: "HTTP ${response.code}"
                    return@withContext AgentResult.Error("Claude API error ${response.code}: $err")
                }

                val responseJson = JSONObject(response.body?.string() ?: "")
                val contentArray = responseJson.optJSONArray("content")

                if (contentArray != null) {
                    var plainText = ""
                    for (i in 0 until contentArray.length()) {
                        val block = contentArray.getJSONObject(i)
                        val type = block.optString("type")
                        if (type == "tool_use") {
                            val funcName = block.optString("name")
                            val inputObj = block.optJSONObject("input")
                            if (inputObj != null) {
                                if (funcName == "execute_minecraft_command") {
                                    if (!isOperator) {
                                        return@withContext AgentResult.ChatMessage("Only operator '${config.authorizedOperatorPlayer}' can execute server commands.")
                                    }
                                    val command = inputObj.optString("command", "").trim()
                                    val explanation = inputObj.optString("explanation", "").trim().ifBlank { null }
                                    if (command.isNotBlank()) {
                                        return@withContext AgentResult.ExecuteCommand(command, explanation)
                                    }
                                } else if (funcName == "search_minecraft_wiki") {
                                    val query = inputObj.optString("query", prompt).trim()
                                    val wikiResult = MinecraftWikiEngine.search(query)
                                    return@withContext AgentResult.ChatMessage(wikiResult)
                                }
                            }
                        } else if (type == "text") {
                            plainText += block.optString("text", "")
                        }
                    }

                    if (plainText.isNotBlank()) {
                        return@withContext AgentResult.ChatMessage(plainText.trim())
                    }
                }

                AgentResult.ChatMessage("Action processed.")
            } catch (e: Exception) {
                Log.e(tag, "Anthropic request error", e)
                AgentResult.Error("Claude error: ${e.message}")
            }
        }

    override suspend fun validateApiKey(config: AppConfig): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = config.apiKey.trim()
        val baseUrl = config.effectiveBaseUrl.removeSuffix("/")
        val modelName = config.effectiveModel.ifBlank { "claude-3-5-haiku-20241022" }

        try {
            val rootJson = JSONObject().apply {
                put("model", modelName)
                put("max_tokens", 10)
                val messages = JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
                put("messages", messages)
            }

            val request = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(rootJson.toString().toRequestBody(jsonMediaType))
                .build()

            val res = client.newCall(request).execute()
            if (res.isSuccessful) {
                Result.success("Anthropic connected (HTTP ${res.code})")
            } else {
                val err = res.body?.string() ?: "HTTP ${res.code}"
                Result.failure(Exception("HTTP ${res.code}: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
