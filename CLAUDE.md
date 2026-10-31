# CLAUDE.md

sandbox の api（バックエンド）のリポジトリ。Spring Boot（Java）。sandbox は個人の学習とポートフォリオを兼ねたプロダクトで、最初の範囲は TOTP 認証（登録・ログイン）だけ。画面と BFF は `yosing-sandbox-web`（Next.js）。

## 状況（2026-10-30 時点）

- Spring Boot の土台（Actuator のヘルスチェック、プロファイルのグループ）、Dockerfile、静的チェック、CI（ビルド・テスト、ECR への push、PR 環境と dev / test へのデプロイ・確認、PR 環境の削除）まで。
- これからの作業の順番：
  1. API の仕様を Notion にまとめる
  2. アプリの中身の準備（DynamoDB Local、暗号化と署名の抽象化）
- nonprod のインフラ（ECR・ECS クラスター・Cloud Map・IAM・SG・ロググループ・KMS・DynamoDB・CI ロールの権限）は準備済み。
- stg / prod のインフラはまだ無い（後で `aws-infra-env` に作る）。
- 未決定：機能ごとのパッケージの分け方。Notion の DB 設計の未確定事項（有効期間、失敗回数の閾値、パスワードのハッシュ方式、ログイン ID の正規化等）は、各機能の実装のときに決める。

## 技術

| 項目 | 内容 |
| :--- | :--- |
| 言語・フレームワーク | Java 25（LTS）、Spring Boot 4.1.2 |
| ビルド | Gradle（Kotlin DSL）。Java 25 は Gradle のツールチェーンで用意する（無ければ foojay で自動ダウンロード） |
| group ID・artifact ID | `net.yosing.sandbox`・`api`（ベースのパッケージは `net.yosing.sandbox.api`） |
| TOTP | `com.eatthepath:java-otp`。一致したステップを返す検証（前後のステップの照合）は自前で書く（再利用の防止に、一致したステップが要るため） |
| JWT | `nimbus-jose-jwt`。署名・検証は KMS（GenerateMac / VerifyMac）とローカル（HmacSHA256）で差し替える |
| AWS | AWS SDK for Java 2.x（`dynamodb-enhanced`、`kms`）。Spring Cloud AWS は使わない |
| ヘルスチェック | Actuator の `/actuator/health`（`/healthz` はリバースプロキシの NGINX が先に応答するため使わない） |
| テスト | JUnit 5。DynamoDB を使う結合テストは Testcontainers（DynamoDB Local）。単体テストと同じ `./gradlew build` で流す |
| 静的チェック | Checkstyle（独自の設定 `config/checkstyle/checkstyle.xml`。字下げはタブのため、字下げのルールは使わない）、SpotBugs ＋ Find Security Bugs（`config/spotbugs/exclude.xml`。テストのコードは対象外）。`./gradlew build` で動き、1件でも検出したら失敗する |
| イメージ | Dockerfile。jar は先に `./gradlew build` で作り（`build/libs/app.jar`）、Dockerfile では層に分けて入れるだけ。層に分ける段は `--platform=$BUILDPLATFORM` で動かすため、x86 のランナーでも arm64 のイメージをエミュレーションなしで作れる（ARM64 のランナー `ubuntu-24.04-arm` は、公開リポジトリなら無料）。ベースは `eclipse-temurin:25-jre-noble`（ダイジェストで固定） |

### プロファイル

`SPRING_PROFILES_ACTIVE` には環境名を指定し、`application.yml` のグループで、動き方のプロファイルを有効にする。設定ファイルは動き方の単位で書き、環境ごとのファイルは必要になるまで作らない。環境ごとの値（テーブル名・KMS の鍵の ARN・環境名）はファイルに書かず、環境変数で渡す。

| 指定する（環境名） | 有効になるもの | DynamoDB | 暗号化・署名 |
| :--- | :--- | :--- | :--- |
| `local` | `local`、`dynamodb-local` | DynamoDB Local（Docker） | AES-GCM・HmacSHA256 |
| `pr` | `pr`、`aws`、`dynamodb-local` | DynamoDB Local（サイドカー） | KMS |
| `dev` / `test` / `stg` / `prod` | 環境名、`aws` | 本物のテーブル | KMS |

- 実装の切り替え（`@Profile`）は、環境名ではなく `aws` / `dynamodb-local` / `local` の単位で書く。
- KMS の暗号化コンテキストの `env` は環境名（PR 環境はすべて `pr`。`pr<番号>` ではない）。IAM の条件で強制されている。
- dev / test のタスクロールに許可されている DynamoDB の操作は `GetItem` / `PutItem` / `UpdateItem` / `DeleteItem` / `ConditionCheckItem` だけ（`TransactWriteItems` は中の操作で判定される）。テーブルの作成・確認は `dynamodb-local` のときだけ行う。

### コードを書くときの注意

- Spring の Bean は1つのインスタンスを全部の要求で共有する。Bean に書き換えられるフィールド（要求ごとの状態）を持たせない（SpotBugs でも検出しきれない）。
- 静的チェックの検出を外すときは、外す範囲を最小にし（`@SuppressWarnings("checkstyle:...")` / `@SuppressFBWarnings`、または除外のファイル）、理由を書く。

### 手元のビルド

Norton の TLS 検査で、Claude のシェルからの Gradle の通信（wrapper・依存ライブラリ・JDK のダウンロード）が PKIX のエラーで失敗する。`./gradlew build` はユーザーに PowerShell か IntelliJ で実行してもらう。

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

## CI の流れ（予定。作るのは PR → develop の順、stg / prod はインフラができてから）

| 契機 | 内容 |
| :--- | :--- |
| PR（作成・更新） | 静的チェック・テスト → イメージの push → PR 環境 `pr<番号>` へのデプロイ（Cloud Map の設定を含む） → 確認 |
| PR を閉じたとき | PR 環境の削除（ECS のサービス・Cloud Map のサービス・スケジュール） |
| develop へのマージ | PR とほぼ同じ。デプロイ先は dev と test、確認は test に向けて |
| main へのマージ | イメージの push → stg へのデプロイ → 確認 |
| release（手動） | stg と同じイメージを prod にデプロイ |

- イメージは1コミットにつき1つ（タグはコミットのハッシュ、環境名は入れない）。環境の違いはタスク定義の環境変数だけ。
- ジョブは `check → image → deploy → verify` の順に分ける。今の `verify` はヘルスチェックだけ。E2E（`yosing-sandbox-e2e`）ができたら、その再利用できるワークフロー（`workflow_call`）を呼ぶ `e2e` のジョブを後ろに足す。
- test 環境の排他：GitHub の `concurrency` は同じリポジトリの中でしか効かない。今はリポジトリの中だけで直列にし、web と api の重なりは許す。E2E ができたら、DynamoDB の条件付き書き込み（期限付き）のロックで、デプロイから E2E までを web と api の間で直列にする（テーブルと権限は `aws-infra-env` に足す）。
- ランナーは `ubuntu-latest`（x86）。Dockerfile の作りでエミュレーションなしに arm64 を作れるため。ARM のランナーに替えるのは `runs-on` の変更だけで済む。
- イメージのタグ：PR / develop / main はコミットのハッシュ。release（手動）でバージョン（例：`1.2.0`）を入力し、stg に出したイメージ（同じダイジェスト）に `v1.2.0` のタグを足して prod に出す（git にも同じタグを付ける）。バージョンは release のときに決めるため、アプリ（jar）にはバージョンを入れず、コミットのハッシュを返す。`build.gradle.kts` の `version` は手で上げない。
- stg / prod を作るときの課題：main で push する CI ロールが無い（信頼ポリシーは `pull_request` と develop だけ）、ECR のライフサイクルに「`v` で始まるタグは直近の10個を残す」ルールを足す（直近の30個だけだと prod のイメージが消える。`aws-infra-global`）、stg は毎日 3:00 に削除される（再構築とデプロイの順番）、Application Auto Scaling のサービスにリンクされたロール（`AWSServiceRoleForApplicationAutoScaling_ECSService`）を Terraform で先に作る（初めての登録で自動で作られるが、作った直後は使えずに失敗することがある。NonProd では一度失敗し、再実行で通った）。

## nonprod へのデプロイ

- デプロイは **ecspresso**。定義は `deploy/`（`ecspresso.jsonnet`・`task-def.jsonnet`・`service-def.jsonnet`）。値は SSM パラメータから読み（ecspresso の `ssm` / `ssm_list`）、環境ごとの値は CI が環境変数で渡す（`APP_ENV`：`dev` / `test` / `pr<番号>`、`ROLE_ENV`：`dev` / `test` / `pr`、`ECS_CLUSTER`、`IMAGE`、`CLOUD_MAP_SERVICE_ARN`）。
- ワークフロー：契機ごとのファイルが、共通の手順（再利用できるワークフロー、ファイル名の先頭が `_`）を呼ぶ。手順を直すときは `_build.yml` / `_deploy.yml` の1か所だけを直す。

| ファイル | 契機 | 内容 |
| :--- | :--- | :--- |
| `pr.yml` | PR（作成・更新） | `_build` → `_deploy`（`pr<番号>`）→ PR へのコメント |
| `develop.yml` | develop へのマージ | `_build` → `_deploy`（`dev`）と `_deploy`（`test`）を並べて動かす |
| `pr-cleanup.yml` | PR を閉じたとき | PR 環境の削除 |
| `_build.yml` | 呼ばれる側 | `check`（静的チェック・テスト・jar）→ `image`（arm64 のイメージの push。タグを返す） |
| `_deploy.yml` | 呼ばれる側 | 1つの環境（入力：`app_env`・`role_env`・`image_tag`）への `deploy` → `verify` |

  - 呼ぶ側のジョブで `permissions`（`contents: read`、`id-token: write`）を渡す。呼ばれる側で取った OIDC のトークンの `sub` は、呼ぶ側の契機（`pull_request` / `ref:refs/heads/develop`）になる。
  - `concurrency`：`pr.yml` と `pr-cleanup.yml` は `pr-<番号>` のグループ（新しい実行が古い実行を止める）。`develop.yml` は `develop` のグループで止めずに順番に流す（待っている実行は最新の1つだけ残る）。
  - verify：ランナーに Session Manager のプラグイン（バージョンとチェックサムを固定）を入れ、SSM のポートフォワーディングでリバースプロキシにつなぎ、`https://<env>.api.local.sandbox.yosing.net:8090/actuator/health` が `UP` になるまで待つ（デプロイの直後は DNS の TTL の分だけ待つことがある）。
  - PR の Conversation に、PR 環境のヘルスチェックの URL をコメントで出す（`.github/scripts/pr-env-comment.sh`。目印 `<!-- pr-env -->` のコメントを1つだけ作り、デプロイのたびに書き換え、PR を閉じたら「削除済み」にする）。ジョブに `environment:` を付ける方法は使わない（OIDC のトークンの `sub` が `environment:<名前>` に変わり、CI ロールの信頼ポリシーに合わなくなるため）。
- CI ロール：NonProd の `github-actions-sandbox-api-role`（GitHub の OIDC。引き受けられるのは `pull_request` と develop ブランチ）。
- アカウント ID・ロールの ARN・ECR の URL はファイルに書かず、GitHub の Variables に置く（インフラの情報を公開しないため）。公開リポジトリでは Actions のログも公開されるため、ワークフローの最初に `::add-mask::` で伏せる。
  - Repository variables：`AWS_ROLE_ARN`（NonProd の CI ロールの ARN）、`ECR_REGISTRY`（`<Shared-Infra のアカウント ID>.dkr.ecr.ap-northeast-1.amazonaws.com`）。stg / prod は Environments に同じ名前で置く予定（Environment の値が優先される）。
  - ECR で使える操作は push と取得だけ（`ecr:DescribeImages` は無い）。イメージがあるかは `docker buildx imagetools inspect` で確かめる。
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
  - エラーに出ているリソースの ARN の形で許可する（AWS が権限を確かめるリソースは、操作の名前から推測すると外れることがある。例：`ecs:DescribeServiceDeployments` はサービスの ARN、`servicediscovery:CreateService` は名前空間と `service/*` の両方）。
  - 直した後は、`aws-infra-env` の main への apply が終わってから、少し待って再実行する（IAM の反映に時間がかかる）。
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
