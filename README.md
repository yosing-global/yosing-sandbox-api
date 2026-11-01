# yosing-sandbox-api

sandbox アプリのバックエンド API のリポジトリです。画面と BFF は [yosing-sandbox-web](https://github.com/yosing-global/yosing-sandbox-web) にあります。

sandbox は、個人の学習とポートフォリオを兼ねたアプリです。最初の範囲は TOTP（認証アプリのワンタイムパスワード）による認証（登録・ログイン）です。

## 技術

| 項目 | 内容 |
| :--- | :--- |
| 言語・フレームワーク | Java 25、Spring Boot 4.1 |
| データ | Amazon DynamoDB（単一テーブル設計） |
| 暗号化・署名 | AWS KMS（TOTP の秘密情報の暗号化、仮認証のトークンの署名） |
| 実行環境 | Amazon ECS on AWS Fargate（ARM64） |
| デプロイ | GitHub Actions、ecspresso |

## アーキテクチャ

軽量な DDD（ドメイン駆動設計）を、オニオンアーキテクチャで構成しています。

```text
        presentation          infrastructure
     （API の入出口）     （DynamoDB・KMS 等の実装）
               \               /
                ▼             ▼
                  application
               （ユースケース）
                      │
                      ▼
               domain/service
                      │
                      ▼
                domain/model
          （エンティティ・値オブジェクト）
```

- 矢印は依存の向きです。中心の domain は、Spring や AWS SDK などのライブラリに依存しない、ふつうの Java で書きます。
- 外の技術（DynamoDB・KMS・JWT 等）は、domain と application が持つインターフェースを infrastructure が実装する形にしています。そのため、手元では DynamoDB Local やローカルの暗号化に、AWS では本物の DynamoDB と KMS に、Spring のプロファイルで差し替えられます。
- 依存の決まりは、ArchUnit のテスト（`ArchitectureTests`）で確かめています。

### ディレクトリ構成

```text
.
├── src/main/java/net/yosing/sandbox/api/
│   ├── presentation/      API の入出口。コントローラー、リクエスト・レスポンスの型、エラー応答（Problem Details）への変換
│   ├── application/       ユースケース（登録・ログイン・セッション）の処理の流れ
│   │   └── port/          アプリが外の技術に求めるもののインターフェース（暗号化・署名・トランザクションの保存等）
│   ├── domain/
│   │   ├── model/         集約ごとのエンティティ・値オブジェクト・Repository のインターフェース（user・registration・session）
│   │   │   └── shared/    2つ以上の集約から使う値オブジェクト（totp 等）
│   │   └── service/       複数のモデルにまたがる業務の決まり（TOTP の検証等）
│   └── infrastructure/    技術の実装（aws・dynamodb・crypto・jwt）と、その設定
├── src/main/resources/    設定ファイル（application.yml と、プロファイルごとの application-*.yml）
├── src/test/              単体テスト（ドメインの決まり・ユースケース・ArchUnit。Docker なしで動く）
├── src/integrationTest/   結合テスト（アプリを起動し、Testcontainers の DynamoDB Local を使う。API のリクエストとレスポンス等）
├── config/                静的チェック（Checkstyle・SpotBugs）の設定
├── deploy/                ECS のタスク定義・サービスの定義（ecspresso）
├── .github/               GitHub Actions のワークフロー（PR・develop・PR 環境の削除）と PR のテンプレート
├── compose.yaml           手元の開発用の DynamoDB Local
└── Dockerfile             イメージ（linux/arm64）
```

## ブランチと環境

| 契機 | デプロイ先 |
| :--- | :--- |
| Pull Request | PR ごとの環境（PR を閉じると削除） |
| `develop` へのマージ | dev、test |
| `main` へのマージ | stg |
| 手動 | prod |

## ローカルでの開発

Java 25 が無い場合は、初回のビルドで Gradle が自動でダウンロードします（Gradle 自体は Java 17 以上で動きます）。結合テストと手元での起動には Docker が要ります（DynamoDB Local を動かすため）。単体テストだけなら Docker は要りません。

```powershell
# ビルドとテスト（単体テスト・結合テスト・静的チェック。結合テストは Testcontainers で DynamoDB Local を自動で起動する）
.\gradlew.bat build

# 単体テストだけ（Docker なしで速く動く）
.\gradlew.bat test

# 結合テストだけ
.\gradlew.bat integrationTest

# DynamoDB Local の起動（止めるとデータは消える）
docker compose up -d

# 起動（http://localhost:8080/actuator/health で確認できる。起動時に DynamoDB Local にテーブルを作る）
.\gradlew.bat bootRun --args='--spring.profiles.active=local'

# DynamoDB Local の停止
docker compose down
```

### イメージ

```powershell
# jar を作ってから、イメージを作る（Dockerfile は build/libs/app.jar を読む）
.\gradlew.bat build
docker buildx build --platform linux/arm64 --tag api .
```

本番は linux/arm64 で動かします。手元で起動して確かめる場合は、PC の CPU に合わせて `--platform linux/amd64` で作ります。

### プロファイル

`SPRING_PROFILES_ACTIVE`（または `--spring.profiles.active`）には、環境名を指定します。

| 環境名 | 用途 |
| :--- | :--- |
| `local` | 手元の PC での開発 |
| `pr` | Pull Request ごとの環境 |
| `dev` / `test` / `stg` / `prod` | 各環境 |

環境ごとの値（テーブル名等）は、設定ファイルではなく環境変数で渡します。
