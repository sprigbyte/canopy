# Canopy - JIRA to Azure DevOps Bridge Plugin

A comprehensive JetBrains Rider plugin that streamlines the development workflow between JIRA issue tracking and Azure DevOps version control.

## Features

### 🎫 JIRA Integration
- **List Assigned Tickets**: View all JIRA tickets assigned to you with real-time status updates
- **Ticket Details**: See ticket summary, status, and other key information
- **Direct Links**: Open tickets directly in JIRA from the plugin

### 🌿 Git Branch Management
- **Auto Branch Creation**: Create properly named Git branches from ticket IDs
- **Branch Switching**: Easily switch between existing ticket branches
- **Smart Naming**: Configurable branch prefixes (e.g., `feature/PROJ-123`)

### 🔄 Azure DevOps Integration
- **Pull Request Creation**: Generate pull requests with rich ticket information
- **Auto-filled Descriptions**: PR descriptions include ticket details, links, and metadata
- **PR Tracking**: See which tickets already have associated pull requests

### ⚙️ Persistent Configuration
- **Secure Storage**: API keys and settings are stored securely using IntelliJ's persistent state
- **Connection Testing**: Test JIRA and Azure DevOps connections before saving
- **Flexible Settings**: Configurable branch prefixes, target branches, and UI preferences

## Installation

### From Source
1. Clone this repository
2. Build the plugin: `./gradlew buildPlugin`
3. Install the generated ZIP file from `build/distributions/` in Rider:
   - Go to `File → Settings → Plugins`
   - Click gear icon and select "Install Plugin from Disk..."
   - Select the ZIP file and restart Rider

## Configuration

### Prerequisites
- **JIRA**: Access to JIRA Cloud/Server with API token
- **Azure DevOps**: Access to Azure DevOps with Personal Access Token
- **Git**: Git repository in your project

### Setup Steps
1. Open the Canopy tool window from the right sidebar or via `Tools → Canopy → Open Canopy Tool Window`
2. Click "Settings" to configure your connections
3. Fill in the required fields:

#### JIRA Configuration
- **Base URL**: Your JIRA instance URL (e.g., `https://your-company.atlassian.net`)
- **Username/Email**: Your JIRA username or email address
- **API Token**: [Generate from Atlassian Account Settings](https://id.atlassian.com/manage-profile/security/api-tokens)

#### Azure DevOps Configuration
- **Organization**: Your Azure DevOps organization name
- **Project**: The project name in Azure DevOps
- **Repository**: The repository name where PRs will be created
- **Personal Access Token**: Generate from Azure DevOps → User Settings → Personal Access Tokens
  - Required scopes: `Code (read & write)`

#### Git Configuration
- **Default Target Branch**: Branch to create new branches from (usually `main` or `master`)
- **Branch Prefix**: Prefix for created branches (e.g., `feature/`, `bugfix/`)

4. Use "Test Connection" buttons to verify your settings
5. Click "Save" to store your configuration

## Usage

### Basic Workflow
1. **View Tickets**: The tool window shows all JIRA tickets assigned to you
2. **Create Branch**: Double-click a ticket and select "Create Branch"
3. **Work on Code**: Make your changes in the newly created branch
4. **Create PR**: Double-click the ticket again and select "Create Pull Request"

### Tool Window Features
- **Ticket List**: Shows key, summary, status, and branch/PR status
- **Status Colors**: Visual indicators for different ticket statuses
- **Quick Actions**: Double-click for context menu with available actions
- **Refresh**: Manual refresh button to get latest ticket updates

### Available Actions per Ticket
- **View in JIRA**: Opens the ticket in your browser
- **Create Branch**: Creates and checks out a new branch (if none exists)
- **Switch to Branch**: Checks out existing branch (if branch exists)
- **Create Pull Request**: Creates Azure DevOps PR with ticket details (if branch exists)
- **View Pull Request**: Opens existing PR in browser (if PR exists)

## Development

### Project Structure
```
src/main/kotlin/com/sprigbyte/canopy/
├── actions/           # Plugin actions
├── config/           # Persistent configuration
├── model/            # Data models for JIRA/Azure APIs
├── services/         # Core services (JIRA, Azure DevOps, Git)
└── ui/               # User interface components
```

### Building and Testing
```bash
# Build the plugin
./gradlew buildPlugin

# Run Rider with the plugin
./gradlew runIde

# Run tests (when available)
./gradlew test
```

## API References
- [JIRA REST API v3](https://developer.atlassian.com/cloud/jira/platform/rest/v3/)
- [Azure DevOps REST API](https://docs.microsoft.com/en-us/rest/api/azure/devops/)
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)

## Troubleshooting

### Common Issues
1. **Authentication Failed**
   - Verify API tokens have correct permissions
   - Check URLs don't have trailing slashes
   - Ensure tokens haven't expired

2. **Branch Creation Fails**
   - Ensure you have a Git repository in your project
   - Check you have uncommitted changes that need to be stashed
   - Verify target branch exists and is accessible

3. **PR Creation Fails**
   - Ensure the source branch exists and has been pushed to remote
   - Verify Azure DevOps PAT has Pull Request write permissions
   - Check repository name matches exactly

### Debug Logging
Enable debug logging in Rider's log configuration:
```
com.sprigbyte.canopy:DEBUG
```

## License
This project is licensed under the MIT License.

## Contributing
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/new-feature`
3. Make your changes and add tests
4. Submit a pull request

---

*Developed to bridge the gap between JIRA issue tracking and Azure DevOps version control for seamless development workflows.*