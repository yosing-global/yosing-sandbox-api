# yosing-sandbox-api

sandbox アプリのバックエンド API のリポジトリです。画面と BFF は [yosing-sandbox-web](https://github.com/yosing-global/yosing-sandbox-web) にあります。

sandbox は、個人の学習とポートフォリオを兼ねたアプリです。最初の範囲は TOTP（認証アプリのワンタイムパスワード）による認証（登録・ログイン）です。

## 技術

| 項目 | 内容 |
| :--- | :--- |
| 言語・フレームワーク | Java、Spring Boot |
| データ | Amazon DynamoDB（単一テーブル設計） |
| 暗号化・署名 | AWS KMS（TOTP の秘密情報の暗号化、仮認証のトークンの署名） |
| 実行環境 | Amazon ECS on AWS Fargate（ARM64） |
| デプロイ | GitHub Actions、ecspresso |

## ブランチと環境

| 契機 | デプロイ先 |
| :--- | :--- |
| Pull Request | PR ごとの環境（PR を閉じると削除） |
| `develop` へのマージ | dev、test |
| `main` へのマージ | stg |
| 手動 | prod |

## ローカルでの開発

Java 25 が無い場合は、初回のビルドで Gradle が自動でダウンロードします（Gradle 自体は Java 17 以上で動きます）。

```powershell
# ビルドとテスト
.\gradlew.bat build

# 起動（http://localhost:8080/actuator/health で確認できる）
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
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
