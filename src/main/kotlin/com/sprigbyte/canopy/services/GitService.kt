package com.sprigbyte.canopy.services

import com.sprigbyte.canopy.config.CanopyConfigurationService
import com.sprigbyte.canopy.model.ApiResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import git4idea.branch.GitBrancher
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.CompletableFuture

/**
 * Service for Git operations including branch creation and management.
 * Uses IntelliJ's Git4Idea integration for reliable Git operations.
 */
@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {
    
    private val logger = thisLogger()
    
    companion object {
        fun getInstance(project: Project): GitService {
            return project.getService(GitService::class.java)
        }
    }
    
    /**
     * Creates a new branch from the specified ticket key.
     * Branch name format: {branchPrefix}{ticketKey} (e.g., "feature/PROJ-123")
     */
    fun createBranchFromTicket(ticketKey: String): ApiResult<String> {
        return try {
            val config = CanopyConfigurationService.getInstance().state
            val branchName = "${config.branchPrefix}$ticketKey"
            
            val repository = getGitRepository() ?: return ApiResult.Error("No Git repository found in project")
            
            // Check if branch already exists
            if (branchExists(repository, branchName)) {
                return ApiResult.Error("Branch '$branchName' already exists")
            }
            
            // Ensure we're on the target branch (main/master) before creating new branch
            val targetBranch = config.defaultTargetBranch
            if (!isOnBranch(repository, targetBranch)) {
                val checkoutResult = checkoutBranch(repository, targetBranch)
                if (checkoutResult is ApiResult.Error) {
                    return checkoutResult
                }
            }
            
            // Pull latest changes
            val pullResult = pullLatestChanges(repository)
            if (pullResult is ApiResult.Error) {
                logger.warn("Failed to pull latest changes: ${pullResult.message}")
                // Continue anyway - user might want to create branch from current state
            }
            
            // Create and checkout the new branch
            val brancher = GitBrancher.getInstance(project)
            val future = CompletableFuture<Boolean>()
            
            ApplicationManager.getApplication().invokeLater {
                brancher.checkoutNewBranch(branchName, listOf(repository))
                future.complete(true)
            }
            
            val success = future.get()
            if (success) {
                logger.info("Successfully created and checked out branch: $branchName")
                ApiResult.Success(branchName)
            } else {
                ApiResult.Error("Failed to create branch: $branchName")
            }
        } catch (e: Exception) {
            val error = "Error creating branch for ticket $ticketKey: ${e.message}"
            logger.error(error, e)
            ApiResult.Error(error, e)
        }
    }
    
    /**
     * Checks if a branch with the given ticket key exists
     */
    fun hasBranchForTicket(ticketKey: String): Boolean {
        val config = CanopyConfigurationService.getInstance().state
        val branchName = "${config.branchPrefix}$ticketKey"
        val repository = getGitRepository() ?: return false
        return branchExists(repository, branchName)
    }
    
    /**
     * Gets the branch name for a ticket (whether it exists or not)
     */
    fun getBranchNameForTicket(ticketKey: String): String {
        val config = CanopyConfigurationService.getInstance().state
        return "${config.branchPrefix}$ticketKey"
    }
    
    /**
     * Switches to an existing branch for the given ticket
     */
    fun checkoutBranchForTicket(ticketKey: String): ApiResult<String> {
        val branchName = getBranchNameForTicket(ticketKey)
        val repository = getGitRepository() ?: return ApiResult.Error("No Git repository found in project")
        
        if (!branchExists(repository, branchName)) {
            return ApiResult.Error("Branch '$branchName' does not exist")
        }
        
        return checkoutBranch(repository, branchName)
    }
    
    /**
     * Gets the current branch name
     */
    fun getCurrentBranch(): String? {
        val repository = getGitRepository() ?: return null
        return repository.currentBranch?.name
    }
    
    fun getGitRepository(): GitRepository? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val repositories = repositoryManager.repositories
        
        // Return the first repository, or null if no repositories
        return repositories.firstOrNull()
    }
    
    private fun branchExists(repository: GitRepository, branchName: String): Boolean {
        return repository.branches.findLocalBranch(branchName) != null ||
               repository.branches.findRemoteBranch("origin/$branchName") != null
    }
    
    private fun isOnBranch(repository: GitRepository, branchName: String): Boolean {
        return repository.currentBranch?.name == branchName
    }
    
    private fun checkoutBranch(repository: GitRepository, branchName: String): ApiResult<String> {
        return try {
            val brancher = GitBrancher.getInstance(project)
            val future = CompletableFuture<Boolean>()
            
            ApplicationManager.getApplication().invokeLater {
                // Check if it's a local branch
                val localBranch = repository.branches.findLocalBranch(branchName)
                if (localBranch != null) {
                    brancher.checkout(branchName, false, listOf(repository), null)
                    future.complete(true)
                } else {
                    // Try to checkout from remote
                    val remoteBranch = repository.branches.findRemoteBranch("origin/$branchName")
                    if (remoteBranch != null) {
                        brancher.checkoutNewBranchStartingFrom(branchName, "origin/$branchName", listOf(repository), null)
                        future.complete(true)
                    } else {
                        future.complete(false)
                    }
                }
            }
            
            val success = future.get()
            if (success) {
                ApiResult.Success(branchName)
            } else {
                ApiResult.Error("Failed to checkout branch: $branchName")
            }
        } catch (e: Exception) {
            ApiResult.Error("Error checking out branch $branchName: ${e.message}", e)
        }
    }
    
    private fun pullLatestChanges(repository: GitRepository): ApiResult<String> {
        return try {
            val git = Git.getInstance()
            val handler = GitLineHandler(project, repository.root, GitCommand.PULL)
            handler.setSilent(true)
            
            val result = git.runCommand(handler)
            if (result.success()) {
                ApiResult.Success("Successfully pulled latest changes")
            } else {
                ApiResult.Error("Failed to pull: ${result.errorOutputAsJoinedString}")
            }
        } catch (e: VcsException) {
            ApiResult.Error("VCS error during pull: ${e.message}", e)
        } catch (e: Exception) {
            ApiResult.Error("Error during pull: ${e.message}", e)
        }
    }
}