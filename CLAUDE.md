# CLAUDE.md

sandbox の api（バックエンド）のリポジトリ。Spring Boot（Java）。sandbox は個人の学習とポートフォリオを兼ねたプロダクトで、最初の範囲は TOTP 認証（登録・ログイン）だけ。画面と BFF は `yosing-sandbox-web`（Next.js）。

## 状況（2026-10-30 時点）

- リポジトリは README と本ファイルだけ。次は Spring Boot の土台と CI（nonprod へのデプロイ）。
- nonprod のインフラ（ECR・ECS クラスター・Cloud Map・IAM・SG・ロググループ・KMS・DynamoDB・CI ロールの権限）は準備済み。
- stg / prod のインフラはまだ無い（後で `aws-infra-env` に作る）。
- 未決定：Java と Spring Boot のバージョン、ビルドツール（Gradle / Maven）、パッケージ構成、ライブラリ（TOTP 等）。

## 関連リポジトリ（`../` に clone 済み）

| リポジトリ | 内容 |
| :--- | :--- |
| `aws-infra-global-bootstrap` | AWS の組織・ガードレール（境界ポリシー・タグポリシー・SCP/RCP） |
| `aws-infra-global` | ECR（Shared-Infra の `sandbox/api`）、アプリの CI ロールの信頼ポリシー、DNS のホストゾーン |
| `aws-infra-env` | 環境のインフラ（`envs/`）と sandbox のインフラ（`products/sandbox/nonprod`）。CI ロールの権限もここ |
| `yosing-sandbox-web` | 画面と BFF（Next.js） |

- インフラの変更が必要なら `aws-infra-env` の `products/sandbox/nonprod` で行う。本リポジトリでは AWS のリソースを Terraform で作らない（ECS のサービス・タスク定義・Cloud Map のサービス・稼働時間のスケジュールは、本リポジトリの CI が作る）。
- インフラの詳細（名前・権限・SSM パラメータの一覧）は `../aws-infra-env/README.md` の「sandbox（nonprod）」が正。

## 設計

Notion の「方式設計」と「4.3 DB設計」（Notion MCP で検索できる）。要点：

- **構成**：web（BFF）→ api を VPC 内で呼ぶ（Cloud Map の `<env>-api.sandbox.internal:8080`）。api は公開しない。待ち受けは 8080。
- **データ**：DynamoDB の単一テーブル `sandbox-<env>-app`。キーは `PK` / `SK`（文字列）、TTL の属性は `expiresAt`。
  - アイテム：`USER#<userId>` / `PROFILE`、`LOGINID#<loginId>` / `LOGINID`（ログイン ID の一意性）、`REG#<registrationId>` / `REG`（登録途中）、`SESSION#<セッション ID のハッシュ>` / `SESSION`
  - 登録の確定は1つのトランザクション（USER と LOGINID の作成、REG の削除、SESSION の作成）。ログインの TOTP 成功時は、最後に使ったステップの条件付き更新と SESSION の作成（同じコードの再利用を防ぐ）。
  - ログインはメールアドレスではなく、任意のログイン ID。
- **KMS（TOTP の秘密情報）**：`kms:Encrypt` / `kms:Decrypt` に、暗号化コンテキスト `{"userId": <userId>, "env": <アプリの環境>}` を必ず渡す（渡さないと KMS の呼び出しが拒否される。詳細は `../aws-infra-env/README.md`）。復号のときのコンテキストは DB から読まず、処理中のユーザーと環境から組み立てる。
- **KMS（仮認証 JWT の署名）**：HMAC の鍵で `kms:GenerateMac` / `kms:VerifyMac`。鍵は dev / test / pr で共有するため、JWT の `aud` に環境名を入れ、他の環境の JWT を受け付けない。
- **ローカルの開発**：DynamoDB は DynamoDB Local。KMS は使わず、暗号化・署名をインターフェースで抽象化して、ローカルでは Java 標準の AES-GCM（コンテキストを AAD に）・HmacSHA256 に差し替える（Spring のプロファイルで切り替え）。

## 環境とブランチ

| 契機 | デプロイ先 |
| :--- | :--- |
| PR | PR 環境 `pr<番号>`（PR を閉じたら削除） |
| develop へのマージ | `dev` と `test` |
| main へのマージ | `stg`（インフラ未作成） |
| 手動 | `prod`（インフラ未作成。stg に出したものだけ） |

- nonprod（dev / test / PR 環境）は1つの AWS アカウント（NonProd）を共有する。
- nonprod の URL は `https://<env>.api.local.sandbox.yosing.net:8090`。SSM のポートフォワーディング（ローカルの 8090 → リバースプロキシの 443）でアクセスする。手順は `../aws-infra-env/README.md`。
- nonprod の稼働時間は毎日 9:00〜翌 1:00（JST）。それ以外はタスクが 0 になる。

## nonprod へのデプロイ

- デプロイは **ecspresso**。タスク定義・サービスの定義を本リポジトリに置き、値は SSM パラメータから読む。
- CI ロール：NonProd の `github-actions-sandbox-api-role`（GitHub の OIDC。引き受けられるのは `pull_request` と develop ブランチ）。
- アカウント ID・ロールの ARN・ECR の URL はファイルに書かず、GitHub の Variables に置く（インフラの情報を公開しないため）。
- イメージ：Shared-Infra の ECR `sandbox/api`。**linux/arm64**（Fargate Spot の ARM64 で動かす）。タグはコミットのハッシュ（上書き禁止）。develop では作り直す。
- 名前（`<env>` は `dev` / `test` / `pr<番号>`）：

| もの | 名前 |
| :--- | :--- |
| ECS のサービス・Cloud Map のサービス | `<env>-api` |
| タスク定義のファミリー | `sandbox-<env>-api` |
| ロググループ | `/sandbox/nonprod/api`（ログストリームの先頭を `<env>/` にする） |

- タスクのサイズ：dev / test は 0.5 vCPU / 1GB。PR 環境は 0.5 vCPU / 2GB（api 1GB ＋ DynamoDB Local 1GB）。
- PR 環境は本物のテーブルを使わず、タスクに DynamoDB Local（`amazon/dynamodb-local`、`-inMemory`）をサイドカーで同居させ、api から `localhost:8000` で使う。データはデプロイ・Spot の中断・毎日の停止で消えるため、api の起動時にテーブル作成と初期データを流す。テーブルの定義は `aws-infra-env` の `dynamodb.tf` と揃える。
- Cloud Map のサービスと、稼働時間のスケジュール（Application Auto Scaling。9:00 に 1、翌 1:00 に 0、タイムゾーン `Asia/Tokyo`）は、CI が AWS CLI で作る。PR 環境の削除のときに一緒に消す。
- CI で AccessDenied が出たら、CI ロールの権限（`aws-infra-env` の `products/sandbox/nonprod/iam.tf`）を直す。
- E2E：GitHub のランナーから SSM のポートフォワーディングでリバースプロキシ（Name タグ `nonprod-nat-ec2`、文書 `AWS-StartPortForwardingSession`）につなぎ、`https://<env>.api.local.sandbox.yosing.net:8090` を叩く（DNS は 127.0.0.1 を返す）。

使う SSM パラメータ（NonProd、すべて String。サブネットは StringList）：

| パラメータ | 内容 |
| :--- | :--- |
| `/products/sandbox/ecs/cluster-name` | ECS クラスター |
| `/products/sandbox/ecs/task-execution-role-arn` | タスク実行ロール |
| `/products/sandbox/cloud-map/namespace-id` | Cloud Map の名前空間 |
| `/products/sandbox/api/<env>/task-role-arn` | タスクロール（`<env>` は `dev` / `test` / `pr`。PR 環境はすべて `pr`） |
| `/products/sandbox/api/security-group-id` | SG |
| `/products/sandbox/api/log-group-name` | ロググループ |
| `/products/sandbox/api/kms-key-arn` | TOTP の秘密情報の暗号化用の鍵 |
| `/products/sandbox/api/jwt-kms-key-arn` | JWT の署名用の鍵 |
| `/products/sandbox/dynamodb/<env>/table-name` | テーブル（`dev` / `test` だけ） |
| `/infra-env/network/private-subnet-ids` | タスクを置くサブネット |

## 作業の進め方

- AWS のリソースを変える操作（作成・削除）は、CI 経由かユーザーの確認のうえで行う。
