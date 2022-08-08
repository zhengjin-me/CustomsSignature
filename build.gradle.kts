import java.io.ByteArrayOutputStream

val profileName = System.getProperty("profile") ?: "development"
val profiles = profileName.split(",")

plugins {
    // global version
    val kotlinVersion: String by System.getProperties()
    val dokkaVersion: String by System.getProperties()
    val ktlintVersion: String by System.getProperties()
    val springBootVersion: String by System.getProperties()
    val springDependencyManagementVersion: String by System.getProperties()

    idea
    id("org.springframework.boot") version springBootVersion
    id("io.spring.dependency-management") version springDependencyManagementVersion
    id("org.jetbrains.dokka") version dokkaVersion
    id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
    kotlin("jvm") version kotlinVersion
    kotlin("kapt") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
}

val commonCoreVersion: String by project
val commonCorsVersion: String by project
val commonUtilsVersion: String by project
val commonCustomsBeansVersion: String by project

group = "me.zhengjin"
// 使用最新的tag名称作为版本号
// version = { ext["latestTagVersion"] }

/**
 * 源码JDK版本
 */
java.sourceCompatibility = JavaVersion.VERSION_1_8
/**
 * 编译后字节码可运行环境的版本
 */
java.targetCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    api(fileTree("dir" to "libs", "include" to listOf("*.jar")))
    implementation("me.zhengjin:common-core:$commonCoreVersion") {
        exclude("com.querydsl")
        exclude("org.springframework.boot", "spring-boot-starter-data-jpa")
    }
    implementation("me.zhengjin:common-cors:$commonCorsVersion")
    implementation("me.zhengjin:common-utils:$commonUtilsVersion")
    implementation("me.zhengjin:common-customs-beans:$commonCustomsBeansVersion") {
        exclude("com.querydsl")
        exclude("org.springframework.boot", "spring-boot-starter-data-jpa")
    }
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.data:spring-data-commons")
    // 国密证书支持
    implementation("org.bouncycastle:bcprov-jdk18on:1.71")
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))
    testCompileOnly("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}

tasks {
    register("getLatestTagVersion") {
        ext["latestTagVersionNumber"] = ByteArrayOutputStream().use {
            try {
                exec {
                    commandLine("git", "rev-list", "--tags", "--max-count=1")
                    standardOutput = it
                }
            } catch (e: Exception) {
                logger.error("Failed to get latest tag version number: [${e.message}]")
                return@use "unknown"
            }
            return@use it.toString().trim()
        }

        ext["latestTagVersion"] = ByteArrayOutputStream().use {
            try {
                exec {
                    commandLine("git", "describe", "--tags", ext["latestTagVersionNumber"])
                    standardOutput = it
                }
            } catch (e: Exception) {
                logger.error("Failed to get latest tag version: [${e.message}]")
                return@use "unknown"
            }
            val tagName = it.toString().trim()
            return@use Regex("^v?(?<version>\\d+\\.\\d+.\\d+(?:-SNAPSHOT|-snapshot)?)\$").matchEntire(tagName)?.groups?.get("version")?.value
                ?: throw IllegalStateException("Failed to get latest tag version, tagName: [$tagName]")
        }
        project.version = ext["latestTagVersion"]!!
        ext["isReleaseVersion"] = !version.toString().endsWith("SNAPSHOT", true)
        println("当前构建产物: [${project.group}:${project.name}:${project.version}]")
    }

    build {
        // 执行build之前 先获取版本号
        dependsOn("getLatestTagVersion")
    }

    jar {
        enabled = false
        classifier = ""
    }

    bootJar {
        enabled = true
        println("${project.name} profileName ==> $profileName")
        archiveBaseName.set(project.name)
    }

    /**
     * 定义那些注解修饰的类自动开放
     */
    allOpen {
        annotations(
            "javax.persistence.Entity",
            "javax.persistence.MappedSuperclass",
            "javax.persistence.Embeddable"
        )
    }

    test {
        useJUnitPlatform()
    }

    /**
     * kotlin编译
     */
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "1.8"
        }
    }

    /**
     * application动态参数处理
     */
    processResources {
        if (profiles.contains("production")) {
            exclude("*")
        }
        val tokens = mutableMapOf("profileName" to profileName)
        from(sourceSets["main"].resources.srcDirs) {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
            include("**/application.yml")
            filter {
                var context = it
                tokens.forEach { (k, v) ->
                    context = context.replace("@$k@", v)
                }
                context
            }
        }
    }
}
