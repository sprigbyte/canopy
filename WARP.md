# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

Canopy is a JetBrains Rider plugin that bridges JIRA issue tracking with Azure DevOps version control. It provides a seamless workflow for developers to view JIRA tickets, create Git branches, and generate pull requests with rich ticket metadata.

## Build & Development Commands

### Core Development
```bash
# Build the plugin
./gradlew buildPlugin

# Run Rider with the plugin for testing
./gradlew runIde

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean
```

### Plugin Installation
```bash
# After building, the plugin ZIP will be in:
# build/distributions/canopy-*.zip
# Install via: Rider → File → Settings → Plugins → Install from Disk
```

## Architecture Overview

### Service Layer Architecture
The plugin uses IntelliJ's service architecture with three main service types:

- **Application Services**: Singleton services shared across all projects
  - `JiraService`: Handles JIRA REST API communication
  - `AzureDevOpsService`: Manages Azure DevOps pull requests
  - `CanopyConfigurationService`: Persistent configuration storage

- **Project Services**: Per-project instances
  - `GitService`: Git operations using Git4Idea integration

### Core Components

**Services (`src/main/kotlin/com/sprigbyte/canopy/services/`)**
- `JiraService`: JIRA API integration with JQL querying, ticket retrieval, and authentication
- `AzureDevOpsService`: Azure DevOps API integration for PR creation and management
- `GitService`: Git branch operations using IntelliJ's Git4Idea plugin
- All services use `ApiResult<T>` sealed class for error handling

**Configuration (`src/main/kotlin/com/sprigbyte/canopy/config/`)**
- `CanopyConfigurationService`: Persistent state management using IntelliJ's PersistentStateComponent
- Stores API tokens, URLs, and user preferences securely
- Configuration stored in `CanopyConfiguration.xml`

**Models (`src/main/kotlin/com/sprigbyte/canopy/model/`)**
- `JiraModels.kt`: Data classes for JIRA API responses (tickets, users, status, etc.)
- Includes Azure DevOps models for pull request management
- `ApiResult` sealed class for consistent error handling

**UI Layer (`src/main/kotlin/com/sprigbyte/canopy/ui/`)**
- `CanopyToolWindow`: Main UI component displaying ticket list and actions
- `CanopySettingsDialog`: Configuration dialog for API credentials
- `CanopyToolWindowFactory`: Factory for creating tool window instances

### Integration Points

**IntelliJ Platform Integration**
- Uses Git4Idea plugin for reliable Git operations
- Integrates with IntelliJ's VCS system for change detection
- Tool window appears in right sidebar alongside other IDE tools

**External API Dependencies**
- JIRA REST API v3 for ticket management
- Azure DevOps REST API for pull request operations
- OkHttp for HTTP client with proper timeout configuration

## Development Patterns

### Service Registration
Services are registered in `plugin.xml` using IntelliJ's dependency injection:
```xml
<applicationService serviceInterface="..." serviceImplementation="..."/>
<projectService serviceInterface="..." serviceImplementation="..."/>
```

### Error Handling
All service operations return `ApiResult<T>` sealed class:
- `ApiResult.Success<T>(data: T)`
- `ApiResult.Error(message: String, exception: Throwable?)`

### Async Operations
Git operations use `CompletableFuture` with IntelliJ's application threading:
```kotlin
ApplicationManager.getApplication().invokeLater {
    // UI thread operations
}
```

### Configuration Management
Uses IntelliJ's `@State` annotation for persistent configuration:
```kotlin
@State(name = "CanopyConfiguration", storages = [Storage("CanopyConfiguration.xml")])
```

## Key Dependencies

- **Kotlin**: JVM 21 target, version 2.1.0
- **IntelliJ Platform**: Version 2025.1.4.1 (IC - IntelliJ Community)
- **Git4Idea**: Bundled plugin for Git operations
- **OkHttp**: 4.12.0 for HTTP client
- **Gson**: 2.10.1 for JSON serialization

## Testing & Debugging

### Plugin Testing
```bash
# Run plugin in test IDE environment
./gradlew runIde

# Access debug logs in test environment:
# Help → Show Log in Explorer/Finder
```

### Debug Logging
Enable debug logging for the plugin:
```
com.sprigbyte.canopy:DEBUG
```

### Common Issues
- **API Authentication**: Verify API tokens have correct permissions
- **Git Operations**: Ensure clean working directory for branch operations  
- **Thread Safety**: Git operations must run on proper IntelliJ threads

## Workflow Integration

The plugin follows this typical workflow:
1. User views JIRA tickets in tool window
2. Double-click ticket → Create branch (uses configured prefix + ticket key)
3. User develops on the branch
4. Double-click ticket → Create pull request (auto-filled with ticket metadata)
5. PR includes ticket description, links, and formatted information

## Configuration Requirements

The plugin requires:
- JIRA Cloud/Server with API token access
- Azure DevOps with Personal Access Token (Code read/write, PR read/write scopes)
- Git repository in the project
- Proper branch naming configuration (prefix + target branch)