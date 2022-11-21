# Vaadin Package Scanner Gradle Plugin

**WORK IN PROGRESS**

Generate the setting `vaadin.whitelisted-packages` in
`src/main/resources/application.properties` for Vaadin Flow/Spring
projects.

In a Vaadin Flow project, on startup there is intensive search for
annotations, that influence the application.  E.g. Vaadin tries to find
CSS and JavaScript files to include into the build or looks for NPM
packages to download.

In a growing project, this is a growing overhead on each start,
especially while developing.  There even is a warning in the dev server,
when this takes longer than 10 seconds, which is an unacceptable long
time especially if you don't have hot-reload available.

To limit the locations where to look for the annotations, there are two
settings to allow or deny a list of package names.  Since adding or
creating new components is the rarest event, that needs changing this
lists, recreating the allow-list when needed, is the most efficient way
to deal with this.

And this is what is plugin for:

- Add the plugin in your `build.gradle` file, where Vaadin Flow is used.
- Optionally configure the plugin.
- Run `./gradlew vaadinScanPackages` to re-create the allow-list from
  the current class-path.
- When adding new deps or creating new components yourself, re-run the
  task to update the setting.


## Setup

Add the plugin in your `build.gradle`

```groovy
plugins {
    // ...
    id 'net.ofnir.gradle.vaadin-package-scanner' version "${vaadinPackageScannerPluginVersion}"
}
```


## Usage

Run:

```console
# ./gradlew vaadinScanPackages
...
> Task :vaadinScanPackages
Updated Vaadin package allow list (vaadin.whitelisted-packages) in .../src/main/resources/application.properties
```


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
