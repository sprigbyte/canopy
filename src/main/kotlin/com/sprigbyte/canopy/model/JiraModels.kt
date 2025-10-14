package com.sprigbyte.canopy.model

/**
 * Data classes representing JIRA API responses and ticket information.
 */

data class JiraTicket(
    val key: String,
    val fields: JiraFields
) {
    fun getTitle(): String = "[$key]: ${fields.summary}"
}

data class JiraFields(
    val summary: String,
    val status: JiraStatus,
    val assignee: JiraUser?,
    val reporter: JiraUser?,
    val issuetype: JiraIssueType,
    val created: String,
    val updated: String
)

data class JiraStatus(
    val name: String,
    val statusCategory: JiraStatusCategory?
)

data class JiraStatusCategory(
    val key: String,
    val colorName: String,
    val name: String
)

data class JiraUser(
    val displayName: String,
    val emailAddress: String?,
    val accountId: String
)

data class JiraIssueType(
    val name: String,
    val iconUrl: String?
)

data class JiraSearchResponse(
    val issues: List<JiraTicket>,
    val total: Int,
    val startAt: Int,
    val maxResults: Int
)

data class JiraTransition(
    val id: String,
    val name: String
)

data class JiraTransitionsResponse(
    val transitions: List<JiraTransition>
)

data class JiraTransitionRequest(
    val id: String
)

data class AtlassianDocumentFormatWrapper(
    val content: List<AtlassianDocumentFormatContent>,
    val type: String,
    val version: Int
)

data class AtlassianDocumentFormatContent(
    val type: String,
    val content: List<AtlassianDocumentFormatContent>?,
    val text: String?,
    val marks: List<AtlassianDocumentFormatContentMark>?
)

data class AtlassianDocumentFormatContentMark(
    val type: String,
    val attrs: Map<String, String>
)

/**
 * Represents the current state of a ticket for UI display
 */
data class TicketDisplayInfo(
    val ticket: JiraTicket,
    val hasBranch: Boolean = false,
    val branchName: String? = null,
    val hasPullRequest: Boolean = false,
    val pullRequestUrl: String? = null
)

/**
 * Azure DevOps Pull Request models
 */
data class AzurePullRequest(
    val pullRequestId: Int,
    val title: String,
    val description: String,
    val sourceRefName: String,
    val targetRefName: String,
    val status: String,
)

data class CreatePullRequestRequest(
    val sourceRefName: String,
    val targetRefName: String,
    val title: String,
    val description: String
)

/**
 * Result wrapper for API operations
 */
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : ApiResult<Nothing>()
}