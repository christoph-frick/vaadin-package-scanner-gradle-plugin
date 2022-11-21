/*
 * This Groovy source file was generated by the Gradle 'init' task.
 */
package net.ofnir.gradle.vaadinpackagescanner

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class VaadinPackageScannerPluginFunctionalTest extends Specification {

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
    id 'com.vaadin' version "${vaadinVersion}"
    id('net.ofnir.gradle.vaadin-package-scanner')
}
repositories {
    mavenCentral()
}
dependencies {
    implementation platform("com.vaadin:vaadin-bom:${vaadinVersion}")
    implementation "com.vaadin:vaadin-spring-boot-starter:${vaadinVersion}"
}
"""

        expect:
        !propsFile.exists()

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

        when:
        def props = new Properties()
        propsFile.withReader { it -> props.load(it) }

        then:
        props.containsKey('vaadin.whitelisted-packages')
        props.get('vaadin.whitelisted-packages') == expected

        where:
        vaadinVersion || expected
        "23.2.8"      || "com.vaadin.flow.data.renderer,com.vaadin.flow.router,com.vaadin.flow.spring.scopes,com.vaadin.flow.spring.security,com.vaadin.flow.theme.lumo,com.vaadin.flow.theme.material"
    }
}
