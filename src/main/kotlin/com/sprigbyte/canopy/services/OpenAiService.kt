package com.sprigbyte.canopy.services

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sprigbyte.canopy.config.CanopyConfigurationService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit


@Service(Service.Level.PROJECT)
class OpenAiService {
    private val gson: Gson = GsonBuilder().create()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        fun getInstance(project: Project): OpenAiService {
            return project.getService(OpenAiService::class.java)
        }
    }

    fun summarizeDiff(diff: String): String? {
        val config = CanopyConfigurationService.getInstance().state
        // Construct messages array
        val messages = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", "You are a helpful assistant that summarizes git diffs clearly and concisely.")
            })
            add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", "Summarize the following git diff:\n\n$diff")
            })
        }

        // Build the JSON body for the API request
        val jsonBody = JsonObject().apply {
            addProperty("model", "gpt-5")
            add("messages", messages)
        }

        val body = gson.toJson(jsonBody)
            .toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer ${config.openAiKey}")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }

            val responseBody = response.body?.string() ?: ""
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val choices = jsonResponse.getAsJsonArray("choices")
            val message = choices[0].asJsonObject.getAsJsonObject("message")
            return message.get("content").asString.trim()
        }
    }
}