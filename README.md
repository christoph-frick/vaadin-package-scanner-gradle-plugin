# Vaadin Package Scanner Gradle Plugin

**WORK IN PROGRESS**

Generate the setting `vaadin.whitelisted-packages` in
`src/main/resources/application.properties` from the `runtimeClasspath`
for Vaadin/Spring projects.

## Configuration

The default configuration aims to work out of the box with the current LTS version (23 as of this writing).

```groovy
vaadinScanPackages {
    // Gradle source set to search for deps; should represent the
    // effective runtime classpath
    sourceSet = 'main'
    // The full qualified class name, that Vaadin uses itself to load
    // deps (usually the one with `HandlesTypes`
    markerClassName = 'com.vaadin.base.devserver.startup.DevModeStartupListener'
    // Annotation on marker class, that contains the list of interface
    // to look for deps
    handlesTypesAnnotationClassName = 'javax.servlet.annotation.HandlesTypes'
    // Ignored packages, that never get added to the allow list;
    // By default `com.vaadin.flow.component` as it's always in included
    // by Vaadin
    alwaysBlockRegexp = /^com\.vaadin\.flow\.component($|\..*)/
    // File to create/append the allow-list too
    applicationProperties = "${projectDir}/src/main/resources/application.properties"
    // Key of the property inside the properties file, to set the
    // allow-list too
    vaadinProperty = 'vaadin.whitelisted-packages'
}
```

### Vaadin 23

Defaults will suffice.

### Vaadin 14

- Different `markerClassName`

```groovy
vaadinScanPackages {
    markerClassName = 'com.vaadin.flow.server.startup.DevModeInitializer'
}
```
