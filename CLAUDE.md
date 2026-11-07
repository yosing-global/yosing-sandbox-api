# CLAUDE.md

sandbox の api（バックエンド）のリポジトリ。Spring Boot（Java）。sandbox は個人の学習とポートフォリオを兼ねたプロダクトで、最初の範囲は TOTP 認証（登録・ログイン）だけ。画面と BFF は `yosing-sandbox-web`（Next.js）。

## 状況（2026-11-03 時点）

- Spring Boot の土台（Actuator のヘルスチェック、プロファイルのグループ）、Dockerfile、静的チェック、CI（ビルド・テスト、ECR への push、PR 環境と dev / test へのデプロイ・確認、PR 環境の削除）まで。
- API の仕様は Notion の「4.2 API設計」にまとめ済み。アプリの中身の準備は、DynamoDB の土台（クライアント・DynamoDB Local・起動時のテーブル作成）、TOTP の秘密情報の暗号化、仮認証 JWT の署名（どちらも KMS とローカルの差し替え）まで。
- API の実装（4.2 API設計のエンドポイント）は、登録の開始 → 登録の確定 → ログインの開始 → ログインの確定 → セッションの順に、PR を分けて進め、全部のエンドポイント（`POST /v1/registrations`・`/v1/registrations/{registrationId}/verify`・`/v1/logins`・`/v1/logins/verify`・`GET /v1/me`・`DELETE /v1/sessions/current`）まで。
- 次の作業：web（BFF）からの呼び出しと E2E（`yosing-sandbox-e2e`）、stg / prod のインフラ。
- nonprod のインフラ（ECR・ECS クラスター・Cloud Map・IAM・SG・ロググループ・KMS・DynamoDB・CI ロールの権限）は準備済み。
- stg / prod のインフラはまだ無い（後で `aws-infra-env` に作る）。
- 属性名・型（DynamoDB の項目）は、各機能の実装のときに決める（Notion の DB 設計の未確定事項）。

## 技術

| 項目 | 内容 |
| :--- | :--- |
| 言語・フレームワーク | Java 25（LTS）、Spring Boot 4.1.2 |
| ビルド | Gradle（Kotlin DSL）。Java 25 は Gradle のツールチェーンで用意する（無ければ foojay で自動ダウンロード） |
| group ID・artifact ID | `net.yosing.sandbox`・`api`（ベースのパッケージは `net.yosing.sandbox.api`） |
| TOTP | `com.eatthepath:java-otp`。一致したステップを返す検証（前後のステップの照合）は自前で書く（再利用の防止に、一致したステップが要るため） |
| JWT | `nimbus-jose-jwt`。署名・検証は KMS（GenerateMac / VerifyMac）とローカル（HmacSHA256）で差し替える |
| AWS | AWS SDK for Java 2.x（`dynamodb`、`kms`）。Enhanced Client は使わず、ドメインの型と DynamoDB の項目の変換は infrastructure で書く。Spring Cloud AWS は使わない |
| ヘルスチェック | Actuator の `/actuator/health`（`/healthz` はリバースプロキシの NGINX が先に応答するため使わない） |
| テスト | JUnit。単体テスト（`src/test`）と結合テスト（`src/integrationTest`。Testcontainers の DynamoDB Local）に分ける。`./gradlew build` で両方を流す（下の「テスト」） |
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

### パッケージ構成（軽量 DDD・オニオンアーキテクチャ）

`net.yosing.sandbox.api` の下を、一番上は層で、層の中は機能（集約）で分ける。

| パッケージ | 置くもの |
| :--- | :--- |
| `presentation/<機能>/` | Spring MVC のコントローラー、API のリクエスト・レスポンスの型（Jackson・Bean Validation の注釈はここ）。`presentation/error/` に例外 → Problem Details（`code`）の変換 |
| `application/<機能>/` | ユースケース（`*UseCase`。4.2 API設計の「処理概要」の流れ）、その入出力の型（`*Command` / `*Result`） |
| `application/error/` | 2つ以上の機能で使うアプリの例外（`OtpInvalidException`・`OtpAttemptsExceededException`）。1つの機能だけの例外は、その機能のパッケージに置く |
| `application/port/` | アプリが外の技術に求めるもののインターフェース（秘密情報の暗号化、JWT の署名、パスワードのハッシュ、集約をまたぐトランザクションの保存等）。時計は port を作らず、Java 標準の `java.time.Clock` の Bean（`infrastructure/time`）を使う |
| `domain/model/<集約>/` | 集約ごとのエンティティ・値オブジェクト・Repository のインターフェース（`user`・`registration`・`session`） |
| `domain/model/shared/<概念>/` | 2つ以上の集約から使う値オブジェクト（`totp`：`TotpSecret`・`OtpCode`・`TotpStep`） |
| `domain/service/` | 複数のモデルにまたがるか、どのモデルにも属さない業務の決まり（例：`TotpVerifier`。名前に `Service` は付けない）。Spring に依存しないため、Bean は `application/DomainServiceConfiguration` の `@Bean` で作る |
| `infrastructure/<技術>/` | 技術の実装（`aws`・`dynamodb`（Repository の実装、項目の変換）・`crypto`（KMS とローカル）・`jwt`）と、その Spring の設定 |

- 依存の向き：`domain/model` ← `domain/service` ← `application` ← `presentation` / `infrastructure`。`presentation` と `infrastructure` は互いに依存しない。`domain` は Spring・AWS SDK・Nimbus・Jakarta・Jackson に依存しない（ふつうの Java）。`ArchitectureTests`（ArchUnit）で確かめる。
- 1つのモデルで完結する決まり（ロックの判定、期限の延長、失敗回数の上限、ログイン ID の規則等）はモデルの中に書き、`domain/service` には寄せない（モデルがただのデータ入れにならないようにする）。
- `domain/model` の直下は集約だけにする。`shared/` に置くのは2つ以上の集約から使う値オブジェクトだけで、1つの集約でしか使わないものはその集約に置く（`shared/` を何でも置き場にしない）。
- 集約どうしは ID（`UserId` 等）だけで参照し、ほかの集約のエンティティは持たない（例：`Registration` は `UserId` を持つが `User` は持たない）。
- 型は層ごとに分ける：API のリクエスト・レスポンス（presentation）、ユースケースの入出力（application）、ドメインモデル（domain）、DynamoDB の項目（infrastructure）。
- 集約をまたぐトランザクション（登録の確定・ログインの確定）は、`application/port` のユースケース専用の保存のインターフェースで表し、`infrastructure/dynamodb` で `TransactWriteItems` を使って実装する。

### テスト

| 種類 | 置き場所（Gradle のタスク） | 内容 |
| :--- | :--- | :--- |
| 単体テスト | `src/test`（`test`） | ドメインの決まり（ロック・期限・ログイン ID の規則・TOTP の照合等）、ユースケースの流れ（port を偽物に差し替える）、ArchUnit、プロファイルの解決。Spring の Bean を作らず、Docker なしで速く動く |
| 結合テスト | `src/integrationTest`（`integrationTest`） | API のテスト（`@SpringBootTest` ＋ MockMvc でリクエストを送り、ステータス・本文・エラーの `code`、必要なら DB の内容を確かめる）と、技術の実装のテスト（Repository の条件付き書き込み・トランザクション・項目の変換を DynamoDB Local で動かす）。Docker が要る |
| E2E | `yosing-sandbox-e2e` と CI | デプロイした環境で、画面・BFF・API を通して確かめる |

- テストのクラスとメソッド（ArchUnit のルールを含む）には、何を確かめるかを簡潔に書いた JavaDoc を付ける（メソッドは期待する動きを1文で。理由があれば括弧で添える）。
- テスト用の仮の値（ログイン ID・パスワード・鍵・ARN・環境名・jti 等）は、先頭か末尾に `dummy`（`DUMMY`）を入れ、本物の値でないと分かるようにする（例：`dummy_alice`、`dummy correct horse battery`、`arn:...:key/dummy`、`dummy-pr12`）。形式の規則のため入れられないもの（UUID・日時・3文字の境界値・バイト列）と、規格の試験用の値（RFC の試験ベクトル）はそのままにする。
- テストのパッケージは `main` と同じ形にする（package-private のクラスを使うため。`integration` のようなパッケージは作らない）。
- 結合テストは `local` のプロファイルと `DynamoDbLocalContainerConfiguration`（Testcontainers）で動かす。
- TOTP のコードや期限は時刻で決まるため、ユースケースは今の日時を `java.time.Clock` の Bean から取り、テストでは `Clock.fixed` に差し替える。domain には日時を引数で渡す。
- KMS の実装は結合テストでは動かせない（手元とテストはローカルの暗号化）。AWS SDK を偽物に差し替えた単体テストで呼び出し方（暗号化コンテキスト等）を確かめ、実際の動きは PR 環境で確かめる。
- テストのコード（単体・結合）は SpotBugs の対象外、Checkstyle の対象。
- 差し替えの方針：domain のテストは本物のオブジェクトだけ（時刻は引数で渡す）。application（ユースケース）のテストは、Repository・port を手書きのフェイク（メモリ上の実装 `InMemory*`、決まった動きの `Fake*`。`src/test` の、インターフェースと同じパッケージに置く。時計は `Clock.fixed`）に差し替え、結果の状態を確かめる。Mockito は、infrastructure で外のライブラリ（AWS SDK 等）の呼び出し方を確かめるときと、フェイクでは作りにくい失敗を起こすときだけに使う。presentation は単体で確かめず、結合テスト（API のテスト）で確かめる。

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

Notion の「2. 方式設計」「4. 外部設計（共通方針）」「4.2 API設計」「4.3 DB設計」（Notion MCP で検索できる）。要点：

- **構成**：web（BFF）→ api を VPC 内で呼ぶ（Cloud Map の `<env>-api.sandbox.internal:8080`）。api は公開しない。待ち受けは 8080。
- **データ**：DynamoDB の単一テーブル `sandbox-<env>-app`。キーは `PK` / `SK`（文字列）、TTL の属性は `expiresAt`。
  - アイテム：`USER#<userId>` / `PROFILE`、`LOGINID#<loginId>` / `LOGINID`（ログイン ID の一意性）、`REG#<registrationId>` / `REG`（登録途中）、`SESSION#<セッション ID のハッシュ>` / `SESSION`
  - 登録の確定は1つのトランザクション（USER と LOGINID の作成、REG の削除、SESSION の作成）。ログインの TOTP 成功時は、最後に使ったステップの条件付き更新と SESSION の作成（同じコードの再利用を防ぐ）。
  - ログインはメールアドレスではなく、任意のログイン ID。
- **KMS（TOTP の秘密情報）**：`kms:Encrypt` / `kms:Decrypt` に、暗号化コンテキスト `{"userId": <userId>, "env": <アプリの環境>}` を必ず渡す（渡さないと KMS の呼び出しが拒否される。詳細は `../aws-infra-env/README.md`）。復号のときのコンテキストは DB から読まず、処理中のユーザーと環境から組み立てる。
- **KMS（仮認証 JWT の署名）**：HMAC の鍵で `kms:GenerateMac` / `kms:VerifyMac`。鍵は dev / test / pr で共有するため、JWT の `aud` に環境名を入れ、他の環境の JWT を受け付けない。
- **API**（詳細は 4.2 API設計）：
  - エンドポイント：`POST /v1/registrations`、`POST /v1/registrations/{registrationId}/verify`、`POST /v1/logins`、`POST /v1/logins/verify`、`GET /v1/me`、`DELETE /v1/sessions/current`。JSON、項目名は lowerCamelCase、日時は ISO 8601 の UTC。
  - セッション ID は `Authorization: Bearer <sessionId>`（API は Cookie を扱わない）。仮認証 JWT は本文の `loginToken`（有効期間5分、`aud` に環境名、`sub` に userId、`jti` を USER に保存して照合する）。
  - エラーは Problem Details（RFC 9457）に `code`（`VALIDATION_FAILED` / `INVALID_CREDENTIALS` / `OTP_INVALID` / `LOGIN_TOKEN_INVALID` / `SESSION_INVALID` / `REGISTRATION_NOT_FOUND` / `LOGIN_ID_TAKEN` / `OTP_ATTEMPTS_EXCEEDED` / `INTERNAL_ERROR`）を加える。
  - 入力：`loginId` は3〜32文字の英小文字・数字・`_`・`-`（小文字にそろえる）、`password` は12〜64文字、`code` は数字6桁。
  - TOTP は6桁・30秒、前後1ステップを許容。仮登録は10分。OTP の失敗は1回の仮登録につき5回まで。ログインは、パスワードまたは OTP を5回続けて間違えたら、アカウントを15分ロック（ロック中もふつうの失敗と同じ応答。OTP の失敗回数は仮認証をまたいで数える）。セッションは最終操作から12時間、または発行から7日の早い方。
  - パスワードは bcrypt（強さ12、`DelegatingPasswordEncoder`）。レート制限は API では行わない（送信元単位は公開の入口の WAF）。
- **ローカルの開発**：DynamoDB は DynamoDB Local。KMS は使わず、暗号化・署名をインターフェースで抽象化して、ローカルでは Java 標準の AES-GCM（コンテキストを AAD に）・HmacSHA256 に差し替える（Spring のプロファイルで切り替え）。
- **環境名**（`infrastructure/environment/EnvironmentProperties`）：`sandbox.environment.app-env`（`dev` / `test` / `pr<番号>` / `local`。仮認証 JWT の `aud`）と `sandbox.environment.role-env`（`dev` / `test` / `pr` / `local`。KMS の暗号化コンテキストの `env`）。AWS ではタスク定義の環境変数 `APP_ENV`・`ROLE_ENV` で渡す。
- **TOTP の秘密情報の暗号化**（port：`application/port/TotpSecretEncryptor`、実装：`infrastructure/crypto`）：
  - aws のプロファイル：`KmsTotpSecretEncryptor`（KMS の Encrypt / Decrypt。鍵の ARN は `sandbox.kms.totp-secret-key-arn` ＝ 環境変数 `KMS_KEY_ARN`。復号でも鍵の ARN と暗号化コンテキストを渡す）。
  - local のプロファイル：`LocalTotpSecretEncryptor`（AES-GCM。コンテキストを AAD に入れる。暗号文は IV 12バイト＋暗号文とタグ）。鍵は `application-local.yml` の手元専用の固定の鍵（本物の秘密ではない。手元の DynamoDB Local は api を再起動しても残るため、固定にしている）。
  - ドメインの型：`UserId`（UUID）、`TotpSecret`（toString で中身を出さない）、`EncryptedTotpSecret`（DB に保存する形）。
- **仮認証 JWT の署名**（port：`application/port/LoginTokenSigner`・`LoginTokenClaims`、実装：`infrastructure/jwt`）：
  - `NimbusLoginTokenSigner`：JWT（HS256、`typ` は JWT）の組み立てと検証。`aud` に app-env、`sub` に userId、`jti`・`iat`・`exp` を入れる。検証は、形式・アルゴリズム・`aud`・有効期限を先に確かめ、通ったものだけ署名を確かめる（KMS の呼び出しを減らす）。1024 文字を超える JWT は解析しない。不正な JWT は空の `Optional` を返す。
  - 有効期間（5分）・`jti` の採番・`jti` と USER の値の照合はユースケースで行う（port は JWT にするだけ）。今の日時は引数で渡す。
  - aws のプロファイル：`KmsMacSigner` / `KmsMacVerifier`（Nimbus の `JWSSigner` / `JWSVerifier` を KMS の GenerateMac / VerifyMac で実装。鍵の ARN は `sandbox.kms.jwt-key-arn` ＝ 環境変数 `JWT_KMS_KEY_ARN`）。署名が合わないとき KMS は `KmsInvalidMacException` を返すため false にする。KMS のほかの失敗は例外のまま（不正な JWT とは扱わない）。
  - local のプロファイル：Nimbus の `MACSigner` / `MACVerifier`（HmacSHA256）。鍵は `application-local.yml` の手元専用の固定の鍵（256 ビット）。
  - `nimbus-jose-jwt` は Spring Boot の BOM で管理されていないため、`build.gradle.kts` にバージョンを書いている。
- **エラー応答**（`presentation/error/ApiExceptionHandler`・`ErrorCode`）：Spring の `ResponseEntityExceptionHandler` を継承し、Problem Details に `code` を加える。
  - 入力の検証（Bean Validation）と JSON の形式の誤り：400 `VALIDATION_FAILED`。`errors` は `field`・`message` だけ（入力の値は返さない）。並び順は項目名の順。
  - アプリの例外（`LoginIdTakenException` 等）は `@ExceptionHandler` で `ErrorCode` に対応させる。`ErrorCode` は API を作るときに足していく。
  - 想定外の例外：500 `INTERNAL_ERROR`（内容はログだけ）。Spring MVC の例外（404・405 等）は Spring の既定のまま（`code` なし）。
- **入力の検証**：形式の規則はドメインの型が持ち（`LoginId.PATTERN`・`Password.PATTERN`）、presentation の Bean Validation の注釈は同じ定数を使う（規則を1か所にする）。presentation で検証した後にドメインの型に変える。
  - パスワードは、表示できる ASCII（空白を含む）だけにしている。bcrypt は72バイトまでしか扱わず（Spring Security は超えると例外）、64文字でも72バイトを超えないようにするため。
- **パスワードのハッシュ**（port：`application/port/PasswordHasher`、実装：`infrastructure/password/BcryptPasswordHasher`）：`spring-security-crypto` の `DelegatingPasswordEncoder`（bcrypt、強さ12）。Spring Security の認証の仕組みは入れない。照合は `matches`。
- **認証アプリの設定 URI**（`domain/model/shared/totp/OtpauthUri`）：`otpauth://totp/<issuer>:<loginId>?secret=<Base32>&issuer=...&algorithm=SHA1&digits=6&period=30`。issuer は `sandbox.otpauth.issuer`（`sandbox-<app-env>`。同じ端末に複数の環境を登録しても見分けられるように）。秘密情報は20バイト（`TotpSecret.generate`）。
- **TOTP の照合**（`domain/service/TotpVerifier`）：java-otp（1.0.0）の HOTP でステップのコードを計算し、今のステップの前後1ステップを照合して、一致したステップを返す。最後に使ったステップを渡すと、それより後だけを受け付ける。一致したかで時間が変わらないよう、全部の候補を計算して一定時間で比べる。java-otp は計算だけのライブラリのため、domain から使っている（ArchUnit の禁止の対象外）。
- **登録の確定**（`VerifyRegistrationUseCase`）：仮登録が無い・期限切れなら 404。OTP の試行は照合の前に数え（`RegistrationRepository.countOtpAttempt`：`ADD`。条件は上限の5回に達していないこと。数えられた試行だけが照合に進むため、同時に試されても上限を超えて照合しない。数えられなければ 404）、5回目の失敗で仮登録を削除して 429、それ以外は 401（`remainingAttempts`）。成功したら `Registration.complete` でユーザーを作り（一致したステップを `lastUsedTotpStep` にする）、`Session.issue` でセッションを発行し、`RegistrationCompletionStore` で保存する。応答は 201（`sessionId`・`sessionExpiresAt`）。UUID の形でない登録 ID も 404。
- **ログインの開始**（`StartLoginUseCase`）：
  - ロック（`User`）：パスワードまたは OTP の失敗が5回続いたら、アカウントを15分ロックする（期限は `lockedUntil` の1つで、どちらでロックしたかは区別しない）。ロック中は照合しない。ロックの期限が過ぎた後の最初のログインの開始で、パスワード・OTP の失敗回数を0から数え直す。パスワードが合ったら（`User.passwordVerified`）、パスワードの失敗回数を0に戻してロックを解き、新しい jti を保存する。OTP の失敗回数は、OTP が合ったときだけ0に戻す（パスワードが合っても戻さない。ログインを始め直して OTP を試し続けられないようにするため）。
  - 照合の前に、その試行を失敗として数えて保存する（`User.countPasswordAttempt` → `UserRepository.savePasswordAttempt`。5回目ならロックの期限も同時に書く）。保存は、読んだときから失敗回数・ロックの期限が変わっていないことを条件にし、同時の試行は先に書いた1つだけが照合に進む（bcrypt の照合の間に並べて試されても、上限を超えて照合しない）。先を越された試行は 401。
  - ユーザーが無い・ロック中・先を越されたときも、照合しないで返すのではなく、ダミーのハッシュと照合してから 401 を返す（応答時間で、ユーザーの有無やロックが分からないようにする）。応答はすべて 401 `INVALID_CREDENTIALS`。
  - 成功したら、jti（UUID）を保存してから仮認証 JWT（有効期間5分、`StartLoginUseCase.LOGIN_TOKEN_VALIDITY`）に署名し、200（`loginToken`・`expiresAt`）を返す。前の仮認証の JWT は jti が変わるため使えなくなる。
- **ログインの確定**（`VerifyLoginUseCase`）：
  - 仮認証 JWT を検証し（署名・`aud`・期限）、`sub` のユーザーの `loginTokenId` が JWT の `jti` と同じことを確かめる（`User.hasLoginToken`）。違えば 401 `LOGIN_TOKEN_INVALID`（使用済み・新しいログインの開始で置き換わった・上限で無効にした仮認証）。
  - OTP の試行は、照合の前に数える（`UserRepository.countOtpAttempt`：`ADD`。条件は jti が同じで、上限の5回に達していないこと）。数えられた試行だけが照合に進むため、同時に試されても上限を超えて照合しない。照合は前後1ステップで、最後に使ったステップより後のものだけ。
  - 失敗したら、上限に達していれば、アカウントを15分ロックして仮認証を終え（`User.lockForOtpFailures` → `UserRepository.saveOtpLock`。jti が同じときだけ書き、新しい仮認証は消さない）429、それ以外は 401 `OTP_INVALID`（`remainingAttempts`）。
  - 成功したら `User.otpVerified` で、一致したステップを最後に使ったステップにし、OTP の失敗回数を0に戻して仮認証を終える。セッションの作成と一緒に `LoginCompletionStore` で保存する。条件に合わなければ（同じコードが同時に使われた）失敗と同じに扱う。応答は 200（`sessionId`・`sessionExpiresAt`）。
- **セッション**（`domain/model/session`）：セッション ID は 256 ビットの乱数の Base64URL（43文字）。DB には SHA-256 の16進数（`SessionIdHash`）だけを保存し、元の値は発行したとき（`IssuedSession`）にしか持たない。期限は最終操作から12時間・発行から7日の早い方。
- **セッションの API**（`GetCurrentUserUseCase`・`LogoutUseCase`、`presentation/session`）：
  - セッション ID は `Authorization: Bearer <sessionId>` で受け取る（`BearerSessionId`。方式の名前は大文字と小文字を区別しない）。ヘッダーが無い・Bearer でない・発行する形（43文字の Base64URL、`SessionId.fromClient`）でないときは、DB を読まずに 401 `SESSION_INVALID`。401 には `WWW-Authenticate: Bearer` を付ける。
  - `GET /v1/me`：セッションを読み（無い・期限切れなら 401）、`Session.touch` で最終操作の日時を今にして期限を延ばし（発行から7日より後にはならない）、保存する（`SessionRepository.saveAccess`。セッションがあることが条件で、同時にログアウトされていたら 401）。応答は 200（`loginId`・`createdAt`・`sessionExpiresAt`。`sessionExpiresAt` は延ばした後の期限で、BFF がセッションの Cookie の期限をそろえるために使う）。
  - `DELETE /v1/sessions/current`：セッションを削除して 204。既に無くても 204（何度呼んでも同じ結果）。
- **DynamoDB の実装**（`net.yosing.sandbox.api.infrastructure.dynamodb`）：
  - 設定は `sandbox.dynamodb.table-name`（dev / test 等はタスク定義の環境変数 `DYNAMODB_TABLE_NAME`）と `sandbox.dynamodb.endpoint`（`dynamodb-local` のプロファイルだけ `http://localhost:8000`）。リージョンは `sandbox.aws.region`。
  - `dynamodb-local` のプロファイルでは、起動時にテーブルを作る（`DynamoDbLocalTableInitializer`。DynamoDB Local に接続できるまで1秒おきに最大30回待つ）。接続先が DynamoDB Local のときは、固定のダミーの認証情報を使う。
  - DynamoDB Local のイメージは ECR Public の `aws-dynamodb-local` 3.3.2（ダイジェストで固定、`-inMemory -sharedDb`）。`compose.yaml`（手元）、`deploy/task-def.jsonnet`（PR 環境のサイドカー）、`DynamoDbLocalContainerConfiguration`（テスト）の3か所で揃える。
  - 結合テスト（`src/integrationTest`）は `local` のプロファイルと `DynamoDbLocalContainerConfiguration`（Testcontainers）で動かす（Docker が要る）。テーブルはテストの間で共有するため、テストごとに違うキー（ログイン ID 等）を使う。
  - 低レベルの `DynamoDbClient` を使い、項目の変換は種類ごとの `*ItemMapper`（属性名は定数）に書く。キーは `ItemKeys`。属性名は lowerCamelCase、日時は ISO 8601 の文字列（S）、TTL の `expiresAt` は Unix 時刻の秒（N）、暗号文はバイナリ（B）。
  - 仮登録（`REG#`）の属性：`registrationId`・`userId`・`loginId`・`passwordHash`・`totpSecret`・`otpFailureCount`・`createdAt`・`expiresAt`。保存は `attribute_not_exists` の条件付き。
  - ユーザー（`USER#`）の属性：`userId`・`loginId`・`passwordHash`・`totpSecret`・`lastUsedTotpStep`（N）・`passwordFailureCount`（N）・`otpFailureCount`（N）・`lockedUntil`（S。ロック中だけ）・`loginTokenId`（S。仮認証中だけ）・`createdAt`。ログイン ID（`LOGINID#`）の属性：`userId`。
  - セッション（`SESSION#<ハッシュ>`）の属性：`userId`・`createdAt`・`lastAccessedAt`・`expiresAt`（N、TTL）。
  - ユーザーは `DynamoDbUserRepository`：ログイン ID の項目 → ユーザーの項目の順に読む（GSI は使わない）。パスワードの試行は、失敗回数（パスワード・OTP）とロックの期限が読んだときと同じことを条件に `UpdateItem`（`ConditionalCheckFailedException` なら false を返す）。パスワードが合ったときは、パスワードの失敗回数を0、`loginTokenId` を新しい jti にして、`lockedUntil` を消す（OTP の失敗回数は変えない）。OTP の試行は `ADD`、OTP のロックは jti が同じことを条件に `lockedUntil` を書いて `loginTokenId` を消す。
  - ログインの確定は `DynamoDbLoginCompletionStore`（`TransactWriteItems`：USER の更新は、`loginTokenId` が読んだときと同じで `lastUsedTotpStep` が一致したステップより小さいこと、SESSION は無いこと）。USER の条件に合わなければ false を返す。
  - セッションは `DynamoDbSessionRepository`：読み取りは強い整合性。延長は、項目があることを条件に `lastAccessedAt` と `expiresAt`（TTL）を更新する。期限（`expiresAt`）は、読むときに発行と最終操作の日時から計算し直す。
  - 登録の確定は `DynamoDbRegistrationCompletionStore`（`TransactWriteItems`：USER・LOGINID・SESSION は無いこと、REG はあること）。取り消しの理由（`CancellationReason`。書いた順に並ぶ）で、ログイン ID の先取り（409）と仮登録が無い（404）を見分ける。
  - 結合テストで項目を準備・確認するときは、`src/integrationTest` の `DynamoDbTestItems`（本体の `ItemKeys`・`*ItemMapper` を使う）を使い、キーや属性名をテストに文字列で書かない。
  - 認証の判定に使う読み取りは `consistentRead(true)`。

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

- デプロイは **ecspresso**。定義は `deploy/`（`ecspresso.jsonnet`・`task-def.jsonnet`・`service-def.jsonnet`）。値は SSM パラメータから読み（ecspresso の `ssm` / `ssm_list`）、環境ごとの値は CI が環境変数で渡す（`APP_ENV`：`dev` / `test` / `pr<番号>`、`ROLE_ENV`：`dev` / `test` / `pr`、`ECS_CLUSTER`、`IMAGE`、`CLOUD_MAP_SERVICE_ARN`）。api のコンテナには、`SPRING_PROFILES_ACTIVE`（＝`ROLE_ENV`）、`APP_ENV`、`ROLE_ENV`、`KMS_KEY_ARN`、`JWT_KMS_KEY_ARN`、`DYNAMODB_TABLE_NAME`（dev / test だけ）を渡す。
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
- PR 環境は本物のテーブルを使わず、タスクに DynamoDB Local（ECR Public の `aws-dynamodb-local`、`-inMemory -sharedDb`）をサイドカーで同居させ、api から `localhost:8000` で使う。データはデプロイ・Spot の中断・毎日の停止で消えるため、api の起動時にテーブルを作る（初期データは、必要になったら足す）。テーブルの定義は `aws-infra-env` の `dynamodb.tf` と揃える。
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
