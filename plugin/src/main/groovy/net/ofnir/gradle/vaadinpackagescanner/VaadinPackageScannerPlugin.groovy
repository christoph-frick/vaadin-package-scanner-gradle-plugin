package net.ofnir.gradle.vaadinpackagescanner

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.ClassInfoList
import io.github.classgraph.ScanResult
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction

class VaadinPackageScannerPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.tasks.create("vaadinScanPackages", VaadinScanPackagesTask)
    }
}

abstract class VaadinScanPackagesTask extends DefaultTask {

    @Input
    abstract Property<String> getSourceSet()

    @Input
    abstract Property<String> getMarkerClassName()

    @Input
    abstract Property<String> getHandlesTypesAnnotationClassName()

    @Input
    abstract Property<String> getAlwaysBlockRegexp()

    @Input
    abstract Property<String> getVaadinProperty()

    @OutputFile
    abstract RegularFileProperty getApplicationProperties()

    VaadinScanPackagesTask() {
        // setup
        setGroup('Vaadin Package Scanner')
        setDescription('Update the allow list for package scanning')
        // props
        sourceSet.convention('main')
        markerClassName.convention("com.vaadin.base.devserver.startup.DevModeStartupListener")
        handlesTypesAnnotationClassName.convention("javax.servlet.annotation.HandlesTypes")
        alwaysBlockRegexp.convention(/^com\.vaadin\.flow\.component($|\..*)/)
        applicationProperties.convention(project.layout.projectDirectory.file('src/main/resources/application.properties'))
        vaadinProperty.convention('vaadin.whitelisted-packages')
    }

    protected ClassLoader classLoader

    protected Class loadClass(String className) {
        try {
            return classLoader.loadClass(className)
        } catch (ClassNotFoundException e) {
            logger.error("Could not find marker class `${className}`")
            throw e
        }
    }

    @TaskAction
    void execute() {
        logger.debug("Starting package scan")
        logger.debug("Loading source sets")
        SourceSetContainer ssc = project.sourceSets
        logger.debug("Loading source set by name: ${sourceSet.get()}")
        SourceSet ss = ssc.getByName(sourceSet.get())
        def cp = ss.runtimeClasspath*.toURI()*.toURL()
        logger.debug("Using classpath: $cp")
        classLoader = new URLClassLoader(cp as URL[])

        logger.debug("Looking for marker class ${markerClassName.get()}")
        def devModeStartupListenerClass = loadClass(markerClassName.get())
        logger.debug("Found ${markerClassName.get()}")

        logger.debug("Looking for annotation class ${handlesTypesAnnotationClassName.get()}")
        def handlesTypesClass = loadClass(handlesTypesAnnotationClassName.get())
        logger.debug("Found ${handlesTypesAnnotationClassName.get()}")

        logger.debug("Scanning for dev relevant classes/annotation")
        def scanner = new Scanner(classLoader)
        def devClasses = devModeStartupListenerClass.getAnnotation(handlesTypesClass).value()
        logger.debug("Found dev relevant classes/annotation: ${devClasses}}")

        logger.debug("Finding classes inheriting from or being annotated")
        def allPackages = devClasses.collectMany(new TreeSet()) {
            logger.debug("Scanning for ${it.name}")
            def packages = scanner.scan(it)
            logger.debug("Packages for ${it.name}: ${packages}")
            return packages
        }
        logger.debug("All packages: ${allPackages}")

        def finalPackages = allPackages.findAll { !(it ==~ alwaysBlockRegexp.get()) }.sort()
        logger.debug("Allow list to write: ${finalPackages}")

        def propsFile = applicationProperties.get().asFile
        Properties props
        if (propsFile.exists()) {
            logger.debug("Load application.properties from ${applicationProperties.get()}")
            props = propsFile.withInputStream { is ->
                new Properties().tap{
                    load(is)
                }
            } as Properties
        } else {
            props = new Properties()
        }

        logger.trace("Setting properties")
        props.putAt(vaadinProperty.get(), finalPackages.join(','))

        logger.debug("Save application.properties to ${applicationProperties.get()}")
        propsFile.withOutputStream { os ->
            props.store(os, "Updated vaadin allow list")
        }

        logger.quiet("Updated Vaadin package allow list (${vaadinProperty.get()}) in ${applicationProperties.get()}")
    }

}

class Scanner {

    private final ClassLoader classLoader

    Scanner(ClassLoader classLoader) {
        this.classLoader = classLoader
    }

    Collection<String> scan(Class cls) {
        // scan result can not be reused
        try (ScanResult scanResult =
                new ClassGraph() // .verbose()
                        .overrideClassLoaders(classLoader)
                        .enableAnnotationInfo()
                        .enableClassInfo()
                        .scan()) {
            ClassInfoList cli
            if (cls.isAnnotation()) {
                cli = scanResult.getClassesWithAnnotation(cls.name)
            } else if (cls.isInterface()) {
                cli = scanResult.getClassesImplementing(cls.name)
            } else {
                cli = scanResult.getSubclasses(cls.name)
            }
            return cli.collect(ClassInfo::getPackageName)
        }
    }

}
