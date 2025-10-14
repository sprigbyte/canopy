package com.sprigbyte.canopy.services

import com.sprigbyte.canopy.config.CanopyConfigurationService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.sprigbyte.canopy.model.ApiResult
import com.sprigbyte.canopy.model.AzurePullRequest
import com.sprigbyte.canopy.model.CreatePullRequestRequest
import com.sprigbyte.canopy.model.JiraTicket
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.get

/**
 * Service for interacting with Azure DevOps REST API.
 * Handles pull request creation and management.
 */
@Service(Service.Level.PROJECT)
class AzureDevOpsService(private val project: Project) {
    
    private val logger = thisLogger()
    private val gson: Gson = GsonBuilder().create()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gitService = GitService.getInstance(project)
    private val openAiService = OpenAiService.getInstance(project)
    
    companion object {
        fun getInstance(project: Project): AzureDevOpsService {
            return project.getService(AzureDevOpsService::class.java)
        }
    }
    
    /**
     * Creates a pull request from a JIRA ticket
     */
    fun createPullRequestFromTicket(ticket: JiraTicket, sourceBranch: String, targetBranch: String? = null): ApiResult<AzurePullRequest> {
        val config = CanopyConfigurationService.getInstance().state
        
        if (!CanopyConfigurationService.getInstance().isAzureDevOpsConfigured()) {
            return ApiResult.Error("Azure DevOps configuration is incomplete")
        }
        
        val actualTargetBranch = targetBranch ?: config.defaultTargetBranch
        val prTitle = ticket.getTitle()
        val prDescription = buildPullRequestDescription(ticket)
        
        return createPullRequest(
            title = prTitle,
            description = prDescription,
            sourceBranch = sourceBranch,
            targetBranch = actualTargetBranch
        )
    }
    
    /**
     * Creates a pull request with the specified details
     */
    fun createPullRequest(
        title: String,
        description: String,
        sourceBranch: String,
        targetBranch: String
    ): ApiResult<AzurePullRequest> {
        val config = CanopyConfigurationService.getInstance().state
        
        try {
            val repoName = gitService.getGitRepository()?.project?.name
            if (repoName == null) {
                val error = "No repository found"
                logger.error(error)
                return ApiResult.Error(error)
            }
            val prUrl = buildPullRequestUrl(config.azureOrganization, config.azureProject, repoName)
            
            val requestBody = CreatePullRequestRequest(
                sourceRefName = "refs/heads/$sourceBranch",
                targetRefName = "refs/heads/$targetBranch",
                title = title,
                description = description
            )
            
            val request = Request.Builder()
                .url(prUrl)
                .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
                .header("Authorization", createBasicAuthHeader("", config.azurePersonalAccessToken))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
            
            logger.info("Creating pull request: $sourceBranch -> $targetBranch")
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = "Failed to create pull request: HTTP ${response.code} - ${response.message}"
                    val responseBody = response.body?.string() ?: ""
                    logger.warn("$error\nResponse: $responseBody")
                    return ApiResult.Error("$error\nDetails: $responseBody")
                }
                
                val responseBody = response.body?.string() ?: ""
                val pullRequest = gson.fromJson(responseBody, AzurePullRequest::class.java)
                
                logger.info("Successfully created pull request #${pullRequest.pullRequestId}")
                return ApiResult.Success(pullRequest)
            }
        } catch (e: IOException) {
            val error = "Network error while creating pull request: ${e.message}"
            logger.error(error, e)
            return ApiResult.Error(error, e)
        } catch (e: Exception) {
            val error = "Unexpected error while creating pull request: ${e.message}"
            logger.error(error, e)
            return ApiResult.Error(error, e)
        }
    }
    
    /**
     * Gets existing pull requests for the repository
     */
    fun getPullRequests(status: String = "active"): ApiResult<List<AzurePullRequest>> {
        val config = CanopyConfigurationService.getInstance().state
        
        try {
            val repoName = gitService.getGitRepository()?.project?.name
            if (repoName == null) {
                val error = "No repository found"
                logger.error(error)
                return ApiResult.Error(error)
            }
            val prUrl = buildPullRequestUrl(config.azureOrganization, config.azureProject, repoName)
            val urlWithQuery = "$prUrl&searchCriteria.status=$status"
            
            val request = Request.Builder()
                .url(urlWithQuery)
                .get()
                .header("Authorization", createBasicAuthHeader("", config.azurePersonalAccessToken))
                .header("Accept", "application/json")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = "Failed to get pull requests: HTTP ${response.code} - ${response.message}"
                    logger.warn(error)
                    return ApiResult.Error(error)
                }
                
                val responseBody = response.body?.string() ?: ""
                val jsonObject = gson.fromJson(responseBody, Map::class.java)
                val value = jsonObject["value"] as? List<*> ?: emptyList<Any>()
                
                val pullRequests = value.mapNotNull { item ->
                    try {
                        gson.fromJson(gson.toJson(item), AzurePullRequest::class.java)
                    } catch (e: Exception) {
                        logger.warn("Failed to parse pull request: ${e.message}")
                        null
                    }
                }
                
                return ApiResult.Success(pullRequests)
            }
        } catch (e: IOException) {
            val error = "Network error while getting pull requests: ${e.message}"
            logger.error(error, e)
            return ApiResult.Error(error, e)
        } catch (e: Exception) {
            val error = "Unexpected error while getting pull requests: ${e.message}"
            logger.error(error, e)
            return ApiResult.Error(error, e)
        }
    }
    
    /**
     * Checks if a pull request exists for the given source branch
     */
    fun pullRequestExistsForBranch(branchName: String): ApiResult<AzurePullRequest?> {
        return when (val result = getPullRequests()) {
            is ApiResult.Success -> {
                val pr = result.data.find { pr ->
                    pr.sourceRefName == "refs/heads/$branchName"
                }
                ApiResult.Success(pr)
            }
            is ApiResult.Error -> result
        }
    }
    
    /**
     * Tests the Azure DevOps connection with current configuration
     */
    fun testConnection(): ApiResult<String> {
        val config = CanopyConfigurationService.getInstance().state
        
        try {
            val orgUrl = "https://dev.azure.com/${config.azureOrganization}/_apis/projects/${config.azureProject}?api-version=7.0"
            
            val request = Request.Builder()
                .url(orgUrl)
                .get()
                .header("Authorization", createBasicAuthHeader("", config.azurePersonalAccessToken))
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
    
    private fun buildPullRequestUrl(organization: String, project: String, repository: String): String {
        return "https://dev.azure.com/$organization/$project/_apis/git/repositories/$repository/pullrequests?api-version=7.0"
    }
    
    private fun buildPullRequestDescription(ticket: JiraTicket): String {
        return buildString {
            appendLine("## ${ticket.key}: ${ticket.fields.summary}")
            appendLine()
            
            val summary = openAiService.summarizeDiff(gitService.getGitDiff())
            summary?.let { aiSummary ->
                appendLine("### AI Summary")
                appendLine(aiSummary)
                appendLine()
            }

            appendLine("### Links")
            val config = CanopyConfigurationService.getInstance().state
            val ticketUrl = "${config.jiraBaseUrl.trimEnd('/')}/browse/${ticket.key}"
            appendLine("[View in JIRA]($ticketUrl)")
            appendLine()
            
            appendLine("---")
            appendLine("*This pull request was created automatically by [Canopy](https://plugins.jetbrains.com/plugin/28641-canopy)*")
        }
    }
    
    private fun createBasicAuthHeader(username: String, password: String): String {
        val credentials = "$username:$password"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }
}