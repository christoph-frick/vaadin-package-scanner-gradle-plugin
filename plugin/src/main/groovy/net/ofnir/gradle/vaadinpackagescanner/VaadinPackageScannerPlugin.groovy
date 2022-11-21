package net.ofnir.gradle.vaadinpackagescanner

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.ClassInfoList
import io.github.classgraph.ScanResult
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction

@SuppressWarnings('unused')
class VaadinPackageScannerPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.tasks.create("vaadinScanPackages", VaadinScanPackagesTask)
    }
}

abstract class VaadinScanPackagesTask extends DefaultTask {

    @Input
    abstract Property<String> getSourceSetName()

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
        setGroup('Vaadin Flow Package Scanner')
        setDescription('Update the allow-list for package scanning')
        // props
        sourceSetName.convention('main')
        markerClassName.convention("com.vaadin.base.devserver.startup.DevModeStartupListener")
        handlesTypesAnnotationClassName.convention("javax.servlet.annotation.HandlesTypes")
        alwaysBlockRegexp.convention(/^com\.vaadin\.flow\.component($|\..*)/)
        applicationProperties.convention(project.layout.projectDirectory.file('src/main/resources/application.properties'))
        vaadinProperty.convention('vaadin.whitelisted-packages')
    }

    @TaskAction
    void execute() {
        logger.debug("Starting package scan")

        def sourceSetClassLoader = buildSourceSetClassLoader(sourceSetName.get())

        def markerClass = sourceSetClassLoader.loadClass(markerClassName.get())
        def handlesTypesClass = sourceSetClassLoader.loadClass(handlesTypesAnnotationClassName.get())

        logger.debug("Get dev relevant classes/annotation")
        def devClasses = markerClass.getAnnotation(handlesTypesClass).value()
        logger.debug("Found dev relevant classes/annotation: ${devClasses}}")

        logger.debug("Scanning for classes inheriting from or being annotated with dev relevant classes/annotations")
        def allPackages = new ClassGraphScanner(logger, sourceSetClassLoader.classLoader).scanAll(devClasses)
        logger.debug("All packages: ${allPackages}")

        def finalPackages = allPackages.findAll { !(it ==~ alwaysBlockRegexp.get()) }.sort()
        logger.debug("Allow list to write: ${finalPackages}")

        saveAllowList(applicationProperties.get().asFile, vaadinProperty.get(), finalPackages)

        logger.quiet("Updated Vaadin package allow-list (${vaadinProperty.get()}) in ${applicationProperties.get()}")
    }

    protected SourceSetClassLoader buildSourceSetClassLoader(String sourceSetName) {
        logger.debug("Creating class loader from source set ${sourceSetName}")
        new SourceSetClassLoader(logger, getSourceSet(sourceSetName))
    }

    protected getSourceSet(String sourceSetName) {
        logger.debug("Loading source sets")
        SourceSetContainer ssc = project.sourceSets
        logger.debug("Loading source set by name: ${sourceSetName}")
        return ssc.getByName(sourceSetName)
    }

    protected Properties loadOrCreateProperties(File propsFile) {
        Properties props
        if (propsFile.exists()) {
            logger.debug("Load application.properties from ${propsFile}")
            props = propsFile.withInputStream { is ->
                new Properties().tap {
                    load(is)
                }
            } as Properties
        } else {
            props = new Properties()
        }
        return props
    }

    protected void saveProperties(File propsFile, Properties props) {
        logger.debug("Save application.properties to ${propsFile}")
        propsFile.withOutputStream { os ->
            props.store(os, "Updated Vaadin allow-list")
        }
    }

    protected void saveAllowList(File propsFile, String key, Collection<String> packages) {
        def props = loadOrCreateProperties(propsFile)
        logger.trace("Setting properties")
        props.putAt(key, packages.join(','))
        saveProperties(propsFile, props)
    }

}

class SourceSetClassLoader {

    private final Logger logger

    final ClassLoader classLoader

    SourceSetClassLoader(Logger logger, SourceSet sourceSet) {
        this.logger = logger
        this.classLoader = buildClassLoader(sourceSet)
    }

    Class loadClass(String className) {
        try {
            logger.debug("Looking for class ${className}")
            def clazz = classLoader.loadClass(className)
            logger.debug("Found ${clazz.getName()}")
            return clazz
        } catch (ClassNotFoundException e) {
            logger.error("Could not find marker class `${className}`")
            throw e
        }
    }

    protected URL[] getRuntimeClassPaths(SourceSet ss) {
        ss.runtimeClasspath*.toURI()*.toURL()
    }

    protected URLClassLoader createClassLoader(URL[] cp) {
        new URLClassLoader(cp as URL[])
    }

    protected URLClassLoader buildClassLoader(SourceSet sourceSet) {
        def cp = getRuntimeClassPaths(sourceSet)
        logger.debug("Using classpath: $cp")
        return createClassLoader(cp)
    }

}

class ClassGraphScanner {

    private final Logger logger
    private final ClassLoader classLoader

    ClassGraphScanner(Logger logger, ClassLoader classLoader) {
        this.logger = logger
        this.classLoader = classLoader
    }

    Set<String> scanAll(Class[] devClasses) {
        return devClasses.collectMany(new TreeSet()) {
            logger.debug("Scanning for ${it.name}")
            def packages = scan(it)
            logger.debug("Packages for ${it.name}: ${packages}")
            return packages
        }
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
