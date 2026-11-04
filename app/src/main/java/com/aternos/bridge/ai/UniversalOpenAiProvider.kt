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
 * Universal OpenAI-compatible API client supporting OpenAI, OpenRouter, Groq, DeepSeek,
 * xAI Grok, Mistral, NVIDIA NIM, Together, Fireworks, Cerebras, Ollama, and Custom endpoints.
 */
class UniversalOpenAiProvider : AiProvider {

    private val tag = "UniversalOpenAiProvider"

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
            val modelName = config.effectiveModel

            val isOperator = !config.enforceOperatorOnlyCommands ||
                    player.equals(config.authorizedOperatorPlayer.trim(), ignoreCase = true)

            val operatorNotice = if (isOperator) {
                "Player '$player' IS the authorized server operator with full OP command execution privileges. If they request an action (give items, set time/weather, teleport, summon, or 'slime/kill' someone), call 'execute_minecraft_command'."
            } else {
                "Player '$player' is a regular player. ONLY operator '${config.authorizedOperatorPlayer}' can execute server commands. If '$player' asks to run commands or spawn items, DO NOT call execute_minecraft_command. Instead, answer with funny Gen Z refusal or Minecraft Wiki advice in chat."
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
                // Build JSON payload with tools
                val rootJson = JSONObject().apply {
                    put("model", modelName)
                    put("temperature", 0.3)
                    put("max_tokens", 300)

                    val messagesArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemInstruction)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Player $player: $prompt")
                        })
                    }
                    put("messages", messagesArray)

                    // Add function calling tools
                    val toolsArray = JSONArray().apply {
                        // 1. Execute Command Tool
                        val cmdTool = JSONObject().apply {
                            put("type", "function")
                            val functionObj = JSONObject().apply {
                                put("name", "execute_minecraft_command")
                                put("description", "Execute an OP command on the Minecraft server. No leading slashes.")
                                val parametersObj = JSONObject().apply {
                                    put("type", "object")
                                    val propertiesObj = JSONObject().apply {
                                        put("command", JSONObject().apply {
                                            put("type", "string")
                                            put("description", "Minecraft command (e.g. give GamerBhoid diamond 5, time set day)")
                                        })
                                        put("explanation", JSONObject().apply {
                                            put("type", "string")
                                            put("description", "Short friendly message explaining what was done")
                                        })
                                    }
                                    put("properties", propertiesObj)
                                    put("required", JSONArray().put("command"))
                                }
                                put("parameters", parametersObj)
                            }
                            put("function", functionObj)
                        }
                        put(cmdTool)

                        // 2. Wiki Search Tool
                        val wikiTool = JSONObject().apply {
                            put("type", "function")
                            val functionObj = JSONObject().apply {
                                put("name", "search_minecraft_wiki")
                                put("description", "Search Minecraft wiki for 1.21 recipes, items, mob stats, or ore levels.")
                                val parametersObj = JSONObject().apply {
                                    put("type", "object")
                                    val propertiesObj = JSONObject().apply {
                                        put("query", JSONObject().apply {
                                            put("type", "string")
                                            put("description", "Search topic or item name")
                                        })
                                    }
                                    put("properties", propertiesObj)
                                    put("required", JSONArray().put("query"))
                                }
                                put("parameters", parametersObj)
                            }
                            put("function", functionObj)
                        }
                        put(wikiTool)
                    }
                    put("tools", toolsArray)
                    put("tool_choice", "auto")
                }

                val requestBuilder = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .post(rootJson.toString().toRequestBody(jsonMediaType))

                if (apiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }

                // Extra headers for OpenRouter / specific providers
                if (config.providerType == AiProviderType.OPENROUTER) {
                    requestBuilder.addHeader("HTTP-Referer", "https://github.com/aternos-ai-bridge")
                    requestBuilder.addHeader("X-Title", "Aternos AI Console Bridge")
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: "HTTP ${response.code}"
                    Log.e(tag, "Universal provider returned error HTTP ${response.code}: $errBody")
                    return@withContext AgentResult.Error("API returned error code ${response.code}: $errBody")
                }

                val responseBody = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBody)
                val choices = responseJson.optJSONArray("choices")

                if (choices != null && choices.length() > 0) {
                    val messageObj = choices.getJSONObject(0).optJSONObject("message")
                    if (messageObj != null) {
                        // 1. Check for tool_calls
                        val toolCalls = messageObj.optJSONArray("tool_calls")
                        if (toolCalls != null && toolCalls.length() > 0) {
                            val firstCall = toolCalls.getJSONObject(0)
                            val functionObj = firstCall.optJSONObject("function")
                            if (functionObj != null) {
                                val funcName = functionObj.optString("name")
                                val argsString = functionObj.optString("arguments", "{}")
                                val argsJson = JSONObject(argsString)

                                if (funcName == "execute_minecraft_command") {
                                    if (!isOperator) {
                                        return@withContext AgentResult.ChatMessage("Only operator '${config.authorizedOperatorPlayer}' can execute server commands.")
                                    }
                                    val command = argsJson.optString("command", "").trim()
                                    val explanation = argsJson.optString("explanation", "").trim().ifBlank { null }
                                    if (command.isNotBlank()) {
                                        return@withContext AgentResult.ExecuteCommand(command, explanation)
                                    }
                                } else if (funcName == "search_minecraft_wiki") {
                                    val query = argsJson.optString("query", prompt).trim()
                                    val wikiResult = MinecraftWikiEngine.search(query)
                                    return@withContext AgentResult.ChatMessage(wikiResult)
                                }
                            }
                        }

                        // 2. Check content text
                        val content = messageObj.optString("content", "").trim()
                        if (content.isNotBlank()) {
                            val parsedFallback = tryParseJsonCommand(content)
                            if (parsedFallback != null) {
                                if (parsedFallback is AgentResult.ExecuteCommand && !isOperator) {
                                    return@withContext AgentResult.ChatMessage("Only operator '${config.authorizedOperatorPlayer}' can execute server commands.")
                                }
                                return@withContext parsedFallback
                            }
                            return@withContext AgentResult.ChatMessage(content)
                        }
                    }
                }

                AgentResult.ChatMessage("Action completed.")
            } catch (e: Exception) {
                Log.e(tag, "Universal OpenAI call failed", e)
                AgentResult.Error("Connection error: ${e.localizedMessage ?: e.message}")
            }
        }

    private fun tryParseJsonCommand(text: String): AgentResult? {
        return try {
            val clean = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            if (clean.startsWith("{") && clean.endsWith("}")) {
                val json = JSONObject(clean)
                if (json.has("command")) {
                    val cmd = json.getString("command").trim()
                    val exp = json.optString("explanation", "").trim().ifBlank { null }
                    if (cmd.isNotBlank()) {
                        return AgentResult.ExecuteCommand(cmd, exp)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun validateApiKey(config: AppConfig): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = config.apiKey.trim()
        val baseUrl = config.effectiveBaseUrl.removeSuffix("/")
        val modelName = config.effectiveModel

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

            val requestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(rootJson.toString().toRequestBody(jsonMediaType))

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            val res = client.newCall(requestBuilder.build()).execute()
            if (res.isSuccessful) {
                Result.success("Connection verified (HTTP ${res.code})")
            } else {
                val body = res.body?.string() ?: "HTTP ${res.code}"
                Result.failure(Exception("HTTP ${res.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
