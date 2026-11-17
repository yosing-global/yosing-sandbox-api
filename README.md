# yosing-sandbox-api

sandbox アプリのバックエンド API のリポジトリです。画面と BFF は [yosing-sandbox-web](https://github.com/yosing-global/yosing-sandbox-web) で管理しています。

sandbox は、個人の学習とポートフォリオを兼ねたアプリです。最初のリリースの範囲は、TOTP（認証アプリのワンタイムパスワード）による認証（登録・ログイン）です。

API の仕様と動作確認の手順は、[Wiki](https://github.com/yosing-global/yosing-sandbox-api/wiki) にまとめています。

## 技術

| 項目 | 内容 |
| :--- | :--- |
| 言語・フレームワーク | Java 25、Spring Boot 4.1 |
| データ | Amazon DynamoDB（シングルテーブル設計） |
| 暗号化・署名 | AWS KMS（TOTP の秘密情報の暗号化、仮認証トークンの署名） |
| 実行環境 | Amazon ECS on AWS Fargate（ARM64） |
| デプロイ | GitHub Actions、ecspresso |

## アーキテクチャ

軽量な DDD（ドメイン駆動設計）を、オニオンアーキテクチャで構成しています。

```text
        presentation          infrastructure
       （API の入口）     （DynamoDB・KMS 等の実装）
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

- 矢印は依存の向きを表します。中心の domain は、Spring や AWS SDK などのライブラリに依存しない、プレーンな Java で実装しています。
- 外部の技術（DynamoDB・KMS・JWT 等）は、domain と application が定義するインターフェースを infrastructure が実装する形にしています。そのため、ローカルでは DynamoDB Local とローカルの暗号化処理を、AWS では DynamoDB と KMS を、Spring のプロファイルで切り替えられます。
- 依存のルールは、ArchUnit のテスト（`ArchitectureTests`）で検証しています。

### ディレクトリ構成

```text
.
├── src/main/java/net/yosing/sandbox/api/
│   ├── presentation/      API の入口。コントローラー、リクエスト・レスポンスの型、エラー応答（Problem Details）への変換
│   ├── application/       ユースケース（登録・ログイン・セッション）の処理の流れ
│   │   └── port/          アプリが外部の技術に求める機能のインターフェース（暗号化・署名・トランザクションでの保存等）
│   ├── domain/
│   │   ├── model/         集約ごとのエンティティ・値オブジェクト・Repository のインターフェース（user・registration・session）
│   │   │   └── shared/    複数の集約で使う値オブジェクト（totp 等）
│   │   └── service/       複数のモデルにまたがる業務ルール（TOTP の検証等）
│   └── infrastructure/    技術的な実装（aws・dynamodb・crypto・jwt）とその設定
├── src/main/resources/    設定ファイル（application.yml と、プロファイルごとの application-*.yml）
├── src/test/              単体テスト（ドメインのルール・ユースケース・ArchUnit。Docker なしで実行可能）
├── src/integrationTest/   結合テスト（アプリを起動し、Testcontainers の DynamoDB Local を使用。API のリクエストとレスポンス等）
├── config/                静的解析（Checkstyle・SpotBugs）の設定
├── deploy/                ECS のタスク定義・サービス定義（ecspresso）
├── .github/               GitHub Actions のワークフロー（PR・develop・main・prod・PR 環境の削除・テストユーザーの削除・develop への同期）と PR のテンプレート
├── compose.yaml           ローカル開発用の DynamoDB Local
└── Dockerfile             コンテナイメージ（linux/arm64）
```

## ブランチと環境

| トリガー | デプロイ先 | デプロイ後の処理 |
| :--- | :--- | :--- |
| Pull Request | PR ごとの環境（PR のクローズで削除） | なし |
| `develop` へのマージ | dev、test | test で E2E → dev・test のテストユーザーの削除 |
| `main` へのマージ | stg | stg で E2E → stg のテストユーザーの削除 |
| 手動（`prod.yml`。stg で成功したコミットのみ） | prod | なし |

- E2E は、[yosing-sandbox-e2e](https://github.com/yosing-global/yosing-sandbox-e2e) の再利用可能なワークフローを呼び出して実行します（Playwright。ブラウザーから web・API・DB までを通して、登録・ログイン・ログアウトの流れを検証）。シナリオと実装方針は、e2e の README を参照してください。
- api の PR では E2E を実行しません。api の変更が画面からの操作の流れを壊していないかは、develop へのマージ後に test の E2E で確認します。API の細かな判定（入力ルールの境界値・有効期限・ロックの回数）は、このリポジトリの結合テストで検証します。
- test では web の CI も E2E を実行します。web と api の間で排他制御をしていないため、相手のデプロイ中に実行されると失敗することがあります。その場合は再実行します（自動で1回再試行し、それでも失敗した場合は手動で Re-run）。
- 各環境のタスクは通常は停止しており、使用するときに起動します（毎日 3:00 JST に停止）。

### テストユーザーの削除

E2E は、テストのたびに画面からユーザーを登録し、削除せずに残します。蓄積したユーザーは、develop・main へのマージのたびに、E2E の後で削除します（E2E の成否にかかわらず実行し、削除に失敗してもデプロイは取り消しません）。

- 削除の対象：ログイン ID が `e2e_dummy_` で始まり、登録から24時間を過ぎたユーザー（ユーザーとログイン ID の項目。セッション等は TTL で自動削除）。24時間の猶予は、実行中の E2E のユーザーを削除しないためです。
- 仕組み：api のタスク定義のプロファイルを `<環境名>,cleanup` に差し替え、ECS のタスクとして一度だけ実行します（`.github/workflows/_cleanup.yml`）。データの削除はその環境のタスクロールで行い、CI ロールにはタスクの起動・停止の権限だけを与えています。
- 安全のため、dev・test・stg 以外の環境では何もせずに失敗します。ログには件数だけを出力します（ログイン ID は出力しない）。
- 定期実行はしません。マージがない間は、テストユーザーが蓄積されたままになります（テスト結果には影響しません）。

## ローカルでの開発

Java 25 がない場合は、初回のビルドで Gradle が自動でダウンロードします（Gradle 自体は Java 17 以上で動作します）。結合テストとローカルでの起動には、DynamoDB Local を動かすために Docker が必要です。単体テストだけであれば Docker は不要です。

```powershell
# ビルドとテスト（単体テスト・結合テスト・静的解析。結合テストは Testcontainers で DynamoDB Local を自動で起動する）
.\gradlew.bat build

# 単体テストのみ（Docker なしで高速に実行できる）
.\gradlew.bat test

# 結合テストのみ
.\gradlew.bat integrationTest

# DynamoDB Local の起動（停止するとデータは削除される）
docker compose up -d

# API の起動（http://localhost:8080/actuator/health で確認できる。起動時に DynamoDB Local にテーブルを作成する）
.\gradlew.bat bootRun --args='--spring.profiles.active=local'

# DynamoDB Local の停止
docker compose down
```

### コンテナイメージ

```powershell
# jar を作成してから、イメージを作成する（Dockerfile は build/libs/app.jar を読み込む）
.\gradlew.bat build
docker buildx build --platform linux/arm64 --tag api .
```

本番環境は linux/arm64 で動作します。ローカルで起動して確認する場合は、PC の CPU に合わせて `--platform linux/amd64` で作成します。

### プロファイル

`SPRING_PROFILES_ACTIVE`（または `--spring.profiles.active`）には、環境名を指定します。

| 環境名 | 用途 |
| :--- | :--- |
| `local` | ローカルの PC での開発 |
| `pr` | Pull Request ごとの環境 |
| `dev` / `test` / `stg` / `prod` | 各環境 |
| `cleanup` | テストユーザーの削除（環境名と組み合わせて `dev,cleanup` のように指定する。Web サーバーを起動せず、削除が終わると終了する。上記「テストユーザーの削除」を参照） |

環境ごとの値（テーブル名等）は、設定ファイルではなく環境変数で渡します。
