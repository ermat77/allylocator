# Documentation Check Rule

## Context7 MCP Documentation

When working with libraries, frameworks, or APIs, always check documentation using Context7 MCP before making assumptions or implementing solutions.

### Process:
1. Use `resolve-library-id` to find the correct library ID for the package/library in question
2. Use `query-docs` with the resolved library ID to retrieve up-to-date documentation and code examples
3. Reference the Context7 documentation when:
   - Using a library or framework for the first time
   - Implementing features that require library-specific APIs
   - Troubleshooting issues with library usage
   - Looking for best practices or code examples
   - Verifying correct usage patterns

### Guidelines:
- Always resolve the library ID first before querying documentation
- Be specific in queries to get the most relevant documentation
- Prefer Context7 documentation over general web searches when working with specific libraries
- Use the documentation to ensure code follows library best practices
