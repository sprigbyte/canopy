package com.sprigbyte.canopy.services

import com.sprigbyte.canopy.config.CanopyConfigurationService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.sprigbyte.canopy.model.ApiResult
import com.sprigbyte.canopy.model.JiraSearchResponse
import com.sprigbyte.canopy.model.JiraTicket
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with JIRA REST API.
 * Handles authentication, ticket retrieval, and JQL queries.
 */
@Service
class JiraService {
    
    private val logger = thisLogger()
    private val gson: Gson = GsonBuilder().create()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        fun getInstance(): JiraService {
            return ApplicationManager.getApplication().getService(JiraService::class.java)
        }
    }
    
    /**
     * Retrieves tickets assigned to the current user
     */
    fun getMyAssignedTickets(): ApiResult<List<JiraTicket>> {
        val config = CanopyConfigurationService.getInstance().state
        
        if (!CanopyConfigurationService.getInstance().isJiraConfigured()) {
            return ApiResult.Error("JIRA configuration is incomplete")
        }
        
        val jql = buildString {
            append("assignee = currentUser()")
            append(" AND type != Epic")
            if (!config.showCompletedTickets) {
                append(" AND statusCategory != Done")
            }
            append(" ORDER BY updated DESC")
        }
        
        return searchTickets(jql, config.maxTicketsToShow)
    }
    
    /**
     * Searches for tickets using JQL
     */
    fun searchTickets(jql: String, maxResults: Int = 50): ApiResult<List<JiraTicket>> {
        val config = CanopyConfigurationService.getInstance().state
        
        try {
            val baseUrl = config.jiraBaseUrl.trimEnd('/')
            val searchUrl = "$baseUrl/rest/api/3/search/jql"
            
            val requestBody = mapOf(
                "jql" to jql,
                "maxResults" to maxResults,
                "fields" to listOf("summary", "description", "status", "assignee", "reporter", "issuetype", "created", "updated"),
                "expand" to "names, schema"
            )
            
            val request = Request.Builder()
                .url(searchUrl)
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .header("Authorization", createBasicAuthHeader(config.jiraUsername, config.jiraApiToken))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
            
            logger.info("Searching JIRA tickets with JQL: $jql")
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = "Failed to search tickets: HTTP ${response.code} - ${response.message}"
                    logger.warn(error)
                    return ApiResult.Error(error)
                }
                
                val responseBody = response.body?.string() ?: ""
                val searchResponse = gson.fromJson(responseBody, JiraSearchResponse::class.java)
                
                logger.info("Found ${searchResponse.issues.size} tickets out of ${searchResponse.total} total")
                return ApiResult.Success(searchResponse.issues)
            }
        } catch (e: IOException) {
            val error = "Network error while searching tickets: ${e.message}"
            logger.error(error, e)
            return ApiResult.Error(error, e)
        } catch (e: Exception) {
            val error = "Unexpected error while searching tickets: ${e.message}"
            logger.error(error, e)
            return ApiResult.Error(error, e)
        }
    }
    
    /**
     * Tests the JIRA connection with current configuration
     */
    fun testConnection(): ApiResult<String> {
        val config = CanopyConfigurationService.getInstance().state
        
        try {
            val baseUrl = config.jiraBaseUrl.trimEnd('/')
            val myselfUrl = "$baseUrl/rest/api/3/myself"
            
            val request = Request.Builder()
                .url(myselfUrl)
                .get()
                .header("Authorization", createBasicAuthHeader(config.jiraUsername, config.jiraApiToken))
                .header("Accept", "application/json")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = "Authentication failed: HTTP ${response.code} - ${response.message}"
                    return ApiResult.Error(error)
                }
                
                return ApiResult.Success("Connection successful")
            }
        } catch (e: IOException) {
            val error = "Network error: ${e.message}"
            return ApiResult.Error(error, e)
        } catch (e: Exception) {
            val error = "Unexpected error: ${e.message}"
            return ApiResult.Error(error, e)
        }
    }
    
    private fun createBasicAuthHeader(username: String, password: String): String {
        val credentials = "$username:$password"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }
}