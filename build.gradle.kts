import com.github.spotbugs.snom.Effort

plugins {
	java
	checkstyle
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.github.spotbugs") version "6.5.13"
}

group = "net.yosing.sandbox"
version = "0.0.1-SNAPSHOT"
description = "sandbox API"

// SpotBugs 本体と注釈（@SuppressFBWarnings）のバージョンを揃える
val spotbugsVersion = "4.10.5"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// AWS SDK for Java 2.x（モジュールのバージョンは BOM で揃える）
	implementation(platform("software.amazon.awssdk:bom:2.57.8"))
	implementation("software.amazon.awssdk:dynamodb")
	// SpotBugs の検出を個別に外すとき（@SuppressFBWarnings）に使う。実行時には要らない
	compileOnly("com.github.spotbugs:spotbugs-annotations:$spotbugsVersion")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	// DynamoDB Local をコンテナで動かす（Docker が要る）
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers")
	// パッケージの依存の決まり（オニオンアーキテクチャ）の確認
	testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	// SpotBugs のセキュリティ向けの検出（弱い乱数・暗号やハッシュの誤用等）
	spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// 静的チェック（./gradlew check・build で動く。1件でも検出したら失敗する）
checkstyle {
	toolVersion = "14.3.0"
	maxWarnings = 0
}

spotbugs {
	toolVersion = spotbugsVersion
	effort = Effort.MAX
	excludeFilter = file("config/spotbugs/exclude.xml")
}

// テストのコードは SpotBugs の対象にしない（モック等で誤検出が多いため。Checkstyle は対象にする）
tasks.spotbugsTest {
	enabled = false
}

tasks.spotbugsMain {
	reports.create("html") {
		required = true
	}
}

// Dockerfile が読む jar の名前を固定する（build/libs/app.jar）
tasks.bootJar {
	archiveFileName = "app.jar"
}

// 実行できない jar（-plain.jar）は使わないため作らない
tasks.jar {
	enabled = false
}
