package com.github.imflog.schema.registry.register

import com.github.imflog.schema.registry.REGISTRY_FAKE_PORT
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.*
import java.io.File

class RegisterSchemasTaskTest {
    lateinit var folderRule: TemporaryFolder

    lateinit var buildFile: File

    companion object {
        lateinit var wiremockServerItem: WireMockServer

        @BeforeClass
        @JvmStatic
        fun initClass() {
            wiremockServerItem = WireMockServer(
                    WireMockConfiguration
                            .wireMockConfig()
                            .port(REGISTRY_FAKE_PORT)
                            .notifier(ConsoleNotifier(true)))
            wiremockServerItem.start()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            wiremockServerItem.stop()
        }
    }

    @Before
    fun init() {
        folderRule = TemporaryFolder()
        // Register schema
        wiremockServerItem.stubFor(
                WireMock.post(WireMock
                        .urlMatching("/subjects/.*/versions"))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withBody("{\"id\": 1}")))
    }

    @After
    internal fun tearDown() {
        folderRule.delete()
    }

    @Test
    fun `RegisterSchemasTask should register schemas`() {
        folderRule.create()
        buildFile = folderRule.newFile("build.gradle")
        buildFile.writeText("""
            plugins {
                id 'java'
                id 'com.github.imflog.kafka-schema-registry-gradle-plugin'
            }

            schemaRegistry {
                url = 'http://localhost:$REGISTRY_FAKE_PORT/'
                register {
                    subject('testSubject1', 'avro/test.avsc')
                    subject('testSubject2', 'avro/other_test.avsc')
                }
            }
        """)

        folderRule.newFolder("avro")
        var testAvsc = folderRule.newFile("avro/other_test.avsc")
        val schemaTest = """
            {
                "type":"record",
                "name":"Blah",
                "fields":[
                    {
                        "name":"name",
                        "type":"string"
                    }
                ]
            }
        """.trimIndent()
        testAvsc.writeText(schemaTest)

        var testAvsc2 = folderRule.newFile("avro/test.avsc")
        testAvsc2.writeText(schemaTest)

        val result: BuildResult? = GradleRunner.create()
                .withGradleVersion("4.9")
                .withProjectDir(folderRule.root)
                .withArguments(REGISTER_SCHEMAS_TASK)
                .withPluginClasspath()
                .withDebug(true)
                .build()
        Assertions.assertThat(result?.task(":registerSchemasTask")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}