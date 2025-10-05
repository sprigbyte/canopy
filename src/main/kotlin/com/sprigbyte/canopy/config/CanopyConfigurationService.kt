package com.sprigbyte.canopy.config

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent configuration service for Canopy plugin that stores JIRA and Azure DevOps settings.
 * Uses IntelliJ's persistent state component to securely store configuration data.
 */
@State(
    name = "CanopyConfiguration",
    storages = [Storage("CanopyConfiguration.xml")]
)
@Service
class CanopyConfigurationService : PersistentStateComponent<CanopyConfigurationService.State> {
    
    private var state = State()
    
    data class State(
        // JIRA Configuration
        var jiraBaseUrl: String = "",
        var jiraUsername: String = "",
        var jiraApiToken: String = "",
        
        // Azure DevOps Configuration
        var azureOrganization: String = "",
        var azureProject: String = "",
        var azurePersonalAccessToken: String = "",
        
        // Git Configuration
        var defaultTargetBranch: String = "main",
        var branchPrefix: String = "feature/",
        
        // UI Settings
        var autoRefreshIntervalMinutes: Int = 15,
        var showCompletedTickets: Boolean = false,
        var maxTicketsToShow: Int = 50
    )
    
    companion object {
        fun getInstance(): CanopyConfigurationService {
            return service<CanopyConfigurationService>()
        }
    }
    
    override fun getState(): State {
        return state
    }
    
    override fun loadState(loadedState: State) {
        XmlSerializerUtil.copyBean(loadedState, state)
    }
    
    // Convenience methods for accessing configuration
    fun isJiraConfigured(): Boolean {
        return state.jiraBaseUrl.isNotBlank() && 
               state.jiraUsername.isNotBlank() && 
               state.jiraApiToken.isNotBlank()
    }
    
    fun isAzureDevOpsConfigured(): Boolean {
        return state.azureOrganization.isNotBlank() && 
               state.azureProject.isNotBlank() && 
               state.azurePersonalAccessToken.isNotBlank()
    }
    
    fun updateJiraConfiguration(baseUrl: String, username: String, apiToken: String) {
        state.jiraBaseUrl = baseUrl.trim()
        state.jiraUsername = username.trim()
        state.jiraApiToken = apiToken.trim()
    }
    
    fun updateAzureDevOpsConfiguration(organization: String, project: String, pat: String) {
        state.azureOrganization = organization.trim()
        state.azureProject = project.trim()
        state.azurePersonalAccessToken = pat.trim()
    }
    
    fun updateGitConfiguration(targetBranch: String, branchPrefix: String) {
        state.defaultTargetBranch = targetBranch.trim()
        state.branchPrefix = branchPrefix.trim()
    }
    
    fun updateUISettings(refreshInterval: Int, showCompleted: Boolean, maxTickets: Int) {
        state.autoRefreshIntervalMinutes = refreshInterval
        state.showCompletedTickets = showCompleted
        state.maxTicketsToShow = maxTickets
    }
}