# syntax=docker/dockerfile:1

# api のイメージ（linux/arm64 で動かす。Fargate Spot の ARM64）
#   jar は先に ./gradlew build で作っておく（build/libs/app.jar）。ここでは Gradle を動かさない
#   ベースのイメージはダイジェストで固定する（同じタグのまま中身が変わっても、知らないうちに入れ替わらない）
ARG BASE_IMAGE=eclipse-temurin:25-jre-noble@sha256:2ef7a81085e0502d7d4ff7e7c0257b6d4f46433f8ea6021180aa3c8a2b9ca3ec

# jar を層（依存ライブラリ・アプリ等）に分ける。コードだけ変えたときは、小さな層だけを push すればよい
#   出力は CPU に依存しないため、ビルドするマシンの CPU で動かす（x86 のマシンで arm64 を作るときも、エミュレーションが要らない）
FROM --platform=$BUILDPLATFORM ${BASE_IMAGE} AS extract
WORKDIR /work
COPY build/libs/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM ${BASE_IMAGE}
WORKDIR /app

# 変わりにくい順に重ねる
COPY --from=extract /work/extracted/dependencies/ ./
COPY --from=extract /work/extracted/spring-boot-loader/ ./
COPY --from=extract /work/extracted/snapshot-dependencies/ ./
COPY --from=extract /work/extracted/application/ ./

# root で動かさない（ベースのイメージ（Ubuntu）にある一般ユーザー ubuntu）
USER 1000:1000

EXPOSE 8080

# ヒープはコンテナのメモリの 75%（既定の 25% では、1GB のタスクで 256MB しか使えない）
#   exec 形式で、java が SIGTERM を直接受け取る（ECS の停止のときに、処理中の要求を終えてから止まる）
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
