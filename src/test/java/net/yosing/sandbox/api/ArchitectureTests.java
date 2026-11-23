package net.yosing.sandbox.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures;

/**
 * パッケージの依存の決まり（オニオンアーキテクチャ）を確認する。
 *
 * <ul>
 * <li>domain（model・service）← application ← presentation / infrastructure の向きにだけ依存する</li>
 * <li>presentation と infrastructure は互いに依存しない</li>
 * <li>domain は Spring・AWS SDK・JWT 等のライブラリに依存しない（ふつうの Java で書く）</li>
 * </ul>
 */
@AnalyzeClasses(packages = "net.yosing.sandbox.api", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTests {

	// ルールは static のフィールドだけで、ArchUnit はインスタンスを作らない
	private ArchitectureTests() {
	}

	/**
	 * 層の依存が、内側（domain）に向かう向きだけになっている。
	 */
	@ArchTest
	static final ArchRule ONION_ARCHITECTURE = Architectures.onionArchitecture()
		.domainModels("..domain.model..")
		.domainServices("..domain.service..")
		.applicationServices("..application..")
		.adapter("presentation", "..presentation..")
		.adapter("infrastructure", "..infrastructure..")
		// まだクラスが無い層があっても失敗させない
		.withOptionalLayers(true);

	/**
	 * domain が Spring・AWS SDK・Nimbus・Jakarta・Jackson に依存していない。
	 */
	@ArchTest
	static final ArchRule DOMAIN_IS_PLAIN_JAVA = ArchRuleDefinition.noClasses()
		.that()
		.resideInAPackage("..domain..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..", "software.amazon..", "com.nimbusds..", "jakarta..",
				"com.fasterxml..", "tools.jackson..")
		.allowEmptyShould(true);

}
