import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
	java
	checkstyle
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.github.spotbugs") version "6.5.13"
	// コードの整形（Eclipse のフォーマッター、Spring の流儀の設定）。./gradlew spotlessApply で整形し、check・build で崩れを確かめる
	id("com.diffplug.spotless") version "8.11.0"
}

group = "net.yosing.sandbox"
version = "0.0.1-SNAPSHOT"
description = "sandbox API"

// SpotBugs 本体と注釈（@SuppressFBWarnings）のバージョンを揃える
val spotbugsVersion = "4.10.5"

// Checkstyle 本体のバージョン（toolVersion と、Spring のルールと一緒に足す本体で揃える）
val checkstyleVersion = "14.3.0"

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
	implementation("software.amazon.awssdk:kms")
	// 仮認証 JWT（Spring Boot の BOM で管理されていないため、バージョンを書く）
	implementation("com.nimbusds:nimbus-jose-jwt:10.10")
	// TOTP のコードの計算（RFC 4226 / 6238。依存するライブラリは無い）
	implementation("com.eatthepath:java-otp:1.0.0")
	// パスワードのハッシュ（bcrypt）。Spring Security のうち、暗号の部品だけを使う（認証の仕組みは入れない）
	implementation("org.springframework.security:spring-security-crypto")
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
	// Checkstyle の Spring のルール（SpringChecks。Spring Java Format に付いている、Spring Boot の開発の決まり）
	//   依存を足すと、Gradle は既定の Checkstyle 本体（toolVersion）を足さなくなるため、本体も書く
	checkstyle("com.puppycrawl.tools:checkstyle:$checkstyleVersion")
	checkstyle("io.spring.javaformat:spring-javaformat-checkstyle:0.0.48")
}

// テストは2つに分ける
//   test（src/test）：単体テスト。Spring の Bean を作らず、Docker なしで速く動く（./gradlew test）
//   integrationTest（src/integrationTest）：結合テスト。Spring を起動し、Testcontainers の DynamoDB Local を使う（Docker が要る）
//   ./gradlew check・build は両方を流す
testing {
	suites {
		register<JvmTestSuite>("integrationTest") {
			// アプリのクラス・設定ファイル（jar は作らないため、project() ではなくコンパイル結果を直接使う）
			sources {
				compileClasspath += sourceSets.main.get().output
				runtimeClasspath += sourceSets.main.get().output
			}
			targets {
				all {
					testTask.configure {
						shouldRunAfter(tasks.test)
					}
				}
			}
		}
	}
}

// 結合テストは、単体テストと同じライブラリ（と、アプリの依存）を使う
configurations.named("integrationTestImplementation") {
	extendsFrom(configurations.testImplementation.get())
}
configurations.named("integrationTestRuntimeOnly") {
	extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.check {
	dependsOn(testing.suites.named("integrationTest"))
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// コードの整形（./gradlew check・build の spotlessCheck で崩れを確かめ、./gradlew spotlessApply で整形する）
//   設定は Spring Java Format の Eclipse の設定から、コメントの整形だけを切ったもの（config/formatter/eclipse-formatter.prefs）
spotless {
	java {
		eclipse().configFile("config/formatter/eclipse-formatter.prefs")
		// import の順は、Checkstyle の Spring のルール（SpringImportOrderCheck）と同じにする
		//   java → javax → その他 → org.springframework → static（グループの間は空行）
		importOrder("java", "javax", "", "org.springframework", "\\#")
	}
}

// 静的チェック（./gradlew check・build で動く。1件でも検出したら失敗する）
//   使うルールは config/checkstyle/checkstyle.xml（Spring のルール＋独自のルール）
checkstyle {
	toolVersion = checkstyleVersion
	maxWarnings = 0
}

spotbugs {
	toolVersion = spotbugsVersion
	effort = Effort.MAX
	excludeFilter = file("config/spotbugs/exclude.xml")
}

// テストのコード（単体・結合）は SpotBugs の対象にしない（モック等で誤検出が多いため。Checkstyle は対象にする）
tasks.withType<SpotBugsTask>().configureEach {
	enabled = name == "spotbugsMain"
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
