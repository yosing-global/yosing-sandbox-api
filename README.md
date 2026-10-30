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

（Spring Boot の土台を作ったあとに記載します）
