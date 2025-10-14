package com.sprigbyte.canopy.ui

import com.sprigbyte.canopy.config.CanopyConfigurationService
import com.sprigbyte.canopy.services.AzureDevOpsService
import com.sprigbyte.canopy.services.JiraService
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.sprigbyte.canopy.model.ApiResult
import javax.swing.*

/**
 * Settings dialog for configuring JIRA and Azure DevOps connections.
 */
class CanopySettingsDialog(private val project: Project) : DialogWrapper(project) {
    
    private val config = CanopyConfigurationService.getInstance()
    
    // JIRA fields
    private val jiraBaseUrlField = JBTextField().apply { text = config.state.jiraBaseUrl }
    private val jiraUsernameField = JBTextField().apply { text = config.state.jiraUsername }
    private val jiraApiTokenField = JBPasswordField().apply { text = config.state.jiraApiToken }
    private val testJiraButton = JButton("Test Connection")
    
    // Azure DevOps fields
    private val azureOrganizationField = JBTextField().apply { text = config.state.azureOrganization }
    private val azureProjectField = JBTextField().apply { text = config.state.azureProject }
    private val azurePatField = JBPasswordField().apply { text = config.state.azurePersonalAccessToken }
    private val testAzureButton = JButton("Test Connection")
    
    // Git configuration
    private val targetBranchField = JBTextField().apply { text = config.state.defaultTargetBranch }
    private val branchPrefixField = JBTextField().apply { text = config.state.branchPrefix }

    // OpenAI configuration
    private val openAiKeyField = JBPasswordField().apply { text = config.state.openAiKey }
    
    // UI Settings
    private val refreshIntervalField = JBTextField().apply { text = config.state.autoRefreshIntervalMinutes.toString() }
    private val showCompletedTicketsField = JBCheckBox("Show completed tickets").apply { isSelected = config.state.showCompletedTickets }
    private val maxTicketsField = JBTextField().apply { text = config.state.maxTicketsToShow.toString() }
    
    init {
        title = "Canopy Plugin Settings"
        setOKButtonText("Save")
        setupButtonActions()
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            // JIRA Configuration
            .addComponent(createSectionLabel("JIRA Configuration"))
            .addLabeledComponent("Base URL:", jiraBaseUrlField)
            .addTooltip("Your JIRA instance URL (e.g., https://your-company.atlassian.net)")
            .addLabeledComponent("Username/Email:", jiraUsernameField)
            .addTooltip("Your JIRA username or email address")
            .addLabeledComponent("API Token:", jiraApiTokenField)
            .addTooltip("Generate from Atlassian Account Settings > Security > API Tokens")
            .addComponent(createButtonPanel(testJiraButton))
            
            .addVerticalGap(15)
            .addComponent(JSeparator())
            .addVerticalGap(10)
            
            // Azure DevOps Configuration
            .addComponent(createSectionLabel("Azure DevOps Configuration"))
            .addLabeledComponent("Organization:", azureOrganizationField)
            .addTooltip("Your Azure DevOps organization name")
            .addLabeledComponent("Project:", azureProjectField)
            .addTooltip("The project name in Azure DevOps")
            .addLabeledComponent("Personal Access Token:", azurePatField)
            .addTooltip("Generate from Azure DevOps > User Settings > Personal Access Tokens")
            .addTooltip("Required scopes: Code (read & write)")
            .addComponent(createButtonPanel(testAzureButton))
            
            .addVerticalGap(15)
            .addComponent(JSeparator())
            .addVerticalGap(10)
            
            // Git Configuration
            .addComponent(createSectionLabel("Git Configuration"))
            .addLabeledComponent("Default Target Branch:", targetBranchField)
            .addTooltip("The branch to create new branches from and target for PRs (usually 'main' or 'master')")
            .addLabeledComponent("Branch Prefix:", branchPrefixField)
            .addTooltip("Prefix for created branches (e.g., 'feature/', 'bugfix/')")
            
            .addVerticalGap(15)
            .addComponent(JSeparator())
            .addVerticalGap(10)

            // OpenAI Configuration
            .addComponent(createSectionLabel("OpenAI Configuration"))
            .addLabeledComponent("OpenAI Key:", openAiKeyField)
            .addTooltip("Your OpenAI key")

            .addVerticalGap(15)
            .addComponent(JSeparator())
            .addVerticalGap(10)
            
            // UI Settings
            .addComponent(createSectionLabel("UI Settings"))
            .addLabeledComponent("Auto-refresh interval (minutes):", refreshIntervalField)
            .addTooltip("How often to automatically refresh the ticket list (0 to disable)")
            .addComponent(showCompletedTicketsField)
            .addLabeledComponent("Max tickets to show:", maxTicketsField)
            .addTooltip("Maximum number of tickets to display in the list")
            
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        panel.border = JBUI.Borders.empty(10)
        return panel
    }
    
    override fun doValidate(): ValidationInfo? {
        // JIRA validation
        if (jiraBaseUrlField.text.isBlank()) {
            return ValidationInfo("JIRA Base URL is required", jiraBaseUrlField)
        }
        if (!jiraBaseUrlField.text.startsWith("http")) {
            return ValidationInfo("JIRA Base URL must start with http:// or https://", jiraBaseUrlField)
        }
        if (jiraUsernameField.text.isBlank()) {
            return ValidationInfo("JIRA Username is required", jiraUsernameField)
        }
        if (jiraApiTokenField.password.isEmpty()) {
            return ValidationInfo("JIRA API Token is required", jiraApiTokenField)
        }
        
        // Azure DevOps validation
        if (azureOrganizationField.text.isBlank()) {
            return ValidationInfo("Azure DevOps Organization is required", azureOrganizationField)
        }
        if (azureProjectField.text.isBlank()) {
            return ValidationInfo("Azure DevOps Project is required", azureProjectField)
        }
        if (azurePatField.password.isEmpty()) {
            return ValidationInfo("Azure DevOps Personal Access Token is required", azurePatField)
        }
        
        // Git validation
        if (targetBranchField.text.isBlank()) {
            return ValidationInfo("Default Target Branch is required", targetBranchField)
        }
        if (branchPrefixField.text.isBlank()) {
            return ValidationInfo("Branch Prefix is required", branchPrefixField)
        }
        
        // UI Settings validation
        try {
            val refreshInterval = refreshIntervalField.text.toInt()
            if (refreshInterval < 0) {
                return ValidationInfo("Refresh interval must be 0 or positive", refreshIntervalField)
            }
        } catch (e: NumberFormatException) {
            return ValidationInfo("Refresh interval must be a valid number", refreshIntervalField)
        }
        
        try {
            val maxTickets = maxTicketsField.text.toInt()
            if (maxTickets <= 0) {
                return ValidationInfo("Max tickets must be greater than 0", maxTicketsField)
            }
        } catch (e: NumberFormatException) {
            return ValidationInfo("Max tickets must be a valid number", maxTicketsField)
        }
        
        return null
    }
    
    override fun doOKAction() {
        // Save configuration
        config.updateJiraConfiguration(
            jiraBaseUrlField.text.trim(),
            jiraUsernameField.text.trim(),
            String(jiraApiTokenField.password)
        )
        
        config.updateAzureDevOpsConfiguration(
            azureOrganizationField.text.trim(),
            azureProjectField.text.trim(),
            String(azurePatField.password)
        )
        
        config.updateGitConfiguration(
            targetBranchField.text.trim(),
            branchPrefixField.text.trim()
        )
        
        config.updateOpenAiConfiguration(
           String(openAiKeyField.password)
        )
        
        config.updateUISettings(
            refreshIntervalField.text.toInt(),
            showCompletedTicketsField.isSelected,
            maxTicketsField.text.toInt()
        )
        
        super.doOKAction()
    }
    
    private fun setupButtonActions() {
        testJiraButton.addActionListener {
            testJiraConnection()
        }
        
        testAzureButton.addActionListener {
            testAzureConnection()
        }
    }
    
    private fun testJiraConnection() {
        // Temporarily update configuration for testing
        val originalConfig = config.state.copy()
        config.updateJiraConfiguration(
            jiraBaseUrlField.text.trim(),
            jiraUsernameField.text.trim(),
            String(jiraApiTokenField.password)
        )
        
        testJiraButton.isEnabled = false
        testJiraButton.text = "Testing..."
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Testing JIRA Connection", false) {
            override fun run(indicator: ProgressIndicator) {
                val jiraService = JiraService.getInstance()
                val result = jiraService.testConnection()
                
                // Restore original configuration
                config.loadState(originalConfig)
                
                SwingUtilities.invokeLater {
                    testJiraButton.isEnabled = true
                    testJiraButton.text = "Test Connection"
                    
                    when (result) {
                        is ApiResult.Success -> {
                            Messages.showInfoMessage(
                                project,
                                "JIRA connection successful!",
                                "Connection Test"
                            )
                        }
                        is ApiResult.Error -> {
                            Messages.showErrorDialog(
                                project,
                                "JIRA connection failed: ${result.message}",
                                "Connection Test"
                            )
                        }
                    }
                }
            }
        })
    }
    
    private fun testAzureConnection() {
        // Temporarily update configuration for testing
        val originalConfig = config.state.copy()
        config.updateAzureDevOpsConfiguration(
            azureOrganizationField.text.trim(),
            azureProjectField.text.trim(),
            String(azurePatField.password)
        )
        
        testAzureButton.isEnabled = false
        testAzureButton.text = "Testing..."
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Testing Azure DevOps Connection", false) {
            override fun run(indicator: ProgressIndicator) {
                val azureService = AzureDevOpsService.getInstance(project)
                val result = azureService.testConnection()
                
                // Restore original configuration
                config.loadState(originalConfig)
                
                SwingUtilities.invokeLater {
                    testAzureButton.isEnabled = true
                    testAzureButton.text = "Test Connection"
                    
                    when (result) {
                        is ApiResult.Success -> {
                            Messages.showInfoMessage(
                                project,
                                "Azure DevOps connection successful!",
                                "Connection Test"
                            )
                        }
                        is ApiResult.Error -> {
                            Messages.showErrorDialog(
                                project,
                                "Azure DevOps connection failed: ${result.message}",
                                "Connection Test"
                            )
                        }
                    }
                }
            }
        })
    }
    
    private fun createSectionLabel(text: String): JComponent {
        return JLabel("<html><b>$text</b></html>").apply {
            border = JBUI.Borders.emptyBottom(5)
        }
    }
    
    private fun createButtonPanel(button: JButton): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(Box.createHorizontalGlue())
            add(button)
        }
    }
}