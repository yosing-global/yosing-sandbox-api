// タスク定義（値は SSM パラメータから読む。アカウント ID 等をファイルに書かない）
local must_env = std.native('must_env');
local ssm = std.native('ssm');

local appEnv = must_env('APP_ENV');
local roleEnv = must_env('ROLE_ENV');

// PR 環境は本物のテーブルを使わず、DynamoDB Local をサイドカーで同居させる（api から localhost:8000）
local isPr = roleEnv == 'pr';

local logConfiguration = {
  logDriver: 'awslogs',
  options: {
    'awslogs-group': ssm('/products/sandbox/api/log-group-name'),
    'awslogs-region': 'ap-northeast-1',
    // ログストリームは <アプリの環境>/<コンテナ>/<タスク ID>
    'awslogs-stream-prefix': appEnv,
  },
};

local api = {
  name: 'api',
  image: must_env('IMAGE'),
  essential: true,
  // JVM のヒープ（コンテナのメモリの 75%）の基準にするため、コンテナの上限を決める
  memory: 1024,
  portMappings: [
    {
      containerPort: 8080,
      protocol: 'tcp',
    },
  ],
  environment: [
    // 環境名のプロファイル（application.yml のグループで aws 等が有効になる）
    { name: 'SPRING_PROFILES_ACTIVE', value: roleEnv },
    // 環境名（APP_ENV：仮認証 JWT の aud、ROLE_ENV：KMS の暗号化コンテキストの env）
    { name: 'APP_ENV', value: appEnv },
    { name: 'ROLE_ENV', value: roleEnv },
    // TOTP の秘密情報の暗号化用の KMS の鍵
    { name: 'KMS_KEY_ARN', value: ssm('/products/sandbox/api/kms-key-arn') },
  ] + (
    if isPr then [] else [
      // テーブル（dev / test。stg / prod はインフラを作るときに足す）
      { name: 'DYNAMODB_TABLE_NAME', value: ssm('/products/sandbox/dynamodb/' + roleEnv + '/table-name') },
    ]
  ),
  logConfiguration: logConfiguration,
} + (
  // api は起動時に DynamoDB Local にテーブルを作る（DynamoDB Local の起動後に始め、接続できるまで待つ）
  if isPr then { dependsOn: [{ containerName: 'dynamodb-local', condition: 'START' }] } else {}
);

// イメージは compose.yaml・テスト（DynamoDbLocalContainerConfiguration）と揃える
//   3.3.2 のダイジェストで指定する（タグとダイジェストを同時に書かない）
//   -inMemory：データはタスクの停止（デプロイ・Spot の中断・毎日の停止）で消える
local dynamodbLocal = {
  name: 'dynamodb-local',
  image: 'public.ecr.aws/aws-dynamodb-local/aws-dynamodb-local@sha256:6ada631ec7aa5fc83446855eb17233e3dad184fec47477e8140c6c569cfe0c86',
  essential: true,
  memory: 1024,
  command: ['-jar', 'DynamoDBLocal.jar', '-inMemory', '-sharedDb'],
  logConfiguration: logConfiguration,
};

{
  family: 'sandbox-' + appEnv + '-api',
  networkMode: 'awsvpc',
  requiresCompatibilities: ['FARGATE'],
  runtimePlatform: {
    cpuArchitecture: 'ARM64',
    operatingSystemFamily: 'LINUX',
  },
  cpu: '512',
  // PR 環境は api 1GB ＋ DynamoDB Local 1GB
  memory: if isPr then '2048' else '1024',
  executionRoleArn: ssm('/products/sandbox/ecs/task-execution-role-arn'),
  taskRoleArn: ssm('/products/sandbox/api/' + roleEnv + '/task-role-arn'),
  containerDefinitions: [api] + (if isPr then [dynamodbLocal] else []),
}
