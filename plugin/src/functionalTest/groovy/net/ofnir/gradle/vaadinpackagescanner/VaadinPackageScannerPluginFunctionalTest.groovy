/*
 * This Groovy source file was generated by the Gradle 'init' task.
 */
package net.ofnir.gradle.vaadinpackagescanner

import spock.lang.Specification
import spock.lang.TempDir
import org.gradle.testkit.runner.GradleRunner

class VaadinPackageScannerPluginFunctionalTest extends Specification {

    public static final VAADIN_VERSION = "23.0.10"

    @TempDir
    private File projectDir

    private getBuildFile() {
        new File(projectDir, "build.gradle")
    }

    private getSettingsFile() {
        new File(projectDir, "settings.gradle")
    }

    private getPropsFile() {
        new File(projectDir, "src/main/resources/application.properties")
    }

    def "happy path"() {
        given:
        settingsFile << ""
        buildFile << """
plugins {
    id 'java'
    id 'com.vaadin' version "${VAADIN_VERSION}"
    id('net.ofnir.gradle.vaadin-package-scanner')
}
repositories {
    mavenCentral()
}
dependencies {
    implementation platform("com.vaadin:vaadin-bom:${VAADIN_VERSION}")
    implementation "com.vaadin:vaadin-spring-boot-starter:${VAADIN_VERSION}"
}
"""

        expect:
        ! propsFile.exists()

        when:
        def runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("vaadinScanPackages")
        runner.withProjectDir(projectDir)
        def result = runner.build()

        then:
        result.output.contains("Updated Vaadin package allow list")
        propsFile.exists()
        propsFile.text.contains('vaadin.whitelisted-packages')
    }
}