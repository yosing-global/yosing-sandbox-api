// ecspresso の設定（api の ECS のサービス）
//   CI が環境変数で渡す値
//     APP_ENV     … アプリの環境（dev / test / pr<番号>）。サービス・タスク定義・ログの名前に使う
//     ROLE_ENV    … タスクロール・プロファイルの単位（dev / test / pr。PR 環境はすべて pr）
//     ECS_CLUSTER … クラスター（SSM の /products/sandbox/ecs/cluster-name）
//     IMAGE       … イメージ（<レジストリ>/sandbox/api:<コミットのハッシュ>）
local must_env = std.native('must_env');

{
  required_version: '>= 2.8.0, < 3',
  region: 'ap-northeast-1',
  cluster: must_env('ECS_CLUSTER'),
  service: must_env('APP_ENV') + '-api',
  service_definition: 'service-def.jsonnet',
  task_definition: 'task-def.jsonnet',
  timeout: '15m',
}
