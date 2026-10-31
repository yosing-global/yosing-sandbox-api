// タスク定義（値は SSM パラメータから読む。アカウント ID 等をファイルに書かない）
local must_env = std.native('must_env');
local ssm = std.native('ssm');

local appEnv = must_env('APP_ENV');
local roleEnv = must_env('ROLE_ENV');

{
  family: 'sandbox-' + appEnv + '-api',
  networkMode: 'awsvpc',
  requiresCompatibilities: ['FARGATE'],
  runtimePlatform: {
    cpuArchitecture: 'ARM64',
    operatingSystemFamily: 'LINUX',
  },
  cpu: '512',
  memory: '1024',
  executionRoleArn: ssm('/products/sandbox/ecs/task-execution-role-arn'),
  taskRoleArn: ssm('/products/sandbox/api/' + roleEnv + '/task-role-arn'),
  containerDefinitions: [
    {
      name: 'api',
      image: must_env('IMAGE'),
      essential: true,
      portMappings: [
        {
          containerPort: 8080,
          protocol: 'tcp',
        },
      ],
      environment: [
        // 環境名のプロファイル（application.yml のグループで aws 等が有効になる）
        { name: 'SPRING_PROFILES_ACTIVE', value: roleEnv },
      ],
      logConfiguration: {
        logDriver: 'awslogs',
        options: {
          'awslogs-group': ssm('/products/sandbox/api/log-group-name'),
          'awslogs-region': 'ap-northeast-1',
          // ログストリームは <アプリの環境>/api/<タスク ID>
          'awslogs-stream-prefix': appEnv,
        },
      },
    },
  ],
}
