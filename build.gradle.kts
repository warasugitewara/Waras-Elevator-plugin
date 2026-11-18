plugins {
    id("java")
}

group = "com.warasugitewara"
version = "1.1.0"

repositories {
    mavenCentral {
        // Paper API は PaperMC リポジトリにしか無い。Maven Central に問い合わせると
        // 通常は404で次のリポジトリへ進むが、429(レート制限)等が返るとビルドが失敗するため除外する。
        content {
            excludeGroupByRegex("io\\.papermc(\\..*)?")
        }
    }
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
