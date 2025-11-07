# Hierarchical Project Preferences

## Overview

Hierarchical Project Preferences is a feature that allows nested projects to inherit preference values from their ancestor projects. This simplifies the management of preferences across multiple related projects.

## How It Works

### Project Nesting

A project A is considered nested within a project B if:
```
B.getLocation().isPrefixOf(A.getLocation())
```

For example:
```
/workspace
  /projectB (at /path/to/projectB)
    /projectA (at /path/to/projectB/projectA)
```

In this structure, projectA is nested within projectB.

### Preference Inheritance

When hierarchical project preferences are enabled:

1. Preferences are searched up the chain of nested projects
2. Values from deeper nested projects override values from ancestor projects
3. If a nested project doesn't have a preference file, it can still inherit from ancestors
4. All preference files remain unchanged - inheritance is read-only

### Example

Consider this project structure:
```
/workspace
  /projectRoot (has key1=rootValue, key2=rootValue)
    /projectMid (has key2=midValue, key3=midValue)
      /projectLeaf (has key3=leafValue)
```

When reading preferences from projectLeaf:
- `key1` will be `rootValue` (inherited from projectRoot)
- `key2` will be `midValue` (inherited from projectMid, which overrides projectRoot)
- `key3` will be `leafValue` (from projectLeaf itself)

## Enabling/Disabling the Feature

The feature is controlled by the preference:
```java
ResourcesPlugin.PREF_ENABLE_HIERARCHICAL_PROJECT_PREFERENCES
```

Default value: `true` (enabled)

To disable hierarchical preferences programmatically:
```java
Preferences node = Platform.getPreferencesService().getRootNode()
    .node(InstanceScope.SCOPE)
    .node(ResourcesPlugin.PI_RESOURCES);
node.putBoolean(ResourcesPlugin.PREF_ENABLE_HIERARCHICAL_PROJECT_PREFERENCES, false);
node.flush();
```

## Use Cases

### 1. Shared Team Settings

Apply common settings to all projects in a workspace or directory:
```
/myWorkspace
  /teamSettings (contains shared team preferences)
    /project1 (inherits team settings)
    /project2 (inherits team settings)
    /project3 (can override specific settings while inheriting others)
```

### 2. Modular Applications

In a modular application with multiple sub-projects:
```
/myApp
  /core (defines base preferences)
    /ui-module (inherits and may override)
    /api-module (inherits and may override)
    /impl-module (inherits and may override)
```

### 3. Configuration Hierarchies

Set up configuration hierarchies for different environments:
```
/config
  /base (common settings)
    /development (dev-specific overrides)
      /dev-project1
      /dev-project2
    /production (prod-specific overrides)
      /prod-project1
      /prod-project2
```

## Implementation Details

### ProjectNestingCache

The `ProjectNestingCache` class efficiently caches project nesting relationships:
- Cache is computed lazily on first access
- Cache is cleared when projects are deleted or moved
- Projects without accessible locations are automatically filtered out

### Performance Considerations

- Preference loading is performed only once per preference node (cached in `loadedNodes`)
- The nesting cache reduces the need to recompute project relationships
- Cache is cleared conservatively to ensure correctness

### API Compatibility

This feature is fully backward compatible:
- When disabled, behavior is identical to previous versions
- No changes to existing preference file formats
- Preference files are never modified by inheritance

## Testing

The implementation includes comprehensive tests covering:
- Simple nesting (2 levels)
- Multi-level nesting (3+ levels)
- Nested projects without preference files
- Inheritance through intermediate projects without preferences
- File immutability (inheritance doesn't modify files)
- Disabling the feature
- Complex nesting scenarios with multiple preferences

Each test includes ASCII art diagrams to illustrate the project structure.

## API

### New Constants

#### `ResourcesPlugin.PREF_ENABLE_HIERARCHICAL_PROJECT_PREFERENCES`
Preference key to enable/disable hierarchical project preferences.
- Type: `String`
- Value: `"enableHierarchicalProjectPreferences"`
- Since: 3.20

#### `ResourcesPlugin.DEFAULT_PREF_ENABLE_HIERARCHICAL_PROJECT_PREFERENCES`
Default value for hierarchical project preferences preference.
- Type: `boolean`
- Value: `true`
- Since: 3.20

### New Classes

#### `ProjectNestingCache` (internal)
Cache for project nesting relationships.
- Package: `org.eclipse.core.internal.resources`
- Since: 3.20

**Key Methods:**
- `getAncestorProjects(IProject, Workspace)` - Returns list of ancestor projects
- `clearCache()` - Clears the entire cache
- `clearCache(IProject)` - Clears cache for specific project

## Migration Guide

No migration is required. The feature is enabled by default and works transparently with existing projects.

To disable the feature workspace-wide, add this to your workspace preferences:
```
org.eclipse.core.resources/enableHierarchicalProjectPreferences=false
```

## Known Limitations

1. Only file system locations are considered for nesting - linked resources don't affect nesting relationships
2. Projects must be open and accessible to participate in the hierarchy
3. Circular nesting is not possible (by definition of `isPrefixOf`)

## Future Enhancements

Possible future improvements:
- UI to visualize project nesting relationships
- Preference to show inherited values differently in the preferences UI
- Support for excluding specific qualifiers from inheritance
- Performance optimizations for very large project hierarchies
