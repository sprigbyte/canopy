package com.sprigbyte.canopy.ui

import com.sprigbyte.canopy.config.CanopyConfigurationService
import com.sprigbyte.canopy.services.AzureDevOpsService
import com.sprigbyte.canopy.services.GitService
import com.sprigbyte.canopy.services.JiraService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.sprigbyte.canopy.model.ApiResult
import com.sprigbyte.canopy.model.JiraTicket
import com.sprigbyte.canopy.model.TicketDisplayInfo
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Main tool window for the Canopy plugin.
 * Shows JIRA tickets, allows branch creation, and PR creation.
 */
class CanopyToolWindow(private val project: Project) {
    
    private val logger = thisLogger()
    private val jiraService = JiraService.getInstance()
    private val gitService = GitService.getInstance(project)
    private val azureService = AzureDevOpsService.getInstance(project)
    
    // UI Components
    private val ticketTable = JBTable()
    private val ticketTableModel = TicketTableModel()
    private val refreshButton = JButton("Refresh Tickets")
    private val settingsButton = JButton("Settings")
    private val statusLabel = JLabel("Ready")
    
    // Data
    private var ticketDisplayInfos = mutableListOf<TicketDisplayInfo>()
    
    fun getContent(): JComponent {
        setupTable()
        setupButtons()
        
        val toolbar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(refreshButton)
            add(Box.createHorizontalStrut(5))
            add(settingsButton)
            add(Box.createHorizontalGlue())
            add(statusLabel)
        }
        
        val scrollPane = JBScrollPane(ticketTable).apply {
            preferredSize = Dimension(800, 400)
        }
        
        val mainPanel = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            border = JBUI.Borders.empty(10)
        }
        
        // Load tickets on startup if configured
        if (isConfigured()) {
            refreshTickets()
        } else {
            statusLabel.text = "Please configure JIRA and Azure DevOps settings"
        }
        
        return mainPanel
    }
    
    private fun setupTable() {
        ticketTable.model = ticketTableModel
        ticketTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        ticketTable.rowHeight = 25
        
        // Setup column widths
        ticketTable.columnModel.apply {
            getColumn(0).preferredWidth = 100  // Key
            getColumn(1).preferredWidth = 300  // Summary
            getColumn(2).preferredWidth = 80   // Status
            getColumn(3).preferredWidth = 100  // Branch
            getColumn(4).preferredWidth = 80   // PR
        }
        
        // Custom renderer for status column
        ticketTable.columnModel.getColumn(2).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): Component {
                val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                
                if (!isSelected && value is String) {
                    when {
                        value.contains("Done", true) || value.contains("Closed", true) -> background = JBUI.CurrentTheme.List.BACKGROUND
                        value.contains("Progress", true) || value.contains("Development", true) -> {
                            background = Color(173, 216, 230) // Light blue
                        }
                        value.contains("Open", true) || value.contains("To Do", true) -> {
                            background = Color(255, 255, 224) // Light yellow
                        }
                    }
                }
                
                return component
            }
        }
        
        // Double-click handler
        ticketTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if ((e.button == 1 && e.clickCount == 2) || e.button == 3) {
                    val selectedRow = ticketTable.selectedRow
                    if (selectedRow >= 0) {
                        showTicketActions(selectedRow)
                    }
                }
            }
        })
    }
    
    private fun setupButtons() {
        refreshButton.addActionListener { refreshTickets() }
        settingsButton.addActionListener { showSettings() }
    }
    
    private fun refreshTickets() {
        if (!isConfigured()) {
            Messages.showWarningDialog(
                project,
                "Please configure JIRA and Azure DevOps settings first.",
                "Configuration Required"
            )
            showSettings()
            return
        }
        
        statusLabel.text = "Loading tickets..."
        refreshButton.isEnabled = false
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading JIRA Tickets", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Fetching tickets from JIRA..."
                
                when (val result = jiraService.getMyAssignedTickets()) {
                    is ApiResult.Success -> {
                        val tickets = result.data
                        
                        ApplicationManager.getApplication().invokeLater {
                            indicator.text = "Processing ticket information..."
                            
                            ticketDisplayInfos.clear()
                            tickets.forEach { ticket ->
                                val hasBranch = gitService.hasBranchForTicket(ticket.key)
                                val branchName = if (hasBranch) gitService.getBranchNameForTicket(ticket.key) else null
                                
                                // Check for existing PR
                                var hasPR = false
                                var prUrl: String? = null
                                if (hasBranch && branchName != null) {
                                    when (val prResult = azureService.pullRequestExistsForBranch(branchName)) {
                                        is ApiResult.Success -> {
                                            val config = CanopyConfigurationService.getInstance().state
                                            val repoName = gitService.getGitRepository()?.project?.name
                                            if (repoName != null) {
                                                hasPR = prResult.data != null
                                                prUrl = "https://dev.azure.com/${config.azureOrganization}/${config.azureProject}/_git/${repoName}/pullrequest/${prResult.data?.pullRequestId}"
                                            }
                                            
                                        }
                                        is ApiResult.Error -> {
                                            logger.warn("Failed to check PR for ${ticket.key}: ${prResult.message}")
                                        }
                                    }
                                }
                                
                                ticketDisplayInfos.add(
                                    TicketDisplayInfo(
                                        ticket = ticket,
                                        hasBranch = hasBranch,
                                        branchName = branchName,
                                        hasPullRequest = hasPR,
                                        pullRequestUrl = prUrl
                                    )
                                )
                            }
                            
                            ticketTableModel.fireTableDataChanged()
                            statusLabel.text = "Loaded ${tickets.size} tickets"
                            refreshButton.isEnabled = true
                        }
                    }
                    is ApiResult.Error -> {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                "Failed to load tickets: ${result.message}",
                                "Error"
                            )
                            statusLabel.text = "Failed to load tickets"
                            refreshButton.isEnabled = true
                        }
                    }
                }
            }
        })
    }
    
    private fun showTicketActions(row: Int) {
        val ticketInfo = ticketDisplayInfos[row]
        val ticket = ticketInfo.ticket
        
        val branchName = gitService.getCurrentBranch()
        
        val actions = mutableListOf<String>()
        actions.add("View in JIRA")
        
        if (!ticketInfo.hasBranch) {
            actions.add("Create Branch")
        } else {
            if (ticketInfo.branchName != branchName) {
                actions.add("Switch to Branch")
            }
            if (!ticketInfo.hasPullRequest) {
                actions.add("Create Pull Request")
            } else {
                actions.add("View Pull Request")
            }
        }
        
        val choice = Messages.showChooseDialog(
            project,
            "Choose action for ${ticket.key}:",
            "Ticket Actions",
            Messages.getInformationIcon(),
            actions.toTypedArray(),
            actions[0]
        )
        
        if (choice >= 0) {
            when (actions[choice]) {
                "View in JIRA" -> openJiraTicket(ticket)
                "Create Branch" -> createBranchForTicket(ticket)
                "Switch to Branch" -> switchToBranch(ticket)
                "Create Pull Request" -> createPullRequestForTicket(ticket)
                "View Pull Request" -> openPullRequest(ticketInfo)
            }
        }
    }
    
    private fun createBranchForTicket(ticket: JiraTicket) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Creating Branch", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Creating branch for ${ticket.key}..."
                
                when (val result = gitService.createBranchFromTicket(ticket.key)) {
                    is ApiResult.Success -> {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showInfoMessage(
                                project,
                                "Successfully created and checked out branch: ${result.data}",
                                "Branch Created"
                            )
                            refreshTickets()
                        }
                    }
                    is ApiResult.Error -> {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                "Failed to create branch: ${result.message}",
                                "Error"
                            )
                        }
                    }
                }
            }
        })
    }
    
    private fun switchToBranch(ticket: JiraTicket) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Switching Branch", false) {
            override fun run(indicator: ProgressIndicator) {
                when (val result = gitService.checkoutBranchForTicket(ticket.key)) {
                    is ApiResult.Success -> {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showInfoMessage(
                                project,
                                "Switched to branch: ${result.data}",
                                "Branch Switched"
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                "Failed to switch branch: ${result.message}",
                                "Error"
                            )
                        }
                    }
                }
            }
        })
    }
    
    private fun createPullRequestForTicket(ticket: JiraTicket) {
        val branchName = gitService.getBranchNameForTicket(ticket.key)
        
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Creating Pull Request", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Creating pull request for ${ticket.key}..."
                
                when (val result = azureService.createPullRequestFromTicket(ticket, branchName)) {
                    is ApiResult.Success -> {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showInfoMessage(project, "Successfully created pull request #${result.data.pullRequestId}", "Pull Request Created")
                            refreshTickets()
                        }
                    }
                    is ApiResult.Error -> {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                "Failed to create pull request: ${result.message}",
                                "Error"
                            )
                        }
                    }
                }
            }
        })
    }
    
    private fun openJiraTicket(ticket: JiraTicket) {
        val config = CanopyConfigurationService.getInstance().state
        val url = "${config.jiraBaseUrl.trimEnd('/')}/browse/${ticket.key}"
        Desktop.getDesktop().browse(URI(url))
    }
    
    private fun openPullRequest(ticketInfo: TicketDisplayInfo) {
        ticketInfo.pullRequestUrl?.let { url ->
            Desktop.getDesktop().browse(URI(url))
        }
    }
    
    private fun showSettings() {
        val dialog = CanopySettingsDialog(project)
        if (dialog.showAndGet()) {
            refreshTickets()
        }
    }
    
    private fun isConfigured(): Boolean {
        val config = CanopyConfigurationService.getInstance()
        return config.isJiraConfigured() && config.isAzureDevOpsConfigured()
    }
    
    // Table model for displaying tickets
    private inner class TicketTableModel : AbstractTableModel() {
        private val columnNames = arrayOf("Key", "Summary", "Status", "Branch", "PR")
        
        override fun getRowCount(): Int = ticketDisplayInfos.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val ticketInfo = ticketDisplayInfos[rowIndex]
            val ticket = ticketInfo.ticket
            
            return when (columnIndex) {
                0 -> ticket.key
                1 -> ticket.fields.summary
                2 -> ticket.fields.status.name
                3 -> if (ticketInfo.hasBranch) "✓" else "-"
                4 -> if (ticketInfo.hasPullRequest) "✓" else "-"
                else -> ""
            }
        }
    }
}